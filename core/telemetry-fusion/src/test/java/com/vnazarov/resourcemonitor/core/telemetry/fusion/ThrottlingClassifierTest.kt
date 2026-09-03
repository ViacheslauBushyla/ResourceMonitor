package com.vnazarov.resourcemonitor.core.telemetry.fusion

import com.vnazarov.resourcemonitor.core.model.RawTelemetryPacket
import com.vnazarov.resourcemonitor.core.model.ThrottleState
import org.junit.Assert.assertEquals
import org.junit.Test

class ThrottlingClassifierTest {

    @Test
    fun `cpu throttling detected when load is high but frequency is pinned to base clock`() {
        val packet = RawTelemetryPacket(
            cpuLoadPercentage = 95f,
            cpuFrequenciesKhz = listOf(394000L),
            cpuMaxFrequencyKhz = 3200000L
        )

        val state = ThrottlingClassifier.classifyCpu(packet)
        assertEquals(ThrottleState.CRITICAL_THROTTLED, state)
    }

    @Test
    fun `cpu boost detected when load is high and frequency is high`() {
        val packet = RawTelemetryPacket(
            cpuLoadPercentage = 85f,
            cpuFrequenciesKhz = listOf(3000000L),
            cpuMaxFrequencyKhz = 3200000L
        )

        val state = ThrottlingClassifier.classifyCpu(packet)
        assertEquals(ThrottleState.WARNING_BOOST, state)
    }

    @Test
    fun `cpu nominal when load is low`() {
        val packet = RawTelemetryPacket(
            cpuLoadPercentage = 20f,
            cpuFrequenciesKhz = listOf(1200000L),
            cpuMaxFrequencyKhz = 3200000L
        )

        val state = ThrottlingClassifier.classifyCpu(packet)
        assertEquals(ThrottleState.NOMINAL, state)
    }

    @Test
    fun `cellular drop detected when RSRP is below -115 dBm`() {
        val weakPacket = RawTelemetryPacket(rsrpDbm = -120)
        assertEquals(ThrottleState.CRITICAL_THROTTLED, ThrottlingClassifier.classifyCellular(weakPacket))

        val normalPacket = RawTelemetryPacket(rsrpDbm = -80)
        assertEquals(ThrottleState.NOMINAL, ThrottlingClassifier.classifyCellular(normalPacket))
    }
}
