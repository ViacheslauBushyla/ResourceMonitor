package com.vnazarov.resourcemonitor.animations.hologram

import com.vnazarov.resourcemonitor.core.designsystem.theme.NeonPalette
import com.vnazarov.resourcemonitor.core.model.MetricValue
import com.vnazarov.resourcemonitor.core.model.SystemTelemetrySnapshot
import com.vnazarov.resourcemonitor.core.model.ThrottleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.abs

class HologramProjectionChallengeTest {

    private val defaultConfig = HologramBehaviorConfig()

    // ---------------------------------------------------------------------------------------------
    // 1. Full Spectrum Mathematical Precision: QUADRATIC, LINEAR, SIGMOID
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `quadratic speed formula matches theoretical values within 0_0001 RPS across 1001 points`() {
        val vMin = defaultConfig.vMinRps
        val vMax = defaultConfig.vMaxRps
        val vRange = vMax - vMin

        for (i in 0..1000) {
            val load = i / 1000.0f
            val actual = HologramProjectionCalculator.calculateSpeed(load, 1.0f, defaultConfig)
            val theoretical = vMin + (load * load) * vRange

            assertEquals("Quadratic mismatch at load $load", theoretical, actual, 0.0001f)
            assertTrue("Tolerance exceeded +/- 0.01 RPS at load $load", abs(actual - theoretical) <= 0.01f)
            assertTrue("Speed out of bounds [$vMin, $vMax] at load $load", actual in vMin..vMax)
        }
    }

    @Test
    fun `linear speed formula matches theoretical values within 0_0001 RPS across 1001 points`() {
        val config = HologramBehaviorConfig(speedCurve = SpeedCurve.LINEAR)
        val vMin = config.vMinRps
        val vMax = config.vMaxRps
        val vRange = vMax - vMin

        for (i in 0..1000) {
            val load = i / 1000.0f
            val actual = HologramProjectionCalculator.calculateSpeed(load, 1.0f, config)
            val theoretical = vMin + load * vRange

            assertEquals("Linear mismatch at load $load", theoretical, actual, 0.0001f)
            assertTrue("Tolerance exceeded +/- 0.01 RPS at load $load", abs(actual - theoretical) <= 0.01f)
            assertTrue("Speed out of bounds [$vMin, $vMax] at load $load", actual in vMin..vMax)
        }
    }

