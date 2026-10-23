# Smart Format Converter — Known Issues & Technical Risks

## Technical Risks & Mitigation Strategies

1. **Storage Access Framework (SAF) Performance & File Scoped Access**
   - *Risk*: `content://` URIs do not allow direct file path access (`/storage/emulated/0/...`) for standard C/C++ file I/O in FFmpeg without File Descriptor (FD) access or local staging.
   - *Mitigation*: Pipe via Android File Descriptors (`ParcelFileDescriptor.detachFd()`) or temporary input/output cache files (`ContentResolver.openInputStream` / `openOutputStream`) when interacting with native engines.

2. **Large File Memory Allocation**
   - *Risk*: OutOfMemory (OOM) errors when processing high-resolution images or high-bitrate video streams in memory.
   - *Mitigation*: Use streaming I/O, downsampled bounds decoding for previews, and delegate heavy media processing strictly to native FFmpeg / native memory buffers.

3. **Background Process Termination on Android 12+ / 14+**
   - *Risk*: Long-running video conversions or batch downloads killed by OS power management or foreground service restrictions.
   - *Mitigation*: Properly configure Foreground Service with `foregroundServiceType="dataSync"` or `shortService` / `mediaPlayback`, maintain explicit ongoing notifications with foreground service start mechanisms, handle task interruption/resume states.

4. **yt-dlp Runtime Environment**
   - *Risk*: Extractor breakage or requirement of a JS runtime / Python execution environment.
   - *Mitigation*: Build a robust, isolated yt-dlp execution pipeline with error parsing, fallback options, and update mechanisms.
