package com.vnazarov.resourcemonitor.animations.hologram

import androidx.compose.ui.graphics.Color
import com.vnazarov.resourcemonitor.core.designsystem.theme.NeonPalette
import com.vnazarov.resourcemonitor.core.model.MetricValue
import com.vnazarov.resourcemonitor.core.model.SystemTelemetrySnapshot
import com.vnazarov.resourcemonitor.core.model.ThrottleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

class HologramInvariantMatrixTest {

    private val speedToleranceRps = 0.01f

    @Test
    fun test1_all30InvariantCasesVerifyRotationalSpeedsMatchTheoreticalCalculations() {
        assertEquals("Master invariant case suite must contain exactly 30 entries", 30, HologramInvariantCases.ALL_CASES.size)

        for (case in HologramInvariantCases.ALL_CASES) {
            val params = HologramProjectionCalculator.computeParameters(case.snapshot)

            assertEquals(
                "Case #${case.id} [${case.name}] outer circle rotational speed mismatch",
                case.expectedOuterSpeedRps,
                params.outerRingSpeedRps,
                speedToleranceRps
            )
            assertEquals(
                "Case #${case.id} [${case.name}] middle circle rotational speed mismatch",
                case.expectedMiddleSpeedRps,
                params.middleRingSpeedRps,
                speedToleranceRps
            )
            assertEquals(
                "Case #${case.id} [${case.name}] inner circle rotational speed mismatch",
                case.expectedInnerSpeedRps,
                params.innerRingSpeedRps,
                speedToleranceRps
            )

            assertTrue(
                "Case #${case.id} outer speed out of bounds: ${params.outerRingSpeedRps}",
                params.outerRingSpeedRps in 0.200f..5.000f
            )
            assertTrue(
                "Case #${case.id} middle speed out of bounds: ${params.middleRingSpeedRps}",
                params.middleRingSpeedRps in 0.200f..5.000f
            )
            assertTrue(
                "Case #${case.id} inner speed out of bounds: ${params.innerRingSpeedRps}",
                params.innerRingSpeedRps in 0.200f..5.000f
            )
        }
    }

    @Test
    fun test2_all30InvariantCasesVerifyCircleColorsStrictlyFollowPaletteRules() {
        for (case in HologramInvariantCases.ALL_CASES) {
            val params = HologramProjectionCalculator.computeParameters(case.snapshot)

            assertEquals(
                "Case #${case.id} [${case.name}] outer color mismatch",
                case.expectedOuterColor,
                params.outerColor
            )
            assertEquals(
                "Case #${case.id} [${case.name}] middle color mismatch",
                case.expectedMiddleColor,
                params.middleColor
            )
            assertEquals(
                "Case #${case.id} [${case.name}] inner color mismatch",
                case.expectedInnerColor,
                params.innerColor
            )
        }

        // Specific Alert Gamut Validation: Case 28 (zRAM Memory Thrashing)
        val case28 = HologramInvariantCases.getById(28)
            ?: throw IllegalStateException("Case 28 missing")
        val params28 = HologramProjectionCalculator.computeParameters(case28.snapshot)
        assertEquals("Case 28 middle ring MUST use MemoryThrashPurple", NeonPalette.MemoryThrashPurple, params28.middleColor)
        assertEquals("Case 28 outer ring MUST remain CyanCpu", NeonPalette.CyanCpu, params28.outerColor)
        assertEquals("Case 28 inner ring MUST remain MagentaGpuNet", NeonPalette.MagentaGpuNet, params28.innerColor)
        assertFalse("Case 28 MUST NOT trigger meltdown alert", params28.isMeltdownAlert)

        // Specific Alert Gamut Validation: Cases 21..23 (Cellular RF Degradation)
        for (id in 21..23) {
            val cellCase = HologramInvariantCases.getById(id)
                ?: throw IllegalStateException("Case $id missing")
            val paramsCell = HologramProjectionCalculator.computeParameters(cellCase.snapshot)
            assertEquals("Case $id inner ring MUST use MeltdownRed alert", NeonPalette.MeltdownRed, paramsCell.innerColor)
            assertEquals("Case $id outer ring MUST remain CyanCpu", NeonPalette.CyanCpu, paramsCell.outerColor)
            assertEquals("Case $id middle ring MUST remain OrangeRam", NeonPalette.OrangeRam, paramsCell.middleColor)
            assertFalse("Case $id MUST NOT trigger meltdown alert", paramsCell.isMeltdownAlert)
        }

        // Meltdown Cases (7, 15, 20, 25, 27, 30): All rings must be MeltdownRed
        val meltdownIds = listOf(7, 15, 20, 25, 27, 30)
        for (id in meltdownIds) {
            val meltdownCase = HologramInvariantCases.getById(id)
                ?: throw IllegalStateException("Case $id missing")
            val paramsMeltdown = HologramProjectionCalculator.computeParameters(meltdownCase.snapshot)
            assertTrue("Case $id MUST trigger meltdown alert", paramsMeltdown.isMeltdownAlert)
            assertEquals("Case $id outer color MUST be MeltdownRed", NeonPalette.MeltdownRed, paramsMeltdown.outerColor)
            assertEquals("Case $id middle color MUST be MeltdownRed", NeonPalette.MeltdownRed, paramsMeltdown.middleColor)
            assertEquals("Case $id inner color MUST be MeltdownRed", NeonPalette.MeltdownRed, paramsMeltdown.innerColor)
        }

        // Baseline Cases 1..5: Nominal Gamut
        for (id in 1..5) {
            val baseCase = HologramInvariantCases.getById(id)
                ?: throw IllegalStateException("Case $id missing")
            val paramsBase = HologramProjectionCalculator.computeParameters(baseCase.snapshot)
            assertEquals("Case $id outer color MUST be CyanCpu", NeonPalette.CyanCpu, paramsBase.outerColor)
            assertEquals("Case $id middle color MUST be OrangeRam", NeonPalette.OrangeRam, paramsBase.middleColor)
            assertEquals("Case $id inner color MUST be MagentaGpuNet", NeonPalette.MagentaGpuNet, paramsBase.innerColor)
            assertFalse("Case $id MUST NOT trigger meltdown", paramsBase.isMeltdownAlert)
        }
    }

