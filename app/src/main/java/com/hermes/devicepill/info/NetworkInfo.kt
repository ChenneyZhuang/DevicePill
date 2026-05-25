package com.hermes.devicepill.info

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager

data class NetworkData(
    val isConnected: Boolean,
    val type: String,           // "Wi-Fi" / "5G" / "4G" / "无网络"
    val wifiSsid: String,       // "" if not Wi-Fi
    val signalStrength: Int,    // 0-100 or -1
    val ipAddress: String       // "" if unavailable
)

object NetworkInfo {

    fun read(context: Context): NetworkData {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }

        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkData(false, "无网络", "", -1, "")
        }

        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> getCellularType(caps)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
            else -> "其他"
        }

        // Wi-Fi SSID
        val ssid = try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
                    as? android.net.wifi.WifiManager
            val info = wifiManager?.connectionInfo
            info?.ssid?.trim('"') ?: ""
        } catch (_: Exception) { "" }

        // Signal strength (rough)
        var signal = -1
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val si = tm?.signalStrength
            if (si != null) {
                signal = si.level * 25  // level is 0-4, scale to 0-100
            }
        } catch (_: Exception) { }

        // IP
        val ip = try {
            java.net.NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(":") != true }
                ?.hostAddress ?: ""
        } catch (_: Exception) { "" }

        return NetworkData(true, type, ssid, signal, ip)
    }

    private fun getCellularType(caps: NetworkCapabilities): String {
        return when {
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> "5G"
            else -> "4G"
        }
    }
}
