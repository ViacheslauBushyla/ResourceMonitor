package com.vnazarov.resourcemonitor.core.telemetry.fusion

import com.vnazarov.resourcemonitor.core.model.RawTelemetryPacket
import com.vnazarov.resourcemonitor.core.model.ThrottleState

object ThrottlingClassifier {

    fun classifyCpu(packet: RawTelemetryPacket): ThrottleState {
        val load = packet.cpuLoadPercentage
        val currentFreq = packet.cpuFrequenciesKhz.firstOrNull() ?: 0L
        val maxFreq = packet.cpuMaxFrequencyKhz

        // Throttle ratio: current frequency vs max frequency
        val freqRatio = if (maxFreq > 0) currentFreq.toFloat() / maxFreq.toFloat() else 1f

        return when {
            // High load (>85%) but frequency is stepped down (<30% of max, e.g. base clock) -> Thermal clock-capping
            load >= 85f && freqRatio in 0.01f..0.35f -> ThrottleState.CRITICAL_THROTTLED
            // High load with boosted frequency -> High performance boost
            load >= 70f -> ThrottleState.WARNING_BOOST
            else -> ThrottleState.NOMINAL
        }
    }

    fun classifyRam(packet: RawTelemetryPacket): ThrottleState {
        val total = packet.ramTotalBytes
        val available = packet.ramAvailableBytes
        val usedRatio = if (total > 0) (total - available).toFloat() / total.toFloat() else 0f

        return when {
            // High RAM used and active compact stalls / heavy zRAM thrashing
            usedRatio > 0.85f && (packet.compactStallsCount > 100 || packet.zRamUsedBytes > 2_000_000_000L) ->
                ThrottleState.CRITICAL_THROTTLED
            usedRatio > 0.70f -> ThrottleState.WARNING_BOOST
            else -> ThrottleState.NOMINAL
        }
    }

    fun classifyCellular(packet: RawTelemetryPacket): ThrottleState {
        val rsrp = packet.rsrpDbm ?: return ThrottleState.NOMINAL
        return when {
            rsrp <= -115 -> ThrottleState.CRITICAL_THROTTLED
            rsrp <= -95 -> ThrottleState.WARNING_BOOST
            else -> ThrottleState.NOMINAL
        }
    }

    fun classifyStorage(packet: RawTelemetryPacket): ThrottleState {
        return when {
            packet.isStorageStall -> ThrottleState.CRITICAL_THROTTLED
            packet.ioWaitCycleDelta > 50L -> ThrottleState.WARNING_BOOST
            else -> ThrottleState.NOMINAL
        }
    }

    fun classifyGpu(packet: RawTelemetryPacket): ThrottleState {
        val thermalStatus = packet.thermalStatusLevel ?: 0
        val gpuTemp = packet.gpuTemperatureMilliC ?: 0
        val load = packet.gpuLoadPercentage
        return when {
            thermalStatus >= 4 || gpuTemp >= 60_000 || load >= 95f -> ThrottleState.CRITICAL_THROTTLED
            thermalStatus >= 2 || gpuTemp >= 50_000 || load >= 70f -> ThrottleState.WARNING_BOOST
            else -> ThrottleState.NOMINAL
        }
    }
}
