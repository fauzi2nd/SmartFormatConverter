package org.roruku.smartformatconverter.engine.image

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/** Recompress IDAT without modifying PNG pixels, filtering or metadata. */
internal object PngLosslessOptimizer {
    private const val MAX_BYTES = 12L * 1024 * 1024

    fun optimize(file: File): Boolean {
        if (file.length() > MAX_BYTES || file.length() < 40) return false
        val original = file.readBytes()
        val signature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        if (!original.copyOfRange(0, 8).contentEquals(signature)) return false
        data class Chunk(val type: String, val payload: ByteArray)
        val chunks = mutableListOf<Chunk>()
        val compressed = ByteArrayOutputStream()
        var pos = 8
        var firstIdat = -1
        while (pos + 12 <= original.size) {
            val length = readInt(original, pos)
            if (length < 0 || length > original.size - pos - 12) return false
            val type = String(original, pos + 4, 4, Charsets.US_ASCII)
            val data = original.copyOfRange(pos + 8, pos + 8 + length)
            if (type == "IDAT") {
                if (firstIdat == -1) firstIdat = chunks.size
                compressed.write(data)
            } else chunks.add(Chunk(type, data))
            pos += length + 12
            if (type == "IEND") break
        }
        if (firstIdat < 0 || compressed.size() > MAX_BYTES) return false
        val candidate = ByteArrayOutputStream()
        val deflater = Deflater(9)
        try {
            DeflaterOutputStream(candidate, deflater).use { zip ->
                InflaterInputStream(ByteArrayInputStream(compressed.toByteArray())).use { decoded ->
                    val buf = ByteArray(64 * 1024)
                    var expanded = 0L
                    while (true) {
                        val n = decoded.read(buf)
                        if (n < 0) break
                        expanded += n
                        if (expanded > 128L * 1024 * 1024) return false
                        zip.write(buf, 0, n)
                    }
                }
            }
        } finally { deflater.end() }
        if (candidate.size() >= compressed.size()) return false
        val rebuilt = ByteArrayOutputStream(original.size)
        rebuilt.write(signature)
        chunks.forEachIndexed { index, chunk ->
            if (index == firstIdat) writeChunk(rebuilt, "IDAT", candidate.toByteArray())
            writeChunk(rebuilt, chunk.type, chunk.payload)
        }
        // IDAT may be followed by no non-IDAT chunks except IEND, but keep both valid.
        if (firstIdat == chunks.size) writeChunk(rebuilt, "IDAT", candidate.toByteArray())
        val replacement = rebuilt.toByteArray()
        if (replacement.size >= original.size) return false
        val temp = File(file.parentFile, file.name + ".png-opt")
        temp.writeBytes(replacement)
        if (!temp.renameTo(file)) {
            temp.delete()
            return false
        }
        return true
    }

    private fun readInt(data: ByteArray, off: Int): Int =
        ((data[off].toInt() and 255) shl 24) or ((data[off + 1].toInt() and 255) shl 16) or
            ((data[off + 2].toInt() and 255) shl 8) or (data[off + 3].toInt() and 255)

    private fun writeInt(out: ByteArrayOutputStream, n: Int) {
        out.write((n ushr 24) and 255)
        out.write((n ushr 16) and 255)
        out.write((n ushr 8) and 255)
        out.write(n and 255)
    }

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        writeInt(out, data.size)
        val bytes = type.toByteArray(Charsets.US_ASCII)
        out.write(bytes)
        out.write(data)
        val crc = CRC32()
        crc.update(bytes)
        crc.update(data)
        writeInt(out, crc.value.toInt())
    }
}
