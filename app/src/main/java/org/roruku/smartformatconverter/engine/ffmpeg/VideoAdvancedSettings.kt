package org.roruku.smartformatconverter.engine.ffmpeg

import org.json.JSONObject

/**
 * Immutable job snapshot. Values are stored in ConversionJobEntity.customParamsJson,
 * not read from mutable UI preferences when a queued conversion starts.
 *
 * Source resolution/FPS/sample rate/channels are represented by null and
 * ignored by the Media3 path unless explicitly supported.
 */
data class VideoAdvancedSettings(
    val schemaVersion: Int = 1,
    val format: String = "mp4",
    val preset: String = "Custom",
    val width: Int? = null,
    val height: Int? = null,
    val fps: String? = null,
    val preventUpscale: Boolean = true,
    val videoCodec: String = "auto",
    val encoder: String = "auto",
    val rateControl: String = "vbr",
    val crf: Int = 23,
    val videoBitrateKbps: Int = 8000,
    val pixelFormat: String = "auto",
    val audioCodec: String = "auto",
    val audioBitrateKbps: Int = 192,
    val volumePercent: Int = 100,
    val audioStream: String = "first",
    val sampleRate: Int? = null,
    val channels: Int? = null,
    val subtitleMode: String = "auto",
    val preserveMetadata: Boolean = true,
    val fastStart: Boolean = true,
) {
    val isRemux: Boolean get() = preset.equals("Original Quality / Remux", ignoreCase = true)

    fun applyPreset(name: String): VideoAdvancedSettings = when (name) {
        "Original Quality / Remux" -> copy(preset = name)
        // Media3 uses encoder target bitrate, not libx264 CRF. Presets must
        // adjust the setting actually consumed by the runtime.
        "High Quality" -> copy(preset = name, crf = 18, rateControl = "vbr", videoBitrateKbps = 12000)
        "Balanced" -> copy(preset = name, crf = 23, rateControl = "vbr", videoBitrateKbps = 6000)
        "Small File" -> copy(preset = name, crf = 28, rateControl = "vbr", videoBitrateKbps = 2500)
        else -> copy(preset = "Custom")
    }

    fun toJson(): String = JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("format", format)
        put("preset", preset)
        if (width != null) put("width", width)
        if (height != null) put("height", height)
        if (fps != null) put("fps", fps)
        put("preventUpscale", preventUpscale)
        put("videoCodec", videoCodec)
        put("encoder", encoder)
        put("rateControl", rateControl)
        put("crf", crf)
        put("videoBitrateKbps", videoBitrateKbps)
        put("pixelFormat", pixelFormat)
        put("audioCodec", audioCodec)
        put("audioBitrateKbps", audioBitrateKbps)
        put("volumePercent", volumePercent)
        put("audioStream", audioStream)
        if (sampleRate != null) put("sampleRate", sampleRate)
        if (channels != null) put("channels", channels)
        put("subtitleMode", subtitleMode)
        put("preserveMetadata", preserveMetadata)
        put("fastStart", fastStart)
    }.toString()

    companion object {
        fun fromJson(json: String): VideoAdvancedSettings {
            val obj = JSONObject(json)
            require(obj.optInt("schemaVersion", 1) == 1) {
                "Unsupported video settings schema"
            }
            return VideoAdvancedSettings(
                format = obj.optString("format", "mp4"),
                preset = obj.optString("preset", "Custom"),
                width = obj.optInt("width").takeIf { obj.has("width") },
                height = obj.optInt("height").takeIf { obj.has("height") },
                fps = obj.optString("fps", "").ifBlank { null },
                preventUpscale = obj.optBoolean("preventUpscale", true),
                videoCodec = obj.optString("videoCodec", "auto"),
                encoder = obj.optString("encoder", "auto"),
                rateControl = obj.optString("rateControl", "crf"),
                crf = obj.optInt("crf", 23),
                videoBitrateKbps = obj.optInt("videoBitrateKbps", 8000),
                pixelFormat = obj.optString("pixelFormat", "auto"),
                audioCodec = obj.optString("audioCodec", "auto"),
                audioBitrateKbps = obj.optInt("audioBitrateKbps", 192),
                volumePercent = obj.optInt("volumePercent", 100),
                audioStream = obj.optString("audioStream", "first"),
                sampleRate = obj.optInt("sampleRate").takeIf { obj.has("sampleRate") },
                channels = obj.optInt("channels").takeIf { obj.has("channels") },
                subtitleMode = obj.optString("subtitleMode", "auto"),
                preserveMetadata = obj.optBoolean("preserveMetadata", true),
                fastStart = obj.optBoolean("fastStart", true),
            )
        }
    }
}

/**
 * Validates the active Media3 export settings. Unsupported legacy FFmpeg
 * options are additionally rejected by Media3OutputPolicy for queued snapshots.
 */
object VideoSettingsValidator {
    fun validate(s: VideoAdvancedSettings): String? {
        if (s.schemaVersion != 1) return "Unsupported configuration version"
        if (s.format !in setOf("mp4", "webm")) return "Unsupported video output container"
        if (s.videoCodec !in setOf("auto", "h264", "h265", "hevc", "vp9")) {
            return "Unsupported output video codec"
        }
        if (s.audioCodec !in setOf("auto", "aac", "opus", "none")) {
            return "Unsupported output audio codec"
        }
        if (s.format == "webm" && s.videoCodec !in setOf("auto", "vp9")) {
            return "WebM requires VP9 video"
        }
        if (s.format == "webm" && s.audioCodec !in setOf("auto", "opus", "none")) {
            return "WebM requires Opus audio or no audio"
        }
        if (s.format == "mp4" && s.videoCodec !in setOf("auto", "h264", "h265", "hevc")) {
            return "MP4 requires H.264 or H.265 video"
        }
        if (s.format == "mp4" && s.audioCodec !in setOf("auto", "aac", "none")) {
            return "MP4 requires AAC audio or no audio"
        }
        if (s.videoBitrateKbps !in 100..200000) return "Video bitrate must be 100–200000 kbps"
        if (s.audioBitrateKbps !in 32..1024) return "Audio bitrate must be 32–1024 kbps"
        if ((s.width == null) != (s.height == null)) return "Set both width and height"
        val width = s.width
        val height = s.height
        if (width != null && height != null && (width !in 16..8192 ||
                height !in 16..8192 || width % 2 != 0 || height % 2 != 0)) {
            return "Resolution must use even dimensions between 16 and 8192"
        }
        if (s.fps != null && (s.fps.toDoubleOrNull() == null ||
                s.fps.toDouble() !in 1.0..240.0)) return "Invalid FPS"
        if (s.rateControl !in setOf("crf", "vbr", "cbr")) return "Invalid bitrate mode"
        if (s.crf !in 0..51) return "Invalid legacy CRF field"
        if (s.encoder !in setOf("auto", "software", "hardware")) return "Invalid encoder"
        if (s.volumePercent !in 0..200) return "Invalid volume"
        if (s.audioStream !in setOf("first", "second", "all", "none")) return "Invalid audio route"
        if (s.subtitleMode !in setOf("auto", "copy", "none")) return "Invalid subtitle mode"
        if (s.pixelFormat !in setOf("auto", "yuv420p", "yuv420p10le", "yuv422p", "yuv444p")) {
            return "Invalid pixel format"
        }
        if (s.sampleRate != null && s.sampleRate !in setOf(32000, 44100, 48000, 96000)) {
            return "Invalid sample rate"
        }
        if (s.channels != null && s.channels !in setOf(1, 2, 6)) return "Invalid channel count"
        return null
    }
}
