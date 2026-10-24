package org.roruku.smartformatconverter.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.roruku.smartformatconverter.MainActivity
import org.roruku.smartformatconverter.R
import org.roruku.smartformatconverter.data.db.AppDatabase
import org.roruku.smartformatconverter.data.db.ConversionJobEntity
import org.roruku.smartformatconverter.data.db.JobStatus
import org.roruku.smartformatconverter.data.db.MediaType
import org.roruku.smartformatconverter.data.preferences.UserPreferencesRepository
import org.roruku.smartformatconverter.data.repository.ActiveConversionJobs
import org.roruku.smartformatconverter.data.repository.ConversionRepository
import kotlinx.coroutines.flow.first

class MediaConversionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: ConversionRepository
    private lateinit var notificationManager: NotificationManager

    @Volatile private var isRunning = false
    @Volatile private var foregroundStarted = false
    @Volatile private var currentJobId: String? = null
    private var lastNotificationAt = 0L
    private var lastNotifiedPercent = -1
    private var lastNotifiedJob: String? = null

    override fun onCreate() {
        super.onCreate()
        repository = ConversionRepository(applicationContext)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
        // Observe database progress from the existing single-writer repository.
        serviceScope.launch {
            AppDatabase.getInstance(applicationContext).conversionJobDao().getAllJobsFlow().collect { jobs ->
                val current = jobs.firstOrNull { it.id == currentJobId }
                if (current != null && current.status in setOf(JobStatus.RUNNING, JobStatus.PREPARING)) {
                    showProgress(current, jobs)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val jobId = intent?.getStringExtra(EXTRA_JOB_ID)
        when (intent?.action) {
            ACTION_PAUSE -> {
                if (jobId != null) {
                    serviceScope.launch {
                        val job = AppDatabase.getInstance(applicationContext).conversionJobDao().getJobById(jobId)
                        if (repository.pauseJob(jobId) && job != null) showPaused(job)
                    }
                }
                return START_NOT_STICKY
            }
            ACTION_CANCEL -> {
                if (jobId != null) {
                    serviceScope.launch {
                        repository.cancelJob(jobId)
                        notificationManager.cancel(pausedNotificationId(jobId))
                    }
                }
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                // Explicit notification interaction is a user-initiated service start.
                startForegroundIfNeeded()
                serviceScope.launch {
                    notificationManager.cancel(pausedNotificationId(jobId.orEmpty()))
                    if (jobId != null) repository.resumeJob(jobId)
                    runQueueIfNeeded()
                }
                return START_NOT_STICKY
            }
            else -> {
                startForegroundIfNeeded()
                runQueueIfNeeded()
                return START_NOT_STICKY
            }
        }
    }

    private fun runQueueIfNeeded() {
        if (isRunning) return
        isRunning = true
        serviceScope.launch {
            val dao = AppDatabase.getInstance(applicationContext).conversionJobDao()
            try {
                dao.resetStuckJobsToPending()
                while (isRunning && serviceScope.isActive) {
                    val pending = dao.getActiveAndPendingJobs()
                    if (pending.isEmpty()) break
                    for (snapshot in pending) {
                        if (!serviceScope.isActive || !isRunning) break
                        val current = dao.getJobById(snapshot.id) ?: continue
                        if (current.status !in setOf(JobStatus.PENDING, JobStatus.PREPARING, JobStatus.RUNNING)) continue
                        currentJobId = current.id
                        lastNotificationAt = 0L
                        lastNotifiedPercent = -1
                        lastNotifiedJob = null
                        setForegroundType(current.mediaType)
                        try {
                            serviceScope.async { repository.executeJob(current, this) }.await()
                        } catch (cancelled: CancellationException) {
                            if (!serviceScope.isActive) throw cancelled
                            Log.i(TAG, "Job stopped by Pause/Cancel: ${current.id}")
                        } catch (error: Exception) {
                            Log.e(TAG, "Job failed: ${current.id}", error)
                        }
                        val finalJob = dao.getJobById(current.id)
                        if (finalJob?.status == JobStatus.COMPLETED || finalJob?.status == JobStatus.FAILED) {
                            showOutcome(finalJob)
                        }
                        currentJobId = null
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Queue processor stopped unexpectedly", error)
            } finally {
                currentJobId = null
                isRunning = false
                foregroundStarted = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun startForegroundIfNeeded() {
        if (foregroundStarted) return
        val initial = buildProgressNotification(
            getString(R.string.app_name),
            getString(R.string.conversion_notification_processing),
            0, null, true
        )
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, initial,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )
        foregroundStarted = true
    }

    private fun setForegroundType(mediaType: MediaType) {
        val type = if (Build.VERSION.SDK_INT >= 35 && mediaType != MediaType.DOWNLOAD) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else if (Build.VERSION.SDK_INT >= 29) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 0
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID,
            buildProgressNotification(
                getString(R.string.app_name),
                getString(R.string.conversion_notification_processing), 0, currentJobId, true
            ),
            type
        )
    }

    private fun showProgress(job: ConversionJobEntity, all: List<ConversionJobEntity>) {
        if (!foregroundStarted || !isRunning || !canPostNotifications()) return
        val now = SystemClock.elapsedRealtime()
        val percent = (job.progress * 100).toInt().coerceIn(0, 99)
        if (job.id == lastNotifiedJob && percent == lastNotifiedPercent && now - lastNotificationAt < 1500L) return
        if (job.id == lastNotifiedJob && now - lastNotificationAt < 750L) return
        val ordered = all.sortedBy { it.createdAt }
        val index = ordered.indexOfFirst { it.id == job.id } + 1
        val title = getString(R.string.notification_progress_title, index.coerceAtLeast(1), ordered.size.coerceAtLeast(1), job.fileName)
        val extra = job.eta?.takeIf { it.isNotBlank() } ?: getString(R.string.queue_measuring)
        val detail = if (job.mediaType == MediaType.DOWNLOAD && job.fileSize <= 0L) {
            getString(R.string.conversion_notification_processing)
        } else getString(R.string.notification_progress_detail, percent, extra)
        notificationManager.notify(
            NOTIFICATION_ID, buildProgressNotification(title, detail, percent, job.id, job.mediaType == MediaType.DOWNLOAD && job.fileSize <= 0L)
        )
        lastNotifiedJob = job.id
        lastNotifiedPercent = percent
        lastNotificationAt = now
    }

    private fun buildProgressNotification(
        title: String, detail: String, percent: Int, jobId: String?, indeterminate: Boolean
    ): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(queuePendingIntent())
            .setProgress(100, percent, indeterminate)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (jobId != null) {
            builder.addAction(
                android.R.drawable.ic_media_pause, getString(R.string.action_pause),
                serviceAction(ACTION_PAUSE, jobId, false)
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.cancel),
                serviceAction(ACTION_CANCEL, jobId, false)
            )
            builder.addAction(
                android.R.drawable.ic_menu_view, getString(R.string.notification_action_queue),
                queuePendingIntent()
            )
        }
        return builder.build()
    }

    private fun showPaused(job: ConversionJobEntity) {
        if (!canPostNotifications()) return
        val notification = NotificationCompat.Builder(this, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.notification_status_paused, job.fileName))
            .setContentText(getString(R.string.pause_resume_hint))
            .setContentIntent(queuePendingIntent())
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_media_play, getString(R.string.action_resume),
                serviceAction(ACTION_RESUME, job.id, true)
            )
            .build()
        notificationManager.notify(pausedNotificationId(job.id), notification)
    }

    private suspend fun showOutcome(job: ConversionJobEntity) {
        if (!canPostNotifications()) return
        val settings = UserPreferencesRepository(applicationContext).userSettingsFlow.first()
        val completed = job.status == JobStatus.COMPLETED
        if (completed && !settings.notifyCompleted) return
        if (!completed && !settings.notifyFailed) return
        val title = if (completed) getString(R.string.notification_status_completed, job.fileName)
            else getString(R.string.notification_status_failed, job.fileName)
        val detail = if (completed) getString(R.string.notification_status_complete_detail)
            else getString(R.string.notification_status_failure_detail)
        val notification = NotificationCompat.Builder(this, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(queuePendingIntent())
            .setAutoCancel(true)
            .build()
        notificationManager.notify(outcomeNotificationId(job.id), notification)
    }

    private fun createNotificationChannels() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                getString(R.string.conversion_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.conversion_notification_channel_description)
            }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULTS, getString(R.string.notification_channel_results), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun queuePendingIntent(): PendingIntent = PendingIntent.getActivity(
        this, 500, Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_QUEUE, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun serviceAction(actionName: String, jobId: String, foreground: Boolean): PendingIntent {
        val intent = Intent(this, MediaConversionService::class.java).apply {
            action = actionName
            putExtra(EXTRA_JOB_ID, jobId)
        }
        val requestCode = jobId.hashCode() xor actionName.hashCode()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (foreground) {
            PendingIntent.getForegroundService(this, requestCode, intent, flags)
        } else PendingIntent.getService(this, requestCode, intent, flags)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15+ enforces a background time budget for mediaProcessing/dataSync.
        // Stop promptly instead of allowing Android to kill the whole process.
        isRunning = false
        ActiveConversionJobs.cancelAll()
        serviceScope.launch {
            AppDatabase.getInstance(applicationContext).conversionJobDao()
                .interruptRunningJobs("Android background service time limit reached. Retry from Queue.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun pausedNotificationId(jobId: String): Int = 200000 + (jobId.hashCode() and 0x3fffffff) % 1000000
    private fun outcomeNotificationId(jobId: String): Int = 1200000 + (jobId.hashCode() and 0x3fffffff) % 1000000

    companion object {
        private const val TAG = "MediaConversionService"
        private const val CHANNEL_PROGRESS = "smart_format_converter_conversion_channel"
        private const val CHANNEL_RESULTS = "smart_format_converter_results"
        private const val ACTION_PAUSE = "org.roruku.smartformatconverter.PAUSE"
        private const val ACTION_RESUME = "org.roruku.smartformatconverter.RESUME"
        private const val ACTION_CANCEL = "org.roruku.smartformatconverter.CANCEL"
        private const val EXTRA_JOB_ID = "job_id"
        const val EXTRA_OPEN_QUEUE = "open_queue"
        const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            context.startForegroundService(Intent(context, MediaConversionService::class.java))
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, MediaConversionService::class.java))
        }
    }
}
