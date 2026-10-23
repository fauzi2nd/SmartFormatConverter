# Phase 9.2 Native Integration Documentation

## 1. TurboJPEG Integration Architecture
- **Library API**: `libjpeg-turbo` v3.0 C API (`turbojpeg.h`).
- **Header Declarations**: [`turbojpeg.h`](file:///C:/Users/space/AndroidStudioProjects/SmartFormatConverter/app/src/main/cpp/libjpeg-turbo/turbojpeg.h).
- **C Engine Sources**: [`turbojpeg.c`](file:///C:/Users/space/AndroidStudioProjects/SmartFormatConverter/app/src/main/cpp/libjpeg-turbo/turbojpeg.c).
- **JNI C++ Wrapper**: [`jpeg_encoder.cpp`](file:///C:/Users/space/AndroidStudioProjects/SmartFormatConverter/app/src/main/cpp/jpeg_encoder.cpp).
- **CMake Build**: Linked directly as `jpeg_encoder_native` in [`CMakeLists.txt`](file:///C:/Users/space/AndroidStudioProjects/SmartFormatConverter/app/src/main/cpp/CMakeLists.txt).

---

## 2. JNI API Contract
```cpp
extern "C" JNIEXPORT jboolean JNICALL
Java_org_roruku_smartformatconverter_engine_image_JpegNativeEncoder_nativeEncodeJpeg(
        JNIEnv* env,
        jclass clazz,
        jobject bitmap,
        jstring jOutputPath,
        jint quality,
        jint subsamplingMode, // 0 = 4:4:4, 1 = 4:2:2, 2 = 4:2:0
        jboolean preserveTransparencyWhite
);
```

- **Bitmap Format Required**: `ANDROID_BITMAP_FORMAT_RGBA_8888`.
- **Row Stride**: Extracted dynamically via `info.stride`.
- **Memory Handling**: Direct NDK `AndroidBitmap_lockPixels` and `AndroidBitmap_unlockPixels` calls with zero unnecessary full-image array allocations.
