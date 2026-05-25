package com.hermes.devicepill

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hermes.devicepill.info.DeviceInfo
import kotlinx.coroutines.*

/**
 * Foreground service that continuously monitors device performance
 * and displays live stats as a persistent notification.
 *
 * On ColorOS 16, this notification automatically appears in:
 * - 流体云 (Fluid Cloud)
 * - 锁屏岛 (Lock Screen Island)
 * - Always-on Display
 *
 * No root required — all data via system APIs.
 */
class DeviceMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var updateJob: Job? = null

    companion object {
        const val CHANNEL_ID = "device_monitor"
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
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "实时充电数据 — 流体云 · 锁屏岛"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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
        startForeground(NOTIFICATION_ID, buildNotification(this, snapshot))
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
                nm.notify(NOTIFICATION_ID, buildNotification(this@DeviceMonitorService, snap))
            }
        }
    }
}

/**
 * Builds a compact notification for Fluid Cloud.
 * Shows key metrics: CPU temp, battery, memory usage.
 */
private fun buildNotification(context: Context, snap: com.hermes.devicepill.info.DeviceSnapshot): Notification {
    val stopIntent = PendingIntent.getService(context, 0,
        Intent(context, DeviceMonitorService::class.java).apply { action = DeviceMonitorService.ACTION_STOP },
        PendingIntent.FLAG_IMMUTABLE)

    val openIntent = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE)

    val cpu = snap.cpu
    val bat = snap.battery
    val mem = snap.memory

    // Compact title for Fluid Cloud pill — prioritize charging data
    val title = if (bat.isCharging) {
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

    val body = buildString {
        // Charging Island section
        append("⚡ Charging Island")
        append("\n状态: %s".format(bat.status))
        append("  |  电量: %d%%".format(bat.levelPercent))

        if (bat.isCharging) {
            append("\n\n📊 充电参数")
            if (bat.powerW > 0) {
                append("\n瞬时功率: %.2fW".format(bat.powerW))
            }
            append("\n电池温度: %.1f°C".format(bat.temperatureC))
            append("\n电池电压: %.2fV".format(bat.voltageV))
            if (bat.currentMa != 0) {
                append("\n电池电流: %dmA".format(bat.currentMa))
            }
            append("\n供电状态: %s".format(bat.chargeType))
        }

        append("\n电池状态: %s".format(bat.health))

        // Capacity section
        if (bat.capacityTotalMah > 0) {
            append("\n\n🔋 容量信息")
            append("\n系统剩余容量 / 估算总容量: %d / %d mAh".format(
                bat.capacityRemainingMah, bat.capacityTotalMah))
            append("\n对应电量占比: %d%%".format(bat.levelPercent))
        }

        // Device section
        append("\n\n🖥 设备状态")
        if (cpu.temperatureC > 0) {
            append("\nCPU: %.1f°C".format(cpu.temperatureC))
        }
        append("  |  内存: %d%%".format(mem.usagePercent))
        if (cpu.currentFrequencyMHz > 0) {
            append("\nCPU频率: %.0f MHz".format(cpu.currentFrequencyMHz))
        }
        append("\nCPU型号: %s (%d核)".format(cpu.model, cpu.cores))
    }

    return NotificationCompat.Builder(context, DeviceMonitorService.CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(Notification.CATEGORY_STATUS)
        .setContentIntent(openIntent)
        .addAction(android.R.drawable.ic_media_pause, "停止监控", stopIntent)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setShowWhen(false)
        .build()
}
