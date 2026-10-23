package org.roruku.smartformatconverter.data.storage

import org.roruku.smartformatconverter.data.db.MediaType

/**
 * Some document providers return application/octet-stream for media files.
 * Prefer recognized extensions and fall back to the MIME type.
 */
object MediaSelectionPolicy {
    private val videoExtensions = setOf("mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp", "3g2", "ts", "m2ts", "mpg", "mpeg", "wmv")
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif", "tif", "tiff", "avif")
    private val audioExtensions = setOf("mp3", "aac", "m4a", "wav", "flac", "ogg", "opus", "wma", "aiff", "aif", "amr")

    fun detect(mimeType: String?, fileName: String): MediaType? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        when (ext) {
            in videoExtensions -> return MediaType.VIDEO
            in imageExtensions -> return MediaType.IMAGE
            in audioExtensions -> return MediaType.AUDIO
        }

        return when {
            mimeType?.startsWith("video/", ignoreCase = true) == true -> MediaType.VIDEO
            mimeType?.startsWith("image/", ignoreCase = true) == true -> MediaType.IMAGE
            mimeType?.startsWith("audio/", ignoreCase = true) == true -> MediaType.AUDIO
            else -> null
        }
    }
}
