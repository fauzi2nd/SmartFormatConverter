package org.roruku.smartformatconverter.engine.media

import org.json.JSONObject

/**
 * Versioned immutable snapshots stored in ConversionJobEntity.customParamsJson.
 * The engine never reads mutable Compose state after a job is queued.
 */
data class ImageAdvancedSettings(
    val format: String = "jpg",
    val jpegQuality: Int = 100,
    val jpegSubsampling: String = "encoder",
    val webpQuality: Int = 95,
    val webpLossless: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    val keepAspectRatio: Boolean = true,
    val cropToExactRatio: Boolean = false,
    val preventUpscale: Boolean = true,
    val preserveMetadata: Boolean = true,
    val preserveGps: Boolean = false,
    val matteColor: String = "white",
    val preset: String = "Custom",
) {
    fun applyPreset(name: String): ImageAdvancedSettings = when (name) {
        "Smart Auto" -> copy(preset = name, jpegQuality = 92, jpegSubsampling = "encoder",
            webpQuality = 90, webpLossless = false)
        "High Quality" -> copy(preset = name, jpegQuality = 100, jpegSubsampling = "encoder",
            webpQuality = 100, webpLossless = false)
        "Balanced" -> copy(preset = name, jpegQuality = 90, jpegSubsampling = "encoder",
            webpQuality = 85, webpLossless = false)
        "Small File" -> copy(preset = name, jpegQuality = 75, jpegSubsampling = "encoder",
            webpQuality = 70, webpLossless = false)
        else -> copy(preset = "Custom")
    }

    fun toJson(): String = JSONObject().apply {
        put("kind", "image")
        put("schemaVersion", 1)
        put("format", format)
        put("preset", preset)
        put("jpegQuality", jpegQuality)
        put("jpegSubsampling", jpegSubsampling)
        put("webpQuality", webpQuality)
        put("webpLossless", webpLossless)
        width?.let { put("width", it) }
        height?.let { put("height", it) }
        put("keepAspectRatio", keepAspectRatio)
        put("cropToExactRatio", cropToExactRatio)
        put("preventUpscale", preventUpscale)
        put("preserveMetadata", preserveMetadata)
        put("preserveGps", preserveGps)
        put("matteColor", matteColor)
    }.toString()

    companion object {
        fun fromJson(json: String): ImageAdvancedSettings {
            val obj = JSONObject(json)
            require(obj.optString("kind") == "image" && obj.optInt("schemaVersion") == 1) {
                "Unsupported image settings snapshot"
            }
            return ImageAdvancedSettings(
                format = obj.optString("format", "jpg"),
                preset = obj.optString("preset", "Custom"),
                jpegQuality = obj.optInt("jpegQuality", 100),
                jpegSubsampling = "encoder", // Legacy requests cannot force Bitmap.compress subsampling.
                webpQuality = obj.optInt("webpQuality", 95),
                webpLossless = obj.optBoolean("webpLossless", false),
                width = if (obj.has("width")) obj.getInt("width") else null,
                height = if (obj.has("height")) obj.getInt("height") else null,
                keepAspectRatio = obj.optBoolean("keepAspectRatio", true),
                cropToExactRatio = obj.optBoolean("cropToExactRatio", false),
                preventUpscale = obj.optBoolean("preventUpscale", true),
                preserveMetadata = obj.optBoolean("preserveMetadata", true),
                preserveGps = obj.optBoolean("preserveGps", false),
                matteColor = obj.optString("matteColor", "white")
            )
        }
    }
}

object ImageSettingsValidator {
    fun validate(s: ImageAdvancedSettings): String? = when {
        s.format !in setOf("jpg", "jpeg", "png", "webp", "heic", "avif", "gif") -> "Unsupported image format"
        s.jpegQuality !in 1..100 || s.webpQuality !in 1..100 -> "Image quality must be 1–100"
        s.jpegSubsampling != "encoder" -> "Android JPEG encoder does not expose chroma subsampling control"
        s.matteColor !in setOf("white", "black") -> "Unsupported transparency background"
        (s.width == null) != (s.height == null) -> "Set both image width and height"
        s.width != null && s.width !in 1..8192 -> "Image width must be 1–8192"
        s.height != null && s.height !in 1..8192 -> "Image height must be 1–8192"
        else -> null
    }
}

