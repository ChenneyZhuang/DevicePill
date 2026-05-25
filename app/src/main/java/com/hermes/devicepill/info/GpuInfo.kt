package com.hermes.devicepill.info

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import java.io.File

data class GpuData(
    val renderer: String,
    val vendor: String,
    val version: String,
    val temperatureC: Float
)

object GpuInfo {

    fun read(context: Context): GpuData {
        // Read GL strings
        var renderer = "未知"
        var vendor = "未知"
        var version = "未知"

        try {
            val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                val ver = IntArray(2)
                EGL14.eglInitialize(eglDisplay, ver, 0, ver, 1)

                val configs = arrayOfNulls<EGLConfig>(1)
                val num = IntArray(1)
                EGL14.eglChooseConfig(eglDisplay, intArrayOf(
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE
                ), 0, configs, 0, 1, num, 0)

                if (num[0] > 0 && configs[0] != null) {
                    val ctx = EGL14.eglCreateContext(eglDisplay, configs[0],
                        EGL14.EGL_NO_CONTEXT,
                        intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)

                    if (ctx != EGL14.EGL_NO_CONTEXT) {
                        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, ctx)
                        renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "未知"
                        vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "未知"
                        version = GLES20.glGetString(GLES20.GL_VERSION) ?: "未知"
                        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                        EGL14.eglDestroyContext(eglDisplay, ctx)
                    }
                }
                EGL14.eglTerminate(eglDisplay)
            }
        } catch (_: Exception) { }

        // GPU temperature from /sys/class/thermal/
        var temp = -1f
        try {
            val thermalDir = File("/sys/class/thermal")
            if (thermalDir.exists() && thermalDir.isDirectory) {
                thermalDir.listFiles()?.forEach { zone ->
                    try {
                        val typeFile = File(zone, "type")
                        if (!typeFile.exists()) return@forEach
                        val type = typeFile.readText().trim().lowercase()
                        if (type.contains("gpu")) {
                            val tempFile = File(zone, "temp")
                            if (tempFile.exists()) {
                                val raw = tempFile.readText().trim().toLongOrNull() ?: return@forEach
                                val celsius = if (raw > 1000) raw / 1000f else raw.toFloat()
                                temp = celsius
                            }
                        }
                    } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }

        return GpuData(renderer, vendor, version, temp)
    }
}
