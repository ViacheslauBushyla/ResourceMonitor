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

class ExpandedHologramInvariantMatrixTest {

    private val speedToleranceRps = 0.01f

    @Test
    fun test1_all30CasesVerify5RotationalSpeedsWithinTolerance() {
        val cases = HologramInvariantCases.EXPANDED_CASES
        assertEquals("Expanded 5-channel invariant matrix must contain exactly 30 cases", 30, cases.size)

        for (case in cases) {
            val params = HologramProjectionCalculator.computeParameters(case.snapshot)

            assertEquals(
                "Case #${case.id} [${case.name}] R1 speed mismatch",
                case.expectedR1SpeedRps,
                params.ring1SpeedRps,
                speedToleranceRps
            )
            assertEquals(
                "Case #${case.id} [${case.name}] R2 speed mismatch",
                case.expectedR2SpeedRps,
                params.ring2SpeedRps,
                speedToleranceRps
            )
            assertEquals(
                "Case #${case.id} [${case.name}] R3 speed mismatch",
                case.expectedR3SpeedRps,
                params.ring3SpeedRps,
                speedToleranceRps
            )
            assertEquals(
                "Case #${case.id} [${case.name}] R4 speed mismatch",
                case.expectedR4SpeedRps,
                params.ring4SpeedRps,
                speedToleranceRps
            )
            assertEquals(
                "Case #${case.id} [${case.name}] R5 speed mismatch",
                case.expectedR5SpeedRps,
                params.ring5SpeedRps,
                speedToleranceRps
            )

            assertTrue("Case #${case.id} R1 speed out of bounds: ${params.ring1SpeedRps}", params.ring1SpeedRps in 0.200f..5.000f)
            assertTrue("Case #${case.id} R2 speed out of bounds: ${params.ring2SpeedRps}", params.ring2SpeedRps in 0.200f..5.000f)
            assertTrue("Case #${case.id} R3 speed out of bounds: ${params.ring3SpeedRps}", params.ring3SpeedRps in 0.200f..5.000f)
            assertTrue("Case #${case.id} R4 speed out of bounds: ${params.ring4SpeedRps}", params.ring4SpeedRps in 0.200f..5.000f)
            assertTrue("Case #${case.id} R5 speed out of bounds: ${params.ring5SpeedRps}", params.ring5SpeedRps in 0.200f..5.000f)

            // Verify backward compatibility properties match rings 1..3
            assertEquals(params.ring1SpeedRps, params.outerRingSpeedRps, 0.0001f)
            assertEquals(params.ring2SpeedRps, params.middleRingSpeedRps, 0.0001f)
            assertEquals(params.ring3SpeedRps, params.innerRingSpeedRps, 0.0001f)
        }
    }

    @Test
    fun test2_all30CasesVerifyColorsFollowNominalAndAlertGamuts() {
        val cases = HologramInvariantCases.EXPANDED_CASES
        for (case in cases) {
            val params = HologramProjectionCalculator.computeParameters(case.snapshot)

            assertEquals("Case #${case.id} [${case.name}] R1 color mismatch", case.expectedR1Color, params.ring1Color)
            assertEquals("Case #${case.id} [${case.name}] R2 color mismatch", case.expectedR2Color, params.ring2Color)
            assertEquals("Case #${case.id} [${case.name}] R3 color mismatch", case.expectedR3Color, params.ring3Color)
            assertEquals("Case #${case.id} [${case.name}] R4 color mismatch", case.expectedR4Color, params.ring4Color)
            assertEquals("Case #${case.id} [${case.name}] R5 color mismatch", case.expectedR5Color, params.ring5Color)

            assertEquals(params.ring1Color, params.outerColor)
            assertEquals(params.ring2Color, params.middleColor)
            assertEquals(params.ring3Color, params.innerColor)
        }
    }

    @Test
    fun test3_meltdownAlertAndStatusLabelsMatchStrictInvariants() {
        val cases = HologramInvariantCases.EXPANDED_CASES
        val meltdownIds = setOf(7, 20, 23, 24, 25, 30)

        for (case in cases) {
            val params = HologramProjectionCalculator.computeParameters(case.snapshot)

            assertEquals(
                "Case #${case.id} [${case.name}] meltdown alert flag mismatch",
                case.expectedIsMeltdown,
                params.isMeltdownAlert
            )
            assertEquals(
                "Case #${case.id} [${case.name}] meltdown membership mismatch",
                meltdownIds.contains(case.id),
                params.isMeltdownAlert
            )
            assertTrue(
                "Case #${case.id} [${case.name}] status '${params.systemStatusLabel}' does not contain '${case.expectedStatusSubstring}'",
                params.systemStatusLabel.contains(case.expectedStatusSubstring)
            )
            assertEquals(
                "Case #${case.id} [${case.name}] energy label mismatch",
                case.expectedEnergyLabel,
                params.energyOutputLabel
            )
        }
    }

