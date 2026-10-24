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
            "{\\\"error\\\":\\\"expired token\\\"}"
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
    fun queueUsesOutputExtensionForDownloads() {
        fun job(ext: String) = ConversionJobEntity(
            mediaType = MediaType.DOWNLOAD,
            fileName = "file.$ext",
            inputUri = "https://example.com/file.$ext",
            inputFormat = ext, outputFormat = ext,
            status = JobStatus.COMPLETED, outputUri = "content://output/file"
        )
        assertEquals(CompletedMediaKind.IMAGE, completedMediaKind(job("webp")))
        assertEquals(CompletedMediaKind.AUDIO, completedMediaKind(job("mp3")))
        assertEquals(CompletedMediaKind.VIDEO, completedMediaKind(job("mp4")))
        assertEquals(CompletedMediaKind.UNKNOWN, completedMediaKind(job("bin")))
    }
}
