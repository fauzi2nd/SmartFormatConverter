package org.roruku.smartformatconverter

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.roruku.smartformatconverter.engine.media3.PcmAudioProcessor

class PcmAudioProcessorTest {
    private fun wave(samples: IntArray, channels: Int, rate: Int): File {
        val out = File.createTempFile("test_pcm_", ".wav")
        val size = samples.size * 2
        val head = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(size + 36)
            put("WAVEfmt ".toByteArray())
            putInt(16)
            putShort(1.toShort())
            putShort(channels.toShort())
            putInt(rate)
            putInt(rate * channels * 2)
            putShort((channels * 2).toShort())
            putShort(16.toShort())
            put("data".toByteArray())
            putInt(size)
        }.array()
        out.outputStream().use { stream ->
            stream.write(head)
            samples.forEach {
                stream.write(it and 255)
                stream.write((it ushr 8) and 255)
            }
        }
        return out
    }

    private fun readSamples(file: File): IntArray {
        val b = file.readBytes()
        return IntArray((b.size - 44) / 2) { i ->
            val offset = 44 + i * 2
            ((b[offset + 1].toInt() shl 8) or (b[offset].toInt() and 255))
                .toShort().toInt()
        }
    }

    @Test fun resamplesMonoToStereoAndKeepsDuration() = runBlocking {
        val input = wave(intArrayOf(0, 1000, 2000, 3000), 1, 32000)
        val output = File.createTempFile("test_stereo_", ".wav")
        try {
            PcmAudioProcessor.transform(
                input, output, sampleRate = 48000, channels = 2
            ).getOrThrow()
            val bytes = output.readBytes()
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(48000, header.getInt(24))
            assertEquals(2, header.getShort(22).toInt())
            val sample = readSamples(output)
            assertEquals(12, sample.size) // 4 mono frames → 6 stereo frames
            for (i in sample.indices step 2) assertEquals(sample[i], sample[i + 1])
        } finally { input.delete(); output.delete() }
    }

    @Test fun monoDownmixAndGainAreAppliedToPcm() = runBlocking {
        val input = wave(intArrayOf(1000, -1000, 2000, 0), 2, 44100)
        val output = File.createTempFile("test_mono_", ".wav")
        try {
            PcmAudioProcessor.transform(
                input, output, channels = 1, volumePercent = 50
            ).getOrThrow()
            assertArrayEquals(intArrayOf(0, 500), readSamples(output))
        } finally { input.delete(); output.delete() }
    }

    @Test fun peakNormalizationChangesPcmWithoutClipping() = runBlocking {
        val input = wave(intArrayOf(1000, -1000, 500), 1, 48000)
        val output = File.createTempFile("test_normalized_", ".wav")
        try {
            PcmAudioProcessor.transform(
                input, output, normalizePeak = true
            ).getOrThrow()
            val peak = readSamples(output).maxOf { kotlin.math.abs(it) }
            assertTrue(peak in 29000..29300)
        } finally { input.delete(); output.delete() }
    }
}