    @Test
    fun test4_singleMetricIsolationAcrossAll5Channels() {
        val baseSpeed = 0.200f
        val cases = HologramInvariantCases.EXPANDED_CASES

        // Channel 1 (CPU): Case 6 (80%) and Case 7 (100%)
        val case6 = cases.first { it.id == 6 }
        val p6 = HologramProjectionCalculator.computeParameters(case6.snapshot)
        assertEquals(3.272f, p6.ring1SpeedRps, speedToleranceRps)
        assertTrue("R2..R5 must remain near idle in CPU isolation", p6.ring2SpeedRps < 0.35f && p6.ring3SpeedRps < 0.35f && p6.ring4SpeedRps == baseSpeed && p6.ring5SpeedRps < 0.35f)

        val case7 = cases.first { it.id == 7 }
        val p7 = HologramProjectionCalculator.computeParameters(case7.snapshot)
        assertEquals(5.000f, p7.ring1SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p7.ring2SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p7.ring3SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p7.ring4SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p7.ring5SpeedRps, speedToleranceRps)

        // Channel 2 (RAM): Case 8 (80%) and Case 9 (100%)
        val case8 = cases.first { it.id == 8 }
        val p8 = HologramProjectionCalculator.computeParameters(case8.snapshot)
        assertEquals(3.272f, p8.ring2SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p8.ring1SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p8.ring3SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p8.ring4SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p8.ring5SpeedRps, speedToleranceRps)

        val case9 = cases.first { it.id == 9 }
        val p9 = HologramProjectionCalculator.computeParameters(case9.snapshot)
        assertEquals(5.000f, p9.ring2SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p9.ring1SpeedRps, speedToleranceRps)

        // Channel 3 (Network): Case 10 (80%) and Case 11 (100%)
        val case10 = cases.first { it.id == 10 }
        val p10 = HologramProjectionCalculator.computeParameters(case10.snapshot)
        assertEquals(3.272f, p10.ring3SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p10.ring1SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p10.ring2SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p10.ring4SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p10.ring5SpeedRps, speedToleranceRps)

        val case11 = cases.first { it.id == 11 }
        val p11 = HologramProjectionCalculator.computeParameters(case11.snapshot)
        assertEquals(5.000f, p11.ring3SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p11.ring1SpeedRps, speedToleranceRps)

        // Channel 4 (Storage SSD): Case 12 (85%)
        val case12 = cases.first { it.id == 12 }
        val p12 = HologramProjectionCalculator.computeParameters(case12.snapshot)
        assertEquals(3.668f, p12.ring4SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p12.ring1SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p12.ring2SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p12.ring3SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p12.ring5SpeedRps, speedToleranceRps)

        // Channel 5 (GPU/Thermal): Case 13 (85%)
        val case13 = cases.first { it.id == 13 }
        val p13 = HologramProjectionCalculator.computeParameters(case13.snapshot)
        assertEquals(3.668f, p13.ring5SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p13.ring1SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p13.ring2SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p13.ring3SpeedRps, speedToleranceRps)
        assertEquals(baseSpeed, p13.ring4SpeedRps, speedToleranceRps)
    }

