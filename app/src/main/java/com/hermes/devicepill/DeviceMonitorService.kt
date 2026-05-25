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
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.*

/**
 * Floating pill overlay near camera cutout — "金标充电岛"
 *
 * Uses WindowManager TYPE_APPLICATION_OVERLAY to draw a pill
 * in the status bar area near the front camera cutout.
 * Requires SYSTEM_ALERT_WINDOW permission.
 *
 * This is NOT a notification-based approach.
 * The notification is only used to keep the service alive.
 */
class DeviceMonitorService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "charging_island_bg"
        const val NOTIFICATION_ID = 2001
        internal const val ACTION_STOP = "com.hermes.devicepill.STOP"

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
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Minimal notification channel just to keep service alive
        val channel = NotificationChannel(
            CHANNEL_ID,
            "充电岛后台",
            NotificationManager.IMPORTANCE_MIN  // Silent, no heads-up
        ).apply {
            description = "保持充电岛悬浮窗运行"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopChargingIsland()
            return START_NOT_STICKY
        }

        // Minimal foreground notification — just to keep service alive
        val minimalNotif = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("充电岛运行中")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, minimalNotif)

        // Create the floating pill overlay
        createChargingIsland()
        startBatteryMonitoring()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopChargingIsland()
        scope.cancel()
        super.onDestroy()
    }

    // ============================================================
    // Floating Pill (WindowManager overlay)
    // ============================================================

    private fun createChargingIsland() {
        if (floatingView != null) return

        // Build the pill layout programmatically (lightweight, no XML needed)
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#E6000000"))  // Semi-transparent black
                cornerRadius = dp(20).toFloat()
            }
        }

        val powerText = TextView(this).apply {
            id = 1
            text = "⚡ 0W"
            textSize = 11f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dp(6), 0)
        }

        val percentText = TextView(this).apply {
            id = 2
            text = "0%"
            textSize = 11f
            setTextColor(Color.parseColor("#FFD700"))  // Gold
            typeface = Typeface.DEFAULT_BOLD
        }

        pill.addView(powerText)
        pill.addView(percentText)
        floatingView = pill

        // Position near top center (camera cutout area)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // Position below status bar, near camera cutout
            y = dp(8)
        }

        windowManager.addView(floatingView, params)
    }

    private fun updatePill(powerW: Double, levelPercent: Int, isCharging: Boolean, tempC: Float) {
        val pill = floatingView as? LinearLayout ?: return
        val powerView = pill.findViewById<TextView>(1) ?: return
        val percentView = pill.findViewById<TextView>(2) ?: return

        if (isCharging && powerW > 0) {
            powerView.text = "⚡ ${String.format("%.0f", powerW)}W"
            powerView.visibility = View.VISIBLE
        } else if (isCharging) {
            powerView.text = "⚡ 充电"
            powerView.visibility = View.VISIBLE
        } else {
            powerView.visibility = View.GONE
        }

        percentView.text = "${levelPercent}%"
        if (tempC > 0 && isCharging) {
            percentView.text = "${levelPercent}% ${String.format("%.0f", tempC)}°"
        }
    }

    private fun removeChargingIsland() {
        floatingView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        floatingView = null
    }

    private fun stopChargingIsland() {
        removeChargingIsland()
        batteryReceiver?.let { unregisterReceiver(it) }
        batteryReceiver = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ============================================================
    // Battery Monitoring
    // ============================================================

    private fun startBatteryMonitoring() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent ?: return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val levelPct = if (scale > 0) level * 100 / scale else 0
                val tempDeci = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                val temp = tempDeci / 10f
                val statusCode = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                val isCharging = statusCode == BatteryManager.BATTERY_STATUS_CHARGING ||
                                 statusCode == BatteryManager.BATTERY_STATUS_FULL

                // Get current/power
                var powerW = 0.0
                if (isCharging) {
                    val bm = getSystemService(BATTERY_SERVICE) as? BatteryManager
                    if (bm != null) {
                        val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                        if (raw != Int.MIN_VALUE) {
                            var currentMa = raw
                            if (kotlin.math.abs(currentMa) > 10000) currentMa /= 1000
                            val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                            val voltageV = voltageMv / 1000.0
                            powerW = voltageV * kotlin.math.abs(currentMa) / 1000.0
                        }
                    }
                }

                updatePill(powerW, levelPct, isCharging, temp)
            }
        }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
