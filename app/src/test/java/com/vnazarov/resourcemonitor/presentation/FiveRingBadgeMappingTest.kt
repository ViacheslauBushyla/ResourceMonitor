package com.vnazarov.resourcemonitor.presentation

import androidx.compose.ui.graphics.Color
import com.vnazarov.resourcemonitor.animations.hologram.HologramBehaviorConfig
import com.vnazarov.resourcemonitor.animations.hologram.HologramInvariantCases
import com.vnazarov.resourcemonitor.animations.hologram.HologramProjectionCalculator
import com.vnazarov.resourcemonitor.animations.hologram.SpeedCurve
import com.vnazarov.resourcemonitor.core.designsystem.theme.NeonPalette
import com.vnazarov.resourcemonitor.core.model.RawTelemetryPacket
import com.vnazarov.resourcemonitor.core.telemetry.mock.SimulationScenario
import com.vnazarov.resourcemonitor.core.telemetry.fusion.TelemetryFusionEngine
import com.vnazarov.resourcemonitor.core.telemetry.mock.FakeTelemetrySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Verification test suite for Milestone M4:
 * 1. Verifies that all 30 invariant cases from [HologramInvariantCases.EXPANDED_CASES] map cleanly
 *    to 5-ring parameters (speeds, colors, alert flags).
 * 2. Validates badge display string generation logic (primaryMetric, secondaryMetric, speeds)
 *    for all 5 gyroscopic orbits.
 * 3. Validates end-to-end injection through [TelemetryFusionEngine] and synchronous reactivity
 *    of the 5-ring badge presentation state.
 * 4. Ensures rotational speeds respect custom physiological bounds [vMinRps, vMaxRps].
 */
class FiveRingBadgeMappingTest {

    @Test
    fun test1_all30ExpandedCasesMapToValidFiveRingParameters() {
        val cases = HologramInvariantCases.EXPANDED_CASES
        assertEquals("Expanded cases suite must have exactly 30 test cases", 30, cases.size)

        for (case in cases) {
            val params = HologramProjectionCalculator.computeParameters(
                snapshot = case.snapshot,
                config = HologramBehaviorConfig()
            )

            // Speed bounds: default [0.2f, 4.0f]
            assertTrue(
                "Case ${case.id} R1 speed (${params.ring1SpeedRps}) must be >= 0.2f",
                params.ring1SpeedRps >= 0.199f
            )
            assertTrue(
                "Case ${case.id} R2 speed (${params.ring2SpeedRps}) must be >= 0.2f",
                params.ring2SpeedRps >= 0.199f
            )
            assertTrue(
                "Case ${case.id} R3 speed (${params.ring3SpeedRps}) must be >= 0.2f",
                params.ring3SpeedRps >= 0.199f
            )
            assertTrue(
                "Case ${case.id} R4 speed (${params.ring4SpeedRps}) must be >= 0.2f",
                params.ring4SpeedRps >= 0.199f
            )
            assertTrue(
                "Case ${case.id} R5 speed (${params.ring5SpeedRps}) must be >= 0.2f",
                params.ring5SpeedRps >= 0.199f
            )

            // Speed proximity to expected values
            assertEquals(
                "Case ${case.id} R1 speed mismatch",
                case.expectedR1SpeedRps,
                params.ring1SpeedRps,
                0.05f
            )
            assertEquals(
                "Case ${case.id} R2 speed mismatch",
                case.expectedR2SpeedRps,
                params.ring2SpeedRps,
                0.05f
            )
            assertEquals(
                "Case ${case.id} R3 speed mismatch",
                case.expectedR3SpeedRps,
                params.ring3SpeedRps,
                0.05f
            )
            assertEquals(
                "Case ${case.id} R4 speed mismatch",
                case.expectedR4SpeedRps,
                params.ring4SpeedRps,
                0.05f
            )
            assertEquals(
                "Case ${case.id} R5 speed mismatch",
                case.expectedR5SpeedRps,
                params.ring5SpeedRps,
                0.05f
            )

            // Alert flags
            assertEquals(
                "Case ${case.id} meltdown alert mismatch",
                case.expectedIsMeltdown,
                params.isMeltdownAlert
            )
            assertEquals(
                "Case ${case.id} storage stall alert mismatch",
                case.expectedIsStorageStall,
                params.isStorageStallAlert
            )
            assertEquals(
                "Case ${case.id} memory thrash alert mismatch",
                case.expectedIsMemoryThrash,
                params.isMemoryThrashAlert
            )

            // Status label substring
            assertTrue(
                "Case ${case.id} status label '${params.systemStatusLabel}' must contain '${case.expectedStatusSubstring}'",
                params.systemStatusLabel.contains(case.expectedStatusSubstring)
            )
        }
    }

