# Feature Compatibility Matrix: RxFFmpeg vs Pure Kotlin (Media3 + Android SDK)

Dokumen ini memetakan perbandingan kemampuan konversi multimedia antara engine lama (`RxFFmpeg` C++/JNI) dan engine baru (`Pure Kotlin` menggunakan AndroidX Media3 Transformer dan Android SDK Graphics).

---

## 1. Ringkasan Eksekutif

| Indikator | Engine Lama (RxFFmpeg) | Engine Baru (Pure Kotlin + Media3) |
| :--- | :--- | :--- |
| **Bahasa & Runtime** | C++17, NDK 28, JNI C-bindings | 100% Pure Kotlin / Java bytecode |
| **Ukuran Bundel (.aab)** | 56.7 MB | **17.2 MB** (Pengurangan 70%) |
| **Kepatuhan 16 KB (Android 15+)** | ⚠️ Gagal / Warning ELF alignment | **100% Patuh (0 library `.so`)** |
| **Akselerasi Perangkat Keras** | Terbatas (Software CPU libx264) | **Penuh (MediaCodec VPU/GPU)** |
| **Efisiensi Baterai & Suhu** | Berat (beban CPU tinggi) | **Sangat Ringan (Hardware Dedicated)** |

---

## 2. Format Masukan (Input Formats)

Media3 Transformer menggunakan AndroidX Media3 Extractor yang mendukung pembacaan dan decoding berbagai format multimedia:

| Format Input | Ekstensi | Status | Keterangan |
| :--- | :--- | :--- | :--- |
| **MP4 / M4V** | `.mp4`, `.m4v` | ✅ Didukung | Native ISO-BMFF extractor |
| **Matroska / MKV** | `.mkv` | ✅ Didukung | Native Media3 Matroska extractor |
| **WebM** | `.webm` | ✅ Didukung | Native Media3 WebM VP8/VP9 extractor |
| **QuickTime / MOV** | `.mov` | ✅ Didukung | Native Media3 QuickTime atoms extractor |
| **AVI** | `.avi` | ✅ Didukung | Native Media3 AVI extractor |
| **MPEG-TS** | `.ts`, `.m2ts` | ✅ Didukung | Native Media3 TS extractor |
| **MP3** | `.mp3` | ✅ Didukung | Native Media3 MP3 audio extractor |
| **AAC / M4A** | `.aac`, `.m4a` | ✅ Didukung | Native Media3 ADTS & MP4 audio extractor |
| **FLAC** | `.flac` | ✅ Didukung | Native Media3 FLAC extractor |
| **WAV** | `.wav` | ✅ Didukung | Native Media3 WAV / PCM extractor |
| **OGG / Opus** | `.ogg`, `.opus` | ✅ Didukung | Native Media3 Ogg extractor |
| **JPEG** | `.jpg`, `.jpeg` | ✅ Didukung | Native Android BitmapFactory |
| **PNG** | `.png` | ✅ Didukung | Native Android BitmapFactory |
| **WebP** | `.webp` | ✅ Didukung | Native Android BitmapFactory |

---

## 3. Format Keluaran & Muxing Container (Output Formats)

Pada Android tanpa dependensi C++, penulisan file container bergantung pada `InAppMp4Muxer` dan `android.media.MediaMuxer`. Demi integritas file dan memastikan hasil konversi dapat diputar di semua perangkat:

| Format Output | Container Muxer | Codec | Status | Catatan Integritas |
| :--- | :--- | :--- | :--- | :--- |
| **MP4 Video (`.mp4`)** | Media3 `InAppMp4Muxer` | H.264 / AVC, H.265 / HEVC | ✅ **Fully Supported** | Format video universal standar Android & iOS. Fast-start otomatis. |
| **M4A Audio (`.m4a`)** | Media3 `InAppMp4Muxer` | AAC (`audio/mp4a-latm`) | ✅ **Fully Supported** | Format audio berkualitas tinggi universal standar industri. |
| **JPEG Image (`.jpg`)** | Android SDK `Bitmap.compress` | JPEG | ✅ **Fully Supported** | 4:2:0 subsampling seimbang, kontrol kualitas 1–100. |
| **PNG Image (`.png`)** | Android SDK `Bitmap.compress` | PNG Lossless | ✅ **Fully Supported** | Kompresi lossless penuh. |
| **WebP Image (`.webp`)** | Android SDK `Bitmap.compress` | WebP (Lossy / Lossless) | ✅ **Fully Supported** | Mendukung WebP lossless pada Android 11+ (API 30+). |
| **MKV / AVI / MOV Output** | Tidak tersedia muxer native | - | ⛔ **Input Only** | Dikonversi menjadi MP4 universal untuk menjaga validitas container. |
| **Raw MP3 Output** | AOSP tidak memiliki encoder MP3 | - | ⛔ **Input Only** | Audio diekstrak / dikonversi ke M4A (AAC) standar. |

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
| **CRF (Constant Rate Factor)** | Software libx264 CRF | Dipetakan ke Target Bitrate VPU | 🔄 **Otomatis Dikonversi ke Bitrate** |
| **Audio Volume Adjustment** | `-filter:a volume` | Native volume processing | ⚠️ **Terbatas pada Player Preview** |
| **Subtitle Copy / Muxing** | `-c:s copy` (MKV) | Tidak didukung oleh Media3 Muxer | ⛔ **Dinonaktifkan** |
| **Custom Chroma 4:4:4 (JPEG)** | libjpeg-turbo C++ | Default Android SDK 4:2:0 | ℹ️ **Menggunakan Default Sistem 4:2:0** |
