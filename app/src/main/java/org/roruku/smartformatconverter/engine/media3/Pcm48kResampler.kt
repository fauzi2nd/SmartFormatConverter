package org.roruku.smartformatconverter.engine.media3

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Bounded-memory PCM16 windowed-sinc resampler, used only when an Opus encoder
 * cannot directly accept the source WAV rate. Produces headerless 48 kHz PCM16.
 * Does not resample by changing container metadata or drop frames.
 */
internal object Pcm48kResampler {
    private const val TARGET_RATE = 48_000
    private const val TAPS = 24
    private const val PHASES = 512

    suspend fun convert(wav: File, output: File, rate: Int, channels: Int,
                        onProgress: (Float) -> Unit): Long {
        require(rate in 8_000..192_000 && channels in 1..2)
        val inputFrames = (wav.length() - 44L) / (channels * 2)
        require(inputFrames > 0) { "Input WAV has no PCM data" }
        val outputFrames = (inputFrames * TARGET_RATE + rate - 1) / rate
        val cutoff = min(1.0, TARGET_RATE.toDouble() / rate) * 0.96
        val weights = Array(PHASES + 1) { phase ->
            val fraction = phase.toDouble() / PHASES
            val taps = DoubleArray(TAPS)
            var sum = 0.0
            for (t in 0 until TAPS) {
                val x = t - 11 - fraction
                val ax = kotlin.math.abs(x)
                val sinc = if (ax < 1e-10) cutoff else sin(PI * x * cutoff) / (PI * x)
                val window = 0.42 + 0.5 * cos(PI * x / 12.0) +
                    0.08 * cos(2.0 * PI * x / 12.0)
                taps[t] = sinc * window
                sum += taps[t]
            }
            for (i in taps.indices) taps[i] /= sum
            taps
        }

        BufferedInputStream(FileInputStream(wav), 65536).use { input ->
            var remainingHeader = 44
            while (remainingHeader > 0) {
                val skipped = input.skip(remainingHeader.toLong())
                require(skipped > 0L) { "Truncated WAV header" }
                remainingHeader -= skipped.toInt()
            }
            // Hold only 64 PCM frames, including enough look-ahead for the sinc kernel.
            val ring = Array(64) { ShortArray(channels) }
            val bytes = ByteArray(channels * 2)
            var latest = -1L
            fun ensure(target: Long) {
                val boundedTarget = min(target, inputFrames - 1L)
                while (latest < boundedTarget) {
                    var n = 0
                    while (n < bytes.size) {
                        val count = input.read(bytes, n, bytes.size - n)
                        require(count > 0) { "Truncated PCM input during resampling" }
                        n += count
                    }
                    latest++
                    val frame = ring[(latest % ring.size).toInt()]
                    for (channel in 0 until channels) {
                        val off = channel * 2
                        frame[channel] = ((bytes[off].toInt() and 255) or
                            ((bytes[off + 1].toInt() and 255) shl 8)).toShort()
                    }
                }
            }
            BufferedOutputStream(FileOutputStream(output), 65536).use { dest ->
                val chunk = ByteArray(32768)
                var used = 0
                for (i in 0 until outputFrames) {
                    if (i % 8192L == 0L) {
                        currentCoroutineContext().ensureActive()
                        onProgress(i.toFloat() / outputFrames)
                    }
                    val sourcePos = i.toDouble() * rate / TARGET_RATE
                    val center = floor(sourcePos).toLong()
                    val fraction = (sourcePos - center).coerceIn(0.0, 1.0)
                    val phase = (fraction * PHASES).roundToInt().coerceIn(0, PHASES)
                    val kernel = weights[phase]
                    ensure(center + 12L)
                    for (channel in 0 until channels) {
                        var value = 0.0
                        for (tap in 0 until TAPS) {
                            val frameIndex = (center + tap - 11L).coerceIn(0L, inputFrames - 1L)
                            value += ring[(frameIndex % ring.size).toInt()][channel] * kernel[tap]
                        }
                        val sample = value.roundToInt().coerceIn(-32768, 32767)
                        chunk[used++] = sample.toByte()
                        chunk[used++] = (sample ushr 8).toByte()
                        if (used == chunk.size) {
                            dest.write(chunk, 0, used)
                            used = 0
                        }
                    }
                }
                if (used > 0) dest.write(chunk, 0, used)
            }
        }
        onProgress(1f)
        return outputFrames
    }
}
