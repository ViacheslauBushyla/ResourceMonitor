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
import kotlinx.coroutines.withContext
import kotlin.math.abs

class TelemetryFusionEngine(
    private var telemetrySource: TelemetrySource,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val realSource: TelemetrySource? = null,
    private val fakeSource: TelemetrySource? = null
) {
    companion object {
        const val INTERVAL_ACTIVE_MS = 250L
        const val INTERVAL_IDLE_DECAY_MS = 2000L
        const val IDLE_LOAD_THRESHOLD = 0.30f
    }

    private val _snapshot = MutableStateFlow(SystemTelemetrySnapshot())
    val snapshot: StateFlow<SystemTelemetrySnapshot> = _snapshot.asStateFlow()

    private val _lastPacket = MutableStateFlow<RawTelemetryPacket?>(null)
    val lastPacket: StateFlow<RawTelemetryPacket?> = _lastPacket.asStateFlow()

    private val cpuEma = EmaFilter(alpha = 0.35f)
    private val ramEma = EmaFilter(alpha = 0.15f)
    private val netEma = EmaFilter(alpha = 0.25f)
    private val cellEma = EmaFilter(alpha = 0.20f)
    private val storageEma = EmaFilter(alpha = 0.20f)
    private val gpuEma = EmaFilter(alpha = 0.25f)

    private var pollingJob: Job? = null
    var currentIntervalMs: Long = INTERVAL_IDLE_DECAY_MS
        private set
    var activePollingIntervalMs: Long = INTERVAL_ACTIVE_MS
        private set
    var idlePollingIntervalMs: Long = INTERVAL_IDLE_DECAY_MS
        private set

    var isAmbientMode: Boolean = true
        private set

    var sourceMode: String = "REAL"
        private set

    fun setAmbientMode(ambient: Boolean) {
        isAmbientMode = ambient
    }

    fun setTelemetrySource(source: TelemetrySource) {
        this.telemetrySource = source
    }

    fun switchSource(mode: String) {
        sourceMode = mode
        val target = if (mode.equals("REAL", ignoreCase = true)) {
            realSource ?: telemetrySource
        } else {
            fakeSource ?: telemetrySource
        }
        this.telemetrySource = target
    }

    fun getTelemetrySource(): TelemetrySource = telemetrySource

    fun updatePollingIntervals(activeMs: Long, idleMs: Long) {
        if (activeMs > 0) activePollingIntervalMs = activeMs
        if (idleMs > 0) idlePollingIntervalMs = idleMs
    }

    fun start() {
        if (pollingJob?.isActive == true) return

        pollingJob = coroutineScope.launch {
            while (isActive) {
                val packet = withContext(Dispatchers.IO) {
                    telemetrySource.poll()
                }
                _lastPacket.value = packet
                val fused = processPacket(packet)
                val old = _snapshot.value
                val isSignificant = !isAmbientMode ||
                    old.timestampMs == 0L ||
                    abs(fused.cpu.smoothedValue - old.cpu.smoothedValue) > 0.015f ||
                    abs(fused.ram.smoothedValue - old.ram.smoothedValue) > 0.015f ||
                    abs(fused.network.smoothedValue - old.network.smoothedValue) > 0.02f ||
                    abs(fused.storageIo.smoothedValue - old.storageIo.smoothedValue) > 0.02f ||
                    abs(fused.gpu.smoothedValue - old.gpu.smoothedValue) > 0.02f ||
                    fused.worstThrottleState != old.worstThrottleState
                if (isSignificant) {
                    _snapshot.value = fused
                }

                // Adaptive polling logic:
                // Active polling (250ms) is warranted ONLY when foreground companion app is visible
                // AND system exhibits active stress: CPU > 30%, active network > 150 KB/s, GPU > 15%,
                // or critical throttling (meltdown, thermal crisis, severe storage stall).
                // When overlay is running ambiently in background OR system is nominal, back off to idle (2000ms).
                val isSystemActive = fused.cpu.smoothedValue > IDLE_LOAD_THRESHOLD ||
                    fused.network.smoothedValue > 0.15f ||
                    fused.gpu.smoothedValue > 0.15f ||
                    fused.worstThrottleState == ThrottleState.CRITICAL_THROTTLED ||
                    (fused.worstThrottleState == ThrottleState.WARNING_BOOST && fused.cpu.smoothedValue > 0.20f)

                currentIntervalMs = if (isAmbientMode || !isSystemActive) {
                    idlePollingIntervalMs
                } else {
                    activePollingIntervalMs
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
        _lastPacket.value = packet
        if (resetEma) {
            cpuEma.reset(TelemetryNormalizer.normalizeCpu(packet))
            ramEma.reset(TelemetryNormalizer.normalizeRam(packet))
            netEma.reset(TelemetryNormalizer.normalizeNetwork(packet))
            cellEma.reset(TelemetryNormalizer.normalizeCellularQuality(packet))
            storageEma.reset(TelemetryNormalizer.normalizeStorage(packet))
            gpuEma.reset(TelemetryNormalizer.normalizeGpu(packet))
        }
        val fused = processPacket(packet)
        _snapshot.value = fused
        return fused
    }

    fun injectSnapshot(snapshot: SystemTelemetrySnapshot) {
        _snapshot.value = snapshot
    }

    fun processPacket(packet: RawTelemetryPacket): SystemTelemetrySnapshot {
        val cpuRaw = TelemetryNormalizer.normalizeCpu(packet)
        val ramRaw = TelemetryNormalizer.normalizeRam(packet)
        val netRaw = TelemetryNormalizer.normalizeNetwork(packet)
        val cellRaw = TelemetryNormalizer.normalizeCellularQuality(packet)
        val storageRaw = TelemetryNormalizer.normalizeStorage(packet)
        val gpuRaw = TelemetryNormalizer.normalizeGpu(packet)

        val cpuSmoothed = cpuEma.filter(cpuRaw)
        val ramSmoothed = ramEma.filter(ramRaw)
        val netSmoothed = netEma.filter(netRaw)
        val cellSmoothed = cellEma.filter(cellRaw)
        val storageSmoothed = storageEma.filter(storageRaw)
        val gpuSmoothed = gpuEma.filter(gpuRaw)

        val cpuThrottle = ThrottlingClassifier.classifyCpu(packet)
        val ramThrottle = ThrottlingClassifier.classifyRam(packet)
        val cellThrottle = ThrottlingClassifier.classifyCellular(packet)
        val storageThrottle = ThrottlingClassifier.classifyStorage(packet)
        val gpuThrottle = ThrottlingClassifier.classifyGpu(packet)

        val worstState = maxOf(cpuThrottle, ramThrottle, cellThrottle, storageThrottle, gpuThrottle)

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
            storageIo = MetricValue(
                rawNormalized = storageRaw,
                smoothedValue = storageSmoothed,
                throttleState = storageThrottle,
                displayLabel = if (packet.isStorageStall) "STALL" else "${(storageSmoothed * 100).toInt()}%"
            ),
            gpu = MetricValue(
                rawNormalized = gpuRaw,
                smoothedValue = gpuSmoothed,
                throttleState = gpuThrottle,
                displayLabel = "${(gpuSmoothed * 100).toInt()}%"
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
