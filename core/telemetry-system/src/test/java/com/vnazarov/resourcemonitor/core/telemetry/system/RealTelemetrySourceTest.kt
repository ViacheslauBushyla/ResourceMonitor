package com.vnazarov.resourcemonitor.core.telemetry.system

import com.vnazarov.resourcemonitor.core.telemetry.api.CpuCollector
import com.vnazarov.resourcemonitor.core.telemetry.api.NetworkCollector
import com.vnazarov.resourcemonitor.core.telemetry.api.RamCollector
import com.vnazarov.resourcemonitor.core.telemetry.api.StorageCollector
import com.vnazarov.resourcemonitor.core.telemetry.api.ThermalCollector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RealTelemetrySourceTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakeCpuCollector : CpuCollector {
        override fun getLoadPercentage(): Float = 42.5f
        override fun getFrequenciesKhz(): List<Long> = listOf(600_000L, 1_200_000L)
        override fun getMaxFrequencyKhz(): Long = 2_800_000L
        override fun getTemperatureMilliC(): Int = 41000
    }

    private class FakeRamCollector : RamCollector {
        override fun getTotalBytes(): Long = 8_000_000_000L
        override fun getAvailableBytes(): Long = 3_000_000_000L
        override fun getZramUsedBytes(): Long = 1_500_000_000L
        override fun getCompactStallsCount(): Long = 12L
    }

    private class FakeNetworkCollector : NetworkCollector {
        override fun getRxBytesPerSec(): Long = 250_000L
        override fun getTxBytesPerSec(): Long = 75_000L
        override fun getCellularRsrpDbm(): Int = -85
        override fun getCellularSinrDb(): Int? = null
        override fun isWifiConnected(): Boolean = true
        override fun getWifiLinkSpeedMbps(): Int = 433
    }

    private class FakeStorageCollector(private val latency: Float = 1.2f) : StorageCollector {
        override fun getTotalBytes(): Long = 128_000_000_000L
        override fun getAvailableBytes(): Long = 64_000_000_000L
        override fun getWriteLatencyMs(): Float = latency
        override fun getIoWaitCycles(): Long = (latency * 10).toLong()
    }

    private class FakeThermalCollector : ThermalCollector {
        override fun getThermalStatusLevel(): Int = 1
        override fun getBatteryTemperatureMilliC(): Int = 36500
    }

    @Test
    fun poll_aggregatesAllCollectorOutputsIntoRawTelemetryPacket() {
        val source = RealTelemetrySource(
            cpuCollector = FakeCpuCollector(),
            ramCollector = FakeRamCollector(),
            networkCollector = FakeNetworkCollector(),
            storageCollector = FakeStorageCollector(latency = 1.5f),
            thermalCollector = FakeThermalCollector(),
            ioDispatcher = testDispatcher
        )

        val packet = source.poll()

        assertEquals(42.5f, packet.cpuLoadPercentage, 0.01f)
        assertEquals(listOf(600_000L, 1_200_000L), packet.cpuFrequenciesKhz)
        assertEquals(2_800_000L, packet.cpuMaxFrequencyKhz)
        assertEquals(41000, packet.cpuTemperatureMilliC)

        assertEquals(8_000_000_000L, packet.ramTotalBytes)
        assertEquals(3_000_000_000L, packet.ramAvailableBytes)
        assertEquals(1_500_000_000L, packet.zRamUsedBytes)
        assertEquals(12L, packet.compactStallsCount)

        assertEquals(250_000L, packet.rxBytesPerSec)
        assertEquals(75_000L, packet.txBytesPerSec)
        assertEquals(-85, packet.rsrpDbm)
        assertTrue(packet.isWifiActive)
        assertEquals(433, packet.wifiLinkSpeedMbps)

        assertEquals(15L, packet.ioWaitCycleDelta)
        assertFalse(packet.isStorageStall)
        assertEquals(1, packet.thermalStatusLevel)
    }

    @Test
    fun poll_whenStorageLatencyHigh_setsStorageStallFlag() {
        val source = RealTelemetrySource(
            cpuCollector = FakeCpuCollector(),
            ramCollector = FakeRamCollector(),
            networkCollector = FakeNetworkCollector(),
            storageCollector = FakeStorageCollector(latency = 165f), // > 150ms stall
            thermalCollector = FakeThermalCollector(),
            ioDispatcher = testDispatcher
        )

        val packet = source.poll()
        assertTrue(packet.isStorageStall)
    }

    @Test
    fun observe_emitsPacketsPeriodically() = runTest(testDispatcher) {
        val source = RealTelemetrySource(
            cpuCollector = FakeCpuCollector(),
            ramCollector = FakeRamCollector(),
            networkCollector = FakeNetworkCollector(),
            storageCollector = FakeStorageCollector(),
            thermalCollector = FakeThermalCollector(),
            ioDispatcher = testDispatcher
        )

        val packets = source.observe(intervalMs = 10L).take(3).toList()
        assertEquals(3, packets.size)
    }
}
