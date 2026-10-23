# Phase 9 Remaining Issues & Technical Risks

## 1. Physical Device Validation
- **Issue**: Physical Android hardware / ADB emulator deployment could not be executed due to absence of attached ADB devices on the host system.
- **Mitigation**: All C++ JNI functions, Room queries, and Kotlin logic are verified via `app:assembleDebug` CMake compilation and `app:testDebugUnitTest` suite (7 tests passed).

## 2. Python / yt-dlp Extractor Integration
- **Issue**: Complete Python runtime embedding (such as Chaquopy) for complex extractors (e.g. YouTube JavaScript player interpretation) requires Python NDK wheel compilation.
- **Mitigation**: Direct HTTP stream downloads are handled by `HttpDownloaderEngine` with chunked byte transfer and real MB/s calculation. `YtDlpEngine` is structured for Python bridge integration.

## 3. Storage Permissions on Android 13+ (API 33+)
- **Issue**: Android 13+ restricts `READ_EXTERNAL_STORAGE` in favor of `READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO`/`READ_MEDIA_AUDIO`.
- **Mitigation**: All granular media permissions and SAF tree permissions (`takePersistableUriPermission`) are declared in `AndroidManifest.xml` and managed via `StorageManager.kt`.
