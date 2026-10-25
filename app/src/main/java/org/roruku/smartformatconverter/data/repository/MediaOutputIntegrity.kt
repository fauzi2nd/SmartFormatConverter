package org.roruku.smartformatconverter.data.repository

import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import org.roruku.smartformatconverter.data.db.MediaType
import java.io.File

/**
 * Lightweight structural check before making a conversion visible in SAF.
 * This does not fully decode or guarantee playback on every chipset, but
 * prevents empty/unreadable files from being marked as completed.
 */
internal object MediaOutputIntegrity {

    fun containsExpectedTrack(type: MediaType, trackMimeTypes: List<String>): Boolean =
        when (type) {
            MediaType.VIDEO -> trackMimeTypes.any { it.startsWith("video/") }
            MediaType.AUDIO -> trackMimeTypes.any { it.startsWith("audio/") }
            MediaType.IMAGE, MediaType.DOWNLOAD -> true
        }

    fun validate(file: File, type: MediaType): String? {
        if (!file.isFile || file.length() <= 0L) return "Output file is missing or empty"
        return when (type) {
            MediaType.VIDEO, MediaType.AUDIO -> validateAv(file, type)
            MediaType.IMAGE -> validateImage(file)
            MediaType.DOWNLOAD -> null // Direct HTTP downloads have separate transfer checks.
        }
    }

    private fun validateAv(file: File, type: MediaType): String? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val mimes = (0 until extractor.trackCount).map { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
            }
            if (containsExpectedTrack(type, mimes)) null
            else "Exported file does not contain the expected ${type.name.lowercase()} track"
        } catch (error: Exception) {
            "Exported media container cannot be parsed: ${error.message ?: error.javaClass.simpleName}"
        } finally {
            extractor.release()
        }
    }

    private fun validateImage(file: File): String? =
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) null
            else "Output image dimensions are invalid"
        } catch (error: Exception) {
            "Output image cannot be decoded: ${error.message ?: error.javaClass.simpleName}"
        }
}
