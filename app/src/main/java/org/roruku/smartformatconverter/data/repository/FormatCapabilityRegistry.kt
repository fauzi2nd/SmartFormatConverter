package org.roruku.smartformatconverter.data.repository

import org.roruku.smartformatconverter.data.db.MediaType

/**
 * Canonical contract between selectable formats, queue validation, MIME types,
 * engine muxers and output integrity checks. Decoding inputs does not imply
 * the ability to encode the same container.
 */
internal data class OutputCapability(
    val type: MediaType,
    val extension: String,
    val mimeType: String,
    val videoMime: String? = null,
    val audioMime: String? = null
)

internal object FormatCapabilityRegistry {
    private val supported = listOf(
        OutputCapability(MediaType.VIDEO, "mp4", "video/mp4", "video/avc", "audio/mp4a-latm"),
        OutputCapability(MediaType.VIDEO, "webm", "video/webm", "video/x-vnd.on2.vp9", "audio/opus"),
        OutputCapability(MediaType.AUDIO, "m4a", "audio/mp4", audioMime = "audio/mp4a-latm"),
        OutputCapability(MediaType.AUDIO, "aac", "audio/aac", audioMime = "audio/mp4a-latm"),
        OutputCapability(MediaType.AUDIO, "ogg", "audio/ogg", audioMime = "audio/opus"),
        OutputCapability(MediaType.AUDIO, "wav", "audio/wav", audioMime = "audio/raw"),
        OutputCapability(MediaType.AUDIO, "flac", "audio/flac", audioMime = "audio/flac"),
        OutputCapability(MediaType.IMAGE, "jpg", "image/jpeg"),
        OutputCapability(MediaType.IMAGE, "png", "image/png"),
        OutputCapability(MediaType.IMAGE, "webp", "image/webp"),
        OutputCapability(MediaType.IMAGE, "heic", "image/heic"),
        OutputCapability(MediaType.IMAGE, "avif", "image/avif"),
        OutputCapability(MediaType.IMAGE, "gif", "image/gif")
    )

    fun formats(type: MediaType): List<OutputCapability> = supported.filter { it.type == type }

    fun find(type: MediaType, requested: String): OutputCapability? {
        val normalized = requested.trim().lowercase()
        val extension = if (type == MediaType.IMAGE && normalized == "jpeg") "jpg" else normalized
        return supported.firstOrNull { it.type == type && it.extension == extension }
    }

    fun rejectionReason(type: MediaType, requested: String): String? {
        if (type == MediaType.DOWNLOAD || find(type, requested) != null) return null
        val available = formats(type).joinToString(", ") { it.extension.uppercase() }
        return "Unsupported output: " + requested + ". Available: " + available
    }
}