    @Test
    fun test5_storageStallCellularAlertAndMemoryThrashIntegrity() {
        val cases = HologramInvariantCases.EXPANDED_CASES

        // Case 23: Storage Stall Flare & Meltdown
        val case23 = cases.first { it.id == 23 }
        val p23 = HologramProjectionCalculator.computeParameters(case23.snapshot)
        assertTrue("Case 23 must set isStorageStallAlert", p23.isStorageStallAlert)
        assertTrue("Case 23 must set isMeltdownAlert", p23.isMeltdownAlert)
        assertEquals("Case 23 Ring 4 must flare White", NeonPalette.StorageStallWhite, p23.ring4Color)
        assertEquals("Case 23 Ring 1 must be Meltdown Crimson", NeonPalette.MeltdownRed, p23.ring1Color)
        assertEquals("Case 23 Ring 2 must be Meltdown Crimson", NeonPalette.MeltdownRed, p23.ring2Color)
        assertEquals("Case 23 Ring 3 must be Meltdown Crimson", NeonPalette.MeltdownRed, p23.ring3Color)
        assertEquals("Case 23 Ring 5 must be Meltdown Crimson", NeonPalette.MeltdownRed, p23.ring5Color)
        assertTrue("Case 23 status must contain IO_WAIT_STALL", p23.systemStatusLabel.contains("IO_WAIT_STALL"))

        // Case 21: Cellular Signal Degraded
        val case21 = cases.first { it.id == 21 }
        val p21 = HologramProjectionCalculator.computeParameters(case21.snapshot)
        assertTrue("Case 21 must set isCellularDegradedAlert", p21.isCellularDegradedAlert)
        assertFalse("Case 21 must NOT be meltdown", p21.isMeltdownAlert)
        assertEquals("Case 21 Ring 3 must be Warning Amber", NeonPalette.WarningAmber, p21.ring3Color)
        assertEquals("Case 21 Ring 1 must remain Cyan", NeonPalette.CyanCpu, p21.ring1Color)
        assertEquals("Case 21 Ring 2 must remain Orange", NeonPalette.OrangeRam, p21.ring2Color)
        assertEquals("Case 21 Ring 4 must remain IceBlue", NeonPalette.IceBlueStorage, p21.ring4Color)
        assertEquals("Case 21 Ring 5 must remain Emerald", NeonPalette.EmeraldGpu, p21.ring5Color)

        // Case 22: Cellular Dead Zone
        val case22 = cases.first { it.id == 22 }
        val p22 = HologramProjectionCalculator.computeParameters(case22.snapshot)
        assertTrue("Case 22 must set isCellularDegradedAlert", p22.isCellularDegradedAlert)
        assertFalse("Case 22 must NOT be meltdown", p22.isMeltdownAlert)
        assertEquals("Case 22 Ring 3 must be MeltdownRed alert", NeonPalette.MeltdownRed, p22.ring3Color)

        // Case 27: Memory Thrashing Purple
        val case27 = cases.first { it.id == 27 }
        val p27 = HologramProjectionCalculator.computeParameters(case27.snapshot)
        assertTrue("Case 27 must set isMemoryThrashAlert", p27.isMemoryThrashAlert)
        assertFalse("Case 27 must NOT set isMeltdownAlert", p27.isMeltdownAlert)
        assertEquals("Case 27 Ring 2 must be MemoryThrashPurple", NeonPalette.MemoryThrashPurple, p27.ring2Color)
        assertEquals("Case 27 Ring 1 must remain Cyan", NeonPalette.CyanCpu, p27.ring1Color)
        assertEquals("Case 27 Ring 3 must remain Magenta", NeonPalette.MagentaGpuNet, p27.ring3Color)
        assertEquals("Case 27 Ring 4 must remain IceBlue", NeonPalette.IceBlueStorage, p27.ring4Color)
        assertEquals("Case 27 Ring 5 must remain Emerald", NeonPalette.EmeraldGpu, p27.ring5Color)
    }

    @Test
    fun test6_thermalCrisisAndClockCappingInvariants() {
        val cases = HologramInvariantCases.EXPANDED_CASES

        // Case 24: CPU Clock-Capped 95%
        val case24 = cases.first { it.id == 24 }
        val p24 = HologramProjectionCalculator.computeParameters(case24.snapshot)
        assertTrue(p24.isMeltdownAlert)
        assertEquals(4.532f, p24.ring1SpeedRps, speedToleranceRps)
        assertEquals(NeonPalette.MeltdownRed, p24.ring1Color)

        // Case 25: Severe OS Thermal Throttling
        val case25 = cases.first { it.id == 25 }
        val p25 = HologramProjectionCalculator.computeParameters(case25.snapshot)
        assertTrue(p25.isMeltdownAlert)
        assertEquals(1.928f, p25.ring1SpeedRps, speedToleranceRps)
        assertEquals("Case 25 R5 thermal corona must be pinned to exactly 3.000 RPS", 3.000f, p25.ring5SpeedRps, 0.001f)
        assertEquals(NeonPalette.MeltdownRed, p25.ring5Color)

        // Case 26: High-Performance Boost 88% at 2.9 GHz
        val case26 = cases.first { it.id == 26 }
        val p26 = HologramProjectionCalculator.computeParameters(case26.snapshot)
        assertFalse("Unthrottled boost must NOT trigger meltdown", p26.isMeltdownAlert)
        assertEquals(3.917f, p26.ring1SpeedRps, speedToleranceRps)
        assertEquals(NeonPalette.CyanCpu, p26.ring1Color)
        assertEquals("ENERGY OUTPUT: HIGH", p26.energyOutputLabel)
    }

