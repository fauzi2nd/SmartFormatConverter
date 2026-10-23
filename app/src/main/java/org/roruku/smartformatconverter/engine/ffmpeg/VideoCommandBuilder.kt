package org.roruku.smartformatconverter.engine.ffmpeg

import java.util.Locale

/**
 * Builds an argument ARRAY (not a shell string); no external shell escaping
 * or user-provided arbitrary FFmpeg arguments are accepted.
 */
object VideoCommandBuilder {
    fun build(inputPath: String, outputPath: String, settings: VideoAdvancedSettings): List<String> {
        VideoSettingsValidator.validate(settings)?.let { throw IllegalArgumentException(it) }

        val args = mutableListOf("ffmpeg", "-hide_banner", "-nostdin", "-y", "-i", inputPath)

        if (settings.isRemux) {
            args += listOf("-map", "0", "-c", "copy")
            if (settings.preserveMetadata) args += listOf("-map_metadata", "0")
            else args += listOf("-map_metadata", "-1")
            if (settings.format == "mp4" && settings.fastStart) {
                args += listOf("-movflags", "+faststart")
            }
            args += outputPath
            return args
        }

        args += listOf("-map", "0:v:0")

        val audioCodec = VideoSettingsValidator.audioCodec(settings)
        if (audioCodec != "none" && settings.audioStream != "none") {
            val mapping = when (settings.audioStream) {
                "all" -> "0:a?"
                "second" -> "0:a:1"  // Intentional failure when track #2 is unavailable.
                else -> "0:a:0?"
            }
            args += listOf("-map", mapping)
        }

        // 'auto' safely keeps subtitles only in Matroska; MP4/MOV/WebM
        // subtitle codecs vary, so don't silently mux incompatible streams.
        val keepSubtitles = settings.subtitleMode == "copy" ||
            (settings.subtitleMode == "auto" && settings.format == "mkv")
        if (keepSubtitles) args += listOf("-map", "0:s?")

        val video = VideoSettingsValidator.videoCodec(settings)
        val codecName = when (video) {
            "h264" -> if (settings.encoder == "hardware") "h264_mediacodec" else "libx264"
            "h265" -> if (settings.encoder == "hardware") "hevc_mediacodec" else "libx265"
            "vp9" -> "libvpx-vp9"
            "av1" -> "libsvtav1"
            "copy" -> "copy"
            else -> throw IllegalArgumentException("Unsupported codec: $video")
        }
        args += listOf("-c:v", codecName)

        if (video != "copy") {
            settings.width?.let { w ->
                val h = requireNotNull(settings.height)
                val filter = if (settings.preventUpscale) {
                    "scale=w='min($w,iw)':h='min($h,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2"
                } else {
                    "scale=w=$w:h=$h:force_original_aspect_ratio=decrease:force_divisible_by=2"
                }
                args += listOf("-vf", filter)
            }
            settings.fps?.let { args += listOf("-r", it) }

            when (settings.rateControl) {
                "crf" -> {
                    when (video) {
                        "h264", "h265" -> {
                            args += listOf("-preset", "medium")
                            args += listOf("-crf", settings.crf.toString())
                        }
                        "vp9" -> args += listOf("-b:v", "0", "-crf", settings.crf.toString())
                        "av1" -> args += listOf("-preset", "8", "-crf", settings.crf.toString())
                    }
                }
                "vbr", "cbr" -> {
                    val bitrate = "${settings.videoBitrateKbps}k"
                    args += listOf("-b:v", bitrate)
                    if (settings.encoder != "hardware" && video in setOf("h264", "h265")) {
                        args += listOf("-preset", "medium")
                    }
                    if (settings.rateControl == "cbr") {
                        args += listOf(
                            "-minrate", bitrate,
                            "-maxrate", bitrate,
                            "-bufsize", "${settings.videoBitrateKbps * 2}k"
                        )
                    }
                }
            }
            val pixFmt = when {
                settings.pixelFormat != "auto" -> settings.pixelFormat
                video == "h264" || video == "vp9" -> "yuv420p"
                else -> null
            }
            pixFmt?.let { args += listOf("-pix_fmt", it) }
        }

        when (audioCodec) {
            "none" -> args += "-an"
            "copy" -> args += listOf("-c:a", "copy")
            else -> {
                val audioEncoder = when (audioCodec) {
                    "aac" -> "aac"
                    "mp3" -> "libmp3lame"
                    "opus" -> "libopus"
                    "vorbis" -> "libvorbis"
                    "flac" -> "flac"
                    "pcm" -> "pcm_s16le"
                    else -> error("Unknown audio codec")
                }
                args += listOf("-c:a", audioEncoder)
                if (audioCodec !in setOf("flac", "pcm")) {
                    args += listOf("-b:a", "${settings.audioBitrateKbps}k")
                }
                val audioRate = if (audioCodec == "opus") 48000 else settings.sampleRate
                audioRate?.let { args += listOf("-ar", it.toString()) }
                settings.channels?.let { args += listOf("-ac", it.toString()) }
                if (settings.volumePercent != 100) {
                    val multiplier = String.format(
                        Locale.US, "%.2f", settings.volumePercent / 100.0
                    )
                    args += listOf("-filter:a", "volume=$multiplier")
                }
            }
        }

        if (keepSubtitles) args += listOf("-c:s", "copy")
        if (settings.preserveMetadata) args += listOf("-map_metadata", "0")
        else args += listOf("-map_metadata", "-1")

        if (settings.format == "mp4" && settings.fastStart) {
            args += listOf("-movflags", "+faststart")
        }

        args += outputPath
        return args
    }
}
