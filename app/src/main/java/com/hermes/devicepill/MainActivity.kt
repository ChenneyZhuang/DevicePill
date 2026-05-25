package com.hermes.devicepill

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var toggleBtn: Button
    private lateinit var notifBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        toggleBtn = findViewById(R.id.toggle_btn)
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
        val hasNotif = if (Build.VERSION.SDK_INT >= 33)
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        else true
        val isRunning = DeviceMonitorService.isRunning(this)

        if (!hasNotif && Build.VERSION.SDK_INT >= 33) {
            notifBtn.isEnabled = true
            notifBtn.text = "🔔 授权通知权限"
            notifBtn.visibility = android.view.View.VISIBLE
            toggleBtn.isEnabled = false
        } else {
            notifBtn.visibility = android.view.View.GONE
            toggleBtn.isEnabled = true
        }

        if (isRunning) {
            toggleBtn.text = "⏹ 停止"
            statusText.text = "● 流体云运行中\n插上充电器即可在摄像头旁看到充电信息"
            statusText.setTextColor(0xFF3FB950.toInt())
        } else {
            toggleBtn.text = "▶ 启动"
            statusText.text = "○ 已停止"
            statusText.setTextColor(0xFF8B949E.toInt())
        }
    }
}
