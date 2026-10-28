package org.roruku.smartformatconverter.engine.media3

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Safely adds standard FLAC Vorbis comments (title, artist, album, genre).
 * Does not pretend to preserve cover art or unsupported MP4/ADTS/OGG metadata.
 * Rewrites metadata blocks without touching any encoded FLAC audio frames.
 */
internal object FlacMetadataWriter {
    private fun le32(value: Int) = byteArrayOf(
        (value and 255).toByte(), (value ushr 8).toByte(),
        (value ushr 16).toByte(), (value ushr 24).toByte()
    )

    suspend fun preserve(context: Context, uri: Uri, output: File) =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            val tags = mutableMapOf<String, String>()
            try {
                retriever.setDataSource(context, uri)
                listOf(
                    "TITLE" to MediaMetadataRetriever.METADATA_KEY_TITLE,
                    "ARTIST" to MediaMetadataRetriever.METADATA_KEY_ARTIST,
                    "ALBUM" to MediaMetadataRetriever.METADATA_KEY_ALBUM,
                    "GENRE" to MediaMetadataRetriever.METADATA_KEY_GENRE
                ).forEach { (key, field) ->
                    retriever.extractMetadata(field)?.trim()?.takeIf { it.isNotEmpty() }
                        ?.take(4096)?.let { tags[key] = it }
                }
            } catch (_: Exception) {
                // Source tags may be unreadable. This is not an audio error.
            } finally {
                runCatching { retriever.release() }
            }
            if (tags.isNotEmpty()) addComments(output, tags)
        }

    internal fun addComments(output: File, comments: Map<String, String>) {
        if (comments.isEmpty()) return
        val vendor = "SmartFormatConverter".toByteArray(Charsets.UTF_8)
        val values = comments.map { (name, value) ->
            "$name=$value".toByteArray(Charsets.UTF_8)
        }
        val payloadSize = 4L + vendor.size + 4L +
            values.sumOf { 4L + it.size }
        require(payloadSize in 1..0xFFFFFFL) { "FLAC metadata block too large" }
        val temporary = File.createTempFile("flac_metadata_", ".tmp", output.parentFile)
        try {
            DataInputStream(FileInputStream(output).buffered()).use { input ->
                DataOutputStream(FileOutputStream(temporary).buffered()).use { dest ->
                    val signature = ByteArray(4)
                    input.readFully(signature)
                    require(String(signature, Charsets.US_ASCII) == "fLaC") {
                        "Invalid FLAC signature"
                    }
                    dest.write(signature)
                    var completed = false
                    var blocks = 0
                    while (!completed) {
                        require(++blocks <= 256) { "Too many FLAC metadata blocks" }
                        val header = input.readUnsignedByte()
                        val size = (input.readUnsignedByte() shl 16) or
                            (input.readUnsignedByte() shl 8) or input.readUnsignedByte()
                        val payload = ByteArray(size)
                        input.readFully(payload)
                        completed = header and 0x80 != 0
                        // The new Vorbis comment becomes the last metadata block.
                        dest.writeByte(header and 0x7f)
                        dest.writeByte((size ushr 16) and 255)
                        dest.writeByte((size ushr 8) and 255)
                        dest.writeByte(size and 255)
                        dest.write(payload)
                    }
                    dest.writeByte(0x80 or 4) // last metadata block, Vorbis comments
                    val count = payloadSize.toInt()
                    dest.writeByte((count ushr 16) and 255)
                    dest.writeByte((count ushr 8) and 255)
                    dest.writeByte(count and 255)
                    dest.write(le32(vendor.size))
                    dest.write(vendor)
                    dest.write(le32(values.size))
                    values.forEach {
                        dest.write(le32(it.size))
                        dest.write(it)
                    }
                    input.copyTo(dest)
                }
            }
            // Keep the original on disk until metadata copy succeeds.
            FileInputStream(temporary).buffered().use { src ->
                FileOutputStream(output).buffered().use { dst -> src.copyTo(dst) }
            }
        } finally {
            temporary.delete()
        }
    }
}
