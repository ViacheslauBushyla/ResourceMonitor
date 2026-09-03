package com.vnazarov.resourcemonitor.core.telemetry.mock

import com.vnazarov.resourcemonitor.core.model.RawTelemetryPacket

sealed class SimulationScenario(val name: String) {
    abstract fun generatePacket(): RawTelemetryPacket

    data object IdleCalm : SimulationScenario("Idle & Calm (15% CPU, 30% RAM)") {
        override fun generatePacket() = RawTelemetryPacket(
            cpuLoadPercentage = 15f,
            cpuFrequenciesKhz = listOf(1800000L),
            cpuMaxFrequencyKhz = 3200000L,
            cpuTemperatureMilliC = 38000,
            ramTotalBytes = 16_000_000_000L,
            ramAvailableBytes = 11_200_000_000L,
            rxBytesPerSec = 45_000L,
            rsrpDbm = -82,
            isWifiActive = true
        )
    }

    data object PeakGaming : SimulationScenario("Peak Gaming Load (85% CPU, 75% RAM, Boosted)") {
        override fun generatePacket() = RawTelemetryPacket(
            cpuLoadPercentage = 85f,
            cpuFrequenciesKhz = listOf(3000000L),
            cpuMaxFrequencyKhz = 3200000L,
            cpuTemperatureMilliC = 44000,
            ramTotalBytes = 16_000_000_000L,
            ramAvailableBytes = 4_000_000_000L,
            rxBytesPerSec = 1_800_000L,
            rsrpDbm = -75,
            isWifiActive = true
        )
    }

    data object ThermalMeltdown : SimulationScenario("Critical Thermal Meltdown (95% CPU throttled to 394MHz)") {
        override fun generatePacket() = RawTelemetryPacket(
            cpuLoadPercentage = 95f,
            cpuFrequenciesKhz = listOf(394000L), // Throttled to minimum base clock!
            cpuMaxFrequencyKhz = 3200000L,
            cpuTemperatureMilliC = 52000,
            ramTotalBytes = 16_000_000_000L,
            ramAvailableBytes = 2_000_000_000L,
            zRamUsedBytes = 3_000_000_000L,
            compactStallsCount = 450L,
            rxBytesPerSec = 50_000L,
            rsrpDbm = -120,
            isWifiActive = false
        )
    }

    data object CellularDrop : SimulationScenario("Cellular Signal Drop (RSRP -122 dBm)") {
        override fun generatePacket() = RawTelemetryPacket(
            cpuLoadPercentage = 35f,
            cpuFrequenciesKhz = listOf(2200000L),
            cpuMaxFrequencyKhz = 3200000L,
            cpuTemperatureMilliC = 41000,
            ramTotalBytes = 16_000_000_000L,
            ramAvailableBytes = 8_000_000_000L,
            rxBytesPerSec = 5_000L,
            rsrpDbm = -122,
            sinrDb = -18,
            isWifiActive = false
        )
    }
}
