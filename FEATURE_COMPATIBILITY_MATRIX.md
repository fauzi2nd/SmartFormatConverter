# Feature Compatibility Matrix: RxFFmpeg vs Pure Kotlin (Media3 + Android SDK)

Dokumen ini memetakan perbandingan kemampuan konversi multimedia antara engine lama (`RxFFmpeg` C++/JNI) dan engine baru (`Pure Kotlin` menggunakan AndroidX Media3 Transformer dan Android SDK Graphics).

---

## 1. Ringkasan Eksekutif

| Indikator | Engine Lama (RxFFmpeg) | Engine Baru (Pure Kotlin + Media3) |
| :--- | :--- | :--- |
| **Bahasa & Runtime** | C++17, NDK 28, JNI C-bindings | 100% Pure Kotlin / Java bytecode |
| **Ukuran Bundel (.aab)** | 56.7 MB | **17.2 MB** (Pengurangan ~70%) |
| **Kepatuhan 16 KB (Android 15+)** | ⚠️ Berisiko (ELF alignment library C++) | **100% Patuh (Semua ELF .so AndroidX terverifikasi 16 KB)** |
| **Akselerasi Perangkat Keras** | Terbatas (Software CPU libx264) | **Arsitektur MediaCodec VPU/GPU Native** (Fallback CPU otomatis) |
| **Efisiensi Daya & Suhu** | Beban CPU tinggi pada encoding software | **Ekspektasi Lebih Hemat Daya** (Menggunakan hardware encoder SoC; benchmark aktual bervariasi per chipset) |

---

## 2. Format Masukan (Input Formats)

Media3 Transformer menggunakan AndroidX Media3 Extractor yang membaca dan mendekode berbagai container multimedia:

| Format Input | Ekstensi | Status | Keterangan |
| :--- | :--- | :--- | :--- |
| **MP4 / M4V** | `.mp4`, `.m4v` | ✅ Didukung Penuh | Native ISO-BMFF extractor resmi AOSP. |
| **Matroska / MKV** | `.mkv` | ✅ Didukung Penuh | Native Media3 Matroska extractor. |
| **WebM** | `.webm` | ✅ Didukung Penuh | Native Media3 WebM VP8/VP9 extractor. |
| **QuickTime / MOV** | `.mov` | ✅ Didukung Penuh | Native Media3 QuickTime atoms extractor. |
| **MPEG-TS** | `.ts`, `.m2ts` | ✅ Didukung Penuh | Native Media3 TS extractor. |
| **MP3** | `.mp3` | ✅ Didukung Penuh | Native Media3 MP3 audio extractor. |
| **AAC / M4A** | `.aac`, `.m4a` | ✅ Didukung Penuh | Native Media3 ADTS & MP4 audio extractor. |
| **FLAC** | `.flac` | ✅ Didukung Penuh | Native Media3 FLAC extractor. |
| **WAV** | `.wav` | ✅ Didukung Penuh | Native Media3 WAV / PCM extractor. |
| **OGG / Opus** | `.ogg`, `.opus` | ✅ Didukung Penuh | Native Media3 Ogg extractor. |
| **AVI** | `.avi` | ⚠️ Parsial / Eksperimental | Didukung oleh Media3 `AviExtractor`. Bukan container progressive standar resmi ExoPlayer; keberhasilan decoding bergantung pada ketersediaan codec video/audio di platform (codec lawas seperti Cinepak/Indeo tidak dijamin oleh AOSP). |
| **JPEG** | `.jpg`, `.jpeg` | ✅ Didukung Penuh | Native Android BitmapFactory. |
| **PNG** | `.png` | ✅ Didukung Penuh | Native Android BitmapFactory. |
| **WebP** | `.webp` | ✅ Didukung Penuh | Native Android BitmapFactory. |

---

## 3. Format Keluaran & Muxing Container (Output Formats)

Pada arsitektur Pure Kotlin/Java tanpa dependensi C++, penulisan stream container bergantung pada `InAppMp4Muxer` (atau `FrameworkMuxer`) milik AndroidX Media3. 

Untuk memastikan integritas container dan mencegah corrupt / ekstensi file yang tidak cocok dengan format data stream di dalamnya, UI dan engine membatasi format ekspor secara ketat:

