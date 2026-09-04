package com.vnazarov.resourcemonitor.core.telemetry.system

import com.vnazarov.resourcemonitor.core.telemetry.system.collector.CpuFreqCollector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CpuFreqCollectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun calculateLoad_whenFrequenciesAtMinimum_returnsZeroPercent() {
        val cpuDir = tempFolder.newFolder("cpu_min")
        createCore(cpuDir, 0, minKhz = 300_000, maxKhz = 1_000_000, curKhz = 300_000)
        createCore(cpuDir, 1, minKhz = 400_000, maxKhz = 2_000_000, curKhz = 400_000)

        val collector = CpuFreqCollector(cpuBaseDir = cpuDir)
        assertEquals(0f, collector.getLoadPercentage(), 0.01f)
    }

    @Test
    fun calculateLoad_whenFrequenciesAtMaximum_returnsHundredPercent() {
        val cpuDir = tempFolder.newFolder("cpu_max")
        createCore(cpuDir, 0, minKhz = 300_000, maxKhz = 1_000_000, curKhz = 1_000_000)
        createCore(cpuDir, 1, minKhz = 400_000, maxKhz = 2_000_000, curKhz = 2_000_000)

        val collector = CpuFreqCollector(cpuBaseDir = cpuDir)
        assertEquals(100f, collector.getLoadPercentage(), 0.01f)
    }

    @Test
    fun calculateLoad_whenFrequenciesHalfway_returnsFiftyPercent() {
        val cpuDir = tempFolder.newFolder("cpu_half")
        createCore(cpuDir, 0, minKhz = 300_000, maxKhz = 1_000_000, curKhz = 650_000) // delta = 350,000 / 700,000
        createCore(cpuDir, 1, minKhz = 500_000, maxKhz = 1_500_000, curKhz = 1_000_000) // delta = 500,000 / 1,000,000

        val collector = CpuFreqCollector(cpuBaseDir = cpuDir)
        assertEquals(50f, collector.getLoadPercentage(), 0.01f)
    }

    @Test
    fun getFrequenciesKhz_returnsCurFrequenciesForAllDiscoveredCores() {
        val cpuDir = tempFolder.newFolder("cpu_multi")
        createCore(cpuDir, 0, minKhz = 300_000, maxKhz = 1_000_000, curKhz = 600_000)
        createCore(cpuDir, 1, minKhz = 400_000, maxKhz = 1_800_000, curKhz = 900_000)
        createCore(cpuDir, 2, minKhz = 500_000, maxKhz = 2_400_000, curKhz = 1_500_000)

        val collector = CpuFreqCollector(cpuBaseDir = cpuDir)
        val freqs = collector.getFrequenciesKhz()

        assertEquals(3, freqs.size)
        assertEquals(600_000L, freqs[0])
        assertEquals(900_000L, freqs[1])
        assertEquals(1_500_000L, freqs[2])
        assertEquals(2_400_000L, collector.getMaxFrequencyKhz())
    }

    @Test
    fun getTemperatureMilliC_whenCpuThermalZoneExists_readsTemperature() {
        val cpuDir = tempFolder.newFolder("cpu_thermal")
        val thermalDir = tempFolder.newFolder("thermal")

        val zone0 = File(thermalDir, "thermal_zone0").apply { mkdirs() }
        File(zone0, "type").writeText("soc_thermal\n")
        File(zone0, "temp").writeText("42500\n")

        val collector = CpuFreqCollector(cpuBaseDir = cpuDir, thermalBaseDir = thermalDir)
        assertEquals(42500, collector.getTemperatureMilliC())
    }

    @Test
    fun emptyOrMissingDirectory_gracefulFallback() {
        val emptyDir = tempFolder.newFolder("cpu_empty")
        val collector = CpuFreqCollector(cpuBaseDir = emptyDir)

        assertEquals(0f, collector.getLoadPercentage(), 0.001f)
        assertTrue(collector.getFrequenciesKhz().isEmpty())
        assertEquals(3_000_000L, collector.getMaxFrequencyKhz())
        assertNull(collector.getTemperatureMilliC())
    }

    private fun createCore(baseDir: File, index: Int, minKhz: Long, maxKhz: Long, curKhz: Long) {
        val coreDir = File(baseDir, "cpu$index/cpufreq").apply { mkdirs() }
        File(coreDir, "cpuinfo_min_freq").writeText("$minKhz\n")
        File(coreDir, "cpuinfo_max_freq").writeText("$maxKhz\n")
        File(coreDir, "scaling_cur_freq").writeText("$curKhz\n")
    }
}
