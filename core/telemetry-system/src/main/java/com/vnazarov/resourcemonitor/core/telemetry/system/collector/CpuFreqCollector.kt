package com.vnazarov.resourcemonitor.core.telemetry.system.collector

import android.content.Context
import com.vnazarov.resourcemonitor.core.telemetry.api.CpuCollector
import java.io.File

class CpuFreqCollector(
    private val context: Context? = null,
    private val cpuBaseDir: File = File("/sys/devices/system/cpu"),
    private val thermalBaseDir: File = File("/sys/class/thermal")
) : CpuCollector {

    private data class CoreSpec(
        val coreIndex: Int,
        val curFreqFile: File,
        val minFreqFile: File,
        val maxFreqFile: File,
        val minFreqKhz: Long,
        val maxFreqKhz: Long
    )

    private val cores: List<CoreSpec> = discoverCores()
    private val totalFreqRange: Long = cores.sumOf { (it.maxFreqKhz - it.minFreqKhz).coerceAtLeast(1L) }.coerceAtLeast(1L)
    private val systemMaxFreqKhz: Long = cores.maxOfOrNull { it.maxFreqKhz } ?: 3_000_000L

    private fun discoverCores(): List<CoreSpec> {
        val discovered = mutableListOf<CoreSpec>()
        if (!cpuBaseDir.exists() || !cpuBaseDir.isDirectory) {
            return discovered
        }

        var index = 0
        while (true) {
            val coreDir = File(cpuBaseDir, "cpu$index")
            if (!coreDir.exists() || !coreDir.isDirectory) break

            val cpufreqDir = File(coreDir, "cpufreq")
            val curFreqFile = File(cpufreqDir, "scaling_cur_freq")
            val minFreqFile = File(cpufreqDir, "cpuinfo_min_freq")
            val maxFreqFile = File(cpufreqDir, "cpuinfo_max_freq")

            val minFreq = readLong(minFreqFile) ?: 300_000L
            val maxFreq = readLong(maxFreqFile) ?: 2_500_000L

            discovered.add(
                CoreSpec(
                    coreIndex = index,
                    curFreqFile = curFreqFile,
                    minFreqFile = minFreqFile,
                    maxFreqFile = maxFreqFile,
                    minFreqKhz = minFreq,
                    maxFreqKhz = maxFreq.coerceAtLeast(minFreq + 1L)
                )
            )
            index++
        }
        return discovered
    }

    private var cachedFreqsTimestamp = 0L
    private var cachedFreqs: List<Long> = emptyList()
    private var cachedLoadPercentage: Float = 0f

    @Synchronized
    private fun sampleFrequencies() {
        val now = System.currentTimeMillis()
        if (now - cachedFreqsTimestamp < 200L && cachedFreqs.isNotEmpty()) return

        if (cores.isEmpty()) {
            cachedFreqs = emptyList()
            cachedLoadPercentage = 0f
            cachedFreqsTimestamp = now
            return
        }

        var currentDeltaSum = 0L
        val freqs = mutableListOf<Long>()
        for (core in cores) {
            val curFreq = readLong(core.curFreqFile) ?: core.minFreqKhz
            freqs.add(curFreq)
            val delta = (curFreq - core.minFreqKhz).coerceAtLeast(0L)
            currentDeltaSum += delta
        }

        val ratio = (currentDeltaSum.toFloat() / totalFreqRange.toFloat()).coerceIn(0f, 1f)
        cachedLoadPercentage = ratio * 100f
        cachedFreqs = freqs
        cachedFreqsTimestamp = now
    }

    override fun getLoadPercentage(): Float {
        sampleFrequencies()
        return cachedLoadPercentage
    }

    override fun getFrequenciesKhz(): List<Long> {
        sampleFrequencies()
        return cachedFreqs
    }

    override fun getMaxFrequencyKhz(): Long = systemMaxFreqKhz

    private val cpuThermalZoneFile: File? by lazy {
        if (!thermalBaseDir.exists() || !thermalBaseDir.isDirectory || !thermalBaseDir.canRead()) return@lazy null
        val files = thermalBaseDir.listFiles { file ->
            file.name.startsWith("thermal_zone")
        } ?: return@lazy null

        for (zone in files) {
            val typeFile = File(zone, "type")
            val type = readString(typeFile)?.lowercase() ?: ""
            if (type.contains("cpu") || type.contains("soc") || type.contains("cluster")) {
                return@lazy File(zone, "temp")
            }
        }
        null
    }

    override fun getTemperatureMilliC(): Int? {
        val tempFile = cpuThermalZoneFile ?: return null
        val temp = readLong(tempFile)?.toInt() ?: return null
        return if (temp in 1..999) temp * 1000 else if (temp > 0) temp else null
    }

    private fun readLong(file: File): Long? {
        return try {
            if (file.exists() && file.canRead()) {
                file.bufferedReader().use { it.readLine()?.trim()?.toLongOrNull() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readString(file: File): String? {
        return try {
            if (file.exists() && file.canRead()) {
                file.bufferedReader().use { it.readLine()?.trim() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
