package com.coreshift.maden;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.scheduler.BukkitTask;

public final class PendingManager implements Listener {
   private final CoreShiftMaden plugin;
   private final File file;
   private final Map<String, Map<Long, PendingBlock>> pendingByWorld = new HashMap<>();
   private final Map<String, Set<PendingBlock>> pendingByMine = new HashMap<>();
   private final Map<String, Map<Long, Set<PendingBlock>>> pendingByChunk = new HashMap<>();
   private final PriorityQueue<PendingManager.RestoreEntry> restoreQueue = new PriorityQueue<>(
      Comparator.comparingLong(PendingManager.RestoreEntry::nextAttemptAt)
   );
   private final ArrayDeque<PendingBlock> bedrockQueue = new ArrayDeque<>();
   private final Map<String, BlockData> blockDataCache = new HashMap<>();
   private final Set<String> invalidBlockDataWarnings = new HashSet<>();
   private final Map<String, Long> missingWorldSince = new HashMap<>();
   private final ExecutorService ioExecutor;
   private final AtomicBoolean saveQueued = new AtomicBoolean(false);
   private final AtomicLong savedGeneration = new AtomicLong(0L);
   private long generation;
   private int pendingCount;
   private BukkitTask tickTask;
   private BukkitTask saveTask;
   private int restoreMaxPerTick;
   private int bedrockMaxPerTick;
   private long missingWorldExpireMillis;
   private long invalidDataRetryMillis;
   private long saveIntervalTicks;

   public PendingManager(CoreShiftMaden var1) {
      this.plugin = var1;
      this.file = new File(var1.getDataFolder(), "pending.yml");
      this.ioExecutor = Executors.newSingleThreadExecutor(var0 -> {
         Thread var1x = new Thread(var0, "CoreShiftMaden-PendingIO");
         var1x.setDaemon(true);
         return var1x;
      });
      this.loadSettingsValues();
      this.load();
      Bukkit.getPluginManager().registerEvents(this, var1);
      this.scheduleTasks();
   }

   public boolean contains(Block var1) {
      String var2 = worldKey(var1.getWorld().getName());
      Map<Long, PendingBlock> var3 = this.pendingByWorld.get(var2);
      return var3 != null && var3.containsKey(PendingBlock.packPosition(var1.getX(), var1.getY(), var1.getZ()));
   }

   public void add(PendingBlock var1, BlockData var2) {
      PendingBlock var3 = this.get(var1.world(), var1.positionKey());
      if (var3 != null) {
         this.removeInternal(var3, false);
      }

      this.index(var1);
      if (!this.blockDataCache.containsKey(var1.blockData())) {
         this.blockDataCache.put(var1.blockData(), var2.clone());
      }
      this.restoreQueue.offer(new PendingManager.RestoreEntry(var1, var1.restoreAt()));
      this.bedrockQueue.offer(var1);
      this.markDirty();
   }

   public void restoreMineNow(String var1) {
      Set<PendingBlock> var2 = this.pendingByMine.get(mineKey(var1));
      if (var2 != null && !var2.isEmpty()) {
         long var3 = System.currentTimeMillis();
         boolean var5 = false;

         for (PendingBlock var7 : List.copyOf(var2)) {
            if (this.get(var7.world(), var7.positionKey()) == var7) {
               this.removeInternal(var7, false);
               PendingBlock var8 = new PendingBlock(var7.mine(), var7.world(), var7.x(), var7.y(), var7.z(), var7.blockData(), var3);
               this.index(var8);
               this.restoreQueue.offer(new PendingManager.RestoreEntry(var8, var3));
               var5 = true;
            }
         }

         if (var5) {
            this.markDirty();
         }
      }
   }

   public int size() {
      return this.pendingCount;
   }

