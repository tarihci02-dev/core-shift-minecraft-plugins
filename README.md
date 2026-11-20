# Core Shift Minecraft eklentileri

Bu depo iki bağımsız Paper eklentisinin Java kaynaklarını ve derlenmiş sürümlerini içerir. Kaynaklar mevcut JAR dosyalarından geri kazanıldı ve Core Shift sürümleri için düzenlendi.

| Eklenti | Kullanım | JAR |
| --- | --- | --- |
| Maden 1.3.0 | Seçili alandaki cevherleri kaydeder. Kırılan cevherin geri gelme süresini, eşya ve deneyim ödüllerini maden başına ayarlayabilirsin. | [CoreShiftMaden-1.3.0.jar](dist/CoreShiftMaden-1.3.0.jar) |
| Dungeon Mobs 1.4.0 | Zindan yaratıklarının doğma noktalarını, oyuncuya yakınlık koşulunu, ekipmanını ve ödüllerini yönetir. | [CoreShiftDungeonMobs-1.4.0.jar](dist/CoreShiftDungeonMobs-1.4.0.jar) |

## Kurulum

Hedef sunucu aralığı Paper 1.16.5–26.3. Her eklenti için aynı JAR kullanılır. Sunucuyu kullandığın Paper sürümünün gerektirdiği Java sürümüyle başlat. Kullanacağın JAR dosyalarını sunucunun `plugins` klasörüne koy ve sunucuyu yeniden başlat. Maden oluşturmak için sunucu sürümüyle uyumlu WorldEdit veya FastAsyncWorldEdit gerekir.

- **Maden.** WorldEdit ile bir dikdörtgen prizma seç ve `/maden create demir` komutunu çalıştır. `plugins/CoreShiftMaden/mines/demir.yml` dosyasını düzenle ve `/maden reload` ile uygula.
- **Dungeon Mobs.** Doğma noktası için bir bloğa bak ve `/mob create muhafiz ZOMBIE` komutunu çalıştır. `plugins/CoreShiftDungeonMobs/mobs/muhafiz.yml` içinde doğma aralığını, canı ve ödülleri ayarlayabilirsin. Değişiklikleri `/mob reload` ile uygula.
- **Yetkiler.** Yönetici komutları `coreshift.maden.admin` ve `coreshift.mob.admin` yetkilerini kullanır. Cevher kırma yetkisi `coreshift.maden.use` varsayılan olarak oyunculara açıktır. Kayıtlı madenlerde eklentinin kırma izni diğer koruma eklentilerinin kararını geçersiz kılabildiği için bölge izinlerini kurulumda kontrol et.

## Önceki kurulumdan geçiş

1. Sunucuyu durdur. Dünya ve eklenti verilerinin yedeğini al. Önceki iki JAR dosyasını etkin eklenti klasöründen çıkar.
2. Önceki maden ve zindan veri klasörlerini sırasıyla `plugins/CoreShiftMaden` ve `plugins/CoreShiftDungeonMobs` adıyla kopyala. Madenler, doğma noktaları ve kırılıp henüz geri gelmemiş blokların kaydı bu klasörlerdedir.
3. Daha önce doğmuş yaratıkların tanınması için zindan eklentisinin `config.yml` dosyasına aşağıdaki ayarı ekle. İlk başlatmadan önce yapılmalıdır. Önceki sürümden kalan yaratıklar yüklendikçe yeni kimlikleri yazılır.

```yaml
migration:
  previous-namespace: "craftneradungeonmobs"
```

4. Yetki yöneticisindeki eski `craftnera.*` tanımlarını karşılık gelen `coreshift.*` adlarıyla güncelle. Özelleştirilmiş mesaj dosyalarında eski klasör adları varsa yeni adları yaz.

## Kaynaktan derleme

Java 21 ve Maven 3.9 ile deponun ana klasöründe çalıştır.

```sh
mvn clean package
```

Yeni JAR dosyaları `maden/target/` ve `dungeonmobs/target/` altında oluşur.

## Bu sürümdeki iyileştirmeler

- Maden, aynı blok durumunu her kırmada yeniden kopyalamak yerine ilk karşılaşmada önbelleğe alır. Bir dünya veya chunk yüklendiğinde bekleyen blokları kuyruğa eklerken geçici bir liste oluşturmaz.
- Dungeon Mobs, oyuncuların doğma noktasına mesafesini hesaplarken aynı konum nesnesini tekrar kullanır. Dünya ve chunk taramalarında zindan etiketi olmayan canlılar için ikinci bir etiket sorgusunu atlar.
- Maden oluşturma sınırı 500.000 bloktur. Seçim sınırı aşıldığında komut gerçek sınırı gösterir.

Aynı iki JAR için 1.16.5, 1.21.4 ve 26.3 (Paper API build 200) simülasyonlarında açılış, listeleme ve yeniden yükleme komutları, deneyim ödülü, cevherin geri gelmesi, oyuncuya yakın doğma noktasının etkinleşmesi ve yaratık etiketi doğrulandı. Testler canlı sunucu, yük testi veya oyun içi WorldEdit seçimi içermedi.
