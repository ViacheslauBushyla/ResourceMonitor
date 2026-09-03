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
}
