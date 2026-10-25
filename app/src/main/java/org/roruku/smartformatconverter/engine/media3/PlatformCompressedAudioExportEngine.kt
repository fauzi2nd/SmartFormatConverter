package org.roruku.smartformatconverter.engine.media3

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Native Android encoders with no FFmpeg/JNI/new third-party encoder:
 * PCM16 -> MediaCodec AAC/Opus -> platform MP4/OGG muxer or ADTS stream.
 *
 * The intermediate WAV is necessary to keep decoder and encoder state isolated
 * across devices; all staging files are deleted on exit.
 */
internal object PlatformCompressedAudioExportEngine {
    suspend fun export(
        context: Context,
        input: Uri,
        output: File,
        format: AudioOutputFormat,
        bitrateKbps: Int,
        onProgress: (Float, String?, String?) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        require(format in setOf(AudioOutputFormat.M4A, AudioOutputFormat.AAC, AudioOutputFormat.OGG))
        val pcmStage = File.createTempFile("audio_decode_", ".wav", context.cacheDir)
        var resampled: File? = null
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var rawAac: FileOutputStream? = null
        var muxerStarted = false
        var success = false
        try {
            PcmWavExportEngine.export(context, input, pcmStage) { p, _, _ ->
                onProgress(p * 0.35f, null, null)
            }.getOrThrow()

            val wavHeader = ByteArray(44)
            FileInputStream(pcmStage).use { stream ->
                var n = 0
                while (n < wavHeader.size) {
                    val read = stream.read(wavHeader, n, wavHeader.size - n)
                    require(read > 0) { "Truncated PCM stage" }
                    n += read
                }
            }
            val header = ByteBuffer.wrap(wavHeader).order(ByteOrder.LITTLE_ENDIAN)
            require(String(wavHeader, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(wavHeader, 8, 4, Charsets.US_ASCII) == "WAVE") { "Invalid intermediate WAV" }
            val channels = header.getShort(22).toInt()
            val originalRate = header.getInt(24)
            val bits = header.getShort(34).toInt()
            require(channels in 1..2 && bits == 16) { "Encoder requires PCM16 mono/stereo" }

            val opus = format == AudioOutputFormat.OGG
            require(!opus || Build.VERSION.SDK_INT >= 29) { "OGG Opus requires Android 10 or newer" }
            val rate = if (opus) 48_000 else originalRate
            require(rate in setOf(8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000)) {
                "AAC output does not support this input sample rate: " + originalRate +
                    " Hz. Sample rate conversion is not available for AAC yet."
            }
            val pcmInput: File
            val skip: Long
            if (opus && originalRate != rate) {
                val stage = File.createTempFile("opus_pcm48_", ".raw", context.cacheDir)
                resampled = stage
                Pcm48kResampler.convert(pcmStage, stage, originalRate, channels) {
                    onProgress(0.35f + it * 0.15f, null, null)
                }
                pcmInput = stage
                skip = 0L
            } else {
                pcmInput = pcmStage
                skip = 44L
                onProgress(0.5f, null, null)
            }

            val mime = if (opus) MediaFormat.MIMETYPE_AUDIO_OPUS else MediaFormat.MIMETYPE_AUDIO_AAC
            val encoderFormat = MediaFormat.createAudioFormat(mime, rate, channels).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitrateKbps.coerceIn(32, 512) * 1000)
                if (!opus) setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }
            codec = MediaCodec.createEncoderByType(mime)
            val activeCodec = codec
            requireNotNull(activeCodec)
            require(activeCodec.codecInfo.getCapabilitiesForType(mime)
                .audioCapabilities?.isSampleRateSupported(rate) == true) {
                "The device " + mime + " encoder does not support " + rate + " Hz"
            }
            activeCodec.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            activeCodec.start()

            if (format == AudioOutputFormat.AAC) {
                rawAac = FileOutputStream(output)
            } else {
                // This API 29 constant is guarded by the SDK check above.
                @SuppressLint("InlinedApi")
                val containerType = if (opus) MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
                    else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                muxer = MediaMuxer(output.absolutePath, containerType)
            }

            val frameBytes = channels * 2
            val totalFrames = ((pcmInput.length() - skip) / frameBytes).coerceAtLeast(1L)
            val info = MediaCodec.BufferInfo()
            var submittedFrames = 0L
            var inputDone = false
            var outputDone = false
            var track = -1
            var packetCount = 0L
            var lastActivity = SystemClock.elapsedRealtime()
            FileInputStream(pcmInput).use { source ->
                var remain = skip
                while (remain > 0) {
                    val advanced = source.skip(remain)
                    require(advanced > 0L) { "Cannot skip staged WAV header" }
                    remain -= advanced
                }
                while (!outputDone) {
                    currentCoroutineContext().ensureActive()
                    require(SystemClock.elapsedRealtime() - lastActivity < 90_000L) {
                        "Android " + mime + " encoder stalled for more than 90 seconds"
                    }
                    if (!inputDone) {
                        val index = activeCodec.dequeueInputBuffer(10_000)
                        if (index >= 0) {
                            val buffer = requireNotNull(activeCodec.getInputBuffer(index))
                            buffer.clear()
                            val capacity = (minOf(buffer.remaining(), 32768) / frameBytes) * frameBytes
                            require(capacity > 0) { "Encoder PCM input buffer is too small" }
                            val payload = ByteArray(capacity)
                            var count = 0
                            while (count < payload.size) {
                                val read = source.read(payload, count, payload.size - count)
                                if (read < 0) break
                                require(read > 0)
                                count += read
                            }
                            require(count % frameBytes == 0) { "Partial PCM audio frame" }
                            val pts = submittedFrames * 1_000_000L / rate
                            if (count == 0) {
                                activeCodec.queueInputBuffer(index, 0, 0, pts,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                buffer.put(payload, 0, count)
                                activeCodec.queueInputBuffer(index, 0, count, pts, 0)
                                submittedFrames += count / frameBytes
                                onProgress(0.5f + 0.48f * (submittedFrames.toFloat() / totalFrames)
                                    .coerceIn(0f, 1f), null, null)
                            }
                            lastActivity = SystemClock.elapsedRealtime()
                        }
                    }
                    when (val slot = activeCodec.dequeueOutputBuffer(info, 10_000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxer != null) {
                                require(!muxerStarted) { "Audio encoder changed output format twice" }
                                track = muxer.addTrack(activeCodec.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                            lastActivity = SystemClock.elapsedRealtime()
                        }
                        in 0..Int.MAX_VALUE -> {
                            if (info.size > 0 &&
                                info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                val data = requireNotNull(activeCodec.getOutputBuffer(slot)).duplicate()
                                data.position(info.offset)
                                data.limit(info.offset + info.size)
                                if (rawAac != null) {
                                    require(info.size + 7 <= 8191) { "AAC frame exceeds ADTS maximum" }
                                    rawAac.write(adtsHeader(info.size, rate, channels))
                                    val payload = ByteArray(info.size)
                                    data.get(payload)
                                    rawAac.write(payload)
                                } else {
                                    require(muxerStarted && track >= 0) {
                                        "Audio output sample arrived before muxer track format"
                                    }
                                    val sampleInfo = MediaCodec.BufferInfo()
                                    sampleInfo.set(info.offset, info.size, info.presentationTimeUs,
                                        info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv())
                                    muxer?.writeSampleData(track, data, sampleInfo)
                                }
                                packetCount++
                            }
                            outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            activeCodec.releaseOutputBuffer(slot, false)
                            lastActivity = SystemClock.elapsedRealtime()
                        }
                    }
                }
            }
            require(packetCount > 0) { "Audio encoder returned no compressed samples" }
            if (muxerStarted) {
                muxer?.stop()
                muxerStarted = false
            }
            rawAac?.flush()
            require(output.length() > 16L) { "Encoded audio output is empty" }
            success = true
            onProgress(1f, null, null)
            Result.success(output)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            try { rawAac?.close() } catch (_: Exception) {}
            try { if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            pcmStage.delete()
            resampled?.delete()
            if (!success) output.delete()
        }
    }

    /** MPEG-4 AAC LC, 7-byte ADTS frame header (no CRC). */
    internal fun adtsHeader(payloadSize: Int, sampleRate: Int, channels: Int): ByteArray {
        val frequencyIndex = when (sampleRate) {
            48000 -> 3
            44100 -> 4
            32000 -> 5
            24000 -> 6
            22050 -> 7
            16000 -> 8
            12000 -> 9
            11025 -> 10
            8000 -> 11
            else -> error("Unsupported ADTS sample rate")
        }
        require(channels in 1..2 && payloadSize > 0 && payloadSize + 7 <= 8191)
        val frameLength = payloadSize + 7
        val aacLcProfile = 1
        return byteArrayOf(
            0xff.toByte(), 0xf1.toByte(),
            (((aacLcProfile and 3) shl 6) or (frequencyIndex shl 2) or (channels shr 2)).toByte(),
            (((channels and 3) shl 6) or (frameLength shr 11)).toByte(),
            ((frameLength shr 3) and 0xff).toByte(),
            (((frameLength and 7) shl 5) or 0x1f).toByte(),
            0xfc.toByte()
        )
    }
}
