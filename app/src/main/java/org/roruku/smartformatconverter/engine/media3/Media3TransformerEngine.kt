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
import androidx.media3.muxer.Muxer
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
import org.roruku.smartformatconverter.engine.media.AudioAdvancedSettings
import org.roruku.smartformatconverter.engine.media.VideoAdvancedSettings
import org.roruku.smartformatconverter.data.db.MediaType
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
        Media3DeviceCapabilities.unsupportedReason(MediaType.VIDEO, config.outputFormat.extension)?.let {
            return@withContext Result.failure(IllegalStateException(it))
        }
        val mediaItem = MediaItem.fromUri(inputUri)
        val editedItemBuilder = EditedMediaItem.Builder(mediaItem)

        val effects = mutableListOf<Effect>()
        val width = advancedSettings?.width
        val height = advancedSettings?.height
        if (width != null && height != null && width > 0 && height > 0) {
            // Do not upscale a smaller source when the user enabled "Prevent upscale".
            // If the source dimensions cannot be read, fail rather than silently
            // violating an explicit quality requirement.
            val canResize = if (advancedSettings?.preventUpscale == true) {
                val input = MediaProbe.probeMedia(context, inputUri)
                if (input.videoWidth <= 0 || input.videoHeight <= 0) {
                    return@withContext Result.failure(
                        IllegalStateException("Cannot determine source resolution for prevent-upscale. Try original resolution.")
                    )
                }
                width <= input.videoWidth && height <= input.videoHeight
            } else true
            if (canResize) {
                effects.add(Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_SCALE_TO_FIT))
            }
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
            webmOutput = config.outputFormat == VideoOutputFormat.WEBM,
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
        Media3DeviceCapabilities.unsupportedReason(MediaType.AUDIO, config.outputFormat.extension)?.let {
            return@withContext Result.failure(IllegalStateException(it))
        }
        if (config.outputFormat == AudioOutputFormat.WAV) {
            return@withContext PcmWavExportEngine.export(context, inputUri, tempOutputFile, onProgress)
        }
        if (config.outputFormat == AudioOutputFormat.FLAC) {
            return@withContext PlatformFlacExportEngine.export(context, inputUri, tempOutputFile, onProgress)
        }
        val mediaItem = MediaItem.fromUri(inputUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .build()

        val audioBitrate = (advancedSettings?.bitrateKbps ?: config.bitrateKbps).takeIf { it > 0 }
        val audioMime = when (config.outputFormat) {
            AudioOutputFormat.OGG -> MimeTypes.AUDIO_OPUS
            else -> MimeTypes.AUDIO_AAC
        }
        val muxerFactory: Muxer.Factory? = when (config.outputFormat) {
            AudioOutputFormat.AAC -> AdtsAacMuxerFactory()
            AudioOutputFormat.OGG -> OggOpusMuxerFactory()
            else -> null
        }
        executeTransformation(
            context = context,
            editedMediaItem = editedMediaItem,
            outputFile = tempOutputFile,
            videoMimeType = null,
            audioMimeType = audioMime,
            videoBitrateKbps = null,
            audioBitrateKbps = audioBitrate,
            muxerFactory = muxerFactory,
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
        webmOutput: Boolean = false,
        muxerFactory: Muxer.Factory? = null,
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
        if (webmOutput) transformerBuilder.setMuxerFactory(WebmMuxerFactory())
        if (muxerFactory != null) transformerBuilder.setMuxerFactory(muxerFactory)

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
                    if (continuation.isActive) {
                        if (outputFile.isFile && outputFile.length() > 0L) {
                            onProgress(1.0f, "Complete", "00:00")
                            continuation.resume(Result.success(outputFile))
                        } else {
                            continuation.resume(Result.failure(IllegalStateException("Media3 produced an empty output file")))
                        }
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    isDone = true
                    Log.e(TAG, "Transformation error", exportException)
                    outputFile.delete()
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(exportException))
                    }
                }
            })
            .build()

        continuation.invokeOnCancellation {
            // Cancellation may arrive from the IO worker thread. Media3
            // Transformer must be controlled on the application/main Looper.
            handler.post {
                isDone = true
                try {
                    transformer.cancel()
                } catch (e: Exception) {
                    Log.w(TAG, "Error cancelling transformer", e)
                } finally {
                    outputFile.delete()
                }
            }
        }

        try {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            transformer.start(editedMediaItem, outputFile.absolutePath)
        } catch (e: Exception) {
            isDone = true
            outputFile.delete()
            if (continuation.isActive) {
                continuation.resume(Result.failure(e))
            }
            return@suspendCancellableCoroutine
        }

        // Periodic progress polling runnable on main handler
        val progressRunnable = object : Runnable {
            override fun run() {
                if (!isDone && continuation.isActive) {
                    try {
                        val progressState = transformer.getProgress(progressHolder)
                        if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                            val pct = (progressHolder.progress.coerceIn(0, 99)) / 100f
                            onProgress(pct, "Processing…", null)
                        }
                    } catch (error: Exception) {
                        // Progress is best effort. A transient polling failure must
                        // not crash the main Looper or interrupt a valid export.
                        Log.w(TAG, "Media3 progress polling unavailable", error)
                    }
                    if (!isDone && continuation.isActive) handler.postDelayed(this, 150)
                }
            }
        }
        handler.postDelayed(progressRunnable, 150)
    }
}
