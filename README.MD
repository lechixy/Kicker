# Kick

Kick.com client built with Jetpack Compose for Android 💚

Hesap bağlamak isteğe bağlıdır. Yayın izleme, arama, VOD ve sohbet okuma misafir olarak kullanılabilir.
Kick hesabınızla uygulama içindeki Kick giriş ekranından bir kez giriş yaparak gerçek takiplerinize erişebilir,
kanalları takip edebilir ve sohbet mesajı gönderebilirsiniz.

Uygulama doğrudan Kick web API ve WebSocket servislerine bağlanır; ayrı bir backend,
Developer uygulama kaydı veya giriş için ek yapılandırma gerekmez.
Oturum cihazda Android Keystore destekli şifrelemeyle saklanır; yedeklere ve ayar dışa aktarımlarına dahil edilmez.
Çıkış yapmak uygulamanın sakladığı oturumu ve hesap verilerini temizler.

Projeyi Android Studio'da açıp `app` yapılandırmasını çalıştırabilirsiniz.
Kick web API değişiklikleri, hesap özelliklerinde uyumluluk güncellemesi gerektirebilir.

## Dosya düzeni

Kotlin kaynakları `com.lechixy.kick` altında kullanım alanına ve görevine göre gruplanır:

- `screens/<ekran>`: ekran, ViewModel ve ekran durumu.
- `components/<ekran>`: tek ekrana veya hesap paneli gibi bir arayüz alanına ait bileşenler. Kanal sohbeti `components/channel/chat`, ayar bileşenleri `components/settings` altındadır. Birden fazla ekranda kullanılan bileşenler doğrudan `components` içinde kalır.
- `theme`: Compose renk, tipografi ve tema tanımları.
- `data/<alan>/<görev>`: `account`, `chat`, `channel`, `browse`, `following`, `favorites`, `player`, `settings` alanlarının `model`, `repository`, `remote`, `local` ve `policy` dosyaları. Alanlar arasında ortak veri ve ağ kodu `data/common` altındadır.
- `service/player` ve `util/player`: oynatma servisinin yardımcıları ve oynatıcı araçları. Manifestte kayıtlı `service.PlaybackService` yerini korur; genel yardımcılar `util` içinde kalır.

`res/values` ve `res/values-tr` içindeki `strings_<ekran>.xml` dosyaları aynı ekran gruplarını kullanır. Kanal ekranının sohbet ve oynatıcı metinleri `strings_channel.xml`, hesap/giriş/ödül metinleri `strings_account.xml`, ayar ve geliştirici aracı metinleri `strings_settings.xml` içindedir. Ekranlar arasında ortak metinler ve servis bildirimleri `strings.xml` içinde tutulur. Kaynak adları ekran dosyası değiştiğinde yeniden adlandırılmaz.

Testler üretim paketlerini izler; alanlar arası senaryolar `integration`, ortak hesap test yardımcıları `data/account/support` altındadır.
