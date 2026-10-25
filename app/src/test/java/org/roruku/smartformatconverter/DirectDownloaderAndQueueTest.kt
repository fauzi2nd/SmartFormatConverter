package org.roruku.smartformatconverter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.roruku.smartformatconverter.data.db.ConversionJobEntity
import org.roruku.smartformatconverter.data.db.JobStatus
import org.roruku.smartformatconverter.data.db.MediaType
import org.roruku.smartformatconverter.engine.downloader.DirectMediaPolicy
import org.roruku.smartformatconverter.ui.queue.CompletedMediaKind
import org.roruku.smartformatconverter.ui.queue.completedMediaKind

class DirectDownloaderAndQueueTest {
    @Test
    fun mimeBeatsMisleadingUrlExtension() {
        assertEquals("jpg", DirectMediaPolicy.extension(
            "/movie.mp4", "image/jpeg; charset=binary"))
        assertEquals("m4a", DirectMediaPolicy.extension(
            "/api/download", "audio/mp4"))
        assertEquals("webm", DirectMediaPolicy.extension(
            "/some.file", "video/webm"))
    }

    @Test
    fun htmlAndPlaylistsCannotMasqueradeAsDirectVideo() {
        assertNull(DirectMediaPolicy.extension("/video.mp4", "text/html"))
        assertNull(DirectMediaPolicy.extension("/video.mp4", "application/vnd.apple.mpegurl"))
        assertNull(DirectMediaPolicy.extension("/playlist.m3u8", "application/octet-stream"))
        assertNull(DirectMediaPolicy.extension("/unknown.bin", "application/octet-stream"))
    }

    @Test
    fun genericContentTypeUsesOnlyAllowedFileExtensions() {
        assertEquals("mp4", DirectMediaPolicy.extension(
            "/api/file", "application/octet-stream", "clip.mp4"))
        assertEquals("jpg", DirectMediaPolicy.extension(
            "/photos/my-pic.JPEG", null))
        assertEquals("flac", DirectMediaPolicy.extension(
            "/audio.flac", "application/download"))
    }

    @Test
    fun dispositionFilenameIsDecodedAndPathSafe() {
        assertEquals("test file.mp4", DirectMediaPolicy.contentDispositionName(
            "attachment; filename*=UTF-8''test%20file.mp4"))
        assertEquals("holiday.jpg", DirectMediaPolicy.contentDispositionName(
            "attachment; filename=\"..\\holiday.jpg\""))
    }

    @Test
    fun mislabeledHtmlAndManifestBodiesAreBlocked() {
        for (bad in listOf(
            "<!DOCTYPE html><html>Sign in</html>",
            "<html><body>403 forbidden</body></html>",
            "#EXTM3U\\n#EXT-X-STREAM-INF",
            """{"error":"expired token"}"""
        )) {
            val bytes = bad.toByteArray(Charsets.UTF_8)
            org.junit.Assert.assertTrue(
                "Should reject direct file spoof: $bad",
                DirectMediaPolicy.looksLikeWebResponse(bytes, bytes.size)
            )
        }
        val validJpeg = byteArrayOf(
            0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte(), 0x00
        )
        org.junit.Assert.assertFalse(
            DirectMediaPolicy.looksLikeWebResponse(validJpeg, validJpeg.size)
        )
    }

    @Test
    fun onlyHttpOrHttpsWithHostCanBeUsed() {
        assertNotNull(DirectMediaPolicy.validatedHttpUrl("https://example.com/file.mp4"))
        assertThrows(IllegalArgumentException::class.java) {
            DirectMediaPolicy.validatedHttpUrl("file:///sdcard/DCIM/photo.jpg")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DirectMediaPolicy.validatedHttpUrl("https://user:secret@example.com/file.mp4")
        }
    }

