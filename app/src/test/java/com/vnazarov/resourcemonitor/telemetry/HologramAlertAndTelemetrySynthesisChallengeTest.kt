package com.vnazarov.resourcemonitor.telemetry

import androidx.compose.ui.graphics.Color
import com.vnazarov.resourcemonitor.animations.hologram.HologramBehaviorConfig
import com.vnazarov.resourcemonitor.animations.hologram.HologramInvariantCases
import com.vnazarov.resourcemonitor.animations.hologram.HologramProjectionCalculator
import com.vnazarov.resourcemonitor.core.designsystem.theme.NeonPalette
import com.vnazarov.resourcemonitor.core.model.MetricValue
import com.vnazarov.resourcemonitor.core.model.RawTelemetryPacket
import com.vnazarov.resourcemonitor.core.model.SystemTelemetrySnapshot
import com.vnazarov.resourcemonitor.core.model.ThrottleState
import com.vnazarov.resourcemonitor.core.telemetry.fusion.TelemetryFusionEngine
import com.vnazarov.resourcemonitor.core.telemetry.fusion.TelemetryNormalizer
import com.vnazarov.resourcemonitor.core.telemetry.fusion.ThrottlingClassifier
import com.vnazarov.resourcemonitor.core.telemetry.mock.FakeTelemetrySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Challenger 2 Empirical Test Harness for Milestone M2:
 * 1. Challenge color assertions across all 30 cases (Nominal Cyan, Orange, Magenta; Meltdown Crimson #FF1744).
 * 2. Challenge meltdown states: ensure meltdown boolean is true STRICTLY on Cases 7, 15, 20, 25, 27, 30.
 * 3. Memory Thrashing Purple (#BA68C8) specifically verified on Case 28.
 * 4. Cellular Alert Amber (#FFAB00) on Warning state verified.
 * 5. Telemetry synthesis & toRawPacket() fidelity across all 30 cases ingested into TelemetryNormalizer,
 *    ThrottlingClassifier, and TelemetryFusionEngine pipeline.
 * 6. Alert gamut precedence and multi-subsystem simultaneous stress states.
 */
class HologramAlertAndTelemetrySynthesisChallengeTest {

    private val meltdownIds = setOf(7, 15, 20, 25, 27, 30)

    // ---------------------------------------------------------------------------------------------
    // Challenge 1: Meltdown State Invariant: True Strictly on Cases 7, 15, 20, 25, 27, 30
    // ---------------------------------------------------------------------------------------------

    @Test
    fun challenge1_meltdownBooleanIsTrueStrictlyAndOnlyOnCases_7_15_20_25_27_30() {
        val allCases = HologramInvariantCases.ALL_CASES
        assertEquals("Invariant case count must be exactly 30", 30, allCases.size)

        val observedMeltdownIds = mutableListOf<Int>()
        val observedNominalIds = mutableListOf<Int>()

        for (case in allCases) {
            val params = HologramProjectionCalculator.computeParameters(case.snapshot)

            // Verify calculator matches case assertion
            assertEquals(
                "Case #${case.id} [${case.name}] expectedIsMeltdown mismatch with calculator",
                case.expectedIsMeltdown,
                params.isMeltdownAlert
            )

            if (params.isMeltdownAlert) {
                observedMeltdownIds.add(case.id)
            } else {
                observedNominalIds.add(case.id)
            }
        }

        // Assert strictly the required 6 cases
        assertEquals("Meltdown cases must match exactly {7, 15, 20, 25, 27, 30}", meltdownIds.toList().sorted(), observedMeltdownIds.sorted())
        assertEquals("Meltdown count must be exactly 6", 6, observedMeltdownIds.size)
        assertEquals("Nominal/Non-meltdown count must be exactly 24", 24, observedNominalIds.size)

        // Ensure no other case triggered meltdown
        for (id in observedNominalIds) {
            assertFalse("Case #$id must NOT be a meltdown case", meltdownIds.contains(id))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Challenge 2: Color Assertions Across All 30 Cases: Nominal & Meltdown Gamuts
    // ---------------------------------------------------------------------------------------------

    @Test
    fun challenge2_colorGamutsAcrossAll30Cases_nominalVsMeltdownStrictValidation() {
        val cyanCpu = Color(0xFF00FFFF)
        val orangeRam = Color(0xFFFF8C00)
        val magentaNet = Color(0xFFFF00FF)
        val meltdownCrimson = Color(0xFFFF1744)

        // Verify NeonPalette constants match specification hex codes
        assertEquals(cyanCpu, NeonPalette.CyanCpu)
        assertEquals(orangeRam, NeonPalette.OrangeRam)
        assertEquals(magentaNet, NeonPalette.MagentaGpuNet)
        assertEquals(meltdownCrimson, NeonPalette.MeltdownRed)

        for (case in HologramInvariantCases.ALL_CASES) {
            val params = HologramProjectionCalculator.computeParameters(case.snapshot)

            if (meltdownIds.contains(case.id)) {
                // Meltdown cases MUST turn ALL THREE rings into Meltdown Crimson #FF1744
                assertEquals("Meltdown Case #${case.id} outer ring MUST be Meltdown Crimson #FF1744", meltdownCrimson, params.outerColor)
                assertEquals("Meltdown Case #${case.id} middle ring MUST be Meltdown Crimson #FF1744", meltdownCrimson, params.middleColor)
                assertEquals("Meltdown Case #${case.id} inner ring MUST be Meltdown Crimson #FF1744", meltdownCrimson, params.innerColor)
                assertEquals("Meltdown Case #${case.id} expectedOuterColor in case model", meltdownCrimson, case.expectedOuterColor)
                assertEquals("Meltdown Case #${case.id} expectedMiddleColor in case model", meltdownCrimson, case.expectedMiddleColor)
                assertEquals("Meltdown Case #${case.id} expectedInnerColor in case model", meltdownCrimson, case.expectedInnerColor)
            } else {
                // Non-meltdown cases: outer ring MUST ALWAYS be Cyan
                assertEquals("Non-meltdown Case #${case.id} outer ring MUST be Cyan", cyanCpu, params.outerColor)
                assertEquals("Non-meltdown Case #${case.id} outer color matches case model", case.expectedOuterColor, params.outerColor)

                // Middle ring: MemoryThrashPurple for Case 28, otherwise Orange
                if (case.id == 28) {
                    assertEquals("Case 28 middle ring MUST be MemoryThrashPurple", Color(0xFFBA68C8), params.middleColor)
                } else {
                    assertEquals("Case #${case.id} middle ring MUST be Orange", orangeRam, params.middleColor)
                }

                // Inner ring: MeltdownRed for RF degraded cases (21, 22, 23), otherwise Magenta
                if (case.id in 21..23) {
                    assertEquals("Cellular Alert Case #${case.id} inner ring MUST be MeltdownRed", meltdownCrimson, params.innerColor)
                } else {
                    assertEquals("Case #${case.id} inner ring MUST be Magenta", magentaNet, params.innerColor)
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Challenge 3: Memory Thrashing Purple (#BA68C8) on Case 28 & Subsystem Decoupling
    // ---------------------------------------------------------------------------------------------

    @Test
    fun challenge3_case28_zRamMemoryThrashingPurpleValidation() {
        val thrashPurple = Color(0xFFBA68C8)
        assertEquals("NeonPalette token must match #BA68C8", thrashPurple, NeonPalette.MemoryThrashPurple)

        val case28 = HologramInvariantCases.getById(28)
            ?: throw AssertionError("Case 28 not found in ALL_CASES")

        val params = HologramProjectionCalculator.computeParameters(case28.snapshot)

        // Assert exact color gamut on Case 28
        assertEquals("Case 28 outer ring MUST remain Cyan", NeonPalette.CyanCpu, params.outerColor)
        assertEquals("Case 28 middle ring MUST be MemoryThrashPurple #BA68C8", thrashPurple, params.middleColor)
        assertEquals("Case 28 inner ring MUST remain Magenta", NeonPalette.MagentaGpuNet, params.innerColor)

        // Decoupled subsystem: RAM thrashing must NOT cause meltdown or set meltdown alert
        assertFalse("Case 28 MUST NOT be marked as meltdown", params.isMeltdownAlert)
        assertFalse("Case 28 expectedIsMeltdown MUST be false", case28.expectedIsMeltdown)
        assertFalse("Case 28 status must NOT contain CRITICAL MELTDOWN", params.systemStatusLabel.contains("CRITICAL MELTDOWN"))
        assertTrue("Case 28 status must contain SYSTEM BOOST ACTIVE", params.systemStatusLabel.contains("SYSTEM BOOST ACTIVE"))
    }

    // ---------------------------------------------------------------------------------------------
    // Challenge 4: Cellular Alert Amber (#FFAB00) on Warning State & Status Precedence
    // ---------------------------------------------------------------------------------------------

    @Test
    fun challenge4_cellularAlertAmber_warningStateValidation() {
        val alertAmber = Color(0xFFFFAB00)
        assertEquals("NeonPalette.WarningAmber must match #FFAB00", alertAmber, NeonPalette.WarningAmber)

        // Build synthetic snapshot with cellular warning boost (e.g. RSRP -105 dBm)
        val warningCellSnapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.25f, throttleState = ThrottleState.NOMINAL),
            ram = MetricValue(smoothedValue = 0.30f, throttleState = ThrottleState.NOMINAL),
            network = MetricValue(smoothedValue = 0.10f, throttleState = ThrottleState.NOMINAL),
            cellularQuality = MetricValue(
                smoothedValue = 0.45f,
                throttleState = ThrottleState.WARNING_BOOST,
                displayLabel = "-105 dBm"
            ),
            worstThrottleState = ThrottleState.WARNING_BOOST
        )

        val params = HologramProjectionCalculator.computeParameters(warningCellSnapshot)

        // Assert colors under cellular warning
        assertEquals("Outer ring MUST remain Cyan", NeonPalette.CyanCpu, params.outerColor)
        assertEquals("Middle ring MUST remain Orange", NeonPalette.OrangeRam, params.middleColor)
        assertEquals("Inner ring MUST be Warning Amber #FFAB00", alertAmber, params.innerColor)
        assertFalse("Cellular warning MUST NOT trigger meltdown", params.isMeltdownAlert)
        assertEquals("Status label MUST be CELLULAR SIGNAL DEGRADED", "CELLULAR SIGNAL DEGRADED", params.systemStatusLabel)

        // Challenge customizable alertAmberColor
        val customAmberConfig = HologramBehaviorConfig(alertAmberColor = Color(0xFFFFD600))
        val customParams = HologramProjectionCalculator.computeParameters(warningCellSnapshot, customAmberConfig)
        assertEquals("Inner ring MUST reflect customized amber color", Color(0xFFFFD600), customParams.innerColor)
    }

    // ---------------------------------------------------------------------------------------------
    // Challenge 5: Multi-Subsystem Alert Precedence & Decoupling Matrix
    // ---------------------------------------------------------------------------------------------

    @Test
    fun challenge5_multiSubsystemAlertPrecedenceMatrix() {
        // Scenario A: Simultaneous RAM Thrashing + Cellular Warning (No CPU Meltdown)
        val snapshotA = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.20f, throttleState = ThrottleState.NOMINAL),
            ram = MetricValue(smoothedValue = 0.88f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            network = MetricValue(smoothedValue = 0.10f, throttleState = ThrottleState.NOMINAL),
            cellularQuality = MetricValue(smoothedValue = 0.40f, throttleState = ThrottleState.WARNING_BOOST)
        )
        val paramsA = HologramProjectionCalculator.computeParameters(snapshotA)
        assertEquals("Outer remains Cyan", NeonPalette.CyanCpu, paramsA.outerColor)
        assertEquals("Middle is MemoryThrashPurple", NeonPalette.MemoryThrashPurple, paramsA.middleColor)
        assertEquals("Inner is WarningAmber", NeonPalette.WarningAmber, paramsA.innerColor)
        assertFalse("Subsystems decoupled: no meltdown", paramsA.isMeltdownAlert)

        // Scenario B: Simultaneous RAM Thrashing + Cellular Critical Lost (No CPU Meltdown)
        val snapshotB = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.20f, throttleState = ThrottleState.NOMINAL),
            ram = MetricValue(smoothedValue = 0.90f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            network = MetricValue(smoothedValue = 0.10f, throttleState = ThrottleState.NOMINAL),
            cellularQuality = MetricValue(smoothedValue = 0.05f, throttleState = ThrottleState.CRITICAL_THROTTLED)
        )
        val paramsB = HologramProjectionCalculator.computeParameters(snapshotB)
        assertEquals("Outer remains Cyan", NeonPalette.CyanCpu, paramsB.outerColor)
        assertEquals("Middle is MemoryThrashPurple", NeonPalette.MemoryThrashPurple, paramsB.middleColor)
        assertEquals("Inner is MeltdownRed (RF lost)", NeonPalette.MeltdownRed, paramsB.innerColor)
        assertFalse("Subsystems decoupled: no meltdown", paramsB.isMeltdownAlert)
        assertEquals("Cellular Lost takes status precedence over boost", "CELLULAR LINK LOST", paramsB.systemStatusLabel)

        // Scenario C: CPU Meltdown overrides RAM Thrashing AND Cellular Alerts
        val snapshotC = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.95f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            ram = MetricValue(smoothedValue = 0.95f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            network = MetricValue(smoothedValue = 0.80f, throttleState = ThrottleState.NOMINAL),
            cellularQuality = MetricValue(smoothedValue = 0.05f, throttleState = ThrottleState.CRITICAL_THROTTLED)
        )
        val paramsC = HologramProjectionCalculator.computeParameters(snapshotC)
        assertTrue("CPU Meltdown is active", paramsC.isMeltdownAlert)
        assertEquals("CPU Meltdown overrides outer to MeltdownRed", NeonPalette.MeltdownRed, paramsC.outerColor)
        assertEquals("CPU Meltdown overrides middle to MeltdownRed", NeonPalette.MeltdownRed, paramsC.middleColor)
        assertEquals("CPU Meltdown overrides inner to MeltdownRed", NeonPalette.MeltdownRed, paramsC.innerColor)
        assertTrue("Meltdown overrides status label", paramsC.systemStatusLabel.contains("CRITICAL MELTDOWN"))
    }

    // ---------------------------------------------------------------------------------------------
    // Challenge 6: toRawPacket() Fidelity & Telemetry Pipeline Ingestion Across All 30 Cases
    // ---------------------------------------------------------------------------------------------

    @Test
    fun challenge6_toRawPacketFidelityAndPipelineIngestionAll30Cases() {
        val fakeSource = FakeTelemetrySource()
        val fusionEngine = TelemetryFusionEngine(fakeSource)

        for (case in HologramInvariantCases.ALL_CASES) {
            val packet = case.toRawPacket()
            assertNotNull("Case #${case.id} packet must not be null", packet)

            // 1. Structural packet assertions
            assertTrue("Case #${case.id} timestamp must be positive", packet.timestampNs > 0L)
            assertTrue("Case #${case.id} max frequency must be positive", packet.cpuMaxFrequencyKhz > 0L)
            assertTrue("Case #${case.id} cpu frequencies must not be empty", packet.cpuFrequenciesKhz.isNotEmpty())
            assertTrue("Case #${case.id} total RAM must be positive", packet.ramTotalBytes > 0L)
            assertTrue("Case #${case.id} available RAM <= total RAM", packet.ramAvailableBytes <= packet.ramTotalBytes)
            assertTrue("Case #${case.id} available RAM >= 0", packet.ramAvailableBytes >= 0L)
            assertTrue("Case #${case.id} rxBytes >= 0", packet.rxBytesPerSec >= 0L)

            // 2. Ingest into TelemetryNormalizer without exceptions or NaNs
            val normCpu = TelemetryNormalizer.normalizeCpu(packet)
            val normRam = TelemetryNormalizer.normalizeRam(packet)
            val normNet = TelemetryNormalizer.normalizeNetwork(packet)
            val normCell = TelemetryNormalizer.normalizeCellularQuality(packet)

            assertFalse("Case #${case.id} normCpu must not be NaN", normCpu.isNaN())
            assertFalse("Case #${case.id} normRam must not be NaN", normRam.isNaN())
            assertFalse("Case #${case.id} normNet must not be NaN", normNet.isNaN())
            assertFalse("Case #${case.id} normCell must not be NaN", normCell.isNaN())

            assertTrue("Case #${case.id} normCpu in [0, 1]: $normCpu", normCpu in 0f..1f)
            assertTrue("Case #${case.id} normRam in [0, 1]: $normRam", normRam in 0f..1f)
            assertTrue("Case #${case.id} normNet in [0, 1]: $normNet", normNet in 0f..1f)
            assertTrue("Case #${case.id} normCell in [0, 1]: $normCell", normCell in 0f..1f)

            // 3. Fidelity of CPU & RAM loads (cases 1..28)
            if (case.id in 1..28) {
                assertEquals(
                    "Case #${case.id} CPU load normalized matches snapshot",
                    case.snapshot.cpu.smoothedValue,
                    normCpu,
                    0.01f
                )
                assertEquals(
                    "Case #${case.id} RAM load normalized matches snapshot",
                    case.snapshot.ram.smoothedValue,
                    normRam,
                    0.01f
                )
            }

            // 4. Ingest into ThrottlingClassifier
            val cpuThrottle = ThrottlingClassifier.classifyCpu(packet)
            val ramThrottle = ThrottlingClassifier.classifyRam(packet)
            val cellThrottle = ThrottlingClassifier.classifyCellular(packet)

            assertNotNull("Case #${case.id} cpuThrottle non-null", cpuThrottle)
            assertNotNull("Case #${case.id} ramThrottle non-null", ramThrottle)
            assertNotNull("Case #${case.id} cellThrottle non-null", cellThrottle)

            // 5. Ingest into TelemetryFusionEngine.processPacket()
            val processedSnapshot = fusionEngine.processPacket(packet)
            assertNotNull("Case #${case.id} processed snapshot non-null", processedSnapshot)
            assertTrue("Case #${case.id} processed timestamp valid", processedSnapshot.timestampMs > 0L)
            assertNotNull("Case #${case.id} processed cpu metric", processedSnapshot.cpu)
            assertNotNull("Case #${case.id} processed ram metric", processedSnapshot.ram)
            assertNotNull("Case #${case.id} processed net metric", processedSnapshot.network)
            assertNotNull("Case #${case.id} processed cellular metric", processedSnapshot.cellularQuality)
        }

        // Specific Marker Verification:
        // Case 28: zRAM Memory Thrashing
        val packet28 = HologramInvariantCases.getById(28)!!.toRawPacket()
        assertEquals("Case 28 zRAM swap bytes", 3_000_000_000L, packet28.zRamUsedBytes)
        assertEquals("Case 28 compact stalls count", 450L, packet28.compactStallsCount)
        assertEquals("Case 28 classifyRam", ThrottleState.CRITICAL_THROTTLED, ThrottlingClassifier.classifyRam(packet28))

        // Case 25: Thermal Clock-Capping
        val packet25 = HologramInvariantCases.getById(25)!!.toRawPacket()
        assertEquals("Case 25 CPU frequency", 394_000L, packet25.cpuFrequenciesKhz.first())
        assertEquals("Case 25 CPU classify", ThrottleState.CRITICAL_THROTTLED, ThrottlingClassifier.classifyCpu(packet25))

        // Cases 21..23: Cellular Loss
        val packet21 = HologramInvariantCases.getById(21)!!.toRawPacket()
        val packet22 = HologramInvariantCases.getById(22)!!.toRawPacket()
        val packet23 = HologramInvariantCases.getById(23)!!.toRawPacket()
        assertEquals("Case 21 RSRP", -125, packet21.rsrpDbm)
        assertEquals("Case 22 RSRP", -116, packet22.rsrpDbm)
        assertEquals("Case 23 RSRP", -140, packet23.rsrpDbm)
        assertEquals("Case 21 classifyCellular", ThrottleState.CRITICAL_THROTTLED, ThrottlingClassifier.classifyCellular(packet21))
        assertEquals("Case 22 classifyCellular", ThrottleState.CRITICAL_THROTTLED, ThrottlingClassifier.classifyCellular(packet22))
        assertEquals("Case 23 classifyCellular", ThrottleState.CRITICAL_THROTTLED, ThrottlingClassifier.classifyCellular(packet23))
    }
}
