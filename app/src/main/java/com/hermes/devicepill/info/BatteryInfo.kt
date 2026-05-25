package com.hermes.devicepill.info

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

data class BatteryData(
    val levelPercent: Int,
    val voltageV: Double,
    val temperatureC: Float,
    val currentMa: Int,
    val powerW: Double,
    val isCharging: Boolean,
    val chargeType: String,      // "USB" / "AC" / "无线" / "未充电"
    val health: String,          // "良好" / "过热" / "低温" / "损坏"
    val status: String,          // "充电中" / "已充满" / "放电中"
    val capacityRemainingMah: Long,
    val capacityTotalMah: Long
)

object BatteryInfo {

    fun read(context: Context): BatteryData {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return BatteryData(0, 0.0, 0f, 0, 0.0, false, "", "", "", 0, 0)

        // Level
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val levelPct = if (scale > 0) level * 100 / scale else 0

        // Voltage (mV → V)
        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val voltage = voltageMv / 1000.0

        // Temperature (deci-C → °C)
        val tempDeci = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val temp = tempDeci / 10f

        // Status
        val statusCode = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val isCharging = statusCode == BatteryManager.BATTERY_STATUS_CHARGING || statusCode == BatteryManager.BATTERY_STATUS_FULL
        val statusText = when (statusCode) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
            BatteryManager.BATTERY_STATUS_FULL -> "已充满"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
            else -> "未知"
        }

        // Plug type
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val chargeType = when {
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "快速充电"
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB供电"
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "无线充电"
            isCharging -> "充电中"
            else -> "未充电"
        }

        // Health
        val healthCode = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val health = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
            BatteryManager.BATTERY_HEALTH_COLD -> "低温"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
            BatteryManager.BATTERY_HEALTH_DEAD -> "损坏"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
            else -> "未知"
        }

        // Current (mA) and Power (W)
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        var currentMa = 0
        if (bm != null) {
            val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (raw != Int.MIN_VALUE) {
                currentMa = raw
                // Some devices report µA, normalize to mA
                if (kotlin.math.abs(currentMa) > 10000) currentMa /= 1000
            }
        }
        val power = if (isCharging) voltage * kotlin.math.abs(currentMa) / 1000.0 else 0.0

        // Capacity
        var capRemaining = 0L
        var capTotal = 0L
        if (bm != null) {
            val chargeCounter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            if (chargeCounter != Long.MIN_VALUE) {
                capRemaining = chargeCounter / 1000  // µAh → mAh
                if (levelPct > 0) capTotal = capRemaining * 100 / levelPct
            }
            if (capRemaining == 0L) {
                val cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (cap != Int.MIN_VALUE) {
                    capRemaining = cap.toLong()
                    if (levelPct > 0) capTotal = capRemaining * 100 / levelPct
                }
            }
        }

        return BatteryData(
            levelPercent = levelPct,
            voltageV = voltage,
            temperatureC = temp,
            currentMa = currentMa,
            powerW = power,
            isCharging = isCharging,
            chargeType = chargeType,
            health = health,
            status = statusText,
            capacityRemainingMah = capRemaining,
            capacityTotalMah = capTotal
        )
    }
}
