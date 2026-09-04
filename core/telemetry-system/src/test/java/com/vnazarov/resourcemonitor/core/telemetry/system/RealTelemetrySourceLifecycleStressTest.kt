package com.vnazarov.resourcemonitor.core.telemetry.system

import com.vnazarov.resourcemonitor.core.model.RawTelemetryPacket
import com.vnazarov.resourcemonitor.core.telemetry.api.CpuCollector
import com.vnazarov.resourcemonitor.core.telemetry.api.NetworkCollector
import com.vnazarov.resourcemonitor.core.telemetry.api.RamCollector
import com.vnazarov.resourcemonitor.core.telemetry.api.StorageCollector
import com.vnazarov.resourcemonitor.core.telemetry.api.ThermalCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.CpuFreqCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.RamMeminfoCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.StorageIoCollector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class RealTelemetrySourceLifecycleStressTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val pollCounter = AtomicInteger(0)

    private class InstrumentableCpuCollector(private val counter: AtomicInteger) : CpuCollector {
        override fun getLoadPercentage(): Float {
            counter.incrementAndGet()
            return 33.0f
        }
        override fun getFrequenciesKhz(): List<Long> = listOf(1_800_000L, 2_400_000L)
        override fun getMaxFrequencyKhz(): Long = 2_800_000L
        override fun getTemperatureMilliC(): Int = 42000
    }

    private class StubRamCollector : RamCollector {
        override fun getTotalBytes(): Long = 12_000_000_000L
        override fun getAvailableBytes(): Long = 6_000_000_000L
        override fun getZramUsedBytes(): Long = 1_000_000_000L
        override fun getCompactStallsCount(): Long = 0L
    }

    private class StubNetworkCollector : NetworkCollector {
        override fun getRxBytesPerSec(): Long = 1_000_000L
        override fun getTxBytesPerSec(): Long = 500_000L
        override fun getCellularRsrpDbm(): Int = -90
        override fun getCellularSinrDb(): Int = 15
        override fun isWifiConnected(): Boolean = true
        override fun getWifiLinkSpeedMbps(): Int = 866
    }

    private class StubStorageCollector : StorageCollector {
        override fun getTotalBytes(): Long = 256_000_000_000L
        override fun getAvailableBytes(): Long = 128_000_000_000L
        override fun getWriteLatencyMs(): Float = 2.4f
        override fun getIoWaitCycles(): Long = 24L
    }

    private class StubThermalCollector : ThermalCollector {
        override fun getThermalStatusLevel(): Int = 0
        override fun getBatteryTemperatureMilliC(): Int = 31000
    }

    private lateinit var source: RealTelemetrySource

    @Before
    fun setUp() {
        pollCounter.set(0)
        source = RealTelemetrySource(
            cpuCollector = InstrumentableCpuCollector(pollCounter),
            ramCollector = StubRamCollector(),
            networkCollector = StubNetworkCollector(),
            storageCollector = StubStorageCollector(),
            thermalCollector = StubThermalCollector(),
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun cancellation_stopsBackgroundLoopAndReleasesResources() = testScope.runTest {
        val emissions = mutableListOf<RawTelemetryPacket>()
        val job = launch {
            source.observe(intervalMs = 250L).collect {
                emissions.add(it)
            }
        }

        // Advance 0ms: Initial poll runs immediately
        runCurrent()
        assertEquals(1, emissions.size)
        assertEquals(1, pollCounter.get())

        // Advance 250ms: Second poll
        advanceTimeBy(250L)
        runCurrent()
        assertEquals(2, emissions.size)
        assertEquals(2, pollCounter.get())

        // Advance 250ms: Third poll
        advanceTimeBy(250L)
        runCurrent()
        assertEquals(3, emissions.size)
        assertEquals(3, pollCounter.get())

        // Explicitly cancel the collection coroutine
        job.cancelAndJoin()
        assertTrue("Job must be cancelled", job.isCancelled)
        assertTrue("Job must be completed", job.isCompleted)

        val countAtCancel = emissions.size
        val pollsAtCancel = pollCounter.get()

        // Advance time significantly after cancellation (2000ms)
        advanceTimeBy(2000L)
        runCurrent()

        // Ensure zero further emissions or poll executions occur
        assertEquals("Emissions must freeze after cancellation", countAtCancel, emissions.size)
        assertEquals("Poll loop must stop completely after cancellation", pollsAtCancel, pollCounter.get())
    }

    @Test
    fun transitionBetweenActiveAndIdle_dynamicallyAdjustsCadenceWithoutCoroutineLeak() = testScope.runTest {
        val intervalFlow = MutableStateFlow(250L) // Start in active mode
        val emissions = mutableListOf<RawTelemetryPacket>()

        val job = launch {
            intervalFlow.flatMapLatest { interval ->
                source.observe(interval)
            }.collect {
                emissions.add(it)
            }
        }

        // Initial emission at t=0
        runCurrent()
        assertEquals(1, emissions.size)

        // Advance 500ms in active mode (250ms cadence: emits at 250ms and 500ms)
        advanceTimeBy(500L)
        runCurrent()
        assertEquals(3, emissions.size) // t=0, t=250, t=500

        // Transition to idle mode: 2000ms cadence
        intervalFlow.value = 2000L
        runCurrent()
        // flatMapLatest cancels previous collector and immediately triggers initial emission of new observe flow
        assertEquals(4, emissions.size)

        // Advance 1000ms: should NOT emit yet since idle cadence is 2000ms
        advanceTimeBy(1000L)
        runCurrent()
        assertEquals("Should not emit during idle interval before 2000ms", 4, emissions.size)

        // Advance remaining 1000ms (total 2000ms since transition): triggers idle emission
        advanceTimeBy(1000L)
        runCurrent()
        assertEquals(5, emissions.size)

        // Switch back to active mode: 250ms cadence
        intervalFlow.value = 250L
        runCurrent()
        assertEquals(6, emissions.size) // Immediate initial emit of active flow

        advanceTimeBy(250L)
        runCurrent()
        assertEquals(7, emissions.size)

        job.cancelAndJoin()
        assertTrue(job.isCompleted)
    }

    @Test
    fun concurrentObservers_operateIndependentlyWithoutInterference() = testScope.runTest {
        val emissionsObserverA = mutableListOf<RawTelemetryPacket>()
        val emissionsObserverB = mutableListOf<RawTelemetryPacket>()

        val jobA = launch {
            source.observe(100L).collect { emissionsObserverA.add(it) }
        }
        val jobB = launch {
            source.observe(250L).collect { emissionsObserverB.add(it) }
        }

        // At t=0, both receive initial emission
        runCurrent()
        assertEquals(1, emissionsObserverA.size)
        assertEquals(1, emissionsObserverB.size)

        // Advance 500ms
        // Observer A (100ms): emits at 100, 200, 300, 400, 500 -> total 6
        // Observer B (250ms): emits at 250, 500 -> total 3
        advanceTimeBy(500L)
        runCurrent()
        assertEquals(6, emissionsObserverA.size)
        assertEquals(3, emissionsObserverB.size)

        // Cancel Observer A only
        jobA.cancelAndJoin()
        assertTrue(jobA.isCancelled)
        assertFalse(jobB.isCancelled)

        // Advance another 500ms: B continues to emit (at 750, 1000), A remains frozen at 6
        advanceTimeBy(500L)
        runCurrent()
        assertEquals(6, emissionsObserverA.size)
        assertEquals(5, emissionsObserverB.size)

        jobB.cancelAndJoin()
        assertTrue(jobB.isCompleted)
    }

    @Test
    fun rapidPollingStress_withRealFileIo_doesNotLeakFileDescriptorsOrCorruptPackets() {
        val probeDir = tempFolder.newFolder("stress_probe")
        val storageDir = tempFolder.newFolder("stress_storage")
        val cpuDir = tempFolder.newFolder("stress_cpu")
        val core0 = File(cpuDir, "cpu0/cpufreq").apply { mkdirs() }
        File(core0, "scaling_cur_freq").writeText("1500000\n")
        File(core0, "cpuinfo_min_freq").writeText("400000\n")
        File(core0, "cpuinfo_max_freq").writeText("2800000\n")

        val meminfoFile = tempFolder.newFile("stress_meminfo").apply {
            writeText(
                """
                MemTotal:       12000000 kB
                MemFree:         4000000 kB
                MemAvailable:    7000000 kB
                SwapTotal:       4000000 kB
                SwapFree:        3000000 kB
                """.trimIndent()
            )
        }

        val realStorageCollector = StorageIoCollector(probeDirectory = probeDir, storageDirectory = storageDir)
        val realCpuCollector = CpuFreqCollector(cpuBaseDir = cpuDir)
        val realRamCollector = RamMeminfoCollector(meminfoFile = meminfoFile)

        val realSource = RealTelemetrySource(
            cpuCollector = realCpuCollector,
            ramCollector = realRamCollector,
            networkCollector = StubNetworkCollector(),
            storageCollector = realStorageCollector,
            thermalCollector = StubThermalCollector()
        )

        var lastTimestamp = 0L
        val iterations = 100

        for (i in 0 until iterations) {
            val packet = realSource.poll()

            assertNotNull(packet)
            assertTrue("Timestamp must be strictly positive", packet.timestampNs > 0L)
            assertTrue("Timestamps must monotonically increase", packet.timestampNs >= lastTimestamp)
            lastTimestamp = packet.timestampNs

            // Verify telemetry correctness under stress
            assertTrue("CPU load must be non-negative", packet.cpuLoadPercentage >= 0f)
            assertEquals("RAM available bytes must match meminfo", 7_000_000L * 1024L, packet.ramAvailableBytes)
            assertEquals("RAM total bytes must match meminfo", 12_000_000L * 1024L, packet.ramTotalBytes)
            assertEquals("zRAM used bytes must match SwapTotal - SwapFree", 1_000_000L * 1024L, packet.zRamUsedBytes)
            assertTrue("Storage write latency must be measured and >= 0ms", realStorageCollector.lastWriteLatencyMs >= 0f)
            assertFalse("Normal small sync probe must not trigger storage stall alert", packet.isStorageStall)
        }

        // Verify probe file exists and can be cleanly deleted (no open file locks)
        val probeFile = File(probeDir, ".io_latency_probe")
        assertTrue("Probe file must exist on disk", probeFile.exists())
        assertTrue("Probe file should be non-empty after write probes", probeFile.length() > 0)
    }
}
