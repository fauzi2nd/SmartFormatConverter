package org.roruku.smartformatconverter.engine.media3

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * FLAC via device MediaCodec encoder. PCM16 comes from our validated WAV
 * decoder; this does NOT add FFmpeg, libFLAC or an ELF dependency.
 *
 * The encoded file is accepted only when it starts with a valid fLaC marker.
 * If a vendor emits incomplete codec-specific packets, fail and clean up.
 */
internal object PlatformFlacExportEngine {
    suspend fun export(
        context: Context,
        input: Uri,
        output: File,
        onProgress: (Float, String?, String?) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val stage = File.createTempFile("flac_pcm_", ".wav", context.cacheDir)
        var encoder: MediaCodec? = null
        var completed = false
        try {
            val decode = PcmWavExportEngine.export(context, input, stage) { p, _, _ ->
                onProgress(p * 0.40f, null, null)
            }
            decode.getOrThrow()
            val header = ByteArray(44)
            FileInputStream(stage).use { require(it.read(header) == 44) { "Invalid WAV stage" } }
            val h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            require(String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(header, 8, 4, Charsets.US_ASCII) == "WAVE") { "Invalid PCM stage" }
            val channels = h.getShort(22).toInt()
            val sampleRate = h.getInt(24)
            val bitDepth = h.getShort(34).toInt()
            require(channels in 1..2 && bitDepth == 16 && sampleRate in 8000..48000) {
                "FLAC encoder supports PCM16 mono/stereo up to 48 kHz"
            }
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_FLAC, sampleRate, channels)
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_FLAC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            val codec = encoder
            val info = MediaCodec.BufferInfo()
            val frameBytes = channels * 2
            val totalFrames = (stage.length() - 44L).coerceAtLeast(1L) / frameBytes
            var fedFrames = 0L
            var fedEos = false
            var gotEos = false
            var bytesWritten = 0L
            FileInputStream(stage).use { raw ->
                require(raw.skip(44) == 44L)
                FileOutputStream(output).use { dest ->
                    while (!gotEos) {
                        currentCoroutineContext().ensureActive()
                        if (!fedEos) {
                            val slot = codec.dequeueInputBuffer(10_000)
                            if (slot >= 0) {
                                val buf = requireNotNull(codec.getInputBuffer(slot))
                                buf.clear()
                                val maxBytes = (buf.remaining() / frameBytes) * frameBytes
                                require(maxBytes > 0) { "FLAC input buffer too small" }
                                val scratch = ByteArray(minOf(maxBytes, 32 * 1024))
                                var n = 0
                                while (n < scratch.size) {
                                    val got = raw.read(scratch, n, scratch.size - n)
                                    if (got < 0) break
                                    n += got
                                }
                                if (n == 0) {
                                    codec.queueInputBuffer(slot, 0, 0,
                                        fedFrames * 1_000_000L / sampleRate,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    fedEos = true
                                } else {
                                    val usable = n - (n % frameBytes)
                                    require(usable > 0) { "Unaligned PCM16 input" }
                                    buf.put(scratch, 0, usable)
                                    codec.queueInputBuffer(slot, 0, usable,
                                        fedFrames * 1_000_000L / sampleRate, 0)
                                    fedFrames += usable / frameBytes
                                    onProgress(0.40f + 0.35f * (fedFrames.toFloat() / totalFrames).coerceIn(0f, 1f), null, null)
                                }
                            }
                        }
                        when (val slot = codec.dequeueOutputBuffer(info, 10_000)) {
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                            in 0..Int.MAX_VALUE -> {
                                if (info.size > 0) {
                                    val data = requireNotNull(codec.getOutputBuffer(slot)).duplicate()
                                    data.position(info.offset)
                                    data.limit(info.offset + info.size)
                                    val bytes = ByteArray(data.remaining())
                                    data.get(bytes)
                                    dest.write(bytes)
                                    bytesWritten += bytes.size
                                }
                                gotEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                codec.releaseOutputBuffer(slot, false)
                            }
                        }
                    }
                }
            }
            require(bytesWritten > 42L) { "FLAC encoder returned no complete stream" }
            // Some Android FLAC encoders omit totalSamples in STREAMINFO,
            // making a fully playable output appear to have unknown duration.
            // Repair only the zero field, without touching compressed frames.
            RandomAccessFile(output, "rw").use { stream ->
                val infoHeader = ByteArray(42)
                stream.seek(0L)
                stream.readFully(infoHeader)
                val updated = FlacStreamInfoSamples.withKnownTotalSamples(
                    infoHeader, fedFrames
                ) ?: error("Device FLAC encoder returned invalid STREAMINFO")
                if (!updated.contentEquals(infoHeader)) {
                    stream.seek(0L)
                    stream.write(updated)
                }
            }
            completed = true
            onProgress(1f, null, null)
            Result.success(output)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            stage.delete()
            if (!completed) output.delete()
        }
    }
}
