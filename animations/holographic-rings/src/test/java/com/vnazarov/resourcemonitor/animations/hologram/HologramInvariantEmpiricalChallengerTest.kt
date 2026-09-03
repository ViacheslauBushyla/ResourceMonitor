package com.vnazarov.resourcemonitor.animations.hologram

import androidx.compose.ui.graphics.Color
import com.vnazarov.resourcemonitor.core.designsystem.theme.NeonPalette
import com.vnazarov.resourcemonitor.core.model.ThrottleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp

/**
 * Empirical Challenger Test Suite for Milestone M2:
 * 1. Independently calculates theoretical rotational speeds across all 30 invariant cases.
 * 2. Compares calculator output, case expected constants, and theoretical equations within +/- 0.01 RPS.
 * 3. Exhaustively stresses parameter mutations (custom V_min/V_max, LINEAR/SIGMOID curves, sensitivities).
 * 4. Stresses boundary mutations (zero delta, inverted range, extreme sensitivities, NaN/Inf robustness).
 */
class HologramInvariantEmpiricalChallengerTest {

    private val rpsTolerance = 0.01f
    private val fineRpsTolerance = 0.001f

    // ---------------------------------------------------------------------------------------------
    // Independent Theoretical Speed Oracles
    // ---------------------------------------------------------------------------------------------

    private fun theoreticalQuadraticSpeed(
        rawLoad: Float,
        sensitivity: Float = 1.0f,
        vMin: Float = 0.2f,
        vMax: Float = 5.0f
    ): Float {
        val safeLoad = if (rawLoad.isNaN()) 0f else rawLoad
        val clampedLoad = (safeLoad * sensitivity).coerceIn(0f, 1f)
        val vRange = vMax - vMin
        return vMin + (clampedLoad * clampedLoad) * vRange
    }

    private fun theoreticalLinearSpeed(
        rawLoad: Float,
        sensitivity: Float = 1.0f,
        vMin: Float = 0.2f,
        vMax: Float = 5.0f
    ): Float {
        val safeLoad = if (rawLoad.isNaN()) 0f else rawLoad
        val clampedLoad = (safeLoad * sensitivity).coerceIn(0f, 1f)
        val vRange = vMax - vMin
        return vMin + clampedLoad * vRange
    }

    private fun theoreticalSigmoidSpeed(
        rawLoad: Float,
        sensitivity: Float = 1.0f,
        vMin: Float = 0.2f,
        vMax: Float = 5.0f
    ): Float {
        val safeLoad = if (rawLoad.isNaN()) 0f else rawLoad
        val clampedLoad = (safeLoad * sensitivity).coerceIn(0f, 1f)
        val vRange = vMax - vMin
        val sigmoid = 1f / (1f + exp(-10f * (clampedLoad - 0.5f)))
        return vMin + sigmoid * vRange
    }

