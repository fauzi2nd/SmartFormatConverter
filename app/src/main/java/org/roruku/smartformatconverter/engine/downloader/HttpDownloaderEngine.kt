package org.roruku.smartformatconverter.engine.downloader

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

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
    val etag: String? = null,
    val lastModified: String? = null,
)

/**
 * A reliable *direct-file* HTTP client, NOT a website/video-platform extractor.
 * Never label raw video bytes as an MP3 download or present fabricated quality
 * choices. HLS/DASH manifests and web pages are explicitly rejected.
 */
object HttpDownloaderEngine {
    private const val TAG = "HttpDownloaderEngine"
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val MAX_ATTEMPTS = 3
    private const val USER_AGENT = "SmartFormatConverter/1.0 (Android; direct media downloader)"

    private data class Probe(
        val extension: String,
        val title: String,
        val length: Long,
        val etag: String?,
        val lastModified: String?
    )

    private class InvalidMediaException(message: String) : IOException(message)

    private fun createConnection(url: URL, method: String, range: String? = null): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Encoding", "identity")
            if (range != null) setRequestProperty("Range", range)
        }

    private fun parseContentRangeTotal(value: String?): Long? =
        value?.substringAfterLast('/', "")?.toLongOrNull()?.takeIf { it > 0L }

    private fun parseContentRangeStart(value: String?): Long? =
        value?.let { Regex("""bytes\\s+(\\d+)-""", RegexOption.IGNORE_CASE)
            .find(it)?.groupValues?.get(1)?.toLongOrNull() }

    /** HEAD first; fall back to a one-byte ranged GET for HEAD-hostile CDNs. */
    suspend fun extractMediaInfo(
        context: Context,
        urlString: String
    ): Result<HttpDownloadableMediaInfo> = withContext(Dispatchers.IO) {
        try {
            val url = DirectMediaPolicy.validatedHttpUrl(urlString).toURL()
            var probe: Probe? = null
            var firstError: Exception? = null

            for (method in listOf("HEAD", "GET")) {
                coroutineContext.ensureActive()
                val connection = createConnection(
                    url, method,
                    if (method == "GET") "bytes=0-0" else null
                )
                try {
                    val status = connection.responseCode
                    if (status !in 200..299) {
                        throw IOException("Server returned HTTP $status for $method")
                    }
                    val contentType = connection.contentType
                    val contentDisposition = connection.getHeaderField("Content-Disposition")
                    val name = DirectMediaPolicy.contentDispositionName(contentDisposition)
                    val format = DirectMediaPolicy.extension(url.path, contentType, name)
                    if (format != null) {
                        // A one-byte 206 response has Content-Length: 1.
                        // Use Content-Range's total size, never 1, for this probe.
                        val length = if (status == 206) {
                            parseContentRangeTotal(connection.getHeaderField("Content-Range"))
                        } else connection.contentLengthLong.takeIf { it > 0 }
                        probe = Probe(
                            extension = format,
                            title = DirectMediaPolicy.displayName(url.path, contentDisposition),
                            length = length ?: 0L,
                            etag = connection.getHeaderField("ETag"),
                            lastModified = connection.getHeaderField("Last-Modified")
                        )
                        break
                    }
                    firstError = InvalidMediaException(
                        "This URL is not a supported direct media file (Content-Type: ${contentType ?: "unknown"})."
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    firstError = e
                } finally {
                    connection.disconnect()
                }
            }
            val found = probe ?: return@withContext Result.failure(
                firstError ?: InvalidMediaException("Unsupported direct media URL")
            )
            val audio = DirectMediaPolicy.mimeTypeForExtension(found.extension).startsWith("audio/")
            val format = HttpMediaFormatOption(
                formatId = "direct_${found.extension}",
                extension = found.extension,
                resolution = "Source file",
                note = "Direct download (no transcoding)",
                fileSizeEstimate = found.length,
                isAudioOnly = audio
            )
            Result.success(
                HttpDownloadableMediaInfo(
                    url = urlString.trim(),
                    title = found.title,
                    author = url.host,
                    availableFormats = listOf(format),
                    etag = found.etag,
                    lastModified = found.lastModified
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Direct media inspection failed: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    /**
     * Downloads and optionally resumes a previously interrupted job.
     * Resume requires the same HTTP ETag or Last-Modified validator captured
     * during inspection; otherwise any old partial file is discarded.
     *
     * GET always checks the media Content-Type. The server cannot substitute
     * an HTML login/error page and still receive a successful media job.
     */
    suspend fun downloadMedia(
        context: Context,
        mediaInfo: HttpDownloadableMediaInfo,
        formatOption: HttpMediaFormatOption,
        tempOutputFile: File,
        onProgress: (progress: Float, speed: String?, eta: String?) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val url = try {
            DirectMediaPolicy.validatedHttpUrl(mediaInfo.url).toURL()
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
        val format = formatOption.extension.lowercase(Locale.ROOT)
        val validator = mediaInfo.etag?.takeIf { !it.startsWith("W/") }
            ?: mediaInfo.lastModified?.takeIf { it.isNotBlank() }
        if (DirectMediaPolicy.mimeTypeForExtension(format) == "application/octet-stream") {
            return@withContext Result.failure(InvalidMediaException("Unsupported output format: $format"))
        }
        val cacheDir = requireNotNull(tempOutputFile.parentFile)
        if (!cacheDir.isDirectory && !cacheDir.mkdirs()) {
            return@withContext Result.failure(IOException("Cannot create download cache directory"))
        }

        var lastError: Exception = IOException("Download did not complete")
        for (attempt in 0 until MAX_ATTEMPTS) {
            coroutineContext.ensureActive()
            var existing = tempOutputFile.takeIf { it.isFile }?.length() ?: 0L
            if (validator == null && existing > 0L) {
                // Never append an unknown revision of a media file.
                tempOutputFile.delete()
                existing = 0
            }
            val connection = createConnection(url, "GET",
                if (existing > 0) "bytes=$existing-" else null)
            if (existing > 0) connection.setRequestProperty("If-Range", validator)
            try {
                onProgress(0.01f, "Connecting…", null)
                val response = connection.responseCode
                if (response == 416 && existing > 0L) {
                    val total = parseContentRangeTotal(connection.getHeaderField("Content-Range"))
                    if (total == existing) {
                        onProgress(1f, null, "00:00")
                        return@withContext Result.success(tempOutputFile)
                    }
                    tempOutputFile.delete()
                    throw IOException("Remote media changed; restarting download")
                }
                if (response !in 200..299) {
                    if (response in 400..499 && response != 408 && response != 429) {
                        throw InvalidMediaException("Server rejected download with HTTP $response")
                    }
                    throw IOException("HTTP $response while downloading media")
                }

                val detected = DirectMediaPolicy.extension(
                    url.path, connection.contentType,
                    DirectMediaPolicy.contentDispositionName(
                        connection.getHeaderField("Content-Disposition")
                    )
                )
                if (detected != format) {
                    throw InvalidMediaException(
                        "Server returned ${connection.contentType ?: "unknown content"} instead of the inspected $format file. Inspect the URL again."
                    )
                }

                val isPartial = response == 206
                val start = parseContentRangeStart(connection.getHeaderField("Content-Range"))
                if (isPartial && (existing == 0L || start != existing)) {
                    tempOutputFile.delete()
                    throw IOException("Unexpected Content-Range while resuming; restarting safely")
                }
                val append = existing > 0L && isPartial
                val initialBytes = if (append) existing else 0L
                val responseSize = connection.contentLengthLong.takeIf { it > 0L }
                val totalBytes = if (append) {
                    parseContentRangeTotal(connection.getHeaderField("Content-Range"))
                        ?: responseSize?.plus(existing)
                } else responseSize
                var downloaded = initialBytes
                val started = System.nanoTime()
                var lastUpdate = started

                connection.inputStream.use { input ->
                    FileOutputStream(tempOutputFile, append).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            if (downloaded == 0L &&
                                DirectMediaPolicy.looksLikeWebResponse(buffer, count)) {
                                throw InvalidMediaException(
                                    "Server returned an HTML/JSON page or streaming manifest instead of media bytes."
                                )
                            }
                            output.write(buffer, 0, count)
                            downloaded += count
                            val now = System.nanoTime()
                            if (now - lastUpdate >= 250_000_000L ||
                                (totalBytes != null && downloaded >= totalBytes)) {
                                lastUpdate = now
                                val elapsed = (now - started).coerceAtLeast(1L) / 1_000_000_000.0
                                val transferred = downloaded - initialBytes
                                val speed = transferred / elapsed
                                val speedText = String.format(Locale.US, "%.2f MB/s",
                                    speed / (1024.0 * 1024.0))
                                val pct = if (totalBytes != null && totalBytes > 0L) {
                                    (downloaded.toDouble() / totalBytes)
                                        .toFloat().coerceIn(0f, 0.99f)
                                } else {
                                    // Unknown Content-Length must never show fabricated 50%.
                                    0.02f
                                }
                                val eta = if (totalBytes == null || speed <= 0) {
                                    "—"
                                } else {
                                    val seconds = ((totalBytes - downloaded).coerceAtLeast(0)
                                        / speed).toLong()
                                    String.format(Locale.US, "%02d:%02d",
                                        seconds / 60, seconds % 60)
                                }
                                onProgress(pct, speedText, eta)
                            }
                        }
                        output.flush()
                    }
                }
                if (!tempOutputFile.isFile || downloaded <= 0L) {
                    throw IOException("Downloaded file is empty")
                }
                if (totalBytes != null && downloaded != totalBytes) {
                    throw IOException("Incomplete download: $downloaded of $totalBytes bytes")
                }
                onProgress(1f, null, "00:00")
                return@withContext Result.success(tempOutputFile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidMediaException) {
                // Don't keep an HTML error page or permanently unsupported data.
                tempOutputFile.delete()
                return@withContext Result.failure(e)
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Direct download attempt ${attempt + 1}/$MAX_ATTEMPTS failed: ${e.javaClass.simpleName}")
                if (attempt < MAX_ATTEMPTS - 1) {
                    delay(1000L shl attempt)
                }
            } finally {
                connection.disconnect()
            }
        }
        Result.failure(lastError)
    }
}
