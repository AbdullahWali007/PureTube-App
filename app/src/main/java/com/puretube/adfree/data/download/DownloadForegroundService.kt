package com.puretube.adfree.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.puretube.adfree.MainActivity
import com.puretube.adfree.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class DownloadForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var downloadObserverJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isForegroundStarted = false

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.i(TAG, "DownloadForegroundService created.")
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.i(TAG, "DownloadForegroundService onStartCommand.")

        if (intent?.action == ACTION_STOP) {
            stopForegroundService()
            return START_NOT_STICKY
        }

        // Start foreground immediately with default notification to satisfy Android requirements
        if (!isForegroundStarted) {
            val initialNotification = buildNotification(
                title = "PureTube Downloader",
                contentText = "Preparing download...",
                progress = 0,
                isIndeterminate = true
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        initialNotification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, initialNotification)
                }
                isForegroundStarted = true
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to start foreground: ${e.message}", e)
            }
        }

        startObservingDownloads()
        return START_STICKY
    }

    private fun startObservingDownloads() {
        downloadObserverJob?.cancel()
        downloadObserverJob = serviceScope.launch {
            try {
                val database = DownloadDatabase.getInstance(applicationContext)
                val dao = database.downloadDao()

                dao.getRunningDownloadsFlow().collectLatest { runningList ->
                    if (runningList.isEmpty()) {
                        AppLogger.i(TAG, "No running downloads. Stopping foreground service.")
                        stopForegroundService()
                    } else {
                        updateNotificationForDownloads(runningList)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error in download flow observer: ${e.message}", e)
            }
        }
    }

    private fun updateNotificationForDownloads(runningList: List<DownloadEntity>) {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean(PREF_DOWNLOAD_NOTIFICATIONS, true)

        val active = runningList.firstOrNull { it.status == DownloadStatus.DOWNLOADING } ?: runningList.first()
        val totalBytes = active.totalBytes
        val downloadedBytes = active.downloadedBytes

        val percent = if (totalBytes > 0) {
            ((downloadedBytes.toFloat() / totalBytes) * 100f).toInt().coerceIn(0, 100)
        } else 0

        val speedText = if (active.speedBytesPerSec > 0) " • ${formatSpeed(active.speedBytesPerSec)}" else ""
        val extraItemsText = if (runningList.size > 1) " (+${runningList.size - 1} queued)" else ""
        val titleText = if (active.title.isNotBlank()) active.title else "Downloading media"
        val contentText = "${formatSize(downloadedBytes)} / ${formatSize(totalBytes)}$speedText$extraItemsText"

        val notification = if (notificationsEnabled) {
            buildNotification(
                title = titleText,
                contentText = contentText,
                progress = percent,
                isIndeterminate = totalBytes <= 0 || (downloadedBytes == 0L && percent == 0)
            )
        } else {
            // Minimal silent notification required to keep background execution alive
            buildNotification(
                title = "PureTube Background Download",
                contentText = "Downloading in progress...",
                progress = 0,
                isIndeterminate = true
            )
        }

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update notification: ${e.message}", e)
        }
    }

    private fun buildNotification(
        title: String,
        contentText: String,
        progress: Int,
        isIndeterminate: Boolean
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_DOWNLOADS, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, isIndeterminate)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress and status of active video & audio downloads"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "PureTube:DownloadWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                    acquire(3 * 60 * 60 * 1000L) // 3 hours timeout safety
                }
                AppLogger.d(TAG, "Partial WakeLock acquired for background downloading.")
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                AppLogger.d(TAG, "WakeLock released.")
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error releasing WakeLock: ${e.message}")
        } finally {
            wakeLock = null
        }
    }

    private fun stopForegroundService() {
        releaseWakeLock()
        downloadObserverJob?.cancel()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error in stopForeground: ${e.message}")
        }
        stopSelf()
    }

    override fun onDestroy() {
        AppLogger.i(TAG, "DownloadForegroundService destroyed.")
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "DownloadForegroundService"
        const val CHANNEL_ID = "puretube_downloads_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP = "com.puretube.adfree.action.STOP_DOWNLOAD_SERVICE"
        const val EXTRA_OPEN_DOWNLOADS = "extra_open_downloads"

        const val PREFS_NAME = "puretube_preferences"
        const val PREF_DOWNLOAD_NOTIFICATIONS = "pref_download_notifications"

        fun start(context: Context) {
            try {
                val intent = Intent(context, DownloadForegroundService::class.java)
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to start DownloadForegroundService: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, DownloadForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to stop DownloadForegroundService: ${e.message}")
            }
        }

        private fun formatSize(bytes: Long): String {
            if (bytes <= 0) return "0 MB"
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            return if (mb >= 1024.0) {
                String.format(Locale.US, "%.1f GB", mb / 1024.0)
            } else {
                String.format(Locale.US, "%.1f MB", mb)
            }
        }

        private fun formatSpeed(bytesPerSec: Long): String {
            if (bytesPerSec <= 0) return "0 KB/s"
            val kb = bytesPerSec.toDouble() / 1024.0
            return if (kb >= 1024.0) {
                String.format(Locale.US, "%.1f MB/s", kb / 1024.0)
            } else {
                String.format(Locale.US, "%.0f KB/s", kb)
            }
        }
    }
}