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

5. **Legacy configurations and queues.** Unsupported FFmpeg-era output requests, CRF, subtitle and routing settings are rejected with actionable errors. Strict MP4 remux is available only for a single AVC/HEVC video and at most one AAC audio track without subtitles or extra streams. Smart Auto falls back to transcoding when incompatible.

6. **Build versus device validation.** CI assembles APK/AAB and compiles
   Android instrumented tests. Real device smoke tests, production signing
   and Google Play review remain separate release gates.

7. **New P6–P10 output formats.** AAC, OGG/Opus, WAV, FLAC, HEIC, AVIF and GIF (still only) need on-device verification; do not assume compiler success guarantees codec support. AndroidX HeifWriter declares minSdk 28 and app keeps minSdk 26 using a manifest override with runtime API gating. Test API 26 launch explicitly. MP3 and MKV output remain unsupported pending an audited encoder/muxer. Animated GIF is NOT retained.

8. **YouTube/yt-dlp.** Python/JS runtime extraction is not bundled.
   Direct HTTP/HTTPS downloader can only handle URLs and streams supported
   by its own direct-download policy.
