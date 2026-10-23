# Phase 9 Dependency Audit

## 1. Active Dependencies

| Dependency | Group / Name | Version | Purpose | Status |
|---|---|---|---|---|
| **Android Gradle Plugin** | `com.android.application` | `9.4.1` | Android build configuration | **VERIFIED OK** |
| **Kotlin** | `org.jetbrains.kotlin.android` | `2.2.10` | Programming language & Compose compiler | **VERIFIED OK** |
| **CMake** | Native C++ NDK | `3.22.1` | Compiles native C++ JPEG 4:4:4 encoder (`libjpeg_encoder_native.so`) | **VERIFIED OK** |
| **FFmpeg Native** | `com.github.microshow:RxFFmpeg` | `4.9.0` | Native JNI FFmpeg execution & stream probing | **VERIFIED OK** |
| **Jetpack Compose** | Compose BOM | `2026.02.01` | Declarative UI components | **VERIFIED OK** |
| **Navigation Compose** | `androidx.navigation:navigation-compose` | `2.8.8` | Bottom navigation bar & screen routing | **VERIFIED OK** |
| **Room Database** | `androidx.room:room-runtime` / `ktx` | `2.6.1` | SQLite persistent queue & job records | **VERIFIED OK** |
| **DataStore** | `androidx.datastore:datastore-preferences` | `1.1.3` | Settings storage | **VERIFIED OK** |
| **ExifInterface** | `androidx.exifinterface:exifinterface` | `1.4.0` | EXIF metadata & orientation handling | **VERIFIED OK** |
| **DocumentFile** | `androidx.documentfile:documentfile` | `1.0.1` | Storage Access Framework tree URI file access | **VERIFIED OK** |
| **Coil** | `io.coil-kt:coil-compose` | `2.7.0` | Asynchronous image loading | **VERIFIED OK** |
