# Feature Verification Matrix

| Feature Area | Sub-Feature / Component | Verification State | Implementation Notes |
|---|---|---|---|
| **Submodule Management** | Upstream libjpeg-turbo `.gitmodules` | **VERIFIED WORKING** | Tracked in `.gitmodules` at commit `36956793b60deb2d1169d1432be6d19871f5ecb1`. |
| **Native Build** | CMake TurboJPEG 3 NDK Linkage | **VERIFIED WORKING** | Compiled for all 4 ABIs (`arm64-v8a`, `x86_64`, `armeabi-v7a`, `x86`). |
| **Native C++ Safety** | Overflow Protection & RAII Cleanup | **VERIFIED WORKING** | Dimension/stride checks, `BitmapLocker`, `JniStringHolder`, `Tj3Holder`, staged `.tmp` output. |
| **Image Engine** | Real JPEG Output Validation | **VERIFIED WORKING** | Full bitmap decoding validation in `ImageConverterEngine.kt`. |
| **Image Engine** | EXIF 8-Mode Orientation & Normalization | **VERIFIED WORKING** | Supports all 8 EXIF orientation modes and normalizes output tag to 1 (`ORIENTATION_NORMAL`). |
| **FFmpeg Engine** | Native FFmpeg JNI Linkage | **VERIFIED WORKING** | `RxFFmpeg` native libraries linked in APK classpath (`RxFFmpegInvoke`). |
| **FFmpeg Engine** | Media Probing | **VERIFIED WORKING** | Uses `MediaExtractor` & `MediaMetadataRetriever` to extract tracks/codecs. |
| **FFmpeg Engine** | Video Transcoding / Remuxing | **VERIFIED WORKING** | Generates real FFmpeg commands (`-c:v libx264`, `-crf`, `-c:a aac`) via JNI. |
| **FFmpeg Engine** | Audio Transcoding & Extraction | **VERIFIED WORKING** | Supports MP3/AAC/WAV/FLAC/M4A audio transcoding (`-vn`, `-c:a`). |
| **FFmpeg Engine** | Progress & Cancellation | **VERIFIED WORKING** | `RxFFmpegSubscriber` callbacks update percentage; `RxFFmpegInvoke.exit()` cancels. |
| **Downloader Engine**| Direct Stream Downloading | **VERIFIED WORKING** | Chunked byte stream download via `HttpURLConnection` with MB/s calculation. |
| **Downloader Engine**| Full On-Device yt-dlp Extractor | **NOT_IMPLEMENTED** | On-device Python/JS yt-dlp engine is not bundled; fallback direct HTTP downloader used instead. |
| **Persistence** | Room Job Queue | **VERIFIED WORKING** | `ConversionJobEntity` status lifecycle (`PENDING` -> `RUNNING` -> `COMPLETED`/`FAILED`). |
| **Persistence** | DataStore User Preferences | **VERIFIED WORKING** | Default output folder, naming templates, metadata flags persisted. |
| **Storage** | SAF & Staged Document Output | **VERIFIED WORKING** | DocumentFile creation in SAF tree URIs and temp cache staging. |
| **Service** | MediaConversionService | **VERIFIED WORKING** | Android Foreground Service with `dataSync` type and ongoing notification. |
| **CI / Automation** | GitHub Actions Workflow | **VERIFIED WORKING** | Configured in `.github/workflows/android-ci.yml`. |
