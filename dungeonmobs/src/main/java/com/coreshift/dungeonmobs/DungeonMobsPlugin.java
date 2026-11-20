package com.coreshift.dungeonmobs;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class DungeonMobsPlugin
extends JavaPlugin {
    private MobManager mobManager;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.mobManager = new MobManager(this);
        this.mobManager.reloadAll();
        this.mobManager.start();
        MobCommand mobCommand = new MobCommand(this, this.mobManager);
        PluginCommand pluginCommand = this.getCommand("mob");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(mobCommand);
            pluginCommand.setTabCompleter(mobCommand);
        }
        this.getServer().getPluginManager().registerEvents(new MobDeathListener(this.mobManager), this);
        this.getServer().getPluginManager().registerEvents(new MobWorldListener(this.mobManager), this);
        this.getLogger().info("Core Shift Dungeon Mobs v" + getDescription().getVersion() + " aktif. Yuklenen mob dosyasi: " + this.mobManager.getDefinitionCount());
    }

    @Override
    public void onDisable() {
        if (this.mobManager != null) {
            this.mobManager.stop();
        }
    }

    public int getMaxTargetDistance() {
        return Math.max(5, this.getConfig().getInt("max-target-distance", 30));
    }
}
