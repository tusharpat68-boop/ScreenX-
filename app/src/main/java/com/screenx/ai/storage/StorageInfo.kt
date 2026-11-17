package com.screenx.ai.storage

import android.os.Environment
import android.os.StatFs

data class StorageSnapshot(val freeBytes: Long, val totalBytes: Long)

object StorageInfo {
    /** Reads free/total space of the device's internal data partition. */
    fun read(): StorageSnapshot {
        val stat = StatFs(Environment.getDataDirectory().path)
        return StorageSnapshot(
            freeBytes = stat.availableBytes,
            totalBytes = stat.totalBytes
        )
    }
}