    @Test
    fun test3_meltdownAlertBooleanStatusLabelsAndEnergyOutputLabelsMatchAssertions() {
        for (case in HologramInvariantCases.ALL_CASES) {
            val params = HologramProjectionCalculator.computeParameters(case.snapshot)

            assertEquals(
                "Case #${case.id} [${case.name}] meltdown alert flag mismatch",
                case.expectedIsMeltdown,
                params.isMeltdownAlert
            )
            assertTrue(
                "Case #${case.id} [${case.name}] status label '${params.systemStatusLabel}' does not contain expected substring '${case.expectedStatusSubstring}'",
                params.systemStatusLabel.contains(case.expectedStatusSubstring)
            )
            assertEquals(
                "Case #${case.id} [${case.name}] energy output label mismatch",
                case.expectedEnergyLabel,
                params.energyOutputLabel
            )
        }
    }

    @Test
    fun test4_mathematicalRobustnessNegativeAndOverflowLoadsClampedSafely() {
        val config = HologramBehaviorConfig()

        // Case 29: Negative inputs
        val case29 = HologramInvariantCases.getById(29)
            ?: throw IllegalStateException("Case 29 missing")
        val params29 = HologramProjectionCalculator.computeParameters(case29.snapshot, config)
        assertEquals("Case 29 outer speed clamped to V_min", 0.200f, params29.outerRingSpeedRps, 0.001f)
        assertEquals("Case 29 middle speed clamped to V_min", 0.200f, params29.middleRingSpeedRps, 0.001f)
        assertEquals("Case 29 inner speed clamped to V_min", 0.200f, params29.innerRingSpeedRps, 0.001f)
        assertFalse("Case 29 outer speed must not be NaN", params29.outerRingSpeedRps.isNaN())
        assertFalse("Case 29 outer speed must not be infinite", params29.outerRingSpeedRps.isInfinite())
        assertFalse("Case 29 must not be meltdown", params29.isMeltdownAlert)

        // Case 30: Overflow inputs
        val case30 = HologramInvariantCases.getById(30)
            ?: throw IllegalStateException("Case 30 missing")
        val params30 = HologramProjectionCalculator.computeParameters(case30.snapshot, config)
        assertEquals("Case 30 outer speed clamped to V_max", 5.000f, params30.outerRingSpeedRps, 0.001f)
        assertEquals("Case 30 middle speed clamped to V_max", 5.000f, params30.middleRingSpeedRps, 0.001f)
        assertEquals("Case 30 inner speed clamped to V_max", 5.000f, params30.innerRingSpeedRps, 0.001f)
        assertFalse("Case 30 outer speed must not be NaN", params30.outerRingSpeedRps.isNaN())
        assertFalse("Case 30 outer speed must not be infinite", params30.outerRingSpeedRps.isInfinite())
        assertTrue("Case 30 must be meltdown", params30.isMeltdownAlert)

        // Extreme Non-Finite & Floating Boundary Inputs
        val nanSpeed = HologramProjectionCalculator.calculateSpeed(Float.NaN, 1.0f, config)
        assertEquals("NaN load must safely clamp to V_min", 0.200f, nanSpeed, 0.001f)

        val negInfSpeed = HologramProjectionCalculator.calculateSpeed(Float.NEGATIVE_INFINITY, 1.0f, config)
        assertEquals("-Infinity load must clamp to V_min", 0.200f, negInfSpeed, 0.001f)

        val posInfSpeed = HologramProjectionCalculator.calculateSpeed(Float.POSITIVE_INFINITY, 1.0f, config)
        assertEquals("+Infinity load must clamp to V_max", 5.000f, posInfSpeed, 0.001f)

        val hugeNegativeSpeed = HologramProjectionCalculator.calculateSpeed(-1_000_000f, 1.0f, config)
        assertEquals("Huge negative load must clamp to V_min", 0.200f, hugeNegativeSpeed, 0.001f)

        val hugePositiveSpeed = HologramProjectionCalculator.calculateSpeed(1_000_000f, 1.0f, config)
        assertEquals("Huge positive load must clamp to V_max", 5.000f, hugePositiveSpeed, 0.001f)
    }

