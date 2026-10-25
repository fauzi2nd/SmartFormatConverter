package org.roruku.smartformatconverter.engine.media3

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.roruku.smartformatconverter.data.storage.StorageManager

data class MediaProbeDetails(
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val bitrate: Int = 0,
    val videoCodec: String? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val frameRate: Float = 0f,
    val audioCodec: String? = null,
    val audioChannels: Int = 0,
    val audioSampleRate: Int = 0,
    val hasVideo: Boolean = false,
    val hasAudio: Boolean = false,
    val videoBitrateKbps: Int = 0,
    val audioBitrateKbps: Int = 0
)

object MediaProbe {
    private const val TAG = "MediaProbe"
    suspend fun probeMedia(context: Context, inputUri: Uri): MediaProbeDetails = withContext(Dispatchers.IO) {
        val details = StorageManager.parseMediaDetails(context, inputUri)
        var vCodec: String? = null
        var aCodec: String? = null
        var hasVideo = false
        var hasAudio = false
        var fr = 0f
        var videoRateKbps = 0
        var audioRateKbps = 0

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, inputUri, null)
            val trackCount = extractor.trackCount
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    hasVideo = true
                    vCodec = mime.removePrefix("video/")
                    if (videoRateKbps == 0) videoRateKbps = trackBitrateKbps(format)
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        fr = try { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() } catch (e: Exception) { 0f }
                    }
                } else if (mime.startsWith("audio/")) {
                    hasAudio = true
                    aCodec = mime.removePrefix("audio/")
                    if (audioRateKbps == 0) audioRateKbps = trackBitrateKbps(format)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaExtractor failed for probe on $inputUri", e)
        } finally {
            try { extractor.release() } catch (e: Exception) { /* ignore */ }
        }

        MediaProbeDetails(
            durationMs = details.durationMs,
            sizeBytes = details.fileSize,
            bitrate = details.bitrate,
            videoCodec = vCodec ?: details.mimeType,
            videoWidth = details.width,
            videoHeight = details.height,
            frameRate = fr,
            audioCodec = aCodec,
            audioChannels = details.audioChannels,
            audioSampleRate = details.sampleRate,
            hasVideo = hasVideo || details.width > 0,
            hasAudio = hasAudio,
            videoBitrateKbps = videoRateKbps,
            audioBitrateKbps = audioRateKbps
        )
    }

    private fun trackBitrateKbps(format: MediaFormat): Int = try {
        if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
            (format.getInteger(MediaFormat.KEY_BIT_RATE).toLong() / 1000L)
                .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        } else 0
    } catch (_: Exception) { 0 }
}
