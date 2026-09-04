package com.vnazarov.resourcemonitor.core.model

data class RawTelemetryPacket(
    val timestampNs: Long = System.nanoTime(),
    val cpuFrequenciesKhz: List<Long> = emptyList(),
    val cpuMaxFrequencyKhz: Long = 0L,
    val cpuLoadPercentage: Float = 0f,
    val cpuTemperatureMilliC: Int? = null,
    val ramTotalBytes: Long = 0L,
    val ramAvailableBytes: Long = 0L,
    val zRamUsedBytes: Long = 0L,
    val compactStallsCount: Long = 0L,
    val rxBytesPerSec: Long = 0L,
    val txBytesPerSec: Long = 0L,
    val rsrpDbm: Int? = null,
    val sinrDb: Int? = null,
    val isWifiActive: Boolean = false,
    val wifiLinkSpeedMbps: Int? = null,
    val ioWaitCycleDelta: Long = 0L,
    val gpuLoadPercentage: Float = 0f,
    val gpuTemperatureMilliC: Int? = null,
    val isStorageStall: Boolean = false,
    val thermalStatusLevel: Int? = null
)
