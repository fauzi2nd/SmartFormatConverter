# Panduan Rilis Google Play (Release Pipeline Guide)

Panduan ini mendokumentasikan langkah-langkah pembuatan paket rilis **Android App Bundle (.aab)** dan penandatanganan (signing) untuk publikasi **Smart Format Converter** di Google Play Console.

---

## 1. Konfigurasi Signing Key (Keystore)

Aplikasi telah dikonfigurasi di `app/build.gradle.kts` untuk mendukung penandatanganan rilis melalui Environment Variables atau Gradle Properties tanpa menyimpan kunci rahasia di dalam repositori git.

### Cara Membuat Keystore Rilis (Upload Keystore)

Jalankan perintah berikut di terminal (menggunakan JDK `keytool`):

```bash
keytool -genkey -v -keystore smartformatconverter-release.jks -alias releaseKey -keyalg RSA -keysize 2048 -validity 10000
```

> **PENTING:** Simpan file `smartformatconverter-release.jks` dan kata sandi di tempat yang aman (misalnya pengelola kata sandi atau brankas aman). Jangan pernah melakukan commit file keystore ke git.

---

## 2. Mengatur Environment Variables untuk Build Rilis

Sebelum menjalankan build rilis lokal, atur environment variables berikut:

### Di Windows PowerShell:
```powershell
$env:KEYSTORE_FILE = "C:\path\to\smartformatconverter-release.jks"
$env:KEYSTORE_PASSWORD = "PasswordKeystoreAnda"
$env:KEY_ALIAS = "releaseKey"
$env:KEY_PASSWORD = "PasswordKeyAnda"
```

### Atau via `local.properties` (Aman / Tidak Ter-commit ke Git):
```properties
KEYSTORE_FILE=C\:/path/to/smartformatconverter-release.jks
KEYSTORE_PASSWORD=PasswordKeystoreAnda
KEY_ALIAS=releaseKey
KEY_PASSWORD=PasswordKeyAnda
```

> **Catatan:** Google Play Console mewajibkan file AAB ditandatangani dengan Upload Key (keystore rilis) sebelum diunggah. Jika variabel di atas belum disetel, Gradle akan menghasilkan unsigned AAB yang perlu ditandatangani menggunakan `jarsigner` sebelum diunggah. Disarankan menyetel properti di atas pada `local.properties` agar `bundleRelease` langsung menghasilkan signed AAB.

---

## 3. Perintah Build Release

### Membangun Android App Bundle (AAB untuk Google Play)
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew bundleRelease
```
File output akan tersedia di:
`app/build/outputs/bundle/release/app-release.aab`

### Membangun Release APK (Untuk Pengujian Manual di Perangkat Fisik)
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew assembleRelease
```
File output akan tersedia di:
`app/build/outputs/apk/release/app-release-unsigned.apk` (atau `app-release.apk` jika keystore dikonfigurasi).

---

## 4. Verifikasi Kualitas Rilis (Quality Gates)

Pastikan semua pengujian lulus sebelum mengunggah bundle ke Play Console:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew testDebugUnitTest lintDebug lintRelease bundleRelease
```

Semua pemeriksaan di atas telah diintegrasikan secara otomatis ke dalam **GitHub Actions CI**.

---

## 5. Unggah ke Google Play Console

1. Buka [Google Play Console](https://play.google.com/console).
2. Pilih aplikasi **Smart Format Converter**.
3. Di menu kiri, buka **Release** > **Testing** > **Closed testing** (atau **Production**).
4. Buat rilis baru (Create new release).
5. Unggah file `app/build/outputs/bundle/release/app-release.aab`.
6. Tinjau dan pastikan **Google Play App Signing** aktif.
7. Masukkan catatan rilis (Release notes) dan klik **Save** lalu **Review release**.
