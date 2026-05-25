package com.hermes.devicepill

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import com.hermes.devicepill.info.DeviceInfo
import com.hermes.devicepill.info.DeviceSnapshot
import kotlinx.coroutines.*

/**
 * ColorOS 16 流体云 foreground service.
 *
 * Follows the Cmd2Gui pattern documented in
 * sy-ntfy-android/docs/LIVE_UPDATE_RESEARCH.md:
 *
 * All 8 conditions for Android 16 Live Update:
 *   1. Standard style → BigTextStyle ✓
 *   2. POST_PROMOTED_NOTIFICATIONS permission ✓
 *   3. android.requestPromotedOngoing extra ✓
 *   4. FLAG_ONGOING_EVENT ✓
 *   5. contentTitle ✓
 *   6. No RemoteViews / customContentView ✓
 *   7. Not group summary ✓
 *   8. Not colorized ✓
 *
 * SDK 36+ bonus: setShortCriticalText(null)
 */
class DeviceMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var updateJob: Job? = null

    companion object {
        const val CHANNEL_ID = "fluid_cloud_channel"
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
            "实时状态通知",
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
        startForeground(NOTIFICATION_ID, buildFluidCloudNotification(snapshot))
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
                nm.notify(NOTIFICATION_ID, buildFluidCloudNotification(snap))
            }
        }
    }

    /**
     * Build notification following ALL 8 Android 16 Live Update conditions
     * and the Cmd2Gui ColorOS private channel pattern.
     */
    private fun buildFluidCloudNotification(snap: DeviceSnapshot): Notification {
        val bat = snap.battery
        val cpu = snap.cpu
        val mem = snap.memory

        // --- Title (condition 5) ---
        val title = if (bat.isCharging) {
            buildString {
                append("⚡ 充电中 ")
                if (bat.powerW > 0) append("${String.format("%.1f", bat.powerW)}W · ")
                append("${bat.levelPercent}%")
            }
        } else {
            "🔋 ${bat.levelPercent}% · ${String.format("%.1f", cpu.temperatureC)}°C"
        }

        // --- Body text for BigTextStyle (condition 1: standard style) ---
        val bigTitle = "⚡ Charging Island"
        val bigText = buildString {
            append("电量: ${bat.levelPercent}%  |  状态: ${bat.status}")

            if (bat.isCharging) {
                append("\n\n📊 充电参数")
                if (bat.powerW > 0) append("\n瞬时功率: ${String.format("%.2f", bat.powerW)}W")
                append("\n电池温度: ${String.format("%.1f", bat.temperatureC)}°C")
                append("\n电池电压: ${String.format("%.2f", bat.voltageV)}V")
                if (bat.currentMa != 0) append("\n充电电流: ${bat.currentMa}mA")
                append("\n供电方式: ${bat.chargeType}")
            }
            append("\n电池健康: ${bat.health}")

            if (bat.capacityTotalMah > 0) {
                append("\n\n🔋 容量: ${bat.capacityRemainingMah}/${bat.capacityTotalMah} mAh")
            }

            append("\n\n🖥 设备")
            append("\nCPU: ${String.format("%.1f", cpu.temperatureC)}°C  ·  内存: ${mem.usagePercent}%")
            if (cpu.currentFrequencyMHz > 0) append("\n频率: ${String.format("%.0f", cpu.currentFrequencyMHz)} MHz")
            append("\n${cpu.model} (${cpu.cores}核)")
        }

        // --- Intents ---
        val openIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = PendingIntent.getService(this, 0,
            Intent(this, DeviceMonitorService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE)

        // --- Build notification with framework Notification.Builder (like Cmd2Gui) ---
        // Condition 1: BigTextStyle (standard style, no RemoteViews)
        // Condition 6: NO customContentView (no RemoteViews)
        // Condition 7: NOT group summary
        // Condition 8: NOT colorized
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(title)                    // Condition 5
            .setContentText(bat.status)
            .setStyle(Notification.BigTextStyle()
                .setBigContentTitle(bigTitle)
                .bigText(bigText))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(openIntent)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_media_pause,
                "停止",
                stopIntent
            ).build())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())

        // Condition 3: android.requestPromotedOngoing via extras (ColorOS private channel)
        val extras = Bundle()
        extras.putBoolean("android.requestPromotedOngoing", true)
        builder.setExtras(extras)

        // Condition 4: FLAG_ONGOING_EVENT (flag 2)
        builder.setFlag(Notification.FLAG_ONGOING_EVENT, true)

        // SDK 36+ bonus: setShortCriticalText(null)
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                builder.setShortCriticalText(null)
            } catch (_: Exception) { }
        }

        return builder.build()
    }
}
