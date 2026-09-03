package com.vnazarov.resourcemonitor.core.telemetry.api

interface CpuCollector {
    fun getLoadPercentage(): Float
    fun getFrequenciesKhz(): List<Long>
    fun getMaxFrequencyKhz(): Long
    fun getTemperatureMilliC(): Int?
}

interface RamCollector {
    fun getTotalBytes(): Long
    fun getAvailableBytes(): Long
    fun getZramUsedBytes(): Long
    fun getCompactStallsCount(): Long
}

interface NetworkCollector {
    fun getRxBytesPerSec(): Long
    fun getTxBytesPerSec(): Long
    fun getCellularRsrpDbm(): Int?
    fun getCellularSinrDb(): Int?
    fun isWifiConnected(): Boolean
    fun getWifiLinkSpeedMbps(): Int?
}
