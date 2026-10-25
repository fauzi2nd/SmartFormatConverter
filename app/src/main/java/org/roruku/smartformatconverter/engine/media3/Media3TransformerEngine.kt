package org.roruku.smartformatconverter.engine.media3

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.roruku.smartformatconverter.engine.ffmpeg.AudioAdvancedSettings
import org.roruku.smartformatconverter.engine.ffmpeg.AudioConversionConfig
import org.roruku.smartformatconverter.engine.ffmpeg.AudioOutputFormat
import org.roruku.smartformatconverter.engine.ffmpeg.VideoAdvancedSettings
import org.roruku.smartformatconverter.engine.ffmpeg.VideoConversionConfig
import org.roruku.smartformatconverter.engine.ffmpeg.VideoOutputFormat
import java.io.File
import kotlin.coroutines.resume

@OptIn(UnstableApi::class)
object Media3TransformerEngine {
    private const val TAG = "Media3TransformerEngine"

    suspend fun convertVideo(
        context: Context,
        inputUri: Uri,
        tempOutputFile: File,
        config: VideoConversionConfig,
        advancedSettings: VideoAdvancedSettings? = null,
        onProgress: (progress: Float, speed: String?, eta: String?) -> Unit
    ): Result<File> = withContext(Dispatchers.Main) {
        val mediaItem = MediaItem.fromUri(inputUri)
        val editedItemBuilder = EditedMediaItem.Builder(mediaItem)

        val effects = mutableListOf<Effect>()
        val width = advancedSettings?.width
        val height = advancedSettings?.height
        if (width != null && height != null && width > 0 && height > 0) {
            effects.add(Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_SCALE_TO_FIT))
        }

        if (effects.isNotEmpty()) {
            editedItemBuilder.setEffects(Effects(emptyList(), effects))
        }

        if (advancedSettings?.audioCodec == "none") {
            editedItemBuilder.setRemoveAudio(true)
        }

        val videoMime = when (config.outputFormat) {
            VideoOutputFormat.WEBM -> MimeTypes.VIDEO_VP9
            else -> {
                if (advancedSettings?.videoCodec == "h265" || advancedSettings?.videoCodec == "hevc") {
                    MimeTypes.VIDEO_H265
                } else {
                    MimeTypes.VIDEO_H264
                }
            }
        }

        val audioMime = when (config.outputFormat) {
            VideoOutputFormat.WEBM -> MimeTypes.AUDIO_OPUS
            else -> MimeTypes.AUDIO_AAC
        }

        val videoBitrate = (advancedSettings?.videoBitrateKbps ?: config.bitrateKbps)?.takeIf { it > 0 }
        val audioBitrate = (advancedSettings?.audioBitrateKbps ?: config.audioBitrateKbps).takeIf { it > 0 }

        executeTransformation(
            context = context,
            editedMediaItem = editedItemBuilder.build(),
            outputFile = tempOutputFile,
            videoMimeType = videoMime,
            audioMimeType = audioMime,
            videoBitrateKbps = videoBitrate,
            audioBitrateKbps = audioBitrate,
            onProgress = onProgress
        )
    }

    suspend fun convertAudio(
        context: Context,
        inputUri: Uri,
        tempOutputFile: File,
        config: AudioConversionConfig,
        advancedSettings: AudioAdvancedSettings? = null,
        onProgress: (progress: Float, speed: String?, eta: String?) -> Unit
    ): Result<File> = withContext(Dispatchers.Main) {
        val mediaItem = MediaItem.fromUri(inputUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .build()

        val audioBitrate = (advancedSettings?.bitrateKbps ?: config.bitrateKbps).takeIf { it > 0 }

        executeTransformation(
            context = context,
            editedMediaItem = editedMediaItem,
            outputFile = tempOutputFile,
            videoMimeType = null,
            audioMimeType = MimeTypes.AUDIO_AAC,
            videoBitrateKbps = null,
            audioBitrateKbps = audioBitrate,
            onProgress = onProgress
        )
    }

    private suspend fun executeTransformation(
        context: Context,
        editedMediaItem: EditedMediaItem,
        outputFile: File,
        videoMimeType: String?,
        audioMimeType: String?,
        videoBitrateKbps: Int?,
        audioBitrateKbps: Int?,
        onProgress: (progress: Float, speed: String?, eta: String?) -> Unit
    ): Result<File> = suspendCancellableCoroutine { continuation ->
        val encoderFactoryBuilder = DefaultEncoderFactory.Builder(context)
            .setEnableFallback(true)

        if (videoBitrateKbps != null && videoBitrateKbps > 0) {
            encoderFactoryBuilder.setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(videoBitrateKbps * 1000)
                    .build()
            )
        }

        if (audioBitrateKbps != null && audioBitrateKbps > 0) {
            encoderFactoryBuilder.setRequestedAudioEncoderSettings(
                AudioEncoderSettings.Builder()
                    .setBitrate(audioBitrateKbps * 1000)
                    .build()
            )
        }

        val transformerBuilder = Transformer.Builder(context)
            .setEncoderFactory(encoderFactoryBuilder.build())

        if (videoMimeType != null) {
            transformerBuilder.setVideoMimeType(videoMimeType)
        }
        if (audioMimeType != null) {
            transformerBuilder.setAudioMimeType(audioMimeType)
        }

        var isDone = false
        val progressHolder = ProgressHolder()
        val handler = Handler(Looper.getMainLooper())

        val transformer = transformerBuilder
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    isDone = true
                    onProgress(1.0f, "Complete", "00:00")
                    if (continuation.isActive) {
                        continuation.resume(Result.success(outputFile))
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    isDone = true
                    Log.e(TAG, "Transformation error", exportException)
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(exportException))
                    }
                }
            })
            .build()

        continuation.invokeOnCancellation {
            isDone = true
            try {
                transformer.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Error cancelling transformer", e)
            }
            if (outputFile.exists()) {
                outputFile.delete()
            }
        }

        try {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            transformer.start(editedMediaItem, outputFile.absolutePath)
        } catch (e: Exception) {
            isDone = true
            if (continuation.isActive) {
                continuation.resume(Result.failure(e))
            }
            return@suspendCancellableCoroutine
        }

        // Periodic progress polling runnable on main handler
        val progressRunnable = object : Runnable {
            override fun run() {
                if (!isDone && continuation.isActive) {
                    val progressState = transformer.getProgress(progressHolder)
                    if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val pct = (progressHolder.progress.coerceIn(0, 99)) / 100f
                        onProgress(pct, "Processing…", null)
                    }
                    handler.postDelayed(this, 150)
                }
            }
        }
        handler.postDelayed(progressRunnable, 150)
    }
}
