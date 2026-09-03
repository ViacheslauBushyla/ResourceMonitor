package com.vnazarov.resourcemonitor.core.telemetry.fusion

import com.vnazarov.resourcemonitor.core.model.MetricValue
import com.vnazarov.resourcemonitor.core.model.RawTelemetryPacket
import com.vnazarov.resourcemonitor.core.model.SystemTelemetrySnapshot
import com.vnazarov.resourcemonitor.core.model.ThrottleState
import com.vnazarov.resourcemonitor.core.telemetry.api.TelemetrySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TelemetryFusionEngine(
    private val telemetrySource: TelemetrySource,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        const val INTERVAL_ACTIVE_MS = 250L
        const val INTERVAL_IDLE_DECAY_MS = 2000L
        const val IDLE_LOAD_THRESHOLD = 0.30f
    }

    private val _snapshot = MutableStateFlow(SystemTelemetrySnapshot())
    val snapshot: StateFlow<SystemTelemetrySnapshot> = _snapshot.asStateFlow()

    private val cpuEma = EmaFilter(alpha = 0.35f)
    private val ramEma = EmaFilter(alpha = 0.15f)
    private val netEma = EmaFilter(alpha = 0.25f)
    private val cellEma = EmaFilter(alpha = 0.20f)

    private var pollingJob: Job? = null
    var currentIntervalMs: Long = INTERVAL_ACTIVE_MS
        private set

    fun start() {
        if (pollingJob?.isActive == true) return

        pollingJob = coroutineScope.launch {
            while (isActive) {
                val packet = telemetrySource.poll()
                val fused = processPacket(packet)
                _snapshot.value = fused

                // Adaptive polling logic
                currentIntervalMs = if (fused.worstThrottleState != ThrottleState.NOMINAL ||
                    fused.cpu.smoothedValue > IDLE_LOAD_THRESHOLD ||
                    fused.network.smoothedValue > 0.15f
                ) {
                    INTERVAL_ACTIVE_MS
                } else {
                    INTERVAL_IDLE_DECAY_MS
                }

                delay(currentIntervalMs)
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun injectPacket(packet: RawTelemetryPacket, resetEma: Boolean = true): SystemTelemetrySnapshot {
        if (resetEma) {
            cpuEma.reset(TelemetryNormalizer.normalizeCpu(packet))
            ramEma.reset(TelemetryNormalizer.normalizeRam(packet))
            netEma.reset(TelemetryNormalizer.normalizeNetwork(packet))
            cellEma.reset(TelemetryNormalizer.normalizeCellularQuality(packet))
        }
        val fused = processPacket(packet)
        _snapshot.value = fused
        return fused
    }

    fun processPacket(packet: RawTelemetryPacket): SystemTelemetrySnapshot {
        val cpuRaw = TelemetryNormalizer.normalizeCpu(packet)
        val ramRaw = TelemetryNormalizer.normalizeRam(packet)
        val netRaw = TelemetryNormalizer.normalizeNetwork(packet)
        val cellRaw = TelemetryNormalizer.normalizeCellularQuality(packet)

        val cpuSmoothed = cpuEma.filter(cpuRaw)
        val ramSmoothed = ramEma.filter(ramRaw)
        val netSmoothed = netEma.filter(netRaw)
        val cellSmoothed = cellEma.filter(cellRaw)

        val cpuThrottle = ThrottlingClassifier.classifyCpu(packet)
        val ramThrottle = ThrottlingClassifier.classifyRam(packet)
        val cellThrottle = ThrottlingClassifier.classifyCellular(packet)

        val worstState = maxOf(cpuThrottle, ramThrottle, cellThrottle)

        return SystemTelemetrySnapshot(
            timestampMs = System.currentTimeMillis(),
            cpu = MetricValue(
                rawNormalized = cpuRaw,
                smoothedValue = cpuSmoothed,
                throttleState = cpuThrottle,
                displayLabel = "${(cpuSmoothed * 100).toInt()}%"
            ),
            ram = MetricValue(
                rawNormalized = ramRaw,
                smoothedValue = ramSmoothed,
                throttleState = ramThrottle,
                displayLabel = "${(ramSmoothed * 100).toInt()}%"
            ),
            network = MetricValue(
                rawNormalized = netRaw,
                smoothedValue = netSmoothed,
                displayLabel = formatSpeed(packet.rxBytesPerSec + packet.txBytesPerSec)
            ),
            cellularQuality = MetricValue(
                rawNormalized = cellRaw,
                smoothedValue = cellSmoothed,
                throttleState = cellThrottle,
                displayLabel = packet.rsrpDbm?.let { "${it} dBm" } ?: "N/A"
            ),
            worstThrottleState = worstState
        )
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1_000_000 -> String.format("%.1f MB/s", bytesPerSec / 1_000_000.0)
            bytesPerSec >= 1_000 -> String.format("%.0f KB/s", bytesPerSec / 1_000.0)
            else -> "$bytesPerSec B/s"
        }
    }
}
