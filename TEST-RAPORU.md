# TV Kumanda 1.0 — doğrulama

Tarih: 5 Eylül 2026

## Teslim APK

- Dosya: TVBoxKumanda.apk
- Boyut: 49.467 bayt (48,31 KiB / yaklaşık 49 KB)
- Paket: com.androidkumanda
- Sürüm: 1.0 (versionCode 1)
- Minimum Android: 5.0 / API 21
- Hedef/derleme SDK: 35
- Tür: R8 ile küçültülmüş release, yerel debug sertifikasıyla imzalı
- SHA-256: EAF2044E39CBBD822061F68A9964EE842F39C9F8CA368EF0313F2825FE02F601

## Yapılan kontroller

`gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`: başarılı.
Son eklenen başlatma testlerinden sonra `:app:testDebugUnitTest :app:lintDebug`: başarılı.

16 otomatik test, 0 başarısızlık, 0 hata, 0 atlanan test.

9 saf Kotlin tuş akışı testi: bir basışta bir açılış, uzun basma, başarısız açılışta olayları bırakma, eşleşmeyen tuş, basılıyken atama değişimi, farklı cihazdan UP, kayıp UP sonrasında yeni basış, scan code ayrımı, korunan tuşlar.

7 Robolectric testi (Android API 28 benzetimi): yeni kayıt okuyucusunda atamaların korunması, tek satır sıfırlama, çakışan atamanın reddi, geçersiz kayıtları yok sayma, bulunmayan uygulama, TV/normal başlatma ekranı seçimi, tekilleştirilmiş uygulama listesi, ana ekran ve butonların oluşturulması.

Başlangıçta işlevi olmayan tuş yönlendiricisinde 9 testin 7'si beklenen davranış eksikliğiyle başarısız oldu; uygulama yazıldıktan sonra hepsi geçti.

Android lint: 0 hata, 7 öneri uyarısı. Uyarılar sabitlenmiş SDK/Gradle/test kütüphanesi sürümleri, TV banner vektör boyutu, eski sürüm yedekleme tanımı ve yalnızca Türkçe olan ekranda metin birleştirme hakkındadır. `allowBackup=false`; Android 12+ veri çıkarma kuralları da yedeklemeyi dışlar.

`apksigner verify --verbose --print-certs`: doğrulandı, v1 ve v2 imzalar geçerli. v3/v4 imza kullanılmıyor. Araç standart META-INF build metadata dosyalarının v1 JAR imzası kapsamı dışında kaldığını belirtti; v2 APK imzası geçerli.

`zipalign -c -p 4`: çıkış kodu 0.

`aapt dump badging`: Android ve TV launcher girişleri mevcut; dokunmatik ekran ve Leanback donanımı zorunlu değil.

`aapt dump permissions`: uses-permission yok. Hizmete Android'in bağlanması BIND_ACCESSIBILITY_SERVICE ile korunur; kullanıcı hizmeti erişilebilirlik ayarlarından açar.

## Yapılmayan kontroller

Gerçek cihaz kurulumu, fiziksel Netflix/Disney/Prime olay teslimi, kumandayla görsel kullanım, firmware'in orijinal uygulamayı engellemesi, başka uygulama üzerinden arka plan başlatma, TV box yeniden başlatılması ve gerçek RAM/CPU/gecikme ölçümü yapılmadı.

Robolectric bir Android cihazı veya tam sistem emülatörü değildir. Bu testler hizmetin gerçek TV box'ta etkinleştiğini veya firmware tuşlarını yakaladığını kanıtlamaz. Gerçek cihaz kabul adımları README.md içinde yer alır.
