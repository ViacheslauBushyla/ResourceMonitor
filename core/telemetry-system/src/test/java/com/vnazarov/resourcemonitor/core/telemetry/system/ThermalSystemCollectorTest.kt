package com.vnazarov.resourcemonitor.core.telemetry.system

import com.vnazarov.resourcemonitor.core.telemetry.system.collector.ThermalSystemCollector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalSystemCollectorTest {

    @Test
    fun thermalStatusLevels_correctlyMapsThrottleAndCriticalStates() {
        var status = 0
        val collector = ThermalSystemCollector(
            thermalStatusProvider = { status }
        )

        // Status 0: None
        assertEquals(0, collector.getThermalStatusLevel())
        assertFalse(collector.isThrottled())
        assertFalse(collector.isCritical())

        // Status 2: Moderate
        status = 2
        assertEquals(2, collector.getThermalStatusLevel())
        assertFalse(collector.isThrottled())
        assertFalse(collector.isCritical())

        // Status 3: Severe
        status = 3
        assertEquals(3, collector.getThermalStatusLevel())
        assertTrue(collector.isThrottled())
        assertFalse(collector.isCritical())

        // Status 4: Critical
        status = 4
        assertEquals(4, collector.getThermalStatusLevel())
        assertTrue(collector.isThrottled())
        assertTrue(collector.isCritical())
    }

    @Test
    fun batteryTemperature_returnsMillidegreesFromTenths() {
        val collector = ThermalSystemCollector(
            batteryTempProvider = { 39500 } // 39.5 °C in m°C
        )

        assertEquals(39500, collector.getBatteryTemperatureMilliC())
    }
}
