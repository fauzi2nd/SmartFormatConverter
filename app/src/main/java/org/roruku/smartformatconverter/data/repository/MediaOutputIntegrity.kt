package org.roruku.smartformatconverter.data.repository

import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import org.roruku.smartformatconverter.data.db.MediaType
import java.io.File
import java.io.FileInputStream

/**
 * Structural verification before exporting a temporary file to SAF.
 * It checks container signatures, track types, codecs and non-empty samples.
 * This is NOT a full decode or a guarantee of playback on every device.
 */
internal object MediaOutputIntegrity {
    fun containsExpectedTrack(type: MediaType, trackMimeTypes: List<String>): Boolean =
        when (type) {
            MediaType.VIDEO -> trackMimeTypes.any { it.startsWith("video/") }
            MediaType.AUDIO -> trackMimeTypes.any { it.startsWith("audio/") }
            MediaType.IMAGE, MediaType.DOWNLOAD -> true
        }

    fun signatureMatches(extension: String, header: ByteArray): Boolean {
        val ext = extension.lowercase()
        return when (ext) {
            "mp4", "m4a" -> header.size >= 12 &&
                String(header, 4, 4, Charsets.US_ASCII) == "ftyp"
            "webm" -> header.size >= 4 &&
                header[0] == 0x1a.toByte() && header[1] == 0x45.toByte() &&
                header[2] == 0xdf.toByte() && header[3] == 0xa3.toByte()
            else -> false
        }
    }

    fun codecMatches(type: MediaType, extension: String, trackMimes: List<String>): Boolean =
        when (type) {
            MediaType.VIDEO -> when (extension.lowercase()) {
                "mp4" -> trackMimes.any { it in setOf("video/avc", "video/hevc") } &&
                    trackMimes.filter { it.startsWith("audio/") }.all { it == "audio/mp4a-latm" }
                "webm" -> trackMimes.any { it == "video/x-vnd.on2.vp9" } &&
                    trackMimes.filter { it.startsWith("audio/") }.all { it == "audio/opus" }
                else -> false
            }
            MediaType.AUDIO -> extension.equals("m4a", true) &&
                trackMimes.any { it == "audio/mp4a-latm" } &&
                trackMimes.none { it.startsWith("video/") }
            MediaType.IMAGE, MediaType.DOWNLOAD -> true
        }

    fun validate(file: File, type: MediaType, extension: String = file.extension): String? {
        if (!file.isFile || file.length() <= 0L) return "Output file is missing or empty"
        return when (type) {
            MediaType.VIDEO, MediaType.AUDIO -> validateAv(file, type, extension)
            MediaType.IMAGE -> validateImage(file)
            MediaType.DOWNLOAD -> null
        }
    }

    private fun validateAv(file: File, type: MediaType, extension: String): String? {
        if (FormatCapabilityRegistry.find(type, extension) == null) return "Unexpected output format: " + extension
        val signature = ByteArray(12)
        val length = try {
            FileInputStream(file).use { it.read(signature) }
        } catch (error: Exception) {
            return "Unable to read output header: " + (error.message ?: error.javaClass.simpleName)
        }
        if (length < 4 || !signatureMatches(extension, signature.copyOf(length))) {
            return "Output header is not a valid " + extension.uppercase() + " container"
        }

        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val mimes = (0 until extractor.trackCount).map { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
            }
            if (!containsExpectedTrack(type, mimes)) {
                "Exported file does not contain the expected " + type.name.lowercase() + " track"
            } else if (!codecMatches(type, extension, mimes)) {
                "Exported track codecs are incompatible with " + extension.uppercase()
            } else {
                val index = (0 until extractor.trackCount).firstOrNull { index ->
                    extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
                        .startsWith(if (type == MediaType.VIDEO) "video/" else "audio/")
                } ?: return "Expected media track missing"
                val track = extractor.getTrackFormat(index)
                if (track.containsKey(MediaFormat.KEY_DURATION) &&
                    track.getLong(MediaFormat.KEY_DURATION) <= 0L) {
                    "Exported track duration is invalid"
                } else {
                    extractor.selectTrack(index)
                    if (extractor.sampleTime < 0L) "Exported track contains no readable samples"
                    else null
                }
            }
        } catch (error: Exception) {
            "Exported media container cannot be parsed: " + (error.message ?: error.javaClass.simpleName)
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
            "Output image cannot be decoded: " + (error.message ?: error.javaClass.simpleName)
        }
}
