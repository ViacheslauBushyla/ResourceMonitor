package com.vnazarov.resourcemonitor.core.telemetry.fusion

import com.vnazarov.resourcemonitor.core.model.RawTelemetryPacket
import kotlin.math.ln
import kotlin.math.max

object TelemetryNormalizer {

    fun normalizeCpu(packet: RawTelemetryPacket): Float {
        return (packet.cpuLoadPercentage / 100f).coerceIn(0f, 1f)
    }

    fun normalizeRam(packet: RawTelemetryPacket): Float {
        val total = packet.ramTotalBytes
        if (total <= 0) return 0f
        val used = (total - packet.ramAvailableBytes).coerceAtLeast(0L)
        return (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    fun normalizeNetwork(packet: RawTelemetryPacket): Float {
        val totalSpeed = packet.rxBytesPerSec + packet.txBytesPerSec
        if (totalSpeed <= 1024) return 0f
        // Logarithmic scale up to 10 MB/s
        val maxLog = ln(10_000_000.0)
        val curLog = ln(totalSpeed.toDouble().coerceAtLeast(1.0))
        return (curLog / maxLog).toFloat().coerceIn(0f, 1f)
    }

    fun normalizeCellularQuality(packet: RawTelemetryPacket): Float {
        val rsrp = packet.rsrpDbm ?: return 1f // If wifi or unknown, assume nominal
        // Range: -140 dBm (dead zone, 0.0) to -65 dBm (excellent signal, 1.0)
        return ((rsrp + 140f) / 75f).coerceIn(0f, 1f)
    }

    fun normalizeStorage(packet: RawTelemetryPacket): Float {
        if (packet.isStorageStall) return 0.95f
        if (packet.ioWaitCycleDelta > 0L) {
            return (packet.ioWaitCycleDelta / 100f).coerceIn(0f, 1f)
        }
        return 0f
    }

    fun normalizeGpu(packet: RawTelemetryPacket): Float {
        return (packet.gpuLoadPercentage / 100f).coerceIn(0f, 1f)
    }
}