    @Test
    fun test7_mathematicalRobustnessNegativeAndOverflowClamps() {
        val cases = HologramInvariantCases.EXPANDED_CASES
        val config = HologramBehaviorConfig()

        // Case 29: Negative Loads (-20%)
        val case29 = cases.first { it.id == 29 }
        val p29 = HologramProjectionCalculator.computeParameters(case29.snapshot, config)
        assertEquals(0.200f, p29.ring1SpeedRps, 0.001f)
        assertEquals(0.200f, p29.ring2SpeedRps, 0.001f)
        assertEquals(0.200f, p29.ring3SpeedRps, 0.001f)
        assertEquals(0.200f, p29.ring4SpeedRps, 0.001f)
        assertEquals(0.200f, p29.ring5SpeedRps, 0.001f)
        assertFalse(p29.ring1SpeedRps.isNaN())
        assertFalse(p29.ring4SpeedRps.isInfinite())
        assertFalse(p29.isMeltdownAlert)

        // Case 30: Overflow Loads (250%)
        val case30 = cases.first { it.id == 30 }
        val p30 = HologramProjectionCalculator.computeParameters(case30.snapshot, config)
        assertEquals(5.000f, p30.ring1SpeedRps, 0.001f)
        assertEquals(5.000f, p30.ring2SpeedRps, 0.001f)
        assertEquals(5.000f, p30.ring3SpeedRps, 0.001f)
        assertEquals(5.000f, p30.ring4SpeedRps, 0.001f)
        assertEquals(5.000f, p30.ring5SpeedRps, 0.001f)
        assertTrue(p30.isMeltdownAlert)

        // Extreme Non-Finite Checks
        assertEquals(0.200f, HologramProjectionCalculator.calculateSpeed(Float.NaN, 1.0f, config), 0.001f)
        assertEquals(0.200f, HologramProjectionCalculator.calculateSpeed(Float.NEGATIVE_INFINITY, 1.0f, config), 0.001f)
        assertEquals(5.000f, HologramProjectionCalculator.calculateSpeed(Float.POSITIVE_INFINITY, 1.0f, config), 0.001f)
        assertEquals(0.200f, HologramProjectionCalculator.calculateSpeed(-9999f, 1.0f, config), 0.001f)
        assertEquals(5.000f, HologramProjectionCalculator.calculateSpeed(9999f, 1.0f, config), 0.001f)
    }

