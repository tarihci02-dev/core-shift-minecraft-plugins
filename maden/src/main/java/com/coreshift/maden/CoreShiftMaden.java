package com.coreshift.maden;

import java.util.Objects;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class CoreShiftMaden
extends JavaPlugin {
    private MineManager mineManager;
    private PendingManager pendingManager;
    private RewardService rewardService;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        SelectionProvider selectionProvider = this.loadSelectionProvider();
        this.mineManager = new MineManager(this, selectionProvider);
        this.pendingManager = new PendingManager(this);
        this.rewardService = new RewardService();
        this.getServer().getPluginManager().registerEvents(new MineListener(this, this.mineManager, this.pendingManager, this.rewardService), this);
        MadenCommand madenCommand = new MadenCommand(this, this.mineManager, this.pendingManager);
        Objects.requireNonNull(this.getCommand("maden")).setExecutor(madenCommand);
        Objects.requireNonNull(this.getCommand("maden")).setTabCompleter(madenCommand);
        this.getLogger().info("Core Shift Maden v" + this.getDescription().getVersion() + " aktif. " + this.mineManager.getAll().size() + " maden, " + this.pendingManager.size() + " pending blok yuklendi.");
    }

    @Override
    public void onDisable() {
        if (this.pendingManager != null) {
            this.pendingManager.shutdown();
        }
    }

    private SelectionProvider loadSelectionProvider() {
        PluginManager pluginManager = this.getServer().getPluginManager();
        boolean selectionAvailable = pluginManager.isPluginEnabled("WorldEdit") || pluginManager.isPluginEnabled("FastAsyncWorldEdit");
        if (!selectionAvailable) {
            this.getLogger().warning("WorldEdit/FAWE bulunamadi. Plugin calisir; sadece /maden create devre disidir.");
            return null;
        }
        try {
            Class<?> clazz = Class.forName("com.coreshift.maden.WorldEditSelectionProvider", true, this.getClass().getClassLoader());
            return (SelectionProvider)clazz.getDeclaredConstructor().newInstance();
        }
        catch (LinkageError | ReflectiveOperationException throwable) {
            this.getLogger().severe("WorldEdit hook yuklenemedi: " + throwable.getMessage());
            return null;
        }
    }

    public void msg(CommandSender commandSender, String string, String ... stringArray) {
        String string2 = this.getConfig().getString("messages.prefix", "");
        String string3 = this.getConfig().getString("messages." + string, string);
        int n = 0;
        while (n + 1 < stringArray.length) {
            string3 = string3.replace(stringArray[n], stringArray[n + 1]);
            n += 2;
        }
        commandSender.sendMessage(CoreShiftMaden.color(string2 + string3));
    }

    public static String color(String string) {
        return ChatColor.translateAlternateColorCodes('&', string == null ? "" : string);
    }
}
