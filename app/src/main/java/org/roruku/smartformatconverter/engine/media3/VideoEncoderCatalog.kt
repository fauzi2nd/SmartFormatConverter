package org.roruku.smartformatconverter.engine.media3

import android.media.MediaCodecInfo
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EncoderUtil
import org.roruku.smartformatconverter.engine.media.VideoEncoderSelection
import org.roruku.smartformatconverter.engine.media.VideoEncoderEligibility
import org.roruku.smartformatconverter.engine.media.AdaptiveAutoResolution
import org.roruku.smartformatconverter.engine.media.AdaptiveResolutionPolicy

/**
 * Device-only encoder discovery and strict encoder selection.
 * A capability result is advisory; a vendor codec may still fail at runtime.
 */
@OptIn(UnstableApi::class)
internal object VideoEncoderCatalog {
    data class Entry(
        val name: String,
        val hardware: Boolean,
        val reportedSourceSupported: Boolean?,
    )

    /**
     * Container-aware formats actually supported by our output muxers.
     * Availability is separately filtered through MediaCodec at runtime.
     */
    fun supportedCodecs(format: String): List<String> = when (format.lowercase()) {
        "webm" -> listOf("vp8", "vp9")
        "mp4" -> listOf("h264", "h265")
        else -> emptyList()
    }

    fun mimeType(format: String, codec: String): String = when (codec.lowercase()) {
        "auto" -> when (format.lowercase()) {
            "webm" -> MimeTypes.VIDEO_VP9
            "mp4" -> MimeTypes.VIDEO_H264
            else -> throw IllegalArgumentException("Unsupported video output format: $format")
        }
        "vp8" -> MimeTypes.VIDEO_VP8
        "vp9" -> MimeTypes.VIDEO_VP9
        "h264", "avc" -> MimeTypes.VIDEO_H264
        "h265", "hevc" -> MimeTypes.VIDEO_H265
        else -> throw IllegalArgumentException("Unsupported video codec: $codec")
    }

    fun available(mimeType: String): List<MediaCodecInfo> =
        EncoderUtil.getSupportedEncoders(mimeType)

    fun selected(mimeType: String, choice: String): List<MediaCodecInfo> =
        available(mimeType).filter { info ->
            VideoEncoderSelection.matches(
                choice, info.name, EncoderUtil.isHardwareAccelerated(info, mimeType)
            )
        }

    fun incompatibilityReason(
        mimeType: String, encoder: String, width: Int, height: Int,
        fps: Float = 0f
    ): String? = VideoEncoderEligibility.rejection(
        mime = mimeType,
        encoderChoice = encoder,
        width = width,
        height = height,
        fps = fps,
        candidates = describe(mimeType, width, height, 0, fps).map {
            VideoEncoderEligibility.Candidate(
                it.name, it.hardware, it.reportedSourceSupported
            )
        }
    )

    /** Same auto sizing algorithm is used by the editor and queued job. */
    fun bestAutoResolution(
        mimeType: String, encoder: String,
        source: AdaptiveResolutionPolicy.Size, fps: Float
    ): AdaptiveAutoResolution.Choice {
        if (selected(mimeType, encoder).isEmpty())
            return AdaptiveAutoResolution.Choice(null, false)
        return AdaptiveAutoResolution.choose(source) { size ->
            val reports = describe(mimeType, size.width, size.height, 0, fps)
                .filter { VideoEncoderSelection.matches(encoder, it.name, it.hardware) }
                .map { it.reportedSourceSupported }
            when {
                reports.any { it == true } -> true
                reports.any { it == null } -> null
                else -> false
            }
        }
    }

    fun describe(
        mimeType: String,
        width: Int = 0,
        height: Int = 0,
        rotation: Int = 0,
        fps: Float = 0f,
    ): List<Entry> {
        val target = VideoGeometryPolicy.displaySize(width, height, rotation)
        return available(mimeType).map { info ->
            Entry(
                name = info.name,
                hardware = EncoderUtil.isHardwareAccelerated(info, mimeType),
                reportedSourceSupported = if (target.width > 0 && target.height > 0) {
                    try {
                        val caps = info.getCapabilitiesForType(mimeType).videoCapabilities
                        // Codec size information is only a preliminary filter:
                        // frame rotation and vendor-specific alignment can differ.
                        if (caps == null) null else {
                            val sizeOkay = caps.isSizeSupported(target.width, target.height)
                            if (fps > 0f && fps.isFinite()) {
                                sizeOkay && caps.areSizeAndRateSupported(
                                    target.width, target.height, fps.toDouble()
                                )
                            } else sizeOkay
                        }
                    } catch (_: Exception) {
                        null
                    }
                } else null,
            )
        }
    }
}
