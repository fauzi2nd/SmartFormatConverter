package org.roruku.smartformatconverter.engine.media3

/**
 * Current export models. Legacy FFmpeg configurations are read only through
 * versioned AdvancedSettings snapshots and explicitly validated at queue time.
 */
enum class VideoOutputFormat(val extension: String, val mimeType: String) {
    MP4("mp4", "video/mp4"),
    WEBM("webm", "video/webm")
}

enum class AudioOutputFormat(val extension: String, val mimeType: String) {
    M4A("m4a", "audio/mp4")
}

enum class VideoPreset(val displayName: String, val targetBitrateKbps: Int) {
    REMUX("Original Quality / Remux", 0), // Old persisted jobs are rejected safely.
    HIGH_QUALITY("High Quality", 12000),
    BALANCED("Balanced", 6000),
    SMALL_FILE("Small File", 2500),
    CUSTOM("Custom", 8000)
}

data class VideoConversionConfig(
    val outputFormat: VideoOutputFormat = VideoOutputFormat.MP4,
    val bitrateKbps: Int? = null,
    val audioBitrateKbps: Int = 192
)

data class AudioConversionConfig(
    val outputFormat: AudioOutputFormat = AudioOutputFormat.M4A,
    val bitrateKbps: Int = 192
)
