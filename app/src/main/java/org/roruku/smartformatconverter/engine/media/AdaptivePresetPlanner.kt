package org.roruku.smartformatconverter.engine.media

import kotlin.math.roundToInt

/**
 * Pure estimator shared by the UI and the Media3 export engine.
 * These are *target* rates, not CRF and not guarantees of resulting size/quality.
 * No codec choice is made here: callers must pass the effective output codec.
 */
data class VideoSourceProfile(
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val videoBitrateKbps: Int = 0,
    val audioBitrateKbps: Int = 0,
    val audioChannels: Int = 2,
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val hasAudio: Boolean = true
)

data class AdaptiveVideoPlan(
    val videoKbps: Int,
    val audioKbps: Int?,
    val estimatedBytes: Long?,
    val lowBytes: Long?,
    val highBytes: Long?,
    val sourceBytes: Long,
    val mayGrow: Boolean,
    val sourceLimited: Boolean
)

object AdaptivePresetPlanner {
    /** Tunable 30-fps H.264 SDR anchors (height/pixel dimensions preserved). */
    private val anchorPixels = listOf(
        854L * 480 to 2_000.0,
        1280L * 720 to 4_000.0,
        1920L * 1080 to 8_000.0,
        2560L * 1440 to 16_000.0,
        3840L * 2160 to 35_000.0
    )

    private fun baseH264Kbps(pixels: Long): Double {
        if (pixels <= 0) return 5_000.0
        if (pixels <= anchorPixels.first().first) {
            return (2_000.0 * pixels / anchorPixels.first().first)
                .coerceAtLeast(350.0)
        }
        val segment = anchorPixels.zipWithNext().firstOrNull { (a,b) ->
            pixels in a.first..b.first
        }
        if (segment != null) {
            val (a,b) = segment
            val t = (pixels - a.first).toDouble() / (b.first - a.first)
            return a.second + t * (b.second - a.second)
        }
        return (35_000.0 * pixels / anchorPixels.last().first)
            .coerceAtMost(100_000.0)
    }

    fun outputCodec(format: String, videoCodec: String): String = when {
        format.equals("webm", ignoreCase = true) && videoCodec == "vp8" -> "vp8"
        format.equals("webm", ignoreCase = true) -> "vp9"
        videoCodec in setOf("h265", "hevc") -> "hevc"
        else -> "h264"
    }

    fun plan(settings: VideoAdvancedSettings, source: VideoSourceProfile): AdaptiveVideoPlan {
        val codec = outputCodec(settings.format, settings.videoCodec)
        val canApplyResize = settings.width != null && settings.height != null &&
            (!settings.preventUpscale || source.width <= 0 ||
                (settings.width <= source.width && settings.height <= source.height))
        val width = if (canApplyResize) settings.width else source.width
        val height = if (canApplyResize) settings.height else source.height
        val pixels = width.toLong() * height.toLong()
        val fps = source.frameRate.takeIf { it in 1f..240f }?.toDouble() ?: 30.0
        // 60 FPS needs more bits than 30 FPS, but not a 2x increase.
        val fpsFactor = (fps / 30.0).coerceIn(0.5, 4.0).let {
            kotlin.math.sqrt(it)
        }
        val codecFactor = when (codec) {
            "hevc" -> 0.67
            "vp9" -> 0.72
            "vp8" -> 1.0
            else -> 1.0
        }
        val presetFactor = when (settings.preset) {
            "High Quality" -> 1.0
            "Balanced" -> 0.60
            "Small File" -> 0.34
            else -> 0.76 // Smart Auto and non-manual Custom
        }
        val suggested = (baseH264Kbps(pixels) * fpsFactor * codecFactor *
            presetFactor).roundToInt().coerceIn(150, 150_000)
        val sourceRate = source.videoBitrateKbps.takeIf { it in 100..200_000 }
            ?: run {
                val totalRate = if (source.durationMs > 0 && source.sizeBytes > 0)
                    (source.sizeBytes.toDouble() * 8 / source.durationMs).roundToInt()
                else 0
                (totalRate - source.audioBitrateKbps.coerceAtLeast(0))
                    .takeIf { it in 100..200_000 }
            }
        val manual = settings.videoBitrateKbps
        val sourceCeiling = when (settings.preset) {
            "High Quality" -> 1.25
            "Balanced" -> 1.0
            "Small File" -> 0.75
            else -> 1.10
        }
        val constrained = sourceRate?.let {
            minOf(suggested, (it * sourceCeiling).roundToInt().coerceAtLeast(150))
        } ?: suggested
        val bitrate = when {
            manual > 0 -> manual
            manual == BitrateSelection.SOURCE -> sourceRate ?: constrained
            else -> constrained
        }.coerceIn(100, 200_000)
        val opus = settings.format.equals("webm", ignoreCase = true)
        val audioRate = if (!source.hasAudio || settings.audioCodec == "none") null else {
            AdaptiveAudioPlanner.targetKbps(
                selection = settings.audioBitrateKbps,
                sourceRateKbps = source.audioBitrateKbps,
                channels = source.audioChannels,
                opus = opus,
                preset = settings.preset
            )
        }
        val estimate = estimateBytes(bitrate, audioRate, source.durationMs)
        // VBR and hardware codec variability: range is deliberately wide.
        val low = estimate?.let { (it * 0.75).toLong() }
        val high = estimate?.let { (it * 1.35).toLong() }
        return AdaptiveVideoPlan(
            videoKbps = bitrate, audioKbps = audioRate,
            estimatedBytes = estimate, lowBytes = low, highBytes = high,
            sourceBytes = source.sizeBytes,
            mayGrow = source.sizeBytes > 0 && high != null &&
                high > (source.sizeBytes * 1.10),
            sourceLimited = manual <= 0 && sourceRate != null && constrained < suggested
        )
    }

    fun estimateBytes(videoKbps: Int, audioKbps: Int?, durationMs: Long): Long? {
        if (durationMs <= 0) return null
        // kbps * milliseconds / 8 -> bytes; allow 1% for container overhead.
        return (((videoKbps.toLong() + (audioKbps ?: 0).toLong()) *
            durationMs) / 8.0 * 1.01).toLong().takeIf { it > 0 }
    }
}

object AdaptiveAudioPlanner {
    fun targetKbps(
        selection: Int,
        sourceRateKbps: Int,
        channels: Int,
        opus: Boolean,
        preset: String
    ): Int {
        val stereo = channels != 1
        val base = when (preset) {
            "High Quality" -> if (opus) 192 else 288
            "Balanced" -> if (opus) 128 else 176
            "Small File" -> if (opus) 64 else 80
            else -> if (opus) 144 else 192
        }
        val recommended = if (stereo) base else (base * 0.60).roundToInt()
        val validSource = sourceRateKbps.takeIf { it in 32..512 }
        // Explicit manual kbps and Source are respected. Otherwise, each
        // quality preset adapts to codec, channels and source rate so a 128 kbps
        // source is never pointlessly inflated to 320 kbps.
        return when {
            selection > 0 -> selection
            selection == BitrateSelection.SOURCE -> validSource ?: recommended
            else -> if (validSource != null) minOf(recommended, validSource) else recommended
        }.coerceIn(32, 512)
    }
}