    @Test
    fun supportsAdditionalDirectMediaWithConsistentMimeMapping() {
        val supported = mapOf(
            "heic" to "image/heic",
            "heif" to "image/heif",
            "avif" to "image/avif",
            "bmp" to "image/bmp",
            "3gp" to "video/3gpp"
        )
        for ((extension, mime) in supported) {
            assertEquals(extension, DirectMediaPolicy.extension("/remote/file.$extension", mime))
            assertEquals(mime, DirectMediaPolicy.mimeTypeForExtension(extension))
            assertEquals(extension, DirectMediaPolicy.extension(
                "/signed?token=1", "application/octet-stream", "download.$extension"))
        }
    }

    @Test
    fun queueUsesOutputExtensionForDownloads() {
        fun job(ext: String) = ConversionJobEntity(
            mediaType = MediaType.DOWNLOAD,
            fileName = "file.$ext",
            inputUri = "https://example.com/file.$ext",
            inputFormat = ext, outputFormat = ext,
            status = JobStatus.COMPLETED, outputUri = "content://output/file"
        )
        assertEquals(CompletedMediaKind.IMAGE, completedMediaKind(job("webp")))
        assertEquals(CompletedMediaKind.IMAGE, completedMediaKind(job("avif")))
        assertEquals(CompletedMediaKind.IMAGE, completedMediaKind(job("heic")))
        assertEquals(CompletedMediaKind.VIDEO, completedMediaKind(job("3gp")))
        assertEquals(CompletedMediaKind.AUDIO, completedMediaKind(job("mp3")))
        assertEquals(CompletedMediaKind.VIDEO, completedMediaKind(job("mp4")))
        assertEquals(CompletedMediaKind.UNKNOWN, completedMediaKind(job("bin")))
    }

    @Test
    fun downloadJobDistinguishesIndeterminateAndDeterminateProgress() {
        val indeterminateDownload = ConversionJobEntity(
            mediaType = MediaType.DOWNLOAD,
            fileName = "stream.mp4",
            inputUri = "https://example.com/stream.mp4",
            inputFormat = "mp4",
            outputFormat = "mp4",
            fileSize = 0L,
            progress = 0f,
            status = JobStatus.RUNNING
        )
        val determinateDownload = indeterminateDownload.copy(
            fileSize = 10_000_000L,
            progress = 0.45f
        )
        val videoConversion = indeterminateDownload.copy(
            mediaType = MediaType.VIDEO,
            fileSize = 0L,
            progress = 0.30f
        )

        val isIndeterminate1 = indeterminateDownload.mediaType == MediaType.DOWNLOAD && indeterminateDownload.fileSize <= 0L
        val isIndeterminate2 = determinateDownload.mediaType == MediaType.DOWNLOAD && determinateDownload.fileSize <= 0L
        val isIndeterminate3 = videoConversion.mediaType == MediaType.DOWNLOAD && videoConversion.fileSize <= 0L

        org.junit.Assert.assertTrue(isIndeterminate1)
        org.junit.Assert.assertFalse(isIndeterminate2)
        org.junit.Assert.assertFalse(isIndeterminate3)
    }

    @Test
    fun jobEntityPreservesAndIncrementsExecutionToken() {
        val initialJob = ConversionJobEntity(
            fileName = "clip.mp4",
            inputUri = "content://media/clip.mp4",
            mediaType = MediaType.VIDEO,
            inputFormat = "mp4",
            outputFormat = "mp4",
            executionToken = 1L
        )
        assertEquals(1L, initialJob.executionToken)
        val pausedJob = initialJob.copy(
            status = JobStatus.PAUSED,
            executionToken = initialJob.executionToken + 1
        )
        assertEquals(2L, pausedJob.executionToken)
        val resumedJob = pausedJob.copy(
            status = JobStatus.PENDING,
            executionToken = pausedJob.executionToken + 1
        )
        assertEquals(3L, resumedJob.executionToken)
        org.junit.Assert.assertNotEquals(initialJob.executionToken, resumedJob.executionToken)
    }
}
