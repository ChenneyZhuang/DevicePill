package com.hermes.devicepill

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import com.hermes.devicepill.info.DeviceInfo
import com.hermes.devicepill.info.DeviceSnapshot
import kotlinx.coroutines.*

/**
 * Foreground service using Android 16 Notification.ProgressStyle
 * for ColorOS 16 流体云 native support.
 *
 * ColorOS 16 流体云 is built on Android 16's Live Updates API
 * (Notification.ProgressStyle). No OPPO-specific hacks needed —
 * use ProgressStyle with segments and it just works.
 */
class DeviceMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var updateJob: Job? = null

    companion object {
        const val CHANNEL_ID = "charging_island"
        const val NOTIFICATION_ID = 2001
        internal const val ACTION_STOP = "com.hermes.devicepill.STOP"
        private const val PREFS = "device_pill_prefs"

        fun start(context: Context) {
            val intent = Intent(context, DeviceMonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, DeviceMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun isRunning(context: Context): Boolean {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            return am.getRunningServices(Integer.MAX_VALUE)
                .any { it.service.className == DeviceMonitorService::class.java.name }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            "充电岛",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "电池充电状态 · 流体云实时显示"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val snapshot = DeviceInfo.snapshot(this)
        startForeground(NOTIFICATION_ID, buildNotification(snapshot))
        startPeriodicUpdates()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        updateJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startPeriodicUpdates() {
        updateJob?.cancel()
        updateJob = scope.launch {
            while (isActive) {
                delay(3000)
                val snap = DeviceInfo.snapshot(this@DeviceMonitorService)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(snap))
            }
        }
    }

    private fun buildNotification(snap: DeviceSnapshot): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            buildProgressStyleNotification(snap)
        } else {
            buildFallbackNotification(snap)
        }
    }

    /**
     * Android 16 ProgressStyle — this directly maps to ColorOS 16 流体云.
     * No hacks, no reflection, no custom bundles. Just the standard API.
     */
    private fun buildProgressStyleNotification(snap: DeviceSnapshot): Notification {
        val bat = snap.battery
        val cpu = snap.cpu

        // Create segments for battery level visualization
        val segments = createBatterySegments(bat.levelPercent)

        val progressStyle = Notification.ProgressStyle()
            .setProgress(bat.levelPercent)
            .setProgressSegments(segments)
            .setStyledByProgress(true)

        val title = if (bat.isCharging) {
            buildString {
                append("⚡ 充电中 ")
                append("${bat.levelPercent}%")
                if (bat.powerW > 0) append(" · ${String.format("%.1f", bat.powerW)}W")
            }
        } else {
            "🔋 ${bat.levelPercent}% · ${String.format("%.1f", cpu.temperatureC)}°C"
        }

        val subText = buildString {
            if (bat.isCharging) {
                append(bat.chargeType)
                if (bat.temperatureC > 0) append(" · ${String.format("%.1f", bat.temperatureC)}°C")
            } else {
                append("未充电")
            }
        }

        val openIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = PendingIntent.getService(this, 0,
            Intent(this, DeviceMonitorService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE)

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(title)
            .setContentText(subText)
            .setSubText(if (bat.isCharging) "${bat.chargeType} · ${bat.health}" else bat.health)
            .setStyle(progressStyle)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    "停止",
                    stopIntent
                ).build()
            )
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()

        return notification
    }

    private fun createBatterySegments(currentLevel: Int): List<Notification.ProgressStyle.Segment> {
        // Four segments: 0-25, 25-50, 50-75, 75-100
        return listOf(
            Notification.ProgressStyle.Segment(25).setColor(
                if (currentLevel <= 25) Color.rgb(255, 59, 48)   // Red when low
                else Color.rgb(50, 205, 50)                       // Green when past
            ),
            Notification.ProgressStyle.Segment(25).setColor(
                if (currentLevel in 26..50) Color.rgb(255, 149, 0)  // Orange when here
                else if (currentLevel > 50) Color.rgb(50, 205, 50)   // Green when past
                else Color.rgb(80, 80, 80)                           // Grey when not reached
            ),
            Notification.ProgressStyle.Segment(25).setColor(
                if (currentLevel in 51..75) Color.rgb(255, 204, 0)   // Yellow when here
                else if (currentLevel > 75) Color.rgb(50, 205, 50)   // Green when past
                else Color.rgb(80, 80, 80)                           // Grey when not reached
            ),
            Notification.ProgressStyle.Segment(25).setColor(
                if (currentLevel > 75) Color.rgb(50, 205, 50)        // Green
                else Color.rgb(80, 80, 80)                           // Grey when not reached
            )
        )
    }

    /** Fallback for pre-Android-16 devices */
    private fun buildFallbackNotification(snap: DeviceSnapshot): Notification {
        val bat = snap.battery
        val cpu = snap.cpu

        val title = if (bat.isCharging) {
            "⚡ 充电中 ${bat.levelPercent}%"
        } else {
            "🔋 ${bat.levelPercent}%"
        }

        val body = buildString {
            append("${bat.status}")
            if (bat.isCharging && bat.powerW > 0) {
                append(" · ${String.format("%.1f", bat.powerW)}W")
            }
            append(" · ${String.format("%.1f", cpu.temperatureC)}°C")
            append(" · ${bat.health}")
        }

        val openIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = PendingIntent.getService(this, 0,
            Intent(this, DeviceMonitorService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE)

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    "停止",
                    stopIntent
                ).build()
            )
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
    }
}
