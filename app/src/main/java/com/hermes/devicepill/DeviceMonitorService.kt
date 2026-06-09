package com.hermes.devicepill

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
 * ColorOS 16 流体云 — 技术方案参考自 LLMonitor
 *
 * Features:
 *   - Fluid Cloud golden pill (ColorOS 16 native)
 *   - Real-time W / V / A / °C in notification
 *   - High-temperature alert (>42°C)
 *   - Charge complete alert (100%)
 */
class DeviceMonitorService : Service() {

    private var monitorRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private var prevCharging: Boolean? = null
    // One-shot alert flags — fire once, reset on state change
    private var tempWarned = false
    private var fullCharged = false

    companion object {
        const val CHANNEL_ID = "battery_monitor"
        const val ALERT_CHANNEL_ID = "battery_alerts"
        const val NOTIFICATION_ID = 1
        const val ALERT_NOTIFICATION_ID = 2
        internal const val ACTION_STOP = "com.hermes.devicepill.STOP"
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
        private const val PREFS_NAME = "device_pill_prefs"
        private const val PREF_MONITOR_RUNNING = "monitor_was_running"

        @Volatile private var _running = false
        fun isRunning(): Boolean = _running

        fun start(context: Context) {
            context.startForegroundService(Intent(context, DeviceMonitorService::class.java))
        }
        fun stop(context: Context) {
            context.startService(Intent(context, DeviceMonitorService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val mainChannel = NotificationChannel(CHANNEL_ID, "电池监控", NotificationManager.IMPORTANCE_LOW).apply {
            description = "显示实时充电功率"
            setShowBadge(false); enableVibration(false); enableLights(false)
            setSound(null, null); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val alertChannel = NotificationChannel(ALERT_CHANNEL_ID, "电池提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "温度过高、充电完成等提醒"
            setShowBadge(true); enableVibration(true)
        }
        nm.createNotificationChannel(mainChannel)
        nm.createNotificationChannel(alertChannel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            _running = false
            stopMonitor()
            return START_NOT_STICKY
        }
        _running = true
        // Reset one-shot alert flags on fresh start
        tempWarned = false; fullCharged = false
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_MONITOR_RUNNING, true).apply()
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
        _running = false
        monitorRunning = false
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_MONITOR_RUNNING, false).apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ============================================================
    // Build notification — readable current display (2.8A not 2800mA)
    // ============================================================

    @android.annotation.SuppressLint("NewApi")
    private fun buildNotification(batteryIntent: Intent?): Notification {
        val intent = batteryIntent
            ?: registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (scale > 0) level * 100 / scale else 0
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val tempDeci = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempDeci / 10f
        val mv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val voltageV = mv / 1000.0

        val isCharging = plugged != 0
        val chargeType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "⚡超级闪充"
            BatteryManager.BATTERY_PLUGGED_USB -> "🔌USB充电"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "🛜无线充电"
            else -> if (isCharging) "充电中" else ""
        }

        var watts = 0.0
        var currentMa = 0
        val bm = getSystemService(BATTERY_SERVICE) as? BatteryManager
        if (bm != null) {
            val raw = runCatching {
                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            }.getOrDefault(Int.MIN_VALUE)
            if (raw != Int.MIN_VALUE) {
                var ma = raw
                if (abs(ma) > 10000) ma /= 1000
                ma = if (isCharging) abs(ma) else -abs(ma)
                currentMa = ma
                watts = (mv / 1000.0) * ma / 1000.0
            }
        }

        // Format current: 2800mA → 2.8A for readability
        val currentStr = when {
            currentMa == 0 -> null
            abs(currentMa) >= 1000 -> "${"%.1f".format(abs(currentMa) / 1000f)}A"
            else -> "${abs(currentMa)}mA"
        }

        // Compact notification text: 92% · 67W · 4.2V · 2.8A · 38°C
        val parts = mutableListOf<String>()
        parts.add("$pct%")
        val wattInt = watts.toInt()
        if (isCharging && wattInt > 0) parts.add("${wattInt}W")
        if (chargeType.isNotEmpty() && isCharging) parts.add(chargeType)
        parts.add("${"%.1f".format(voltageV)}V")
        if (currentStr != null) parts.add(currentStr)
        parts.add("${"%.1f".format(tempC)}°C")
        val text = parts.joinToString(" · ")

        val title = if (isCharging) "${"%.0f".format(watts)}W" else "${"%.1f".format(tempC)}°C"
        val subText = if (isCharging) "充电中" else "未充电"

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

        if (Build.VERSION.SDK_INT >= 36) {
            try {
                val progressStyle = Notification.ProgressStyle()
                    .setProgress(pct)
                builder.setStyle(progressStyle)

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
    // Alert notifications — fire once per cycle
    // ============================================================

    private fun sendAlert(title: String, text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val alert = Notification.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(this, 1,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        nm.notify(ALERT_NOTIFICATION_ID, alert)
    }

    // ============================================================
    // Battery polling loop
    // ============================================================

    private fun startMonitor() {
        if (monitorRunning) return
        monitorRunning = true

        val runnable = object : Runnable {
            override fun run() {
                if (!monitorRunning) return
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

                val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
                val nowCharging = plugged != 0
                val pct = run {
                    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
                    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                    if (scale > 0) level * 100 / scale else 0
                }
                val tempDeci = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                val tempC = tempDeci / 10f

                // --- State transition → force fluid cloud refresh ---
                if (prevCharging != null && nowCharging != prevCharging) {
                    nm.cancel(NOTIFICATION_ID)
                    // Reset alert flags on charge/discharge transition
                    if (!nowCharging) { tempWarned = false; fullCharged = false }
                }
                prevCharging = nowCharging

                // --- One-shot alerts ---
                // Temperature warning: >42°C, once per charging session
                if (nowCharging && tempC > 42f && !tempWarned) {
                    tempWarned = true
                    sendAlert("⚠️ 电池温度偏高", "${"%.1f".format(tempC)}°C · 建议暂停充电降温")
                }
                // Charge complete: 100% with charger still plugged
                if (nowCharging && pct >= 100 && !fullCharged) {
                    fullCharged = true
                    sendAlert("✅ 充电完成", "电池已充满 · 可以拔掉充电器了")
                }

                // Update main notification
                val notif = buildNotification(intent)
                nm.notify(NOTIFICATION_ID, notif)

                handler.postDelayed(this, 3000L)
            }
        }
        handler.post(runnable)
    }
}
