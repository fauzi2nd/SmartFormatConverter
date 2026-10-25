package org.roruku.smartformatconverter.data.repository

import java.io.File
import java.io.RandomAccessFile

/**
 * Checks the native FLAC stream envelope without depending on MediaExtractor,
 * whose reported track MIME and extractor support differ across OEM builds.
 * This is structural validation, NOT a full decode or CRC check.
 */
internal object FlacStreamVerifier {
    fun validate(file: File): String? = try {
        if (!file.isFile || file.length() < 46L) {
            "FLAC stream is missing or truncated"
        } else {
            RandomAccessFile(file, "r").use { input ->
                val magic = ByteArray(4)
                input.readFully(magic)
                if (!magic.contentEquals("fLaC".toByteArray(Charsets.US_ASCII))) {
                    return "FLAC signature is missing"
                }
                var last = false
                var metadataCount = 0
                while (!last && metadataCount++ < 128) {
                    if (input.filePointer + 4L > input.length()) {
                        return "FLAC metadata is truncated"
                    }
                    val block = input.readUnsignedByte()
                    last = (block and 0x80) != 0
                    val blockType = block and 0x7f
                    val size = (input.readUnsignedByte() shl 16) or
                        (input.readUnsignedByte() shl 8) or input.readUnsignedByte()
                    if (size.toLong() > input.length() - input.filePointer) {
                        return "FLAC metadata block exceeds file length"
                    }
                    if (metadataCount == 1) {
                        if (blockType != 0 || size != 34) {
                            return "FLAC is missing the required 34-byte STREAMINFO block"
                        }
                        val streamInfo = ByteArray(34)
                        input.readFully(streamInfo)
                        // STREAMINFO: sample rate 20b, channels-1 3b,
                        // bitsPerSample-1 5b, followed by 36b total samples.
                        val rate = ((streamInfo[10].toInt() and 0xff) shl 12) or
                            ((streamInfo[11].toInt() and 0xff) shl 4) or
                            ((streamInfo[12].toInt() and 0xf0) shr 4)
                        val channels = ((streamInfo[12].toInt() and 0x0e) shr 1) + 1
                        val depth = (((streamInfo[12].toInt() and 1) shl 4) or
                            ((streamInfo[13].toInt() and 0xf0) shr 4)) + 1
                        if (rate !in 1..655350 || channels !in 1..8 || depth !in 4..32) {
                            return "FLAC STREAMINFO contains invalid audio properties"
                        }
                    } else {
                        input.seek(input.filePointer + size)
                    }
                }
                if (!last) return "FLAC metadata has no final block"
                if (input.length() - input.filePointer < 4) {
                    return "FLAC contains no encoded audio frame"
                }
                val first = input.readUnsignedByte()
                val second = input.readUnsignedByte()
                if (first != 0xff || (second and 0xfe) != 0xf8) {
                    "FLAC audio frame sync is missing after metadata"
                } else {
                    null
                }
            }
        }
    } catch (e: Exception) {
        "Cannot inspect FLAC stream: " + (e.message ?: e.javaClass.simpleName)
    }
}