| Format Output | Container Muxer | Codec | Status | Catatan Integritas |
| :--- | :--- | :--- | :--- | :--- |
| **MP4 Video (`.mp4`)** | Media3 `InAppMp4Muxer` | H.264 / AVC, H.265 / HEVC | ✅ **Fully Supported** | Format video universal standar Android & iOS. Fast-start otomatis. |
| **M4A Audio (`.m4a`)** | Media3 `InAppMp4Muxer` | AAC (`audio/mp4a-latm`) | ✅ **Fully Supported** | Kontainer MP4 berisi stream audio AAC standar industri dengan ekstensi `.m4a`. |
| **JPEG Image (`.jpg`)** | Android SDK `Bitmap.compress` | JPEG | ✅ **Fully Supported** | Kualitas 1–100. Subsampling didelegasikan ke engine Skia/libjpeg internal platform (umumnya 4:2:0 default sistem). |
| **PNG Image (`.png`)** | Android SDK `Bitmap.compress` | PNG Lossless | ✅ **Fully Supported** | Kompresi lossless penuh native. |
| **WebP Image (`.webp`)** | Android SDK `Bitmap.compress` | WebP (Lossy / Lossless) | ✅ **Fully Supported** | Lossy pada semua API, lossless pada Android 11+ (API 30+). |
| **MKV / WebM / AVI / MOV Output** | Tidak disediakan oleh Media3 DefaultMuxer | - | ⛔ **Input Only** | Dikonversi ke MP4 universal agar hasil ekspor valid dan dapat diputar di semua platform. |
| **MP3 Output** | AOSP tidak memiliki encoder MP3 | - | ⛔ **Input Only** | Audio dikonversi ke M4A (AAC) standar. |

---

## 4. Matriks Pengaturan Lanjutan (Advanced Settings)

| Pengaturan | RxFFmpeg | Pure Kotlin / Media3 | Status Implementasi |
| :--- | :--- | :--- | :--- |
| **Custom Resolution (Width & Height)** | `-vf scale=w:h` | `Presentation.createForWidthAndHeight()` | ✅ **Aktif & Diterapkan** |
| **Video Bitrate (Target Bitrate)** | `-b:v` | `VideoEncoderSettings.Builder().setBitrate()` | ✅ **Aktif & Diterapkan** |
| **Audio Bitrate** | `-b:a` | `AudioEncoderSettings.Builder().setBitrate()` | ✅ **Aktif & Diterapkan** |
| **Mute / Remove Audio** | `-an` | `EditedMediaItem.setRemoveAudio(true)` | ✅ **Aktif & Diterapkan** |
| **Video Codec (H.264 vs H.265)** | `libx264` / `libx265` | `MimeTypes.VIDEO_H264` / `VIDEO_H265` | ✅ **Aktif & Diterapkan** |
| **Hardware Encoding** | Flag eksperimental | MediaCodec Hardware Encoder Default | ✅ **Aktif Secara Native** |
| **CRF (Constant Rate Factor)** | Software libx264 CRF | Digantikan oleh Target Bitrate (VBR) | ℹ️ **Disederhanakan ke Kontrol Bitrate** |
| **Chroma Subsampling (JPEG)** | libjpeg-turbo C++ flags | Skia / Android BitmapFactory default | ℹ️ **Didelegasikan ke Platform OS** |
| **Audio Multi-Track Routing** | `-map 0:a:x` | Tidak didukung oleh Media3 tanpa custom composition | ⛔ **Disederhanakan** |
| **Subtitle Copy / Muxing** | `-c:s copy` (MKV) | Tidak didukung oleh Media3 Muxer | ⛔ **Dinonaktifkan** |

---

## 5. Validasi Migrasi Basis Data (Room)

- **Unit Test SQLite Asli (`RoomMigrationTest.kt`)**: Memvalidasi migrasi skema tabel `conversion_jobs` dan `user_settings` menggunakan driver JDBC SQLite asli di JVM untuk memastikan `ALTER TABLE`, penambahan kolom baru, dan integritas data tidak menyebabkan crash atau kehilangan data historis.
- **Pengujian Perangkat Fisik Pra-Rilis**: Direkomendasikan melakukan uji instalasi pembaruan (in-place update) dari APK versi rilis sebelumnya pada perangkat fisik sebelum peluncuran production di Google Play.
