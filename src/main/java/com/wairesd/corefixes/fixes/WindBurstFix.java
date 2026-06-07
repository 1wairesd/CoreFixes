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

/**
 * Fixes missing Wind Burst launch velocity when Paper's EntityExplodeEvent is cancelled.
 * Uses ENTITY_VELOCITY packet.
 *
 * Исправляет отсутствие impulse от Wind Burst когда Paper отменяет взрыв.
 * Использует ENTITY_VELOCITY пакет.
 */
public class WindBurstFix implements Listener {

    private static final Enchantment WIND_BURST =
            Enchantment.getByKey(NamespacedKey.minecraft("wind_burst"));

    private final JavaPlugin plugin;
    private Constructor<?> vec3Ctor;
    private Constructor<?> motionPacketCtor;
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
        packetInterface = Class.forName("net.minecraft.network.protocol.Packet");

        Class<?> vec3Class = Class.forName("net.minecraft.world.phys.Vec3");
        vec3Ctor = vec3Class.getConstructor(double.class, double.class, double.class);

        Class<?> motionClass = Class.forName(
                "net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket");
        motionPacketCtor = motionClass.getDeclaredConstructor(int.class, vec3Class);
        motionPacketCtor.setAccessible(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (player.getInventory().getItemInMainHand().getType() != org.bukkit.Material.MACE) return;
        if (WIND_BURST == null
                || player.getInventory().getItemInMainHand().getEnchantmentLevel(WIND_BURST) <= 0) return;
        if (player.getFallDistance() <= 1.5f) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            org.bukkit.util.Vector vel = player.getVelocity();
            if (vel.getY() < 1.0) return;
            sendVelocityPacket(player, vel.getX(), vel.getY(), vel.getZ());
        }, 1L);
    }

    private void sendVelocityPacket(Player player, double vx, double vy, double vz) {
        try {
            Object nmsPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Object vel = vec3Ctor.newInstance(vx, vy, vz);
            Object packet = motionPacketCtor.newInstance(player.getEntityId(), vel);

            Object connection = findConnection(nmsPlayer);
            connection.getClass().getMethod("send", packetInterface).invoke(connection, packet);

            player.getWorld().spawnParticle(
                    org.bukkit.Particle.GUST_EMITTER_SMALL,
                    player.getLocation().add(0, 0.5, 0), 2, 0.8, 0.8, 0.8, 0);
            player.getWorld().playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.0f);
        } catch (Exception e) {
            plugin.getLogger().warning("[WindBurstFix] sendVelocityPacket failed: " + e);
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
        throw new RuntimeException("Connection not found for " + nmsPlayer);
    }
}