    @Test
    fun test5_parameterOverridesDeterministicallyAlterOutputSpeeds() {
        // 1. Custom Base Speeds (vMinRps = 0.5f, vMaxRps = 8.0f)
        val customSpeedConfig = HologramBehaviorConfig(
            vMinRps = 0.5f,
            vMaxRps = 8.0f,
            speedCurve = SpeedCurve.QUADRATIC
        )
        // At 0% load: 0.5f
        val speed0 = HologramProjectionCalculator.calculateSpeed(0.0f, 1.0f, customSpeedConfig)
        assertEquals(0.500f, speed0, 0.001f)
        // At 50% load: 0.5 + 0.25 * 7.5 = 2.375f
        val speed50 = HologramProjectionCalculator.calculateSpeed(0.5f, 1.0f, customSpeedConfig)
        assertEquals(2.375f, speed50, 0.001f)
        // At 100% load: 8.0f
        val speed100 = HologramProjectionCalculator.calculateSpeed(1.0f, 1.0f, customSpeedConfig)
        assertEquals(8.000f, speed100, 0.001f)

        // 2. Speed Curve Switching: LINEAR
        val linearConfig = HologramBehaviorConfig(speedCurve = SpeedCurve.LINEAR)
        assertEquals(0.200f, HologramProjectionCalculator.calculateSpeed(0.0f, 1.0f, linearConfig), 0.001f)
        assertEquals(1.400f, HologramProjectionCalculator.calculateSpeed(0.25f, 1.0f, linearConfig), 0.001f)
        assertEquals(2.600f, HologramProjectionCalculator.calculateSpeed(0.50f, 1.0f, linearConfig), 0.001f)
        assertEquals(5.000f, HologramProjectionCalculator.calculateSpeed(1.0f, 1.0f, linearConfig), 0.001f)

        // 3. Speed Curve Switching: SIGMOID
        val sigmoidConfig = HologramBehaviorConfig(speedCurve = SpeedCurve.SIGMOID)
        val sig0Expected = 0.2f + (1f / (1f + exp(5f))) * 4.8f // ~0.232f
        val sig50Expected = 0.2f + 0.5f * 4.8f // 2.600f
        val sig100Expected = 0.2f + (1f / (1f + exp(-5f))) * 4.8f // ~4.968f
        assertEquals(sig0Expected, HologramProjectionCalculator.calculateSpeed(0.0f, 1.0f, sigmoidConfig), 0.01f)
        assertEquals(sig50Expected, HologramProjectionCalculator.calculateSpeed(0.50f, 1.0f, sigmoidConfig), 0.01f)
        assertEquals(sig100Expected, HologramProjectionCalculator.calculateSpeed(1.00f, 1.0f, sigmoidConfig), 0.01f)

        // 4. Per-Circle Sensitivity Multipliers (outer=1.5f, middle=0.5f, inner=2.0f)
        val sensitivityConfig = HologramBehaviorConfig(
            outerSensitivity = 1.5f,
            middleSensitivity = 0.5f,
            innerSensitivity = 2.0f,
            speedCurve = SpeedCurve.QUADRATIC
        )
        val snapshotMulti = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.50f),
            ram = MetricValue(smoothedValue = 0.80f),
            network = MetricValue(smoothedValue = 0.60f)
        )
        val paramsMulti = HologramProjectionCalculator.computeParameters(snapshotMulti, sensitivityConfig)
        // Outer: (0.50 * 1.5) = 0.75 -> 0.2 + 0.75^2 * 4.8 = 2.900f
        assertEquals(2.900f, paramsMulti.outerRingSpeedRps, speedToleranceRps)
        // Middle: (0.80 * 0.5) = 0.40 -> 0.2 + 0.40^2 * 4.8 = 0.968f
        assertEquals(0.968f, paramsMulti.middleRingSpeedRps, speedToleranceRps)
        // Inner: (0.60 * 2.0) = 1.20 -> clamped to 1.00 -> 5.000f
        assertEquals(5.000f, paramsMulti.innerRingSpeedRps, speedToleranceRps)

        // 5. Threshold Overrides (meltdownThreshold = 0.85f, boostThreshold = 0.60f)
        val defaultThresholdConfig = HologramBehaviorConfig()
        val customThresholdConfig = HologramBehaviorConfig(
            meltdownThreshold = 0.85f,
            boostThreshold = 0.60f
        )
        val snapshotThreshold = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.86f),
            ram = MetricValue(smoothedValue = 0.30f),
            network = MetricValue(smoothedValue = 0.20f)
        )
        // Default: 0.86 < 0.90 -> No meltdown
        val paramsDefault = HologramProjectionCalculator.computeParameters(snapshotThreshold, defaultThresholdConfig)
        assertFalse("Under default 0.90 threshold, CPU 0.86 must NOT trigger meltdown", paramsDefault.isMeltdownAlert)
        assertEquals(NeonPalette.CyanCpu, paramsDefault.outerColor)

        // Custom: 0.86 >= 0.85 -> Meltdown!
        val paramsCustom = HologramProjectionCalculator.computeParameters(snapshotThreshold, customThresholdConfig)
        assertTrue("Under custom 0.85 threshold, CPU 0.86 MUST trigger meltdown", paramsCustom.isMeltdownAlert)
        assertEquals(NeonPalette.MeltdownRed, paramsCustom.outerColor)
        assertEquals(NeonPalette.MeltdownRed, paramsCustom.middleColor)
        assertEquals(NeonPalette.MeltdownRed, paramsCustom.innerColor)
        assertEquals("ENERGY OUTPUT: MAX EXCEEDED", paramsCustom.energyOutputLabel)
    }

    @Test
    fun test6_toRawPacketGeneratesValidTelemetryForSimulation() {
        for (case in HologramInvariantCases.ALL_CASES) {
            val packet = case.toRawPacket()
            assertNotNull("RawTelemetryPacket must not be null", packet)
            assertEquals(
                "Packet CPU load percentage must match snapshot",
                case.snapshot.cpu.smoothedValue * 100f,
                packet.cpuLoadPercentage,
                0.01f
            )
            assertTrue("Packet RAM total bytes must be positive", packet.ramTotalBytes > 0L)
            assertTrue("Packet RAM available bytes must be non-negative", packet.ramAvailableBytes >= 0L)
        }

        // Check Case 28 (zRAM Thrashing) packet markers
        val case28 = HologramInvariantCases.getById(28)!!
        val packet28 = case28.toRawPacket()
        assertTrue("Case 28 packet must exhibit high zRAM swap", packet28.zRamUsedBytes > 2_000_000_000L)
        assertTrue("Case 28 packet must exhibit compact stalls", packet28.compactStallsCount > 100L)

        // Check Case 25 (CPU Thermal Capping) packet markers
        val case25 = HologramInvariantCases.getById(25)!!
        val packet25 = case25.toRawPacket()
        assertEquals("Case 25 packet CPU clock capped at 394 MHz", 394_000L, packet25.cpuFrequenciesKhz.firstOrNull())

        // Check Cases 21..23 (Cellular RF Degradation) packet markers
        for (id in 21..23) {
            val cellCase = HologramInvariantCases.getById(id)!!
            val cellPacket = cellCase.toRawPacket()
            assertNotNull("Cellular packet must have RSRP", cellPacket.rsrpDbm)
            assertTrue("Cellular degradation packet RSRP <= -115 dBm", cellPacket.rsrpDbm!! <= -115)
        }
    }
}
