package com.hermes.devicepill

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * ColorOS 16 流体云 — 金标充电显示
 *
 * Based on sy-ntfy-android's verified LiveUpdate implementation:
 *   1. NotificationCompat.Builder with setLiveUpdateEnabled(true)
 *   2. CATEGORY_PROGRESS + setOngoing(true)
 *   3. Framework builder: requestPromotedOngoing() + setShortCriticalText(null)
 *   4. android.requestPromotedOngoing bundle extra (ColorOS private API)
 *   5. Progress bar (battery level 0-100)
 */
class DeviceMonitorService : Service() {

    private var batteryReceiver: BatteryReceiver? = null
    private var lastLevel = 0
    private var lastIsCharging = false
    private var lastPowerW = 0.0
    private var lastTempC = 0f

    companion object {
        const val CHANNEL_ID = "fluid_cloud_channel"
        const val NOTIFICATION_ID = 2001
        internal const val ACTION_STOP = "com.hermes.devicepill.STOP"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, DeviceMonitorService::class.java))
        }
        fun stop(context: Context) {
            context.startService(Intent(context, DeviceMonitorService::class.java).apply {
                action = ACTION_STOP
            })
        }
        fun isRunning(context: Context): Boolean {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            return am.getRunningServices(Integer.MAX_VALUE)
                .any { it.service.className == DeviceMonitorService::class.java.name }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_ID, "实时状态通知",
            NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "用于显示流体云动态信息"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopChargingIsland()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification(0, false, 0.0, 0f))
        startBatteryMonitoring()
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        stopChargingIsland()
        super.onDestroy()
    }

    private fun stopChargingIsland() {
        batteryReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        batteryReceiver = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ============================================================
    // Build Notification following ntfy's verified LiveUpdate pattern
    // ============================================================

    private fun buildNotification(level: Int, isCharging: Boolean, powerW: Double, tempC: Float): Notification {
        // Title for fluid cloud capsule
        val title = if (isCharging && powerW > 0.5) {
            "⚡ ${String.format("%.0f", powerW)}W · ${level}%"
        } else if (isCharging) {
            "⚡ 充电中 · ${level}%"
        } else {
            "🔋 ${level}%"
        }

        val body = if (isCharging && tempC > 0) {
            "功率 ${String.format("%.1f", powerW)}W · ${String.format("%.0f", tempC)}°C"
        } else if (isCharging) {
            "正在充电"
        } else {
            "未充电"
        }

        val openIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = PendingIntent.getService(this, 0,
            Intent(this, DeviceMonitorService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE)

        // === ntfy pattern: NotificationCompat.Builder ===
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(isCharging) // Only ongoing when charging
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "停止", stopIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(100, level, false) // Battery level as progress
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())

        // === ntfy pattern: applyLiveUpdateSettings ===
        if (Build.VERSION.SDK_INT >= 35) { // VANILLA_ICE_CREAM
            applyLiveUpdateSettings(builder, isCharging)
        }

        return builder.build()
    }

    /**
     * Adapted from sy-ntfy-android NotificationService.applyLiveUpdateSettings()
     */
    private fun applyLiveUpdateSettings(builder: NotificationCompat.Builder, isCharging: Boolean) {
        // 1. setLiveUpdateEnabled(true) on Compat builder
        try {
            val method = builder.javaClass.getMethod("setLiveUpdateEnabled", Boolean::class.javaPrimitiveType!!)
            method.invoke(builder, true)
        } catch (_: Exception) {}

        // Only enable LiveUpdate when charging (has progress = ongoing + category progress)
        if (!isCharging) return

        // 2. Get framework builder via reflection
        val frameworkBuilder = try {
            val compatBuilderClass = Class.forName("androidx.core.app.NotificationCompatBuilder")
            val getBuilderMethod = compatBuilderClass.getMethod("getBuilder")
            getBuilderMethod.invoke(builder) as? Notification.Builder
        } catch (_: Exception) { null }

        if (frameworkBuilder != null) {
            // 3. requestPromotedOngoing()
            try {
                val method = frameworkBuilder.javaClass.getMethod("requestPromotedOngoing")
                method.invoke(frameworkBuilder)
            } catch (_: Exception) {
                try {
                    val method = builder.javaClass.getMethod("setRequestPromotedOngoing")
                    method.invoke(builder)
                } catch (_: Exception) {}
            }

            // 4. setShortCriticalText(null) — matches Cmd2Gui g.a()
            try {
                val method = frameworkBuilder.javaClass.getMethod("setShortCriticalText", CharSequence::class.java)
                method.invoke(frameworkBuilder, null)
            } catch (_: Exception) {}

            // 5. ColorOS private: android.requestPromotedOngoing bundle extra
            try {
                val extrasMethod = frameworkBuilder.javaClass.getMethod("getExtras")
                val extras = extrasMethod.invoke(frameworkBuilder) as Bundle
                extras.putBoolean("android.requestPromotedOngoing", true)
            } catch (_: Exception) {
                try {
                    builder.extras.putBoolean("android.requestPromotedOngoing", true)
                } catch (_: Exception) {}
            }
        }
    }

    // ============================================================
    // Battery Monitoring
    // ============================================================

    private fun startBatteryMonitoring() {
        batteryReceiver = BatteryReceiver()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    inner class BatteryReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val pct = if (scale > 0) level * 100 / scale else 0
            val tempDeci = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            val temp = tempDeci / 10f
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL

            var powerW = 0.0
            if (isCharging) {
                try {
                    val bm = getSystemService(BATTERY_SERVICE) as? BatteryManager
                    if (bm != null) {
                        val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                        if (raw != Int.MIN_VALUE) {
                            var ma = raw
                            if (kotlin.math.abs(ma) > 10000) ma /= 1000
                            val mv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                            powerW = (mv / 1000.0) * kotlin.math.abs(ma) / 1000.0
                        }
                    }
                } catch (_: Exception) {}
            }

            // Only update if something changed
            if (pct != lastLevel || isCharging != lastIsCharging ||
                kotlin.math.abs(powerW - lastPowerW) > 0.5 ||
                kotlin.math.abs(temp - lastTempC) > 0.5) {
                lastLevel = pct
                lastIsCharging = isCharging
                lastPowerW = powerW
                lastTempC = temp
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(pct, isCharging, powerW, temp))
            }
        }
    }
}
