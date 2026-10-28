package org.roruku.smartformatconverter.engine.media3

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Streaming PCM16 WAV stage transform; no external binary or in-memory audio
 * buffering. Linear interpolation sample-rate conversion, deterministic
 * stereo-to-mono downmix, mono duplication, gain and peak normalization.
 * This is PEAK normalization (-1 dBFS), not EBU R128 loudness normalization.
 */
internal object PcmAudioProcessor {
    private fun readHeader(file: File): Triple<Int, Int, Long> {
        val bytes = FileInputStream(file).use { input ->
            ByteArray(44).also { input.readFully(it) }
        }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE" &&
            String(bytes, 12, 4, Charsets.US_ASCII) == "fmt " &&
            String(bytes, 36, 4, Charsets.US_ASCII) == "data") {
            "PCM stage must use 44-byte RIFF WAV"
        }
        require(b.getShort(20).toInt() == 1 && b.getShort(34).toInt() == 16) {
            "PCM stage must use signed little-endian 16-bit samples"
        }
        val channels = b.getShort(22).toInt()
        val rate = b.getInt(24)
        require(channels in 1..2 && rate in 8000..192000) { "Unsupported PCM source" }
        val length = file.length() - 44L
        require(length > 0 && length % (channels * 2) == 0L) { "Invalid PCM frame alignment" }
        return Triple(rate, channels, length / (channels * 2))
    }

    private fun FileInputStream.readFully(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val read = read(bytes, offset, bytes.size - offset)
            require(read > 0) { "Truncated WAV header" }
            offset += read
        }
    }

    private fun wavHeader(rate: Int, channels: Int, frames: Long): ByteArray {
        val dataLength = frames * channels * 2
        require(dataLength > 0 && dataLength <= 0xffff_ffffL - 36L) {
            "WAV exceeds RIFF 4 GiB limit"
        }
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt((dataLength + 36).toInt())
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(rate)
            putInt(rate * channels * 2)
            putShort((channels * 2).toShort())
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataLength.toInt())
        }.array()
    }

    suspend fun transform(
        input: File,
        output: File,
        sampleRate: Int? = null,
        channels: Int? = null,
        volumePercent: Int = 100,
        normalizePeak: Boolean = false
    ): Result<File> = withContext(Dispatchers.IO) {
        var completed = false
        try {
            val (originalRate, originalChannels, sourceFrames) = readHeader(input)
            val outputRate = sampleRate ?: originalRate
            val outputChannels = channels ?: originalChannels
            require(outputRate in 8000..192000 && outputChannels in 1..2)
            require(volumePercent in 0..200)
            val targetFrames = ((sourceFrames.toDouble() * outputRate / originalRate)
                .roundToLong()).coerceAtLeast(1L)
            val peak = if (normalizePeak) {
                var max = 0
                BufferedInputStream(FileInputStream(input), 65536).use { src ->
                    repeat(44) { require(src.read() >= 0) }
                    val chunk = ByteArray(65536)
                    var remaining = sourceFrames * originalChannels * 2
                    while (remaining > 0) {
                        currentCoroutineContext().ensureActive()
                        val n = src.read(chunk, 0, minOf(chunk.size.toLong(), remaining).toInt())
                        require(n > 0 && n % 2 == 0) { "Truncated PCM stage" }
                        var j = 0
                        while (j < n) {
                            val sample = ((chunk[j + 1].toInt() shl 8) or
                                (chunk[j].toInt() and 255)).toShort().toInt()
                            max = maxOf(max, kotlin.math.abs(sample))
                            j += 2
                        }
                        remaining -= n
                    }
                }
                max
            } else 0
            val normalizer = if (normalizePeak && peak > 0) {
                (32767.0 * 0.891250938 / peak)
            } else 1.0
            val gain = normalizer * volumePercent / 100.0

            BufferedInputStream(FileInputStream(input), 65536).use { src ->
                repeat(44) { require(src.read() >= 0) }
                fun readFrame(): IntArray? {
                    val frame = IntArray(originalChannels)
                    for (ch in 0 until originalChannels) {
                        val lo = src.read()
                        if (lo < 0) return if (ch == 0) null else error("Partial PCM frame")
                        val hi = src.read()
                        require(hi >= 0) { "Partial PCM sample" }
                        frame[ch] = ((hi shl 8) or lo).toShort().toInt()
                    }
                    return frame
                }

                var left = requireNotNull(readFrame())
                var right = readFrame() ?: left
                var leftIndex = 0L
                BufferedOutputStream(FileOutputStream(output), 65536).use { dest ->
                    dest.write(wavHeader(outputRate, outputChannels, targetFrames))
                    var target = 0L
                    while (target < targetFrames) {
                        if (target and 16383L == 0L)
                            currentCoroutineContext().ensureActive()
                        val sourcePosition = target.toDouble() * originalRate / outputRate
                        val targetSource = floor(sourcePosition).toLong()
                            .coerceAtMost(sourceFrames - 1)
                        while (leftIndex < targetSource) {
                            left = right
                            right = readFrame() ?: left
                            leftIndex++
                        }
                        val fraction = (sourcePosition - targetSource).coerceIn(0.0, 1.0)
                        fun interpolate(ch: Int): Double =
                            left[ch] * (1.0 - fraction) + right[ch] * fraction
                        val leftSample = interpolate(0)
                        val rightSample = if (originalChannels == 2) interpolate(1)
                            else leftSample
                        for (ch in 0 until outputChannels) {
                            val mixed = if (outputChannels == 1)
                                (leftSample + rightSample) / 2.0
                            else if (ch == 0) leftSample else rightSample
                            val sample = (mixed * gain).roundToInt().coerceIn(-32768, 32767)
                            dest.write(sample and 255)
                            dest.write((sample ushr 8) and 255)
                        }
                        target++
                    }
                }
            }
            require(output.length() == targetFrames * outputChannels * 2 + 44L)
            completed = true
            Result.success(output)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            if (!completed) output.delete()
        }
    }
}
