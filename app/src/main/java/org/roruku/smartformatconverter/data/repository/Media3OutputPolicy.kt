package org.roruku.smartformatconverter.data.repository

import org.roruku.smartformatconverter.data.db.MediaType
import org.roruku.smartformatconverter.engine.ffmpeg.AudioAdvancedSettings
import org.roruku.smartformatconverter.engine.ffmpeg.VideoAdvancedSettings

/**
 * The active Media3 muxers support MP4, gated WebM, and AAC-in-MP4 audio.
 * Validate persisted jobs as well as new jobs: old database rows can still
 * contain an FFmpeg-era requested output extension.
 */
internal object Media3OutputPolicy {
    /** Settings that exist in old queue snapshots but are not executed by Media3. */
    fun unsupportedVideoSettings(s: VideoAdvancedSettings): String? = when {
        s.isRemux || s.videoCodec == "copy" || s.audioCodec == "copy" ->
            "Stream copy/remux is not supported. Choose a conversion preset."
        (s.format == "webm" && s.videoCodec !in setOf("auto", "vp9")) ||
            (s.format == "mp4" && s.videoCodec !in setOf("auto", "h264", "h265", "hevc")) ->
            "Select VP9 for WebM or H.264/H.265 for MP4."
        (s.format == "webm" && s.audioCodec !in setOf("auto", "opus", "none")) ||
            (s.format == "mp4" && s.audioCodec !in setOf("auto", "aac", "none")) ->
            "Select Opus for WebM or AAC for MP4."
        s.encoder != "auto" ->
            "Manual hardware/software encoder selection is not supported."
        s.fps != null || s.pixelFormat != "auto" || s.rateControl == "cbr" ->
            "Custom FPS, pixel format or constant bitrate mode is not supported."
        s.sampleRate != null || s.channels != null ||
            s.volumePercent != 100 || s.audioStream !in setOf("first", "none") ->
            "Custom audio routing, channels, sample rate or volume is not supported."
        s.subtitleMode != "auto" || !s.preserveMetadata || !s.fastStart ->
            "Custom subtitles, metadata or fast-start handling is not supported."
        else -> null
    }

    fun unsupportedAudioSettings(s: AudioAdvancedSettings): String? = when {
        s.sampleRate != null || s.channels != null ||
            s.volumePercent != 100 || !s.preserveMetadata ->
            "Custom sample rate, channels, volume or metadata are not yet supported by Media3."
        else -> null
    }

    fun rejectionReason(mediaType: MediaType, format: String): String? =
        FormatCapabilityRegistry.rejectionReason(mediaType, format)
}
