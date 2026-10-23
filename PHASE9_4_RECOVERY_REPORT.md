# Phase 9.4 Strict Upstream Recovery Report

## 1. Upstream Dependency Provenance Evidence
- **Exact Source Directory**: `C:/Users/space/AndroidStudioProjects/SmartFormatConverter/third_party/libjpeg-turbo`
- **Upstream Repository URL**: `https://github.com/libjpeg-turbo/libjpeg-turbo.git`
- **Pinned Git Commit Hash**: `36956793b60deb2d1169d1432be6d19871f5ecb1`
- **License**: Official IJG / 3-clause BSD License (`LICENSE.md`)
- **Terminal Execution Verification**:
  ```powershell
  git clone https://github.com/libjpeg-turbo/libjpeg-turbo.git third_party/libjpeg-turbo
  git -C third_party/libjpeg-turbo rev-parse HEAD
  # Output: 36956793b60deb2d1169d1432be6d19871f5ecb1
  ```

---

## 2. CMake Native NDK Linkage
- **CMake Subdirectory**: `add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/libjpeg-turbo ${CMAKE_CURRENT_BINARY_DIR}/libjpeg-turbo_build)`
- **Native Static Target**: `turbojpeg-static`
- **JNI Integration**: [`jpeg_encoder.cpp`](file:///C:/Users/space/AndroidStudioProjects/SmartFormatConverter/app/src/main/cpp/jpeg_encoder.cpp) includes `<turbojpeg.h>` directly from upstream `third_party/libjpeg-turbo` and calls `tjInitCompress()`, `tjCompress2()`, `tjDestroy()`, and `tjFree()`.
- **ABIs Built**: `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`.

---

## 3. Verification & Test Status Matrix

| Feature Area | Verification State | Evidence & Notes |
|---|---|---|
| **Upstream Acquisition** | **VERIFIED** | Cloned genuine `https://github.com/libjpeg-turbo/libjpeg-turbo.git` @ `36956793b60deb2d1169d1432be6d19871f5ecb1`. |
| **NDK CMake Build** | **BUILD PASS** | `app:assembleDebug` built `libjpeg_encoder_native.so` linked with `turbojpeg-static`. |
| **JNI TurboJPEG API** | **BUILD PASS** | Native JNI bridge invokes upstream `tjCompress2` with `TJSAMP_444`, `TJSAMP_422`, `TJSAMP_420`. |
| **Silent Fallback Removal**| **IMPLEMENTED_NOT_TESTED** | Throws explicit `IllegalStateException` error if native libjpeg fails. |
| **JUnit Unit Tests** | **UNIT TEST PASS** | `app:testDebugUnitTest` passed (7 tests passed, 0 failed). |
| **Physical Device E2E** | **BLOCKED** | No physical Android hardware / ADB emulator attached on build host. |

---

## 4. Build & Unit Test Execution Logs
- `app:assembleDebug` — **PASSED**
- `app:testDebugUnitTest` — **PASSED (7 passed, 0 failed)**
