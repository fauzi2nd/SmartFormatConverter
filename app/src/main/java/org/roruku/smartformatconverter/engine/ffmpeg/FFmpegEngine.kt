package org.roruku.smartformatconverter.engine.ffmpeg

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import io.microshow.rxffmpeg.RxFFmpegInvoke
import io.microshow.rxffmpeg.RxFFmpegSubscriber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.roruku.smartformatconverter.data.storage.StorageManager
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

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
        onProgress: (progress: Float, speed: String?, eta: String?) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            coroutineContext.ensureActive()

            val tempInputFile = StorageManager.createTempFileInCache(context, "ffmpeg_in", "media")
            try {
                onProgress(0.05f, "Preparing input...", null)
                val copied = StorageManager.copyUriToTempFile(context, inputUri, tempInputFile)
                if (!copied || !tempInputFile.exists()) {
                    return@withContext Result.failure(IllegalStateException("Failed staging input file for FFmpeg processing"))
                }

                coroutineContext.ensureActive()

                val cmdList = mutableListOf("ffmpeg", "-y", "-i", tempInputFile.absolutePath)

                if (config.remuxMode || config.preset == VideoPreset.REMUX) {
                    cmdList.addAll(listOf("-c", "copy"))
                } else {
                    cmdList.addAll(listOf("-c:v", config.videoCodec.ffmpegName))
                    cmdList.addAll(listOf("-preset", config.preset.presetSpeed))
                    if (config.crf > 0) {
                        cmdList.addAll(listOf("-crf", config.crf.toString()))
                    }
                    if (config.bitrateKbps != null) {
                        cmdList.addAll(listOf("-b:v", "${config.bitrateKbps}k"))
                    }
                    if (!config.resolution.isNullOrBlank()) {
                        cmdList.addAll(listOf("-vf", "scale=${config.resolution}"))
                    }
                    if (config.frameRate != null) {
                        cmdList.addAll(listOf("-r", config.frameRate.toString()))
                    }

                    cmdList.addAll(listOf("-c:a", config.audioCodec.ffmpegName))
                    cmdList.addAll(listOf("-b:a", "${config.audioBitrateKbps}k"))
                }

                cmdList.add(tempOutputFile.absolutePath)

                val ffmpegCmdArray = cmdList.toTypedArray()
                Log.d(TAG, "Executing FFmpeg command: ${cmdList.joinToString(" ")}")

                val conversionSuccess = executeFFmpegCommand(ffmpegCmdArray, onProgress)

                if (!conversionSuccess || !tempOutputFile.exists() || tempOutputFile.length() == 0L) {
                    return@withContext Result.failure(IllegalStateException("FFmpeg processing failed or produced empty file"))
                }

                onProgress(1.0f, "Complete", "00:00")
                Result.success(tempOutputFile)
            } finally {
                if (tempInputFile.exists()) {
                    tempInputFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Video conversion failed", e)
            Result.failure(e)
        }
    }

    suspend fun convertAudio(
        context: Context,
        inputUri: Uri,
        tempOutputFile: File,
        config: AudioConversionConfig,
        onProgress: (progress: Float, speed: String?, eta: String?) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            coroutineContext.ensureActive()
            val probe = probeMedia(context, inputUri)

            val tempInputFile = StorageManager.createTempFileInCache(context, "ffmpeg_audio_in", "media")
            try {
                onProgress(0.05f, "Preparing audio...", null)
                val copied = StorageManager.copyUriToTempFile(context, inputUri, tempInputFile)
                if (!copied || !tempInputFile.exists()) {
                    return@withContext Result.failure(IllegalStateException("Failed staging input audio file"))
                }

                coroutineContext.ensureActive()

                val cmdList = mutableListOf("ffmpeg", "-y", "-i", tempInputFile.absolutePath, "-vn")

                if (config.copyStream) {
                    cmdList.addAll(listOf("-c:a", "copy"))
                } else {
                    cmdList.addAll(listOf("-c:a", config.audioCodec.ffmpegName))
                    cmdList.addAll(listOf("-b:a", "${config.bitrateKbps}k"))
                    if (config.sampleRate != null) {
                        cmdList.addAll(listOf("-ar", config.sampleRate.toString()))
                    }
                    if (config.channels != null) {
                        cmdList.addAll(listOf("-ac", config.channels.toString()))
                    }
                }

                cmdList.add(tempOutputFile.absolutePath)

                val ffmpegCmdArray = cmdList.toTypedArray()
                Log.d(TAG, "Executing FFmpeg Audio command: ${cmdList.joinToString(" ")}")

                val conversionSuccess = executeFFmpegCommand(ffmpegCmdArray, onProgress)

                if (!conversionSuccess || !tempOutputFile.exists() || tempOutputFile.length() == 0L) {
                    return@withContext Result.failure(IllegalStateException("FFmpeg audio conversion failed or produced empty file"))
                }

                onProgress(1.0f, "Complete", "00:00")
                Result.success(tempOutputFile)
            } finally {
                if (tempInputFile.exists()) {
                    tempInputFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio conversion failed", e)
            Result.failure(e)
        }
    }

    private suspend fun executeFFmpegCommand(
        commandArray: Array<String>,
        onProgress: (progress: Float, speed: String?, eta: String?) -> Unit
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val subscriber = object : RxFFmpegSubscriber() {
            override fun onFinish() {
                if (continuation.isActive) {
                    continuation.resume(value = true)
                }
            }

            override fun onProgress(progress: Int, progressTime: Long) {
                val pct = (progress.toFloat() / 100f).coerceIn(0f, 0.99f)
                val speedStr = String.format(Locale.US, "%.1fx", if (progressTime > 0) (progressTime.toDouble() / 1000.0) else 1.0)
                onProgress(pct, speedStr, null)
            }

            override fun onCancel() {
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }

            override fun onError(message: String?) {
                Log.e(TAG, "FFmpeg error: $message")
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
        }

        continuation.invokeOnCancellation {
            try {
                RxFFmpegInvoke.getInstance().exit()
                subscriber.dispose()
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling FFmpeg command", e)
            }
        }

        try {
            RxFFmpegInvoke.getInstance().runCommand(commandArray, subscriber)
        } catch (e: Exception) {
            Log.e(TAG, "Failed launching FFmpeg native command", e)
            if (continuation.isActive) {
                continuation.resume(false)
            }
        }
    }
}
