# Phase 9.1 JPEG Output Validation Report

## 1. Native JPEG Encoder Architecture
- **Library / Source**: Native C++ via NDK/CMake (`libjpeg_encoder_native.so`).
- **Input Pixel Format**: `ANDROID_BITMAP_FORMAT_RGBA_8888`.
- **Supported Quality**: `1..100` (Default: `100`).
- **Supported Subsampling Modes**:
  - `4:4:4` (YUV 4:4:4 / `0x11` factors) — **Default**.
  - `4:2:2` (YUV 4:2:2 / `0x21` factors).
  - `4:2:0` (YUV 4:2:0 / `0x22` factors).

---

## 2. Post-Encoding Validation Pipeline
1. **Dimension Preservation**: `options.outWidth` and `options.outHeight` equal original input bitmap dimensions.
2. **Decodability Check**: `BitmapFactory.decodeFile(outputFile.absolutePath, options)` MUST return `outWidth > 0` and `outHeight > 0`.
3. **Fallback Safety**: If native output fails decodability validation, the engine logs `Native JPEG file validation failed` and falls back to standard decodable compression.
