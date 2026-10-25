# P6–P10 Implementation Review — 2026-10-25

Branch: `master`. All changes use Android / Kotlin / Java APIs; no FFmpeg
or additional application-owned JNI/native binary has been introduced.

## Implemented and integrated

| Phase | Output | Implementation | Condition |
| --- | --- | --- | --- |
| P6 | AAC (.aac) | Media3 AacMuxer, ADTS bitstream | AAC encoder available |
| P6 | OGG (.ogg) | Media3 OggMuxer, Opus codec | Android 10+ with Opus encoder |
| P6 | WAV (.wav) | Audio MediaExtractor / MediaCodec decode to PCM16, RIFF writer | Source audio decodable; mono/stereo |
| P7 | FLAC (.flac) | Android MediaCodec FLAC encoder from validated PCM16 stage | FLAC encoder available, 8–48 kHz mono/stereo; vendor must return complete fLaC stream |
| P9 | MP4 Stream Copy | MediaExtractor + MediaMuxer; no re-encode | Exactly one AVC/HEVC video and up to one AAC audio track, without extra tracks |
| P9 | Smart Auto | Remux preflight, otherwise Media3 transcode | MP4 output; no requested resolution/codec/audio modifications |
| P10 | HEIC (.heic) | AndroidX HeifWriter, bitmap input | Android 10+, device HEIC/HEVC encoder |
| P10 | AVIF (.avif) | AndroidX AvifWriter, bitmap input | Android 14+, device AV1 encoder |
| P10 | GIF (.gif) | Square Java GIF89a encoder | Still frame only, <= 2048×2048, 256 colors; transparency is white |

Every exported file is verified before publication to SAF. The check validates
container header, track type / codec and extractable sample presence where
applicable. **This is not a full playback or media quality certification.**
Output files are deleted when a conversion fails/cancels.

## Not implemented: P7 MP3

Android's standard MediaCodec media format table has an MP3 decoder but no
MP3 encoder. Delivering a real .mp3 exporter requires a separately audited
encoder such as LAME (native code requiring ABI/16 KB alignment review), or
a verified pure-Java MP3 encoder. No codec will be advertised until it can
actually encode, be decoded, and pass device tests.

## Not implemented: P8 MKV export

AndroidX Media3 includes Matroska input extraction, not a production MKV
export muxer. JCodec has a Java Matroska muxer, but reliable multi-track
AAC, codec initialization, timestamp ordering and subtitle preservation need
a compatibility spike and tests before advertising output. In particular,
never silently discard extra audio tracks, subtitles, attachments or chapters.
MKV **input** can still be remuxed to MP4 only when it contains compatible
H.264/HEVC + AAC tracks and no unsupported extra tracks.

## GIF animation

The current GIF exporter converts a **still image into a single-frame GIF**.
It does not encode multiple input images into an animation and it does
not preserve animation from an existing source GIF. Do not claim animated GIF
conversion, and clearly show that limitation in the Advanced Settings UI.

## Release quality gates

- CI: `assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest`,
  `lintDebug`, `bundleRelease`, `lintRelease`, and ELF 16 KB page alignment.
- On-device: run instrumented conversion smoke tests for AAC, WAV, OGG,
  FLAC, HEIC, AVIF, GIF and MP4 remux. Conditional tests skip unsupported
  hardware encoders; run on multiple Android versions and chipsets.
- Manual: verify playback and seeking, output metadata/orientation, size,
  audio fidelity, transparency rules, save permissions, cancellation, and
  large files. Existing GIF animation must not be silently presented as preserved.
- Release: verify AAB upload signing separately.

## External dependencies and licenses

- AndroidX Media3 muxer: AndroidX Apache-2.0.
- AndroidX HeifWriter: AndroidX Apache-2.0, library minSdk 28;
  app minSdk 26 retained via documented manifest override, writer only called
  on API 29+ (HEIC) and API 34+ (AVIF). Test app launch on API 26 explicitly.
- Square GifEncoder 0.10.1: Apache-2.0; static GIF encoder without transparency.

Documentation:
- https://developer.android.com/media/media3/transformer/supported-formats
- https://developer.android.com/media/platform/supported-formats
- https://developer.android.com/reference/androidx/heifwriter/AvifWriter
- https://github.com/square/gifencoder
