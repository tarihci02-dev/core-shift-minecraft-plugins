package com.coreshift.dungeonmobs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

public final class MobManager {
   private static final long NEVER = Long.MAX_VALUE;
   private final DungeonMobsPlugin plugin;
   private final File mobsFolder;
   private final Map<String, MobDefinition> definitions = new HashMap<>();
   private final Map<String, MobManager.MobRuntime> runtimeByMobId = new HashMap<>();
   private final List<MobManager.MobRuntime> runtimeList = new ArrayList<>();
   private final Map<MobManager.ChunkKey, List<MobManager.SpawnerRuntime>> spawnersByChunk = new HashMap<>();
   private final Map<UUID, MobManager.TrackedMob> trackedById = new HashMap<>();
   private final Map<MobManager.ChunkKey, Set<UUID>> unloadedTrackedByChunk = new HashMap<>();
   private final NamespacedKey previousMobKey;
   private final NamespacedKey previousSpawnerKey;
   private final NamespacedKey mobIdKey;
   private final NamespacedKey spawnerIdKey;
   private final Random random = new Random();
   private final Attribute maxHealthAttribute;
   private BukkitTask task;
   private boolean initialEntityIndexDone;
   private long nextWakeAt;
   private long nextCleanupAt;

   public MobManager(DungeonMobsPlugin var1) {
      this.plugin = var1;
      this.mobsFolder = new File(var1.getDataFolder(), "mobs");
      this.mobIdKey = new NamespacedKey(var1, "mob_id");
      this.spawnerIdKey = new NamespacedKey(var1, "spawner_id");
      String previous = var1.getConfig().getString("migration.previous-namespace", "").trim();
      this.previousMobKey = previous.isEmpty() ? null : NamespacedKey.fromString(previous + ":mob_id");
      this.previousSpawnerKey = previous.isEmpty() ? null : NamespacedKey.fromString(previous + ":spawner_id");
      this.maxHealthAttribute = this.resolveMaxHealthAttribute();
      long var2 = System.currentTimeMillis();
      this.nextWakeAt = var2;
      this.nextCleanupAt = var2 + this.cleanupIntervalMillis();
   }

