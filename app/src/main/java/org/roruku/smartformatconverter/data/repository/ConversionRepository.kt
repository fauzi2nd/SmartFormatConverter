package org.roruku.smartformatconverter.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.roruku.smartformatconverter.data.db.AppDatabase
import org.roruku.smartformatconverter.data.db.ConversionJobDao
import org.roruku.smartformatconverter.data.db.ConversionJobEntity
import org.roruku.smartformatconverter.data.db.JobStatus
import org.roruku.smartformatconverter.data.db.MediaType
import org.roruku.smartformatconverter.data.preferences.UserPreferencesRepository
import org.roruku.smartformatconverter.data.storage.StorageManager
import org.roruku.smartformatconverter.engine.downloader.HttpDownloaderEngine
import org.roruku.smartformatconverter.engine.downloader.HttpMediaFormatOption
import org.roruku.smartformatconverter.engine.ffmpeg.AudioConversionConfig
import org.roruku.smartformatconverter.engine.ffmpeg.FFmpegEngine
import org.roruku.smartformatconverter.engine.ffmpeg.VideoConversionConfig
import org.roruku.smartformatconverter.engine.image.ImageConversionConfig
import org.roruku.smartformatconverter.engine.image.ImageConverterEngine
import org.roruku.smartformatconverter.engine.image.ImageOutputFormat
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ConversionRepository(
    private val context: Context,
    private val dao: ConversionJobDao = AppDatabase.getInstance(context).conversionJobDao(),
    private val userPrefs: UserPreferencesRepository = UserPreferencesRepository(context),
) {
    private companion object {
        const val TAG = "ConversionRepository"
    }
    private val activeJobCoroutines = ConcurrentHashMap<String, Job>()

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
        activeJobCoroutines[jobId]?.cancel()
        activeJobCoroutines.remove(jobId)
        dao.updateProgress(jobId, JobStatus.CANCELLED, 0f, null, null)
    }

    suspend fun retryJob(jobId: String) {
        val job = dao.getJobById(jobId) ?: return
        val retriedJob = job.copy(
            status = JobStatus.PENDING,
            progress = 0f,
            errorMessage = null,
            retryCount = job.retryCount + 1
        )
        dao.updateJob(retriedJob)
    }

    suspend fun deleteJob(job: ConversionJobEntity) {
        activeJobCoroutines[job.id]?.cancel()
        activeJobCoroutines.remove(job.id)
        dao.deleteJob(job)
    }

    suspend fun clearCompletedJobs() {
        dao.clearCompletedJobs()
    }

    suspend fun clearAllJobs() {
        activeJobCoroutines.values.forEach { it.cancel() }
        activeJobCoroutines.clear()
        dao.clearAllJobs()
    }

    suspend fun executeJob(job: ConversionJobEntity, scope: CoroutineScope): Result<String> = withContext(Dispatchers.IO) {
        val jobCoro = scope.coroutineContext[Job]
        if (jobCoro != null) {
            activeJobCoroutines[job.id] = jobCoro
        }

        try {
            dao.updateProgress(job.id, JobStatus.RUNNING, 0.05f, "0.0x", "Estimating...")
            val settings = userPrefs.userSettingsFlow.first()

            val tempOutFile = StorageManager.createTempFileInCache(
                context,
                "out_${job.id}",
                job.outputFormat.lowercase()
            )

            val conversionResult = when (job.mediaType) {
                MediaType.IMAGE -> {
                    val format = when (job.outputFormat.lowercase()) {
                        "jpg", "jpeg" -> ImageOutputFormat.JPEG
                        "png" -> ImageOutputFormat.PNG
                        else -> ImageOutputFormat.WEBP
                    }
                    val config = ImageConversionConfig(
                        outputFormat = format,
                        preserveExif = settings.preserveMetadata
                    )
                    ImageConverterEngine.convertImage(
                        context = context,
                        inputUri = job.inputUri.toUri(),
                        outputFile = tempOutFile,
                        config = config,
                        onProgress = { pct ->
                            scope.launch(Dispatchers.IO) {
                                dao.updateProgress(job.id, JobStatus.RUNNING, pct, "N/A", null)
                            }
                        }
                    )
                }
                MediaType.VIDEO -> {
                    val config = VideoConversionConfig()
                    FFmpegEngine.convertVideo(
                        context = context,
                        inputUri = job.inputUri.toUri(),
                        tempOutputFile = tempOutFile,
                        config = config,
                        onProgress = { pct, speed, eta ->
                            scope.launch(Dispatchers.IO) {
                                dao.updateProgress(job.id, JobStatus.RUNNING, pct, speed, eta)
                            }
                        }
                    )
                }
                MediaType.AUDIO -> {
                    val config = AudioConversionConfig()
                    FFmpegEngine.convertAudio(
                        context = context,
                        inputUri = job.inputUri.toUri(),
                        tempOutputFile = tempOutFile,
                        config = config,
                        onProgress = { pct, speed, eta ->
                            scope.launch(Dispatchers.IO) {
                                dao.updateProgress(job.id, JobStatus.RUNNING, pct, speed, eta)
                            }
                        }
                    )
                }
                MediaType.DOWNLOAD -> {
                    val infoResult = HttpDownloaderEngine.extractMediaInfo(context, job.inputUri)
                    if (infoResult.isFailure) {
                        Result.failure(infoResult.exceptionOrNull() ?: Exception("Failed extracting direct HTTP stream info"))
                    } else {
                        val info = infoResult.getOrThrow()
                        val fmt = info.availableFormats.firstOrNull() ?: HttpMediaFormatOption("default", job.outputFormat, "Default", "Default Format")
                        HttpDownloaderEngine.downloadMedia(
                            context = context,
                            mediaInfo = info,
                            formatOption = fmt,
                            tempOutputFile = tempOutFile,
                            onProgress = { pct, speed, eta ->
                                scope.launch(Dispatchers.IO) {
                                    dao.updateProgress(job.id, JobStatus.RUNNING, pct, speed, eta)
                                }
                            }
                        )
                    }
                }
            }

            if (conversionResult.isFailure) {
                val err = conversionResult.exceptionOrNull()?.message ?: "Unknown conversion error"
                dao.markFailed(job.id, err)
                return@withContext Result.failure(Exception(err))
            }

            val finalFile = conversionResult.getOrThrow()
            val outputSize = finalFile.length()

            if (!settings.defaultOutputUri.isNullOrBlank()) {
                val parentTreeUri = Uri.parse(settings.defaultOutputUri)
                val destDocFile = StorageManager.createDestinationDocumentFile(
                    context = context,
                    treeUri = parentTreeUri,
                    relativeSubfolder = job.relativeFolderPath,
                    fileName = job.fileName,
                    mimeType = StorageManager.getMimeType(context, Uri.fromFile(finalFile))
                )

                if (destDocFile != null) {
                    val copied = StorageManager.copyFileToDocumentUri(context, finalFile, destDocFile)
                    if (copied) {
                        val finalOutputUri = destDocFile.uri.toString()
                        dao.markCompleted(
                            jobId = job.id,
                            outputUri = finalOutputUri,
                            outputFileSize = outputSize
                        )
                        return@withContext Result.success(finalOutputUri)
                    }
                }
            }

            val outputUriStr = finalFile.toURI().toString()
            dao.markCompleted(
                jobId = job.id,
                outputUri = outputUriStr,
                outputFileSize = outputSize
            )

            Result.success(outputUriStr)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing job ${job.id}", e)
            dao.markFailed(job.id, e.message ?: "Job failed with exception")
            Result.failure(e)
        } finally {
            activeJobCoroutines.remove(job.id)
        }
    }
}
