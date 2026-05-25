package com.hermes.devicepill.info

import android.os.Build

data class SystemData(
    val manufacturer: String,
    val model: String,
    val device: String,
    val product: String,
    val board: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val securityPatch: String,
    val kernelVersion: String,
    val osName: String,        // "ColorOS", "OxygenOS" etc
    val osVersion: String,     // "16.0"
    val buildId: String
)

object SystemInfo {

    fun read(): SystemData {
        val kernel = readKernelVersion()
        val osName = detectOs()

        return SystemData(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            board = Build.BOARD,
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH ?: "未知",
            kernelVersion = kernel,
            osName = osName,
            osVersion = getOsVersion(osName),
            buildId = Build.ID
        )
    }

    /** Read /proc/version for kernel info */
    private fun readKernelVersion(): String {
        return try {
            java.io.RandomAccessFile("/proc/version", "r").use { it.readLine().trim() }
                .substringBefore("(").trim()
        } catch (_: Exception) {
            System.getProperty("os.version") ?: "未知"
        }
    }

    /** Detect ColorOS / OxygenOS / etc */
    private fun detectOs(): String {
        // ColorOS exposes this in build properties
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            val colorOsVersion = method.invoke(null, "ro.build.version.opporom", "") as String
            if (colorOsVersion.isNotEmpty()) "ColorOS" else "Android"
        } catch (_: Exception) { "Android" }
    }

    private fun getOsVersion(osName: String): String {
        if (osName == "ColorOS") {
            return try {
                val clazz = Class.forName("android.os.SystemProperties")
                val method = clazz.getMethod("get", String::class.java, String::class.java)
                method.invoke(null, "ro.build.version.opporom", "") as String
            } catch (_: Exception) { "" }
        }
        return ""
    }
}
