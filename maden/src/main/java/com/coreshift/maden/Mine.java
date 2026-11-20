package com.coreshift.maden;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class Mine {
   private final String name;
   private final File file;
   private YamlConfiguration config;
   private String worldName;
   private int minX;
   private int minY;
   private int minZ;
   private int maxX;
   private int maxY;
   private int maxZ;
   private double respawnHours;
   private long respawnMillis;
   private boolean naturalDrops;
   private EnumSet<Material> mineableBlocks;
   private Map<Material, RewardDefinition> rewards;
   private RewardDefinition defaultReward;
   private boolean registeredMode;
   private Map<Long, Material> registeredBlocks;
   private Set<Long> registeredChunkKeys;

   public Mine(String var1, File var2) {
      this.name = var1;
      this.file = var2;
      this.reload();
   }

   public void reload() {
      this.config = YamlConfiguration.loadConfiguration(this.file);
      this.worldName = this.config.getString("region.world", "world");
      int var1 = this.config.getInt("region.min.x");
      int var2 = this.config.getInt("region.min.y");
      int var3 = this.config.getInt("region.min.z");
      int var4 = this.config.getInt("region.max.x");
      int var5 = this.config.getInt("region.max.y");
      int var6 = this.config.getInt("region.max.z");
      this.minX = Math.min(var1, var4);
      this.minY = Math.min(var2, var5);
      this.minZ = Math.min(var3, var6);
      this.maxX = Math.max(var1, var4);
      this.maxY = Math.max(var2, var5);
      this.maxZ = Math.max(var3, var6);
      ConfigurationSection var7 = this.config.getConfigurationSection("respawn");
      if (var7 != null) {
         double var8 = Math.max(0.0, var7.getDouble("hours", 0.0));
         double var10 = Math.max(0.0, var7.getDouble("minutes", 0.0));
         double var12 = Math.max(0.0, var7.getDouble("seconds", 0.0));
         double var14 = var8 * 3600.0 + var10 * 60.0 + var12;
         this.respawnMillis = Math.max(0L, (long)(var14 * 1000.0));
         this.respawnHours = var14 / 3600.0;
      } else {
         this.respawnHours = Math.max(0.0, this.config.getDouble("respawn-hours", 2.0));
         this.respawnMillis = (long)(this.respawnHours * 60.0 * 60.0 * 1000.0);
      }

      this.naturalDrops = this.config.getBoolean("natural-drops", false);
      EnumSet var20 = EnumSet.noneOf(Material.class);

      for (String var22 : this.config.getStringList("mineable-blocks")) {
         Material var11 = matchMaterial(var22);
         if (var11 != null && var11.isBlock()) {
            var20.add(var11);
         }
      }

      List<String> var21 = this.config.getStringList("blocks");
      this.registeredMode = !var21.isEmpty() || this.config.getBoolean("registered-blocks", false);
      HashMap var23 = new HashMap(Math.max(16, (int)(var21.size() / 0.75F) + 1));
      HashSet var24 = new HashSet();

      for (String var13 : var21) {
         if (var13 != null && !var13.trim().isEmpty()) {
            String[] var26 = var13.split(",", 4);
            if (var26.length == 4) {
               try {
                  int var15 = Integer.parseInt(var26[0].trim());
                  int var16 = Integer.parseInt(var26[1].trim());
                  int var17 = Integer.parseInt(var26[2].trim());
                  Material var18 = matchMaterial(var26[3]);
                  if (var18 != null && var18.isBlock()) {
                     var23.put(PendingBlock.packPosition(var15, var16, var17), var18);
                     var24.add(chunkKey(var15 >> 4, var17 >> 4));
                     var20.add(var18);
                  }
               } catch (NumberFormatException var19) {
               }
            }
         }
      }

      this.registeredBlocks = var23;
      this.registeredChunkKeys = var24;
      this.mineableBlocks = var20;
      this.parseRewards();
   }

   private void parseRewards() {
      EnumMap var1 = new EnumMap(Material.class);
      RewardDefinition var2 = null;
      ConfigurationSection var3 = this.config.getConfigurationSection("rewards");
      if (var3 != null) {
         for (String var5 : var3.getKeys(false)) {
            ConfigurationSection var6 = var3.getConfigurationSection(var5);
            if (var6 != null) {
               RewardDefinition var7 = this.parseReward(var6);
               if (var5.equalsIgnoreCase("DEFAULT")) {
                  var2 = var7;
               } else {
                  Material var8 = matchMaterial(var5);
                  if (var8 != null) {
                     var1.put((Enum)var8, var7);
                  }
               }
            }
         }
      }

      this.rewards = var1;
      this.defaultReward = var2;
   }

   private RewardDefinition parseReward(ConfigurationSection var1) {
      double var2 = clamp(var1.getDouble("chance", 100.0));
      int var4 = Math.max(0, var1.getInt("xp", 0));
      ArrayList var5 = new ArrayList();

      for (Map var7 : var1.getMapList("items")) {
         Object var8 = var7.containsKey("material") ? var7.get("material") : "AIR";
         Material var9 = matchMaterial(String.valueOf(var8));
         if (var9 != null && !var9.isAir()) {
            int var10 = Math.max(1, intValue(var7.get("min"), 1));
            int var11 = Math.max(var10, intValue(var7.get("max"), var10));
            double var12 = clamp(doubleValue(var7.get("chance"), 100.0));
            var5.add(new RewardDefinition.RewardItem(var9, var10, var11, var12));
         }
      }

      List var14 = var1.getStringList("commands");
      String var15 = var1.getString("message", "");
      return new RewardDefinition(var2, var4, var5, var14, var15);
   }

   public boolean contains(Location var1) {
      World var2 = var1.getWorld();
      return var2 != null && this.contains(var2.getName(), var1.getBlockX(), var1.getBlockY(), var1.getBlockZ());
   }

   public boolean contains(String var1, int var2, int var3, int var4) {
      if (var1 == null || !var1.equalsIgnoreCase(this.worldName)) {
         return false;
      } else {
         return this.registeredMode
            ? this.registeredBlocks.containsKey(PendingBlock.packPosition(var2, var3, var4))
            : var2 >= this.minX && var2 <= this.maxX && var3 >= this.minY && var3 <= this.maxY && var4 >= this.minZ && var4 <= this.maxZ;
      }
   }

   public boolean containsRegistered(String var1, int var2, int var3, int var4) {
      return var1 != null && var1.equalsIgnoreCase(this.worldName) && this.registeredBlocks.containsKey(PendingBlock.packPosition(var2, var3, var4));
   }

   public Material getRegisteredMaterial(int var1, int var2, int var3) {
      return this.registeredBlocks.get(PendingBlock.packPosition(var1, var2, var3));
   }

   public boolean canMine(Material var1) {
      if (var1 == null || var1.isAir() || "BEDROCK".equals(var1.name())) {
         return false;
      } else {
         return this.registeredMode ? this.mineableBlocks.contains(var1) : this.mineableBlocks.isEmpty() || this.mineableBlocks.contains(var1);
      }
   }

   public RewardDefinition getReward(Material var1) {
      RewardDefinition var2 = this.rewards.get(var1);
      return var2 != null ? var2 : this.defaultReward;
   }

   public boolean isRegisteredMode() {
      return this.registeredMode;
   }

   public int getRegisteredBlockCount() {
      return this.registeredBlocks.size();
   }

   public Set<Long> getRegisteredChunkKeys() {
      return this.registeredChunkKeys;
   }

   private static Material matchMaterial(String var0) {
      return var0 == null ? null : Material.matchMaterial(var0.trim().toUpperCase(Locale.ROOT));
   }

   private static long chunkKey(int var0, int var1) {
      return (long)var0 << 32 ^ var1 & 4294967295L;
   }

   private static double clamp(double var0) {
      return Math.max(0.0, Math.min(100.0, var0));
   }

   private static int intValue(Object var0, int var1) {
      if (var0 instanceof Number) {
         Number var2 = (Number) var0;
         return var2.intValue();
      } else {
         try {
            return Integer.parseInt(String.valueOf(var0));
         } catch (Exception var3) {
            return var1;
         }
      }
   }

   private static double doubleValue(Object var0, double var1) {
      if (var0 instanceof Number) {
         Number var3 = (Number) var0;
         return var3.doubleValue();
      } else {
         try {
            return Double.parseDouble(String.valueOf(var0));
         } catch (Exception var4) {
            return var1;
         }
      }
   }

   public String getName() {
      return this.name;
   }

   public File getFile() {
      return this.file;
   }

   public YamlConfiguration getConfig() {
      return this.config;
   }

   public String getWorldName() {
      return this.worldName;
   }

   public double getRespawnHours() {
      return this.respawnHours;
   }

   public long getRespawnMillis() {
      return this.respawnMillis;
   }

   public boolean isNaturalDrops() {
      return this.naturalDrops;
   }

   public int getMinX() {
      return this.minX;
   }

   public int getMinY() {
      return this.minY;
   }

   public int getMinZ() {
      return this.minZ;
   }

   public int getMaxX() {
      return this.maxX;
   }

   public int getMaxY() {
      return this.maxY;
   }

   public int getMaxZ() {
      return this.maxZ;
   }
}
