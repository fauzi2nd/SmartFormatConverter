package org.roruku.smartformatconverter

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.roruku.smartformatconverter.engine.media3.WebmDefaultDurationWriter

class WebmDefaultDurationWriterTest {
    private fun el(id: ByteArray, data: ByteArray, width: Int = 1): ByteArray {
        require(data.size < 127 || width > 1)
        val size = if (width == 1) byteArrayOf((0x80 or data.size).toByte())
            else byteArrayOf((0x40 or (data.size ushr 8)).toByte(), data.size.toByte())
        return id + size + data
    }

    private fun fixture(): ByteArray {
        val opus = el(byteArrayOf(0x86.toByte()), "A_OPUS".toByteArray())
        val audio = el(byteArrayOf(0xE1.toByte()),
            el(byteArrayOf(0x9f.toByte()), byteArrayOf(1)) +
            byteArrayOf(0xEC.toByte(), 0x85.toByte(), 0, 0, 0, 0, 0))
        val audioTrack = el(byteArrayOf(0xAE.toByte()), opus + audio)
        val videoTrack = el(byteArrayOf(0xAE.toByte()),
            el(byteArrayOf(0x86.toByte()), "V_VP9".toByteArray()) +
            byteArrayOf(0x9C.toByte(), 0x81.toByte(), 0) +
            el(byteArrayOf(0x22, 0xB5.toByte(), 0x9C.toByte()),
                "und".toByteArray()))
        val tracks = el(byteArrayOf(0x16, 0x54, 0xAE.toByte(), 0x6B),
            audioTrack + videoTrack, 2)
        return el(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()),
            byteArrayOf()) +
            byteArrayOf(0x18, 0x53, 0x80.toByte(), 0x67, 0xFF.toByte()) +
            tracks + byteArrayOf(0x1F, 0x43, 0xB6.toByte(), 0x75, 0x81.toByte(), 0)
    }

    @Test fun nearCfrVideoGetsTrueMeasuredDuration() {
        val timestamps = (0 until 306).map { it * 33_333L }
        assertEquals(33_333_000L, WebmDefaultDurationWriter.frameDurationNs(timestamps))
        assertEquals(null, WebmDefaultDurationWriter.frameDurationNs(
            listOf(0L, 33_000L, 66_000L, 150_000L, 183_000L)))
    }

    @Test fun writingDefaultDurationPreservesAllOffsetsAndCodecPayload() {
        val f = File.createTempFile("webm-default-duration", ".webm")
        try {
            val original = fixture()
            f.writeBytes(original)
            assertTrue(WebmDefaultDurationWriter.write(f, 33_333_333L))
            val updated = f.readBytes()
            assertEquals(original.size, updated.size)
            assertArrayEquals(original.copyOfRange(original.size - 6, original.size),
                updated.copyOfRange(updated.size - 6, updated.size))
            assertTrue(updated.toList().windowed(4).any {
                it == listOf(0x23.toByte(), 0xE3.toByte(), 0x83.toByte(), 0x84.toByte())
            })
            assertTrue(String(updated, Charsets.ISO_8859_1).contains("und"))
            assertFalse(WebmDefaultDurationWriter.write(f, 33_333_333L))
        } finally {
            f.delete()
        }
    }

    @Test fun corruptFileIsNotChanged() {
        val f = File.createTempFile("webm-not-ebml", ".webm")
        try {
            val source = byteArrayOf(0, 1, 2, 3)
            f.writeBytes(source)
            assertFalse(WebmDefaultDurationWriter.write(f, 33_333_333L))
            assertArrayEquals(source, f.readBytes())
        } finally { f.delete() }
    }
}
