# TV Kumanda

Netflix, Disney ve Prime kumanda tuşlarını seçtiğiniz uygulamalara yönlendiren küçük Android uygulaması. APK TV box'a kurulur; telefonla TV'ye komut göndermez.

## Kurulum

1. `TVBoxKumanda.apk` dosyasını USB bellek veya kullandığınız dosya aktarma yöntemiyle TV box'a taşıyın.
2. TV box'ta dosyayı açın. Android isterse kullandığınız dosya yöneticisine bilinmeyen uygulama yükleme izni verin.
3. **TV Kumanda** uygulamasını açın, **Kumanda hizmetini aç** düğmesini seçin.
4. Android erişilebilirlik ayarlarında **TV Kumanda** hizmetini etkinleştirip uygulamaya dönün.
5. Netflix satırında **Tuşu öğret** seçeneğine basın, ardından fiziksel Netflix tuşuna bir kez basın.
6. **Uygulama seç** ile açılacak uygulamayı belirleyin. Disney ve Prime için tekrarlayın.
7. **Dene** seçilen uygulamanın açılışını kontrol eder. Ana ekrana dönüp fiziksel tuşları ayrıca deneyin.

Değişiklikler anında yerel olarak kaydedilir. Bir tuşun atamasını kaldırmak için o satırdaki **Sıfırla** düğmesini kullanın. Tüm yönlendirmeyi durdurmak için Android ayarlarında hizmeti kapatın.

## Cihazda yapılacak kısa test

- Her üç tuş da doğru uygulamayı açmalı; orijinal uygulama öne gelmemeli.
- Başka bir uygulama açıkken ve ana ekrandayken ayrı ayrı deneyin.
- Tuşu basılı tutunca tekrar tekrar açılış olmamalı.
- Yön, OK, geri, ses ve güç tuşları normal çalışmalı.
- TV box yeniden başlatıldıktan sonra kayıtlar korunmalı; hizmetin tekrar bağlandığını kontrol edin.
- Hedef uygulamayı kaldırırsanız ilgili satır bunu bildirmeli; yeniden uygulama seçin.

**Fiziksel TV box/kumanda testleri bu ortamda yapılmadı.** Bilgisayardaki birim ve Android benzetimli testler üreticinin firmware davranışını kanıtlamaz.

## Uyumluluk ve sorun giderme

Android 5.0 ve üzeri için derlenmiştir. Gerçek cihazların tümünde çalışacağı garantisi yoktur.

**Tuş algılanamadı:** Öğrenme 15 saniye sürer ve ayar ekranından ayrılınca iptal olur. Yön, OK, geri, klavye, ses, güç ve giriş seçimi tuşları korunur. Bazı üreticiler markalı tuşları uygulamalara iletmez. Tuş Netflix'i açıp ayar ekranını terk ettiriyorsa firmware müdahalesi söz konusu olabilir. APK root veya sistem değişikliği yapmaz.

**Erişilebilirlik açılmıyor:** Bazı Android sürümleri dışarıdan yüklenen uygulamalara “Kısıtlanmış ayarlar” engeli uygular. Android'in uygulama bilgisi ekranındaki menüde varsa “Kısıtlanmış ayarlara izin ver” seçeneğini kullanın. Menü adları üreticiye göre değişebilir.

**Hizmet açık ama tuş çalışmıyor:** Başka bir tuş eşleyici/erişilebilirlik hizmeti olay filtresini kullanıyor olabilir. Diğer eşleyiciyi geçici kapatıp deneyin. TV Kumanda hizmetini kapatıp açmak da bağlantıyı yeniler.

**Dene çalışıyor, fiziksel tuş çalışmıyor:** Dene uygulamayı öndeki ayar ekranından başlatır. Arka plandan başlatma, fiziksel olay teslimi ve orijinal uygulamanın engellenmesi ayrıca cihazda test edilmelidir. Android bazı arka plan açılışlarını hata döndürmeden engelleyebilir.

**Yeniden başlatma sonrası durdu:** Üretici erişilebilirlik hizmetini durdurmuş olabilir. Android ayarlarında hizmeti kontrol edin. Uygulamayı zorla durdurduysanız yeniden açıp hizmeti gerekirse kapatıp açın.

## Gizlilik ve boyut

İnternet, kamera, mikrofon, konum veya depolama izni istemez. Ekran içeriğini okumaz. Tuş imzaları ve seçilen paket adları cihazın özel ayar dosyasında saklanır. Erişilebilirlik servisi arka planda yalnızca tuş olaylarını işler; periyodik tarama, wake lock, reklam ve analitik yoktur.

## Geliştirici bilgisi

Kotlin + Android platform Views. SDK 35, minSdk 21, Gradle 8.13, Android Gradle Plugin 8.11.1. AndroidX/Robolectric yalnızca test bağımlılıklarıdır; uygulamada AndroidX arayüzü kullanılmaz.

JDK 17 veya 21, Android SDK `platforms;android-35` ve `build-tools;35.0.0` gerekir. SDK konumunu `local.properties` içindeki `sdk.dir` ile veya `ANDROID_HOME` ortam değişkeniyle belirtin. Windows properties dosyasında sürücü iki noktasını kaçırın: `sdk.dir=C\:/Android/Sdk`.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`.

Teslim APK'sı küçültülmüş release yapısıdır; elle kurulum için yerel Android debug sertifikasıyla imzalanır. Play Store yayın anahtarı değildir. Gelecek güncellemelerin mevcut kuruluma yüklenebilmesi için aynı imzalama anahtarı korunmalıdır. Kaynak arşivine özel anahtar dahil edilmez; başka bilgisayarda farklı debug anahtarı oluşursa mevcut APK üzerine güncelleme kurulmaz.

Kaynaklar: [Android tuş filtreleme](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_REQUEST_FILTER_KEY_EVENTS), [Tuş olaylarının tüketilmesi](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onKeyEvent(android.view.KeyEvent)), [TV uygulaması başlatma](https://developer.android.com/reference/android/content/pm/PackageManager#getLeanbackLaunchIntentForPackage(java.lang.String)).
