package com.hermes.devicepill.info

import android.os.Build
import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * CPU information — all accessible without root.
 * - Cores/arch/frequency from /proc/cpuinfo + Runtime
 * - Usage from /proc/stat
 * - Temperature via /sys/class/thermal/ (no root required)
 */
data class CpuData(
    val model: String,
    val architecture: String,
    val cores: Int,
    val maxFrequencyMHz: Double,
    val currentFrequencyMHz: Double,
    val usagePercent: Int,
    val temperatureC: Float,
    val governor: String
)

object CpuInfo {

    fun read(context: Context): CpuData {
        val model = readCpuModel()
        val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val cores = Runtime.getRuntime().availableProcessors()
        val maxFreq = readMaxFreq(0)
        val curFreq = readCurFreq(0)
        val usage = readUsage()
        val temp = readTemperature()
        val governor = readGovernor(0)

        return CpuData(model, arch, cores, maxFreq, curFreq, usage, temp, governor)
    }

    private fun readCpuModel(): String {
        return try {
            val lines = BufferedReader(FileReader("/proc/cpuinfo")).use { it.readLines() }
            val hardware = lines.firstOrNull { it.startsWith("Hardware") }?.substringAfter(":")?.trim()
            if (!hardware.isNullOrEmpty()) return hardware
            val modelName = lines.firstOrNull { it.startsWith("model name") }?.substringAfter(":")?.trim()
            if (!modelName.isNullOrEmpty()) return modelName
            val processor = lines.firstOrNull { it.startsWith("Processor") }?.substringAfter(":")?.trim()
            if (!processor.isNullOrEmpty()) return processor
            Build.HARDWARE
        } catch (_: Exception) {
            Build.HARDWARE
        }
    }

    private fun readMaxFreq(core: Int): Double {
        return try {
            val path = "/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq"
            val khz = BufferedReader(FileReader(path)).use { it.readLine().trim().toLong() }
            khz / 1000.0
        } catch (_: Exception) { 0.0 }
    }

    private fun readCurFreq(core: Int): Double {
        return try {
            val path = "/sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq"
            val khz = BufferedReader(FileReader(path)).use { it.readLine().trim().toLong() }
            khz / 1000.0
        } catch (_: Exception) { 0.0 }
    }

    private fun readGovernor(core: Int): String {
        return try {
            val path = "/sys/devices/system/cpu/cpu$core/cpufreq/scaling_governor"
            BufferedReader(FileReader(path)).use { it.readLine().trim() }
        } catch (_: Exception) { "" }
    }

    private fun readUsage(): Int {
        return try {
            val lines = BufferedReader(FileReader("/proc/stat")).use { it.readLines() }
            val cpuLine = lines.firstOrNull { it.startsWith("cpu ") } ?: return 0
            val parts = cpuLine.split("\\s+".toRegex()).drop(1).map { it.toLong() }
            val total = parts.sum()
            val idle = parts.getOrElse(3) { 0 }
            if (total == 0L) 0 else (100 - (idle * 100 / total)).toInt()
        } catch (_: Exception) { 0 }
    }

    /**
     * Read CPU temperature from /sys/class/thermal/.
     * Looks for thermal zones with "cpu" or "tsens" in their type.
     * Works on most devices (including OPPO/OnePlus) without root.
     */
    private fun readTemperature(): Float {
        return try {
            val thermalDir = File("/sys/class/thermal")
            if (!thermalDir.exists() || !thermalDir.isDirectory) return -1f

            val temps = mutableListOf<Float>()
            thermalDir.listFiles()?.forEach { zone ->
                try {
                    val typeFile = File(zone, "type")
                    if (!typeFile.exists()) return@forEach
                    val type = typeFile.readText().trim().lowercase()
                    // Look for CPU-related thermal zones
                    if (type.contains("cpu") || type.contains("tsens") ||
                        type.contains("soc") || type.contains("ddr") ||
                        type.contains("gpu")) {
                        val tempFile = File(zone, "temp")
                        if (tempFile.exists()) {
                            val raw = tempFile.readText().trim().toLongOrNull() ?: return@forEach
                            // Normalize: values are in millidegrees C, divide by 1000
                            val celsius = if (raw > 1000) raw / 1000f else raw.toFloat()
                            temps.add(celsius)
                        }
                    }
                } catch (_: Exception) { }
            }
            if (temps.isEmpty()) -1f else temps.max()
        } catch (_: Exception) { -1f }
    }
}
