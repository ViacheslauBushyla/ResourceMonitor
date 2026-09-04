package com.vnazarov.resourcemonitor.core.telemetry.system.collector

import android.app.ActivityManager
import android.content.Context
import com.vnazarov.resourcemonitor.core.telemetry.api.RamCollector
import java.io.File

class RamMeminfoCollector(
    private val context: Context? = null,
    private val meminfoFile: File = File("/proc/meminfo")
) : RamCollector {

    private val activityManager: ActivityManager? by lazy {
        context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    }
    private val memoryInfo = ActivityManager.MemoryInfo()

    private var lastSampleTimestampMs = 0L
    private var cachedMemTotal = 0L
    private var cachedMemAvailable = 0L
    private var cachedSwapTotal = 0L
    private var cachedSwapFree = 0L

    @Synchronized
    private fun sampleMeminfo() {
        val now = System.currentTimeMillis()
        if (now - lastSampleTimestampMs < 1000L && cachedMemTotal > 0L) return

        try {
            if (!meminfoFile.exists() || !meminfoFile.canRead()) {
                queryActivityManager()
                cachedMemTotal = memoryInfo.totalMem
                cachedMemAvailable = memoryInfo.availMem
                lastSampleTimestampMs = now
                return
            }

            meminfoFile.bufferedReader().useLines { lines ->
                for (line in lines) {
                    when {
                        line.startsWith("MemTotal:") -> cachedMemTotal = parseKb(line, "MemTotal:")
                        line.startsWith("MemAvailable:") -> cachedMemAvailable = parseKb(line, "MemAvailable:")
                        line.startsWith("SwapTotal:") -> cachedSwapTotal = parseKb(line, "SwapTotal:")
                        line.startsWith("SwapFree:") -> cachedSwapFree = parseKb(line, "SwapFree:")
                    }
                }
            }
            lastSampleTimestampMs = now
        } catch (_: Exception) {
            queryActivityManager()
            cachedMemTotal = memoryInfo.totalMem
            cachedMemAvailable = memoryInfo.availMem
        }
    }

    private fun parseKb(line: String, prefix: String): Long {
        var idx = prefix.length
        while (idx < line.length && line[idx] == ' ') idx++
        var end = idx
        while (end < line.length && line[end] in '0'..'9') end++
        if (idx == end) return 0L
        return (line.substring(idx, end).toLongOrNull() ?: 0L) * 1024L
    }

    override fun getTotalBytes(): Long {
        sampleMeminfo()
        return if (cachedMemTotal > 0L) cachedMemTotal else memoryInfo.totalMem
    }

    override fun getAvailableBytes(): Long {
        sampleMeminfo()
        return if (cachedMemAvailable > 0L) cachedMemAvailable else memoryInfo.availMem
    }

    override fun getZramUsedBytes(): Long {
        sampleMeminfo()
        return (cachedSwapTotal - cachedSwapFree).coerceAtLeast(0L)
    }

    override fun getCompactStallsCount(): Long {
        sampleMeminfo()
        if (cachedSwapTotal <= 0L) return 0L
        val swapUsed = getZramUsedBytes()
        val swapRatio = swapUsed.toFloat() / cachedSwapTotal.toFloat()
        return if (swapRatio > 0.85f) 250L else 0L
    }

    private fun queryActivityManager() {
        try {
            activityManager?.getMemoryInfo(memoryInfo)
        } catch (_: Exception) {
            // Ignored on non-Android or missing context
        }
    }
}
