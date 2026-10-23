package org.roruku.smartformatconverter.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

                for (job in pendingJobs) {
                    updateNotification(job.fileName, (job.progress * 100).toInt())
                    repository.executeJob(job, serviceScope)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Media Conversion Service",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows ongoing media conversion progress"
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundServiceNotification() {
        val notification = buildNotification("Smart Format Converter", "Processing media files...", 0)
        startForeground(NOTIFICATION_ID, notification)
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(fileName: String, progressPct: Int) {
        val notification = buildNotification("Converting $fileName", "$progressPct% complete", progressPct)
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
