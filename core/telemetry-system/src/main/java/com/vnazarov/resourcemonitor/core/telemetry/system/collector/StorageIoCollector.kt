package com.vnazarov.resourcemonitor.core.telemetry.system.collector

import android.content.Context
import android.os.StatFs
import com.vnazarov.resourcemonitor.core.telemetry.api.StorageCollector
import java.io.File
import java.io.FileOutputStream

class StorageIoCollector(
    private val context: Context? = null,
    probeDirectory: File? = null,
    storageDirectory: File? = null,
    private val probeThrottleIntervalMs: Long = PROBE_THROTTLE_INTERVAL_MS
) : StorageCollector {

    companion object {
        const val STALL_THRESHOLD_MS = 150f
        const val PROBE_THROTTLE_INTERVAL_MS = 4000L // 4.0s probe throttle window
    }

    private val targetProbeDir: File = probeDirectory
        ?: context?.cacheDir
        ?: File(System.getProperty("java.io.tmpdir") ?: ".")

    private val targetStorageDir: File = storageDirectory
        ?: context?.filesDir
        ?: File(".")

    private val probeFile = File(targetProbeDir, ".io_latency_probe")
    private val probeBuffer = ByteArray(4096)
    private var lastLatencyMs: Float = 0.5f

    override fun getTotalBytes(): Long {
        return try {
            val stat = StatFs(targetStorageDir.absolutePath)
            stat.blockCountLong * stat.blockSizeLong
        } catch (_: Exception) {
            0L
        }
    }

    override fun getAvailableBytes(): Long {
        return try {
            val stat = StatFs(targetStorageDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            0L
        }
    }

    private var lastProbeTimestampMs = 0L

    override fun getWriteLatencyMs(): Float = getWriteLatencyMs(forceProbe = false)

    fun getWriteLatencyMs(forceProbe: Boolean = false): Float {
        val now = System.currentTimeMillis()
        if (!forceProbe && now - lastProbeTimestampMs < probeThrottleIntervalMs && lastLatencyMs > 0f) {
            return lastLatencyMs
        }
        lastProbeTimestampMs = now
        return try {
            if (!targetProbeDir.exists()) {
                targetProbeDir.mkdirs()
            }
            val t0 = System.nanoTime()
            FileOutputStream(probeFile, false).use { fos ->
                fos.write(probeBuffer)
                fos.fd.sync()
            }
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000f
            lastLatencyMs = elapsedMs
            elapsedMs
        } catch (_: Exception) {
            lastLatencyMs
        }
    }

    override fun getIoWaitCycles(): Long {
        val latency = if (lastLatencyMs > 0f) lastLatencyMs else getWriteLatencyMs(forceProbe = false)
        return (latency * 10).toLong()
    }

    fun isStorageStall(): Boolean = lastLatencyMs > STALL_THRESHOLD_MS

    val lastWriteLatencyMs: Float
        get() = lastLatencyMs
}
