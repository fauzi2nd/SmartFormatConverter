package org.roruku.smartformatconverter

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.roruku.smartformatconverter.engine.media3.FlacMetadataWriter

class FlacMetadataWriterTest {
    @Test fun addsVorbisCommentsWithoutChangingFramePayload() {
        val file = File.createTempFile("test_comments_", ".flac")
        try {
            val marker = "fLaC".toByteArray()
            val streamInfo = ByteArray(34)
            val frame = byteArrayOf(0xff.toByte(), 0xf8.toByte(), 7, 4, 3, 2, 1)
            file.writeBytes(marker + byteArrayOf(0x80.toByte(), 0, 0, 34) +
                streamInfo + frame)
            FlacMetadataWriter.addComments(file, mapOf(
                "TITLE" to "ユリイカ", "ARTIST" to "Example"
            ))
            val bytes = file.readBytes()
            assertEquals("fLaC", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals(0, bytes[4].toInt() and 0x80)
            assertEquals(0x84, bytes[42].toInt() and 255)
            assertTrue(String(bytes, Charsets.UTF_8).contains("TITLE=ユリイカ"))
            assertTrue(bytes.copyOfRange(bytes.size - frame.size, bytes.size)
                .contentEquals(frame))
        } finally { file.delete() }
    }
}
