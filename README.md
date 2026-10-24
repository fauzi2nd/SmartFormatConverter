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
- **Language:** Kotlin 2.4.x, C++17 (NDK 28.2)
- **Architecture:** MVVM, Clean Architecture, Unidirectional Data Flow (UDF)
- **Persistence:** Room Database, DataStore Preferences, Storage Access Framework (SAF)
- **Engines:**
  - `libjpeg-turbo` (TurboJPEG 3 C++ JNI)
  - `RxFFmpeg` native engine
  - AndroidX Media3 / ExoPlayer & Coil

---

## Getting Started

### Prerequisites

- **JDK:** OpenJDK 25 (bundled JetBrains Runtime / Android Studio JBR recommended)
- **Android SDK:** Compile SDK 37, Min SDK 26
- **Android NDK:** 28.2.13676358
- **CMake:** 3.22.1

### Build Instructions

1. **Clone the repository with submodules:**
   ```bash
   git clone --recursive <repo-url>
   # If already cloned:
   git submodule update --init --recursive
   ```

2. **Configure SDK path in `local.properties`:**
   ```properties
   sdk.dir=C\:\\Users\\<YourUser>\\AppData\\Local\\Android\\Sdk
   ```

3. **Build Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Install to Connected Device:**
   ```bash
   ./gradlew installDebug
   ```

---

## Documentation

- [System Architecture](ARCHITECTURE.md)
- [Development Progress & Phase History](DEVELOPMENT_PROGRESS.md)
- [Known Issues & Technical Constraints](KNOWN_ISSUES.md)

---

## License

Internal / Proprietary. All rights reserved.
