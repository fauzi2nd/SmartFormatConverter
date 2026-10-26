package org.roruku.smartformatconverter.data.repository

import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import org.roruku.smartformatconverter.engine.media3.WebmOpusHeaderPolicy
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
            "aac" -> header.size >= 2 && header[0] == 0xff.toByte() &&
                (header[1].toInt() and 0xf0) == 0xf0
            "ogg" -> header.size >= 4 && String(header, 0, 4, Charsets.US_ASCII) == "OggS"
            "flac" -> header.size >= 4 && String(header, 0, 4, Charsets.US_ASCII) == "fLaC"
            "wav" -> header.size >= 12 &&
                String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(header, 8, 4, Charsets.US_ASCII) == "WAVE"
            "jpg", "jpeg" -> header.size >= 3 &&
                header[0] == 0xff.toByte() && header[1] == 0xd8.toByte() &&
                header[2] == 0xff.toByte()
            "png" -> header.size >= 8 &&
                header.take(8) == listOf(137,80,78,71,13,10,26,10).map { it.toByte() }
            "webp" -> header.size >= 12 &&
                String(header,0,4,Charsets.US_ASCII) == "RIFF" &&
                String(header,8,4,Charsets.US_ASCII) == "WEBP"
            "gif" -> header.size >= 6 &&
                String(header,0,6,Charsets.US_ASCII) in setOf("GIF87a","GIF89a")
            "heic", "avif" -> header.size >= 12 &&
                String(header,4,4,Charsets.US_ASCII) == "ftyp" &&
                (if (ext == "avif") String(header,8,4,Charsets.US_ASCII) in setOf("avif","avis")
                    else String(header,8,4,Charsets.US_ASCII) in setOf("heic","heix","hevc","mif1","msf1"))
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
            MediaType.AUDIO -> {
                val required = when (extension.lowercase()) {
                    "m4a", "aac" -> "audio/mp4a-latm"
                    "ogg" -> "audio/opus"
                    "flac" -> "audio/flac"
                    "wav" -> "audio/raw"
                    else -> ""
                }
                trackMimes.any { it == required } && trackMimes.none { it.startsWith("video/") }
            }
            MediaType.IMAGE, MediaType.DOWNLOAD -> true
        }

    fun validate(file: File, type: MediaType, extension: String = file.extension): String? {
        if (!file.isFile || file.length() <= 0L) return "Output file is missing or empty"
        return when (type) {
            MediaType.VIDEO, MediaType.AUDIO -> validateAv(file, type, extension)
            MediaType.IMAGE -> validateImage(file, extension)
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

        // FLAC is a standalone stream rather than a muxed container. Some
        // Android vendors expose inconsistent MIME values through MediaExtractor;
        // check native STREAMINFO and the first frame directly instead.
        if (type == MediaType.AUDIO && extension.equals("flac", ignoreCase = true)) {
            return FlacStreamVerifier.validate(file)
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
                "Exported track codecs are incompatible with " + extension.uppercase() +
                    " (device reported: " + mimes.joinToString(", ") + ")"
            } else {
                // Media3/Android may mark a WebM export complete even when its
                // Opus CodecPrivate declares zero channels. Validate every
                // written Opus header before publishing the temp file to SAF.
                if (type == MediaType.VIDEO && extension.equals("webm", ignoreCase = true)) {
                    for (audioIndex in (0 until extractor.trackCount)) {
                        val audioFormat = extractor.getTrackFormat(audioIndex)
                        if (audioFormat.getString(MediaFormat.KEY_MIME) != "audio/opus") continue
                        val channels = try {
                            if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            } else 0
                        } catch (_: Exception) { 0 }
                        val opusHeader = try {
                            audioFormat.getByteBuffer("csd-0")?.duplicate()?.let { buffer ->
                                val data = ByteArray(buffer.remaining())
                                buffer.get(data)
                                data
                            }
                        } catch (_: Exception) { null }
                        val problem = WebmOpusHeaderPolicy.verifyExportedHeader(opusHeader, channels)
                        if (problem != null) return "Invalid WebM Opus track: $problem"
                    }
                }
                val index = (0 until extractor.trackCount).firstOrNull { index ->
                    extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
                        .startsWith(if (type == MediaType.VIDEO) "video/" else "audio/")
                } ?: return "Expected media track missing
                val track = extractor.getTrackFormat(index)
                if (track.containsKey(MediaFormat.KEY_DURATION) &&
                    track.getLong(MediaFormat.KEY_DURATION) <= 0L) {
                    "Exported track duration is invalid"
                } else {
                    extractor.selectTrack(index)
                    val sampleBuffer = java.nio.ByteBuffer.allocateDirect(
                        if (type == MediaType.VIDEO) 2 * 1024 * 1024 else 256 * 1024
                    )
                    val firstSampleSize = extractor.readSampleData(sampleBuffer, 0)
                    if (firstSampleSize <= 0) {
                        "Exported track contains no readable samples (format: " +
                            mimes.joinToString(", ") + ", first sample: " + firstSampleSize + ")"
                    } else null
                }
            }
        } catch (error: Exception) {
            "Exported media container cannot be parsed: " + (error.message ?: error.javaClass.simpleName)
        } finally {
            extractor.release()
        }
    }

    private fun validateImage(file: File, extension: String): String? {
        return try {
            val header = ByteArray(12)
            val n = FileInputStream(file).use { it.read(header) }
            if (!signatureMatches(extension, header.copyOf(n.coerceAtLeast(0)))) {
                "Output image signature does not match " + extension.uppercase()
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outWidth > 0 && bounds.outHeight > 0) null
                else "Output image dimensions are invalid"
            }
        } catch (error: Exception) {
            "Output image cannot be decoded: " + (error.message ?: error.javaClass.simpleName)
        }
    }
}
