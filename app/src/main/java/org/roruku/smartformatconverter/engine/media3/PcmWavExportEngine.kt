package org.roruku.smartformatconverter.engine.media3

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PCM16 WAV output from any first audio track decodable by Android MediaCodec.
 * No FFmpeg, native binary packaging or fake PCM "encoder".
 *
 * Boundaries: mono/stereo PCM16; 4 GiB RIFF limit; no sample-rate forcing.
 * Failure/cancellation never publishes a partial file.
 */
internal object PcmWavExportEngine {
    suspend fun export(
        context: Context,
        inputUri: Uri,
        output: File,
        onProgress: (Float, String?, String?) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, inputUri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
            } ?: error("Source contains no audio track")
            extractor.selectTrack(track)
            val original = extractor.getTrackFormat(track)
            val mime = original.getString(MediaFormat.KEY_MIME) ?: error("Audio MIME missing")
            val durationUs = if (original.containsKey(MediaFormat.KEY_DURATION))
                original.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L) else 0L

            if (mime == MediaFormat.MIMETYPE_AUDIO_RAW) {
                require(pcmEncoding(original) ==
                    AudioFormat.ENCODING_PCM_16BIT) { "WAV export requires PCM16 input" }
                val rate = original.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channels = original.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                RandomAccessFile(output, "rw").use { wav ->
                    prepare(wav, rate, channels)
                    val scratch = ByteArray(32 * 1024)
                    val sample = ByteBuffer.allocateDirect(scratch.size)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        sample.clear()
                        val bytes = extractor.readSampleData(sample, 0)
                        if (bytes < 0) break
                        sample.position(0)
                        sample.limit(bytes)
                        while (sample.hasRemaining()) {
                            val count = minOf(sample.remaining(), scratch.size)
                            sample.get(scratch, 0, count)
                            writeLimited(wav, scratch, count)
                        }
                        val t = extractor.sampleTime
                        if (durationUs > 0 && t >= 0) onProgress((t.toFloat() / durationUs).coerceIn(0f, 0.99f), null, null)
                        extractor.advance()
                    }
                    finish(wav, rate, channels)
                }
            } else {
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(original, null, null, 0)
                codec.start()
                val decoder = codec
                val bufferInfo = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                var wav: RandomAccessFile? = null
                var wavRate = 0
                var wavChannels = 0
                val scratch = ByteArray(32 * 1024)
                try {
                    while (!outputDone) {
                        currentCoroutineContext().ensureActive()
                        if (!inputDone) {
                            val slot = decoder.dequeueInputBuffer(10_000)
                            if (slot >= 0) {
                                val input = requireNotNull(decoder.getInputBuffer(slot))
                                input.clear()
                                val bytes = extractor.readSampleData(input, 0)
                                if (bytes < 0) {
                                    decoder.queueInputBuffer(slot, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputDone = true
                                } else {
                                    decoder.queueInputBuffer(slot, 0, bytes, extractor.sampleTime.coerceAtLeast(0), extractor.sampleFlags)
                                    extractor.advance()
                                }
                            }
                        }
                        when (val slot = decoder.dequeueOutputBuffer(bufferInfo, 10_000)) {
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                val fmt = decoder.outputFormat
                                val encoding = pcmEncoding(fmt)
                                require(encoding == AudioFormat.ENCODING_PCM_16BIT) {
                                    "Decoder emits non-PCM16 audio; WAV export not supported on this device"
                                }
                                val rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                val channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                wavRate = rate
                                wavChannels = channels
                                wav = RandomAccessFile(output, "rw").also { prepare(it, rate, channels) }
                            }
                            in 0..Int.MAX_VALUE -> {
                                if (wav == null) {
                                    val fmt = decoder.outputFormat
                                    val rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                    val channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                    require(pcmEncoding(fmt) ==
                                        AudioFormat.ENCODING_PCM_16BIT) { "Expected PCM16 decoder output" }
                                    wavRate = rate
                                    wavChannels = channels
                                    wav = RandomAccessFile(output, "rw").also { prepare(it, rate, channels) }
                                }
                                if (bufferInfo.size > 0 && bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                    val data = requireNotNull(decoder.getOutputBuffer(slot)).duplicate()
                                    data.position(bufferInfo.offset)
                                    data.limit(bufferInfo.offset + bufferInfo.size)
                                    while (data.hasRemaining()) {
                                        val count = minOf(data.remaining(), scratch.size)
                                        data.get(scratch, 0, count)
                                        writeLimited(requireNotNull(wav), scratch, count)
                                    }
                                }
                                if (durationUs > 0) onProgress(
                                    (bufferInfo.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 0.99f), null, null
                                )
                                outputDone = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                decoder.releaseOutputBuffer(slot, false)
                            }
                        }
                    }
                    val file = requireNotNull(wav) { "No PCM frames were decoded" }
                    finish(file, wavRate, wavChannels)
                } finally {
                    wav?.close()
                }
            }
            require(output.length() > 44L) { "WAV contains no audio data" }
            onProgress(1f, null, null)
            Result.success(output)
        } catch (cancel: CancellationException) {
            output.delete()
            throw cancel
        } catch (e: Exception) {
            output.delete()
            Result.failure(e)
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    private fun prepare(raf: RandomAccessFile, sampleRate: Int, channels: Int) {
        require(sampleRate in 8000..192000 && channels in 1..2) { "WAV supports 8–192 kHz mono/stereo PCM16" }
        raf.setLength(0)
        raf.write(ByteArray(44))
        // A placeholder header is filled in only after successful decoding.
        raf.seek(44)
    }

    private fun writeLimited(raf: RandomAccessFile, chunk: ByteArray, size: Int) {
        require(raf.filePointer + size <= 0xFFFF_FFFFL) { "WAV RIFF exceeds 4 GiB; split the source" }
        raf.write(chunk, 0, size)
    }

    private fun pcmEncoding(format: MediaFormat): Int =
        if (format.containsKey(MediaFormat.KEY_PCM_ENCODING))
            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
        else AudioFormat.ENCODING_PCM_16BIT

    /** Standard little-endian RIFF PCM16; exact size, rate and channels. */
    private fun finish(raf: RandomAccessFile, sampleRate: Int, channels: Int) {
        require(sampleRate in 8000..192000 && channels in 1..2)
        val dataLength = raf.length() - 44
        require(dataLength > 0 && dataLength <= 0xFFFF_FFFFL - 36)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt((dataLength + 36).toInt())
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1) // integer PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(sampleRate * channels * 2)
        header.putShort((channels * 2).toShort())
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataLength.toInt())
        raf.seek(0)
        raf.write(header.array())
    }
}
