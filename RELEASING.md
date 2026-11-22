# APK yayını

## GitHub Secrets kurulumu

Repo → **Settings → Secrets and variables → Actions → New repository secret**.
Aşağıdaki dört secret'ı oluşturun:

| Secret | Değer |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Keystore dosyasının Base64 içeriği |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore parolası |
| `ANDROID_KEY_ALIAS` | Keystore içindeki imzalama anahtarının alias'ı |
| `ANDROID_KEY_PASSWORD` | Seçilen anahtarın parolası; oluştururken aynı parola seçildiyse keystore parolasıyla aynı |

Mevcut keystore'u Base64 olarak panoya almak için kendi PowerShell terminalinizde:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\Users\melih\Keystores\lechixy')) | Set-Clipboard
```

Panodaki metni yalnızca `ANDROID_KEYSTORE_BASE64` secret değerine yapıştırın.
Base64 şifreleme değildir. Keystore'u, Base64 içeriğini ve parolaları repoya veya sohbetlere eklemeyin.
Keystore ve parolaları GitHub dışında güvenli biçimde yedekleyin.

Alias'ı bilmiyorsanız yerel terminalde aşağıdaki komutu çalıştırıp keystore parolasını girin:

```powershell
keytool -list -v -keystore 'C:\Users\melih\Keystores\lechixy'
```

İmzalama için `PrivateKeyEntry` olan girdinin `Alias name` değerini kullanın.
Dosya adı (`lechixy`) ile alias aynı olmak zorunda değildir.
`GITHUB_TOKEN` Actions tarafından otomatik sağlanır; ayrıca secret eklemeyin.

## Sürüm yayınlama

1. `app/build.gradle.kts` içindeki `versionName` değerini sürüme göre ayarlayın.
2. İlk sürümden sonraki her sürümde `versionCode` değerini artırın (1, 2, 3, ...).
3. Değişiklikleri ve workflow'u commit edip GitHub'a gönderin.
4. Aynı commit'e sürüm tag'i ekleyip gönderin:

```powershell
git tag v1.0.0
git push origin v1.0.0
```

Tag'in başındaki `v` isteğe bağlıdır. `v1.0.0-beta.1` gibi ön sürümler desteklenir.
Tag'deki sürüm `versionName` ile aynı olmalıdır; aksi halde workflow yayınlamadan durur.
Tag, uygulama içindeki sürümü veya `versionCode` değerini otomatik değiştirmez.
Örneğin ikinci sürümde `versionName = "1.0.1"`, `versionCode = 2`, tag `v1.0.1` olabilir.

Manuel çalıştırmak için **Actions → Build and Release APKs → Run workflow** ekranındaki
`tag` alanına mevcut tag'i yazın. Workflow doğrudan o tag'in kodunu alır;
bulunmayan tag ile yayın yapmaz. Mevcut tag'i başka commit'e taşımayın.

## Çıktılar ve imzalar

GitHub Release'e yalnızca imzalı release APK'ları ve `sha256sums.txt` eklenir.
Workflow `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` ve `universal` APK'larını derler.

```text
kicker_1.0.0_arm64-v8a_release.apk
kicker_1.0.0_armeabi-v7a_release.apk
kicker_1.0.0_x86_release.apk
kicker_1.0.0_x86_64_release.apk
kicker_1.0.0_universal_release.apk
sha256sums.txt
```

Release APK'ları Secrets'taki kalıcı anahtarla imzalanır. Workflow APK imzalarını,
release sertifikalarının aynı olduğunu, sürümü ve beklenen mimarileri doğrular.
Secrets verilmeden yerel debug derlemesi çalışmaya devam eder. İmzalama ortam değişkenleri
verilmezse yerel release çıktısı debug anahtarıyla imzalanır. GitHub iş akışı yayın için
release anahtarını zorunlu tutar. F-Droid, kaynak kodu derlemeden önce Gradle imza ayarlarını kaldırır.

Yerel imzalı derleme için `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` ortam değişkenlerinin tamamını ayarlayın.
Parolaların Gradle configuration cache'e yazılmaması için `--no-configuration-cache` kullanın.
CI bunu zaten uygular ve geçici keystore'u derleme sonunda siler.

## F-Droid ve IzzyOnDroid

Başvuru koşulları, derleme doğrulaması ve başvuru dosyaları için [dağıtım kılavuzuna](distribution/README.md) bakın.