   public void start() {
      this.stop();
      long var1 = OptimizedCaps.limitInterval(1L, this.plugin.getConfig().getLong("scheduler-period-ticks", 20L));
      this.task = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::tick, var1, var1);
   }

   public void stop() {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }
   }

   public void reloadAll() {
      if (!this.mobsFolder.exists() && !this.mobsFolder.mkdirs()) {
         this.plugin.getLogger().warning("mobs klasoru olusturulamadi: " + this.mobsFolder.getAbsolutePath());
      }

      this.createExampleIfMissing();
      long var1 = System.currentTimeMillis();
      HashMap var3 = new HashMap<>(this.runtimeByMobId);
      this.definitions.clear();
      this.runtimeByMobId.clear();
      this.runtimeList.clear();
      this.spawnersByChunk.clear();
      File[] var4 = this.mobsFolder.listFiles((var0, var1x) -> var1x.toLowerCase(Locale.ROOT).endsWith(".yml"));
      if (var4 != null) {
         for (File var8 : var4) {
            if (!var8.getName().startsWith("_")) {
               try {
                  MobDefinition var9 = this.loadDefinition(var8);
                  MobManager.MobRuntime var10 = this.buildRuntime(var9, (MobManager.MobRuntime)var3.get(var9.id), var1);
                  this.definitions.put(var9.id, var9);
                  this.runtimeByMobId.put(var9.id, var10);
                  this.runtimeList.add(var10);
               } catch (Exception var11) {
                  this.plugin.getLogger().warning(var8.getName() + " yuklenemedi: " + var11.getMessage());
               }
            }
         }
      }

      this.rebindTrackedToCurrentDefinitions();
      if (!this.initialEntityIndexDone) {
         this.indexLoadedEntitiesOnce();
         this.initialEntityIndexDone = true;
      }

      this.nextCleanupAt = var1 + this.cleanupIntervalMillis();
      this.recomputeNextWake(var1);
   }

   public int getDefinitionCount() {
      return this.definitions.size();
   }

   public Collection<MobDefinition> getDefinitions() {
      return new ArrayList<>(this.definitions.values());
   }

   public MobDefinition getDefinition(String var1) {
      return var1 == null ? null : this.definitions.get(var1.toLowerCase(Locale.ROOT));
   }

   public String createOrAddSpawner(String var1, EntityType var2, Location var3) throws IOException {
      String var4 = sanitizeId(var1);
      File var5 = new File(this.mobsFolder, var4 + ".yml");
      YamlConfiguration var6 = YamlConfiguration.loadConfiguration(var5);
      if (var5.exists()) {
         String var7 = var6.getString("entity", var2.name());
         if (!var7.equalsIgnoreCase(var2.name())) {
            throw new IllegalArgumentException("Bu mob zaten " + var7 + " entity tipiyle kayitli.");
         }
      } else {
         this.writeDefaultMob(var6, var4, var2);
      }

      String var8 = nextSpawnerId(var6, var4);
      writeSpawner(var6, var8, var3);
      var6.save(var5);
      this.reloadSingleDefinition(var4);
      return var8;
   }

   public String addSpawner(String var1, Location var2) throws IOException {
      String var3 = sanitizeId(var1);
      File var4 = new File(this.mobsFolder, var3 + ".yml");
      if (!var4.exists()) {
         throw new IllegalArgumentException("Mob bulunamadi: " + var3);
      } else {
         YamlConfiguration var5 = YamlConfiguration.loadConfiguration(var4);
         String var6 = nextSpawnerId(var5, var3);
         writeSpawner(var5, var6, var2);
         var5.save(var4);
         this.reloadSingleDefinition(var3);
         return var6;
      }
   }

   public String removeNearestSpawner(String var1, Location var2, double var3) throws IOException {
      MobDefinition var5 = this.getDefinition(var1);
      if (var5 == null) {
         throw new IllegalArgumentException("Mob bulunamadi: " + var1);
      } else {
         MobDefinition.SpawnerPoint var6 = null;
         double var7 = Double.MAX_VALUE;
         String var9 = var2.getWorld().getName();

         for (MobDefinition.SpawnerPoint var11 : var5.spawners) {
            if (var11.world.equals(var9)) {
               double var12 = var11.x - var2.getX();
               double var14 = var11.y - var2.getY();
               double var16 = var11.z - var2.getZ();
               double var18 = var12 * var12 + var14 * var14 + var16 * var16;
               if (var18 < var7) {
                  var7 = var18;
                  var6 = var11;
               }
            }
         }

         if (var6 != null && !(var7 > var3 * var3)) {
            YamlConfiguration var20 = YamlConfiguration.loadConfiguration(var5.file);
            var20.set("spawners." + var6.id, null);
            var20.save(var5.file);
            String var21 = var6.id;
            this.reloadSingleDefinition(var5.id);
            return var21;
         } else {
            throw new IllegalArgumentException("Yakinda bu moba ait spawner yok (maks. " + (int)var3 + " blok).");
         }
      }
   }

   public boolean deleteMob(String var1) {
      String var2 = sanitizeId(var1);
      MobDefinition var3 = this.getDefinition(var2);
      if (var3 == null) {
         return false;
      } else {
         boolean var4 = var3.file.delete();
         if (var4) {
            this.removeDefinitionFromMemory(var2);
         }

         return var4;
      }
   }

   public LivingEntity spawnForTest(String var1, Location var2) {
      MobDefinition var3 = this.getDefinition(var1);
      if (var3 == null) {
         throw new IllegalArgumentException("Mob bulunamadi: " + var1);
      } else {
         return this.spawnMob(var3, null, var2);
      }
   }

   private static String readTag(LivingEntity entity, NamespacedKey key, NamespacedKey previousKey) {
      var tags = entity.getPersistentDataContainer();
      String value = tags.get(key, PersistentDataType.STRING);
      if (value == null && previousKey != null) {
         value = tags.get(previousKey, PersistentDataType.STRING);
         if (value != null) {
            tags.set(key, PersistentDataType.STRING, value);
         }
      }
      return value;
   }

   public MobDefinition getDefinitionFromEntity(LivingEntity var1) {
      String var2 = readTag(var1, mobIdKey, previousMobKey);
      return this.getDefinition(var2);
   }

   public void onMobDeath(LivingEntity var1) {
      this.removeTracked(var1.getUniqueId());
   }

   public void onChunkUnload(Chunk var1) {
      MobManager.ChunkKey var2 = new MobManager.ChunkKey(var1.getWorld().getName(), var1.getX(), var1.getZ());
      List<SpawnerRuntime> var3 = this.spawnersByChunk.get(var2);
      if (var3 != null) {
         for (MobManager.SpawnerRuntime var5 : var3) {
            var5.nextSpawnAt = Long.MAX_VALUE;
         }
      }

      HashSet var10 = null;

      for (Entity var8 : var1.getEntities()) {
         MobManager.TrackedMob var9 = this.trackedById.get(var8.getUniqueId());
         if (var9 != null) {
            var9.entity = null;
            var9.unloadedChunk = var2;
            if (var10 == null) {
               var10 = new HashSet();
            }

            var10.add(var9.uuid);
         }
      }

      if (var10 != null && !var10.isEmpty()) {
         this.unloadedTrackedByChunk.put(var2, var10);
      }
   }

   public void onChunkLoad(Chunk var1) {
      long var2 = System.currentTimeMillis();
      MobManager.ChunkKey var4 = new MobManager.ChunkKey(var1.getWorld().getName(), var1.getX(), var1.getZ());
      List<SpawnerRuntime> var5 = this.spawnersByChunk.get(var4);
      if (var5 != null) {
         for (MobManager.SpawnerRuntime var7 : var5) {
            var7.world = var1.getWorld();
            var7.nextSpawnAt = var2 + this.inactiveRetryMillis();
         }
      }

      Set<UUID> var14 = this.unloadedTrackedByChunk.remove(var4);

      for (Entity var10 : var1.getEntities()) {
         if (var10 instanceof LivingEntity var11) {
            String var12 = readTag(var11, mobIdKey, previousMobKey);
            if (var12 == null) continue;
            String var13 = readTag(var11, spawnerIdKey, previousSpawnerKey);
            if (var13 != null) {
               this.bindLoadedEntity(var11, var12, var13);
               if (var14 != null) {
                  var14.remove(var11.getUniqueId());
               }
            }
         }
      }

      if (var14 != null) {
         for (UUID var17 : var14) {
            this.removeTracked(var17);
         }
      }

      this.recomputeNextWake(var2);
   }

   public void onWorldLoad(World var1) {
      long var2 = System.currentTimeMillis();

      for (MobManager.MobRuntime var5 : this.runtimeList) {
         for (MobManager.SpawnerRuntime var7 : var5.spawners) {
            if (var7.worldName.equals(var1.getName())) {
               var7.world = var1;
               if (var1.isChunkLoaded(var7.chunkX, var7.chunkZ)) {
                  var7.nextSpawnAt = var2 + this.inactiveRetryMillis();
               }
            }
         }
      }

      this.recomputeNextWake(var2);
   }

   public void onWorldUnload(World var1) {
      for (MobManager.MobRuntime var3 : this.runtimeList) {
         for (MobManager.SpawnerRuntime var5 : var3.spawners) {
            if (var5.world == var1) {
               var5.world = null;
               var5.nextSpawnAt = Long.MAX_VALUE;
            }
         }
      }
   }

   public Random getRandom() {
      return this.random;
   }

   private void tick() {
      long var1 = System.currentTimeMillis();
      if (var1 >= this.nextWakeAt) {
         if (var1 >= this.nextCleanupAt) {
            this.cleanupTrackedReferences();
            this.nextCleanupAt = var1 + this.cleanupIntervalMillis();
         }

         long var3 = this.nextCleanupAt;
         long var5 = this.inactiveRetryMillis();

         for (MobManager.MobRuntime var8 : this.runtimeList) {
            MobDefinition var9 = var8.definition;
            if (var9.enabled) {
               for (MobManager.SpawnerRuntime var11 : var8.spawners) {
                  long var12 = var11.nextSpawnAt;
                  if (var12 != Long.MAX_VALUE) {
                     if (var1 < var12) {
                        if (var12 < var3) {
                           var3 = var12;
                        }
                     } else if (var8.activeCount >= var9.maxAlive) {
                        var11.nextSpawnAt = var1 + var9.intervalMillis;
                        if (var11.nextSpawnAt < var3) {
                           var3 = var11.nextSpawnAt;
                        }
                     } else {
                        World var14 = var11.world;
                        if (var14 != null && var14.isChunkLoaded(var11.chunkX, var11.chunkZ)) {
                           if (!this.hasPlayerInActivationRange(var11, var9.activationRange)) {
                              var11.nextSpawnAt = var1 + var5;
                              if (var11.nextSpawnAt < var3) {
                                 var3 = var11.nextSpawnAt;
                              }
                           } else {
                              int var15 = OptimizedCaps.minSpawn(var9.spawnAmount, var9.maxAlive - var8.activeCount);
                              Location var16 = new Location(var14, var11.x, var11.y, var11.z, var11.yaw, var11.pitch);

                              for (int var17 = 0; var17 < var15; var17++) {
                                 LivingEntity var18 = this.spawnMob(var9, var11.id, var16);
                                 if (var18 != null) {
                                    this.trackSpawnedEntity(var18, var8, var11.id);
                                 }
                              }

                              var11.nextSpawnAt = var1 + var9.intervalMillis;
                              if (var11.nextSpawnAt < var3) {
                                 var3 = var11.nextSpawnAt;
                              }
                           }
                        } else {
                           var11.nextSpawnAt = Long.MAX_VALUE;
                        }
                     }
                  }
               }
            }
         }

         this.nextWakeAt = var3;
      }
   }

   private boolean hasPlayerInActivationRange(SpawnerRuntime spawner, double range) {
      if (range <= 0) {
         return true;
      }
      double rangeSquared = range * range;
      for (Player player : spawner.world.getPlayers()) {
         if (player.isDead() || !player.isValid()) {
            continue;
         }
         double dx = player.getX() - spawner.x;
         double dy = player.getY() - spawner.y;
         double dz = player.getZ() - spawner.z;
         if (dx * dx + dy * dy + dz * dz <= rangeSquared) {
            return true;
         }
      }
      return false;
   }

   private LivingEntity spawnMob(MobDefinition var1, String var2, Location var3) {
      Entity var4 = var3.getWorld().spawnEntity(var3, var1.entityType);
      if (var4 instanceof LivingEntity var5) {
         if (this.maxHealthAttribute != null) {
            AttributeInstance var6 = var5.getAttribute(this.maxHealthAttribute);
            if (var6 != null) {
               var6.setBaseValue(var1.health);
               var5.setHealth(var1.health);
            }
         }

         var5.setCustomName(var1.displayName);
         var5.setCustomNameVisible(true);
         var5.setPersistent(var1.persistent);
         var5.setRemoveWhenFarAway(var1.removeWhenFarAway);
         var5.setCanPickupItems(var1.canPickupItems);
         var5.getPersistentDataContainer().set(this.mobIdKey, PersistentDataType.STRING, var1.id);
         if (var2 != null) {
            var5.getPersistentDataContainer().set(this.spawnerIdKey, PersistentDataType.STRING, var2);
         }

         EntityEquipment var7 = var5.getEquipment();
         if (var7 != null) {
            this.setEquipment(var7, var1);
         }

         return var5;
      } else {
         var4.remove();
         this.plugin.getLogger().warning(var1.id + " canli bir entity degil: " + var1.entityType.name());
         return null;
      }
   }

   private void setEquipment(EntityEquipment var1, MobDefinition var2) {
      if (var2.helmet != null) {
         var1.setHelmet(var2.helmet.clone());
      }

      if (var2.chestplate != null) {
         var1.setChestplate(var2.chestplate.clone());
      }

      if (var2.leggings != null) {
         var1.setLeggings(var2.leggings.clone());
      }

      if (var2.boots != null) {
         var1.setBoots(var2.boots.clone());
      }

      if (var2.mainHand != null) {
         var1.setItemInMainHand(var2.mainHand.clone());
      }

      if (var2.offHand != null) {
         var1.setItemInOffHand(var2.offHand.clone());
      }

      var1.setHelmetDropChance(0.0F);
      var1.setChestplateDropChance(0.0F);
      var1.setLeggingsDropChance(0.0F);
      var1.setBootsDropChance(0.0F);
      var1.setItemInMainHandDropChance(0.0F);
      var1.setItemInOffHandDropChance(0.0F);
   }

   private MobDefinition loadDefinition(File var1) {
      return MobDefinition.load(var1, this.defaultActivationRange(), this.plugin.getLogger());
   }

   private MobManager.MobRuntime buildRuntime(MobDefinition var1, MobManager.MobRuntime var2, long var3) {
      MobManager.MobRuntime var5 = new MobManager.MobRuntime(var1);
      HashMap var6 = new HashMap();
      if (var2 != null) {
         for (MobManager.SpawnerRuntime var8 : var2.spawners) {
            var6.put(var8.id, var8);
         }
      }

      for (MobDefinition.SpawnerPoint var18 : var1.spawners) {
         World var9 = Bukkit.getWorld(var18.world);
         int var10 = (int)Math.floor(var18.x) >> 4;
         int var11 = (int)Math.floor(var18.z) >> 4;
         boolean var12 = var9 != null && var9.isChunkLoaded(var10, var11);
         MobManager.SpawnerRuntime var13 = (MobManager.SpawnerRuntime)var6.get(var18.id);
         long var14 = var12 ? var3 + var1.firstDelaySeconds * 1000L : Long.MAX_VALUE;
         if (var13 != null && var12 && var13.nextSpawnAt != Long.MAX_VALUE) {
            var14 = var13.nextSpawnAt;
         }

         MobManager.SpawnerRuntime var16 = new MobManager.SpawnerRuntime(var5, var18, var9, var14);
         var5.spawners.add(var16);
         this.spawnersByChunk.computeIfAbsent(new MobManager.ChunkKey(var18.world, var16.chunkX, var16.chunkZ), var0 -> new ArrayList<>()).add(var16);
      }

      return var5;
   }

   private void reloadSingleDefinition(String var1) {
      String var2 = sanitizeId(var1);
      File var3 = new File(this.mobsFolder, var2 + ".yml");
      if (!var3.exists()) {
         this.removeDefinitionFromMemory(var2);
      } else {
         MobDefinition var4 = this.loadDefinition(var3);
         MobManager.MobRuntime var5 = this.runtimeByMobId.get(var2);
         MobManager.MobRuntime var6 = this.buildRuntime(var4, var5, System.currentTimeMillis());
         this.definitions.put(var2, var4);
         this.runtimeByMobId.put(var2, var6);
         int var7 = var5 == null ? -1 : this.runtimeList.indexOf(var5);
         if (var7 >= 0) {
            this.runtimeList.set(var7, var6);
         } else {
            this.runtimeList.add(var6);
         }

         for (MobManager.TrackedMob var9 : this.trackedById.values()) {
            if (var9.mobId.equals(var2)) {
               var9.runtime = var6;
               var6.activeCount++;
            }
         }

         this.rebuildSpawnerChunkIndex();
         this.recomputeNextWake(System.currentTimeMillis());
      }
   }

   private void removeDefinitionFromMemory(String var1) {
      this.definitions.remove(var1);
      MobManager.MobRuntime var2 = this.runtimeByMobId.remove(var1);
      if (var2 != null) {
         this.runtimeList.remove(var2);
      }

      this.rebuildSpawnerChunkIndex();
      Iterator var3 = this.trackedById.entrySet().iterator();

      while (var3.hasNext()) {
         MobManager.TrackedMob var4 = (MobManager.TrackedMob)((Entry)var3.next()).getValue();
         if (var4.mobId.equals(var1)) {
            var3.remove();
         }
      }

      this.recomputeNextWake(System.currentTimeMillis());
   }

   private void rebuildSpawnerChunkIndex() {
      this.spawnersByChunk.clear();

      for (MobManager.MobRuntime var2 : this.runtimeList) {
         for (MobManager.SpawnerRuntime var4 : var2.spawners) {
            this.spawnersByChunk.computeIfAbsent(new MobManager.ChunkKey(var4.worldName, var4.chunkX, var4.chunkZ), var0 -> new ArrayList<>()).add(var4);
         }
      }
   }

   private void rebindTrackedToCurrentDefinitions() {
      for (MobManager.MobRuntime var2 : this.runtimeList) {
         var2.activeCount = 0;
      }

      Iterator var4 = this.trackedById.entrySet().iterator();

      while (var4.hasNext()) {
         MobManager.TrackedMob var5 = (MobManager.TrackedMob)((Entry)var4.next()).getValue();
         MobManager.MobRuntime var3 = this.runtimeByMobId.get(var5.mobId);
         if (var3 == null) {
            var4.remove();
         } else {
            var5.runtime = var3;
            var3.activeCount++;
         }
      }
   }

   private void indexLoadedEntitiesOnce() {
      for (World var2 : Bukkit.getWorlds()) {
         for (Entity var4 : var2.getEntities()) {
            if (var4 instanceof LivingEntity var5) {
               String var6 = readTag(var5, mobIdKey, previousMobKey);
               if (var6 == null) continue;
               String var7 = readTag(var5, spawnerIdKey, previousSpawnerKey);
               if (var7 != null) {
                  this.bindLoadedEntity(var5, var6, var7);
               }
            }
         }
      }
   }

   private void bindLoadedEntity(LivingEntity var1, String var2, String var3) {
      MobManager.MobRuntime var4 = this.runtimeByMobId.get(var2.toLowerCase(Locale.ROOT));
      if (var4 != null) {
         UUID var5 = var1.getUniqueId();
         MobManager.TrackedMob var6 = this.trackedById.get(var5);
         if (var6 != null) {
            if (var6.runtime != var4) {
               if (var6.runtime != null && var6.runtime.activeCount > 0) {
                  var6.runtime.activeCount--;
               }

               var6.runtime = var4;
               var4.activeCount++;
            }

            var6.entity = var1;
            var6.unloadedChunk = null;
         } else {
            MobManager.TrackedMob var7 = new MobManager.TrackedMob(var5, var4.definition.id, var3, var4, var1);
            this.trackedById.put(var5, var7);
            var4.activeCount++;
         }
      }
   }

   private void trackSpawnedEntity(LivingEntity var1, MobManager.MobRuntime var2, String var3) {
      UUID var4 = var1.getUniqueId();
      MobManager.TrackedMob var5 = new MobManager.TrackedMob(var4, var2.definition.id, var3, var2, var1);
      MobManager.TrackedMob var6 = this.trackedById.put(var4, var5);
      if (var6 == null) {
         var2.activeCount++;
      } else if (var6.runtime != var2) {
         if (var6.runtime != null && var6.runtime.activeCount > 0) {
            var6.runtime.activeCount--;
         }

         var2.activeCount++;
      }
   }

   private void removeTracked(UUID var1) {
      MobManager.TrackedMob var2 = this.trackedById.remove(var1);
      if (var2 != null) {
         if (var2.runtime != null && var2.runtime.activeCount > 0) {
            var2.runtime.activeCount--;
         }

         if (var2.unloadedChunk != null) {
            Set var3 = this.unloadedTrackedByChunk.get(var2.unloadedChunk);
            if (var3 != null) {
               var3.remove(var1);
               if (var3.isEmpty()) {
                  this.unloadedTrackedByChunk.remove(var2.unloadedChunk);
               }
            }
         }
      }
   }

   private void cleanupTrackedReferences() {
      ArrayList<UUID> var1 = null;

      for (MobManager.TrackedMob var3 : this.trackedById.values()) {
         LivingEntity var4 = var3.entity;
         if (var4 != null && (var4.isDead() || !var4.isValid())) {
            if (var1 == null) {
               var1 = new ArrayList();
            }

            var1.add(var3.uuid);
         }
      }

      if (var1 != null) {
         for (UUID var6 : var1) {
            this.removeTracked(var6);
         }
      }
   }

   private void recomputeNextWake(long var1) {
      long var3 = this.nextCleanupAt > var1 ? this.nextCleanupAt : var1;

      for (MobManager.MobRuntime var6 : this.runtimeList) {
         if (var6.definition.enabled) {
            for (MobManager.SpawnerRuntime var8 : var6.spawners) {
               if (var8.nextSpawnAt < var3) {
                  var3 = var8.nextSpawnAt;
               }
            }
         }
      }

      this.nextWakeAt = var3;
   }

   private long cleanupIntervalMillis() {
      return OptimizedCaps.limitInterval(0L, this.plugin.getConfig().getLong("cleanup-interval-seconds", 10L)) * 1000L;
   }

   private long inactiveRetryMillis() {
      return OptimizedCaps.limitInterval(0L, this.plugin.getConfig().getLong("inactive-retry-seconds", 2L)) * 1000L;
   }

   private double defaultActivationRange() {
      return Math.max(0.0, this.plugin.getConfig().getDouble("default-activation-range", 48.0));
   }

   private void createExampleIfMissing() {
      File var1 = new File(this.mobsFolder, "_ornek_zombi.yml");
      if (!var1.exists()) {
         YamlConfiguration var2 = new YamlConfiguration();
         this.writeDefaultMob(var2, "ornek_zombi", EntityType.ZOMBIE);
         var2.set("enabled", false);

         try {
            var2.save(var1);
         } catch (IOException var4) {
            this.plugin.getLogger().warning("Ornek mob config olusturulamadi: " + var4.getMessage());
         }
      }
   }

   private void writeDefaultMob(YamlConfiguration var1, String var2, EntityType var3) {
      var1.set("enabled", true);
      var1.set("entity", var3.name());
      var1.set("display-name", "&c&l" + var2);
      var1.set("health", 200.0);
      var1.set("behavior.persistent", false);
      var1.set("behavior.remove-when-far-away", true);
      var1.set("behavior.can-pickup-items", false);
      var1.set("spawn.interval.hours", 0);
      var1.set("spawn.interval.minutes", 5);
      var1.set("spawn.interval.seconds", 0);
      var1.set("spawn.first-delay-seconds", 3);
      var1.set("spawn.amount", 1);
      var1.set("spawn.max-alive", 20);
      var1.set("spawn.activation-range", this.defaultActivationRange());
      var1.set("equipment.helmet.material", "IRON_HELMET");
      var1.set("equipment.helmet.name", "&7Zindan Migferi");
      var1.set("equipment.helmet.enchants.PROTECTION", 2);
      var1.set("equipment.chestplate.material", "IRON_CHESTPLATE");
      var1.set("equipment.leggings.material", "IRON_LEGGINGS");
      var1.set("equipment.boots.material", "IRON_BOOTS");
      var1.set("equipment.main-hand.material", "IRON_SWORD");
      var1.set("equipment.off-hand.material", "AIR");
      var1.set("rewards.clear-vanilla-drops", true);
      var1.set("rewards.experience.min", 0);
      var1.set("rewards.experience.max", 0);
      var1.set("rewards.command", "");
      var1.set("rewards.drops.nera_coin.material", "SUNFLOWER");
      var1.set("rewards.drops.nera_coin.amount", 1);
      var1.set("rewards.drops.nera_coin.name", "&bɴᴇʀᴀ ᴄᴏɪɴ");
      var1.set("rewards.drops.nera_coin.lore", Arrays.asList("&aʙᴜ ᴄᴏɪɴɪ ᴋᴜʟʟᴀɴᴍᴀᴋ ɪᴄɪɴ", "&a/ᴡᴀʀᴘ ᴛᴀᴋᴀꜱ"));
      var1.set("rewards.drops.nera_coin.chance", 100.0);
      var1.set("rewards.drops.emerald_block.material", "EMERALD_BLOCK");
      var1.set("rewards.drops.emerald_block.amount", 3);
      var1.set("rewards.drops.emerald_block.chance", 100.0);
      var1.set("rewards.drops.diamond_block.material", "DIAMOND_BLOCK");
      var1.set("rewards.drops.diamond_block.amount", 3);
      var1.set("rewards.drops.diamond_block.chance", 100.0);
      var1.set("rewards.drops.experience_bottle.material", "EXPERIENCE_BOTTLE");
      var1.set("rewards.drops.experience_bottle.amount", 3);
      var1.set("rewards.drops.experience_bottle.chance", 100.0);
      var1.set("rewards.drops.iron_block.material", "IRON_BLOCK");
      var1.set("rewards.drops.iron_block.amount", 3);
      var1.set("rewards.drops.iron_block.chance", 100.0);
   }

   private static void writeSpawner(YamlConfiguration var0, String var1, Location var2) {
      String var3 = "spawners." + var1 + ".";
      var0.set(var3 + "world", var2.getWorld().getName());
      var0.set(var3 + "x", var2.getX());
      var0.set(var3 + "y", var2.getY());
      var0.set(var3 + "z", var2.getZ());
      var0.set(var3 + "yaw", var2.getYaw());
      var0.set(var3 + "pitch", var2.getPitch());
   }

   private static String sanitizeId(String var0) {
      String var1 = var0.toLowerCase(Locale.ROOT);
      if (!var1.matches("[a-z0-9_-]{1,32}")) {
         throw new IllegalArgumentException("Mob ID sadece a-z, 0-9, _ ve - icerebilir (maks. 32 karakter).");
      } else {
         return var1;
      }
   }

   private static String nextSpawnerId(YamlConfiguration var0, String var1) {
      int var2 = 1;

      while (var0.contains("spawners." + var1 + "_" + var2)) {
         var2++;
      }

      return var1 + "_" + var2;
   }

   private Attribute resolveMaxHealthAttribute() {
      for (String var4 : new String[]{"MAX_HEALTH", "GENERIC_MAX_HEALTH"}) {
         try {
            if (Attribute.class.getField(var4).get(null) instanceof Attribute var6) {
               return var6;
            }
         } catch (ReflectiveOperationException var7) {
         }
      }

      this.plugin.getLogger().warning("MAX_HEALTH/GENERIC_MAX_HEALTH attribute bulunamadi; ozel can uygulanamayabilir.");
      return null;
   }

   private static final class ChunkKey {
      final String world;
      final int x;
      final int z;

      ChunkKey(String var1, int var2, int var3) {
         this.world = var1;
         this.x = var2;
         this.z = var3;
      }

      @Override
      public boolean equals(Object var1) {
         if (this == var1) {
            return true;
         } else {
            return !(var1 instanceof MobManager.ChunkKey var2) ? false : this.x == var2.x && this.z == var2.z && this.world.equals(var2.world);
         }
      }

      @Override
      public int hashCode() {
         int var1 = this.world.hashCode();
         var1 = 31 * var1 + this.x;
         return 31 * var1 + this.z;
      }
   }

   private static final class MobRuntime {
      MobDefinition definition;
      final List<MobManager.SpawnerRuntime> spawners = new ArrayList<>();
      int activeCount;

      MobRuntime(MobDefinition var1) {
         this.definition = var1;
      }
   }

   private static final class SpawnerRuntime {
      final MobManager.MobRuntime mob;
      final String id;
      final String worldName;
      final double x;
      final double y;
      final double z;
      final float yaw;
      final float pitch;
      final int chunkX;
      final int chunkZ;
      World world;
      long nextSpawnAt;

      SpawnerRuntime(MobManager.MobRuntime var1, MobDefinition.SpawnerPoint var2, World var3, long var4) {
         this.mob = var1;
         this.id = var2.id;
         this.worldName = var2.world;
         this.x = var2.x;
         this.y = var2.y;
         this.z = var2.z;
         this.yaw = var2.yaw;
         this.pitch = var2.pitch;
         this.chunkX = (int)Math.floor(var2.x) >> 4;
         this.chunkZ = (int)Math.floor(var2.z) >> 4;
         this.world = var3;
         this.nextSpawnAt = var4;
      }
   }

   private static final class TrackedMob {
      final UUID uuid;
      final String mobId;
      final String spawnerId;
      MobManager.MobRuntime runtime;
      LivingEntity entity;
      MobManager.ChunkKey unloadedChunk;

      TrackedMob(UUID var1, String var2, String var3, MobManager.MobRuntime var4, LivingEntity var5) {
         this.uuid = var1;
         this.mobId = var2;
         this.spawnerId = var3;
         this.runtime = var4;
         this.entity = var5;
      }
   }
}
