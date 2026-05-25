package com.hermes.devicepill.info

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.WindowManager

data class DisplayData(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val density: Float,
    val refreshRateHz: Float,
    val brightness: Int,          // 0-255 or -1
    val hdrCapabilities: String   // "HDR10, HLG" or ""
)

object DisplayInfo {

    fun read(context: Context): DisplayData {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

        val display = wm.defaultDisplay ?: return DisplayData(0, 0, 0, 0f, 0f, -1, "")

        // Resolution
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        // Density
        val densityDpi = metrics.densityDpi
        val density = metrics.density

        // Refresh rate
        var refreshRate = display.refreshRate
        // Try getting from DisplayManager for more accuracy
        try {
            val dmDisplay = dm.getDisplay(display.displayId)
            val mode = dmDisplay?.mode
            if (mode != null) refreshRate = mode.refreshRate
        } catch (_: Exception) { }

        // Brightness (may require system-level permission on some devices)
        var brightness = -1
        try {
            brightness = android.provider.Settings.System.getInt(
                context.contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS
            )
        } catch (_: Exception) { }

        // HDR
        val hdrTypes = display.hdrCapabilities?.supportedHdrTypes?.joinToString(", ") {
            hdrTypeToString(it)
        } ?: ""

        return DisplayData(width, height, densityDpi, density, refreshRate, brightness, hdrTypes)
    }

    private fun hdrTypeToString(type: Int): String = when (type) {
        1 -> "Dolby Vision"
        2 -> "HDR10"
        3 -> "HLG"
        4 -> "HDR10+"
        else -> "HDR"
    }
}
