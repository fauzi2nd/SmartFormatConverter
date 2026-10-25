package org.roruku.smartformatconverter.engine.media3

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * True MP4 stream copy, not a low-quality re-encode disguised as "lossless".
 * Copies exactly one H.264/HEVC video track and at most one AAC audio track.
 * All other source tracks, encrypted samples and incompatible codecs are
 * rejected explicitly to avoid silent data loss.
 */
internal object Mp4RemuxEngine {
    internal fun supportedTracks(mimes: List<String>): Boolean =
        mimes.count { it.startsWith("video/") } == 1 &&
            mimes.filter { it.startsWith("video/") }.single() in setOf("video/avc", "video/hevc") &&
            mimes.count { it.startsWith("audio/") } <= 1 &&
            mimes.filter { it.startsWith("audio/") }.all { it == "audio/mp4a-latm" } &&
            mimes.all { it.startsWith("video/") || it.startsWith("audio/") }

    suspend fun remux(
        context: Context,
        input: Uri,
        output: File,
        onProgress: (Float, String?, String?) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var finished = false
        try {
            extractor.setDataSource(context, input, null)
            val formats = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
            val mimes = formats.map { it.getString(MediaFormat.KEY_MIME).orEmpty() }
            require(supportedTracks(mimes)) {
                "MP4 remux supports one H.264/H.265 video and up to one AAC audio; " +
                    "extra tracks/subtitles are not discarded silently"
            }
            val videoIdx = mimes.indexOfFirst { it.startsWith("video/") }
            val videoFormat = formats[videoIdx]
            val orientation = if (videoFormat.containsKey(MediaFormat.KEY_ROTATION))
                videoFormat.getInteger(MediaFormat.KEY_ROTATION) else 0

            val totalDuration = formats.mapNotNull {
                if (it.containsKey(MediaFormat.KEY_DURATION)) it.getLong(MediaFormat.KEY_DURATION) else null
            }.maxOrNull()?.coerceAtLeast(1L) ?: 0L
            val maxInputSize = formats.mapNotNull {
                if (it.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) it.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else null
            }.maxOrNull() ?: (8 * 1024 * 1024)
            require(maxInputSize in 1..(32 * 1024 * 1024)) {
                "Source sample size exceeds safe MP4 remux buffer limit (32 MiB)"
            }
            val buffer = ByteBuffer.allocateDirect(maxOf(8 * 1024 * 1024, maxInputSize))
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (orientation in setOf(90, 180, 270)) muxer.setOrientationHint(orientation)
            val trackMap = IntArray(formats.size) { -1 }
            formats.forEachIndexed { idx, fmt ->
                trackMap[idx] = muxer.addTrack(fmt)
                extractor.selectTrack(idx)
            }
            muxer.start()
            val info = MediaCodec.BufferInfo()
            var samples = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val sourceTrack = extractor.sampleTrackIndex
                if (sourceTrack < 0) break
                require(trackMap[sourceTrack] >= 0) { "Unexpected source track" }
                val flags = extractor.sampleFlags
                require(flags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) {
                    "DRM/encrypted stream copy is not supported"
                }
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                require(size <= buffer.capacity()) { "Remux sample exceeded buffer" }
                info.set(0, size, extractor.sampleTime.coerceAtLeast(0), flags)
                buffer.position(0)
                buffer.limit(size)
                muxer.writeSampleData(trackMap[sourceTrack], buffer, info)
                samples += 1
                if (totalDuration > 0 && samples % 16L == 0L) {
                    onProgress((info.presentationTimeUs.toDouble() / totalDuration).toFloat().coerceIn(0f, 0.99f), null, null)
                }
                extractor.advance()
            }
            require(samples > 0) { "No compressed samples to remux" }
            muxer.stop()
            finished = true
            require(output.isFile && output.length() > 0L) { "Remux produced an empty file" }
            onProgress(1f, null, null)
            Result.success(output)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            extractor.release()
            if (!finished) output.delete()
        }
    }
}
