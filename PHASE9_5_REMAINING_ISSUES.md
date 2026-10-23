# PHASE 9.5 — REMAINING ISSUES & LIMITATIONS REPORT

## Open Limitations
1. **Full On-Device yt-dlp Extraction**: Complex YouTube JS signature extraction is NOT_IMPLEMENTED due to Python/JS runtime dependencies on Android. Direct HTTP streaming is supported via `HttpDownloaderEngine`.
2. **On-Device Instrumentation Tests**: Physical Android device / emulator runtime instrumentation testing is marked `BLOCKED` in local headless CLI environment (unit tests and JVM tests pass 100%).

## Resolved Issues in Phase 9.5
- Unregistered gitlink fixed via `.gitmodules`.
- Native C++ buffer overflow & JNI resource leaks fixed.
- Real JPEG output full decoding validation added.
- Full EXIF 8-mode orientation normalization implemented.
- Deprecated Compose Material Icons replaced.
- GitHub Actions CI workflow added.
