# Smart Format Converter — Known Issues & Technical Risks

## Technical Risks & Mitigation Strategies

1. **Storage Access Framework (SAF) Performance & File Scoped Access**
   - *Risk*: `content://` URIs do not allow direct file path access (`/storage/emulated/0/...`) for standard C/C++ file I/O in FFmpeg without File Descriptor (FD) access or local staging.
   - *Mitigation*: Pipe via Android File Descriptors (`ParcelFileDescriptor.detachFd()`) or temporary input/output cache files (`ContentResolver.openInputStream` / `openOutputStream`) when interacting with native engines.

2. **Large File Memory Allocation**
   - *Risk*: OutOfMemory (OOM) errors when processing high-resolution images or high-bitrate video streams in memory.
   - *Mitigation*: Use streaming I/O, C++ RAII bounds/overflow checks (`width * height * 4`), downsampled bounds decoding for previews, and delegate heavy media processing strictly to native memory buffers.

3. **Background Process Termination on Android 12+ / 14+**
   - *Risk*: Long-running video conversions or batch downloads killed by OS power management or foreground service restrictions.
   - *Mitigation*: Configured Foreground Service with `foregroundServiceType="dataSync"`, maintained explicit ongoing notifications with foreground service start mechanisms, and handle task interruption/resume states.

4. **Full On-Device yt-dlp Python/JS Runtime Limitation**
   - *Risk*: On-device Python/JS runtime is not bundled due to APK size and security constraints.
   - *Mitigation*: Full Python/JS yt-dlp extraction is marked `NOT_IMPLEMENTED`. Direct HTTP stream downloading is implemented and verified via `HttpDownloaderEngine`.
