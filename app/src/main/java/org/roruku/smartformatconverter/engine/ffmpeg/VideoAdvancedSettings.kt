package org.roruku.smartformatconverter.engine.ffmpeg

import org.json.JSONObject

/**
 * Immutable job snapshot. Values are stored in ConversionJobEntity.customParamsJson,
 * not read from mutable UI preferences when a queued conversion starts.
 *
 * Source resolution/FPS/sample rate/channels are represented by null and
 * intentionally omitted from the FFmpeg command.
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
    val rateControl: String = "crf",
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
        "High Quality" -> copy(preset = name, crf = 18, rateControl = "crf")
        "Balanced" -> copy(preset = name, crf = 23, rateControl = "crf")
        "Small File" -> copy(preset = name, crf = 28, rateControl = "crf")
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
 * Strict allow-list and output-container checks. Run both before queueing and
 * again before executing (queued jobs may come from older app versions).
 */
object VideoSettingsValidator {
    private val containers = setOf("mp4", "mkv", "webm", "mov", "avi")
    private val codecs = setOf("auto", "h264", "h265", "vp9", "av1", "copy")
    private val audio = setOf("auto", "aac", "mp3", "opus", "vorbis", "flac", "pcm", "copy", "none")
    private val pixels = setOf("auto", "yuv420p", "yuv420p10le", "yuv422p", "yuv444p")
    private val rateModes = setOf("crf", "vbr", "cbr")

    fun videoCodec(settings: VideoAdvancedSettings): String = when (settings.videoCodec) {
        "auto" -> if (settings.format == "webm") "vp9" else "h264"
        else -> settings.videoCodec
    }

    fun audioCodec(settings: VideoAdvancedSettings): String = when (settings.audioCodec) {
        "auto" -> when (settings.format) {
            "webm" -> "opus"
            "avi" -> "mp3"
            else -> "aac"
        }
        else -> settings.audioCodec
    }

    fun validate(s: VideoAdvancedSettings): String? {
        if (s.schemaVersion != 1) return "Unsupported configuration version"
        if (s.format !in containers) return "Unsupported output container: ${s.format}"
        if (s.videoCodec !in codecs) return "Unsupported video codec"
        if (s.audioCodec !in audio) return "Unsupported audio codec"
        if (s.encoder !in setOf("auto", "software", "hardware")) return "Invalid encoder selection"
        if (s.rateControl !in rateModes || s.crf !in 0..51) return "Invalid rate control or CRF"
        if (s.videoBitrateKbps !in 100..200000) return "Video bitrate must be 100–200000 kbps"
        if (s.audioBitrateKbps !in 32..1024) return "Audio bitrate must be 32–1024 kbps"
        if (s.volumePercent !in 0..200) return "Volume must be between 0% and 200%"
        if (s.pixelFormat !in pixels) return "Unsupported pixel format"
        if (s.audioStream !in setOf("first", "all", "second", "none")) return "Invalid audio stream selection"
        if (s.subtitleMode !in setOf("auto", "none", "copy")) return "Invalid subtitle selection"
        if ((s.width == null) != (s.height == null)) return "Set both width and height"
        if (s.width != null && (s.width !in 16..8192 || s.height !in 16..8192 ||
                    s.width % 2 != 0 || s.height % 2 != 0)) {
            return "Resolution must use even dimensions between 16 and 8192"
        }
        if (s.fps != null && (s.fps.toDoubleOrNull() == null ||
                    s.fps.toDouble() !in 1.0..240.0)) return "FPS must be between 1 and 240"
        if (s.sampleRate != null && s.sampleRate !in setOf(32000, 44100, 48000, 96000)) {
            return "Unsupported audio sample rate"
        }
        if (s.channels != null && s.channels !in setOf(1, 2, 6)) return "Unsupported audio channel count"

        val video = videoCodec(s)
        val sound = audioCodec(s)
        if (s.format == "webm" && video !in setOf("vp9", "av1")) {
            return "WebM requires VP9 or AV1 video"
        }
        if (s.format in setOf("mp4", "mov") && video !in setOf("h264", "h265", "av1", "copy")) {
            return "MP4/MOV requires H.264, H.265 or AV1"
        }
        if (s.format == "avi" && video !in setOf("h264", "copy")) {
            return "AVI: select H.264 or stream copy"
        }
        if (s.format == "webm" && sound !in setOf("opus", "vorbis", "none", "copy")) {
            return "WebM supports Opus or Vorbis audio"
        }
        if (s.format in setOf("mp4", "mov") && sound !in setOf("aac", "mp3", "none", "copy")) {
            return "MP4/MOV: use AAC or MP3 audio"
        }
        if (s.format == "avi" && sound !in setOf("mp3", "pcm", "none", "copy")) {
            return "AVI: use MP3 or PCM audio"
        }
        if (s.isRemux && s.format == "webm") {
            return "Remux to WebM requires source codec verification; use another preset"
        }
        if (s.isRemux) return null // No transcode settings are applied.
        if (video == "copy" && (s.width != null || s.fps != null || s.pixelFormat != "auto")) {
            return "Video stream copy cannot resize, change FPS, or change pixel format"
        }
        if (video == "copy" && s.rateControl != "crf") {
            return "Video stream copy cannot use bitrate rate control"
        }
        if (s.encoder == "hardware") {
            if (video !in setOf("h264", "h265")) return "Hardware encoding supports H.264/H.265 only"
            if (s.rateControl == "crf") return "Hardware encoder requires VBR or CBR, not CRF"
            if (s.pixelFormat !in setOf("auto", "yuv420p")) {
                return "Hardware encoding requires Auto or yuv420p pixel format"
            }
        }
        if (sound == "copy" && (s.volumePercent != 100 || s.sampleRate != null || s.channels != null)) {
            return "Audio stream copy cannot change volume, sample rate or channels"
        }
        if (sound == "none" && s.audioStream != "none" && s.audioStream != "first") {
            return "No audio selected: remove audio stream selection"
        }
        if (sound == "opus" && s.sampleRate != null &&
            s.sampleRate !in setOf(48000)) {
            return "Opus output sample rate must be 48 kHz"
        }
        if (s.audioStream == "all" && s.volumePercent != 100) {
            return "Select a single audio stream before changing volume"
        }
        if (s.subtitleMode == "copy" && s.format != "mkv") {
            return "Subtitle stream copy is supported only for MKV in this version"
        }
        return null
    }
}
