package com.vnazarov.resourcemonitor.core.telemetry.system

import com.vnazarov.resourcemonitor.core.telemetry.api.CpuCollector
import com.vnazarov.resourcemonitor.core.telemetry.api.NetworkCollector
import com.vnazarov.resourcemonitor.core.telemetry.api.RamCollector
import com.vnazarov.resourcemonitor.core.telemetry.api.StorageCollector
import com.vnazarov.resourcemonitor.core.telemetry.api.ThermalCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.CpuFreqCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.NetworkTrafficCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.RamMeminfoCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.StorageIoCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.ThermalSystemCollector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class HardwareCollectorsEmpiricalChallengerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

    // =========================================================================
    // 1. CPU COLLECTOR PATHOLOGICAL INPUT TESTS
    // =========================================================================

    @Test
    fun cpuCollector_zeroCoresDiscovered_returnsZeroPercentAndSafeDefaults() {
        val emptyCpuDir = tempFolder.newFolder("cpu_empty_dir")
        val collector = CpuFreqCollector(cpuBaseDir = emptyCpuDir)

        assertEquals("Load must be 0% when no cores exist", 0f, collector.getLoadPercentage(), 0.001f)
        assertTrue("Frequencies must be empty list", collector.getFrequenciesKhz().isEmpty())
        assertEquals("Max frequency should fallback safely to 3.0 GHz", 3_000_000L, collector.getMaxFrequencyKhz())
        assertNull("Temperature should be null when thermal zone is missing", collector.getTemperatureMilliC())
    }

    @Test
    fun cpuCollector_missingScalingCurFreq_fallsBackToMinFreqSafely() {
        val cpuDir = tempFolder.newFolder("cpu_missing_cur_freq")
        createCoreWithFiles(cpuDir, index = 0, minKhz = 400_000L, maxKhz = 2_000_000L, curKhz = null)
        createCoreWithFiles(cpuDir, index = 1, minKhz = 400_000L, maxKhz = 2_000_000L, curKhz = null)

        val collector = CpuFreqCollector(cpuBaseDir = cpuDir)
        assertEquals("When scaling_cur_freq is missing, load should default to 0%", 0f, collector.getLoadPercentage(), 0.001f)
        val freqs = collector.getFrequenciesKhz()
        assertEquals(2, freqs.size)
        assertEquals(400_000L, freqs[0])
        assertEquals(400_000L, freqs[1])
    }

    @Test
    fun cpuCollector_negativeFrequencies_coercedSafelyWithoutNegativeLoadOrCrash() {
        val cpuDir = tempFolder.newFolder("cpu_negative_freqs")
        // Pathological negative frequencies in sysfs
        createCoreWithFiles(cpuDir, index = 0, minKhz = 300_000L, maxKhz = 2_400_000L, curKhz = -100_000L)

        val collector = CpuFreqCollector(cpuBaseDir = cpuDir)
        val load = collector.getLoadPercentage()
        assertTrue("Load must not be negative despite negative curFreq: $load", load >= 0f)
        assertEquals("Load should be clamped to 0%", 0f, load, 0.001f)
    }

    @Test
    fun cpuCollector_negativeMinAndMaxFrequencies_totalFreqRangeCoercedSafely() {
        val cpuDir = tempFolder.newFolder("cpu_negative_min_max")
        createCoreWithFiles(cpuDir, index = 0, minKhz = -500_000L, maxKhz = -100_000L, curKhz = -200_000L)

        val collector = CpuFreqCollector(cpuBaseDir = cpuDir)
        val load = collector.getLoadPercentage()
        assertTrue("Load must be in [0..100] despite negative limits: $load", load in 0f..100f)
        assertFalse("Load must not be NaN", load.isNaN())
        assertFalse("Load must not be infinite", load.isInfinite())
    }

    @Test
    fun cpuCollector_corruptNonNumericEntries_handlesGracefully() {
        val cpuDir = tempFolder.newFolder("cpu_corrupt_text")
        val coreDir = File(cpuDir, "cpu0/cpufreq").apply { mkdirs() }
        File(coreDir, "cpuinfo_min_freq").writeText("corrupted_min\n")
        File(coreDir, "cpuinfo_max_freq").writeText("corrupted_max\n")
        File(coreDir, "scaling_cur_freq").writeText("corrupted_cur\n")

        val collector = CpuFreqCollector(cpuBaseDir = cpuDir)
        // Corrupted min/max should fallback to 300,000 / 2,500,000
        assertEquals(2_500_000L, collector.getMaxFrequencyKhz())
        val load = collector.getLoadPercentage()
        assertEquals(0f, load, 0.001f)
    }

    @Test
    fun cpuCollector_massiveFrequencySpike_clampedToHundredPercentWithoutArithmeticOverflow() {
        val cpuDir = tempFolder.newFolder("cpu_frequency_spike")
        createCoreWithFiles(cpuDir, index = 0, minKhz = 300_000L, maxKhz = 2_000_000L, curKhz = 999_999_999_999L)

        val collector = CpuFreqCollector(cpuBaseDir = cpuDir)
        val load = collector.getLoadPercentage()
        assertEquals("Massive frequency spike must clamp to 100%", 100f, load, 0.001f)
    }

    @Test
    fun cpuCollector_pathologicalThermalZones_handlesSubzeroAndFormatVariations() {
        val cpuDir = tempFolder.newFolder("cpu_thermal_base")
        val thermalDir = tempFolder.newFolder("thermal_zones")

        // Zone 0: non-CPU thermal zone (e.g. modem)
        val z0 = File(thermalDir, "thermal_zone0").apply { mkdirs() }
        File(z0, "type").writeText("modem_thermal\n")
        File(z0, "temp").writeText("55000\n")

        // Zone 1: CPU zone with negative temperature (pathological)
        val z1 = File(thermalDir, "thermal_zone1").apply { mkdirs() }
        File(z1, "type").writeText("cpu-1-usr\n")
        File(z1, "temp").writeText("-20000\n")

        // Zone 2: CPU zone with valid reading in °C (needs x1000 conversion)
        val z2 = File(thermalDir, "thermal_zone2").apply { mkdirs() }
        File(z2, "type").writeText("soc_thermal\n")
        File(z2, "temp").writeText("48\n") // 48 °C -> 48000 m°C

        val collector = CpuFreqCollector(cpuBaseDir = cpuDir, thermalBaseDir = thermalDir)
        val temp = collector.getTemperatureMilliC()
        assertNotNull("Should extract valid reading from zone 2", temp)
        assertEquals(48000, temp)
    }

    // =========================================================================
    // 2. RAM COLLECTOR PATHOLOGICAL INPUT TESTS
    // =========================================================================

    @Test
    fun ramCollector_missingProcMeminfo_returnsZeroWithoutCrash() {
        val nonExistentFile = tempFolder.newFolder("ram_missing").resolve("non_existent_meminfo")
        val collector = RamMeminfoCollector(meminfoFile = nonExistentFile)

        assertEquals(0L, collector.getTotalBytes())
        assertEquals(0L, collector.getAvailableBytes())
        assertEquals(0L, collector.getZramUsedBytes())
        assertEquals(0L, collector.getCompactStallsCount())
    }

    @Test
    fun ramCollector_partialMeminfoMissingSwap_returnsZeroZramAndStalls() {
        val meminfoFile = tempFolder.newFile("meminfo_no_swap")
        meminfoFile.writeText(
            """
            MemTotal:        8000000 kB
            MemAvailable:    4000000 kB
            """.trimIndent()
        )

        val collector = RamMeminfoCollector(meminfoFile = meminfoFile)
        assertEquals(8000000L * 1024L, collector.getTotalBytes())
        assertEquals(4000000L * 1024L, collector.getAvailableBytes())
        assertEquals(0L, collector.getZramUsedBytes())
        assertEquals(0L, collector.getCompactStallsCount())
    }

    @Test
    fun ramCollector_pathologicalAvailableGreaterThanTotal_parsesValuesAccurately() {
        val meminfoFile = tempFolder.newFile("meminfo_anomalous")
        meminfoFile.writeText(
            """
            MemTotal:        4000000 kB
            MemAvailable:    8000000 kB
            SwapTotal:       2000000 kB
            SwapFree:        3000000 kB
            """.trimIndent()
        )

        val collector = RamMeminfoCollector(meminfoFile = meminfoFile)
        assertEquals(4000000L * 1024L, collector.getTotalBytes())
        assertEquals(8000000L * 1024L, collector.getAvailableBytes())
        // SwapFree > SwapTotal coerced to 0 used
        assertEquals(0L, collector.getZramUsedBytes())
        assertEquals(0L, collector.getCompactStallsCount())
    }

    @Test
    fun ramCollector_swapCompactionStalls_boundaryThresholdTesting() {
        // Threshold is > 85.0% swap pressure
        val meminfoFile = tempFolder.newFile("meminfo_swap_boundary")

        // 84.9% swap usage -> 0 stalls
        // SwapTotal = 1,000,000 kB, SwapFree = 151,000 kB -> SwapUsed = 849,000 kB (84.9%)
        meminfoFile.writeText(
            """
            MemTotal:        8000000 kB
            MemAvailable:    1000000 kB
            SwapTotal:       1000000 kB
            SwapFree:         151000 kB
            """.trimIndent()
        )
        val collector1 = RamMeminfoCollector(meminfoFile = meminfoFile)
        assertEquals(0L, collector1.getCompactStallsCount())

        // 85.1% swap usage -> 250 stalls
        // SwapTotal = 1,000,000 kB, SwapFree = 149,000 kB -> SwapUsed = 851,000 kB (85.1%)
        meminfoFile.writeText(
            """
            MemTotal:        8000000 kB
            MemAvailable:    1000000 kB
            SwapTotal:       1000000 kB
            SwapFree:         149000 kB
            """.trimIndent()
        )
        val collector2 = RamMeminfoCollector(meminfoFile = meminfoFile)
        assertEquals(250L, collector2.getCompactStallsCount())
    }

    // =========================================================================
    // 3. NETWORK COLLECTOR PATHOLOGICAL INPUT & SPIKE TESTS
    // =========================================================================

    @Test
    fun networkCollector_suddenGigabyteTrafficSpikes_calculatedAccurately() {
        var rxBytes = 1_000_000L
        var txBytes = 1_000_000L
        var timeNs = 0L

        val collector = NetworkTrafficCollector(
            rxBytesProvider = { rxBytes },
            txBytesProvider = { txBytes },
            nanoTimeProvider = { timeNs }
        )

        // 10 GB transferred in 500ms
        timeNs = 500_000_000L
        rxBytes += 10_000_000_000L
        txBytes += 5_000_000_000L

        collector.sampleDelta()

        // 10 GB / 0.5s = 20 GB/s
        assertEquals(20_000_000_000L, collector.getRxBytesPerSec())
        // 5 GB / 0.5s = 10 GB/s
        assertEquals(10_000_000_000L, collector.getTxBytesPerSec())
    }

    @Test
    fun networkCollector_counterResets_handlesInterfaceResetToZero() {
        var rxBytes = 50_000_000_000L
        var txBytes = 20_000_000_000L
        var timeNs = 1_000_000_000L

        val collector = NetworkTrafficCollector(
            rxBytesProvider = { rxBytes },
            txBytesProvider = { txBytes },
            nanoTimeProvider = { timeNs }
        )

        // Step 1: Interface reboot / counter reset to 500 bytes
        timeNs += 1_000_000_000L
        rxBytes = 500L
        txBytes = 200L

        collector.sampleDelta()
        assertEquals("On reset, rx rate should be 0L", 0L, collector.getRxBytesPerSec())
        assertEquals("On reset, tx rate should be 0L", 0L, collector.getTxBytesPerSec())

        // Step 2: Next tick resumes normal accumulation from reset baseline
        timeNs += 1_000_000_000L
        rxBytes = 10_500L
        txBytes = 5_200L

        collector.sampleDelta()
        assertEquals(10_000L, collector.getRxBytesPerSec())
        assertEquals(5_000L, collector.getTxBytesPerSec())
    }

    @Test
    fun networkCollector_counter64BitRollover_handlesWrapAroundSafely() {
        var rxBytes = Long.MAX_VALUE - 500L
        var txBytes = Long.MAX_VALUE - 200L
        var timeNs = 1_000_000_000L

        val collector = NetworkTrafficCollector(
            rxBytesProvider = { rxBytes },
            txBytesProvider = { txBytes },
            nanoTimeProvider = { timeNs }
        )

        // Counter wraps around to low number
        timeNs += 1_000_000_000L
        rxBytes = 1_000L
        txBytes = 500L

        collector.sampleDelta()
        assertEquals(0L, collector.getRxBytesPerSec())
        assertEquals(0L, collector.getTxBytesPerSec())
    }

    @Test
    fun networkCollector_zeroOrNegativeTimeDelta_avoidsDivisionByZero() {
        var timeNs = 5_000_000_000L
        var rxBytes = 1_000_000L
        var txBytes = 1_000_000L

        val collector = NetworkTrafficCollector(
            rxBytesProvider = { rxBytes },
            txBytesProvider = { txBytes },
            nanoTimeProvider = { timeNs }
        )

        // Identical timestamp (deltaNs == 0)
        rxBytes += 500_000L
        collector.sampleDelta()
        assertEquals(0L, collector.getRxBytesPerSec())

        // Backward jump in timestamp (NTP clock sync / time jump)
        timeNs -= 1_000_000_000L
        rxBytes += 500_000L
        collector.sampleDelta()
        assertEquals(0L, collector.getRxBytesPerSec())
    }

    @Test
    fun networkCollector_unsupportedTrafficStats_negativeOneHandledGracefully() {
        var rxBytes = -1L // TrafficStats.UNSUPPORTED
        var txBytes = -1L
        var timeNs = 1_000_000_000L

        val collector = NetworkTrafficCollector(
            rxBytesProvider = { rxBytes },
            txBytesProvider = { txBytes },
            nanoTimeProvider = { timeNs }
        )

        timeNs += 1_000_000_000L
        collector.sampleDelta()
        assertEquals(0L, collector.getRxBytesPerSec())
        assertEquals(0L, collector.getTxBytesPerSec())
    }

    // =========================================================================
    // 4. STORAGE COLLECTOR LATENCY & STALL TESTS
    // =========================================================================

    @Test
    fun storageCollector_nominalLatency_underThresholdIsNotStall() {
        val probeDir = tempFolder.newFolder("probe_nominal")
        val collector = StorageIoCollector(probeDirectory = probeDir)

        val latencyMs = collector.getWriteLatencyMs()
        assertTrue("Nominal write latency should be >= 0ms: $latencyMs", latencyMs >= 0f)
        assertFalse("Sub-millisecond write should not be stall", collector.isStorageStall())
    }

    @Test
    fun storageCollector_unwritableDirectory_recoversGracefullyWithoutCrash() {
        val readOnlyDir = File("/sys/kernel/debug/unwritable_probe_dir")
        val collector = StorageIoCollector(probeDirectory = readOnlyDir)

        // When write fails (permission denied / read-only filesystem), fallback to lastLatencyMs
        val latency = collector.getWriteLatencyMs()
        assertEquals("Should return fallback default latency 0.5f on error", 0.5f, latency, 0.001f)
        assertFalse("Should not report stall on initial fallback", collector.isStorageStall())
    }

    // =========================================================================
    // 5. THERMAL SYSTEM COLLECTOR TRANSITION TESTS
    // =========================================================================

    @Test
    fun thermalCollector_fullTransitionLifecycle_nominalToEmergencyToShutdown() {
        var currentStatus = 0
        val collector = ThermalSystemCollector(
            thermalStatusProvider = { currentStatus }
        )

        // Level 0: None
        currentStatus = 0
        assertEquals(0, collector.getThermalStatusLevel())
        assertFalse(collector.isThrottled())
        assertFalse(collector.isCritical())

        // Level 1: Light
        currentStatus = 1
        assertEquals(1, collector.getThermalStatusLevel())
        assertFalse(collector.isThrottled())
        assertFalse(collector.isCritical())

        // Level 2: Moderate
        currentStatus = 2
        assertEquals(2, collector.getThermalStatusLevel())
        assertFalse(collector.isThrottled())
        assertFalse(collector.isCritical())

        // Level 3: Severe (Throttled = true, Critical = false)
        currentStatus = 3
        assertEquals(3, collector.getThermalStatusLevel())
        assertTrue(collector.isThrottled())
        assertFalse(collector.isCritical())

        // Level 4: Critical (Throttled = true, Critical = true)
        currentStatus = 4
        assertEquals(4, collector.getThermalStatusLevel())
        assertTrue(collector.isThrottled())
        assertTrue(collector.isCritical())

        // Level 5: Emergency (Throttled = true, Critical = true)
        currentStatus = 5
        assertEquals(5, collector.getThermalStatusLevel())
        assertTrue(collector.isThrottled())
        assertTrue(collector.isCritical())

        // Level 6: Shutdown (Throttled = true, Critical = true)
        currentStatus = 6
        assertEquals(6, collector.getThermalStatusLevel())
        assertTrue(collector.isThrottled())
        assertTrue(collector.isCritical())
    }

    @Test
    fun thermalCollector_extremeBatteryTemperatures_nominalHighExtreme() {
        var temp: Int? = 36000 // 36.0 °C
        val collector = ThermalSystemCollector(
            batteryTempProvider = { temp }
        )

        assertEquals(36000, collector.getBatteryTemperatureMilliC())

        // Hot battery: 58.5 °C
        temp = 58500
        assertEquals(58500, collector.getBatteryTemperatureMilliC())

        // Extreme emergency: 95.0 °C
        temp = 95000
        assertEquals(95000, collector.getBatteryTemperatureMilliC())

        // Sensor failure / null
        temp = null
        assertNull(collector.getBatteryTemperatureMilliC())
    }

    // =========================================================================
    // 6. REAL TELEMETRY SOURCE INTEGRATION & AGGREGATOR STRESS TESTS
    // =========================================================================

    @Test
    fun realTelemetrySource_simultaneousPathologicalInputs_aggregatesSafely() {
        val pathologicalCpu = object : CpuCollector {
            override fun getLoadPercentage(): Float = 0f
            override fun getFrequenciesKhz(): List<Long> = emptyList()
            override fun getMaxFrequencyKhz(): Long = 3_000_000L
            override fun getTemperatureMilliC(): Int? = null
        }

        val pathologicalRam = object : RamCollector {
            override fun getTotalBytes(): Long = 0L
            override fun getAvailableBytes(): Long = 0L
            override fun getZramUsedBytes(): Long = 0L
            override fun getCompactStallsCount(): Long = 0L
        }

        val pathologicalNet = object : NetworkCollector {
            override fun getRxBytesPerSec(): Long = 0L
            override fun getTxBytesPerSec(): Long = 0L
            override fun getCellularRsrpDbm(): Int? = null
            override fun getCellularSinrDb(): Int? = null
            override fun isWifiConnected(): Boolean = false
            override fun getWifiLinkSpeedMbps(): Int? = null
        }

        val stallingStorage = object : StorageCollector {
            override fun getTotalBytes(): Long = 0L
            override fun getAvailableBytes(): Long = 0L
            override fun getWriteLatencyMs(): Float = 550.0f // >150ms stall
            override fun getIoWaitCycles(): Long = 5500L
        }

        val emergencyThermal = object : ThermalCollector {
            override fun getThermalStatusLevel(): Int = 5 // Emergency
            override fun getBatteryTemperatureMilliC(): Int = 68000
        }

        val source = RealTelemetrySource(
            cpuCollector = pathologicalCpu,
            ramCollector = pathologicalRam,
            networkCollector = pathologicalNet,
            storageCollector = stallingStorage,
            thermalCollector = emergencyThermal,
            ioDispatcher = testDispatcher
        )

        val packet = source.poll()
        assertNotNull(packet)
        assertEquals(0f, packet.cpuLoadPercentage, 0.001f)
        assertEquals(0L, packet.ramTotalBytes)
        assertEquals(0L, packet.rxBytesPerSec)
        assertTrue("Storage stall flag must be set when latency is 550ms", packet.isStorageStall)
        assertEquals(5500L, packet.ioWaitCycleDelta)
        assertEquals(5, packet.thermalStatusLevel)
        assertEquals(68000, packet.cpuTemperatureMilliC)
    }

    @Test
    fun realTelemetrySource_storageStallThresholdSimulation_underVsOver150ms() {
        fun createSourceWithLatency(latency: Float): RealTelemetrySource {
            return RealTelemetrySource(
                cpuCollector = object : CpuCollector {
                    override fun getLoadPercentage(): Float = 10f
                    override fun getFrequenciesKhz(): List<Long> = listOf(1_000_000L)
                    override fun getMaxFrequencyKhz(): Long = 2_000_000L
                    override fun getTemperatureMilliC(): Int? = 35000
                },
                ramCollector = object : RamCollector {
                    override fun getTotalBytes(): Long = 4_000_000_000L
                    override fun getAvailableBytes(): Long = 2_000_000_000L
                    override fun getZramUsedBytes(): Long = 0L
                    override fun getCompactStallsCount(): Long = 0L
                },
                networkCollector = object : NetworkCollector {
                    override fun getRxBytesPerSec(): Long = 10_000L
                    override fun getTxBytesPerSec(): Long = 5_000L
                    override fun getCellularRsrpDbm(): Int? = -80
                    override fun getCellularSinrDb(): Int? = null
                    override fun isWifiConnected(): Boolean = true
                    override fun getWifiLinkSpeedMbps(): Int? = 300
                },
                storageCollector = object : StorageCollector {
                    override fun getTotalBytes(): Long = 64_000_000_000L
                    override fun getAvailableBytes(): Long = 32_000_000_000L
                    override fun getWriteLatencyMs(): Float = latency
                    override fun getIoWaitCycles(): Long = (latency * 10).toLong()
                },
                thermalCollector = object : ThermalCollector {
                    override fun getThermalStatusLevel(): Int = 0
                    override fun getBatteryTemperatureMilliC(): Int? = 30000
                },
                ioDispatcher = testDispatcher
            )
        }

        // Under stall threshold (149ms) -> stall false
        val source149 = createSourceWithLatency(149f)
        assertFalse("149ms write latency must NOT be storage stall", source149.poll().isStorageStall)

        // Boundary stall threshold (150ms) -> stall false
        val source150 = createSourceWithLatency(150f)
        assertFalse("150ms write latency is at threshold, not stall", source150.poll().isStorageStall)

        // Over stall threshold (151ms) -> stall true
        val source151 = createSourceWithLatency(151f)
        assertTrue("151ms write latency must trigger storage stall", source151.poll().isStorageStall)

        // Severe stall (500ms) -> stall true
        val source500 = createSourceWithLatency(500f)
        assertTrue("500ms write latency must trigger storage stall", source500.poll().isStorageStall)
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private fun createCoreWithFiles(
        baseDir: File,
        index: Int,
        minKhz: Long,
        maxKhz: Long,
        curKhz: Long?
    ) {
        val coreDir = File(baseDir, "cpu$index/cpufreq").apply { mkdirs() }
        File(coreDir, "cpuinfo_min_freq").writeText("$minKhz\n")
        File(coreDir, "cpuinfo_max_freq").writeText("$maxKhz\n")
        if (curKhz != null) {
            File(coreDir, "scaling_cur_freq").writeText("$curKhz\n")
        }
    }
}
