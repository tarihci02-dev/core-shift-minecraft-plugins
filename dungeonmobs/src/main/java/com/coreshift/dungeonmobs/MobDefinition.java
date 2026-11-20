package com.coreshift.dungeonmobs;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class MobDefinition {
   public final String id;
   public final File file;
   public final boolean enabled;
   public final EntityType entityType;
   public final String displayName;
   public final double health;
   public final boolean persistent;
   public final boolean removeWhenFarAway;
   public final boolean canPickupItems;
   public final boolean clearVanillaDrops;
   public final long intervalMillis;
   public final int spawnAmount;
   public final int maxAlive;
   public final int firstDelaySeconds;
   public final double activationRange;
   public final int expMin;
   public final int expMax;
   public final ItemStack helmet;
   public final ItemStack chestplate;
   public final ItemStack leggings;
   public final ItemStack boots;
   public final ItemStack mainHand;
   public final ItemStack offHand;
   public final List<MobDefinition.SpawnerPoint> spawners;
   public final List<MobDefinition.DropReward> dropRewards;
   public final List<MobDefinition.CommandReward> commandRewards;

   private MobDefinition(
      String var1,
      File var2,
      boolean var3,
      EntityType var4,
      String var5,
      double var6,
      boolean var8,
      boolean var9,
      boolean var10,
      boolean var11,
      long var12,
      int var14,
      int var15,
      int var16,
      double var17,
      int var19,
      int var20,
      ItemStack var21,
      ItemStack var22,
      ItemStack var23,
      ItemStack var24,
      ItemStack var25,
      ItemStack var26,
      List<MobDefinition.SpawnerPoint> var27,
      List<MobDefinition.DropReward> var28,
      List<MobDefinition.CommandReward> var29
   ) {
      this.id = var1;
      this.file = var2;
      this.enabled = var3;
      this.entityType = var4;
      this.displayName = var5;
      this.health = var6;
      this.persistent = var8;
      this.removeWhenFarAway = var9;
      this.canPickupItems = var10;
      this.clearVanillaDrops = var11;
      this.intervalMillis = var12;
      this.spawnAmount = var14;
      this.maxAlive = var15;
      this.firstDelaySeconds = var16;
      this.activationRange = var17;
      this.expMin = var19;
      this.expMax = var20;
      this.helmet = var21;
      this.chestplate = var22;
      this.leggings = var23;
      this.boots = var24;
      this.mainHand = var25;
      this.offHand = var26;
      this.spawners = Collections.unmodifiableList(var27);
      this.dropRewards = Collections.unmodifiableList(var28);
      this.commandRewards = Collections.unmodifiableList(var29);
   }

   public static MobDefinition load(File var0, double var1, Logger var3) {
      YamlConfiguration var4 = YamlConfiguration.loadConfiguration(var0);
      var4.set("rewards.commands.__direct.command", var4.getString("rewards.command", ""));
      var4.set("rewards.commands.__direct.chance", 100.0);
      String var5 = stripExtension(var0.getName()).toLowerCase(Locale.ROOT);

      EntityType var6;
      try {
         var6 = EntityType.valueOf(var4.getString("entity", "ZOMBIE").toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException var24) {
         throw new IllegalArgumentException("Gecersiz entity: " + var4.getString("entity"));
      }

      if (var6.isAlive() && var6.isSpawnable()) {
         int var7 = Math.max(0, var4.getInt("spawn.interval.hours", 0));
         int var8 = Math.max(0, var4.getInt("spawn.interval.minutes", 5));
         int var9 = Math.max(0, var4.getInt("spawn.interval.seconds", 0));
         long var10 = (var7 * 3600L + var8 * 60L + var9) * 1000L;
         var10 = Math.max(1000L, var10);
         ArrayList var12 = new ArrayList();
         ConfigurationSection var13 = var4.getConfigurationSection("spawners");
         if (var13 != null) {
            for (String var15 : var13.getKeys(false)) {
               String var16 = "spawners." + var15 + ".";
               String var17 = var4.getString(var16 + "world", "world");
               var12.add(
                  new MobDefinition.SpawnerPoint(
                     var15,
                     var17,
                     var4.getDouble(var16 + "x"),
                     var4.getDouble(var16 + "y"),
                     var4.getDouble(var16 + "z"),
                     (float)var4.getDouble(var16 + "yaw", 0.0),
                     (float)var4.getDouble(var16 + "pitch", 0.0)
                  )
               );
            }
         }

         ArrayList var26 = new ArrayList();
         ConfigurationSection var27 = var4.getConfigurationSection("rewards.drops");
         if (var27 != null) {
            for (String var30 : var27.getKeys(false)) {
               ConfigurationSection var18 = var27.getConfigurationSection(var30);
               if (var18 != null) {
                  ItemStack var19 = parseItem(var18, var3, var0.getName() + ":rewards.drops." + var30);
                  if (var19 != null) {
                     int var20 = Math.max(1, var18.getInt("amount-min", var19.getAmount()));
                     int var21 = Math.max(var20, var18.getInt("amount-max", var20));
                     double var22 = clampChance(var18.getDouble("chance", 100.0));
                     var26.add(new MobDefinition.DropReward(var19, var20, var21, var22));
                  }
               }
            }
         }

         ArrayList var29 = new ArrayList();
         ConfigurationSection var31 = var4.getConfigurationSection("rewards.commands");
         if (var31 != null) {
            for (String var33 : var31.getKeys(false)) {
               ConfigurationSection var34 = var31.getConfigurationSection(var33);
               if (var34 != null) {
                  String var35 = var34.getString("command", "").trim();
                  if (!var35.isEmpty()) {
                     var29.add(new MobDefinition.CommandReward(var35, clampChance(var34.getDouble("chance", 100.0))));
                  }
               }
            }
         }

         return new MobDefinition(
            var5,
            var0,
            var4.getBoolean("enabled", true),
            var6,
            color(var4.getString("display-name", "&c" + var5)),
            Math.max(1.0, var4.getDouble("health", 100.0)),
            var4.getBoolean("behavior.persistent", false),
            var4.getBoolean("behavior.remove-when-far-away", true),
            var4.getBoolean("behavior.can-pickup-items", false),
            var4.getBoolean("rewards.clear-vanilla-drops", true),
            var10,
            Math.max(1, var4.getInt("spawn.amount", 1)),
            Math.max(1, var4.getInt("spawn.max-alive", 20)),
            Math.max(0, var4.getInt("spawn.first-delay-seconds", 3)),
            Math.max(0.0, var4.getDouble("spawn.activation-range", var1)),
            Math.max(0, var4.getInt("rewards.experience.min", 0)),
            Math.max(0, var4.getInt("rewards.experience.max", 0)),
            parseItem(var4.getConfigurationSection("equipment.helmet"), var3, var0.getName() + ":equipment.helmet"),
            parseItem(var4.getConfigurationSection("equipment.chestplate"), var3, var0.getName() + ":equipment.chestplate"),
            parseItem(var4.getConfigurationSection("equipment.leggings"), var3, var0.getName() + ":equipment.leggings"),
            parseItem(var4.getConfigurationSection("equipment.boots"), var3, var0.getName() + ":equipment.boots"),
            parseItem(var4.getConfigurationSection("equipment.main-hand"), var3, var0.getName() + ":equipment.main-hand"),
            parseItem(var4.getConfigurationSection("equipment.off-hand"), var3, var0.getName() + ":equipment.off-hand"),
            var12,
            var26,
            var29
         );
      } else {
         throw new IllegalArgumentException("Entity canli ve spawnlanabilir olmali: " + var6.name());
      }
   }

   private static ItemStack parseItem(ConfigurationSection var0, Logger var1, String var2) {
      if (var0 == null) {
         return null;
      } else {
         String var3 = var0.getString("material", "AIR");
         Material var4 = Material.matchMaterial(var3);
         if (var4 != null && var4 != Material.AIR) {
            ItemStack var5 = new ItemStack(var4, Math.max(1, var0.getInt("amount", 1)));
            ItemMeta var6 = var5.getItemMeta();
            RewardCompat.applyLore(var6, var0);
            if (var6 != null) {
               String var7 = var0.getString("name", "");
               if (!var7.isEmpty()) {
                  var6.setDisplayName(color(var7));
               }

               int var8 = var0.getInt("custom-model-data", 0);
               if (var8 > 0) {
                  var6.setCustomModelData(var8);
               }

               ConfigurationSection var9 = var0.getConfigurationSection("enchants");
               if (var9 != null) {
                  for (String var12 : var9.getKeys(false)) {
                     Enchantment var13 = resolveEnchantment(var12);
                     if (var13 == null) {
                        if (var1 != null) {
                           var1.warning("Gecersiz enchant '" + var12 + "' atlandi (" + var2 + "). Ornek: PROTECTION veya minecraft:protection");
                        }
                     } else {
                        var6.addEnchant(var13, Math.max(1, var9.getInt(var12, 1)), true);
                     }
                  }
               }

               var5.setItemMeta(var6);
            }

            return var5;
         } else {
            return null;
         }
      }
   }

   private static Enchantment resolveEnchantment(String var0) {
      if (var0 == null) {
         return null;
      } else {
         String var1 = var0.trim();
         if (var1.isEmpty()) {
            return null;
         } else {
            NamespacedKey var2 = NamespacedKey.fromString(var1.toLowerCase(Locale.ROOT));
            if (var2 != null) {
               Enchantment var3 = Enchantment.getByKey(var2);
               if (var3 != null) {
                  return var3;
               }
            }

            return Enchantment.getByName(var1.toUpperCase(Locale.ROOT));
         }
      }
   }

   private static String stripExtension(String var0) {
      int var1 = var0.lastIndexOf(46);
      return var1 > 0 ? var0.substring(0, var1) : var0;
   }

   private static String color(String var0) {
      return ChatColor.translateAlternateColorCodes('&', var0 == null ? "" : var0);
   }

   private static double clampChance(double var0) {
      return Math.max(0.0, Math.min(100.0, var0));
   }

   public static final class CommandReward {
      public final String command;
      public final double chance;

      public CommandReward(String var1, double var2) {
         this.command = var1;
         this.chance = var2;
      }
   }

   public static final class DropReward {
      public final ItemStack item;
      public final int min;
      public final int max;
      public final double chance;

      public DropReward(ItemStack var1, int var2, int var3, double var4) {
         this.item = var1;
         this.min = var2;
         this.max = var3;
         this.chance = var4;
      }
   }

   public static final class SpawnerPoint {
      public final String id;
      public final String world;
      public final double x;
      public final double y;
      public final double z;
      public final float yaw;
      public final float pitch;

      public SpawnerPoint(String var1, String var2, double var3, double var5, double var7, float var9, float var10) {
         this.id = var1;
         this.world = var2;
         this.x = var3;
         this.y = var5;
         this.z = var7;
         this.yaw = var9;
         this.pitch = var10;
      }
   }
}
