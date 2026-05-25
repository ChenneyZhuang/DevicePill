package com.hermes.devicepill.info

import android.content.Context

/**
 * One-stop aggregator — reads all device info in a single call.
 * Each module is stateless; read() is called on demand.
 */
data class DeviceSnapshot(
    val cpu: CpuData,
    val battery: BatteryData,
    val memory: MemoryData,
    val storage: StorageData,
    val gpu: GpuData,
    val display: DisplayData,
    val system: SystemData,
    val network: NetworkData
)

object DeviceInfo {

    fun snapshot(context: Context): DeviceSnapshot {
        return DeviceSnapshot(
            cpu = CpuInfo.read(context),
            battery = BatteryInfo.read(context),
            memory = MemoryInfo.read(context),
            storage = StorageInfo.read(context),
            gpu = GpuInfo.read(context),
            display = DisplayInfo.read(context),
            system = SystemInfo.read(),
            network = NetworkInfo.read(context)
        )
    }
}
