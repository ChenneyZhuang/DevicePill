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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlin.math.abs

/**
 * ColorOS 16 流体云 — 基于 LLMonitor 反编译验证
 *
 * LLMonitor's approach (verified working on ColorOS 16):
 *   1. Framework Notification.Builder (NOT Compat)
 *   2. CHANNEL importance LOW (=2), progress channel DEFAULT (=3)
 *   3. API 36+: Notification.ProgressStyle with battery level progress
 *   4. android.requestPromotedOngoing extras bundle — THE KEY!
 *   5. setOngoing(true) + setOnlyAlertOnce(true)
 *   6. NO setShortCriticalText, NO setRequestPromotedOngoing
 */
class DeviceMonitorService : Service() {

    private var monitorRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private var batteryReceiver: BatteryReceiver? = null

    companion object {
        const val CHANNEL_ID = "battery_monitor"
        const val LIVE_CHANNEL_ID = "battery_live_update_v2"
        const val NOTIFICATION_ID = 1
        internal const val ACTION_STOP = "com.hermes.devicepill.STOP"
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

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
        createNotificationChannel()
    }

    // LLMonitor pattern: two channels
    private fun createNotificationChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // Main channel (IMPORTANCE_LOW = 2, like LLMonitor)
        val mainChannel = NotificationChannel(CHANNEL_ID, "电池监控", 2).apply {
            description = "显示实时充电功率"
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
            setSound(null, null)
            lockscreenVisibility = 1
        }
        // Live update channel (IMPORTANCE_DEFAULT = 3)
        val liveChannel = NotificationChannel(LIVE_CHANNEL_ID, "实时活动 (灵动岛)", 3).apply {
            description = "充电时显示灵动岛风格通知"
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
            setSound(null, null)
            lockscreenVisibility = 1
        }
        nm.createNotificationChannel(mainChannel)
        nm.createNotificationChannel(liveChannel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitor()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification(null))
        startMonitor()
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null
    override fun onDestroy() {
        stopMonitor()
        super.onDestroy()
    }

    private fun stopMonitor() {
        monitorRunning = false
        batteryReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ============================================================
    // LLMonitor pattern: createInitialNotification
    // ============================================================

    @android.annotation.SuppressLint("NewApi")
    private fun buildNotification(pluggedOverride: Int?): Notification {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (scale > 0) level * 100 / scale else 0
        val plugged = pluggedOverride ?: intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val tempDeci = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempDeci / 10f
        val mv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val voltageV = mv / 1000.0

        val isCharging = plugged != 0
        val chargeType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "超级闪充"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB 充电"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线充电"
            else -> if (isCharging) "充电中" else ""
        }

        // Get power watts
        var watts = 0.0
        if (isCharging) {
            val bm = getSystemService(BATTERY_SERVICE) as? BatteryManager
            if (bm != null) {
                val raw = runCatching { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrDefault(Int.MIN_VALUE)
                if (raw != Int.MIN_VALUE) {
                    var ma = raw
                    if (abs(ma) > 10000) ma /= 1000
                    watts = (mv / 1000.0) * abs(ma) / 1000.0
                }
            }
        }

        // ── Improved notification content ──
        val title: String
        val text: String
        val subText: String

        if (isCharging) {
            if (watts >= 0.5) {
                title = "\uD83D\uDCA5 ${"%.0f".format(watts)}W $chargeType"
                text = "$pct% · ${"%.1f".format(tempC)}℃ · ${"%.1f".format(voltageV)}V"
            } else {
                title = "\uD83D\uDCA5 $chargeType"
                text = "$pct% · ${"%.1f".format(tempC)}℃"
            }
            subText = "充电中"
        } else {
            title = "${"%.1f".format(tempC)}℃ · ${"%.1f".format(voltageV)}V"
            text = "电池 $pct%"
            subText = "未充电"
        }

        // LLMonitor pattern: Framework Notification.Builder with CHANNEL_ID
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(subText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFFFFD700.toInt())
            .setContentIntent(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))

        // LLMonitor pattern: ProgressStyle + android.requestPromotedOngoing extras on API 36+
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                val progressStyle = Notification.ProgressStyle()
                    .setProgress(pct)
                builder.setStyle(progressStyle)

                // THE KEY: android.requestPromotedOngoing extras
                val bundle = Bundle()
                bundle.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
                builder.addExtras(bundle)
            } catch (_: Exception) {
                val bundle = Bundle()
                bundle.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
                builder.addExtras(bundle)
            }
        }

        return builder.build()
    }

    // ============================================================
    // Battery Monitoring (LLMonitor pattern)
    // ============================================================

    private fun startMonitor() {
        if (monitorRunning) return
        monitorRunning = true
        batteryReceiver = BatteryReceiver()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val runnable = object : Runnable {
            override fun run() {
                if (!monitorRunning) return
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(null))
                handler.postDelayed(this, 3000)
            }
        }
        handler.post(runnable)
    }

    inner class BatteryReceiver : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {}
    }
}
