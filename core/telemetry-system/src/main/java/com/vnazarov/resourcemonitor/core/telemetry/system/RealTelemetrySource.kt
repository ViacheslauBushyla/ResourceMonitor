package com.vnazarov.resourcemonitor.core.telemetry.system

import com.vnazarov.resourcemonitor.core.model.RawTelemetryPacket
import com.vnazarov.resourcemonitor.core.telemetry.api.CpuCollector
import com.vnazarov.resourcemonitor.core.telemetry.api.NetworkCollector
import com.vnazarov.resourcemonitor.core.telemetry.api.RamCollector
import com.vnazarov.resourcemonitor.core.telemetry.api.StorageCollector
import com.vnazarov.resourcemonitor.core.telemetry.api.TelemetrySource
import com.vnazarov.resourcemonitor.core.telemetry.api.ThermalCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.NetworkTrafficCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.StorageIoCollector
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

class RealTelemetrySource(
    private val cpuCollector: CpuCollector,
    private val ramCollector: RamCollector,
    private val networkCollector: NetworkCollector,
    private val storageCollector: StorageCollector,
    private val thermalCollector: ThermalCollector,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TelemetrySource {

    override val isAvailable: Boolean = true

    override fun poll(): RawTelemetryPacket {
        (networkCollector as? NetworkTrafficCollector)?.sampleDelta()

        val latencyMs = storageCollector.getWriteLatencyMs()
        val isStorageStall = (storageCollector as? StorageIoCollector)?.isStorageStall() ?: (latencyMs > 150f)
        val thermalStatus = thermalCollector.getThermalStatusLevel()
        val batteryTemp = thermalCollector.getBatteryTemperatureMilliC()

        return RawTelemetryPacket(
            timestampNs = System.nanoTime(),
            cpuFrequenciesKhz = cpuCollector.getFrequenciesKhz(),
            cpuMaxFrequencyKhz = cpuCollector.getMaxFrequencyKhz(),
            cpuLoadPercentage = cpuCollector.getLoadPercentage(),
            cpuTemperatureMilliC = cpuCollector.getTemperatureMilliC() ?: batteryTemp,
            ramTotalBytes = ramCollector.getTotalBytes(),
            ramAvailableBytes = ramCollector.getAvailableBytes(),
            zRamUsedBytes = ramCollector.getZramUsedBytes(),
            compactStallsCount = ramCollector.getCompactStallsCount(),
            rxBytesPerSec = networkCollector.getRxBytesPerSec(),
            txBytesPerSec = networkCollector.getTxBytesPerSec(),
            rsrpDbm = networkCollector.getCellularRsrpDbm(),
            sinrDb = networkCollector.getCellularSinrDb(),
            isWifiActive = networkCollector.isWifiConnected(),
            wifiLinkSpeedMbps = networkCollector.getWifiLinkSpeedMbps(),
            ioWaitCycleDelta = storageCollector.getIoWaitCycles(),
            gpuLoadPercentage = 0f,
            gpuTemperatureMilliC = null,
            isStorageStall = isStorageStall,
            thermalStatusLevel = thermalStatus
        )
    }

    override fun observe(intervalMs: Long): Flow<RawTelemetryPacket> = flow {
        while (currentCoroutineContext().isActive) {
            emit(poll())
            delay(intervalMs)
        }
    }.flowOn(ioDispatcher)
}
