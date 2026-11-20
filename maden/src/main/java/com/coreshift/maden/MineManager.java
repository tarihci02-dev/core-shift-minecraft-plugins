package com.coreshift.maden;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class MineManager {
   private static final long MAX_CREATE_SCAN_BLOCKS = 500000L;
   private final CoreShiftMaden plugin;
   private final File minesFolder;
   private final SelectionProvider selectionProvider;
   private final Map<String, Mine> mines = new LinkedHashMap<>();
   private final Map<String, Map<Long, List<Mine>>> chunkIndex = new HashMap<>();

   public MineManager(CoreShiftMaden var1, SelectionProvider var2) {
      this.plugin = var1;
      this.selectionProvider = var2;
      this.minesFolder = new File(var1.getDataFolder(), "mines");
      if (!this.minesFolder.exists() && !this.minesFolder.mkdirs()) {
         var1.getLogger().warning("Maden klasoru olusturulamadi: " + this.minesFolder.getAbsolutePath());
      }

      this.reloadAll();
   }

   public void reloadAll() {
      this.mines.clear();
      this.chunkIndex.clear();
      File[] var1 = this.minesFolder.listFiles((var0, var1x) -> var1x.toLowerCase(Locale.ROOT).endsWith(".yml"));
      if (var1 != null) {
         ArrayList<File> var2 = new ArrayList<>(List.of(var1));
         var2.sort(Comparator.comparing(File::getName));

         for (File var4 : var2) {
            String var5 = var4.getName().substring(0, var4.getName().length() - 4);

            try {
               Mine var6 = new Mine(var5, var4);
               this.mines.put(var5.toLowerCase(Locale.ROOT), var6);
               this.indexMine(var6);
            } catch (Exception var7) {
               this.plugin.getLogger().severe("Maden yuklenemedi " + var4.getName() + ": " + var7.getMessage());
            }
         }
      }
   }

   public Mine getByBlock(Block var1) {
      String var2 = var1.getWorld().getName().toLowerCase(Locale.ROOT);
      Map<Long, List<Mine>> var3 = this.chunkIndex.get(var2);
      if (var3 == null) {
         return null;
      } else {
         List<Mine> var4 = var3.get(chunkKey(var1.getX() >> 4, var1.getZ() >> 4));
         if (var4 == null) {
            return null;
         } else {
            int var5 = var1.getX();
            int var6 = var1.getY();
            int var7 = var1.getZ();
            String var8 = var1.getWorld().getName();

            for (Mine var10 : var4) {
               if (var10.contains(var8, var5, var6, var7)) {
                  return var10;
               }
            }

            return null;
         }
      }
   }

   public Mine get(String var1) {
      return var1 == null ? null : this.mines.get(var1.toLowerCase(Locale.ROOT));
   }

   public Collection<Mine> getAll() {
      return List.copyOf(this.mines.values());
   }

   public Mine createFromSelection(Player var1, String var2) throws SelectionException, IOException {
      if (this.selectionProvider == null) {
         throw new SelectionException(SelectionException.Reason.UNAVAILABLE);
      } else {
         String var3 = this.sanitize(var2);
         if (var3.isBlank()) {
            throw new IllegalArgumentException("Gecersiz maden adi.");
         } else if (this.get(var3) != null) {
            throw new IllegalArgumentException("Bu isimde bir maden zaten var.");
         } else {
            SelectionBounds var4 = this.selectionProvider.getCuboid(var1);
            long var5 = (long)var4.maxX() - var4.minX() + 1L;
            long var7 = (long)var4.maxY() - var4.minY() + 1L;
            long var9 = (long)var4.maxZ() - var4.minZ() + 1L;

            long var11;
            try {
               var11 = Math.multiplyExact(Math.multiplyExact(var5, var7), var9);
            } catch (ArithmeticException var21) {
               throw new IllegalArgumentException("Secim cok buyuk.");
            }

            if (var11 > MAX_CREATE_SCAN_BLOCKS) {
               throw new IllegalArgumentException("Secim cok buyuk: " + var11 + " blok. Maksimum " + MAX_CREATE_SCAN_BLOCKS + " blok sec.");
            } else {
               World var13 = var1.getWorld();
               if (!var13.getName().equalsIgnoreCase(var4.worldName())) {
                  throw new IllegalArgumentException("WorldEdit secimi bulundugun dunyada olmali.");
               } else {
                  List<String> var14 = new ArrayList<>();
                  TreeSet<String> var15 = new TreeSet<>();

                  for (int var16 = var4.minX(); var16 <= var4.maxX(); var16++) {
                     for (int var17 = var4.minZ(); var17 <= var4.maxZ(); var17++) {
                        for (int var18 = var4.minY(); var18 <= var4.maxY(); var18++) {
                           Material var19 = var13.getBlockAt(var16, var18, var17).getType();
                           if (isOre(var19)) {
                              var14.add(var16 + "," + var18 + "," + var17 + "," + var19.name());
                              var15.add(var19.name());
                           }
                        }
                     }
                  }

                  if (var14.isEmpty()) {
                     throw new IllegalArgumentException("Secilen bolgede kaydedilecek maden blogu bulunamadi.");
                  } else {
                     File var22 = new File(this.minesFolder, var3 + ".yml");
                     YamlConfiguration var23 = new YamlConfiguration();
                     var23.set("region.world", var4.worldName());
                     var23.set("region.min.x", var4.minX());
                     var23.set("region.min.y", var4.minY());
                     var23.set("region.min.z", var4.minZ());
                     var23.set("region.max.x", var4.maxX());
                     var23.set("region.max.y", var4.maxY());
                     var23.set("region.max.z", var4.maxZ());
                     var23.set("registered-blocks", true);
                     var23.set("block-count", var14.size());
                     var23.set("mineable-blocks", new ArrayList(var15));
                     var23.set("blocks", var14);
                     var23.setComments(
                        "blocks",
                        List.of(
                           "Bu liste /maden create sirasinda secili alandaki cevherlerden otomatik olusur.",
                           "Format: x,y,z,MATERIAL. Runtime'da tum bolge taranmaz; sadece bu koordinatlar kontrol edilir."
                        )
                     );
                     var23.set("respawn.hours", 0);
                     var23.set("respawn.minutes", 5);
                     var23.set("respawn.seconds", 0);
                     var23.setComments("respawn", List.of("Kirilmis cevherin BEDROCK olarak kaldiktan sonra geri gelme suresi."));
                     var23.set("natural-drops", false);
                     var23.setComments("natural-drops", List.of("false = normal Minecraft dropu kapali; asagidaki oduller kullanilir."));

                     for (String var26 : var15) {
                        String var20 = "rewards." + var26;
                        var23.set(var20 + ".chance", 100.0);
                        var23.set(var20 + ".xp", 0);
                        var23.set(var20 + ".items", List.of());
                        var23.set(var20 + ".commands", List.of());
                        var23.set(var20 + ".message", "");
                     }

                     var23.set("rewards.DEFAULT.chance", 100.0);
                     var23.set("rewards.DEFAULT.xp", 0);
                     var23.set("rewards.DEFAULT.items", List.of());
                     var23.set("rewards.DEFAULT.commands", List.of());
                     var23.set("rewards.DEFAULT.message", "");
                     var23.setComments(
                        "rewards",
                        List.of(
                           "Her cevher turunun odulu burada ayarlanabilir.",
                           "items ornegi: [{material: DIAMOND, min: 1, max: 3, chance: 100.0}]",
                           "commands ornegi: [\"eco give %player% 500\"]",
                           "Placeholderlar: %player%, %mine%, %block%, %x%, %y%, %z%"
                        )
                     );
                     var23.save(var22);
                     Mine var25 = new Mine(var3, var22);
                     this.mines.put(var3.toLowerCase(Locale.ROOT), var25);
                     this.indexMine(var25);
                     return var25;
                  }
               }
            }
         }
      }
   }

   public boolean delete(String var1) {
      Mine var2 = this.get(var1);
      if (var2 == null) {
         return false;
      } else if (var2.getFile().exists() && !var2.getFile().delete()) {
         return false;
      } else {
         this.mines.remove(var1.toLowerCase(Locale.ROOT));
         this.rebuildSpatialIndex();
         return true;
      }
   }

   private void rebuildSpatialIndex() {
      this.chunkIndex.clear();

      for (Mine var2 : this.mines.values()) {
         this.indexMine(var2);
      }
   }

   private void indexMine(Mine var1) {
      String var2 = var1.getWorldName().toLowerCase(Locale.ROOT);
      Map<Long, List<Mine>> var3 = this.chunkIndex.computeIfAbsent(var2, var0 -> new HashMap<>());
      if (var1.isRegisteredMode()) {
         for (Long var11 : var1.getRegisteredChunkKeys()) {
            var3.computeIfAbsent(var11, var0 -> new ArrayList<>()).add(var1);
         }
      } else {
         int var4 = var1.getMinX() >> 4;
         int var5 = var1.getMaxX() >> 4;
         int var6 = var1.getMinZ() >> 4;
         int var7 = var1.getMaxZ() >> 4;

         for (int var8 = var4; var8 <= var5; var8++) {
            for (int var9 = var6; var9 <= var7; var9++) {
               var3.computeIfAbsent(chunkKey(var8, var9), var0 -> new ArrayList<>()).add(var1);
            }
         }
      }
   }

   private static boolean isOre(Material var0) {
      if (var0 == null) {
         return false;
      } else {
         String var1 = var0.name();
         return var1.endsWith("_ORE") || var1.equals("ANCIENT_DEBRIS") || var1.equals("GILDED_BLACKSTONE");
      }
   }

   private static long chunkKey(int var0, int var1) {
      return (long)var0 << 32 ^ var1 & 4294967295L;
   }

   private String sanitize(String var1) {
      return var1.replaceAll("[^a-zA-Z0-9_\\-]", "").toLowerCase(Locale.ROOT);
   }
}
