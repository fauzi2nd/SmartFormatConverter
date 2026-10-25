# Smart Format Converter

A privacy-focused Android multimedia converter and direct file downloader, built with Kotlin, Jetpack Compose and AndroidX Media3.

## Currently implemented

- **Video conversion:** Export MP4 (H.264/H.265, subject to device codec support) from supported local media sources. Configurable target bitrate and resolution; Media3 selects an available encoder.
- **Audio conversion:** Export AAC audio in an M4A container. Supported input decoding depends on Android/Media3 and the media's codec.
- **Images:** Convert JPEG, PNG and WebP with quality/resizing options, optional JPEG EXIF preservation, and white-background flattening for transparent JPEG inputs. Chroma subsampling is chosen by Android; JPEG 4:4:4 is **not guaranteed**.
- **Direct URL downloads:** Download direct HTTP/HTTPS media files, with progress and resumable byte-range transfers when the server supports them. This is not a general streaming-site extractor.
- **Job queue:** Room-backed batch jobs, SAF output selection, progress notifications, Pause/Resume/Cancel, and cleanup of conversion cache on completion/error.

**Current output limit:** Only MP4 video, M4A audio, JPEG, PNG and WebP are provided. MKV/WebM/AVI/MOV and MP3/FLAC/WAV **export** and lossless video stream-copy/remux are not implemented in the active Media3 pipeline. Input support does not guarantee every platform codec can decode every file.

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

The instrumented smoke tests convert a PNG to JPEG and WAV to M4A and inspect the outputs. Device test results and signed AAB verification are required separately before publication. An unsigned `bundleRelease` artifact is **not** ready for Play upload.

## Documentation

- [Release guide](RELEASE_GUIDE.md)
- [Feature compatibility and limitations](FEATURE_COMPATIBILITY_MATRIX.md)
- [Draft Play Store listing and declarations](PLAY_STORE_METADATA.md)
- [Privacy policy](PRIVACY_POLICY.md)

## License

[MIT](LICENSE). Open source; implementation and third-party dependencies can be independently audited.
