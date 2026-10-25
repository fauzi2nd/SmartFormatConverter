# Smart Format Converter

A modern, privacy-focused, offline-first Android multimedia conversion and downloading application built with Jetpack Compose and Material 3.

---

## Features

- **Video Converter:** Transcode and remux video containers (MP4, MKV, MOV, WebM, etc.) with customizable resolution, framerate, and audio tracks.
- **Image Converter:** High-quality batch image conversion for JPEG, PNG, and WebP with EXIF preservation and native TurboJPEG integration.
- **Audio Converter:** Fast conversion and audio extraction across standard audio formats (MP3, AAC, FLAC, WAV, OGG).
- **URL Downloader:** Resumable chunked byte-stream downloader with live speed and ETA calculations.
- **Persistent Job Queue:** Background foreground-service conversion queue with progress notifications and pause/resume capabilities.

---

## Tech Stack

- **UI:** Jetpack Compose, Material 3, Navigation Compose
- **Language:** Pure Kotlin 2.4.x (100% Kotlin codebase, zero native C++/NDK)
- **Architecture:** MVVM, Clean Architecture, Unidirectional Data Flow (UDF)
- **Persistence:** Room Database, DataStore Preferences, Storage Access Framework (SAF)
- **Engines:**
  - AndroidX Media3 Transformer & ExoPlayer (Hardware-accelerated media transcoding)
  - Android SDK Native Graphics (Pure Kotlin image processing)
  - Coil Image & Video Loader

---

## Getting Started

### Prerequisites

- **JDK:** OpenJDK 25 (bundled JetBrains Runtime / Android Studio JBR recommended)
- **Android SDK:** Compile SDK 37, Min SDK 26

### Build Instructions

1. **Clone the repository:**
   ```bash
   git clone <repo-url>
   ```

2. **Configure SDK path in `local.properties`:**
   ```properties
   sdk.dir=C\:\\Users\\<YourUser>\\AppData\\Local\\Android\\Sdk
   ```

3. **Build Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Build Release AAB (Google Play):**
   ```bash
   ./gradlew bundleRelease
   ```

---

## Documentation

- [Release Pipeline Guide](RELEASE_GUIDE.md)
- [Feature Compatibility Matrix](FEATURE_COMPATIBILITY_MATRIX.md)
- [Google Play Store Metadata](PLAY_STORE_METADATA.md)
- [Privacy Policy](PRIVACY_POLICY.md)

---

## License

MIT License. Open source and publicly auditable.
