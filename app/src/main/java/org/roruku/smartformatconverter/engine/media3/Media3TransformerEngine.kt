package org.roruku.smartformatconverter.engine.media3

import android.content.Context
import android.media.MediaMetadataRetriever
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
import androidx.media3.transformer.EncoderSelector
import androidx.media3.transformer.EncoderUtil
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
import org.roruku.smartformatconverter.engine.media.AdaptiveAudioPlanner
import org.roruku.smartformatconverter.engine.media.AdaptivePresetPlanner
import org.roruku.smartformatconverter.engine.media.VideoSourceProfile
import org.roruku.smartformatconverter.engine.media.BitrateSelection
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
            val canResize = if (advancedSettings.preventUpscale) {
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

        // Source probe drives both adaptive quality and estimated resource demand.
        // A saved manual bitrate continues to override the planner.
        val source = MediaProbe.probeMedia(context, inputUri)
        val effectiveSettings = advancedSettings ?: VideoAdvancedSettings(
            format = config.outputFormat.extension,
            preset = "Smart Auto",
            videoBitrateKbps = config.bitrateKbps ?: BitrateSelection.AUTO,
            audioBitrateKbps = BitrateSelection.AUTO
        )
        val plan = AdaptivePresetPlanner.plan(effectiveSettings,
            VideoSourceProfile(
                width = source.videoWidth, height = source.videoHeight,
                frameRate = source.frameRate,
                videoBitrateKbps = source.videoBitrateKbps,
                audioBitrateKbps = source.audioBitrateKbps,
                audioChannels = source.audioChannels,
                durationMs = source.durationMs,
                sizeBytes = source.sizeBytes,
                hasAudio = source.hasAudio
            )
        )
        Log.d(TAG, "Video export preset=${effectiveSettings.preset} codec=$videoMime " +
            "video=${plan.videoKbps}kbps audio=${plan.audioKbps}kbps " +
            "estimatedBytes=${plan.estimatedBytes}")
        val videoBitrate = plan.videoKbps
        val audioBitrate = plan.audioKbps

        // Preserve source display dimensions unless the user explicitly
        // requests an alternate size or opts in to encoder downscaling.
        val shouldPreserveResolution = !effectiveSettings.allowEncoderDownscale &&
            effectiveSettings.width == null && effectiveSettings.height == null

        executeTransformation(
            context = context,
            editedMediaItem = editedItemBuilder.build(),
            outputFile = tempOutputFile,
            videoMimeType = videoMime,
            audioMimeType = audioMime,
            videoBitrateKbps = videoBitrate,
            audioBitrateKbps = audioBitrate,
            webmOutput = config.outputFormat == VideoOutputFormat.WEBM,
            sourceAudioChannels = source.audioChannels,
            allowEncoderDownscale = effectiveSettings.allowEncoderDownscale,
            expectedDisplaySize = if (shouldPreserveResolution &&
                source.videoWidth > 0 && source.videoHeight > 0
            ) VideoGeometryPolicy.displaySize(
                source.videoWidth, source.videoHeight, source.videoRotation
            ) else null,
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
        // Some vendor MediaMuxer implementations reject otherwise valid Opus
        // packets. Use Media3's dedicated audio muxers as a second route when
        // the platform MediaCodec/MediaMuxer pipeline fails.
        var nativeFailure: Throwable? = null
        val audioChoice = advancedSettings?.bitrateKbps ?: config.bitrateKbps
        val sourceRates = if (audioChoice == BitrateSelection.SOURCE ||
            audioChoice == BitrateSelection.AUTO) {
            MediaProbe.probeMedia(context, inputUri)
        } else null
        val targetAudioBitrate = AdaptiveAudioPlanner.targetKbps(
            selection = audioChoice,
            sourceRateKbps = sourceRates?.audioBitrateKbps ?: 0,
            channels = sourceRates?.audioChannels ?: 2,
            opus = config.outputFormat == AudioOutputFormat.OGG,
            preset = advancedSettings?.preset ?: "Smart Auto"
        )
        if (config.outputFormat in setOf(AudioOutputFormat.M4A, AudioOutputFormat.AAC, AudioOutputFormat.OGG)) {
            val nativeResult = PlatformCompressedAudioExportEngine.export(
                context = context,
                input = inputUri,
                output = tempOutputFile,
                format = config.outputFormat,
                bitrateKbps = targetAudioBitrate,
                onProgress = onProgress
            )
            if (nativeResult.isSuccess) return@withContext nativeResult
            nativeFailure = nativeResult.exceptionOrNull()
            Log.w(TAG, "Platform compressed audio failed; trying Media3 fallback", nativeFailure)
            tempOutputFile.delete()
        }
        val mediaItem = MediaItem.fromUri(inputUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .build()

        val audioBitrate = targetAudioBitrate
        val audioMime = when (config.outputFormat) {
            AudioOutputFormat.OGG -> MimeTypes.AUDIO_OPUS
            else -> MimeTypes.AUDIO_AAC
        }
        val muxerFactory: Muxer.Factory? = when (config.outputFormat) {
            AudioOutputFormat.AAC -> AdtsAacMuxerFactory()
            AudioOutputFormat.OGG -> OggOpusMuxerFactory()
            else -> null
        }
        val fallbackResult = executeTransformation(
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
        if (fallbackResult.isSuccess || nativeFailure == null) return@withContext fallbackResult
        val fallbackFailure = fallbackResult.exceptionOrNull()
        Result.failure(IllegalStateException(
            "Audio export failed using both Android and Media3. Android: " +
                (nativeFailure.message ?: nativeFailure.javaClass.simpleName) +
                "; Media3: " + (fallbackFailure?.message ?: "unknown error"),
            fallbackFailure ?: nativeFailure
        ))
    }

    /**
     * Surface the underlying codec or muxer failure in the queue instead of
     * Media3's unhelpful top-level "Unexpected runtime error" message.
     * Logcat still holds the original exception and full stack trace.
     */
    private fun explainExportFailure(
        exception: ExportException,
        webmOutput: Boolean
    ): IllegalStateException {
        val stage = when (exception.errorCode) {
            ExportException.ERROR_CODE_ENCODER_INIT_FAILED ->
                "Device encoder could not start for the selected resolution/codec"
            ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED ->
                "Requested output codec/profile is not supported by the device"
            ExportException.ERROR_CODE_ENCODING_FAILED ->
                "Device encoder failed while processing media"
            ExportException.ERROR_CODE_DECODER_INIT_FAILED,
            ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                "Device decoder cannot open the source stream"
            ExportException.ERROR_CODE_DECODING_FAILED ->
                "Device decoder failed while reading source frames"
            ExportException.ERROR_CODE_MUXING_FAILED,
            ExportException.ERROR_CODE_MUXING_TIMEOUT ->
                "Output muxer could not write the encoded tracks"
            ExportException.ERROR_CODE_FAILED_RUNTIME_CHECK ->
                "Media3 encountered a failed runtime check (see root cause below)"
            else -> "Media3 could not complete the export"
        }
        // A runtime-check exception may carry no message (for example a null
        // track-language assertion). Preserve the root cause class in that case.
        val causes = generateSequence(exception as Throwable) { it.cause }.take(8).toList()
        val root = causes.last()
        val rootMessage = root.message?.trim()?.takeIf { it.isNotBlank() }
        val explanation = if (root === exception) null else
            (root.javaClass.simpleName + (rootMessage?.let { ": $it" } ?: "")).take(260)
        val container = if (webmOutput) "WebM VP9/Opus" else "Media"
        val detail = explanation?.let { ": " + it } ?: ""
        return IllegalStateException(
            "$container export failed (Media3 code ${exception.errorCode}). $stage$detail",
            exception
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
        sourceAudioChannels: Int = 0,
        allowEncoderDownscale: Boolean = false,
        expectedDisplaySize: VideoGeometryPolicy.Size? = null,
        onProgress: (progress: Float, speed: String?, eta: String?) -> Unit
    ): Result<File> = suspendCancellableCoroutine { continuation ->
        val encoderFactoryBuilder = DefaultEncoderFactory.Builder(context)
            .setEnableFallback(true)
        if (webmOutput) {
            // Media3 EncoderSelector.DEFAULT excludes ALL software encoders if
            // any hardware VP9 encoder is present, even when hardware can only
            // produce 1080x810 from a 1440x1080 source. Include both kinds.
            encoderFactoryBuilder.setVideoEncoderSelector(
                EncoderSelector { mimeType -> EncoderUtil.getSupportedEncoders(mimeType) }
            )
            // Keep bitrate/profile fallback but prohibit resolution fallback
            // unless the user explicitly enabled it in Advanced Settings.
            encoderFactoryBuilder.setEnableFormatFallback(allowEncoderDownscale)
        }

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
        if (webmOutput) transformerBuilder.setMuxerFactory(
            WebmMuxerFactory(sourceAudioChannels))
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
                            val mismatch = expectedDisplaySize?.let { original ->
                                val retriever = MediaMetadataRetriever()
                                try {
                                    retriever.setDataSource(outputFile.absolutePath)
                                    val w = retriever.extractMetadata(
                                        MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                                    )?.toIntOrNull() ?: 0
                                    val h = retriever.extractMetadata(
                                        MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                                    )?.toIntOrNull() ?: 0
                                    val rotation = retriever.extractMetadata(
                                        MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                                    )?.toIntOrNull() ?: 0
                                    VideoGeometryPolicy.unexpectedResize(
                                        original.width, original.height, 0, w, h, rotation
                                    )
                                } catch (error: Exception) {
                                    Log.w(TAG, "Could not independently verify output resolution", error)
                                    null
                                } finally {
                                    retriever.release()
                                }
                            }
                            if (mismatch != null) {
                                outputFile.delete()
                                continuation.resume(Result.failure(IllegalStateException(mismatch)))
                            } else {
                                onProgress(1.0f, "Complete", "00:00")
                                continuation.resume(Result.success(outputFile))
                            }
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
                        continuation.resume(Result.failure(
                            explainExportFailure(exportException, webmOutput)
                        ))
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
