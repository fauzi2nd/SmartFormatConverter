package org.roruku.smartformatconverter.engine.media3

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

/** Discovers actual audio encoders; a container name is not an audio codec. */
internal object AudioEncoderCatalog {
    data class Entry(
        val name: String,
        val hardware: Boolean,
        val flacCompressionMin: Int? = null,
        val flacCompressionMax: Int? = null
    )
    fun mimeFor(format: String): String? = when (format.lowercase()) {
        "m4a", "aac" -> "audio/mp4a-latm"
        "ogg" -> "audio/opus"
        "flac" -> "audio/flac"
        else -> null
    }
    fun list(format: String): List<Entry> {
        val mime = mimeFor(format) ?: return emptyList()
        return try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { it.isEncoder && it.supportedTypes.any { type ->
                    type.equals(mime, ignoreCase = true)
                } }
                .map { info ->
                    val hardware = if (Build.VERSION.SDK_INT >= 29)
                        info.isHardwareAccelerated
                    else !info.name.startsWith("OMX.google.", true) &&
                        !info.name.startsWith("c2.android.", true)
                    val limits = if (format.equals("flac", true)) runCatching {
                        info.getCapabilitiesForType(mime).encoderCapabilities.complexityRange
                    }.getOrNull() else null
                    Entry(info.name, hardware, limits?.lower, limits?.upper)
                }
        } catch (_: Exception) { emptyList() }
    }
    fun open(format: String, name: String): MediaCodec {
        val mime = requireNotNull(mimeFor(format)) { "No audio encoder for $format" }
        return if (name == "auto") MediaCodec.createEncoderByType(mime) else {
            require(list(format).any { it.name == name }) {
                "Selected audio encoder '$name' is not available for $mime"
            }
            MediaCodec.createByCodecName(name)
        }
    }

    fun compressionLevelSupported(format: String, encoder: String, level: Int?): Boolean {
        if (level == null) return true
        if (format != "flac" || level !in 0..8) return false
        val matches = list(format).filter { encoder == "auto" || encoder == it.name }
        return matches.any {
            it.flacCompressionMin != null && it.flacCompressionMax != null &&
                level in it.flacCompressionMin..it.flacCompressionMax
        }
    }
}
