package org.roruku.smartformatconverter.engine.media3

import android.media.MediaCodecList
import android.os.Build
import org.roruku.smartformatconverter.data.db.MediaType
import org.roruku.smartformatconverter.data.repository.FormatCapabilityRegistry

/** Capabilities are discovered from device encoders, never input MIME support. */
internal object Media3DeviceCapabilities {
    fun canEncode(mime: String): Boolean = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { codec ->
            codec.isEncoder && codec.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }
    } catch (_: Exception) { false }

    fun canExport(type: MediaType, format: String): Boolean {
        if (type == MediaType.DOWNLOAD) return true
        val cap = FormatCapabilityRegistry.find(type, format) ?: return false
        return when (type) {
            MediaType.VIDEO -> when (cap.extension) {
                "webm" -> canEncode("video/x-vnd.on2.vp9") && canEncode("audio/opus")
                else -> canEncode("video/avc")
            }
            MediaType.AUDIO -> when (cap.extension) {
                "ogg" -> Build.VERSION.SDK_INT >= 29 && canEncode("audio/opus")
                "wav" -> true // PCM output uses platform audio decoder, not an audio encoder.
                else -> canEncode("audio/mp4a-latm")
            }
            MediaType.IMAGE -> when (cap.extension) {
                "heic" -> Build.VERSION.SDK_INT >= 29 &&
                    (canEncode("image/vnd.android.heic") || canEncode("video/hevc"))
                "avif" -> Build.VERSION.SDK_INT >= 34 && canEncode("video/av01")
                else -> true
            }
            MediaType.DOWNLOAD -> true
        }
    }

    fun unsupportedReason(type: MediaType, format: String): String? {
        if (canExport(type, format)) return null
        return if (FormatCapabilityRegistry.find(type, format) == null) {
            FormatCapabilityRegistry.rejectionReason(type, format)
        } else {
            "No compatible encoder is available on this Android device for " +
                format.uppercase() + ". Select another output format."
        }
    }
}