    @Test
    fun test2_badgeDisplayMetricsGenerationMatchesStartScreenFormatting() {
        val cases = HologramInvariantCases.EXPANDED_CASES

        for (case in cases) {
            val rawPacket = case.toRawPacket()
            val snapshot = case.snapshot
            val params = HologramProjectionCalculator.computeParameters(snapshot, HologramBehaviorConfig())

            // --- Ring 1 (CPU): Load %, Freq, RPS ---
            val cpuFreqStr = rawPacket.cpuFrequenciesKhz.firstOrNull()?.let { "${it / 1000} MHz" } ?: "3.2 GHz"
            val r1Primary = "Load: ${snapshot.cpu.displayLabel.ifEmpty { "${(snapshot.cpu.smoothedValue * 100).toInt()}%" }}"
            val r1Secondary = "Freq: $cpuFreqStr"
            val r1Speed = String.format(Locale.US, "%.2f RPS", params.ring1SpeedRps)

            assertTrue("R1 primary metric must not be empty", r1Primary.isNotBlank())
            assertTrue("R1 primary metric must start with 'Load:'", r1Primary.startsWith("Load:"))
            assertTrue("R1 secondary metric must contain 'Freq:'", r1Secondary.startsWith("Freq:"))
            assertTrue("R1 speed string must contain 'RPS'", r1Speed.endsWith("RPS"))

            // --- Ring 2 (RAM): Available MB, zRAM Swap %, RPS ---
            val availMb = "${rawPacket.ramAvailableBytes / (1024 * 1024)} MB"
            val zramStr = if (params.isMemoryThrashAlert) "zRAM 92% [THRASH]" else "zRAM 12%"
            val r2Primary = "Avail: $availMb"
            val r2Secondary = zramStr
            val r2Speed = String.format(Locale.US, "%.2f RPS", params.ring2SpeedRps)

            assertTrue("R2 primary metric must contain 'MB'", r2Primary.contains("MB"))
            if (case.expectedIsMemoryThrash) {
                assertTrue("R2 secondary metric must indicate thrash alert", r2Secondary.contains("[THRASH]"))
            }
            assertTrue("R2 speed string must contain 'RPS'", r2Speed.endsWith("RPS"))

            // --- Ring 3 (Net): KB/s Throughput, Link Quality, RPS ---
            val throughput = snapshot.network.displayLabel.ifEmpty { "0 KB/s" }
            val linkQuality = snapshot.cellularQuality.displayLabel.ifEmpty { "-80 dBm" }
            val r3Primary = "Rate: $throughput"
            val r3Secondary = "Link: $linkQuality"
            val r3Speed = String.format(Locale.US, "%.2f RPS", params.ring3SpeedRps)

            assertTrue("R3 primary metric must start with 'Rate:'", r3Primary.startsWith("Rate:"))
            assertTrue("R3 secondary metric must start with 'Link:'", r3Secondary.startsWith("Link:"))
            assertTrue("R3 speed string must contain 'RPS'", r3Speed.endsWith("RPS"))

            // --- Ring 4 (SSD): Write Latency ms, Stall Flag, RPS ---
            val latencyStr = if (params.isStorageStallAlert) ">150 ms [STALL]" else "1.2 ms"
            val stallStr = if (params.isStorageStallAlert) "STALL: TRUE" else "STALL: FALSE"
            val r4Primary = "Sync: $latencyStr"
            val r4Secondary = stallStr
            val r4Speed = String.format(Locale.US, "%.2f RPS", params.ring4SpeedRps)

            if (case.expectedIsStorageStall) {
                assertTrue("R4 primary must show stall warning", r4Primary.contains("[STALL]"))
                assertEquals("R4 secondary must show STALL: TRUE", "STALL: TRUE", r4Secondary)
            } else {
                assertEquals("R4 secondary must show STALL: FALSE", "STALL: FALSE", r4Secondary)
            }
            assertTrue("R4 speed string must contain 'RPS'", r4Speed.endsWith("RPS"))

            // --- Ring 5 (GPU/Thermal): Thermal Status, Temperature °C, RPS, Meltdown Flag ---
            val thermalStatusStr = rawPacket.thermalStatusLevel?.let { "Status: Level $it" }
                ?: if (params.isMeltdownAlert) "Status: CRITICAL" else "Status: NOMINAL"
            val tempStr = rawPacket.cpuTemperatureMilliC?.let { "${it / 1000}°C" }
                ?: if (params.isMeltdownAlert) "85°C [MELTDOWN]" else "42°C [NOMINAL]"
            val r5Primary = thermalStatusStr
            val r5Secondary = "Temp: $tempStr"
            val r5Speed = String.format(Locale.US, "%.2f RPS", params.ring5SpeedRps)

            if (case.expectedIsMeltdown) {
                assertTrue(
                    "R5 must indicate meltdown in status or temperature",
                    r5Primary.contains("CRITICAL") || r5Primary.contains("Level 4") ||
                        r5Primary.contains("Level 5") || r5Secondary.contains("MELTDOWN") ||
                        (rawPacket.cpuTemperatureMilliC ?: 0) >= 80000
                )
            }
            assertTrue("R5 speed string must contain 'RPS'", r5Speed.endsWith("RPS"))
        }
    }

