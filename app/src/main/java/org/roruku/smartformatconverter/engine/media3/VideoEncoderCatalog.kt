package org.roruku.smartformatconverter.engine.media3

import android.media.MediaCodecInfo
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EncoderUtil
import org.roruku.smartformatconverter.engine.media.VideoEncoderSelection

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

    fun mimeType(format: String, codec: String): String = when (format.lowercase()) {
        "webm" -> MimeTypes.VIDEO_VP9
        else -> if (codec.lowercase() in setOf("h265", "hevc"))
            MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264
    }

    fun available(mimeType: String): List<MediaCodecInfo> =
        EncoderUtil.getSupportedEncoders(mimeType)

    fun selected(mimeType: String, choice: String): List<MediaCodecInfo> =
        available(mimeType).filter { info ->
            VideoEncoderSelection.matches(
                choice, info.name, EncoderUtil.isHardwareAccelerated(info, mimeType)
            )
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
                        val sizeOkay = caps.isSizeSupported(target.width, target.height)
                        if (fps > 0f && fps.isFinite()) {
                            sizeOkay && caps.areSizeAndRateSupported(
                                target.width, target.height, fps.toDouble()
                            )
                        } else sizeOkay
                    } catch (_: Exception) {
                        null
                    }
                } else null,
            )
        }
    }
}
