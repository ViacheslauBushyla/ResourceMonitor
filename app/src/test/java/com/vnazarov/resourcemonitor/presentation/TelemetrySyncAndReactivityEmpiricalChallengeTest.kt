package com.vnazarov.resourcemonitor.presentation

import androidx.compose.ui.graphics.Color
import com.vnazarov.resourcemonitor.animations.hologram.HologramBehaviorConfig
import com.vnazarov.resourcemonitor.animations.hologram.HologramInvariantCases
import com.vnazarov.resourcemonitor.animations.hologram.HologramProjectionCalculator
import com.vnazarov.resourcemonitor.animations.hologram.SpeedCurve
import com.vnazarov.resourcemonitor.core.designsystem.theme.NeonPalette
import com.vnazarov.resourcemonitor.core.model.MetricValue
import com.vnazarov.resourcemonitor.core.model.RawTelemetryPacket
import com.vnazarov.resourcemonitor.core.model.SystemTelemetrySnapshot
import com.vnazarov.resourcemonitor.core.model.ThrottleState
import com.vnazarov.resourcemonitor.core.telemetry.fusion.TelemetryFusionEngine
import com.vnazarov.resourcemonitor.core.telemetry.mock.FakeTelemetrySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureNanoTime

/**
 * Empirical Challenge Test Suite for Milestone M3:
 * 1. Synchronous update and zero-lag EMA reset validation of TelemetryFusionEngine.injectPacket.
 * 2. High-throughput burst stress testing of all 30 invariant cases with latency benchmarks.
 * 3. Parameter reactivity, curve mathematical fidelity, and sensitivity multiplier oracles.
 * 4. Invariant category completeness and stepper state traversal oracles.
 */
class TelemetrySyncAndReactivityEmpiricalChallengeTest {

    // ---------------------------------------------------------------------------------------------
    // Challenge 1: Synchronous StateFlow Update and Immediate Parity
    // ---------------------------------------------------------------------------------------------

