package org.roruku.smartformatconverter.engine.media3

import android.media.MediaCodecList
import org.roruku.smartformatconverter.data.db.MediaType
import org.roruku.smartformatconverter.data.repository.FormatCapabilityRegistry

/** Encoder availability must be checked separately from input extractor support. */
internal object Media3DeviceCapabilities {
    fun canEncode(mime: String): Boolean = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { codec ->
            codec.isEncoder && codec.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }
    } catch (_: Exception) { false }

    fun canExport(type: MediaType, format: String): Boolean {
        if (type == MediaType.DOWNLOAD) return true
        val target = FormatCapabilityRegistry.find(type, format) ?: return false
        return if (type == MediaType.VIDEO && target.extension == "webm") {
            canEncode(requireNotNull(target.videoMime)) && canEncode(requireNotNull(target.audioMime))
        } else true
    }

    fun unsupportedReason(type: MediaType, format: String): String? {
        if (canExport(type, format)) return null
        return if (FormatCapabilityRegistry.find(type, format) == null)
            FormatCapabilityRegistry.rejectionReason(type, format)
        else "WebM requires device VP9 and Opus encoders. Select MP4 on this device."
    }
}
