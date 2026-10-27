# Terminal Command Cheatsheet (Tanpa Android Studio)

Dokumen ini berisi kumpulan perintah praktis yang siap di-copas langsung ke terminal (**PowerShell** di Windows) untuk pengembangan, build, testing, dan instalasi aplikasi **Smart Format Converter** tanpa memerlukan Android Studio.

---

## 1. Build & Compile Aplikasi

### Build APK Debug (Pengujian Biasa)
Menghasilkan APK Debug siap pasang di HP:
```powershell
.\gradlew.bat assembleDebug
```
> **Lokasi file output:** `app\build\outputs\apk\debug\app-debug.apk`

---

### Bersihkan Cache & Build Ulang (Clean Build)
Gunakan jika ada perubahan kode/resource yang terasa tidak ter-update:
```powershell
.\gradlew.bat clean assembleDebug
```

---

### Build Release Bundle (.aab untuk Google Play)
```powershell
.\gradlew.bat bundleRelease
```
> **Lokasi file output:** `app\build\outputs\bundle\release\app-release.aab`

---

### Build Release APK (.apk untuk Instalasi Mandiri)
```powershell
.\gradlew.bat assembleRelease
```
> **Lokasi file output:** `app\build\outputs\apk\release\app-release-unsigned.apk` (atau `app-release.apk` jika keystore sudah dikonfigurasi).

---

## 2. Testing & Screenshots

### Jalankan Screenshot Test Roborazzi (Compose Previews)
Membuat/memperbarui screenshot pratinjau UI di folder `app/preview/`:
```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "org.roruku.smartformatconverter.ComposePreviewScreenshotTest" --rerun-tasks --console=plain
```

---

### Jalankan Semua Unit Test
```powershell
.\gradlew.bat :app:testDebugUnitTest
```
> Laporan HTML pengujian dapat dilihat di:
> `app/build/reports/tests/testDebugUnitTest/index.html`

---

### Cek Lint (Pemeriksaan Kualitas Kode Android)
```powershell
.\gradlew.bat lintDebug
```
> Laporan HTML lint: `app/build/reports/lint-results-debug.html`

---

## 3. Instal & Jalankan di HP via USB / WiFi (ADB)

> **Catatan:** Pastikan USB Debugging aktif di HP dan HP terhubung ke komputer.

### Cek Perangkat Terhubung
```powershell
adb devices
```

---

### Sekali Jalan: Build + Langsung Install ke HP
```powershell
.\gradlew.bat installDebug
```

---

### Install APK yang Sudah Ada ke HP (Timpa tanpa hapus data)
```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

---

### Buka / Jalankan Aplikasi di HP dari Terminal
```powershell
adb shell am start -n org.roruku.smartformatconverter/.MainActivity
```

---

### Tutup Paksa (Force Stop) Aplikasi di HP
```powershell
adb shell am force-stop org.roruku.smartformatconverter
```

---

### Hapus (Uninstall) Aplikasi dari HP
```powershell
adb uninstall org.roruku.smartformatconverter
```

---

## 4. Debugging & Logcat (Melihat Log Aplikasi)

### Tampilkan Log Khusus Aplikasi Smart Format Converter
```powershell
adb logcat -v time -s "SmartFormatConverter:*"
```

---

### Tampilkan Hanya Pesan Error di Seluruh Sistem
```powershell
adb logcat *:E
```

---

### Bersihkan Riwayat Logcat Lama
```powershell
adb logcat -c
```

---

## 5. Manajemen Gradle Daemon & RAM

### Cek Status Gradle Daemon yang Sedang Berjalan
```powershell
.\gradlew.bat --status
```

---

### Matikan Semua Gradle Daemon (Bebaskan RAM & Reset Daemon)
Jalankan jika laptop terasa berat atau muncul banyak "stopped/busy daemons":
```powershell
.\gradlew.bat --stop
```

---

### Build Sekali Pakai (Tanpa Menyimpan Daemon di Background)
Gunakan jika ingin build tanpa meninggalkan proses daemon di RAM:
```powershell
.\gradlew.bat assembleDebug --no-daemon
```

---

## 6. Git Workflow

### Tarik Kode Terbaru dari GitHub
```powershell
git pull origin master
```

### Cek Status Perubahan File
```powershell
git status
```

### Commit & Push Perubahan
```powershell
git add .
git commit -m "feat/fix: deskripsi perubahan"
git push origin master
```

---

## 7. Tool Analisis Kualitas Offline (Python)

### Bantuan Penggunaan Tool Benchmark SSIM/PSNR
```powershell
python tools/benchmark_quality.py --help
```

### Inspeksi File WebM Opus
```powershell
python tools/inspect_webm_opus.py <path_ke_file.webm>
```
