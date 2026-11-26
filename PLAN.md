# TV Kumanda — uygulama planı

Onay: Kullanıcı 5 Eylül 2026 tarihinde önceki ayrıntılı sohbet planının uygulanmasını onayladı.
Hedef: Netflix, Disney ve Prime kumanda tuşlarını seçilen kurulu uygulamalara yönlendiren küçük, çevrimdışı APK.

- [x] Boş çalışma alanı ve Java ortamını kontrol et.
- [x] Android SDK ve Gradle derleme ortamını hazırla.
- [x] Tuş eşleme ve down/up akışını önce başarısız testlerle doğrula, sonra uygula.
- [x] TV odaklı Türkçe ekran, uygulama seçimi ve tuş öğrenme ekle.
- [x] AccessibilityService, yerel kalıcı kayıt ve uygulama başlatma ekle.
- [x] Birim testleri, lint, küçültülmüş APK ve imza doğrulamasını çalıştır.
- [x] APK ve Türkçe kurulum/test kılavuzunu oluştur; kaynak arşivini teslim için paketle.

Sınırlar: Gerçek TV box bağlantısı yoksa fiziksel tuş, yeniden başlatma ve firmware davranışı test edilmiş sayılmaz.
Hedef Android 5.0+, compile/target SDK 35. Kotlin ve platform Views; üretimde üçüncü taraf UI/ağ/veritabanı bağımlılığı yok.
Ekran içeriği okuma, ağ, root, periyodik tarama ve wake lock kullanılmaz.
Başlatma ilk DOWN olayında bir kez denenir. Başarılı isteğin tekrarları ve UP tüketilir; hata halinde tüm akış bırakılır.
Android'in sessiz engellediği başlatmalar istek başarısından kesin olarak tespit edilemez; gerçek cihaz testi gerekir.
Tuş öğrenme yalnızca ekran görünürken 15 saniye etkin; gezinme ve klavye tuşları korunur.
Kalıcı deviceId kullanılmaz; bağlantı değişince kimlik değişebilir. Basılı tuş akışında deviceId/downTime kullanılır.

Uygulama sonucu: 16 test geçti, lint 0 hata/7 öneri uyarısı. APK 49.467 bayt; v1/v2 imza ve zip hizalama doğrulandı. Ayrıntılar TEST-RAPORU.md dosyasında.
Plan iyileştirmesi: Ayrı Kaydet yerine anında kayıt; Dene ve satır bazlı Sıfırla. Arayüz, küçük tekrarlı satırları platform Views ile koddan kurar.
