package com.hermes.devicepill

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var toggleBtn: Button
    private lateinit var overlayBtn: Button
    private lateinit var notifBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        toggleBtn = findViewById(R.id.toggle_btn)
        overlayBtn = findViewById(R.id.overlay_btn)
        notifBtn = findViewById(R.id.notif_btn)

        refreshUI()

        toggleBtn.setOnClickListener {
            if (DeviceMonitorService.isRunning(this)) {
                DeviceMonitorService.stop(this)
            } else {
                DeviceMonitorService.start(this)
            }
            refreshUI()
        }

        overlayBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }

        notifBtn.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    private fun refreshUI() {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasNotif = if (Build.VERSION.SDK_INT >= 33)
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        else true
        val isRunning = DeviceMonitorService.isRunning(this)

        // Overlay permission
        if (!hasOverlay) {
            overlayBtn.isEnabled = true
            overlayBtn.text = "⚡ 授权悬浮窗 (必要!)"
        } else {
            overlayBtn.isEnabled = false
            overlayBtn.text = "✅ 悬浮窗权限已授权"
        }

        // Notification permission
        if (!hasNotif && Build.VERSION.SDK_INT >= 33) {
            notifBtn.isEnabled = true
            notifBtn.text = "🔔 授权通知"
            notifBtn.visibility = android.view.View.VISIBLE
        } else {
            notifBtn.visibility = android.view.View.GONE
        }

        // Toggle button
        toggleBtn.isEnabled = hasOverlay && hasNotif
        if (isRunning) {
            toggleBtn.text = "⏹ 停止充电岛"
            statusText.text = "● 悬浮窗运行中"
            statusText.setTextColor(0xFF3FB950.toInt())
        } else {
            toggleBtn.text = "▶ 启动充电岛"
            statusText.text = "○ 已停止"
            statusText.setTextColor(0xFF8B949E.toInt())
        }
    }
}
