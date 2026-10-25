package org.roruku.smartformatconverter.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import org.roruku.smartformatconverter.data.db.AppDatabase
import org.roruku.smartformatconverter.data.db.ConversionJobDao
import org.roruku.smartformatconverter.data.db.ConversionJobEntity
import org.roruku.smartformatconverter.data.db.JobStatus
import org.roruku.smartformatconverter.data.db.MediaType
import org.roruku.smartformatconverter.data.preferences.UserPreferencesRepository
import org.roruku.smartformatconverter.data.storage.StorageManager
import org.roruku.smartformatconverter.data.storage.OutputPathPolicy
import org.roruku.smartformatconverter.engine.ffmpeg.VideoOutputFormat
import org.roruku.smartformatconverter.engine.ffmpeg.VideoPreset
import org.roruku.smartformatconverter.engine.ffmpeg.VideoCodec
import org.roruku.smartformatconverter.engine.ffmpeg.AudioOutputFormat
import org.roruku.smartformatconverter.engine.ffmpeg.AudioCodec
import org.roruku.smartformatconverter.engine.downloader.HttpDownloaderEngine
import org.roruku.smartformatconverter.engine.downloader.HttpMediaFormatOption
import org.roruku.smartformatconverter.engine.downloader.HttpDownloadableMediaInfo
import org.roruku.smartformatconverter.engine.downloader.DirectDownloadSnapshot
import org.roruku.smartformatconverter.engine.downloader.DirectMediaPolicy
import org.roruku.smartformatconverter.engine.ffmpeg.AudioConversionConfig
import org.roruku.smartformatconverter.engine.media3.Media3TransformerEngine
import org.roruku.smartformatconverter.engine.ffmpeg.VideoConversionConfig
import org.roruku.smartformatconverter.engine.ffmpeg.VideoAdvancedSettings
import org.roruku.smartformatconverter.engine.ffmpeg.VideoSettingsValidator
import org.roruku.smartformatconverter.engine.image.ImageConversionConfig
import org.roruku.smartformatconverter.engine.image.ImageConverterEngine
import org.roruku.smartformatconverter.engine.image.ImageOutputFormat
import org.roruku.smartformatconverter.engine.image.ChromaSubsampling
import org.roruku.smartformatconverter.engine.ffmpeg.ImageAdvancedSettings
import org.roruku.smartformatconverter.engine.ffmpeg.ImageSettingsValidator
import org.roruku.smartformatconverter.engine.ffmpeg.AudioAdvancedSettings
import org.roruku.smartformatconverter.engine.ffmpeg.AudioSettingsValidator
import java.io.File

