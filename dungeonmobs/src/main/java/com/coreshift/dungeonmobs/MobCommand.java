package com.coreshift.dungeonmobs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public final class MobCommand
implements CommandExecutor,
TabCompleter {
    private final DungeonMobsPlugin plugin;
    private final MobManager manager;

    public MobCommand(DungeonMobsPlugin dungeonMobsPlugin, MobManager mobManager) {
        this.plugin = dungeonMobsPlugin;
        this.manager = mobManager;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (!commandSender.hasPermission("coreshift.mob.admin")) {
            this.msg(commandSender, "&cYetkin yok.");
            return true;
        }
        if (stringArray.length == 0) {
            this.help(commandSender);
            return true;
        }
        try {
            switch (stringArray[0].toLowerCase(Locale.ROOT)) {
                case "create": {
                    this.create(commandSender, stringArray);
                    break;
                }
                case "addspawner": {
                    this.addSpawner(commandSender, stringArray);
                    break;
                }
                case "removespawner": {
                    this.removeSpawner(commandSender, stringArray);
                    break;
                }
                case "delete": {
                    this.delete(commandSender, stringArray);
                    break;
                }
                case "spawn": {
                    this.spawn(commandSender, stringArray);
                    break;
                }
                case "list": {
                    this.list(commandSender);
                    break;
                }
                case "reload": {
                    this.reload(commandSender);
                    break;
                }
                default: {
                    this.help(commandSender);
                    break;
                }
            }
        }
        catch (IllegalArgumentException illegalArgumentException) {
            this.msg(commandSender, "&cHata: " + illegalArgumentException.getMessage());
        }
        catch (IOException iOException) {
            this.msg(commandSender, "&cDosya hatasi: " + iOException.getMessage());
            this.plugin.getLogger().warning("DungeonMobs dosya hatasi: " + iOException.getMessage());
        }
        catch (Exception exception) {
            this.msg(commandSender, "&cBeklenmeyen hata: " + exception.getMessage());
            exception.printStackTrace();
        }
        return true;
    }

    private void create(CommandSender commandSender, String[] stringArray) throws IOException {
        EntityType entityType;
        String string;
        Player player = this.requirePlayer(commandSender);
        if (stringArray.length < 2) {
            this.msg(commandSender, "&eKullanim: /mob create <mob-turu>");
            this.msg(commandSender, "&7Ornek: /mob create SLIME veya /mob create SKELETON");
            this.msg(commandSender, "&7Ozel ID icin: /mob create <id> <mob-turu>");
            return;
        }
        if (stringArray.length >= 3) {
            string = stringArray[1];
            entityType = this.parseEntityType(stringArray[2]);
        } else {
            EntityType parsed = this.tryParseEntityType(stringArray[1]);
            if (parsed != null) {
                entityType = parsed;
                string = parsed.name().toLowerCase(Locale.ROOT);
            } else {
                string = stringArray[1];
                entityType = EntityType.ZOMBIE;
            }
        }
        this.validateMobType(entityType);
        Location location = this.targetLocation(player);
        String string2 = this.manager.createOrAddSpawner(string, entityType, location);
        if (!this.supportsArmor(entityType) && this.stripEquipmentSection(string)) {
            this.manager.reloadAll();
            this.manager.start();
        }
        this.msg(commandSender, "&aMob + gorunmez spawner olusturuldu.");
        this.msg(commandSender, "&7ID: &f" + string.toLowerCase(Locale.ROOT) + " &7| Tur: &f" + entityType.name() + " &7| Spawner: &f" + string2);
        this.msg(commandSender, "&7Config: &fplugins/CoreShiftDungeonMobs/mobs/" + string.toLowerCase(Locale.ROOT) + ".yml");
    }

    private EntityType parseEntityType(String string) {
        EntityType entityType = this.tryParseEntityType(string);
        if (entityType == null) {
            throw new IllegalArgumentException("Gecersiz mob turu: " + string);
        }
        return entityType;
    }

    private EntityType tryParseEntityType(String string) {
        try {
            return EntityType.valueOf(string.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException illegalArgumentException) {
            return null;
        }
    }

    private void validateMobType(EntityType entityType) {
        if (!entityType.isAlive() || !entityType.isSpawnable()) {
            throw new IllegalArgumentException(entityType.name() + " canli/spawn edilebilir bir mob degil.");
        }
    }

    private boolean supportsArmor(EntityType entityType) {
        switch (entityType.name()) {
            case "ZOMBIE":
            case "HUSK":
            case "DROWNED":
            case "ZOMBIE_VILLAGER":
            case "SKELETON":
            case "STRAY":
            case "WITHER_SKELETON":
            case "BOGGED":
            case "PIGLIN":
            case "PIGLIN_BRUTE":
            case "ZOMBIFIED_PIGLIN":
                return true;
            default:
                return false;
        }
    }

    private boolean stripEquipmentSection(String string) throws IOException {
        String string2 = string.toLowerCase(Locale.ROOT);
        if (!string2.matches("[a-z0-9_-]{1,32}")) {
            return false;
        }
        File file = new File(new File(this.plugin.getDataFolder(), "mobs"), string2 + ".yml");
        if (!file.isFile()) {
            return false;
        }
        List<String> list = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        ArrayList<String> arrayList = new ArrayList<String>(list.size());
        boolean bl = false;
        boolean bl2 = false;
        for (String string3 : list) {
            if (!bl && string3.equals("equipment:")) {
                bl = true;
                bl2 = true;
                continue;
            }
            if (bl) {
                if (string3.trim().isEmpty() || Character.isWhitespace(string3.charAt(0))) continue;
                bl = false;
                arrayList.add(string3);
                continue;
            }
            arrayList.add(string3);
        }
        if (bl2) {
            Files.write(file.toPath(), arrayList, StandardCharsets.UTF_8, new OpenOption[0]);
        }
        return bl2;
    }

    private void addSpawner(CommandSender commandSender, String[] stringArray) throws IOException {
        Player player = this.requirePlayer(commandSender);
        if (stringArray.length < 2) {
            this.msg(commandSender, "&eKullanim: /mob addspawner <id>");
            return;
        }
        String string = this.manager.addSpawner(stringArray[1], this.targetLocation(player));
        this.msg(commandSender, "&aSpawner eklendi: &f" + string);
    }

    private void removeSpawner(CommandSender commandSender, String[] stringArray) throws IOException {
        Player player = this.requirePlayer(commandSender);
        if (stringArray.length < 2) {
            this.msg(commandSender, "&eKullanim: /mob removespawner <id>");
            return;
        }
        String string = this.manager.removeNearestSpawner(stringArray[1], player.getLocation(), 10.0);
        this.msg(commandSender, "&aSpawner silindi: &f" + string);
    }

    private void delete(CommandSender commandSender, String[] stringArray) {
        if (stringArray.length < 2) {
            this.msg(commandSender, "&eKullanim: /mob delete <id>");
            return;
        }
        if (this.manager.deleteMob(stringArray[1])) {
            this.msg(commandSender, "&aMob silindi: &f" + stringArray[1]);
        } else {
            this.msg(commandSender, "&cMob bulunamadi: &f" + stringArray[1]);
        }
    }

    private void spawn(CommandSender commandSender, String[] stringArray) {
        Player player = this.requirePlayer(commandSender);
        if (stringArray.length < 2) {
            this.msg(commandSender, "&eKullanim: /mob spawn <id>");
            return;
        }
        this.manager.spawnForTest(stringArray[1], this.targetLocation(player));
        this.msg(commandSender, "&aTest mobu spawn edildi: &f" + stringArray[1]);
    }

    private void list(CommandSender commandSender) {
        this.msg(commandSender, "&bMob sayisi: &f" + this.manager.getDefinitionCount());
        for (MobDefinition mobDefinition : this.manager.getDefinitions()) {
            this.msg(commandSender, "&7- &f" + mobDefinition.id + " &7[" + mobDefinition.entityType.name() + "] &c" + (int)mobDefinition.health + "\u2764 &7Spawner: &f" + mobDefinition.spawners.size());
        }
    }

    private void reload(CommandSender commandSender) {
        this.manager.reloadAll();
        this.manager.start();
        this.msg(commandSender, "&aConfigler yenilendi. Mob sayisi: &f" + this.manager.getDefinitionCount());
    }

    private Player requirePlayer(CommandSender commandSender) {
        if (!(commandSender instanceof Player)) {
            throw new IllegalArgumentException("Bu komut oyuncu olarak kullanilmali.");
        }
        Player player = (Player)commandSender;
        return player;
    }

    private Location targetLocation(Player player) {
        Block block = player.getTargetBlockExact(this.plugin.getMaxTargetDistance());
        if (block == null) {
            throw new IllegalArgumentException("En fazla " + this.plugin.getMaxTargetDistance() + " blok uzakta bir bloga bakmalisin.");
        }
        return block.getLocation().add(0.5, 1.0, 0.5);
    }

    private void help(CommandSender commandSender) {
        this.msg(commandSender, "&b&lCore Shift Dungeon Mobs");
        this.msg(commandSender, "&f/mob create <mob-turu> &7- SLIME, SKELETON, ZOMBIE vb. olusturur");
        this.msg(commandSender, "&f/mob create <id> <mob-turu> &7- Ozel ID ile olusturur");
        this.msg(commandSender, "&f/mob addspawner <id> &7- Ayni moba yeni spawner ekler");
        this.msg(commandSender, "&f/mob removespawner <id> &7- 10 bloktaki en yakin spawneri siler");
        this.msg(commandSender, "&f/mob spawn <id> &7- Baktigin yerde test eder");
        this.msg(commandSender, "&f/mob delete <id> &7- Mob configini siler");
        this.msg(commandSender, "&f/mob list &7- Moblari listeler");
        this.msg(commandSender, "&f/mob reload &7- Configleri yeniden okur");
    }

    private void msg(CommandSender commandSender, String string) {
        commandSender.sendMessage(ChatColor.translateAlternateColorCodes('&', string));
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (stringArray.length == 1) {
            return this.filter(Arrays.asList("create", "addspawner", "removespawner", "spawn", "delete", "list", "reload"), stringArray[0]);
        }
        if (stringArray.length == 2) {
            if (stringArray[0].equalsIgnoreCase("create")) {
                return this.filter(this.entityNames(), stringArray[1]);
            }
            if (Arrays.asList("addspawner", "removespawner", "spawn", "delete").contains(stringArray[0].toLowerCase(Locale.ROOT))) {
                ArrayList<String> arrayList = new ArrayList<String>();
                for (MobDefinition mobDefinition : this.manager.getDefinitions()) {
                    arrayList.add(mobDefinition.id);
                }
                return this.filter(arrayList, stringArray[1]);
            }
        }
        if (stringArray.length == 3 && stringArray[0].equalsIgnoreCase("create")) {
            return this.filter(this.entityNames(), stringArray[2]);
        }
        return Collections.emptyList();
    }

    private List<String> entityNames() {
        ArrayList<String> arrayList = new ArrayList<String>();
        for (EntityType entityType : EntityType.values()) {
            if (!entityType.isAlive() || !entityType.isSpawnable()) continue;
            arrayList.add(entityType.name());
        }
        Collections.sort(arrayList);
        return arrayList;
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
