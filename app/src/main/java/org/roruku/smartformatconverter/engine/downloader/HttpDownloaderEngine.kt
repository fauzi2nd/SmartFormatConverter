package org.roruku.smartformatconverter.engine.downloader

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class HttpMediaFormatOption(
    val formatId: String,
    val extension: String,
    val resolution: String,
    val note: String,
    val fileSizeEstimate: Long = 0L,
    val isAudioOnly: Boolean = false,
)

data class HttpDownloadableMediaInfo(
    val url: String,
    val title: String,
    val author: String? = null,
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String? = null,
    val availableFormats: List<HttpMediaFormatOption> = emptyList(),
)

object HttpDownloaderEngine {
    private const val TAG = "HttpDownloaderEngine"

    suspend fun extractMediaInfo(context: Context, urlString: String): Result<HttpDownloadableMediaInfo> = withContext(Dispatchers.IO) {
        try {
            if (urlString.isBlank() || (!urlString.startsWith("http://") && !urlString.startsWith("https://"))) {
                return@withContext Result.failure(IllegalArgumentException("Invalid URL format. Must start with http:// or https://"))
            }

            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            }

            val contentLength = connection.contentLengthLong
            val contentType = connection.contentType ?: "video/mp4"
            connection.disconnect()

            val ext = when {
                contentType.contains("audio/mpeg") || contentType.contains("mp3") -> "mp3"
                contentType.contains("audio/aac") || contentType.contains("aac") -> "aac"
                contentType.contains("audio/") -> "m4a"
                contentType.contains("webm") -> "webm"
                else -> "mp4"
            }

            val host = url.host ?: "Web Media"
            val pathSegments = url.path.split("/").filter { it.isNotBlank() }
            val fallbackTitle = if (pathSegments.isNotEmpty()) {
                pathSegments.last().substringBefore("?").replace("-", " ").replace("_", " ").capitalizeWords()
            } else {
                "Media_${System.currentTimeMillis()}"
            }

            val formats = if ((ext == "mp3") || (ext == "aac") || (ext == "m4a")) {
                listOf(
                    HttpMediaFormatOption("audio_hq", ext, "Audio Only", "High Quality Audio Stream", contentLength, isAudioOnly = true),
                )
            } else {
                listOf(
                    HttpMediaFormatOption("video_best", ext, "Direct Stream", "Source Quality Video Stream", contentLength, isAudioOnly = false),
                    HttpMediaFormatOption("audio_mp3", "mp3", "Audio Only", "Extract MP3 Audio Stream", 0L, isAudioOnly = true),
                )
            }

            val mediaInfo = HttpDownloadableMediaInfo(
                url = urlString,
                title = fallbackTitle,
                author = host,
                durationSeconds = 0L,
                thumbnailUrl = null,
                availableFormats = formats
            )

            Result.success(mediaInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract direct HTTP media info for $urlString", e)
            Result.failure(e)
        }
    }

    suspend fun downloadMedia(
        context: Context,
        mediaInfo: HttpDownloadableMediaInfo,
        formatOption: HttpMediaFormatOption,
        tempOutputFile: File,
        onProgress: (progress: Float, speed: String?, eta: String?) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            coroutineContext.ensureActive()
            onProgress(0.02f, "Connecting to direct HTTP stream...", null)

            val url = URL(mediaInfo.url)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(IllegalStateException("HTTP request failed with status code $responseCode"))
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: formatOption.fileSizeEstimate
            inputStream = connection.inputStream
            outputStream = FileOutputStream(tempOutputFile)

            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            var totalDownloadedBytes = 0L
            val startTimeMs = System.currentTimeMillis()
            var lastUpdateMs = startTimeMs

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                coroutineContext.ensureActive()
                outputStream.write(buffer, 0, bytesRead)
                totalDownloadedBytes += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastUpdateMs > 300 || totalDownloadedBytes == totalBytes) {
                    lastUpdateMs = now
                    val elapsedSec = (now - startTimeMs) / 1000.0
                    val speedBytesPerSec = if (elapsedSec > 0) (totalDownloadedBytes / elapsedSec).toLong() else 0L
                    val speedMb = String.format(Locale.US, "%.2f MB/s", speedBytesPerSec.toDouble() / (1024.0 * 1024.0))

                    val progressPct = if (totalBytes > 0) {
                        (totalDownloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 0.99f)
                    } else {
                        0.5f
                    }

                    val remainingBytes = if (totalBytes > totalDownloadedBytes) totalBytes - totalDownloadedBytes else 0L
                    val remainingSec = if (speedBytesPerSec > 0) remainingBytes / speedBytesPerSec else 0L
                    val etaStr = String.format(Locale.US, "%02d:%02d", remainingSec / 60, remainingSec % 60)

                    onProgress(progressPct, speedMb, etaStr)
                }
            }

            outputStream.flush()

            if (!tempOutputFile.exists() || tempOutputFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Downloaded media output file is empty"))
            }

            onProgress(1.0f, "Complete", "00:00")
            Result.success(tempOutputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Direct HTTP download failed for ${mediaInfo.url}", e)
            if (tempOutputFile.exists()) {
                tempOutputFile.delete()
            }
            Result.failure(e)
        } finally {
            try { inputStream?.close() } catch (_: Exception) { /* ignore */ }
            try { outputStream?.close() } catch (_: Exception) { /* ignore */ }
            try { connection?.disconnect() } catch (_: Exception) { /* ignore */ }
        }
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        }
    }
}