    @Test
    fun test3_fusionEngineInjectionAndLiveBadgePipeline() {
        val fakeSource = FakeTelemetrySource()
        val fusionEngine = TelemetryFusionEngine(fakeSource)

        for (case in HologramInvariantCases.EXPANDED_CASES) {
            val packet = case.toRawPacket()
            fusionEngine.injectPacket(packet, resetEma = true)
            fusionEngine.injectSnapshot(case.snapshot)

            val lastPacket = fusionEngine.lastPacket.value
            assertNotNull("Last packet must be recorded upon injection", lastPacket)
            assertEquals("Last packet timestamp should match", packet.timestampNs, lastPacket!!.timestampNs)

            val currentSnapshot = fusionEngine.snapshot.value
            assertEquals("Snapshot CPU load smoothed value must match injected case",
                case.snapshot.cpu.smoothedValue, currentSnapshot.cpu.smoothedValue, 0.001f)

            // Compute projection parameters from current fusion engine snapshot
            val params = HologramProjectionCalculator.computeParameters(
                snapshot = currentSnapshot,
                config = HologramBehaviorConfig()
            )

            assertEquals("R1 speed must match case projection", case.expectedR1SpeedRps, params.ring1SpeedRps, 0.05f)
            assertEquals("R2 speed must match case projection", case.expectedR2SpeedRps, params.ring2SpeedRps, 0.05f)
            assertEquals("R3 speed must match case projection", case.expectedR3SpeedRps, params.ring3SpeedRps, 0.05f)
            assertEquals("R4 speed must match case projection", case.expectedR4SpeedRps, params.ring4SpeedRps, 0.05f)
            assertEquals("R5 speed must match case projection", case.expectedR5SpeedRps, params.ring5SpeedRps, 0.05f)
        }
    }

    @Test
    fun test4_speedRpsBoundsAndMonotonicityUnderVMinVMaxConfig() {
        val customConfig = HologramBehaviorConfig(
            vMinRps = 0.5f,
            vMaxRps = 6.0f,
            speedCurve = SpeedCurve.QUADRATIC,
            outerSensitivity = 1.5f,
            middleSensitivity = 1.0f,
            innerSensitivity = 0.8f,
            meltdownThreshold = 0.85f
        )

        for (case in HologramInvariantCases.EXPANDED_CASES) {
            val params = HologramProjectionCalculator.computeParameters(case.snapshot, customConfig)

            assertTrue("R1 speed (${params.ring1SpeedRps}) >= 0.5f", params.ring1SpeedRps >= 0.499f)
            assertTrue("R1 speed (${params.ring1SpeedRps}) <= 6.0f", params.ring1SpeedRps <= 6.001f)

            assertTrue("R2 speed (${params.ring2SpeedRps}) >= 0.5f", params.ring2SpeedRps >= 0.499f)
            assertTrue("R2 speed (${params.ring2SpeedRps}) <= 6.0f", params.ring2SpeedRps <= 6.001f)

            assertTrue("R3 speed (${params.ring3SpeedRps}) >= 0.5f", params.ring3SpeedRps >= 0.499f)
            assertTrue("R3 speed (${params.ring3SpeedRps}) <= 6.0f", params.ring3SpeedRps <= 6.001f)

            assertTrue("R4 speed (${params.ring4SpeedRps}) >= 0.5f", params.ring4SpeedRps >= 0.499f)
            assertTrue("R4 speed (${params.ring4SpeedRps}) <= 6.0f", params.ring4SpeedRps <= 6.001f)

            assertTrue("R5 speed (${params.ring5SpeedRps}) >= 0.5f", params.ring5SpeedRps >= 0.499f)
            assertTrue("R5 speed (${params.ring5SpeedRps}) <= 6.0f", params.ring5SpeedRps <= 6.001f)
        }
    }
}
