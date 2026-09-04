package com.vnazarov.resourcemonitor.animations.hologram

import androidx.compose.ui.graphics.Color
import com.vnazarov.resourcemonitor.core.model.SystemTelemetrySnapshot
import com.vnazarov.resourcemonitor.core.model.ThrottleState
import kotlin.math.exp

object HologramProjectionCalculator {

    fun calculateSpeed(
        normalizedLoad: Float,
        sensitivity: Float,
        config: HologramBehaviorConfig
    ): Float {
        val safeLoad = if (normalizedLoad.isNaN()) 0f else normalizedLoad
        val clampedLoad = (safeLoad * sensitivity).coerceIn(0f, 1f)
        val vMin = config.vMinRps
        val vMax = config.vMaxRps
        val vRange = vMax - vMin

        return when (config.speedCurve) {
            SpeedCurve.QUADRATIC -> vMin + (clampedLoad * clampedLoad) * vRange
            SpeedCurve.LINEAR -> vMin + clampedLoad * vRange
            SpeedCurve.SIGMOID -> {
                val sigmoid = 1f / (1f + exp(-10f * (clampedLoad - 0.5f)))
                vMin + sigmoid * vRange
            }
        }
    }

    fun computeParameters(
        snapshot: SystemTelemetrySnapshot,
        config: HologramBehaviorConfig = HologramBehaviorConfig(),
        bloomMultiplier: Float = 1.0f
    ): HolographicRingsParams {
        val cpuLoad = snapshot.cpu.smoothedValue
        val ramLoad = snapshot.ram.smoothedValue
        val netLoad = snapshot.network.smoothedValue
        val ssdLoad = snapshot.storageIo.smoothedValue
        val gpuLoad = snapshot.gpu.smoothedValue

        val speed1 = calculateSpeed(cpuLoad, config.outerSensitivity, config)
        val speed2 = calculateSpeed(ramLoad, config.middleSensitivity, config)
        val speed3 = calculateSpeed(netLoad, config.innerSensitivity, config)
        val speed4 = calculateSpeed(ssdLoad, config.storageSensitivity, config)

        // Case 25: Forced OS Severe Thermal Throttling pins R5 thermal corona to 3.00 RPS
        val isForcedOsThermal = snapshot.worstThrottleState == ThrottleState.CRITICAL_THROTTLED &&
                snapshot.cpu.throttleState == ThrottleState.CRITICAL_THROTTLED &&
                cpuLoad <= 0.65f && snapshot.gpu.throttleState == ThrottleState.CRITICAL_THROTTLED
        val speed5 = if (isForcedOsThermal) 3.000f else calculateSpeed(gpuLoad, config.gpuSensitivity, config)

        val isCpuMeltdown = snapshot.cpu.throttleState == ThrottleState.CRITICAL_THROTTLED ||
                cpuLoad >= config.meltdownThreshold ||
                (snapshot.worstThrottleState == ThrottleState.CRITICAL_THROTTLED && (cpuLoad >= 0.85f || isForcedOsThermal))

        val isStorageStall = snapshot.storageIo.throttleState == ThrottleState.CRITICAL_THROTTLED
        val isMemoryThrash = snapshot.ram.throttleState == ThrottleState.CRITICAL_THROTTLED

        val r1Color: Color
        val r2Color: Color
        val r3Color: Color
        val r4Color: Color
        val r5Color: Color

        if (isCpuMeltdown) {
            r1Color = config.alertMeltdownColor
            r2Color = config.alertMeltdownColor
            r3Color = config.alertMeltdownColor
            r4Color = if (isStorageStall) config.alertStorageFlareColor else config.alertMeltdownColor
            r5Color = config.alertMeltdownColor
        } else {
            r1Color = config.ring1BaseColor
            r2Color = if (isMemoryThrash) config.alertThrashPurpleColor else config.ring2BaseColor
            r3Color = when (snapshot.cellularQuality.throttleState) {
                ThrottleState.CRITICAL_THROTTLED -> config.alertMeltdownColor
                ThrottleState.WARNING_BOOST -> config.alertAmberColor
                else -> config.ring3BaseColor
            }
            r4Color = if (isStorageStall) config.alertStorageFlareColor else config.ring4BaseColor
            r5Color = if (snapshot.gpu.throttleState == ThrottleState.CRITICAL_THROTTLED) config.alertMeltdownColor else config.ring5BaseColor
        }

        val statusLabel = when {
            isStorageStall -> "STORAGE I/O STALL [IO_WAIT_STALL]"
            isCpuMeltdown -> "SYSTEM PEAK LOAD [CRITICAL MELTDOWN]"
            snapshot.cellularQuality.throttleState == ThrottleState.CRITICAL_THROTTLED -> "CELLULAR LINK LOST"
            snapshot.cellularQuality.throttleState == ThrottleState.WARNING_BOOST -> "CELLULAR SIGNAL DEGRADED"
            snapshot.cpu.throttleState == ThrottleState.WARNING_BOOST ||
                    cpuLoad >= config.boostThreshold ||
                    ramLoad >= config.boostThreshold ||
                    netLoad >= config.boostThreshold ||
                    ssdLoad >= config.boostThreshold ||
                    gpuLoad >= config.boostThreshold ||
                    isMemoryThrash -> "SYSTEM BOOST ACTIVE"
            else -> "SYSTEM NOMINAL [CPU ${(cpuLoad * 100).toInt()}% | RAM ${(ramLoad * 100).toInt()}%]"
        }

        val energyLabel = when {
            isCpuMeltdown -> "ENERGY OUTPUT: MAX EXCEEDED"
            cpuLoad >= config.boostThreshold || netLoad >= config.boostThreshold ||
                    ssdLoad >= config.boostThreshold || gpuLoad >= config.boostThreshold -> "ENERGY OUTPUT: HIGH"
            else -> "ENERGY OUTPUT: NOMINAL"
        }

        return HolographicRingsParams(
            ring1SpeedRps = speed1,
            ring2SpeedRps = speed2,
            ring3SpeedRps = speed3,
            ring4SpeedRps = speed4,
            ring5SpeedRps = speed5,
            ring1Color = r1Color,
            ring2Color = r2Color,
            ring3Color = r3Color,
            ring4Color = r4Color,
            ring5Color = r5Color,
            isMeltdownAlert = isCpuMeltdown,
            isStorageStallAlert = isStorageStall,
            isMemoryThrashAlert = isMemoryThrash,
            isCellularDegradedAlert = snapshot.cellularQuality.throttleState != ThrottleState.NOMINAL,
            bloomIntensity = if (isCpuMeltdown) bloomMultiplier * 1.5f else bloomMultiplier,
            systemStatusLabel = statusLabel,
            energyOutputLabel = energyLabel,
            outerRingSpeedRps = speed1,
            middleRingSpeedRps = speed2,
            innerRingSpeedRps = speed3,
            outerColor = r1Color,
            middleColor = r2Color,
            innerColor = r3Color
        )
    }
}
