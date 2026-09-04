package com.vnazarov.resourcemonitor.animations.hologram

import androidx.compose.ui.graphics.Color
import com.vnazarov.resourcemonitor.core.designsystem.theme.NeonPalette
import com.vnazarov.resourcemonitor.core.model.MetricValue
import com.vnazarov.resourcemonitor.core.model.SystemTelemetrySnapshot
import com.vnazarov.resourcemonitor.core.model.ThrottleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

/**
 * Empirical Challenger Test Suite for Milestone M3:
 * 1. Boundary & Extreme Mutation Stress: Rapidly mutating 5-channel load inputs (-1000.0 .. 1000.0, NaN, Infs, subnormals).
 * 2. High-Concurrency Stress: Multi-threaded parallel calls to `HologramProjectionCalculator.computeParameters()`.
 * 3. Combinatorial ThrottleState Stress: Exhaustive test of all 3^7 = 2,187 ThrottleState combinations.
 * 4. Floating-Point Precision & Drift: Verifying speed deviations never exceed +/- 0.01 RPS (measured against Double oracle).
 * 5. Full 30-case Expanded Matrix Independent Oracle Verification.
 */
class ExpandedHologramEmpiricalChallengerTest {

    private val speedToleranceRps = 0.01f
    private val fineToleranceRps = 0.0001f

    // ---------------------------------------------------------------------------------------------
    // Double-Precision Theoretical Speed Oracles
    // ---------------------------------------------------------------------------------------------

