# Smart Format Converter — Active Technical Risks

Updated 2026-10-25. The active conversion engine is AndroidX Media3,
not RxFFmpeg. This list records genuine release limitations.

1. **Codec availability differs by device.** WebM export uses the Media3
   WebmMuxer and requires VP9 video and Opus audio encoders. The format is
   hidden when either encoder is unavailable. MP4 H.264/H.265 encoding and
   source decoding also vary by chipset. Do not promise universal support.

2. **Output integrity is structural, not full playback certification.**
   Export checks container magic, expected codec/track types, durations when
   available and non-empty media samples. Long-form playback, seeking,
   A/V synchronization and retained audio need device-side verification.

3. **Interrupted foreground operations.** Conversion and direct downloads
   may be stopped by Android foreground-service restrictions or OS process
   management. Pause/cancel and queue recovery need device regression tests.

4. **Large media and storage.** Bitmap exports can exhaust app heap on very
   large images. SAF copy can fail due to permission or space exhaustion.
   Temporary outputs must be deleted on failure. Do not silently rescale
   original-quality exports.

5. **Legacy configurations and queues.** FFmpeg-era output requests and
   unsupported CRF, subtitle, routing or remux settings are rejected with
   actionable errors, rather than creating mismatched file extensions.

6. **Build versus device validation.** CI assembles APK/AAB and compiles
   Android instrumented tests. Real device smoke tests, production signing
   and Google Play review remain separate release gates.

7. **YouTube/yt-dlp.** Python/JS runtime extraction is not bundled.
   Direct HTTP/HTTPS downloader can only handle URLs and streams supported
   by its own direct-download policy.
