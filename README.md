# Smart Format Converter

A privacy-focused Android multimedia converter and direct file downloader, built with Kotlin, Jetpack Compose and AndroidX Media3.

## Currently implemented

- **Video conversion:** Export MP4 (H.264/H.265) and, on devices with VP9 and Opus encoders, WebM (VP9/Opus). Both use Media3 with device-dependent codec support. Configurable target bitrate and resolution.
- **Advanced bitrate selection:** Video and audio use dropdown presets: Auto, Source / original, and standard kbps targets. Source follows the source-track bitrate *when available* but still transcodes unless Remux is used; missing/unsupported metadata falls back to Auto.
- **Adaptive Smart Auto (default):** Calculates video bitrate targets from source resolution, FPS, codec, bitrate and preset; separately plans AAC/Opus targets. The converter shows an output-size estimate and confirms possible growth before queuing. These are estimates, not CRF.
- **Objective quality testing:** An offline Python tool compares converted files using SSIM, PSNR and optional VMAF, without bundling FFmpeg in the app.

- **Audio conversion:** M4A (AAC), raw ADTS AAC, Ogg/Opus, PCM16 WAV and device-gated FLAC. Source decoding and encoder availability depend on the Android chipset; these formats need on-device validation.
- **Images:** JPEG, PNG, WebP, device-gated HEIC and AVIF, and **single-frame GIF** (not animation). Resize and quality controls are available; JPEG 4:4:4 and metadata preservation across all formats are not guaranteed.
- **Direct URL downloads:** Download direct HTTP/HTTPS media files, with progress and resumable byte-range transfers when the server supports them. This is not a general streaming-site extractor.
- **Job queue:** Room-backed batch jobs, SAF output selection, progress notifications, Pause/Resume/Cancel, and cleanup of conversion cache on completion/error.

**Current output limit:** Video MP4/WebM (hardware-dependent); audio M4A, AAC, OGG/Opus, WAV, FLAC (hardware-dependent); images JPEG/PNG/WebP, HEIC, AVIF and one-frame GIF. MP4-only lossless stream-copy and Smart Auto are implemented for compatible source tracks. **MP3 and MKV output are not implemented.**

## Implementation

- Kotlin and Jetpack Compose / Material 3.
- AndroidX Media3 Transformer + MediaCodec, with device-dependent hardware/software selection.
- Android Bitmap APIs for image encoding; no custom C++ image encoder.
- Room, DataStore and Storage Access Framework. Android min SDK 26, target/compile SDK 37.

## Build

Requirements: Android SDK with API 37 and JDK 25 for this repository's Gradle configuration.

```bash
git clone https://github.com/fauzi2nd/SmartFormatConverter.git
cd SmartFormatConverter
./gradlew assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug
./gradlew bundleRelease
```

On Windows, use `gradlew.bat` instead of `./gradlew`. Configure your local Android SDK directory in `local.properties` (never commit it).

`assembleDebugAndroidTest` **compiles** the on-device smoke tests; it does not run them. With an attached Android device or configured emulator, run:

```bash
./gradlew connectedDebugAndroidTest
```

Instrumented smoke tests cover JPEG/GIF/HEIC/AVIF images, M4A/AAC/OGG/WAV/FLAC audio and MP4/WebM video plus strict MP4 remux. They are compiled by CI but require an Android device/emulator for execution. Device test results and signed AAB verification are required separately before publication. An unsigned `bundleRelease` artifact is **not** ready for Play upload.

## Documentation

- [Adaptive presets V2 and offline quality testing](docs/ADAPTIVE_PRESETS_V2.md)
- [Release guide](RELEASE_GUIDE.md)
- [Feature compatibility and limitations](FEATURE_COMPATIBILITY_MATRIX.md)
- [P6–P10 implementation and device restrictions](P6_P10_IMPLEMENTATION.md)
- [Draft Play Store listing and declarations](PLAY_STORE_METADATA.md)
- [Privacy policy](PRIVACY_POLICY.md)

## License

[MIT](LICENSE). Open source; implementation and third-party dependencies can be independently audited.