    @Test
    fun test8_dynamicParameterOverridesDeterminism() {
        // 1. Speed Curve Variations (LINEAR, SIGMOID)
        val linearConfig = HologramBehaviorConfig(speedCurve = SpeedCurve.LINEAR)
        assertEquals(0.200f, HologramProjectionCalculator.calculateSpeed(0.0f, 1.0f, linearConfig), 0.001f)
        assertEquals(2.600f, HologramProjectionCalculator.calculateSpeed(0.5f, 1.0f, linearConfig), 0.001f)
        assertEquals(5.000f, HologramProjectionCalculator.calculateSpeed(1.0f, 1.0f, linearConfig), 0.001f)

        val sigmoidConfig = HologramBehaviorConfig(speedCurve = SpeedCurve.SIGMOID)
        val sig0Expected = 0.2f + (1f / (1f + exp(5f))) * 4.8f
        val sig50Expected = 0.2f + 0.5f * 4.8f
        val sig100Expected = 0.2f + (1f / (1f + exp(-5f))) * 4.8f
        assertEquals(sig0Expected, HologramProjectionCalculator.calculateSpeed(0.0f, 1.0f, sigmoidConfig), 0.01f)
        assertEquals(sig50Expected, HologramProjectionCalculator.calculateSpeed(0.5f, 1.0f, sigmoidConfig), 0.01f)
        assertEquals(sig100Expected, HologramProjectionCalculator.calculateSpeed(1.0f, 1.0f, sigmoidConfig), 0.01f)

        // 2. Multi-channel Sensitivity Multipliers (all 5 channels)
        val sensConfig = HologramBehaviorConfig(
            outerSensitivity = 1.5f,
            middleSensitivity = 0.5f,
            innerSensitivity = 2.0f,
            storageSensitivity = 1.2f,
            gpuSensitivity = 0.8f,
            speedCurve = SpeedCurve.QUADRATIC
        )
        val multiSnap = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.50f),
            ram = MetricValue(smoothedValue = 0.80f),
            network = MetricValue(smoothedValue = 0.60f),
            storageIo = MetricValue(smoothedValue = 0.40f),
            gpu = MetricValue(smoothedValue = 0.50f)
        )
        val pSens = HologramProjectionCalculator.computeParameters(multiSnap, sensConfig)
        // R1: 0.50 * 1.5 = 0.75 -> 0.2 + 0.75^2 * 4.8 = 2.900f
        assertEquals(2.900f, pSens.ring1SpeedRps, speedToleranceRps)
        // R2: 0.80 * 0.5 = 0.40 -> 0.2 + 0.40^2 * 4.8 = 0.968f
        assertEquals(0.968f, pSens.ring2SpeedRps, speedToleranceRps)
        // R3: 0.60 * 2.0 = 1.20 -> clamp to 1.0 -> 5.000f
        assertEquals(5.000f, pSens.ring3SpeedRps, speedToleranceRps)
        // R4: 0.40 * 1.2 = 0.48 -> 0.2 + 0.48^2 * 4.8 = 1.30592f
        assertEquals(1.306f, pSens.ring4SpeedRps, speedToleranceRps)
        // R5: 0.50 * 0.8 = 0.40 -> 0.2 + 0.40^2 * 4.8 = 0.968f
        assertEquals(0.968f, pSens.ring5SpeedRps, speedToleranceRps)

        // 3. Custom vMin / vMax overrides
        val customRangeConfig = HologramBehaviorConfig(
            vMinRps = 0.5f,
            vMaxRps = 10.0f
        )
        val pRange = HologramProjectionCalculator.computeParameters(
            SystemTelemetrySnapshot(
                cpu = MetricValue(smoothedValue = 0.0f),
                ram = MetricValue(smoothedValue = 1.0f)
            ),
            customRangeConfig
        )
        assertEquals(0.500f, pRange.ring1SpeedRps, 0.001f)
        assertEquals(10.000f, pRange.ring2SpeedRps, 0.001f)

        // 4. Custom Meltdown Threshold override
        val customMeltdownConfig = HologramBehaviorConfig(meltdownThreshold = 0.75f)
        val snapUnderThreshold = SystemTelemetrySnapshot(cpu = MetricValue(smoothedValue = 0.74f))
        val pUnder = HologramProjectionCalculator.computeParameters(snapUnderThreshold, customMeltdownConfig)
        assertFalse(pUnder.isMeltdownAlert)

        val snapOverThreshold = SystemTelemetrySnapshot(cpu = MetricValue(smoothedValue = 0.76f))
        val pOver = HologramProjectionCalculator.computeParameters(snapOverThreshold, customMeltdownConfig)
        assertTrue(pOver.isMeltdownAlert)
    }

    @Test
    fun test9_toRawPacketGeneratesValidTelemetryForSimulation() {
        val cases = HologramInvariantCases.EXPANDED_CASES
        for (case in cases) {
            val packet = case.toRawPacket()
            assertNotNull(packet)
            assertEquals(case.snapshot.cpu.smoothedValue * 100f, packet.cpuLoadPercentage, 0.01f)
            assertEquals(case.snapshot.gpu.smoothedValue * 100f, packet.gpuLoadPercentage, 0.01f)
            assertTrue(packet.ramTotalBytes > 0L)
            assertTrue(packet.ramAvailableBytes >= 0L)
        }

        // Verify Case 23: storage stall flag
        val case23 = cases.first { it.id == 23 }
        assertTrue("Case 23 packet must have isStorageStall = true", case23.toRawPacket().isStorageStall)

        // Verify Case 27: zRAM thrash packet markers
        val case27 = cases.first { it.id == 27 }
        val packet27 = case27.toRawPacket()
        assertTrue("Case 27 packet must exhibit high zRAM swap", packet27.zRamUsedBytes > 2_000_000_000L)
        assertTrue("Case 27 packet must exhibit compact stalls", packet27.compactStallsCount > 100L)

        // Verify Case 25: forced thermal throttling markers
        val case25 = cases.first { it.id == 25 }
        val packet25 = case25.toRawPacket()
        assertEquals(5, packet25.thermalStatusLevel)
        assertEquals(65_000, packet25.gpuTemperatureMilliC)
    }
}
