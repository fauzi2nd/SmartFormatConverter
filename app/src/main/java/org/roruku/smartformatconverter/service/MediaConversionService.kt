package org.roruku.smartformatconverter.service

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.roruku.smartformatconverter.MainActivity
import org.roruku.smartformatconverter.R
import org.roruku.smartformatconverter.data.db.AppDatabase
import org.roruku.smartformatconverter.data.repository.ConversionRepository

class MediaConversionService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: ConversionRepository
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        repository = ConversionRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            startForegroundServiceNotification()
            processNextJobs()
        }
        return START_STICKY
    }

    private fun processNextJobs() {
        serviceScope.launch {
            val dao = AppDatabase.getInstance(applicationContext).conversionJobDao()
            dao.resetStuckJobsToPending()
            while (isRunning) {
                val pendingJobs = dao.getActiveAndPendingJobs()
                if (pendingJobs.isEmpty()) {
                    isRunning = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }

                for (snapshot in pendingJobs) {
                    if (!serviceScope.isActive || !isRunning) break
                    // Queue may cancel a pending entry after the list was read.
                    val current = dao.getJobById(snapshot.id) ?: continue
                    if (current.status !in setOf(
                            org.roruku.smartformatconverter.data.db.JobStatus.PENDING,
                            org.roruku.smartformatconverter.data.db.JobStatus.PREPARING,
                            org.roruku.smartformatconverter.data.db.JobStatus.RUNNING
                        )
                    ) continue

                    updateNotification(current.fileName, (current.progress * 100).toInt())
                    try {
                        // A task owns its own child Job. Queue cancellation
                        // stops only this download/conversion, not the loop.
                        serviceScope.async {
                            repository.executeJob(current, this)
                        }.await()
                    } catch (cancelled: CancellationException) {
                        if (!serviceScope.isActive) throw cancelled
                        Log.i("MediaConversionService", "Job cancelled: ${current.id}")
                    } catch (error: Exception) {
                        Log.e("MediaConversionService", "Failed processing queued job", error)
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.conversion_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.conversion_notification_channel_description)
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundServiceNotification() {
        val notification = buildNotification(getString(R.string.app_name), getString(R.string.conversion_notification_processing), 0)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(fileName: String, progressPct: Int) {
        // Android 13+ may hide non-exempt notifications when permission is denied.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val notification = buildNotification(
            getString(R.string.conversion_notification_title, fileName),
            getString(R.string.conversion_notification_progress, progressPct),
            progressPct
        )
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, content: String, progress: Int): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        isRunning = false
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "smart_format_converter_conversion_channel"
        const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            val intent = Intent(context, MediaConversionService::class.java)
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MediaConversionService::class.java)
            context.stopService(intent)
        }
    }
}