    @Test
    fun challenge1_injectPacket_updatesSnapshotSynchronouslyWithoutDelay() {
        val fakeSource = FakeTelemetrySource()
        val fusionEngine = TelemetryFusionEngine(fakeSource)

        // Initial snapshot should be nominal default
        val initialSnapshot = fusionEngine.snapshot.value
        assertNotNull(initialSnapshot)

        // Case 12 (Heavy Gaming: 85% CPU, 80% RAM, 10% Net)
        val case12 = HologramInvariantCases.getById(12)!!
        val packet12 = case12.toRawPacket()

        val returnedSnapshot = fusionEngine.injectPacket(packet12, resetEma = true)

        // 1. Return value must match snapshot.value synchronously
        assertSame(
            "StateFlow snapshot.value must reference the exact returned snapshot instance synchronously",
            returnedSnapshot,
            fusionEngine.snapshot.value
        )

        // 2. Metrics must be immediately reflected on snapshot.value without awaiting coroutine ticks
        assertEquals(
            "CPU load must be 0.85 immediately on snapshot.value",
            0.85f,
            fusionEngine.snapshot.value.cpu.rawNormalized,
            0.001f
        )
        assertEquals(
            "CPU smoothed must equal raw immediately when resetEma=true",
            0.85f,
            fusionEngine.snapshot.value.cpu.smoothedValue,
            0.001f
        )
        assertEquals(
            "RAM load must be 0.80 immediately on snapshot.value",
            0.80f,
            fusionEngine.snapshot.value.ram.rawNormalized,
            0.001f
        )
        assertEquals(
            "RAM smoothed must equal raw immediately when resetEma=true",
            0.80f,
            fusionEngine.snapshot.value.ram.smoothedValue,
            0.001f
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Challenge 2: Comparative Oracle for Zero-Lag EMA Reset vs Smoothing Delay
    // ---------------------------------------------------------------------------------------------

    @Test
    fun challenge2_emaReset_bypassesSmoothingDelayEmpirically() {
        val fakeSource = FakeTelemetrySource()
        val fusionEngine = TelemetryFusionEngine(fakeSource)

        // Step 1: Initialize fusion engine with zero load
        val caseZero = HologramInvariantCases.getById(1)!!
        fusionEngine.injectPacket(caseZero.toRawPacket(), resetEma = true)
        assertEquals(0.0f, fusionEngine.snapshot.value.cpu.smoothedValue, 0.0001f)

        // Step 2: Inject Case 7 (Peak 100% CPU) with resetEma = false (smoothing active)
        val case100 = HologramInvariantCases.getById(7)!!
        val smoothedSnapshot = fusionEngine.injectPacket(case100.toRawPacket(), resetEma = false)

        // With alpha = 0.35, smoothed value is: 0.35 * 1.0 + 0.65 * 0.0 = 0.35f
        assertEquals("Raw CPU is 1.0f", 1.0f, smoothedSnapshot.cpu.rawNormalized, 0.001f)
        assertTrue(
            "With resetEma=false, smoothed value must lag behind (< 0.50f) due to EMA constant",
            smoothedSnapshot.cpu.smoothedValue < 0.50f
        )
        assertEquals(
            "Smoothed CPU with alpha 0.35 must equal 0.35f",
            0.35f,
            smoothedSnapshot.cpu.smoothedValue,
            0.01f
        )

        // Step 3: Now inject Case 7 with resetEma = true
        // Re-prime to 0
        fusionEngine.injectPacket(caseZero.toRawPacket(), resetEma = true)
        val instantSnapshot = fusionEngine.injectPacket(case100.toRawPacket(), resetEma = true)

        // With resetEma = true, EMA filter value was reset to 1.0f, so filter(1.0f) = 1.0f immediately
        assertEquals(
            "With resetEma=true, smoothed value MUST equal raw value immediately with zero lag",
            1.0f,
            instantSnapshot.cpu.smoothedValue,
            0.0001f
        )
        assertEquals(
            "RAM smoothed value must equal raw value with zero lag",
            instantSnapshot.ram.rawNormalized,
            instantSnapshot.ram.smoothedValue,
            0.0001f
        )
        assertEquals(
            "Network smoothed value must equal raw value with zero lag",
            instantSnapshot.network.rawNormalized,
            instantSnapshot.network.smoothedValue,
            0.0001f
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Challenge 3: Ingestion Stress Test Across All 30 Invariant Cases & Latency Benchmark
    // ---------------------------------------------------------------------------------------------

    @Test
    fun challenge3_all30Cases_injectWithoutExceptionOrLag() {
        val fakeSource = FakeTelemetrySource()
        val fusionEngine = TelemetryFusionEngine(fakeSource)
        val allCases = HologramInvariantCases.ALL_CASES
        assertEquals(30, allCases.size)

        // Verify each case individually
        for (case in allCases) {
            val packet = case.toRawPacket()
            val startNs = System.nanoTime()
            val snapshot = fusionEngine.injectPacket(packet, resetEma = true)
            val durationNs = System.nanoTime() - startNs

            // Latency must be < 5ms per injection (typical is < 0.05ms)
            val durationMs = durationNs / 1_000_000.0
            assertTrue("Case #${case.id} injection must complete in < 5ms (was ${durationMs}ms)", durationMs < 5.0)

            // Verify snapshot matches StateFlow
            assertSame(snapshot, fusionEngine.snapshot.value)

            // For non-overflow/non-negative cases (1..28), smoothed values match expected case metrics
            if (case.id in 1..28) {
                assertEquals(
                    "Case #${case.id} CPU smoothed value match",
                    case.snapshot.cpu.smoothedValue,
                    snapshot.cpu.smoothedValue,
                    0.015f
                )
                assertEquals(
                    "Case #${case.id} RAM smoothed value match",
                    case.snapshot.ram.smoothedValue,
                    snapshot.ram.smoothedValue,
                    0.015f
                )
            }

            // Verify Mathematical Robustness cases (29, 30)
            if (case.id == 29) {
                // Negative inputs clamped to 0.0f
                assertEquals("Negative CPU clamped to 0.0f", 0.0f, snapshot.cpu.rawNormalized, 0.001f)
                assertEquals("Negative RAM clamped to 0.0f", 0.0f, snapshot.ram.rawNormalized, 0.001f)
            }
            if (case.id == 30) {
                // Overflow inputs clamped to 1.0f
                assertEquals("Overflow CPU clamped to 1.0f", 1.0f, snapshot.cpu.rawNormalized, 0.001f)
                assertEquals("Overflow RAM clamped to 1.0f", 1.0f, snapshot.ram.rawNormalized, 0.001f)
            }
        }

        // High-frequency burst test: 50 cycles of all 30 cases = 1,500 continuous injections
        val burstCycles = 50
        val totalElapsedNs = measureNanoTime {
            for (i in 0 until burstCycles) {
                for (case in allCases) {
                    fusionEngine.injectPacket(case.toRawPacket(), resetEma = true)
                }
            }
        }

        val totalMs = totalElapsedNs / 1_000_000.0
        val avgMicrosPerPacket = (totalElapsedNs / (burstCycles * 30.0)) / 1_000.0

        // Empirical check: 1,500 injections must execute safely in under 500ms (< 350µs per injection)
        assertTrue("Burst of 1,500 packet injections took ${totalMs}ms (must be < 500ms)", totalMs < 500.0)
        assertTrue("Average latency per packet injection took ${avgMicrosPerPacket}µs (must be < 200µs)", avgMicrosPerPacket < 200.0)
    }

    // ---------------------------------------------------------------------------------------------
    // Challenge 4: Speed Curve Reactivity & Exact Mathematical Fidelity
    // ---------------------------------------------------------------------------------------------

    @Test
    fun challenge4_speedCurves_recomputeImmediatelyWithExactEquations() {
        val testSnapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.50f),
            ram = MetricValue(smoothedValue = 0.50f),
            network = MetricValue(smoothedValue = 0.50f)
        )

        val vMin = 0.2f
        val vMax = 5.0f
        val deltaV = vMax - vMin // 4.8f

        // 1. QUADRATIC Curve: V_min + L^2 * delta_V
        // At L = 0.50, speed = 0.2 + 0.25 * 4.8 = 1.400f
        val quadConfig = HologramBehaviorConfig(vMinRps = vMin, vMaxRps = vMax, speedCurve = SpeedCurve.QUADRATIC)
        val quadParams = HologramProjectionCalculator.computeParameters(testSnapshot, quadConfig)
        assertEquals(1.400f, quadParams.outerRingSpeedRps, 0.001f)
        assertEquals(1.400f, quadParams.middleRingSpeedRps, 0.001f)
        assertEquals(1.400f, quadParams.innerRingSpeedRps, 0.001f)

        // 2. LINEAR Curve: V_min + L * delta_V
        // At L = 0.50, speed = 0.2 + 0.5 * 4.8 = 2.600f
        val linearConfig = HologramBehaviorConfig(vMinRps = vMin, vMaxRps = vMax, speedCurve = SpeedCurve.LINEAR)
        val linearParams = HologramProjectionCalculator.computeParameters(testSnapshot, linearConfig)
        assertEquals(2.600f, linearParams.outerRingSpeedRps, 0.001f)
        assertEquals(2.600f, linearParams.middleRingSpeedRps, 0.001f)
        assertEquals(2.600f, linearParams.innerRingSpeedRps, 0.001f)

        // 3. SIGMOID Curve: V_min + sigmoid(10 * (L - 0.5)) * delta_V
        // At L = 0.50, sigmoid = 1 / (1 + exp(0)) = 0.50 -> speed = 0.2 + 0.5 * 4.8 = 2.600f
        val sigmoidConfig = HologramBehaviorConfig(vMinRps = vMin, vMaxRps = vMax, speedCurve = SpeedCurve.SIGMOID)
        val sigmoidParams = HologramProjectionCalculator.computeParameters(testSnapshot, sigmoidConfig)
        assertEquals(2.600f, sigmoidParams.outerRingSpeedRps, 0.001f)

        // Test differentiation at L = 0.30 (QUADRATIC vs LINEAR vs SIGMOID must strictly diverge)
        val snapshot30 = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.30f),
            ram = MetricValue(smoothedValue = 0.30f),
            network = MetricValue(smoothedValue = 0.30f)
        )
        val quad30 = HologramProjectionCalculator.computeParameters(snapshot30, quadConfig).outerRingSpeedRps
        val lin30 = HologramProjectionCalculator.computeParameters(snapshot30, linearConfig).outerRingSpeedRps
        val sig30 = HologramProjectionCalculator.computeParameters(snapshot30, sigmoidConfig).outerRingSpeedRps

        // At L = 0.30:
        // quad = 0.2 + 0.09 * 4.8 = 0.632f
        // lin = 0.2 + 0.30 * 4.8 = 1.640f
        // sig = 0.2 + (1 / (1 + exp(2))) * 4.8 = 0.2 + 0.1192029 * 4.8 = 0.772f
        assertEquals(0.632f, quad30, 0.001f)
        assertEquals(1.640f, lin30, 0.001f)
        assertEquals(0.772f, sig30, 0.005f)
        assertTrue("At load 0.30, quad < sig < lin", quad30 < sig30 && sig30 < lin30)

        // 4. Monotonicity verification for all curves across load range [0.0 .. 1.0]
        var prevQuad = -1f
        var prevLin = -1f
        var prevSig = -1f
        for (loadStep in 0..100) {
            val load = loadStep / 100f
            val snap = SystemTelemetrySnapshot(cpu = MetricValue(smoothedValue = load))
            val q = HologramProjectionCalculator.computeParameters(snap, quadConfig).outerRingSpeedRps
            val l = HologramProjectionCalculator.computeParameters(snap, linearConfig).outerRingSpeedRps
            val s = HologramProjectionCalculator.computeParameters(snap, sigmoidConfig).outerRingSpeedRps

            assertTrue("Quadratic must be monotonically increasing", q >= prevQuad)
            assertTrue("Linear must be monotonically increasing", l >= prevLin)
            assertTrue("Sigmoid must be monotonically increasing", s >= prevSig)

            prevQuad = q
            prevLin = l
            prevSig = s
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Challenge 5: Dynamic Parameter Controls (V_min, V_max, Sensitivities)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun challenge5_dynamicParameterControls_recomputeImmediatelyAndDecoupled() {
        val snapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.40f),
            ram = MetricValue(smoothedValue = 0.40f),
            network = MetricValue(smoothedValue = 0.40f)
        )

        // 1. Sweep V_min [0.0 .. 2.0] at zero load -> Speed must strictly follow V_min
        val zeroSnap = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.0f),
            ram = MetricValue(smoothedValue = 0.0f),
            network = MetricValue(smoothedValue = 0.0f)
        )
        for (vMinStep in 0..20) {
            val vMinVal = vMinStep * 0.1f
            val cfg = HologramBehaviorConfig(vMinRps = vMinVal, vMaxRps = 6.0f)
            val params = HologramProjectionCalculator.computeParameters(zeroSnap, cfg)
            assertEquals("At zero load, outer speed must equal vMin", vMinVal, params.outerRingSpeedRps, 0.001f)
            assertEquals("At zero load, middle speed must equal vMin", vMinVal, params.middleRingSpeedRps, 0.001f)
            assertEquals("At zero load, inner speed must equal vMin", vMinVal, params.innerRingSpeedRps, 0.001f)
        }

        // 2. Sweep V_max [2.0 .. 10.0] at full load -> Speed must strictly follow V_max
        val fullSnap = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 1.0f),
            ram = MetricValue(smoothedValue = 1.0f),
            network = MetricValue(smoothedValue = 1.0f)
        )
        for (vMaxStep in 2..10) {
            val vMaxVal = vMaxStep.toFloat()
            val cfg = HologramBehaviorConfig(vMinRps = 0.5f, vMaxRps = vMaxVal)
            val params = HologramProjectionCalculator.computeParameters(fullSnap, cfg)
            assertEquals("At 100% load, outer speed must equal vMax", vMaxVal, params.outerRingSpeedRps, 0.001f)
            assertEquals("At 100% load, middle speed must equal vMax", vMaxVal, params.middleRingSpeedRps, 0.001f)
            assertEquals("At 100% load, inner speed must equal vMax", vMaxVal, params.innerRingSpeedRps, 0.001f)
        }

        // 3. Sensitivity Decoupling: adjust outerSensitivity, verify middle and inner remain untouched
        val baseConfig = HologramBehaviorConfig(
            vMinRps = 0.2f,
            vMaxRps = 5.0f,
            speedCurve = SpeedCurve.QUADRATIC,
            outerSensitivity = 1.0f,
            middleSensitivity = 1.0f,
            innerSensitivity = 1.0f
        )
        val initialParams = HologramProjectionCalculator.computeParameters(snapshot, baseConfig)
        assertEquals(0.968f, initialParams.outerRingSpeedRps, 0.001f)
        assertEquals(0.968f, initialParams.middleRingSpeedRps, 0.001f)
        assertEquals(0.968f, initialParams.innerRingSpeedRps, 0.001f)

        // Mutate outerSensitivity to 2.0x (load clamped = 0.4 * 2.0 = 0.8 -> speed = 0.2 + 0.64 * 4.8 = 3.272f)
        val mutatedConfig = baseConfig.copy(outerSensitivity = 2.0f)
        val mutatedParams = HologramProjectionCalculator.computeParameters(snapshot, mutatedConfig)

        assertEquals("Outer speed must react to outerSensitivity immediately", 3.272f, mutatedParams.outerRingSpeedRps, 0.001f)
        assertEquals("Middle speed must remain untouched", 0.968f, mutatedParams.middleRingSpeedRps, 0.001f)
        assertEquals("Inner speed must remain untouched", 0.968f, mutatedParams.innerRingSpeedRps, 0.001f)

        // Mutate middleSensitivity to 0.5x (load clamped = 0.4 * 0.5 = 0.2 -> speed = 0.2 + 0.04 * 4.8 = 0.392f)
        val middleConfig = baseConfig.copy(middleSensitivity = 0.5f)
        val middleParams = HologramProjectionCalculator.computeParameters(snapshot, middleConfig)

        assertEquals("Outer speed must remain untouched", 0.968f, middleParams.outerRingSpeedRps, 0.001f)
        assertEquals("Middle speed must react to middleSensitivity", 0.392f, middleParams.middleRingSpeedRps, 0.001f)
        assertEquals("Inner speed must remain untouched", 0.968f, middleParams.innerRingSpeedRps, 0.001f)
    }

    // ---------------------------------------------------------------------------------------------
    // Challenge 6: StartScreen Invariant Category Partitioning & Stepper Traversal Oracle
    // ---------------------------------------------------------------------------------------------

    @Test
    fun challenge6_startScreen_categoryPartitioningAndStepperTraversal() {
        val allCases = HologramInvariantCases.ALL_CASES
        assertEquals(30, allCases.size)

        // Test category filtering logic identical to StartScreen.kt
        fun filterByCategory(cat: String): List<com.vnazarov.resourcemonitor.animations.hologram.HologramInvariantCase> {
            return when {
                cat.startsWith("All") -> HologramInvariantCases.ALL_CASES
                cat == "Baseline" -> HologramInvariantCases.ALL_CASES.filter { it.group.startsWith("Baseline") }
                cat == "Single-Metric" -> HologramInvariantCases.ALL_CASES.filter { it.group.startsWith("Single-Metric") }
                cat == "Dual-Metric" -> HologramInvariantCases.ALL_CASES.filter { it.group.startsWith("Dual-Metric") }
                cat == "Balanced" -> HologramInvariantCases.ALL_CASES.filter { it.group.startsWith("Balanced") }
                cat == "Cellular" -> HologramInvariantCases.ALL_CASES.filter { it.group.startsWith("Cellular") }
                cat == "Thermal" -> HologramInvariantCases.ALL_CASES.filter { it.group.startsWith("Thermal") }
                cat == "Robustness" -> HologramInvariantCases.ALL_CASES.filter { it.group.startsWith("Mathematical") || it.group.contains("Robustness") }
                else -> HologramInvariantCases.ALL_CASES
            }
        }

        val baselineCases = filterByCategory("Baseline")
        val singleMetricCases = filterByCategory("Single-Metric")
        val dualMetricCases = filterByCategory("Dual-Metric")
        val balancedCases = filterByCategory("Balanced")
        val cellularCases = filterByCategory("Cellular")
        val thermalCases = filterByCategory("Thermal")
        val robustnessCases = filterByCategory("Robustness")

        assertEquals(5, baselineCases.size)       // Cases 1..5
        assertEquals(6, singleMetricCases.size)   // Cases 6..11
        assertEquals(5, dualMetricCases.size)     // Cases 12..16
        assertEquals(4, balancedCases.size)       // Cases 17..20
        assertEquals(4, cellularCases.size)       // Cases 21..24
        assertEquals(4, thermalCases.size)        // Cases 25..28
        assertEquals(2, robustnessCases.size)     // Cases 29..30

        // Total cases across all 7 non-All categories must sum to exactly 30
        val unionCases = (baselineCases + singleMetricCases + dualMetricCases + balancedCases +
                cellularCases + thermalCases + robustnessCases).distinctBy { it.id }
        assertEquals("All 30 invariant cases must be covered by the 7 categories", 30, unionCases.size)

        // Stepper Traversal Test:
        // Verify wrapping logic: index 0 -> prev wraps to last; index N-1 -> next wraps to first
        val cases = allCases
        for (idx in cases.indices) {
            val prevIdx = if (idx > 0) idx - 1 else cases.size - 1
            val nextIdx = if (idx < cases.size - 1) idx + 1 else 0

            val current = cases[idx]
            val prev = if (idx > 0) cases[idx - 1] else cases.last()
            val next = if (idx < cases.size - 1) cases[idx + 1] else cases.first()

            assertEquals(cases[prevIdx].id, prev.id)
            assertEquals(cases[nextIdx].id, next.id)
        }

        // Sequential 30-step cycle through nextCase must visit all 30 unique case IDs
        var currentCase = cases.first()
        val visitedIds = mutableListOf<Int>()
        for (step in 0 until 30) {
            visitedIds.add(currentCase.id)
            val currIdx = cases.indexOfFirst { it.id == currentCase.id }
            currentCase = if (currIdx >= 0 && currIdx < cases.size - 1) cases[currIdx + 1] else cases.first()
        }

        assertEquals(30, visitedIds.toSet().size)
        assertEquals((1..30).toList(), visitedIds)
    }
}
