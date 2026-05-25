package com.hermes.devicepill.info

import android.app.ActivityManager
import android.content.Context

data class MemoryData(
    val totalMb: Long,
    val availableMb: Long,
    val usedMb: Long,
    val usagePercent: Int,
    val isLowMemory: Boolean,
    val thresholdMb: Long    // low memory threshold
)

object MemoryInfo {

    fun read(context: Context): MemoryData {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availableMb = memInfo.availMem / (1024 * 1024)
        val usedMb = totalMb - availableMb
        val usagePct = if (totalMb > 0) (usedMb * 100 / totalMb).toInt() else 0
        val thresholdMb = memInfo.threshold / (1024 * 1024)

        return MemoryData(
            totalMb = totalMb,
            availableMb = availableMb,
            usedMb = usedMb,
            usagePercent = usagePct,
            isLowMemory = memInfo.lowMemory,
            thresholdMb = thresholdMb
        )
    }
}
