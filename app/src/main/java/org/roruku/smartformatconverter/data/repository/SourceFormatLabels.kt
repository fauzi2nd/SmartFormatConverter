package org.roruku.smartformatconverter.data.repository

/** Prefer a real filename suffix to MIME's generic subtype (audio/mpeg is MP3). */
internal object SourceFormatLabels {
    private val knownExtensions = setOf(
        "mp3", "m4a", "aac", "ogg", "opus", "flac", "wav",
        "mp4", "mkv", "webm", "mov", "avi",
        "jpg", "jpeg", "png", "webp", "heic", "avif", "gif", "bmp"
    )

    fun resolve(fileName: String, mimeOrSubtype: String?): String {
        val suffix = fileName.substringAfterLast('.', "").trim().lowercase()
        if (suffix in knownExtensions) return if (suffix == "jpeg") "jpg" else suffix
        val subtype = mimeOrSubtype.orEmpty().substringAfterLast('/').lowercase()
        return when (subtype) {
            "mpeg", "mp3" -> "mp3"
            "mp4", "x-m4a" -> if (mimeOrSubtype.orEmpty().startsWith("audio/")) "m4a" else "mp4"
            "x-wav", "wave", "vnd.wave" -> "wav"
            "x-flac", "flac" -> "flac"
            "x-matroska" -> "mkv"
            "jpeg" -> "jpg"
            "x-opus+ogg" -> "ogg"
            else -> subtype.ifBlank { "unknown" }
        }
    }
}