class ConversionRepository(
    private val context: Context,
    private val dao: ConversionJobDao = AppDatabase.getInstance(context).conversionJobDao(),
    private val userPrefs: UserPreferencesRepository = UserPreferencesRepository(context),
) {
    private companion object {
        const val TAG = "ConversionRepository"
    }

    val allJobsFlow: Flow<List<ConversionJobEntity>> = dao.getAllJobsFlow()
    val completedCountFlow: Flow<Int> = dao.getCompletedJobCountFlow()
    val failedCountFlow: Flow<Int> = dao.getFailedJobCountFlow()
    val totalBytesConvertedFlow: Flow<Long> = dao.getTotalBytesConvertedFlow()

    suspend fun addJob(job: ConversionJobEntity) {
        dao.insertJob(job)
    }

    suspend fun addJobs(jobs: List<ConversionJobEntity>) {
        dao.insertJobs(jobs)
    }

    suspend fun cancelJob(jobId: String) {
        // Persist CANCELLED before stopping the worker so a late progress
        // callback cannot put a cancelled job back into RUNNING.
        dao.cancelJob(jobId)
        ActiveConversionJobs.cancel(jobId)
    }

    /**
     * Cooperative pause: active FFmpeg/image/audio work is cancelled and
     * restarts from zero on Resume. Direct HTTP downloads preserve validated
     * cache bytes so Range/If-Range can continue on a compatible server.
     */
    suspend fun pauseJob(jobId: String): Boolean {
        val changed = dao.pauseJob(jobId) > 0
        if (changed) ActiveConversionJobs.cancel(jobId)
        return changed
    }

    suspend fun resumeJob(jobId: String): Boolean = dao.resumeJob(jobId) > 0

    suspend fun retryJob(jobId: String) {
        val job = dao.getJobById(jobId) ?: return
        val retriedJob = job.copy(
            status = JobStatus.PENDING,
            progress = 0f,
            errorMessage = null,
            speed = null,
            eta = null,
            outputUri = null,
            outputFileSize = 0L,
            completedAt = null,
            retryCount = job.retryCount + 1,
            executionToken = job.executionToken + 1
        )
        dao.updateJob(retriedJob)
    }

    suspend fun deleteJob(job: ConversionJobEntity) {
        ActiveConversionJobs.prune(job.id)
        dao.deleteJob(job)
        if (job.mediaType == MediaType.DOWNLOAD) {
            StorageManager.downloadCacheFile(context, job.id, job.outputFormat).delete()
        }
    }

    suspend fun clearCompletedJobs() {
        dao.clearCompletedJobs()
    }

    suspend fun clearAllJobs() {
        ActiveConversionJobs.cancelAll()
        ActiveConversionJobs.clear()
        dao.clearAllJobs()
        File(context.cacheDir, "temp_conversion").listFiles()
            ?.filter { it.name.startsWith("download_") }?.forEach { it.delete() }
    }

    suspend fun executeJob(job: ConversionJobEntity, scope: CoroutineScope): Result<String> = withContext(Dispatchers.IO) {
        // Track this conversion coroutine with a monotonic generation token.
        val jobCoro = currentCoroutineContext()[Job]
        val generation = if (jobCoro != null) {
            ActiveConversionJobs.beginExecution(job.id, jobCoro)
        } else 0L

        if (generation > 0L) {
            val bound = dao.bindExecutionToken(job.id, generation)
            if (bound <= 0) {
                throw CancellationException("Job state changed before execution began")
            }
        }

        val estimator = ProgressEstimator()
        // One writer protects the database from out-of-order progress callbacks
        // produced by RxFFmpeg and image encoders.
        val updates = Channel<Triple<Float, String, String>>(Channel.CONFLATED)
        val progressWriter = scope.launch(Dispatchers.IO) {
            for ((fraction, speed, eta) in updates) {
                if (generation == 0L || ActiveConversionJobs.isCurrent(job.id, generation)) {
                    dao.updateProgress(job.id, JobStatus.RUNNING, fraction, speed, eta, generation)
                }
            }
        }
        fun emitProgress(progress: Float, reportedSpeed: String? = null, reportedEta: String? = null) {
            if (generation > 0L && !ActiveConversionJobs.isCurrent(job.id, generation)) {
                return
            }
            val reading = estimator.measure(progress)
            updates.trySend(Triple(
                progress.coerceIn(0f, 0.99f),
                reportedSpeed?.takeIf { it.isNotBlank() } ?: reading.speed,
                reportedEta?.takeIf { it.isNotBlank() } ?: reading.eta
            ))
        }

        try {
            dao.updateProgress(job.id, JobStatus.RUNNING, 0.02f, "Measuring…", "Measuring…", generation)
            val settings = userPrefs.userSettingsFlow.first()
            val outputRootUri = settings.defaultOutputUri
                ?.takeIf { it.isNotBlank() }
                ?.let(Uri::parse)
                ?: throw IllegalStateException(
                    "Select a parent output folder in Settings before converting."
                )

            val effectiveExtension = when (job.mediaType) {
                MediaType.VIDEO -> "mp4"
                MediaType.AUDIO -> "m4a"
                MediaType.IMAGE -> when (job.outputFormat.lowercase()) {
                    "png" -> "png"
                    "webp" -> "webp"
                    else -> "jpg"
                }
                MediaType.DOWNLOAD -> job.outputFormat.lowercase()
            }

            val tempOutFile = if (job.mediaType == MediaType.DOWNLOAD) {
                StorageManager.downloadCacheFile(context, job.id, job.outputFormat)
            } else {
                StorageManager.createTempFileInCache(
                    context, "out_${job.id}", effectiveExtension
                )
            }

            val conversionResult = when (job.mediaType) {
                MediaType.IMAGE -> {
                    val format = when (job.outputFormat.lowercase()) {
                        "jpg", "jpeg" -> ImageOutputFormat.JPEG
                        "png" -> ImageOutputFormat.PNG
                        else -> ImageOutputFormat.WEBP
                    }
                    val advanced = job.customParamsJson?.takeIf { it.isNotBlank() }
                        ?.let(ImageAdvancedSettings::fromJson)
                    if (advanced != null) {
                        require(advanced.format == job.outputFormat.lowercase()) {
                            "Queued image format and advanced settings disagree"
                        }
                        ImageSettingsValidator.validate(advanced)?.let {
                            throw IllegalArgumentException(it)
                        }
                    }
                    val config = ImageConversionConfig(
                        outputFormat = format,
                        jpegQuality = advanced?.jpegQuality ?: 100,
                        chromaSubsampling = when (advanced?.jpegSubsampling) {
                            "422" -> ChromaSubsampling.S422
                            "420" -> ChromaSubsampling.S420
                            else -> ChromaSubsampling.S444
                        },
                        webpQuality = advanced?.webpQuality ?: 95,
                        webpLossless = advanced?.webpLossless ?: false,
                        customWidth = advanced?.width,
                        customHeight = advanced?.height,
                        maintainAspectRatio = advanced?.keepAspectRatio ?: true,
                        preventUpscale = advanced?.preventUpscale ?: true,
                        cropToExactRatio = advanced?.cropToExactRatio ?: false,
                        preserveExif = advanced?.preserveMetadata ?: settings.preserveMetadata
                    )
                    ImageConverterEngine.convertImage(
                        context = context,
                        inputUri = job.inputUri.toUri(),
                        outputFile = tempOutFile,
                        config = config,
                        onProgress = { fraction ->
                            emitProgress(0.05f + fraction.coerceIn(0f, 1f) * 0.84f)
                        }
                    )
                }
                MediaType.VIDEO -> {
                    val format = VideoOutputFormat.entries.firstOrNull {
                        it.extension.equals(job.outputFormat, ignoreCase = true)
                    } ?: VideoOutputFormat.MP4
                    val preset = VideoPreset.entries.firstOrNull {
                        it.displayName.equals(job.presetName, ignoreCase = true)
                    } ?: VideoPreset.BALANCED
                    val config = VideoConversionConfig(
                        outputFormat = format,
                        preset = preset,
                        remuxMode = preset == VideoPreset.REMUX,
                        videoCodec = if (format == VideoOutputFormat.WEBM) VideoCodec.VP9 else VideoCodec.H264,
                        audioCodec = when (format) {
                            VideoOutputFormat.WEBM -> AudioCodec.OPUS
                            VideoOutputFormat.AVI -> AudioCodec.MP3
                            else -> AudioCodec.AAC
                        },
                        crf = preset.crf
                    )
                    // New jobs hold an immutable per-job JSON snapshot. Old jobs
                    // without that field continue down FFmpegEngine's legacy path.
                    val advanced = job.customParamsJson
                        ?.takeIf { it.isNotBlank() }
                        ?.let(VideoAdvancedSettings::fromJson)
                    if (advanced != null) {
                        require(advanced.format == job.outputFormat.lowercase()) {
                            "Queued video format and advanced settings disagree"
                        }
                        VideoSettingsValidator.validate(advanced)?.let {
                            throw IllegalArgumentException(it)
                        }
                        if (advanced.encoder == "hardware" && !settings.hardwareAcceleration) {
                            throw IllegalArgumentException(
                                "Hardware encoder is disabled in Settings. Enable it or select Software."
                            )
                        }
                    }
                    Media3TransformerEngine.convertVideo(
                        context = context,
                        inputUri = job.inputUri.toUri(),
                        tempOutputFile = tempOutFile,
                        config = config,
                        advancedSettings = advanced,
                        onProgress = { pct, _, _ ->
                            emitProgress(0.05f + pct.coerceIn(0f, 1f) * 0.84f)
                        }
                    )
                }
                MediaType.AUDIO -> {
                    val format = AudioOutputFormat.entries.firstOrNull {
                        it.extension.equals(job.outputFormat, ignoreCase = true)
                    } ?: AudioOutputFormat.MP3
                    val codec = when (format) {
                        AudioOutputFormat.MP3 -> AudioCodec.MP3
                        AudioOutputFormat.AAC, AudioOutputFormat.M4A -> AudioCodec.AAC
                        AudioOutputFormat.OGG -> AudioCodec.VORBIS
                        AudioOutputFormat.OPUS -> AudioCodec.OPUS
                        AudioOutputFormat.FLAC -> AudioCodec.FLAC
                        AudioOutputFormat.WAV -> AudioCodec.PCM_S16LE
                    }
                    val config = AudioConversionConfig(
                        outputFormat = format,
                        audioCodec = codec,
                        sampleRate = when (format) {
                            AudioOutputFormat.OPUS -> 48000
                            AudioOutputFormat.WAV,
                            AudioOutputFormat.FLAC,
                            AudioOutputFormat.OGG -> null
                            else -> 44100
                        }
                    )
                    val advancedAudio = job.customParamsJson?.takeIf { it.isNotBlank() }
                        ?.let(AudioAdvancedSettings::fromJson)
                    if (advancedAudio != null) {
                        require(advancedAudio.format == job.outputFormat.lowercase()) {
                            "Queued audio format and advanced settings disagree"
                        }
                        AudioSettingsValidator.validate(advancedAudio)?.let {
                            throw IllegalArgumentException(it)
                        }
                    }
                    Media3TransformerEngine.convertAudio(
                        context = context,
                        inputUri = job.inputUri.toUri(),
                        tempOutputFile = tempOutFile,
                        config = config,
                        advancedSettings = advancedAudio,
                        onProgress = { pct, _, _ ->
                            emitProgress(0.05f + pct.coerceIn(0f, 1f) * 0.84f)
                        }
                    )
                }
                MediaType.DOWNLOAD -> {
                    // New jobs persist the inspected file type and ETag/Last-Modified
                    // so retry can resume without a second HEAD request.
                    val snapshot = job.customParamsJson?.takeIf { it.isNotBlank() }
                        ?.let(DirectDownloadSnapshot::fromJson)
                    val infoResult = if (snapshot == null) {
                        // Compatibility with jobs queued by previous APK versions.
                        HttpDownloaderEngine.extractMediaInfo(context, job.inputUri)
                    } else {
                        Result.success(
                            HttpDownloadableMediaInfo(
                                url = job.inputUri,
                                title = job.fileName,
                                availableFormats = listOf(
                                    HttpMediaFormatOption(
                                        formatId = "direct_${snapshot.extension}",
                                        extension = snapshot.extension,
                                        resolution = "Source file",
                                        note = "Direct download (no transcoding)"
                                    )
                                ),
                                etag = snapshot.etag,
                                lastModified = snapshot.lastModified
                            )
                        )
                    }
                    if (infoResult.isFailure) {
                        Result.failure(infoResult.exceptionOrNull()
                            ?: Exception("Failed inspecting direct HTTP media"))
                    } else {
                        val info = infoResult.getOrThrow()
                        val fmt = info.availableFormats.singleOrNull()
                        if (fmt == null ||
                            !fmt.extension.equals(job.outputFormat, ignoreCase = true)) {
                            Result.failure(IllegalStateException(
                                "Queued format does not match the direct file. Inspect and queue it again."
                            ))
                        } else {
                            HttpDownloaderEngine.downloadMedia(
                                context = context,
                                mediaInfo = info,
                                formatOption = fmt,
                                tempOutputFile = tempOutFile,
                                onProgress = { pct, speed, eta ->
                                    emitProgress(
                                        0.05f + pct.coerceIn(0f, 1f) * 0.84f,
                                        reportedSpeed = speed, reportedEta = eta
                                    )
                                }
                            )
                        }
                    }
                }
            }

            if (conversionResult.isFailure) {
                val err = conversionResult.exceptionOrNull()?.message ?: "Unknown conversion error"
                dao.markFailed(job.id, err, executionToken = generation)
                return@withContext Result.failure(Exception(err))
            }

            val finalFile = conversionResult.getOrThrow()
            val outputSize = finalFile.length()

            // Always save below <selected-parent>/Converter/.
            // +Files => Converter/<name>.<ext>
            // Folder => Converter/<relative-batch-path>/<name>.<ext>
            val outputDirectory = OutputPathPolicy.outputDirectory(job.relativeFolderPath)
            val outputName = OutputPathPolicy.outputFileName(
                originalName = job.fileName,
                extension = effectiveExtension,
                format = settings.filenameFormat,
                customPrefix = settings.customPrefix
            )
            val mimeType = if (job.mediaType == MediaType.DOWNLOAD) {
                DirectMediaPolicy.mimeTypeForExtension(job.outputFormat)
            } else StorageManager.getMimeType(context, Uri.fromFile(finalFile))
            var destination: DocumentFile? = null
            var copySuccess = false
            try {
                destination = StorageManager.createDestinationDocumentFile(
                    context = context,
                    treeUri = outputRootUri,
                    relativeSubfolder = outputDirectory,
                    fileName = outputName,
                    mimeType = mimeType
                ) ?: throw IllegalStateException(
                    "Could not create $outputDirectory/$outputName. Check folder permissions."
                )

                emitProgress(0.90f)
                val ok = StorageManager.copyFileToDocumentUri(context, finalFile, destination) { copied ->
                    emitProgress(0.90f + 0.09f * copied)
                }
                if (!ok) {
                    throw IllegalStateException("Failed writing $outputDirectory/$outputName")
                }
                copySuccess = true
            } finally {
                if (!copySuccess && destination != null) {
                    withContext(NonCancellable) {
                        try {
                            destination.delete()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed deleting partial destination document: ${destination.uri}", e)
                        }
                    }
                }
            }

            if (generation > 0L && !ActiveConversionJobs.isCurrent(job.id, generation)) {
                withContext(NonCancellable) {
                    try {
                        destination.delete()
                    } catch (_: Exception) {}
                }
                throw CancellationException("Job was cancelled or superseded before completion")
            }

            val outputUri = destination.uri.toString()
            val rowsUpdated = dao.markCompleted(
                jobId = job.id,
                outputUri = outputUri,
                outputFileSize = outputSize,
                executionToken = generation
            )
            if (rowsUpdated <= 0) {
                withContext(NonCancellable) {
                    try {
                        destination.delete()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed deleting unconfirmed destination document: ${destination.uri}", e)
                    }
                }
                throw CancellationException("Job completion was rejected because state changed to PAUSED/CANCELLED")
            }
            finalFile.delete()
            Result.success(outputUri)
        } catch (e: CancellationException) {
            // Cooperative cancellation from pauseJob or cancelJob.
            // Do not mark the job as failed; its terminal/paused state was already persisted by the caller.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error executing job ${job.id}", e)
            if (generation == 0L || ActiveConversionJobs.isCurrent(job.id, generation)) {
                dao.markFailed(job.id, e.message ?: "Job failed with exception", executionToken = generation)
            }
            Result.failure(e)
        } finally {
            updates.close()
            withContext(NonCancellable) { progressWriter.join() }
            if (generation > 0L) {
                ActiveConversionJobs.unregister(job.id, generation, jobCoro)
            }
        }
    }
}
