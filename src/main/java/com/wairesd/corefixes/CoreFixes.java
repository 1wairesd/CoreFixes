package com.wairesd.corefixes;

import com.wairesd.corefixes.fixes.WindBurstFix;
import org.bukkit.plugin.java.JavaPlugin;

public final class CoreFixes extends JavaPlugin {

    @Override
    public void onEnable() {
        new WindBurstFix(this).register();
    }

    @Override
    public void onDisable() {}
}
