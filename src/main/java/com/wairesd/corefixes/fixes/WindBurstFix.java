package com.wairesd.corefixes.fixes;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class WindBurstFix implements Listener {

    private static final Enchantment WIND_BURST = Enchantment.getByKey(NamespacedKey.minecraft("wind_burst"));

    private final JavaPlugin plugin;
    private final AtomicInteger teleportId = new AtomicInteger(1000);
    private Constructor<?> vec3Ctor;
    private Constructor<?> pmrCtor;
    private Constructor<?> posPacketCtor;
    private Object[] allRelatives;
    private Class<?> packetInterface;

    public WindBurstFix(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        try {
            initReflection();
        } catch (Exception e) {
            plugin.getLogger().severe("[WindBurstFix] Reflection init failed, fix disabled: " + e);
            return;
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("[WindBurstFix] Enabled.");
    }

    private void initReflection() throws Exception {
        Class<?> vec3Class = Class.forName("net.minecraft.world.phys.Vec3");
        vec3Ctor = vec3Class.getConstructor(double.class, double.class, double.class);
        packetInterface = Class.forName("net.minecraft.network.protocol.Packet");

        Class<?> posPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket");

        Class<?> pmrClass = Arrays.stream(posPacketClass.getDeclaredFields())
            .filter(f -> f.getType().getSimpleName().equals("PositionMoveRotation"))
            .findFirst().orElseThrow().getType();
        pmrCtor = pmrClass.getConstructor(vec3Class, vec3Class, float.class, float.class);

        Class<?> relClass = (Class<?>) ((ParameterizedType) Arrays.stream(posPacketClass.getDeclaredFields())
            .filter(f -> f.getName().equals("relatives"))
            .findFirst().orElseThrow().getGenericType()).getActualTypeArguments()[0];
        allRelatives = relClass.getEnumConstants();

        posPacketCtor = posPacketClass.getDeclaredConstructor(int.class, pmrClass, Set.class);
        posPacketCtor.setAccessible(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (player.getInventory().getItemInMainHand().getType() != org.bukkit.Material.MACE) return;
        if (WIND_BURST == null || player.getInventory().getItemInMainHand().getEnchantmentLevel(WIND_BURST) <= 0) return;
        if (player.getFallDistance() <= 0) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            var vel = player.getVelocity();
            if (vel.getY() < 1.0) return;
            sendPositionPacket(player, vel.getX(), vel.getY(), vel.getZ());
        }, 1L);
    }

    private void sendPositionPacket(Player player, double vx, double vy, double vz) {
        try {
            Object zero = vec3Ctor.newInstance(0.0, 0.0, 0.0);
            Object pmr = pmrCtor.newInstance(zero, vec3Ctor.newInstance(vx, vy, vz), 0.0f, 0.0f);
            Set<Object> relatives = new HashSet<>(Arrays.asList(allRelatives));
            Object packet = posPacketCtor.newInstance(teleportId.incrementAndGet(), pmr, relatives);

            Object nmsPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = findConnection(nmsPlayer);
            connection.getClass().getMethod("send", packetInterface).invoke(connection, packet);

            player.getWorld().spawnParticle(org.bukkit.Particle.GUST_EMITTER_SMALL,
                player.getLocation().add(0, 0.5, 0), 2, 0.8, 0.8, 0.8, 0);
            player.getWorld().playSound(player.getLocation(),
                org.bukkit.Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.0f);
        } catch (Exception e) {
            plugin.getLogger().warning("[WindBurstFix] sendPositionPacket failed: " + e);
        }
    }

    private Object findConnection(Object nmsPlayer) throws Exception {
        for (Class<?> c = nmsPlayer.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(nmsPlayer);
                if (val != null && val.getClass().getSimpleName().contains("ServerGamePacketListenerImpl"))
                    return val;
            }
        }
        throw new RuntimeException("Connection not found");
    }
}
