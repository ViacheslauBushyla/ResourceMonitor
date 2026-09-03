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

        val outerSpeed = calculateSpeed(cpuLoad, config.outerSensitivity, config)
        val middleSpeed = calculateSpeed(ramLoad, config.middleSensitivity, config)
        val innerSpeed = calculateSpeed(netLoad, config.innerSensitivity, config)

        val isCpuMeltdown = snapshot.cpu.throttleState == ThrottleState.CRITICAL_THROTTLED ||
                cpuLoad >= config.meltdownThreshold

        val outerColor: Color
        val middleColor: Color
        val innerColor: Color

        if (isCpuMeltdown) {
            outerColor = config.alertMeltdownColor
            middleColor = config.alertMeltdownColor
            innerColor = config.alertMeltdownColor
        } else {
            outerColor = config.outerBaseColor
            middleColor = if (snapshot.ram.throttleState == ThrottleState.CRITICAL_THROTTLED) {
                config.alertThrashPurpleColor
            } else {
                config.middleBaseColor
            }
            innerColor = when (snapshot.cellularQuality.throttleState) {
                ThrottleState.CRITICAL_THROTTLED -> config.alertMeltdownColor
                ThrottleState.WARNING_BOOST -> config.alertAmberColor
                else -> config.innerBaseColor
            }
        }

        val statusLabel = when {
            isCpuMeltdown -> "SYSTEM PEAK LOAD [CRITICAL MELTDOWN]"
            snapshot.cellularQuality.throttleState == ThrottleState.CRITICAL_THROTTLED -> "CELLULAR LINK LOST"
            snapshot.cellularQuality.throttleState == ThrottleState.WARNING_BOOST -> "CELLULAR SIGNAL DEGRADED"
            snapshot.cpu.throttleState == ThrottleState.WARNING_BOOST ||
                    cpuLoad >= config.boostThreshold ||
                    ramLoad >= config.boostThreshold ||
                    netLoad >= config.boostThreshold -> "SYSTEM BOOST ACTIVE"
            else -> "SYSTEM NOMINAL [CPU ${(cpuLoad * 100).toInt()}% | RAM ${(ramLoad * 100).toInt()}%]"
        }

        val energyLabel = when {
            isCpuMeltdown -> "ENERGY OUTPUT: MAX EXCEEDED"
            cpuLoad >= config.boostThreshold || netLoad >= config.boostThreshold -> "ENERGY OUTPUT: HIGH"
            else -> "ENERGY OUTPUT: NOMINAL"
        }

        return HolographicRingsParams(
            outerRingSpeedRps = outerSpeed,
            middleRingSpeedRps = middleSpeed,
            innerRingSpeedRps = innerSpeed,
            outerColor = outerColor,
            middleColor = middleColor,
            innerColor = innerColor,
            isMeltdownAlert = isCpuMeltdown,
            bloomIntensity = if (isCpuMeltdown) bloomMultiplier * 1.5f else bloomMultiplier,
            systemStatusLabel = statusLabel,
            energyOutputLabel = energyLabel
        )
    }
}
