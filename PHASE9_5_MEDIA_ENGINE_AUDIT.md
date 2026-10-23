# PHASE 9.5 — MEDIA ENGINES & QUEUE AUDIT REPORT

## Media Conversion Engines
1. **Native JPEG Engine (`JpegNativeEncoder`)**:
   - Uses genuine TurboJPEG 3 API (`tj3Init`, `tj3Set`, `tj3Compress8`, `tj3Free`, `tj3Destroy`).
   - Supports 4:4:4, 4:2:2, and 4:2:0 chroma subsampling modes.
2. **FFmpeg Engine (`FFmpegEngine`)**:
   - Uses RxFFmpeg native binding for video and audio transcoding.
3. **Downloader Engine (`HttpDownloaderEngine` / `YtDlpEngine`)**:
   - `HttpDownloaderEngine` provides direct HTTP stream downloading with progress callback.
   - `YtDlpEngine` is documented as an HTTP fallback extractor. Full Python/JS `yt-dlp` extraction engine is explicitly marked `NOT_IMPLEMENTED` on-device.

## Queue & Storage Engine
- **Room Job Queue**: `ConversionJobDao` persists jobs in Room database.
- **Staging & Safely Cleaning**: Conversion repository writes to temp cache before staging into SAF destination directory.
- **Interrupted Job Cleanup**: Stale/failed temp files are cleaned up on conversion cancellation.

## Status
- **Media Conversion Engines Audit**: VERIFIED
- **YtDlp Engine Classification**: NOT_IMPLEMENTED (Direct HTTP stream download used instead)
