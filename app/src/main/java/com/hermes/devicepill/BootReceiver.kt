package com.hermes.devicepill

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Auto-restart monitoring after device boot if it was running before.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("device_pill_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("monitor_was_running", false)) {
                DeviceMonitorService.start(context)
            }
        }
    }
}
