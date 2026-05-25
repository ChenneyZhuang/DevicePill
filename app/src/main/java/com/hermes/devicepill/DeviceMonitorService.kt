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
import kotlinx.coroutines.*

/**
 * Foreground service that displays live device stats via ColorOS 16 流体云.
 *
 * Implementation follows the dual-channel architecture documented in
 * sixiang-world/sy-ntfy-android LIVE_UPDATE_RESEARCH.md:
 *
 *   1) POST_PROMOTED_NOTIFICATIONS permission
 *   2) NotificationChannel.setLiveUpdateEnabled(true) via reflection
 *   3) android.requestPromotedOngoing = true in extras bundle
 *   4) Notification.BigTextStyle (NO RemoteViews!)
 *   5) Framework Notification.Builder (NOT Compat)
 *   6) FLAG_ONGOING_EVENT
 *   7) SDK 36+: setShortCriticalText(null)
 */
class DeviceMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var updateJob: Job? = null

    companion object {
        const val CHANNEL_ID = "fluid_cloud_channel"
        const val NOTIFICATION_ID = 2001
        internal const val ACTION_STOP = "com.hermes.devicepill.STOP"
        private const val PREFS = "device_pill_prefs"
        private const val KEY_RUNNING = "monitor_was_running"

        fun start(context: Context) {
            val intent = Intent(context, DeviceMonitorService::class.java)
            context.startForegroundService(intent)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_RUNNING, true).apply()
        }

        fun stop(context: Context) {
            val intent = Intent(context, DeviceMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_RUNNING, false).apply()
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
            "Charging Island",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Charging Island · 流体云 · 锁屏岛"
            setShowBadge(false)
            // Enable fluid cloud / live update capability on the channel
            enableLiveUpdate()
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Reflection call: NotificationChannel.setLiveUpdateEnabled(true)
     * This is a framework API (Android 8.0+), makes the channel eligible for fluid cloud.
     */
    private fun NotificationChannel.enableLiveUpdate() {
        try {
            val method = NotificationChannel::class.java.getMethod(
                "setLiveUpdateEnabled", Boolean::class.java
            )
            method.invoke(this, true)
        } catch (_: Exception) { /* best-effort */ }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_RUNNING, false).apply()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val snapshot = DeviceInfo.snapshot(this)
        startForeground(NOTIFICATION_ID, buildFluidCloudNotification(this, snapshot))
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
                delay(2000)
                val snap = DeviceInfo.snapshot(this@DeviceMonitorService)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildFluidCloudNotification(this@DeviceMonitorService, snap))
            }
        }
    }
}

/**
 * Builds a notification optimized for ColorOS 16 流体云.
 *
 * Uses the framework Notification.Builder (NOT Compat) to avoid
 * RemoteViews incompatibility with the Live Updates API.
 */
private fun buildFluidCloudNotification(
    context: Context,
    snap: com.hermes.devicepill.info.DeviceSnapshot
): Notification {
    val cpu = snap.cpu
    val bat = snap.battery
    val mem = snap.memory

    // --- Title for fluid cloud pill ---
    val title: String = if (bat.isCharging) {
        buildString {
            append("⚡ ")
            if (bat.powerW > 0) append("%.1fW · ".format(bat.powerW))
            append("%d%%".format(bat.levelPercent))
            if (cpu.temperatureC > 0) append(" · %.0f°C".format(cpu.temperatureC))
        }
    } else {
        buildString {
            append("🔋 %d%%".format(bat.levelPercent))
            if (cpu.temperatureC > 0) append(" · CPU %.0f°C".format(cpu.temperatureC))
            append(" · 内存 %d%%".format(mem.usagePercent))
        }
    }

    // --- BigText body ---
    val bigTitle = "⚡ Charging Island"
    val bigText = buildString {
        append("状态: %s  |  电量: %d%%".format(bat.status, bat.levelPercent))

        if (bat.isCharging) {
            append("\n\n📊 充电参数")
            if (bat.powerW > 0) append("\n瞬时功率: %.2fW".format(bat.powerW))
            append("\n电池温度: %.1f°C".format(bat.temperatureC))
            append("\n电池电压: %.2fV".format(bat.voltageV))
            if (bat.currentMa != 0) append("\n电池电流: %dmA".format(bat.currentMa))
            append("\n供电状态: %s".format(bat.chargeType))
        }
        append("\n电池状态: %s".format(bat.health))

        if (bat.capacityTotalMah > 0) {
            append("\n\n🔋 容量信息")
            append("\n剩余容量 / 总容量: %d / %d mAh".format(
                bat.capacityRemainingMah, bat.capacityTotalMah))
            append("\n电量占比: %d%%".format(bat.levelPercent))
        }

        append("\n\n🖥 设备")
        if (cpu.temperatureC > 0) append("\nCPU: %.1f°C".format(cpu.temperatureC))
        append("  |  内存: %d%%".format(mem.usagePercent))
        if (cpu.currentFrequencyMHz > 0) append("\n频率: %.0f MHz".format(cpu.currentFrequencyMHz))
        append("\n%s (%d核)".format(cpu.model, cpu.cores))
    }

    // --- Intents ---
    val stopIntent = PendingIntent.getService(context, 0,
        Intent(context, DeviceMonitorService::class.java).apply {
            action = DeviceMonitorService.ACTION_STOP
        },
        PendingIntent.FLAG_IMMUTABLE)

    val openIntent = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE)

    // --- Build with framework Notification.Builder ---
    val builder = Notification.Builder(context, DeviceMonitorService.CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
        .setContentTitle(title)
        .setContentText(bigText.lines().firstOrNull() ?: title)
        .setStyle(Notification.BigTextStyle()
            .setBigContentTitle(bigTitle)
            .bigText(bigText))
        .setOngoing(true)
        .setCategory(Notification.CATEGORY_STATUS)
        .setContentIntent(openIntent)
        .addAction(Notification.Action.Builder(
            android.R.drawable.ic_media_pause, "停止", stopIntent
        ).build())
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setShowWhen(false)

    // --- Fluid cloud specific extras ---
    val extras = Bundle()
    extras.putBoolean("android.requestPromotedOngoing", true)
    builder.setExtras(extras)

    // FLAG_ONGOING_EVENT for live updates
    builder.setFlag(Notification.FLAG_ONGOING_EVENT, true)

    // SDK 36+: short critical text for live update
    if (Build.VERSION.SDK_INT >= 36) {
        try {
            builder.setShortCriticalText(null)
        } catch (_: Exception) { }
    }

    return builder.build()
}