data class AudioAdvancedSettings(
    val format: String = "m4a",
    val bitrateKbps: Int = BitrateSelection.AUTO,
    // Legacy snapshot: any explicit rate is rejected until runtime support exists.
    val sampleRate: Int? = null,
    val channels: Int? = null,
    val volumePercent: Int = 100,
    val preserveMetadata: Boolean = true,
    val preset: String = "Custom",
    /** Audio MediaCodec implementation: auto or exact encoder name. */
    val encoder: String = "auto",
    /** Peak normalization targeting -1 dBFS, performed on PCM before encoding. */
    val normalizePeak: Boolean = false,
    /** Optional FLAC compression level (0 = fastest, 8 = smallest). */
    val flacCompressionLevel: Int? = null,
) {
    val isLossless: Boolean get() = format in setOf("wav", "flac")

    fun applyPreset(name: String): AudioAdvancedSettings {
        val initial = AudioAdvancedSettings(format = format)
        if (isLossless) return initial.copy(
            preset = if (name == "Custom") "Custom" else "Lossless"
        )
        return initial.copy(
            preset = if (name == "Lossless") "Smart Auto" else name,
            bitrateKbps = BitrateSelection.AUTO
        )
    }

    fun toJson(): String = JSONObject().apply {
        put("kind", "audio")
        put("schemaVersion", 1)
        put("format", format)
        put("preset", preset)
        put("bitrateKbps", bitrateKbps)
        sampleRate?.let { put("sampleRate", it) }
        channels?.let { put("channels", it) }
        put("volumePercent", volumePercent)
        put("preserveMetadata", preserveMetadata)
        put("encoder", encoder)
        put("normalizePeak", normalizePeak)
        flacCompressionLevel?.let { put("flacCompressionLevel", it) }
    }.toString()

    companion object {
        fun fromJson(json: String): AudioAdvancedSettings {
            val obj = JSONObject(json)
            require(obj.optString("kind") == "audio" && obj.optInt("schemaVersion") == 1) {
                "Unsupported audio settings snapshot"
            }
            return AudioAdvancedSettings(
                format = obj.optString("format", "mp3"), // Fail safely for FFmpeg-era jobs missing a format.
                preset = obj.optString("preset", "Custom"),
                bitrateKbps = obj.optInt("bitrateKbps", 192),
                sampleRate = if (obj.has("sampleRate")) obj.getInt("sampleRate") else null,
                channels = if (obj.has("channels")) obj.getInt("channels") else null,
                volumePercent = obj.optInt("volumePercent", 100),
                preserveMetadata = obj.optBoolean("preserveMetadata", true),
                encoder = obj.optString("encoder", "auto"),
                normalizePeak = obj.optBoolean("normalizePeak", false),
                flacCompressionLevel = if (obj.has("flacCompressionLevel"))
                    obj.getInt("flacCompressionLevel") else null
            )
        }
    }
}

/** Media3 audio exporters: M4A, ADTS AAC, Ogg Opus, or decoded PCM16 WAV. */
object AudioSettingsValidator {
    val losslessFormats: Set<String> = setOf("wav", "flac")

    fun validate(s: AudioAdvancedSettings): String? = when {
        s.format !in setOf("m4a", "aac", "ogg", "wav", "flac") -> "Unsupported audio container"
        s.bitrateKbps !in 32..512 &&
            s.bitrateKbps !in setOf(BitrateSelection.AUTO, BitrateSelection.SOURCE) ->
            "Audio bitrate must be Auto, Source, or 32–512 kbps"
        s.sampleRate != null && s.sampleRate !in setOf(32000, 44100, 48000, 96000) ->
            "Unsupported audio sample rate"
        s.sampleRate == 96000 && s.format != "wav" ->
            "96 kHz is available only for WAV output"
        s.format == "ogg" && s.sampleRate != null && s.sampleRate != 48000 ->
            "Opus output requires 48 kHz"
        s.channels != null && s.channels !in setOf(1, 2) -> "Only mono/stereo allowed"
        s.volumePercent !in 0..200 -> "Audio volume must be between 0% and 200%"
        s.flacCompressionLevel != null &&
            (s.format != "flac" || s.flacCompressionLevel !in 0..8) ->
            "FLAC compression level must be 0–8 and only applies to FLAC"
        s.encoder.isBlank() -> "Select a valid audio encoder"
        else -> null
    }
}