    // ---------------------------------------------------------------------------------------------
    // Challenge 1: Independent Mathematical Speed Oracle Verification Across All 30 Cases
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `challenge 1 - independent theoretical calculation matches calculator and invariant matrix across all 30 cases`() {
        assertEquals("Invariant case count must be exactly 30", 30, HologramInvariantCases.ALL_CASES.size)

        for (case in HologramInvariantCases.ALL_CASES) {
            val cpuLoad = case.snapshot.cpu.smoothedValue
            val ramLoad = case.snapshot.ram.smoothedValue
            val netLoad = case.snapshot.network.smoothedValue

            // 1. Compute independent theoretical speeds
            val theoOuter = theoreticalQuadraticSpeed(cpuLoad)
            val theoMiddle = theoreticalQuadraticSpeed(ramLoad)
            val theoInner = theoreticalQuadraticSpeed(netLoad)

            // 2. Compute calculator output
            val actualParams = HologramProjectionCalculator.computeParameters(case.snapshot)

            // 3. Assert Case Expected constants match theoretical speeds within 0.01 RPS
            assertEquals(
                "Case #${case.id} [${case.name}] expectedOuterSpeedRps deviates from theory",
                theoOuter,
                case.expectedOuterSpeedRps,
                rpsTolerance
            )
            assertEquals(
                "Case #${case.id} [${case.name}] expectedMiddleSpeedRps deviates from theory",
                theoMiddle,
                case.expectedMiddleSpeedRps,
                rpsTolerance
            )
            assertEquals(
                "Case #${case.id} [${case.name}] expectedInnerSpeedRps deviates from theory",
                theoInner,
                case.expectedInnerSpeedRps,
                rpsTolerance
            )

            // 4. Assert Calculator actual outputs match theoretical speeds within 0.01 RPS
            assertEquals(
                "Case #${case.id} [${case.name}] actual outerRingSpeedRps deviates from theory",
                theoOuter,
                actualParams.outerRingSpeedRps,
                rpsTolerance
            )
            assertEquals(
                "Case #${case.id} [${case.name}] actual middleRingSpeedRps deviates from theory",
                theoMiddle,
                actualParams.middleRingSpeedRps,
                rpsTolerance
            )
            assertEquals(
                "Case #${case.id} [${case.name}] actual innerRingSpeedRps deviates from theory",
                theoInner,
                actualParams.innerRingSpeedRps,
                rpsTolerance
            )

            // 5. Assert actual speed equals case expected speed within 0.01 RPS
            assertEquals(
                "Case #${case.id} [${case.name}] actual vs expected outer speed mismatch",
                case.expectedOuterSpeedRps,
                actualParams.outerRingSpeedRps,
                rpsTolerance
            )
            assertEquals(
                "Case #${case.id} [${case.name}] actual vs expected middle speed mismatch",
                case.expectedMiddleSpeedRps,
                actualParams.middleRingSpeedRps,
                rpsTolerance
            )
            assertEquals(
                "Case #${case.id} [${case.name}] actual vs expected inner speed mismatch",
                case.expectedInnerSpeedRps,
                actualParams.innerRingSpeedRps,
                rpsTolerance
            )

            // 6. Boundary bounds [0.200, 5.000]
            assertTrue(
                "Case #${case.id} outer speed ${actualParams.outerRingSpeedRps} out of bounds",
                actualParams.outerRingSpeedRps in 0.200f..5.000f
            )
            assertTrue(
                "Case #${case.id} middle speed ${actualParams.middleRingSpeedRps} out of bounds",
                actualParams.middleRingSpeedRps in 0.200f..5.000f
            )
            assertTrue(
                "Case #${case.id} inner speed ${actualParams.innerRingSpeedRps} out of bounds",
                actualParams.innerRingSpeedRps in 0.200f..5.000f
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Challenge 2: Parameter Mutation - Full Linear Curve Across All 30 Cases
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `challenge 2 - parameter mutation linear curve across all 30 cases matches theoretical linear model`() {
        val linearConfig = HologramBehaviorConfig(speedCurve = SpeedCurve.LINEAR)

        for (case in HologramInvariantCases.ALL_CASES) {
            val cpuLoad = case.snapshot.cpu.smoothedValue
            val ramLoad = case.snapshot.ram.smoothedValue
            val netLoad = case.snapshot.network.smoothedValue

            val theoOuter = theoreticalLinearSpeed(cpuLoad)
            val theoMiddle = theoreticalLinearSpeed(ramLoad)
            val theoInner = theoreticalLinearSpeed(netLoad)

            val actualParams = HologramProjectionCalculator.computeParameters(case.snapshot, linearConfig)

            assertEquals(
                "Linear Case #${case.id} [${case.name}] outer speed mismatch",
                theoOuter,
                actualParams.outerRingSpeedRps,
                fineRpsTolerance
            )
            assertEquals(
                "Linear Case #${case.id} [${case.name}] middle speed mismatch",
                theoMiddle,
                actualParams.middleRingSpeedRps,
                fineRpsTolerance
            )
            assertEquals(
                "Linear Case #${case.id} [${case.name}] inner speed mismatch",
                theoInner,
                actualParams.innerRingSpeedRps,
                fineRpsTolerance
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Challenge 3: Parameter Mutation - Full Sigmoid Curve Across All 30 Cases
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `challenge 3 - parameter mutation sigmoid curve across all 30 cases matches theoretical sigmoid model`() {
        val sigmoidConfig = HologramBehaviorConfig(speedCurve = SpeedCurve.SIGMOID)

        for (case in HologramInvariantCases.ALL_CASES) {
            val cpuLoad = case.snapshot.cpu.smoothedValue
            val ramLoad = case.snapshot.ram.smoothedValue
            val netLoad = case.snapshot.network.smoothedValue

            val theoOuter = theoreticalSigmoidSpeed(cpuLoad)
            val theoMiddle = theoreticalSigmoidSpeed(ramLoad)
            val theoInner = theoreticalSigmoidSpeed(netLoad)

            val actualParams = HologramProjectionCalculator.computeParameters(case.snapshot, sigmoidConfig)

            assertEquals(
                "Sigmoid Case #${case.id} [${case.name}] outer speed mismatch",
                theoOuter,
                actualParams.outerRingSpeedRps,
                fineRpsTolerance
            )
            assertEquals(
                "Sigmoid Case #${case.id} [${case.name}] middle speed mismatch",
                theoMiddle,
                actualParams.middleRingSpeedRps,
                fineRpsTolerance
            )
            assertEquals(
                "Sigmoid Case #${case.id} [${case.name}] inner speed mismatch",
                theoInner,
                actualParams.innerRingSpeedRps,
                fineRpsTolerance
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Challenge 4: Parameter Mutation - Custom V_min and V_max Scalings
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `challenge 4 - custom base speeds mutation scaled correctly across all 30 cases`() {
        val customConfigs = listOf(
            HologramBehaviorConfig(vMinRps = 0.5f, vMaxRps = 8.0f, speedCurve = SpeedCurve.QUADRATIC),
            HologramBehaviorConfig(vMinRps = 0.0f, vMaxRps = 1.0f, speedCurve = SpeedCurve.QUADRATIC),
            HologramBehaviorConfig(vMinRps = 1.5f, vMaxRps = 12.0f, speedCurve = SpeedCurve.LINEAR),
            HologramBehaviorConfig(vMinRps = 0.1f, vMaxRps = 3.0f, speedCurve = SpeedCurve.SIGMOID)
        )

        for (config in customConfigs) {
            for (case in HologramInvariantCases.ALL_CASES) {
                val cpuLoad = case.snapshot.cpu.smoothedValue
                val ramLoad = case.snapshot.ram.smoothedValue
                val netLoad = case.snapshot.network.smoothedValue

                val theoOuter = when (config.speedCurve) {
                    SpeedCurve.QUADRATIC -> theoreticalQuadraticSpeed(cpuLoad, vMin = config.vMinRps, vMax = config.vMaxRps)
                    SpeedCurve.LINEAR -> theoreticalLinearSpeed(cpuLoad, vMin = config.vMinRps, vMax = config.vMaxRps)
                    SpeedCurve.SIGMOID -> theoreticalSigmoidSpeed(cpuLoad, vMin = config.vMinRps, vMax = config.vMaxRps)
                }
                val theoMiddle = when (config.speedCurve) {
                    SpeedCurve.QUADRATIC -> theoreticalQuadraticSpeed(ramLoad, vMin = config.vMinRps, vMax = config.vMaxRps)
                    SpeedCurve.LINEAR -> theoreticalLinearSpeed(ramLoad, vMin = config.vMinRps, vMax = config.vMaxRps)
                    SpeedCurve.SIGMOID -> theoreticalSigmoidSpeed(ramLoad, vMin = config.vMinRps, vMax = config.vMaxRps)
                }
                val theoInner = when (config.speedCurve) {
                    SpeedCurve.QUADRATIC -> theoreticalQuadraticSpeed(netLoad, vMin = config.vMinRps, vMax = config.vMaxRps)
                    SpeedCurve.LINEAR -> theoreticalLinearSpeed(netLoad, vMin = config.vMinRps, vMax = config.vMaxRps)
                    SpeedCurve.SIGMOID -> theoreticalSigmoidSpeed(netLoad, vMin = config.vMinRps, vMax = config.vMaxRps)
                }

                val actualParams = HologramProjectionCalculator.computeParameters(case.snapshot, config)

                assertEquals(
                    "Config [vMin=${config.vMinRps}, vMax=${config.vMaxRps}, curve=${config.speedCurve}] Case #${case.id} outer mismatch",
                    theoOuter,
                    actualParams.outerRingSpeedRps,
                    fineRpsTolerance
                )
                assertEquals(
                    "Config [vMin=${config.vMinRps}, vMax=${config.vMaxRps}, curve=${config.speedCurve}] Case #${case.id} middle mismatch",
                    theoMiddle,
                    actualParams.middleRingSpeedRps,
                    fineRpsTolerance
                )
                assertEquals(
                    "Config [vMin=${config.vMinRps}, vMax=${config.vMaxRps}, curve=${config.speedCurve}] Case #${case.id} inner mismatch",
                    theoInner,
                    actualParams.innerRingSpeedRps,
                    fineRpsTolerance
                )
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Challenge 5: Parameter Mutation - Asymmetric Sensitivity Multipliers
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `challenge 5 - asymmetric sensitivity multipliers across all 30 cases match theoretical clamped scaling`() {
        val sensitivityConfigs = listOf(
            HologramBehaviorConfig(outerSensitivity = 0.5f, middleSensitivity = 1.2f, innerSensitivity = 2.0f, speedCurve = SpeedCurve.QUADRATIC),
            HologramBehaviorConfig(outerSensitivity = 1.8f, middleSensitivity = 0.2f, innerSensitivity = 1.0f, speedCurve = SpeedCurve.LINEAR),
            HologramBehaviorConfig(outerSensitivity = 0.1f, middleSensitivity = 2.0f, innerSensitivity = 0.7f, speedCurve = SpeedCurve.SIGMOID)
        )

        for (config in sensitivityConfigs) {
            for (case in HologramInvariantCases.ALL_CASES) {
                val cpuLoad = case.snapshot.cpu.smoothedValue
                val ramLoad = case.snapshot.ram.smoothedValue
                val netLoad = case.snapshot.network.smoothedValue

                val theoOuter = when (config.speedCurve) {
                    SpeedCurve.QUADRATIC -> theoreticalQuadraticSpeed(cpuLoad, sensitivity = config.outerSensitivity, vMin = config.vMinRps, vMax = config.vMaxRps)
                    SpeedCurve.LINEAR -> theoreticalLinearSpeed(cpuLoad, sensitivity = config.outerSensitivity, vMin = config.vMinRps, vMax = config.vMaxRps)
                    SpeedCurve.SIGMOID -> theoreticalSigmoidSpeed(cpuLoad, sensitivity = config.outerSensitivity, vMin = config.vMinRps, vMax = config.vMaxRps)
                }
                val theoMiddle = when (config.speedCurve) {
                    SpeedCurve.QUADRATIC -> theoreticalQuadraticSpeed(ramLoad, sensitivity = config.middleSensitivity, vMin = config.vMinRps, vMax = config.vMaxRps)
                    SpeedCurve.LINEAR -> theoreticalLinearSpeed(ramLoad, sensitivity = config.middleSensitivity, vMin = config.vMinRps, vMax = config.vMaxRps)
                    SpeedCurve.SIGMOID -> theoreticalSigmoidSpeed(ramLoad, sensitivity = config.middleSensitivity, vMin = config.vMinRps, vMax = config.vMaxRps)
                }
                val theoInner = when (config.speedCurve) {
                    SpeedCurve.QUADRATIC -> theoreticalQuadraticSpeed(netLoad, sensitivity = config.innerSensitivity, vMin = config.vMinRps, vMax = config.vMaxRps)
                    SpeedCurve.LINEAR -> theoreticalLinearSpeed(netLoad, sensitivity = config.innerSensitivity, vMin = config.vMinRps, vMax = config.vMaxRps)
                    SpeedCurve.SIGMOID -> theoreticalSigmoidSpeed(netLoad, sensitivity = config.innerSensitivity, vMin = config.vMinRps, vMax = config.vMaxRps)
                }

                val actualParams = HologramProjectionCalculator.computeParameters(case.snapshot, config)

                assertEquals(
                    "Sensitivity Case #${case.id} outer mismatch",
                    theoOuter,
                    actualParams.outerRingSpeedRps,
                    fineRpsTolerance
                )
                assertEquals(
                    "Sensitivity Case #${case.id} middle mismatch",
                    theoMiddle,
                    actualParams.middleRingSpeedRps,
                    fineRpsTolerance
                )
                assertEquals(
                    "Sensitivity Case #${case.id} inner mismatch",
                    theoInner,
                    actualParams.innerRingSpeedRps,
                    fineRpsTolerance
                )
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Challenge 6: Boundary & Degenerate Parameter Mutations
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `challenge 6 - degenerate parameter bounds vMin equals vMax produces invariant constant speed`() {
        // When vMin == vMax == 3.5f, vRange is 0.0f
        val constConfig = HologramBehaviorConfig(vMinRps = 3.5f, vMaxRps = 3.5f)

        for (case in HologramInvariantCases.ALL_CASES) {
            val params = HologramProjectionCalculator.computeParameters(case.snapshot, constConfig)
            assertEquals("Outer ring must be constant 3.5 RPS", 3.5f, params.outerRingSpeedRps, 0.0001f)
            assertEquals("Middle ring must be constant 3.5 RPS", 3.5f, params.middleRingSpeedRps, 0.0001f)
            assertEquals("Inner ring must be constant 3.5 RPS", 3.5f, params.innerRingSpeedRps, 0.0001f)
        }
    }

    @Test
    fun `challenge 7 - inverted velocity range vMin greater than vMax scales monotonically in reverse`() {
        // Inverted: vMin = 5.0f, vMax = 1.0f -> vRange = -4.0f
        val invertedConfig = HologramBehaviorConfig(vMinRps = 5.0f, vMaxRps = 1.0f, speedCurve = SpeedCurve.LINEAR)

        // Load 0% -> 5.0f
        val speed0 = HologramProjectionCalculator.calculateSpeed(0.0f, 1.0f, invertedConfig)
        assertEquals(5.0f, speed0, 0.001f)

        // Load 50% -> 5.0 + 0.5 * (-4.0) = 3.0f
        val speed50 = HologramProjectionCalculator.calculateSpeed(0.5f, 1.0f, invertedConfig)
        assertEquals(3.0f, speed50, 0.001f)

        // Load 100% -> 5.0 + 1.0 * (-4.0) = 1.0f
        val speed100 = HologramProjectionCalculator.calculateSpeed(1.0f, 1.0f, invertedConfig)
        assertEquals(1.0f, speed100, 0.001f)
    }

    @Test
    fun `challenge 8 - extreme sensitivity bounds zero and hyper sensitivity`() {
        val zeroSensitivityConfig = HologramBehaviorConfig(
            outerSensitivity = 0.0f,
            middleSensitivity = 0.0f,
            innerSensitivity = 0.0f
        )
        // Zero sensitivity always zeroes load -> returns vMin
        for (case in HologramInvariantCases.ALL_CASES) {
            val params = HologramProjectionCalculator.computeParameters(case.snapshot, zeroSensitivityConfig)
            assertEquals("Outer ring at 0.0 sensitivity must be vMin", 0.200f, params.outerRingSpeedRps, 0.001f)
            assertEquals("Middle ring at 0.0 sensitivity must be vMin", 0.200f, params.middleRingSpeedRps, 0.001f)
            assertEquals("Inner ring at 0.0 sensitivity must be vMin", 0.200f, params.innerRingSpeedRps, 0.001f)
        }

        // Hyper sensitivity (100.0) saturates all positive loads (> 0.01) to 1.0 -> returns vMax
        val hyperSensitivityConfig = HologramBehaviorConfig(
            outerSensitivity = 100.0f,
            middleSensitivity = 100.0f,
            innerSensitivity = 100.0f
        )
        // Case 6 (CPU 0.80): 0.80 * 100.0 = 80.0 -> clamped to 1.0 -> 5.000f
        val case6 = HologramInvariantCases.getById(6)!!
        val params6 = HologramProjectionCalculator.computeParameters(case6.snapshot, hyperSensitivityConfig)
        assertEquals(5.000f, params6.outerRingSpeedRps, 0.001f)
    }

    // ---------------------------------------------------------------------------------------------
    // Challenge 9: Palette and Meltdown Subsystem Decoupling Invariants Across 30 Cases
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `challenge 9 - alert gamuts and subsystem decoupling match precise matrix expectations`() {
        // Meltdown cases (CPU >= 90% or CPU CRITICAL_THROTTLED)
        val expectedMeltdownIds = setOf(7, 15, 20, 25, 27, 30)
        for (case in HologramInvariantCases.ALL_CASES) {
            val isExpectedMeltdown = expectedMeltdownIds.contains(case.id)
            val params = HologramProjectionCalculator.computeParameters(case.snapshot)

            assertEquals("Case #${case.id} isMeltdownAlert mismatch", isExpectedMeltdown, params.isMeltdownAlert)

            if (isExpectedMeltdown) {
                assertEquals("Meltdown Case #${case.id} outer color must be MeltdownRed", NeonPalette.MeltdownRed, params.outerColor)
                assertEquals("Meltdown Case #${case.id} middle color must be MeltdownRed", NeonPalette.MeltdownRed, params.middleColor)
                assertEquals("Meltdown Case #${case.id} inner color must be MeltdownRed", NeonPalette.MeltdownRed, params.innerColor)
                assertTrue("Meltdown Case #${case.id} status label must indicate meltdown", params.systemStatusLabel.contains("CRITICAL MELTDOWN"))
                assertEquals("Meltdown Case #${case.id} energy label must be MAX EXCEEDED", "ENERGY OUTPUT: MAX EXCEEDED", params.energyOutputLabel)
            }
        }

        // Cellular RF Degradation cases: 21, 22, 23
        for (id in 21..23) {
            val case = HologramInvariantCases.getById(id)!!
            val params = HologramProjectionCalculator.computeParameters(case.snapshot)
            assertFalse("Cellular case #$id must not trigger meltdown", params.isMeltdownAlert)
            assertEquals("Cellular case #$id outer ring must remain CyanCpu", NeonPalette.CyanCpu, params.outerColor)
            assertEquals("Cellular case #$id middle ring must remain OrangeRam", NeonPalette.OrangeRam, params.middleColor)
            assertEquals("Cellular case #$id inner ring must turn MeltdownRed", NeonPalette.MeltdownRed, params.innerColor)
            assertTrue("Cellular case #$id status label must indicate cellular loss", params.systemStatusLabel.contains("CELLULAR LINK LOST"))
        }

        // Case 28 (zRAM Thrashing): Middle ring is MemoryThrashPurple, outer is Cyan, inner is Magenta
        val case28 = HologramInvariantCases.getById(28)!!
        val params28 = HologramProjectionCalculator.computeParameters(case28.snapshot)
        assertFalse("Case 28 must not trigger meltdown", params28.isMeltdownAlert)
        assertEquals("Case 28 outer ring must remain CyanCpu", NeonPalette.CyanCpu, params28.outerColor)
        assertEquals("Case 28 middle ring must be MemoryThrashPurple", NeonPalette.MemoryThrashPurple, params28.middleColor)
        assertEquals("Case 28 inner ring must remain MagentaGpuNet", NeonPalette.MagentaGpuNet, params28.innerColor)
    }
}
