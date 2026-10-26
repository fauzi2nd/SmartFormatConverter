package org.roruku.smartformatconverter

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.roruku.smartformatconverter.engine.media3.WebmOpusBitDepthRepair

/** Regression reproduces the exact 62 64 84 BF 80 00 00 seen in device WebM #420. */
class WebmOpusBitDepthRepairTest {
    private fun element(id: ByteArray, payload: ByteArray): ByteArray {
        require(payload.size < 127)
        return id + byteArrayOf((0x80 or payload.size).toByte()) + payload
    }

    private fun sample(opus: Boolean, invalidBitDepth: Boolean): ByteArray {
        val codec = element(byteArrayOf(0x86.toByte()),
            (if (opus) "A_OPUS" else "A_VORBIS").toByteArray(Charsets.US_ASCII))
        val fields = element(byteArrayOf(0x9F.toByte()), byteArrayOf(1))
        val faulty = if (invalidBitDepth) {
            byteArrayOf(0x62, 0x64, 0x84.toByte(), 0xBF.toByte(), 0x80.toByte(), 0, 0)
        } else byteArrayOf()
        val audio = element(byteArrayOf(0xE1.toByte()), fields + faulty)
        val entry = element(byteArrayOf(0xAE.toByte()), codec + audio)
        val tracks = element(byteArrayOf(0x16, 0x54, 0xAE.toByte(), 0x6B), entry)
        val ebml = element(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()), byteArrayOf())
        val segment = element(byteArrayOf(0x18, 0x53, 0x80.toByte(), 0x67), tracks)
        return ebml + segment
    }

    @Test fun defectiveOpusBitDepthIsReplacedWithoutChangingFileSize() {
        val file = File.createTempFile("opus-bitdepth", ".webm")
        try {
            val original = sample(opus = true, invalidBitDepth = true)
            file.writeBytes(original)
            assertEquals(1, WebmOpusBitDepthRepair.repair(file))
            val after = file.readBytes()
            assertEquals(original.size, after.size)
            val marker = byteArrayOf(0xEC.toByte(), 0x85.toByte(), 0, 0, 0, 0, 0)
            val found = after.indices.any { idx ->
                idx + marker.size <= after.size &&
                    after.copyOfRange(idx, idx + marker.size).contentEquals(marker)
            }
            assertTrue(found)
            assertEquals(0, WebmOpusBitDepthRepair.repair(file))
        } finally {
            file.delete()
        }
    }

    @Test fun videoOnlyAndNonOpusTracksRemainUnchanged() {
        val file = File.createTempFile("nonopus-bitdepth", ".webm")
        try {
            val vorbis = sample(opus = false, invalidBitDepth = true)
            file.writeBytes(vorbis)
            assertEquals(0, WebmOpusBitDepthRepair.repair(file))
            assertArrayEquals(vorbis, file.readBytes())

            val clean = sample(opus = true, invalidBitDepth = false)
            file.writeBytes(clean)
            assertEquals(0, WebmOpusBitDepthRepair.repair(file))
            assertArrayEquals(clean, file.readBytes())
        } finally {
            file.delete()
        }
    }

    @Test fun truncatedEbmlFailsClosed() {
        val file = File.createTempFile("corrupt-ebml", ".webm")
        try {
            file.writeBytes(byteArrayOf(0, 0, 0, 0))
            assertThrows(IllegalArgumentException::class.java) {
                WebmOpusBitDepthRepair.repair(file)
            }
        } finally {
            file.delete()
        }
    }
}
