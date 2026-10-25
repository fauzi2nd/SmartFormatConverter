package org.roruku.smartformatconverter.engine.ffmpeg

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.roruku.smartformatconverter.data.storage.StorageManager
import org.roruku.smartformatconverter.engine.media3.Media3TransformerEngine
import java.io.File

enum class VideoOutputFormat(val extension: String, val mimeType: String) {
    MP4("mp4", "video/mp4"),
    MKV("mkv", "video/x-matroska"),
    WEBM("webm", "video/webm"),
    MOV("mov", "video/quicktime"),
    AVI("avi", "video/x-msvideo")
}

enum class AudioOutputFormat(val extension: String, val mimeType: String) {
    MP3("mp3", "audio/mpeg"),
    AAC("aac", "audio/aac"),
    M4A("m4a", "audio/mp4"),
    WAV("wav", "audio/wav"),
    FLAC("flac", "audio/flac"),
    OGG("ogg", "audio/ogg"),
    OPUS("opus", "audio/opus")
}

enum class VideoCodec(val ffmpegName: String) {
    H264("libx264"),
    H265("libx265"),
    VP9("libvpx-vp9"),
    AV1("libsvtav1"),
    COPY("copy")
}

enum class AudioCodec(val ffmpegName: String) {
    AAC("aac"),
    MP3("libmp3lame"),
    OPUS("libopus"),
    VORBIS("libvorbis"),
    FLAC("flac"),
    PCM_S16LE("pcm_s16le"),
    COPY("copy")
}

enum class VideoPreset(val displayName: String, val crf: Int, val presetSpeed: String) {
    REMUX("Original Quality / Remux", 0, "copy"),
    HIGH_QUALITY("High Quality", 18, "medium"),
    BALANCED("Balanced", 23, "faster"),
    SMALL_FILE("Small File", 28, "veryfast"),
    CUSTOM("Custom", 23, "faster")
}

data class VideoConversionConfig(
    val outputFormat: VideoOutputFormat = VideoOutputFormat.MP4,
    val remuxMode: Boolean = false,
    val preset: VideoPreset = VideoPreset.BALANCED,
    val videoCodec: VideoCodec = VideoCodec.H264,
    val audioCodec: AudioCodec = AudioCodec.AAC,
    val crf: Int = 23,
    val bitrateKbps: Int? = null,
    val resolution: String? = null,
    val frameRate: Int? = null,
    val audioBitrateKbps: Int = 192,
    val audioChannels: Int? = null,
    val audioSampleRate: Int? = null
)

data class AudioConversionConfig(
    val outputFormat: AudioOutputFormat = AudioOutputFormat.MP3,
    val copyStream: Boolean = false,
    val audioCodec: AudioCodec = AudioCodec.MP3,
    val bitrateKbps: Int = 192,
    val sampleRate: Int? = 44100,
    val channels: Int? = 2
)

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
    val hasAudio: Boolean = false
)

object FFmpegEngine {
    private const val TAG = "FFmpegEngine"

    suspend fun probeMedia(context: Context, inputUri: Uri): MediaProbeDetails = withContext(Dispatchers.IO) {
        val details = StorageManager.parseMediaDetails(context, inputUri)
        var vCodec: String? = null
        var aCodec: String? = null
        var hasVideo = false
        var hasAudio = false
        var fr = 0f

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
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        fr = try { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() } catch (e: Exception) { 0f }
                    }
                } else if (mime.startsWith("audio/")) {
                    hasAudio = true
                    aCodec = mime.removePrefix("audio/")
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
            hasAudio = hasAudio
        )
    }

    suspend fun convertVideo(
        context: Context,
        inputUri: Uri,
        tempOutputFile: File,
        config: VideoConversionConfig,
        advancedSettings: VideoAdvancedSettings? = null,
        onProgress: (progress: Float, speed: String?, eta: String?) -> Unit
    ): Result<File> = Media3TransformerEngine.convertVideo(
        context = context,
        inputUri = inputUri,
        tempOutputFile = tempOutputFile,
        config = config,
        advancedSettings = advancedSettings,
        onProgress = onProgress
    )

    suspend fun convertAudio(
        context: Context,
        inputUri: Uri,
        tempOutputFile: File,
        config: AudioConversionConfig,
        advancedSettings: AudioAdvancedSettings? = null,
        onProgress: (progress: Float, speed: String?, eta: String?) -> Unit
    ): Result<File> = Media3TransformerEngine.convertAudio(
        context = context,
        inputUri = inputUri,
        tempOutputFile = tempOutputFile,
        config = config,
        advancedSettings = advancedSettings,
        onProgress = onProgress
    )
}
