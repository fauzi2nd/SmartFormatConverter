package org.roruku.smartformatconverter.engine.downloader

import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import org.json.JSONObject

/**
 * Direct-file selection only. A web page or adaptive playlist is not a media
 * file even if the URL contains ".mp4" in a query parameter.
 * These functions are pure and covered by JVM tests.
 */
object DirectMediaPolicy {
    private val mimeExt = mapOf(
        "video/mp4" to "mp4",
        "video/x-m4v" to "m4v",
        "video/webm" to "webm",
        "video/quicktime" to "mov",
        "video/x-matroska" to "mkv",
        "video/x-msvideo" to "avi",
        "audio/mpeg" to "mp3",
        "audio/mp3" to "mp3",
        "audio/mp4" to "m4a",
        "audio/x-m4a" to "m4a",
        "audio/aac" to "aac",
        "audio/wav" to "wav",
        "audio/wave" to "wav",
        "audio/x-wav" to "wav",
        "audio/flac" to "flac",
        "audio/x-flac" to "flac",
        "audio/ogg" to "ogg",
        "application/ogg" to "ogg",
        "audio/opus" to "opus",
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "image/webp" to "webp",
        "image/gif" to "gif"
    )
    private val fileTypes = setOf(
        "mp4", "m4v", "webm", "mov", "mkv", "avi", "mp3", "m4a", "aac",
        "wav", "flac", "ogg", "opus", "jpg", "jpeg", "png", "webp", "gif"
    )
    private val genericMime = setOf(
        "", "application/octet-stream", "binary/octet-stream",
        "application/binary", "application/download", "application/x-download"
    )
    private val aliases = mapOf("jpeg" to "jpg")

    fun validatedHttpUrl(raw: String): URI {
        val uri = URI(raw.trim())
        require(uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") &&
            !uri.host.isNullOrBlank() && uri.userInfo == null) {
            "A direct HTTP(S) URL without embedded credentials is required."
        }
        return uri
    }

    fun extension(urlPath: String, contentType: String?, filename: String? = null): String? {
        val mime = contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (mime !in genericMime) return mimeExt[mime]
        val fromName = listOfNotNull(filename, urlPath).firstNotNullOfOrNull { name ->
            val candidate = name.substringAfterLast('/').substringBefore('?')
                .substringBefore('#').substringAfterLast('.', "").lowercase(Locale.ROOT)
            candidate.takeIf { it in fileTypes }
        }
        return fromName?.let { aliases[it] ?: it }
    }

    fun contentDispositionName(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val encoded = Regex("""filename\*\s*=\s*(?:UTF-8'')?([^;]+)""", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.get(1)?.trim()?.trim('"')
        val plain = Regex("""filename\s*=\s*(?:"([^"]+)"|([^;]+))""", RegexOption.IGNORE_CASE)
            .find(header)?.let { it.groupValues[1].ifBlank { it.groupValues[2].trim() } }
        val candidate = try {
            if (encoded != null) URLDecoder.decode(encoded, "UTF-8") else plain
        } catch (_: IllegalArgumentException) {
            plain
        }
        return candidate?.substringAfterLast('/')?.substringAfterLast('\\')
            ?.replace(Regex("[\\p{Cntrl}/\\\\]"), "")?.trim()
            ?.take(140)?.takeIf { it.isNotBlank() }
    }

    fun displayName(urlPath: String, header: String?): String {
        val name = contentDispositionName(header)
            ?: urlPath.substringAfterLast('/').ifBlank { "media_file" }
        return name.substringBeforeLast('.', name).replace('_', ' ').replace('-', ' ')
            .trim().take(120).ifBlank { "media_file" }
    }

    fun mimeTypeForExtension(ext: String): String = when (ext.lowercase(Locale.ROOT)) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "ogg" -> "audio/ogg"
        "opus" -> "audio/opus"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "application/octet-stream"
    }
}

/**
 * A single immutable format snapshot. A direct download never offers an
 * imaginary MP3 transcode or guesses quality labels from a media web page.
 */
data class DirectDownloadSnapshot(
    val extension: String,
    val etag: String? = null,
    val lastModified: String? = null
) {
    fun toJson(): String = JSONObject().apply {
        put("kind", "direct_http")
        put("schemaVersion", 1)
        put("extension", extension)
        etag?.let { put("etag", it) }
        lastModified?.let { put("lastModified", it) }
    }.toString()

    companion object {
        fun fromJson(json: String): DirectDownloadSnapshot {
            val source = JSONObject(json)
            require(source.optString("kind") == "direct_http" &&
                source.optInt("schemaVersion") == 1) {
                "Unsupported direct-download settings"
            }
            return DirectDownloadSnapshot(
                extension = source.getString("extension"),
                etag = source.optString("etag").ifBlank { null },
                lastModified = source.optString("lastModified").ifBlank { null }
            )
        }
    }
}
