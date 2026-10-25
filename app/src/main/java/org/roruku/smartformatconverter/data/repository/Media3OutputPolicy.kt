package org.roruku.smartformatconverter.data.repository

import org.roruku.smartformatconverter.data.db.MediaType

/**
 * The current Media3 muxer emits MP4 video and AAC-in-MP4 audio only.
 * Validate persisted jobs as well as new jobs: old database rows can still
 * contain an FFmpeg-era requested output extension.
 */
internal object Media3OutputPolicy {
    fun rejectionReason(mediaType: MediaType, format: String): String? {
        val extension = format.lowercase()
        return when (mediaType) {
            MediaType.VIDEO -> if (extension == "mp4") null
                else "Video output $format is not available in the Media3 version. Re-queue as MP4."
            MediaType.AUDIO -> if (extension == "m4a") null
                else "Audio output $format is not available in the Media3 version. Re-queue as M4A."
            MediaType.IMAGE -> if (extension in setOf("jpg", "jpeg", "png", "webp")) null
                else "Unsupported image output: $format"
            MediaType.DOWNLOAD -> null // Preserves directly downloaded source container.
        }
    }
}
