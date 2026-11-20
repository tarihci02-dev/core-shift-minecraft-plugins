package com.coreshift.maden;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class MadenCommand
implements CommandExecutor,
TabCompleter {
    private final CoreShiftMaden plugin;
    private final MineManager mineManager;
    private final PendingManager pendingManager;

    public MadenCommand(CoreShiftMaden coreShiftMaden, MineManager mineManager, PendingManager pendingManager) {
        this.plugin = coreShiftMaden;
        this.mineManager = mineManager;
        this.pendingManager = pendingManager;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (!commandSender.hasPermission("coreshift.maden.admin")) {
            this.plugin.msg(commandSender, "no-permission", new String[0]);
            return true;
        }
        if (stringArray.length == 0) {
            this.plugin.msg(commandSender, "usage", new String[0]);
            return true;
        }
        switch (stringArray[0].toLowerCase(Locale.ROOT)) {
            case "create": {
                this.create(commandSender, stringArray);
                break;
            }
            case "delete":
            case "sil": {
                this.delete(commandSender, stringArray);
                break;
            }
            case "list":
            case "liste": {
                this.list(commandSender);
                break;
            }
            case "reload": {
                this.reload(commandSender);
                break;
            }
            default: {
                this.plugin.msg(commandSender, "usage", new String[0]);
            }
        }
        return true;
    }

    private void create(CommandSender commandSender, String[] stringArray) {
        if (!(commandSender instanceof Player)) {
            this.plugin.msg(commandSender, "only-player", new String[0]);
            return;
        }
        Player player = (Player)commandSender;
        if (stringArray.length < 2) {
            commandSender.sendMessage(CoreShiftMaden.color("&eKullanim: /maden create <isim>"));
            return;
        }
        try {
            Mine mine = this.mineManager.createFromSelection(player, stringArray[1]);
            this.plugin.msg(commandSender, "created", "%mine%", mine.getName());
        }
        catch (SelectionException selectionException) {
            switch (selectionException.reason()) {
                case UNAVAILABLE: {
                    this.plugin.msg(commandSender, "worldedit-required", new String[0]);
                    break;
                }
                case INCOMPLETE: {
                    this.plugin.msg(commandSender, "selection-required", new String[0]);
                    break;
                }
                case NOT_CUBOID: {
                    this.plugin.msg(commandSender, "cuboid-required", new String[0]);
                }
            }
        }
        catch (IllegalArgumentException illegalArgumentException) {
            commandSender.sendMessage(CoreShiftMaden.color("&c" + illegalArgumentException.getMessage()));
        }
        catch (IOException iOException) {
            commandSender.sendMessage(CoreShiftMaden.color("&cMaden dosyasi kaydedilemedi. Konsolu kontrol et."));
            this.plugin.getLogger().severe("Maden olusturma hatasi: " + iOException.getMessage());
        }
    }

    private void delete(CommandSender commandSender, String[] stringArray) {
        if (stringArray.length < 2) {
            commandSender.sendMessage(CoreShiftMaden.color("&eKullanim: /maden delete <isim>"));
            return;
        }
        Mine mine = this.mineManager.get(stringArray[1]);
        if (mine == null) {
            this.plugin.msg(commandSender, "not-found", "%mine%", stringArray[1]);
            return;
        }
        if (this.mineManager.delete(mine.getName())) {
            this.pendingManager.restoreMineNow(mine.getName());
            this.plugin.msg(commandSender, "deleted", "%mine%", mine.getName());
        } else {
            commandSender.sendMessage(CoreShiftMaden.color("&cMaden dosyasi silinemedi: " + mine.getFile().getName()));
        }
    }

    private void list(CommandSender commandSender) {
        String string = this.mineManager.getAll().stream().map(Mine::getName).collect(Collectors.joining(", "));
        if (string.trim().isEmpty()) {
            string = "yok";
        }
        this.plugin.msg(commandSender, "mine-list", "%mines%", string);
    }

    private void reload(CommandSender commandSender) {
        this.plugin.reloadConfig();
        this.mineManager.reloadAll();
        this.pendingManager.reloadSettings();
        this.plugin.msg(commandSender, "reloaded", new String[0]);
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (!commandSender.hasPermission("coreshift.maden.admin")) {
            return Collections.emptyList();
        }
        if (stringArray.length == 1) {
            return this.filter(java.util.Arrays.asList("create", "delete", "list", "reload"), stringArray[0]);
        }
        if (stringArray.length == 2 && (stringArray[0].equalsIgnoreCase("delete") || stringArray[0].equalsIgnoreCase("sil"))) {
            return this.filter(this.mineManager.getAll().stream().map(Mine::getName).collect(java.util.stream.Collectors.toList()), stringArray[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String string) {
        String string2 = string.toLowerCase(Locale.ROOT);
        ArrayList<String> arrayList = new ArrayList<String>();
        for (String string3 : list) {
            if (!string3.toLowerCase(Locale.ROOT).startsWith(string2)) continue;
            arrayList.add(string3);
        }
        return arrayList;
    }
}
