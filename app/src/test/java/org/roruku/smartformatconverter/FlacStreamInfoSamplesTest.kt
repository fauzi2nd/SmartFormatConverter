package org.roruku.smartformatconverter

import org.junit.Assert.*
import org.junit.Test
import org.roruku.smartformatconverter.engine.media3.FlacStreamInfoSamples

class FlacStreamInfoSamplesTest {
    private fun header(): ByteArray {
        val bytes = ByteArray(42)
        "fLaC".toByteArray(Charsets.US_ASCII).copyInto(bytes)
        bytes[4] = 0x80.toByte()
        bytes[7] = 34
        // A valid 44.1 kHz stereo 16-bit packed STREAMINFO sample format
        val sampleRate = 44100L
        val packing = (sampleRate shl 44) or (1L shl 41) or (15L shl 36)
        for (i in 0..7) bytes[18 + i] = (packing ushr (56 - i * 8)).toByte()
        return bytes
    }

    @Test fun addsSampleCountWithoutTouchingOtherFields() {
        val before = header()
        val samples = 44_100L * 225
        val after = FlacStreamInfoSamples.withKnownTotalSamples(before, samples)!!
        assertEquals(samples, FlacStreamInfoSamples.totalSamples(after))
        assertEquals(0L, FlacStreamInfoSamples.totalSamples(before))
        assertArrayEquals(before.copyOfRange(0, 18), after.copyOfRange(0, 18))
        assertArrayEquals(before.copyOfRange(26, 42), after.copyOfRange(26, 42))
        assertEquals(before[18].toInt(), after[18].toInt())
        assertEquals(before[19].toInt(), after[19].toInt())
        assertEquals(before[20].toInt(), after[20].toInt())
    }

    @Test fun preservesAlreadyKnownMetadata() {
        val filled = FlacStreamInfoSamples.withKnownTotalSamples(header(), 123456L)!!
        assertArrayEquals(filled, FlacStreamInfoSamples.withKnownTotalSamples(filled, 654321L))
    }

    @Test fun rejectsBrokenHeaderAndOutOfRangeValues() {
        assertNull(FlacStreamInfoSamples.withKnownTotalSamples(ByteArray(10), 20L))
        assertNull(FlacStreamInfoSamples.withKnownTotalSamples(header(), 0L))
        assertNull(FlacStreamInfoSamples.withKnownTotalSamples(header(), (1L shl 36)))
        assertNull(FlacStreamInfoSamples.totalSamples(ByteArray(42)))
    }
}
