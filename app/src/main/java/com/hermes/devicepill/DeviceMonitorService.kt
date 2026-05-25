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
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Charging Island — 金标充电悬浮窗
 *
 * Based on MaterialYou-Dynamic-Island pattern:
 * - WindowManager overlay near camera cutout
 * - BatteryReceiver for real-time updates
 * - Foreground service to stay alive
 */
class DeviceMonitorService : Service() {

    private var windowManager: WindowManager? = null
    private var pillView: LinearLayout? = null
    private var batteryReceiver: BatteryReceiver? = null

    companion object {
        const val CHANNEL_ID = "charging_island_bg"
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
        val channel = NotificationChannel(CHANNEL_ID, "充电岛后台",
            NotificationManager.IMPORTANCE_MIN).apply {
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

        val notif = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("充电岛运行中")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notif)

        createChargingIsland()
        startBatteryMonitoring()

        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        stopChargingIsland()
        super.onDestroy()
    }

    // ============================================================
    // Floating Window
    // ============================================================

    private fun createChargingIsland() {
        if (pillView != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        pillView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xE6000000.toInt())
                cornerRadius = dp(20).toFloat()
            }
            tag = "pill"
        }

        // Power text (left)
        pillView!!.addView(TextView(this).apply {
            id = 1
            text = ""
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, dp(6), 0)
        })

        // Percent text (right, gold)
        pillView!!.addView(TextView(this).apply {
            id = 2
            text = "0%"
            textSize = 11f
            setTextColor(0xFFFFD700.toInt())
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(10)
        }

        windowManager?.addView(pillView, params)
    }

    private fun updatePill(powerW: Double, level: Int, isCharging: Boolean, tempC: Float) {
        val pill = pillView ?: return
        val powerTv = pill.findViewById<TextView>(1) ?: return
        val pctTv = pill.findViewById<TextView>(2) ?: return

        if (isCharging && powerW > 0.5) {
            powerTv.text = "⚡ ${String.format("%.0f", powerW)}W"
        } else if (isCharging) {
            powerTv.text = "⚡"
        } else {
            powerTv.text = ""
        }

        val tempStr = if (tempC > 0 && isCharging) " ${String.format("%.0f", tempC)}°" else ""
        pctTv.text = "${level}%$tempStr"
    }

    private fun stopChargingIsland() {
        pillView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        pillView = null
        windowManager = null
        batteryReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        batteryReceiver = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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

            // Hide pill if not charging and battery is above 95%
            if (!isCharging) {
                pillView?.visibility = android.view.View.GONE
            } else {
                pillView?.visibility = android.view.View.VISIBLE
                updatePill(powerW, pct, true, temp)
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
