package com.vnazarov.resourcemonitor.animations.hologram

import com.vnazarov.resourcemonitor.core.designsystem.theme.NeonPalette
import com.vnazarov.resourcemonitor.core.model.MetricValue
import com.vnazarov.resourcemonitor.core.model.SystemTelemetrySnapshot
import com.vnazarov.resourcemonitor.core.model.ThrottleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HolographicRingsPluginTest {

    @Test
    fun `quadratic speed calculation matches specification limits`() {
        // At 0% load -> V_min = 0.2 RPS
        val speed0 = HolographicRingsPlugin.calculateQuadraticSpeed(0.0f)
        assertEquals(0.2f, speed0, 0.001f)

        // At 50% load -> 0.2 + (0.5)^2 * (5.0 - 0.2) = 0.2 + 0.25 * 4.8 = 1.4 RPS
        val speed50 = HolographicRingsPlugin.calculateQuadraticSpeed(0.5f)
        assertEquals(1.4f, speed50, 0.001f)

        // At 100% load -> V_max = 5.0 RPS
        val speed100 = HolographicRingsPlugin.calculateQuadraticSpeed(1.0f)
        assertEquals(5.0f, speed100, 0.001f)
    }

    @Test
    fun `linear and sigmoid speed curves calculate correct speeds`() {
        val linearConfig = HologramBehaviorConfig(speedCurve = SpeedCurve.LINEAR)
        val speedLin0 = HologramProjectionCalculator.calculateSpeed(0.0f, 1.0f, linearConfig)
        assertEquals(0.2f, speedLin0, 0.001f)

        val speedLin50 = HologramProjectionCalculator.calculateSpeed(0.5f, 1.0f, linearConfig)
        assertEquals(2.6f, speedLin50, 0.001f)

        val speedLin100 = HologramProjectionCalculator.calculateSpeed(1.0f, 1.0f, linearConfig)
        assertEquals(5.0f, speedLin100, 0.001f)

        val sigmoidConfig = HologramBehaviorConfig(speedCurve = SpeedCurve.SIGMOID)
        val speedSig50 = HologramProjectionCalculator.calculateSpeed(0.5f, 1.0f, sigmoidConfig)
        assertEquals(2.6f, speedSig50, 0.001f)

        val speedSig0 = HologramProjectionCalculator.calculateSpeed(0.0f, 1.0f, sigmoidConfig)
        assertEquals(0.232f, speedSig0, 0.005f)

        val speedSig100 = HologramProjectionCalculator.calculateSpeed(1.0f, 1.0f, sigmoidConfig)
        assertEquals(4.968f, speedSig100, 0.005f)
    }

    @Test
    fun `input sanitization handles NaN negative and overflow loads safely`() {
        val config = HologramBehaviorConfig(speedCurve = SpeedCurve.QUADRATIC)

        val speedNaN = HologramProjectionCalculator.calculateSpeed(Float.NaN, 1.0f, config)
        assertEquals(0.2f, speedNaN, 0.001f)

        val speedNegative = HologramProjectionCalculator.calculateSpeed(-0.5f, 1.0f, config)
        assertEquals(0.2f, speedNegative, 0.001f)

        val speedOverflow = HologramProjectionCalculator.calculateSpeed(1.5f, 1.0f, config)
        assertEquals(5.0f, speedOverflow, 0.001f)
    }

    @Test
    fun `sensitivity multiplier scales effective load correctly`() {
        val config = HologramBehaviorConfig(speedCurve = SpeedCurve.QUADRATIC)
        // 25% load with sensitivity 2.0 -> clamped load 0.5 -> 0.2 + 0.25 * 4.8 = 1.4 RPS
        val speedScaled = HologramProjectionCalculator.calculateSpeed(0.25f, 2.0f, config)
        assertEquals(1.4f, speedScaled, 0.001f)
    }

    @Test
    fun `parameter mapper sets meltdown alert on critical throttle`() {
        val plugin = HolographicRingsPlugin()
        val snapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.40f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            worstThrottleState = ThrottleState.CRITICAL_THROTTLED
        )

        val params = plugin.parameterMapper.map(snapshot, emptyMap())
        assertTrue(params.isMeltdownAlert)
        assertTrue(params.systemStatusLabel.contains("CRITICAL MELTDOWN"))
        assertEquals(NeonPalette.MeltdownRed, params.outerColor)
        assertEquals(NeonPalette.MeltdownRed, params.middleColor)
        assertEquals(NeonPalette.MeltdownRed, params.innerColor)
    }

    @Test
    fun `cellular degradation does not trigger system meltdown and updates status label`() {
        val plugin = HolographicRingsPlugin()
        // Cellular critical degradation (-125 dBm), CPU and RAM nominal
        val snapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.20f, throttleState = ThrottleState.NOMINAL),
            ram = MetricValue(smoothedValue = 0.30f, throttleState = ThrottleState.NOMINAL),
            network = MetricValue(smoothedValue = 0.10f, throttleState = ThrottleState.NOMINAL),
            cellularQuality = MetricValue(smoothedValue = 0.05f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            worstThrottleState = ThrottleState.CRITICAL_THROTTLED
        )

        val params = plugin.parameterMapper.map(snapshot, emptyMap())
        assertFalse(params.isMeltdownAlert)
        assertEquals(NeonPalette.CyanCpu, params.outerColor)
        assertEquals(NeonPalette.OrangeRam, params.middleColor)
        assertEquals(NeonPalette.MeltdownRed, params.innerColor)
        assertEquals("CELLULAR LINK LOST", params.systemStatusLabel)

        // Cellular warning boost (-110 dBm)
        val warningSnapshot = snapshot.copy(
            cellularQuality = MetricValue(smoothedValue = 0.40f, throttleState = ThrottleState.WARNING_BOOST),
            worstThrottleState = ThrottleState.WARNING_BOOST
        )
        val warningParams = plugin.parameterMapper.map(warningSnapshot, emptyMap())
        assertFalse(warningParams.isMeltdownAlert)
        assertEquals(NeonPalette.WarningAmber, warningParams.innerColor)
        assertEquals("CELLULAR SIGNAL DEGRADED", warningParams.systemStatusLabel)
    }

    @Test
    fun `ram thrashing critical state applies memory thrash purple to middle ring`() {
        val plugin = HolographicRingsPlugin()
        val snapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.20f, throttleState = ThrottleState.NOMINAL),
            ram = MetricValue(smoothedValue = 0.85f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            worstThrottleState = ThrottleState.CRITICAL_THROTTLED
        )

        val params = plugin.parameterMapper.map(snapshot, emptyMap())
        assertFalse(params.isMeltdownAlert)
        assertEquals(NeonPalette.CyanCpu, params.outerColor)
        assertEquals(NeonPalette.MemoryThrashPurple, params.middleColor)
    }

    @Test
    fun `parameter mapper sets nominal status during normal operation`() {
        val plugin = HolographicRingsPlugin()
        val snapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.25f),
            ram = MetricValue(smoothedValue = 0.35f),
            worstThrottleState = ThrottleState.NOMINAL
        )

        val params = plugin.parameterMapper.map(snapshot, emptyMap())
        assertFalse(params.isMeltdownAlert)
        assertTrue(params.systemStatusLabel.contains("SYSTEM NOMINAL"))
    }

    @Test
    fun `parameter mapper parses custom config map overrides`() {
        val plugin = HolographicRingsPlugin()
        val snapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.50f),
            ram = MetricValue(smoothedValue = 0.50f),
            network = MetricValue(smoothedValue = 0.50f)
        )

        val customConfig = mapOf<String, Any>(
            "vMinRps" to 0.5f,
            "vMaxRps" to 2.5f,
            "speedCurve" to "LINEAR",
            "outerSensitivity" to 1.0f,
            "middleSensitivity" to 1.0f,
            "innerSensitivity" to 1.0f
        )

        val params = plugin.parameterMapper.map(snapshot, customConfig)
        // Linear with vMin=0.5, vMax=2.5, range=2.0, load=0.5 -> 0.5 + 0.5 * 2.0 = 1.5 RPS
        assertEquals(1.5f, params.outerRingSpeedRps, 0.001f)
        assertEquals(1.5f, params.middleRingSpeedRps, 0.001f)
        assertEquals(1.5f, params.innerRingSpeedRps, 0.001f)
    }

    @Test
    fun `manifest registers all required config property definitions`() {
        val plugin = HolographicRingsPlugin()
        val propertyKeys = plugin.manifest.configProperties.map { it.key }.toSet()

        val expectedKeys = setOf(
            "bloomIntensity",
            "vMinRps",
            "vMaxRps",
            "speedCurve",
            "outerSensitivity",
            "middleSensitivity",
            "innerSensitivity",
            "boostThreshold",
            "meltdownThreshold"
        )

        for (key in expectedKeys) {
            assertTrue("Expected property $key in manifest", propertyKeys.contains(key))
        }
    }
}