   public void reloadSettings() {
      this.loadSettingsValues();
      if (this.saveTask != null) {
         this.saveTask.cancel();
      }

      this.saveTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::requestAsyncSave, this.saveIntervalTicks, this.saveIntervalTicks);
   }

   public void shutdown() {
      if (this.tickTask != null) {
         this.tickTask.cancel();
      }

      if (this.saveTask != null) {
         this.saveTask.cancel();
      }

      List var1 = this.snapshot();
      long var2 = this.generation;

      try {
         Future var4 = this.ioExecutor.submit(() -> this.writeSnapshot(var1));
         if (Boolean.TRUE.equals(var4.get(30L, TimeUnit.SECONDS))) {
            this.savedGeneration.set(Math.max(this.savedGeneration.get(), var2));
         }
      } catch (Exception var13) {
         this.plugin.getLogger().severe("pending.yml kapanista kaydedilemedi: " + var13.getMessage());
      } finally {
         this.ioExecutor.shutdown();

         try {
            if (!this.ioExecutor.awaitTermination(5L, TimeUnit.SECONDS)) {
               this.ioExecutor.shutdownNow();
            }
         } catch (InterruptedException var12) {
            Thread.currentThread().interrupt();
            this.ioExecutor.shutdownNow();
         }
      }
   }

   @EventHandler
   public void onChunkLoad(ChunkLoadEvent var1) {
      String var2 = worldKey(var1.getWorld().getName());
      Map<Long, Set<PendingBlock>> var3 = this.pendingByChunk.get(var2);
      if (var3 != null) {
         Set<PendingBlock> var4 = var3.get(chunkKey(var1.getChunk().getX(), var1.getChunk().getZ()));
         if (var4 != null && !var4.isEmpty()) {
            long var5 = System.currentTimeMillis();

            for (PendingBlock var8 : var4) {
               if (var5 >= var8.restoreAt()) {
                  this.restoreQueue.offer(new PendingManager.RestoreEntry(var8, var5));
               } else {
                  this.bedrockQueue.offer(var8);
               }
            }
         }
      }
   }

   @EventHandler
   public void onWorldLoad(WorldLoadEvent var1) {
      String var2 = worldKey(var1.getWorld().getName());
      this.missingWorldSince.remove(var2);
      Map<Long, PendingBlock> var3 = this.pendingByWorld.get(var2);
      if (var3 != null && !var3.isEmpty()) {
         long var4 = System.currentTimeMillis();

         for (PendingBlock var7 : var3.values()) {
            if (var4 >= var7.restoreAt()) {
               this.restoreQueue.offer(new PendingManager.RestoreEntry(var7, var4));
            } else if (var1.getWorld().isChunkLoaded(var7.x() >> 4, var7.z() >> 4)) {
               this.bedrockQueue.offer(var7);
            }
         }
      }
   }

   private void scheduleTasks() {
      this.tickTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 1L, 1L);
      this.saveTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::requestAsyncSave, this.saveIntervalTicks, this.saveIntervalTicks);
   }

   private void tick() {
      this.processBedrockQueue();
      this.processRestoreQueue();
   }

   private void processBedrockQueue() {
      long var1 = System.currentTimeMillis();
      int var3 = 0;

      while (var3 < this.bedrockMaxPerTick && !this.bedrockQueue.isEmpty()) {
         PendingBlock var4 = this.bedrockQueue.poll();
         var3++;
         PendingBlock var5 = this.get(var4.world(), var4.positionKey());
         if (var5 == var4) {
            if (var1 >= var4.restoreAt()) {
               this.restoreQueue.offer(new PendingManager.RestoreEntry(var4, var1));
            } else {
               World var6 = Bukkit.getWorld(var4.world());
               if (var6 != null) {
                  int var7 = var4.x() >> 4;
                  int var8 = var4.z() >> 4;
                  if (var6.isChunkLoaded(var7, var8)) {
                     Block var9 = var6.getBlockAt(var4.x(), var4.y(), var4.z());
                     if (var9.getType() != Material.BEDROCK) {
                        var9.setType(Material.BEDROCK, false);
                     }
                  }
               }
            }
         }
      }
   }

   private void processRestoreQueue() {
      long var1 = System.currentTimeMillis();
      int var3 = 0;

      while (var3 < this.restoreMaxPerTick && !this.restoreQueue.isEmpty()) {
         PendingManager.RestoreEntry var4 = this.restoreQueue.peek();
         if (var4.nextAttemptAt() > var1) {
            break;
         }

         this.restoreQueue.poll();
         var3++;
         PendingBlock var5 = var4.block();
         PendingBlock var6 = this.get(var5.world(), var5.positionKey());
         if (var6 == var5) {
            if (var1 < var5.restoreAt()) {
               this.restoreQueue.offer(new PendingManager.RestoreEntry(var5, var5.restoreAt()));
            } else {
               String var7 = worldKey(var5.world());
               World var8 = Bukkit.getWorld(var5.world());
               if (var8 == null) {
                  long var9 = this.missingWorldSince.computeIfAbsent(var7, var2 -> var1);
                  if (var1 - var9 >= this.missingWorldExpireMillis) {
                     this.plugin.getLogger().warning("Bulunamayan dunya nedeniyle pending kaydi temizlendi: " + var5.debugKey());
                     this.removeInternal(var5, true);
                  } else {
                     this.restoreQueue.offer(new PendingManager.RestoreEntry(var5, var9 + this.missingWorldExpireMillis));
                  }
               } else {
                  this.missingWorldSince.remove(var7);
                  int var11 = var5.x() >> 4;
                  int var10 = var5.z() >> 4;
                  if (var8.isChunkLoaded(var11, var10)) {
                     if (this.restore(var5, var8)) {
                        this.removeInternal(var5, true);
                     } else {
                        this.restoreQueue.offer(new PendingManager.RestoreEntry(var5, var1 + this.invalidDataRetryMillis));
                     }
                  }
               }
            }
         }
      }
   }

   private boolean restore(PendingBlock var1, World var2) {
      try {
         BlockData var3 = this.blockDataCache.get(var1.blockData());
         if (var3 == null) {
            var3 = Bukkit.createBlockData(var1.blockData());
            this.blockDataCache.put(var1.blockData(), var3);
         }

         var2.getBlockAt(var1.x(), var1.y(), var1.z()).setBlockData(var3.clone(), false);
         return true;
      } catch (IllegalArgumentException var4) {
         if (this.invalidBlockDataWarnings.add(var1.blockData())) {
            this.plugin.getLogger().warning("Gecersiz BlockData geri yuklenemedi: " + var1.blockData());
         }

         return false;
      }
   }

   private void load() {
      if (this.file.exists()) {
         YamlConfiguration var1 = YamlConfiguration.loadConfiguration(this.file);
         var var2 = new ArrayList<>(var1.getMapList("blocks"));
         if (!var2.isEmpty()) {
            for (Map var9 : var2) {
               PendingBlock var10 = this.parseRow(var9);
               if (var10 != null) {
                  this.indexLoaded(var10);
               }
            }
         } else {
            ConfigurationSection var3 = var1.getConfigurationSection("blocks");
            if (var3 != null) {
               for (String var5 : var3.getKeys(false)) {
                  ConfigurationSection var6 = var3.getConfigurationSection(var5);
                  if (var6 != null) {
                     PendingBlock var7 = new PendingBlock(
                        var6.getString("mine", ""),
                        var6.getString("world", ""),
                        var6.getInt("x"),
                        var6.getInt("y"),
                        var6.getInt("z"),
                        var6.getString("block-data", ""),
                        var6.getLong("restore-at")
                     );
                     if (!var7.mine().isBlank() && !var7.world().isBlank() && !var7.blockData().isBlank()) {
                        this.indexLoaded(var7);
                     }
                  }
               }
            }
         }
      }
   }

   private PendingBlock parseRow(Map<?, ?> var1) {
      String var2 = stringValue(var1.get("mine"));
      String var3 = stringValue(var1.get("world"));
      String var4 = stringValue(var1.get("block-data"));
      return var2 != null && var3 != null && var4 != null && !var2.isBlank() && !var3.isBlank() && !var4.isBlank()
         ? new PendingBlock(
            var2, var3, intValue(var1.get("x"), 0), intValue(var1.get("y"), 0), intValue(var1.get("z"), 0), var4, longValue(var1.get("restore-at"), 0L)
         )
         : null;
   }

   private void indexLoaded(PendingBlock var1) {
      PendingBlock var2 = this.get(var1.world(), var1.positionKey());
      if (var2 != null) {
         this.removeInternal(var2, false);
      }

      this.index(var1);
      this.restoreQueue.offer(new PendingManager.RestoreEntry(var1, var1.restoreAt()));
      World var3 = Bukkit.getWorld(var1.world());
      if (var3 != null && System.currentTimeMillis() < var1.restoreAt() && var3.isChunkLoaded(var1.x() >> 4, var1.z() >> 4)) {
         this.bedrockQueue.offer(var1);
      }
   }

   private void index(PendingBlock var1) {
      String var2 = worldKey(var1.world());
      this.pendingByWorld.computeIfAbsent(var2, var0 -> new HashMap<>()).put(var1.positionKey(), var1);
      this.pendingByMine.computeIfAbsent(mineKey(var1.mine()), var0 -> new HashSet<>()).add(var1);
      this.pendingByChunk
         .computeIfAbsent(var2, var0 -> new HashMap<>())
         .computeIfAbsent(chunkKey(var1.x() >> 4, var1.z() >> 4), var0 -> new HashSet<>())
         .add(var1);
      this.pendingCount++;
   }

   private boolean removeInternal(PendingBlock var1, boolean var2) {
      String var3 = worldKey(var1.world());
      Map var4 = this.pendingByWorld.get(var3);
      if (var4 != null && var4.get(var1.positionKey()) == var1) {
         var4.remove(var1.positionKey());
         if (var4.isEmpty()) {
            this.pendingByWorld.remove(var3);
         }

         String var5 = mineKey(var1.mine());
         Set var6 = this.pendingByMine.get(var5);
         if (var6 != null) {
            var6.remove(var1);
            if (var6.isEmpty()) {
               this.pendingByMine.remove(var5);
            }
         }

         Map var7 = this.pendingByChunk.get(var3);
         if (var7 != null) {
            long var8 = chunkKey(var1.x() >> 4, var1.z() >> 4);
            Set var10 = (Set)var7.get(var8);
            if (var10 != null) {
               var10.remove(var1);
               if (var10.isEmpty()) {
                  var7.remove(var8);
               }
            }

            if (var7.isEmpty()) {
               this.pendingByChunk.remove(var3);
            }
         }

         this.pendingCount--;
         if (var2) {
            this.markDirty();
         }

         return true;
      } else {
         return false;
      }
   }

   private PendingBlock get(String var1, long var2) {
      Map var4 = this.pendingByWorld.get(worldKey(var1));
      return var4 == null ? null : (PendingBlock)var4.get(var2);
   }

   private void markDirty() {
      this.generation++;
   }

   private void requestAsyncSave() {
      if (this.generation > this.savedGeneration.get()) {
         if (this.saveQueued.compareAndSet(false, true)) {
            if (this.ioExecutor.isShutdown()) {
               this.saveQueued.set(false);
            } else {
               List var1 = this.snapshot();
               long var2 = this.generation;
               this.ioExecutor.execute(() -> {
                  try {
                     if (this.writeSnapshot(var1)) {
                        this.savedGeneration.accumulateAndGet(var2, Math::max);
                     }
                  } finally {
                     this.saveQueued.set(false);
                  }
               });
            }
         }
      }
   }

   private List<PendingBlock> snapshot() {
      ArrayList var1 = new ArrayList(this.pendingCount);

      for (Map var3 : this.pendingByWorld.values()) {
         var1.addAll(var3.values());
      }

      return var1;
   }

   private boolean writeSnapshot(List<PendingBlock> var1) {
      try {
         ArrayList var2 = new ArrayList(var1.size());

         for (PendingBlock var4 : var1) {
            LinkedHashMap var5 = new LinkedHashMap();
            var5.put("mine", var4.mine());
            var5.put("world", var4.world());
            var5.put("x", var4.x());
            var5.put("y", var4.y());
            var5.put("z", var4.z());
            var5.put("block-data", var4.blockData());
            var5.put("restore-at", var4.restoreAt());
            var2.add(var5);
         }

         YamlConfiguration var11 = new YamlConfiguration();
         var11.set("format-version", 2);
         var11.set("blocks", var2);
         String var12 = var11.saveToString();
         Path var13 = this.file.toPath();
         Path var6 = var13.getParent();
         if (var6 != null) {
            Files.createDirectories(var6);
         }

         Path var7 = var13.resolveSibling(var13.getFileName() + ".tmp");
         Files.writeString(var7, var12, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

         try {
            Files.move(var7, var13, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException var9) {
            Files.move(var7, var13, StandardCopyOption.REPLACE_EXISTING);
         }

         return true;
      } catch (RuntimeException | IOException var10) {
         this.plugin.getLogger().severe("pending.yml kaydedilemedi: " + var10.getMessage());
         return false;
      }
   }

   private void loadSettingsValues() {
      this.restoreMaxPerTick = OptimizedCaps.limit(2, this.plugin.getConfig().getInt("performance.restore-max-per-tick", 200));
      this.bedrockMaxPerTick = OptimizedCaps.limit(3, this.plugin.getConfig().getInt("performance.bedrock-max-per-tick", 500));
      this.missingWorldExpireMillis = Math.max(1L, this.plugin.getConfig().getLong("performance.missing-world-expire-minutes", 10L)) * 60000L;
      this.invalidDataRetryMillis = Math.max(10L, this.plugin.getConfig().getLong("performance.invalid-blockdata-retry-seconds", 60L)) * 1000L;
      long var1 = Math.max(30L, this.plugin.getConfig().getLong("performance.pending-save-interval-seconds", 30L));
      this.saveIntervalTicks = var1 * 20L;
   }

   private static String worldKey(String var0) {
      return var0.toLowerCase(Locale.ROOT);
   }

   private static String mineKey(String var0) {
      return var0.toLowerCase(Locale.ROOT);
   }

   private static long chunkKey(int var0, int var1) {
      return (long)var0 << 32 ^ var1 & 4294967295L;
   }

   private static String stringValue(Object var0) {
      return var0 == null ? null : String.valueOf(var0);
   }

   private static int intValue(Object var0, int var1) {
      if (var0 instanceof Number var2) {
         return var2.intValue();
      } else {
         try {
            return Integer.parseInt(String.valueOf(var0));
         } catch (Exception var3) {
            return var1;
         }
      }
   }

   private static long longValue(Object var0, long var1) {
      if (var0 instanceof Number var3) {
         return var3.longValue();
      } else {
         try {
            return Long.parseLong(String.valueOf(var0));
         } catch (Exception var4) {
            return var1;
         }
      }
   }

   private record RestoreEntry(PendingBlock block, long nextAttemptAt) {
   }
}
