# Adaptive Quality Presets V2 — P1–P7

## Source-aware behavior

Smart Auto is the initial selection for newly created video, image and audio
jobs, including WebM. Existing serialized queue settings remain immutable and
their manually entered bitrates override the adaptive planner.

The video quality planner lives in
`engine/media/AdaptivePresetPlanner.kt` and is shared by the Media3 engine and
the Converter preview. It estimates an SDR 30-fps H.264 base rate from 480p,
720p, 1080p, 1440p, and UHD anchors (2, 4, 8, 16, 35 Mbps), interpolating
between resolutions. FPS scales sublinearly (sqrt(fps/30)). Multipliers:

| Preset | H.264 rate multiplier |
| --- | ---: |
| High Quality | 1.00 |
| Smart Auto | 0.76 |
| Balanced | 0.60 |
| Small File | 0.34 |

Codec adjustment: H.265/HEVC 0.67, VP9 0.72, H.264 1.00. These
are **tunable initial heuristics**, not universal compression efficiencies.
Where source bitrate is known, auto targets are conservatively limited to
1.25× (High), 1.10× (Smart Auto), 1.00× (Balanced), or 0.75× (Small) source
video bitrate. The planner does not upscale video or change FPS by default.

For Custom, explicit bitrate values remain exact; Auto uses a safe balanced
fallback, Source reuses the source track bitrate when available. Video
container compatibility and encoder availability remain enforced by Media3.

Audio targets are codec/channel/preset aware, bounded by known input bitrate
for Auto, and honor explicitly entered manual/Source bitrates. Codec-based
video audio profiles are independent of the bitrate of an audio-only export.

## Estimates and growth warnings

The preview uses `MediaProbe` on selected files and shares the same planner
as export. It displays a bitrate target, estimated size, wide potential range
(75–135% of target estimate), total batch estimate when all durations are
known, and prompts for confirmation on possible file growth. Source metadata
may be missing and hardware encoder output rate control is imperfect.

Estimates are not guarantees, particularly for screen recordings, live-action
motion, remux paths, codec fallback, or hardware encoder behavior. Do not
increase input bitrate solely to make an output claim of high quality; no
transcoder can reconstruct detail missing from a compressed source.

## P7: Objective quality benchmark

Use the developer-only script `tools/benchmark_quality.py`. It requires
external `ffmpeg` and `ffprobe` executables on PATH; **no FFmpeg binary is
bundled into the Android application**.

Example:

```sh
python tools/benchmark_quality.py input.mp4 converted.webm --seconds 30 --fps 30 --vmaf --json-out reports/webm-quality.json
```

For an optional release gate:

```sh
python tools/benchmark_quality.py input.mp4 converted.mp4 --min-ssim 0.94 --max-size-ratio 1.10
```

The tool records SSIM, PSNR, optional VMAF (only where `libvmaf` is installed)
and byte-size ratio. It normalizes both inputs to source dimensions and a
common sampled FPS and examines at most the first 30 seconds by default.
Objective scores are complementary to visual/audio inspection and can be
content-dependent. A reproducible comparison requires a curated corpus
(low-motion, action, noise, animations, portrait, 30/60-fps, 480p–4K).

JUnit tests cover planner bounds, source caps, codec profiles, manual
overrides, audio target selection, size estimation and warning paths.
Android instrumentation tests still require execution on actual devices.

## Inspect WebM / Opus metadata when FFmpeg cannot open audio

FFprobe returning `{` and exit code 1, or reporting `Audio: opus, 48000 Hz, 0 channels`,
is **not** a successful quality benchmark. Do not declare a file valid based
only on Android MediaExtractor or Media3 Transformer success.

A deterministic offline inspector is available, using Python standard library
only (no FFmpeg required):

```powershell
python .\tools\inspect_webm_opus.py "C:\Benchmark\VID_001.webm" --json-out "reports\webm-opus-inspection.json"
```

Review `codec_private_length`, `opus_head_channels`,
`audio_element_channels`, `opus_mapping_family`, and `issues`.
The tool inspects the actual EBML TrackEntry bytes written to disk and can
distinguish invalid OpusHead from a decoder or packet-level problem. It does
not prove the Opus packets themselves decode correctly. Inspect output from
the *exact* file produced with the current version of the application.

For separate video-only metrics, you may copy the VP9 stream into a temporary
MKV without re-encoding, but that does not repair the original WebM audio.
Never discard audio in production silently.
