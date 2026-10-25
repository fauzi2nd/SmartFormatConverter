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

- **Unit Test SQLite Asli (`RoomMigrationTest.kt`)**: Memvalidasi SQL migrasi tabel `conversion_jobs` menggunakan driver JDBC SQLite asli di JVM untuk memastikan `ALTER TABLE`, penambahan kolom baru, dan integritas data tidak menyebabkan crash atau kehilangan data historis.
- **Pengujian Perangkat Fisik Pra-Rilis**: Direkomendasikan melakukan uji instalasi pembaruan (in-place update) dari APK versi rilis sebelumnya pada perangkat fisik sebelum peluncuran production di Google Play.

---

## 6. Actual Release Boundaries (Audit 2026-10-25)

- **Video export:** MP4 only. **Audio export:** AAC in M4A only. Old queued
  FFmpeg formats are rejected with actionable errors rather than silently
  creating files whose extension disagrees with the data.
- **Legacy remux/stream copy:** not guaranteed by Media3, and rejected in this
  release until validated end-to-end. Do not advertise lossless stream copy.
- **Audio sample rate, channel forcing, volume and metadata editing:** not
  exposed in the current UI because they are not implemented in the active
  Media3 transform path.
- **Video metadata/fast-start manual toggles:** not exposed; Media3 controls
  muxing internally. Video encoding bitrate and selectable H.264/H.265 remain
  the supported tuning knobs.
- **Video prevent-upscale:** requesting dimensions larger than the identified
  source leaves the original dimensions unchanged; unknown source dimensions
  fail safely rather than forcing an upscale.
- **JPEG chroma subsampling:** Android platform encoder chooses the mode.
  Stored legacy "444" settings must NOT be presented as a 4:4:4 guarantee.
- **Image memory:** for explicit downscale without cropping, BitmapFactory
  sample-size reduces decode memory. Original-size exports still require
  sufficient heap and must not be silently resized.
- **CI vs device validation:** a successful Gradle build and unit test run
  does not prove all conversions work across Android chipsets. Manual device
  tests and a signed release AAB are separate launch gates.