    private fun oracleSpeedDouble(
        rawLoad: Float,
        sensitivity: Float,
        config: HologramBehaviorConfig
    ): Double {
        val safeLoad = if (rawLoad.isNaN()) 0.0 else rawLoad.toDouble()
        val effectiveLoad = safeLoad * sensitivity.toDouble()
        val clampedLoad = effectiveLoad.coerceIn(0.0, 1.0)
        val vMin = config.vMinRps.toDouble()
        val vMax = config.vMaxRps.toDouble()
        val vRange = vMax - vMin

        return when (config.speedCurve) {
            SpeedCurve.QUADRATIC -> vMin + (clampedLoad * clampedLoad) * vRange
            SpeedCurve.LINEAR -> vMin + clampedLoad * vRange
            SpeedCurve.SIGMOID -> {
                val sigmoid = 1.0 / (1.0 + exp(-10.0 * (clampedLoad - 0.5)))
                vMin + sigmoid * vRange
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 1. Boundary Mutation Stress: -1000.0 .. 1000.0, NaN, Infs, Subnormals
    // ---------------------------------------------------------------------------------------------

    @Test
    fun test1_boundaryMutationsAcrossExtremeRange_minus1000ToPlus1000() {
        val boundaryLoads = listOf(
            -1000.0f, -500.0f, -100.0f, -10.0f, -2.0f, -1.0f, -0.5f, -0.0001f, -0.0f, 0.0f,
            0.000001f, 0.01f, 0.10f, 0.25f, 0.50f, 0.75f, 0.90f, 0.9999f, 1.0f,
            1.0001f, 1.5f, 2.0f, 5.0f, 10.0f, 50.0f, 100.0f, 500.0f, 1000.0f,
            Float.MIN_VALUE, Float.MAX_VALUE, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NaN
        )

        for (curve in SpeedCurve.values()) {
            val config = HologramBehaviorConfig(speedCurve = curve)
            val vMin = config.vMinRps
            val vMax = config.vMaxRps

            for (load in boundaryLoads) {
                // Test each of the 5 channels independently
                for (channel in 1..5) {
                    val sens = when (channel) {
                        1 -> config.outerSensitivity
                        2 -> config.middleSensitivity
                        3 -> config.innerSensitivity
                        4 -> config.storageSensitivity
                        5 -> config.gpuSensitivity
                        else -> 1.0f
                    }

                    val actualSpeed = HologramProjectionCalculator.calculateSpeed(load, sens, config)

                    // Invariant: Result must NEVER be NaN or Infinite
                    assertFalse("Channel $channel curve $curve load $load must not be NaN", actualSpeed.isNaN())
                    assertFalse("Channel $channel curve $curve load $load must not be Infinite", actualSpeed.isInfinite())

                    // Invariant: Clamping guarantees bounds
                    when {
                        load.isNaN() || load <= 0.0f -> {
                            val expectedMin = when (curve) {
                                SpeedCurve.QUADRATIC, SpeedCurve.LINEAR -> vMin
                                SpeedCurve.SIGMOID -> (vMin + (1.0f / (1.0f + exp(5.0f))) * (vMax - vMin))
                            }
                            assertEquals(
                                "Underflow clamp for channel $channel, curve $curve, load $load",
                                expectedMin,
                                actualSpeed,
                                fineToleranceRps
                            )
                        }
                        load >= 1.0f -> {
                            val expectedMax = when (curve) {
                                SpeedCurve.QUADRATIC, SpeedCurve.LINEAR -> vMax
                                SpeedCurve.SIGMOID -> (vMin + (1.0f / (1.0f + exp(-5.0f))) * (vMax - vMin))
                            }
                            assertEquals(
                                "Overflow clamp for channel $channel, curve $curve, load $load",
                                expectedMax,
                                actualSpeed,
                                fineToleranceRps
                            )
                        }
                        else -> {
                            val theo = oracleSpeedDouble(load, sens, config).toFloat()
                            assertEquals(
                                "Intermediate value for channel $channel, curve $curve, load $load",
                                theo,
                                actualSpeed,
                                fineToleranceRps
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun test2_fuzzingRapidlyMutatingRandomVectorsAcrossExtremeBoundaries() {
        val rng = Random(42) // Deterministic seed for reproducible adversarial fuzzing
        val configs = listOf(
            HologramBehaviorConfig(speedCurve = SpeedCurve.QUADRATIC),
            HologramBehaviorConfig(speedCurve = SpeedCurve.LINEAR),
            HologramBehaviorConfig(speedCurve = SpeedCurve.SIGMOID),
            HologramBehaviorConfig(vMinRps = 0.5f, vMaxRps = 8.0f, outerSensitivity = 1.5f, storageSensitivity = 1.2f)
        )

        val sampleExtremes = floatArrayOf(
            -1000f, -100f, -1f, 0f, 0.001f, 0.5f, 1.0f, 2.0f, 100f, 1000f,
            Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY
        )

        // Run 10,000 randomized 5-channel mutations
        for (i in 0 until 10_000) {
            val config = configs[i % configs.size]

            val cpu = if (rng.nextBoolean()) rng.nextDouble(-1000.0, 1000.0).toFloat() else sampleExtremes[rng.nextInt(sampleExtremes.size)]
            val ram = if (rng.nextBoolean()) rng.nextDouble(-1000.0, 1000.0).toFloat() else sampleExtremes[rng.nextInt(sampleExtremes.size)]
            val net = if (rng.nextBoolean()) rng.nextDouble(-1000.0, 1000.0).toFloat() else sampleExtremes[rng.nextInt(sampleExtremes.size)]
            val ssd = if (rng.nextBoolean()) rng.nextDouble(-1000.0, 1000.0).toFloat() else sampleExtremes[rng.nextInt(sampleExtremes.size)]
            val gpu = if (rng.nextBoolean()) rng.nextDouble(-1000.0, 1000.0).toFloat() else sampleExtremes[rng.nextInt(sampleExtremes.size)]

            val snapshot = SystemTelemetrySnapshot(
                cpu = MetricValue(smoothedValue = cpu),
                ram = MetricValue(smoothedValue = ram),
                network = MetricValue(smoothedValue = net),
                storageIo = MetricValue(smoothedValue = ssd),
                gpu = MetricValue(smoothedValue = gpu)
            )

            val params = HologramProjectionCalculator.computeParameters(snapshot, config)

            // Verify no NaN or Inf in output speeds
            assertFalse("Iteration $i: R1 speed NaN", params.ring1SpeedRps.isNaN())
            assertFalse("Iteration $i: R2 speed NaN", params.ring2SpeedRps.isNaN())
            assertFalse("Iteration $i: R3 speed NaN", params.ring3SpeedRps.isNaN())
            assertFalse("Iteration $i: R4 speed NaN", params.ring4SpeedRps.isNaN())
            assertFalse("Iteration $i: R5 speed NaN", params.ring5SpeedRps.isNaN())

            assertFalse("Iteration $i: R1 speed Infinite", params.ring1SpeedRps.isInfinite())
            assertFalse("Iteration $i: R2 speed Infinite", params.ring2SpeedRps.isInfinite())
            assertFalse("Iteration $i: R3 speed Infinite", params.ring3SpeedRps.isInfinite())
            assertFalse("Iteration $i: R4 speed Infinite", params.ring4SpeedRps.isInfinite())
            assertFalse("Iteration $i: R5 speed Infinite", params.ring5SpeedRps.isInfinite())

            // Speeds must stay strictly within bounds [vMin, vMax]
            val minBound = config.vMinRps
            val maxBound = config.vMaxRps
            assertTrue("Iteration $i: R1 out of [vMin, vMax]", params.ring1SpeedRps in minBound..maxBound)
            assertTrue("Iteration $i: R2 out of [vMin, vMax]", params.ring2SpeedRps in minBound..maxBound)
            assertTrue("Iteration $i: R3 out of [vMin, vMax]", params.ring3SpeedRps in minBound..maxBound)
            assertTrue("Iteration $i: R4 out of [vMin, vMax]", params.ring4SpeedRps in minBound..maxBound)
            // R5 is either within bounds or pinned to 3.000f in Case 25 scenario
            assertTrue("Iteration $i: R5 out of bounds", params.ring5SpeedRps in minBound..maxBound || params.ring5SpeedRps == 3.000f)

            // Backward compatibility aliases must match identically
            assertEquals(params.ring1SpeedRps, params.outerRingSpeedRps, 0.0f)
            assertEquals(params.ring2SpeedRps, params.middleRingSpeedRps, 0.0f)
            assertEquals(params.ring3SpeedRps, params.innerRingSpeedRps, 0.0f)
            assertEquals(params.ring1Color, params.outerColor)
            assertEquals(params.ring2Color, params.middleColor)
            assertEquals(params.ring3Color, params.innerColor)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 2. High-Concurrency Stress: Multi-Threaded Concurrent Execution
    // ---------------------------------------------------------------------------------------------

    @Test
    fun test3_highConcurrencyCallsToComputeParameters() {
        val threadCount = 16
        val iterationsPerThread = 2_500 // 40,000 total concurrent invocations
        val executor = Executors.newFixedThreadPool(threadCount)
        val failureCount = AtomicInteger(0)

        val configs = listOf(
            HologramBehaviorConfig(speedCurve = SpeedCurve.QUADRATIC),
            HologramBehaviorConfig(speedCurve = SpeedCurve.LINEAR),
            HologramBehaviorConfig(speedCurve = SpeedCurve.SIGMOID),
            HologramBehaviorConfig(outerSensitivity = 1.8f, gpuSensitivity = 0.5f)
        )

        val tasks = (0 until threadCount).map { threadId ->
            Callable {
                val threadRng = Random(threadId * 1000L + 7)
                for (iter in 0 until iterationsPerThread) {
                    val config = configs[iter % configs.size]
                    val cLoad = threadRng.nextFloat()
                    val rLoad = threadRng.nextFloat()
                    val nLoad = threadRng.nextFloat()
                    val sLoad = threadRng.nextFloat()
                    val gLoad = threadRng.nextFloat()

                    val snapshot = SystemTelemetrySnapshot(
                        cpu = MetricValue(smoothedValue = cLoad),
                        ram = MetricValue(smoothedValue = rLoad),
                        network = MetricValue(smoothedValue = nLoad),
                        storageIo = MetricValue(smoothedValue = sLoad),
                        gpu = MetricValue(smoothedValue = gLoad)
                    )

                    try {
                        val params1 = HologramProjectionCalculator.computeParameters(snapshot, config)
                        val params2 = HologramProjectionCalculator.computeParameters(snapshot, config)

                        // Invariant: Pure function determinism under concurrency
                        if (params1.ring1SpeedRps != params2.ring1SpeedRps ||
                            params1.ring2SpeedRps != params2.ring2SpeedRps ||
                            params1.ring3SpeedRps != params2.ring3SpeedRps ||
                            params1.ring4SpeedRps != params2.ring4SpeedRps ||
                            params1.ring5SpeedRps != params2.ring5SpeedRps ||
                            params1.ring1Color != params2.ring1Color ||
                            params1.ring4Color != params2.ring4Color ||
                            params1.systemStatusLabel != params2.systemStatusLabel
                        ) {
                            failureCount.incrementAndGet()
                        }
                    } catch (e: Throwable) {
                        failureCount.incrementAndGet()
                    }
                }
            }
        }

        val futures = executor.invokeAll(tasks)
        executor.shutdown()
        val finished = executor.awaitTermination(15, TimeUnit.SECONDS)

        assertTrue("Executor must terminate within timeout", finished)
        for (f in futures) {
            f.get() // Verify no uncaught exceptions in threads
        }

        assertEquals("Zero race conditions or non-deterministic outputs in concurrent execution", 0, failureCount.get())
    }

    // ---------------------------------------------------------------------------------------------
    // 3. Combinatorial ThrottleState Stress: All 3^7 = 2,187 Combinations
    // ---------------------------------------------------------------------------------------------

    @Test
    fun test4_combinatorialThrottleStateStress_all2187Combinations() {
        val states = ThrottleState.values()
        val config = HologramBehaviorConfig()
        var combinationCount = 0

        for (worst in states) {
            for (cpuTh in states) {
                for (ramTh in states) {
                    for (netTh in states) {
                        for (ssdTh in states) {
                            for (gpuTh in states) {
                                for (cellTh in states) {
                                    combinationCount++

                                    val cpuLoad = 0.50f
                                    val ramLoad = 0.50f
                                    val netLoad = 0.50f
                                    val ssdLoad = 0.50f
                                    val gpuLoad = 0.50f

                                    val snapshot = SystemTelemetrySnapshot(
                                        cpu = MetricValue(smoothedValue = cpuLoad, throttleState = cpuTh),
                                        ram = MetricValue(smoothedValue = ramLoad, throttleState = ramTh),
                                        network = MetricValue(smoothedValue = netLoad, throttleState = netTh),
                                        storageIo = MetricValue(smoothedValue = ssdLoad, throttleState = ssdTh),
                                        gpu = MetricValue(smoothedValue = gpuLoad, throttleState = gpuTh),
                                        cellularQuality = MetricValue(smoothedValue = 0.8f, throttleState = cellTh),
                                        worstThrottleState = worst
                                    )

                                    val params = HologramProjectionCalculator.computeParameters(snapshot, config)

                                    // 1. Expected Meltdown Flag
                                    val expectedForcedOsThermal = worst == ThrottleState.CRITICAL_THROTTLED &&
                                            cpuTh == ThrottleState.CRITICAL_THROTTLED &&
                                            cpuLoad <= 0.65f &&
                                            gpuTh == ThrottleState.CRITICAL_THROTTLED
                                    val expectedMeltdown = cpuTh == ThrottleState.CRITICAL_THROTTLED ||
                                            cpuLoad >= config.meltdownThreshold ||
                                            (worst == ThrottleState.CRITICAL_THROTTLED && (cpuLoad >= 0.85f || expectedForcedOsThermal))

                                    assertEquals("Meltdown flag mismatch for combination #$combinationCount", expectedMeltdown, params.isMeltdownAlert)

                                    // 2. Expected Storage Stall Flag
                                    val expectedStorageStall = ssdTh == ThrottleState.CRITICAL_THROTTLED
                                    assertEquals("Storage stall flag mismatch for combination #$combinationCount", expectedStorageStall, params.isStorageStallAlert)

                                    // 3. Expected Memory Thrash Flag
                                    val expectedMemoryThrash = ramTh == ThrottleState.CRITICAL_THROTTLED
                                    assertEquals("Memory thrash flag mismatch for combination #$combinationCount", expectedMemoryThrash, params.isMemoryThrashAlert)

                                    // 4. Expected Cellular Degraded Flag
                                    val expectedCellularDegraded = cellTh != ThrottleState.NOMINAL
                                    assertEquals("Cellular degraded flag mismatch for combination #$combinationCount", expectedCellularDegraded, params.isCellularDegradedAlert)

                                    // 5. Expected Colors
                                    if (expectedMeltdown) {
                                        assertEquals(NeonPalette.MeltdownRed, params.ring1Color)
                                        assertEquals(NeonPalette.MeltdownRed, params.ring2Color)
                                        assertEquals(NeonPalette.MeltdownRed, params.ring3Color)
                                        if (expectedStorageStall) {
                                            assertEquals(NeonPalette.StorageStallWhite, params.ring4Color)
                                        } else {
                                            assertEquals(NeonPalette.MeltdownRed, params.ring4Color)
                                        }
                                        assertEquals(NeonPalette.MeltdownRed, params.ring5Color)
                                    } else {
                                        assertEquals(NeonPalette.CyanCpu, params.ring1Color)
                                        val expR2Color = if (expectedMemoryThrash) NeonPalette.MemoryThrashPurple else NeonPalette.OrangeRam
                                        assertEquals(expR2Color, params.ring2Color)

                                        val expR3Color = when (cellTh) {
                                            ThrottleState.CRITICAL_THROTTLED -> NeonPalette.MeltdownRed
                                            ThrottleState.WARNING_BOOST -> NeonPalette.WarningAmber
                                            else -> NeonPalette.MagentaGpuNet
                                        }
                                        assertEquals(expR3Color, params.ring3Color)

                                        val expR4Color = if (expectedStorageStall) NeonPalette.StorageStallWhite else NeonPalette.IceBlueStorage
                                        assertEquals(expR4Color, params.ring4Color)

                                        val expR5Color = if (gpuTh == ThrottleState.CRITICAL_THROTTLED) NeonPalette.MeltdownRed else NeonPalette.EmeraldGpu
                                        assertEquals(expR5Color, params.ring5Color)
                                    }

                                    // 6. Expected Status Label Precedence
                                    when {
                                        expectedStorageStall -> assertTrue("Status must have IO_WAIT_STALL", params.systemStatusLabel.contains("IO_WAIT_STALL"))
                                        expectedMeltdown -> assertTrue("Status must have CRITICAL MELTDOWN", params.systemStatusLabel.contains("CRITICAL MELTDOWN"))
                                        cellTh == ThrottleState.CRITICAL_THROTTLED -> assertEquals("CELLULAR LINK LOST", params.systemStatusLabel)
                                        cellTh == ThrottleState.WARNING_BOOST -> assertEquals("CELLULAR SIGNAL DEGRADED", params.systemStatusLabel)
                                        cpuTh == ThrottleState.WARNING_BOOST || expectedMemoryThrash -> assertEquals("SYSTEM BOOST ACTIVE", params.systemStatusLabel)
                                        else -> assertTrue("Status must indicate NOMINAL", params.systemStatusLabel.contains("SYSTEM NOMINAL"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        assertEquals("Must test exactly 3^7 = 2187 combinations", 2187, combinationCount)
    }

    // ---------------------------------------------------------------------------------------------
    // 4. Floating-Point Precision & Drift: Speeds Never Drift Beyond +/- 0.01 RPS
    // ---------------------------------------------------------------------------------------------

    @Test
    fun test5_floatingPointSpeedsNeverDriftBeyondToleranceAcrossFullSpectrum() {
        val curves = SpeedCurve.values()
        val sensitivities = listOf(0.1f, 0.5f, 0.8f, 1.0f, 1.2f, 1.5f, 2.0f)
        val configs = listOf(
            HologramBehaviorConfig(vMinRps = 0.2f, vMaxRps = 5.0f),
            HologramBehaviorConfig(vMinRps = 0.0f, vMaxRps = 1.0f),
            HologramBehaviorConfig(vMinRps = 0.5f, vMaxRps = 10.0f),
            HologramBehaviorConfig(vMinRps = 1.0f, vMaxRps = 20.0f)
        )

        var maxObservedDrift = 0.0
        var totalPointsChecked = 0L

        for (baseConfig in configs) {
            for (curve in curves) {
                val config = baseConfig.copy(speedCurve = curve)

                for (sens in sensitivities) {
                    // Test 10,001 points from 0.0 to 1.0 in steps of 0.0001
                    for (step in 0..10_000) {
                        val load = step / 10_000.0f
                        val actual = HologramProjectionCalculator.calculateSpeed(load, sens, config)
                        val oracle = oracleSpeedDouble(load, sens, config)

                        val drift = abs(actual.toDouble() - oracle)
                        if (drift > maxObservedDrift) {
                            maxObservedDrift = drift
                        }

                        assertTrue(
                            "Floating-point drift $drift exceeds +/- 0.01 RPS at load=$load, sens=$sens, curve=$curve",
                            drift <= speedToleranceRps
                        )
                        totalPointsChecked++
                    }
                }
            }
        }

        println("Drift stress test verified $totalPointsChecked points. Maximum observed drift: $maxObservedDrift RPS.")
        assertTrue("Max observed drift $maxObservedDrift must be well below tolerance 0.01 RPS", maxObservedDrift < 0.001)
    }

    // ---------------------------------------------------------------------------------------------
    // 5. Direct Cross-Verification of All 30 Expanded Invariant Cases Against Theoretical Oracle
    // ---------------------------------------------------------------------------------------------

    @Test
    fun test6_crossVerifyAll30ExpandedCasesWithTheoreticalSpeedOracle() {
        val cases = HologramInvariantCases.EXPANDED_CASES
        assertEquals("Expanded cases count must be 30", 30, cases.size)

        val defaultConfig = HologramBehaviorConfig()

        for (case in cases) {
            val cpuLoad = case.snapshot.cpu.smoothedValue
            val ramLoad = case.snapshot.ram.smoothedValue
            val netLoad = case.snapshot.network.smoothedValue
            val ssdLoad = case.snapshot.storageIo.smoothedValue
            val gpuLoad = case.snapshot.gpu.smoothedValue

            // Compute theoretical double speeds
            val theo1 = oracleSpeedDouble(cpuLoad, defaultConfig.outerSensitivity, defaultConfig).toFloat()
            val theo2 = oracleSpeedDouble(ramLoad, defaultConfig.middleSensitivity, defaultConfig).toFloat()
            val theo3 = oracleSpeedDouble(netLoad, defaultConfig.innerSensitivity, defaultConfig).toFloat()
            val theo4 = oracleSpeedDouble(ssdLoad, defaultConfig.storageSensitivity, defaultConfig).toFloat()
            val theo5 = if (case.id == 25) 3.000f else oracleSpeedDouble(gpuLoad, defaultConfig.gpuSensitivity, defaultConfig).toFloat()

            val actualParams = HologramProjectionCalculator.computeParameters(case.snapshot)

            // Verify Case Expected matches Theoretical Oracle within 0.01 RPS
            assertEquals("Case #${case.id} R1 theoretical vs expected drift", theo1, case.expectedR1SpeedRps, speedToleranceRps)
            assertEquals("Case #${case.id} R2 theoretical vs expected drift", theo2, case.expectedR2SpeedRps, speedToleranceRps)
            assertEquals("Case #${case.id} R3 theoretical vs expected drift", theo3, case.expectedR3SpeedRps, speedToleranceRps)
            assertEquals("Case #${case.id} R4 theoretical vs expected drift", theo4, case.expectedR4SpeedRps, speedToleranceRps)
            assertEquals("Case #${case.id} R5 theoretical vs expected drift", theo5, case.expectedR5SpeedRps, speedToleranceRps)

            // Verify Calculator actual output matches Theoretical Oracle within 0.01 RPS
            assertEquals("Case #${case.id} R1 actual vs theoretical drift", theo1, actualParams.ring1SpeedRps, speedToleranceRps)
            assertEquals("Case #${case.id} R2 actual vs theoretical drift", theo2, actualParams.ring2SpeedRps, speedToleranceRps)
            assertEquals("Case #${case.id} R3 actual vs theoretical drift", theo3, actualParams.ring3SpeedRps, speedToleranceRps)
            assertEquals("Case #${case.id} R4 actual vs theoretical drift", theo4, actualParams.ring4SpeedRps, speedToleranceRps)
            assertEquals("Case #${case.id} R5 actual vs theoretical drift", theo5, actualParams.ring5SpeedRps, speedToleranceRps)

            // Verify colors
            assertEquals("Case #${case.id} R1 color mismatch", case.expectedR1Color, actualParams.ring1Color)
            assertEquals("Case #${case.id} R2 color mismatch", case.expectedR2Color, actualParams.ring2Color)
            assertEquals("Case #${case.id} R3 color mismatch", case.expectedR3Color, actualParams.ring3Color)
            assertEquals("Case #${case.id} R4 color mismatch", case.expectedR4Color, actualParams.ring4Color)
            assertEquals("Case #${case.id} R5 color mismatch", case.expectedR5Color, actualParams.ring5Color)

            // Verify Alert Flags
            assertEquals("Case #${case.id} meltdown mismatch", case.expectedIsMeltdown, actualParams.isMeltdownAlert)
            assertEquals("Case #${case.id} storage stall mismatch", case.expectedIsStorageStall, actualParams.isStorageStallAlert)
            assertEquals("Case #${case.id} memory thrash mismatch", case.expectedIsMemoryThrash, actualParams.isMemoryThrashAlert)
            assertEquals("Case #${case.id} cellular degraded mismatch", case.expectedIsCellularDegraded, actualParams.isCellularDegradedAlert)
        }
    }
}