    @Test
    fun `sigmoid speed formula matches theoretical formula within 0_0001 RPS across 1001 points`() {
        val config = HologramBehaviorConfig(speedCurve = SpeedCurve.SIGMOID)
        val vMin = config.vMinRps
        val vMax = config.vMaxRps
        val vRange = vMax - vMin

        for (i in 0..1000) {
            val load = i / 1000.0f
            val actual = HologramProjectionCalculator.calculateSpeed(load, 1.0f, config)
            val sigmoid = 1.0f / (1.0f + exp(-10.0f * (load - 0.5f)))
            val theoretical = vMin + sigmoid * vRange

            assertEquals("Sigmoid mismatch at load $load", theoretical, actual, 0.0001f)
            assertTrue("Tolerance exceeded +/- 0.01 RPS at load $load", abs(actual - theoretical) <= 0.01f)
            assertTrue("Speed out of bounds [$vMin, $vMax] at load $load", actual in vMin..vMax)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 2. Edge Loads: Negative, Overflow, NaN, Infs
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `edge loads clamp safely to theoretical limits without crash or NaN`() {
        val testLoads = listOf(
            -1000f, -1.0f, -0.0001f, -0.0f, 0.0f,
            1.0f, 1.0001f, 1.5f, 2.0f, 100.0f, Float.MAX_VALUE,
            Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NaN
        )

        for (curve in SpeedCurve.values()) {
            val config = HologramBehaviorConfig(speedCurve = curve)
            val vMin = config.vMinRps
            val vMax = config.vMaxRps
            val vRange = vMax - vMin

            for (load in testLoads) {
                val speed = HologramProjectionCalculator.calculateSpeed(load, 1.0f, config)
                assertFalse("Speed must not be NaN for curve $curve, load $load", speed.isNaN())
                assertFalse("Speed must not be Infinite for curve $curve, load $load", speed.isInfinite())

                when {
                    load.isNaN() || load <= 0f -> {
                        val expected = when (curve) {
                            SpeedCurve.QUADRATIC, SpeedCurve.LINEAR -> vMin
                            SpeedCurve.SIGMOID -> vMin + (1f / (1f + exp(5f))) * vRange
                        }
                        assertEquals("Expected min boundary for curve $curve at load $load", expected, speed, 0.001f)
                    }
                    load >= 1f -> {
                        val expected = when (curve) {
                            SpeedCurve.QUADRATIC, SpeedCurve.LINEAR -> vMax
                            SpeedCurve.SIGMOID -> vMin + (1f / (1f + exp(-5f))) * vRange
                        }
                        assertEquals("Expected max boundary for curve $curve at load $load", expected, speed, 0.001f)
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 3. Sensitivity Multiplier Testing (0.1, 1.0, 2.0, Out of Bounds)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `sensitivity multiplier edge cases 0_1, 1_0, 2_0 scale load accurately`() {
        val config = HologramBehaviorConfig(speedCurve = SpeedCurve.QUADRATIC)

        // Sensitivity 0.1
        // Load 1.0 -> effective load 0.1 -> 0.2 + (0.1)^2 * 4.8 = 0.2 + 0.01 * 4.8 = 0.248 RPS
        val speed01_100 = HologramProjectionCalculator.calculateSpeed(1.0f, 0.1f, config)
        assertEquals(0.248f, speed01_100, 0.001f)

        // Load 0.5 -> effective load 0.05 -> 0.2 + (0.05)^2 * 4.8 = 0.2 + 0.0025 * 4.8 = 0.212 RPS
        val speed01_50 = HologramProjectionCalculator.calculateSpeed(0.5f, 0.1f, config)
        assertEquals(0.212f, speed01_50, 0.001f)

        // Sensitivity 1.0
        val speed10_50 = HologramProjectionCalculator.calculateSpeed(0.5f, 1.0f, config)
        assertEquals(1.4f, speed10_50, 0.001f)

        // Sensitivity 2.0
        // Load 0.25 -> effective load 0.5 -> 1.4 RPS
        val speed20_25 = HologramProjectionCalculator.calculateSpeed(0.25f, 2.0f, config)
        assertEquals(1.4f, speed20_25, 0.001f)

        // Load 0.5 -> effective load 1.0 -> 5.0 RPS
        val speed20_50 = HologramProjectionCalculator.calculateSpeed(0.5f, 2.0f, config)
        assertEquals(5.0f, speed20_50, 0.001f)

        // Load 0.8 -> effective load 1.6 clamped to 1.0 -> 5.0 RPS
        val speed20_80 = HologramProjectionCalculator.calculateSpeed(0.8f, 2.0f, config)
        assertEquals(5.0f, speed20_80, 0.001f)
    }

    @Test
    fun `out of bounds sensitivity multipliers clamp safely`() {
        val config = HologramBehaviorConfig(speedCurve = SpeedCurve.QUADRATIC)

        // Negative sensitivity (-1.0f)
        // 0.5 * -1.0 = -0.5 -> clamped to 0.0 -> V_min
        val speedNegSens = HologramProjectionCalculator.calculateSpeed(0.5f, -1.0f, config)
        assertEquals(0.2f, speedNegSens, 0.001f)

        // Zero sensitivity (0.0f)
        val speedZeroSens = HologramProjectionCalculator.calculateSpeed(0.5f, 0.0f, config)
        assertEquals(0.2f, speedZeroSens, 0.001f)

        // Huge sensitivity (100.0f)
        val speedHugeSens = HologramProjectionCalculator.calculateSpeed(0.05f, 100.0f, config)
        assertEquals(5.0f, speedHugeSens, 0.001f)

        // Infinite sensitivity
        val speedInfSens = HologramProjectionCalculator.calculateSpeed(0.1f, Float.POSITIVE_INFINITY, config)
        assertEquals(5.0f, speedInfSens, 0.001f)
    }

    // ---------------------------------------------------------------------------------------------
    // 4. Custom Speed Bounds & Degenerate Configurations
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `custom speed boundaries vMin and vMax produce exact proportions`() {
        val customConfig = HologramBehaviorConfig(
            vMinRps = 1.0f,
            vMaxRps = 10.0f,
            speedCurve = SpeedCurve.LINEAR
        )

        val speed0 = HologramProjectionCalculator.calculateSpeed(0.0f, 1.0f, customConfig)
        assertEquals(1.0f, speed0, 0.001f)

        val speed50 = HologramProjectionCalculator.calculateSpeed(0.5f, 1.0f, customConfig)
        assertEquals(5.5f, speed50, 0.001f)

        val speed100 = HologramProjectionCalculator.calculateSpeed(1.0f, 1.0f, customConfig)
        assertEquals(10.0f, speed100, 0.001f)
    }

    // ---------------------------------------------------------------------------------------------
    // 5. Decoupled Meltdown & Alert State Precedence Matrix
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `meltdown alert is strictly confined to CPU load or CPU critical throttle`() {
        // RAM critical, Cellular critical, CPU nominal -> NO meltdown
        val ramCellCritical = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.50f, throttleState = ThrottleState.NOMINAL),
            ram = MetricValue(smoothedValue = 0.95f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            network = MetricValue(smoothedValue = 0.20f, throttleState = ThrottleState.NOMINAL),
            cellularQuality = MetricValue(smoothedValue = 0.01f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            worstThrottleState = ThrottleState.CRITICAL_THROTTLED
        )
        val params1 = HologramProjectionCalculator.computeParameters(ramCellCritical)
        assertFalse("RAM/Cellular critical must not trigger isMeltdownAlert", params1.isMeltdownAlert)
        assertEquals(NeonPalette.CyanCpu, params1.outerColor)
        assertEquals(NeonPalette.MemoryThrashPurple, params1.middleColor)
        assertEquals(NeonPalette.MeltdownRed, params1.innerColor)
        assertEquals("CELLULAR LINK LOST", params1.systemStatusLabel)

        // CPU load >= 0.90 -> MELTDOWN
        val cpuOverload = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.92f, throttleState = ThrottleState.NOMINAL),
            worstThrottleState = ThrottleState.NOMINAL
        )
        val params2 = HologramProjectionCalculator.computeParameters(cpuOverload)
        assertTrue("CPU >= 0.90 must trigger isMeltdownAlert", params2.isMeltdownAlert)
        assertEquals(NeonPalette.MeltdownRed, params2.outerColor)
        assertEquals(NeonPalette.MeltdownRed, params2.middleColor)
        assertEquals(NeonPalette.MeltdownRed, params2.innerColor)
        assertEquals("SYSTEM PEAK LOAD [CRITICAL MELTDOWN]", params2.systemStatusLabel)
        assertEquals("ENERGY OUTPUT: MAX EXCEEDED", params2.energyOutputLabel)
    }

    @Test
    fun `status label precedence orders meltdown then cellular loss then cellular degraded then boost`() {
        // Case A: CPU Meltdown takes top priority even if cellular is also lost
        val meltdownAndCellLost = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.95f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            cellularQuality = MetricValue(smoothedValue = 0.0f, throttleState = ThrottleState.CRITICAL_THROTTLED)
        )
        val paramsA = HologramProjectionCalculator.computeParameters(meltdownAndCellLost)
        assertEquals("SYSTEM PEAK LOAD [CRITICAL MELTDOWN]", paramsA.systemStatusLabel)

        // Case B: Cellular Link Lost takes priority over general system boost
        val cellLostAndBoost = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.85f, throttleState = ThrottleState.WARNING_BOOST),
            cellularQuality = MetricValue(smoothedValue = 0.0f, throttleState = ThrottleState.CRITICAL_THROTTLED)
        )
        val paramsB = HologramProjectionCalculator.computeParameters(cellLostAndBoost)
        assertEquals("CELLULAR LINK LOST", paramsB.systemStatusLabel)

        // Case C: Cellular Signal Degraded takes priority over general system boost
        val cellDegradedAndBoost = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.85f, throttleState = ThrottleState.WARNING_BOOST),
            cellularQuality = MetricValue(smoothedValue = 0.2f, throttleState = ThrottleState.WARNING_BOOST)
        )
        val paramsC = HologramProjectionCalculator.computeParameters(cellDegradedAndBoost)
        assertEquals("CELLULAR SIGNAL DEGRADED", paramsC.systemStatusLabel)

        // Case D: System Boost active when no cellular issues
        val boostOnly = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.75f, throttleState = ThrottleState.WARNING_BOOST),
            cellularQuality = MetricValue(smoothedValue = 0.8f, throttleState = ThrottleState.NOMINAL)
        )
        val paramsD = HologramProjectionCalculator.computeParameters(boostOnly)
        assertEquals("SYSTEM BOOST ACTIVE", paramsD.systemStatusLabel)
    }

    // ---------------------------------------------------------------------------------------------
    // 6. Adversarial Stress: NaN Sensitivity, Subnormal Values, Full NaN Snapshot
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `full NaN snapshot computes safe nominal parameters without NaN speeds or crash`() {
        val nanSnapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = Float.NaN),
            ram = MetricValue(smoothedValue = Float.NaN),
            network = MetricValue(smoothedValue = Float.NaN),
            cellularQuality = MetricValue(smoothedValue = Float.NaN)
        )

        val params = HologramProjectionCalculator.computeParameters(nanSnapshot)
        assertFalse("outer speed must not be NaN", params.outerRingSpeedRps.isNaN())
        assertFalse("middle speed must not be NaN", params.middleRingSpeedRps.isNaN())
        assertFalse("inner speed must not be NaN", params.innerRingSpeedRps.isNaN())
        assertEquals(0.2f, params.outerRingSpeedRps, 0.001f)
        assertEquals(0.2f, params.middleRingSpeedRps, 0.001f)
        assertEquals(0.2f, params.innerRingSpeedRps, 0.001f)
        assertFalse("Must not trigger meltdown on NaN", params.isMeltdownAlert)
        assertEquals(NeonPalette.CyanCpu, params.outerColor)
        assertEquals(NeonPalette.OrangeRam, params.middleColor)
        assertEquals(NeonPalette.MagentaGpuNet, params.innerColor)
        assertEquals("SYSTEM NOMINAL [CPU 0% | RAM 0%]", params.systemStatusLabel)
    }

    @Test
    fun `subnormal and epsilon loads behave smoothly without underflow issues`() {
        val subnormalLoads = listOf(
            Float.MIN_VALUE,
            1e-10f,
            1e-7f,
            1e-4f
        )
        for (curve in SpeedCurve.values()) {
            val config = HologramBehaviorConfig(speedCurve = curve)
            for (load in subnormalLoads) {
                val speed = HologramProjectionCalculator.calculateSpeed(load, 1.0f, config)
                assertFalse("Speed must not be NaN", speed.isNaN())
                assertTrue("Speed must be >= vMin", speed >= config.vMinRps)
            }
        }
    }

    @Test
    fun `adversarial check on sensitivity NaN propagation`() {
        val config = HologramBehaviorConfig(speedCurve = SpeedCurve.QUADRATIC)
        val speed = HologramProjectionCalculator.calculateSpeed(0.5f, Float.NaN, config)
        println("Sensitivity NaN produced speed: $speed")
        // Note: Float.NaN * load yields NaN, which coerceIn does not alter.
        // Therefore calculateSpeed returns NaN when sensitivity is NaN.
        assertTrue("Identified: sensitivity NaN propagates to output speed", speed.isNaN())
    }
}
