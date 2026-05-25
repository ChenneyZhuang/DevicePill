package com.hermes.devicepill.info

import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.content.Context

data class StorageData(
    val internal: PartitionInfo,
    val external: PartitionInfo?   // null if no SD card
)

data class PartitionInfo(
    val label: String,       // "内部存储" / "SD卡"
    val path: String,
    val totalMb: Long,
    val availableMb: Long,
    val usedMb: Long,
    val usagePercent: Int
)

object StorageInfo {

    fun read(context: Context): StorageData {
        // Primary shared storage (what users see as "internal storage")
        val primaryPath = Environment.getExternalStorageDirectory().absolutePath
        val internal = readPartition(primaryPath, "内部存储")
        val external = findExternal(context)
        return StorageData(internal = internal, external = external)
    }

    private fun readPartition(path: String, label: String): PartitionInfo {
        return try {
            val stat = StatFs(path)
            val blockSize = stat.blockSizeLong
            val total = stat.blockCountLong * blockSize
            val available = stat.availableBlocksLong * blockSize
            val used = total - available
            val totalMb = total / (1024 * 1024)
            val availableMb = available / (1024 * 1024)
            val usedMb = used / (1024 * 1024)
            val usagePct = if (totalMb > 0) (usedMb * 100 / totalMb).toInt() else 0
            PartitionInfo(label, path, totalMb, availableMb, usedMb, usagePct)
        } catch (_: Exception) {
            PartitionInfo(label, path, 0, 0, 0, 0)
        }
    }

    private fun findExternal(context: Context): PartitionInfo? {
        return try {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager ?: return null
            val vols = sm.storageVolumes
            // Find non-primary external volume
            val extVol = vols.firstOrNull { !it.isPrimary && it.state == "mounted" } ?: return null
            val path = extVol.directory?.absolutePath ?: return null
            readPartition(path, extVol.getDescription(context))
        } catch (_: Exception) { null }
    }
}
