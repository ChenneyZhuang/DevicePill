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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlin.math.abs

/**
 * ColorOS 16 流体云 — 基于 islanders 项目验证的实现
 *
 * Key pattern (verified working):
 *   1. Notification.Builder (framework, NOT Compat)
 *   2. IMPORTANCE_HIGH channel
 *   3. setShortCriticalText("6.3W") — the status chip text!
 *   4. setRequestPromotedOngoing(true) on API 36+
 *   5. ProgressStyle via reflection for battery level
 *   6. CATEGORY_STATUS, setOngoing(true), setOnlyAlertOnce(true)
 */
class DeviceMonitorService : Service() {

    private var monitorRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private var batteryReceiver: BatteryReceiver? = null

    companion object {
        const val CHANNEL_ID = "island_charging"
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
        // IMPORTANCE_HIGH — required for status chip promotion
        val channel = NotificationChannel(CHANNEL_ID, "充电状态",
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = "实时充电功率与电量"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitor()
            return START_NOT_STICKY
        }
        // Minimal foreground just to keep alive (separate from the LiveUpdate notification)
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val fgNotif = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("充电岛")
            .setContentText("监控中")
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
        startForeground(NOTIFICATION_ID, fgNotif)

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
        batteryReceiver = null
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ============================================================
    // Battery Polling (like islanders BatteryMonitor)
    // ============================================================

    private fun startMonitor() {
        if (monitorRunning) return
        monitorRunning = true

        batteryReceiver = BatteryReceiver()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val poster = LiveUpdatePoster(this)

        val runnable = object : Runnable {
            override fun run() {
                if (!monitorRunning) return
                val lastIntent = batteryReceiver?.lastIntent ?: return
                val snap = buildSnap(bm, lastIntent)
                poster.postCharging(snap)
                handler.postDelayed(this, 3000)
            }
        }
        handler.post(runnable)
    }

    inner class BatteryReceiver : BroadcastReceiver() {
        var lastIntent: Intent? = null
        override fun onReceive(c: Context?, intent: Intent?) { lastIntent = intent }
    }

    private fun buildSnap(bm: BatteryManager, intent: Intent): BatterySnapshot {
        val currentUa = runCatching { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrDefault(Int.MIN_VALUE)
        val level = runCatching { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }.getOrDefault(0)
        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val tempDeci = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val statusInt = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val isCharging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING || statusInt == BatteryManager.BATTERY_STATUS_FULL

        val currentMa = if (currentUa != Int.MIN_VALUE) currentUa / 1000.0 else 0.0
        val voltageV = if (voltageMv > 0) voltageMv / 1000.0 else 0.0
        val watts = abs(currentMa / 1000.0) * voltageV
        val tempC = if (tempDeci != Int.MIN_VALUE) tempDeci / 10.0 else 0.0

        val protocol = when {
            !isCharging -> "Battery"
            watts >= 80 -> "SuperVOOC"
            watts >= 50 -> "VOOC"
            watts >= 25 -> "FastCharge"
            watts >= 10 -> "QuickCharge"
            plugged != 0 -> "Charging"
            else -> "Battery"
        }

        return BatterySnapshot(watts, level.coerceIn(0, 100), protocol, currentMa, voltageV, tempC, isCharging)
    }

    data class BatterySnapshot(
        val watts: Double, val level: Int, val protocol: String,
        val currentMa: Double, val voltageV: Double, val tempC: Double,
        val isCharging: Boolean,
    )
}

/**
 * islanders LiveUpdatePoster port — builds and posts the fluid cloud notification.
 *
 * Key implementation details from islanders:
 *   - Framework Notification.Builder (NOT Compat)
 *   - setShortCriticalText("6.3W") — NOT null! This is the status chip text!
 *   - setRequestPromotedOngoing(true) on API 36+
 *   - ProgressStyle via reflection
 *   - CATEGORY_STATUS (not CATEGORY_PROGRESS)
 *   - setOngoing + setOnlyAlertOnce + setShowWhen(false)
 */
class LiveUpdatePoster(private val context: Context) {
    private val nm by lazy { context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    fun postCharging(snap: DeviceMonitorService.BatterySnapshot) {
        if (!snap.isCharging) {
            // Don't post if not charging — let it disappear from fluid cloud
            nm.cancel(DeviceMonitorService.NOTIFICATION_ID)
            return
        }

        val short = if (snap.watts >= 0.5) "${"%.1f".format(snap.watts)}W" else "⚡"
        val title = "${snap.protocol} · ${"%.1f".format(snap.watts)} W"
        val text  = "${snap.level}%  ·  ${"%.2f".format(snap.currentMa / 1000.0)} A  ·  ${"%.2f".format(snap.voltageV)} V"
        val tempStr = if (snap.tempC > 0) " · ${"%.0f".format(snap.tempC)}°C" else ""
        val fullText = text + tempStr

        val pi = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        // Framework Notification.Builder (NOT Compat) — islanders pattern
        val b = Notification.Builder(context, DeviceMonitorService.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(title)
            .setContentText(fullText)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)

        if (Build.VERSION.SDK_INT >= 36) {
            // setShortCriticalText — the text on the fluid cloud status chip!
            runCatching {
                Notification.Builder::class.java
                    .getMethod("setShortCriticalText", CharSequence::class.java)
                    .invoke(b, short)
            }
            // setRequestPromotedOngoing(true) — trigger fluid cloud promotion
            runCatching {
                Notification.Builder::class.java
                    .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                    .invoke(b, true)
            }
            // ProgressStyle for battery level bar
            runCatching {
                val styleClass = Class.forName("android.app.Notification\$ProgressStyle")
                val style = styleClass.getConstructor().newInstance()
                styleClass.getMethod("setProgress", Int::class.javaPrimitiveType).invoke(style, snap.level)
                styleClass.getMethod("setProgressMax", Int::class.javaPrimitiveType).invoke(style, 100)
                Notification.Builder::class.java
                    .getMethod("setStyle", Notification.Style::class.java)
                    .invoke(b, style as Notification.Style)
            }
        }

        nm.notify(DeviceMonitorService.NOTIFICATION_ID, b.build())
    }
}
