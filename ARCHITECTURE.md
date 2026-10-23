# Smart Format Converter — System Architecture

## Overview
Smart Format Converter is a local, privacy-focused, offline-first Android multimedia conversion and downloading application.

## Key Layers

```
+-------------------------------------------------------------------+
|                        UI Layer (Jetpack Compose)                 |
|  - Home Dashboard  - Converter  - Downloader  - Queue  - Settings |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                       ViewModel & State Flow                      |
|  - HomeViewModel   - ConverterViewModel  - QueueViewModel ...     |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                           Domain Layer                            |
|  - ConversionEngine  - DownloaderEngine  - StorageManager         |
+-------------------------------------------------------------------+
       /                          |                          \
      v                           v                           v
+---------------+        +------------------+        +---------------+
| Image Engine  |        | FFmpeg Native    |        | Downloader    |
| - Bitmap/EXIF |        | - JNI / CMake    |        | - yt-dlp      |
| - Native JPEG |        | - Remux/Transcode|        | - Python/Bridge|
+---------------+        +------------------+        +---------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|               Persistence & Background Job Management             |
|  - Room Database (ConversionJobEntity)                            |
|  - DataStore Preferences (AppSettings)                            |
|  - Foreground Service (MediaConversionService)                   |
|  - Storage Access Framework (SAF / ContentResolver)               |
+-------------------------------------------------------------------+
```

## Modules & Component Responsibilities
1. **`data/`**:
   - `db/`: Room Database (`AppDatabase`, `ConversionJobDao`, `ConversionJobEntity`).
   - `preferences/`: `UserPreferencesRepository` using AndroidX DataStore.
   - `storage/`: `StorageManager` handling SAF URIs, `ContentResolver`, document output staging, temporary file creation, relative folder structures.
2. **`engine/`**:
   - `image/`: `ImageConverterEngine` for JPEG/PNG/WebP with EXIF preservation and quality/chroma controls.
   - `ffmpeg/`: `FFmpegEngine` for video/audio probing, remuxing, transcoding, cancellation, progress callbacks.
   - `downloader/`: `YtDlpEngine` for video/audio downloading via yt-dlp, metadata inspection, format selection.
3. **`service/`**:
   - `MediaConversionService`: Android Foreground Service executing queued conversion and download jobs with dynamic progress notifications.
4. **`ui/`**:
   - Navigation: Navigation Compose with bottom navigation bar (Home, Converter, Downloader, Queue, Settings).
   - Theme: Premium Dark Theme with M3 color schemes and subtle blue accents.
   - Screens: Responsive UI screens for each section.
