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

/**
 * Adversarial test harness stress-testing alert gamuts, subsystem decoupling,
 * and status label precedence for Milestone M1.
 */
class AlertGamutAdversarialTest {

    private val plugin = HolographicRingsPlugin()

    // -------------------------------------------------------------
    // Requirement 1: Isolated RAM Thrashing -> MemoryThrashPurple (#BA68C8)
    // -------------------------------------------------------------

    @Test
    fun `isolated ram thrashing turns only middle ring purple without triggering meltdown`() {
        val snapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.20f, throttleState = ThrottleState.NOMINAL),
            ram = MetricValue(smoothedValue = 0.88f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            network = MetricValue(smoothedValue = 0.15f, throttleState = ThrottleState.NOMINAL),
            cellularQuality = MetricValue(smoothedValue = 0.90f, throttleState = ThrottleState.NOMINAL),
            worstThrottleState = ThrottleState.CRITICAL_THROTTLED
        )

        val params = HologramProjectionCalculator.computeParameters(snapshot)

        assertFalse("Meltdown must NOT be triggered by RAM thrashing", params.isMeltdownAlert)
        assertEquals("Outer ring must remain Nominal Cyan", NeonPalette.CyanCpu, params.outerColor)
        assertEquals("Middle ring must be MemoryThrashPurple", NeonPalette.MemoryThrashPurple, params.middleColor)
        assertEquals("MemoryThrashPurple token must be #BA68C8", Color(0xFFBA68C8), params.middleColor)
        assertEquals("Inner ring must remain Nominal Magenta", NeonPalette.MagentaGpuNet, params.innerColor)
        assertFalse("Status label must not contain CRITICAL MELTDOWN", params.systemStatusLabel.contains("CRITICAL MELTDOWN"))
    }

    @Test
    fun `ram thrashing with various ram loads keeps memory thrash purple and no meltdown`() {
        val loads = listOf(0.05f, 0.30f, 0.70f, 0.85f, 1.0f)

        for (load in loads) {
            val snapshot = SystemTelemetrySnapshot(
                cpu = MetricValue(smoothedValue = 0.25f, throttleState = ThrottleState.NOMINAL),
                ram = MetricValue(smoothedValue = load, throttleState = ThrottleState.CRITICAL_THROTTLED),
                network = MetricValue(smoothedValue = 0.20f, throttleState = ThrottleState.NOMINAL),
                cellularQuality = MetricValue(smoothedValue = 0.80f, throttleState = ThrottleState.NOMINAL),
                worstThrottleState = ThrottleState.CRITICAL_THROTTLED
            )

            val params = HologramProjectionCalculator.computeParameters(snapshot)
            assertFalse("Meltdown must not trigger at RAM load $load", params.isMeltdownAlert)
            assertEquals("Middle color must be MemoryThrashPurple at RAM load $load", NeonPalette.MemoryThrashPurple, params.middleColor)
            assertEquals("Outer color must remain Cyan at RAM load $load", NeonPalette.CyanCpu, params.outerColor)
            assertEquals("Inner color must remain Magenta at RAM load $load", NeonPalette.MagentaGpuNet, params.innerColor)
        }
    }

    @Test
    fun `high ram load without critical throttle does NOT produce purple middle ring`() {
        val snapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.20f, throttleState = ThrottleState.NOMINAL),
            ram = MetricValue(smoothedValue = 0.99f, throttleState = ThrottleState.NOMINAL),
            network = MetricValue(smoothedValue = 0.10f, throttleState = ThrottleState.NOMINAL),
            cellularQuality = MetricValue(smoothedValue = 0.80f, throttleState = ThrottleState.NOMINAL),
            worstThrottleState = ThrottleState.NOMINAL
        )

        val params = HologramProjectionCalculator.computeParameters(snapshot)
        assertFalse("No meltdown for high RAM", params.isMeltdownAlert)
        assertEquals("Middle color must remain OrangeRam when NOT throttled", NeonPalette.OrangeRam, params.middleColor)
    }

    @Test
    fun `ram warning boost does NOT produce purple middle ring`() {
        val snapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.20f, throttleState = ThrottleState.NOMINAL),
            ram = MetricValue(smoothedValue = 0.75f, throttleState = ThrottleState.WARNING_BOOST),
            network = MetricValue(smoothedValue = 0.10f, throttleState = ThrottleState.NOMINAL),
            cellularQuality = MetricValue(smoothedValue = 0.80f, throttleState = ThrottleState.NOMINAL),
            worstThrottleState = ThrottleState.WARNING_BOOST
        )

        val params = HologramProjectionCalculator.computeParameters(snapshot)
        assertFalse("No meltdown on RAM warning boost", params.isMeltdownAlert)
        assertEquals("Middle color must remain OrangeRam on WARNING_BOOST", NeonPalette.OrangeRam, params.middleColor)
    }

    // -------------------------------------------------------------
    // Requirement 2: Isolated Cellular Drop -> Inner Ring Red + "CELLULAR LINK LOST"
    // -------------------------------------------------------------

    @Test
    fun `isolated cellular drop turns only inner ring red and sets status to CELLULAR LINK LOST`() {
        val snapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.15f, throttleState = ThrottleState.NOMINAL),
            ram = MetricValue(smoothedValue = 0.25f, throttleState = ThrottleState.NOMINAL),
            network = MetricValue(smoothedValue = 0.05f, throttleState = ThrottleState.NOMINAL),
            cellularQuality = MetricValue(smoothedValue = 0.02f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            worstThrottleState = ThrottleState.CRITICAL_THROTTLED
        )

        val params = HologramProjectionCalculator.computeParameters(snapshot)

        assertFalse("Meltdown must NOT be triggered by cellular drop", params.isMeltdownAlert)
        assertEquals("Outer ring must remain Cyan", NeonPalette.CyanCpu, params.outerColor)
        assertEquals("Middle ring must remain Orange", NeonPalette.OrangeRam, params.middleColor)
        assertEquals("Inner ring must be alertMeltdownColor", NeonPalette.MeltdownRed, params.innerColor)
        assertEquals("Status label must be exact CELLULAR LINK LOST", "CELLULAR LINK LOST", params.systemStatusLabel)
    }

    @Test
    fun `cellular warning boost turns inner ring amber and sets status to CELLULAR SIGNAL DEGRADED`() {
        val snapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.15f, throttleState = ThrottleState.NOMINAL),
            ram = MetricValue(smoothedValue = 0.25f, throttleState = ThrottleState.NOMINAL),
            network = MetricValue(smoothedValue = 0.05f, throttleState = ThrottleState.NOMINAL),
            cellularQuality = MetricValue(smoothedValue = 0.35f, throttleState = ThrottleState.WARNING_BOOST),
            worstThrottleState = ThrottleState.WARNING_BOOST
        )

        val params = HologramProjectionCalculator.computeParameters(snapshot)

        assertFalse("Meltdown must NOT be triggered by cellular warning", params.isMeltdownAlert)
        assertEquals("Outer ring must remain Cyan", NeonPalette.CyanCpu, params.outerColor)
        assertEquals("Middle ring must remain Orange", NeonPalette.OrangeRam, params.middleColor)
        assertEquals("Inner ring must be alertAmberColor", NeonPalette.WarningAmber, params.innerColor)
        assertEquals("Status label must be CELLULAR SIGNAL DEGRADED", "CELLULAR SIGNAL DEGRADED", params.systemStatusLabel)
    }

    @Test
    fun `cellular link lost takes precedence over system boost threshold`() {
        // CPU and RAM are in boost range (>= 0.70), but cellular is critically degraded
        val snapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.75f, throttleState = ThrottleState.NOMINAL),
            ram = MetricValue(smoothedValue = 0.80f, throttleState = ThrottleState.NOMINAL),
            network = MetricValue(smoothedValue = 0.85f, throttleState = ThrottleState.NOMINAL),
            cellularQuality = MetricValue(smoothedValue = 0.01f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            worstThrottleState = ThrottleState.CRITICAL_THROTTLED
        )

        val params = HologramProjectionCalculator.computeParameters(snapshot)

        assertFalse("Meltdown must NOT be triggered when CPU is below meltdown threshold", params.isMeltdownAlert)
        assertEquals("CELLULAR LINK LOST must precede SYSTEM BOOST ACTIVE", "CELLULAR LINK LOST", params.systemStatusLabel)
        assertEquals("Inner ring must be MeltdownRed", NeonPalette.MeltdownRed, params.innerColor)
        assertEquals("Outer ring must remain Cyan", NeonPalette.CyanCpu, params.outerColor)
        assertEquals("Middle ring must remain Orange", NeonPalette.OrangeRam, params.middleColor)
    }

    @Test
    fun `cellular signal degraded takes precedence over system boost threshold`() {
        val snapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.75f, throttleState = ThrottleState.NOMINAL),
            ram = MetricValue(smoothedValue = 0.75f, throttleState = ThrottleState.NOMINAL),
            network = MetricValue(smoothedValue = 0.50f, throttleState = ThrottleState.NOMINAL),
            cellularQuality = MetricValue(smoothedValue = 0.30f, throttleState = ThrottleState.WARNING_BOOST),
            worstThrottleState = ThrottleState.WARNING_BOOST
        )

        val params = HologramProjectionCalculator.computeParameters(snapshot)

        assertFalse("Meltdown must NOT be triggered", params.isMeltdownAlert)
        assertEquals("CELLULAR SIGNAL DEGRADED must precede SYSTEM BOOST ACTIVE", "CELLULAR SIGNAL DEGRADED", params.systemStatusLabel)
        assertEquals("Inner ring must be WarningAmber", NeonPalette.WarningAmber, params.innerColor)
    }

    // -------------------------------------------------------------
    // Requirement 3: CPU Meltdown -> ALL 3 Rings Red + Status CRITICAL MELTDOWN
    // -------------------------------------------------------------

    @Test
    fun `cpu critical throttle turns ALL 3 rings red and sets status to CRITICAL MELTDOWN`() {
        val snapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.35f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            ram = MetricValue(smoothedValue = 0.20f, throttleState = ThrottleState.NOMINAL),
            network = MetricValue(smoothedValue = 0.10f, throttleState = ThrottleState.NOMINAL),
            cellularQuality = MetricValue(smoothedValue = 0.90f, throttleState = ThrottleState.NOMINAL),
            worstThrottleState = ThrottleState.CRITICAL_THROTTLED
        )

        val params = HologramProjectionCalculator.computeParameters(snapshot)

        assertTrue("Meltdown alert must be true", params.isMeltdownAlert)
        assertEquals("Outer ring must be MeltdownRed", NeonPalette.MeltdownRed, params.outerColor)
        assertEquals("Middle ring must be MeltdownRed", NeonPalette.MeltdownRed, params.middleColor)
        assertEquals("Inner ring must be MeltdownRed", NeonPalette.MeltdownRed, params.innerColor)
        assertEquals("Status must be SYSTEM PEAK LOAD [CRITICAL MELTDOWN]", "SYSTEM PEAK LOAD [CRITICAL MELTDOWN]", params.systemStatusLabel)
        assertEquals("Energy label must be MAX EXCEEDED", "ENERGY OUTPUT: MAX EXCEEDED", params.energyOutputLabel)
        assertEquals("Bloom intensity must be boosted 1.5x", 1.5f, params.bloomIntensity, 0.001f)
    }

    @Test
    fun `cpu load at or above meltdown threshold triggers meltdown even with nominal throttle`() {
        val loads = listOf(0.90f, 0.95f, 1.00f, 1.20f)

        for (cpuLoad in loads) {
            val snapshot = SystemTelemetrySnapshot(
                cpu = MetricValue(smoothedValue = cpuLoad, throttleState = ThrottleState.NOMINAL),
                ram = MetricValue(smoothedValue = 0.30f, throttleState = ThrottleState.NOMINAL),
                network = MetricValue(smoothedValue = 0.10f, throttleState = ThrottleState.NOMINAL),
                cellularQuality = MetricValue(smoothedValue = 0.90f, throttleState = ThrottleState.NOMINAL),
                worstThrottleState = ThrottleState.NOMINAL
            )

            val params = HologramProjectionCalculator.computeParameters(snapshot)

            assertTrue("Meltdown must trigger at CPU load $cpuLoad", params.isMeltdownAlert)
            assertEquals("Outer ring must be red at CPU load $cpuLoad", NeonPalette.MeltdownRed, params.outerColor)
            assertEquals("Middle ring must be red at CPU load $cpuLoad", NeonPalette.MeltdownRed, params.middleColor)
            assertEquals("Inner ring must be red at CPU load $cpuLoad", NeonPalette.MeltdownRed, params.innerColor)
            assertEquals("Status must be CRITICAL MELTDOWN at CPU load $cpuLoad", "SYSTEM PEAK LOAD [CRITICAL MELTDOWN]", params.systemStatusLabel)
        }
    }

    @Test
    fun `cpu meltdown takes absolute precedence over ram thrashing and cellular link loss`() {
        // Triple crisis: CPU meltdown, RAM thrashing, Cellular lost
        val snapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.95f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            ram = MetricValue(smoothedValue = 0.92f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            network = MetricValue(smoothedValue = 0.85f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            cellularQuality = MetricValue(smoothedValue = 0.01f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            worstThrottleState = ThrottleState.CRITICAL_THROTTLED
        )

        val params = HologramProjectionCalculator.computeParameters(snapshot)

        assertTrue("Meltdown must be active", params.isMeltdownAlert)
        assertEquals("Status must be CRITICAL MELTDOWN overriding cellular lost", "SYSTEM PEAK LOAD [CRITICAL MELTDOWN]", params.systemStatusLabel)
        assertEquals("Outer ring must be MeltdownRed", NeonPalette.MeltdownRed, params.outerColor)
        assertEquals("Middle ring must be MeltdownRed (not purple)", NeonPalette.MeltdownRed, params.middleColor)
        assertEquals("Inner ring must be MeltdownRed", NeonPalette.MeltdownRed, params.innerColor)
    }

    // -------------------------------------------------------------
    // Subsystem Decoupling & Multi-Subsystem Permutations
    // -------------------------------------------------------------

    @Test
    fun `concurrent ram thrashing and cellular drop without cpu meltdown are properly decoupled`() {
        // RAM thrashing AND cellular lost, but CPU is nominal
        val snapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.25f, throttleState = ThrottleState.NOMINAL),
            ram = MetricValue(smoothedValue = 0.85f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            network = MetricValue(smoothedValue = 0.10f, throttleState = ThrottleState.NOMINAL),
            cellularQuality = MetricValue(smoothedValue = 0.01f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            worstThrottleState = ThrottleState.CRITICAL_THROTTLED
        )

        val params = HologramProjectionCalculator.computeParameters(snapshot)

        assertFalse("Meltdown must NOT trigger without CPU crisis", params.isMeltdownAlert)
        assertEquals("Outer ring remains nominal Cyan", NeonPalette.CyanCpu, params.outerColor)
        assertEquals("Middle ring gets MemoryThrashPurple", NeonPalette.MemoryThrashPurple, params.middleColor)
        assertEquals("Inner ring gets MeltdownRed for cellular link loss", NeonPalette.MeltdownRed, params.innerColor)
        assertEquals("Status label is CELLULAR LINK LOST", "CELLULAR LINK LOST", params.systemStatusLabel)
    }

    @Test
    fun `boundary test at meltdown threshold 0_90`() {
        val belowSnapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.899f, throttleState = ThrottleState.NOMINAL),
            ram = MetricValue(smoothedValue = 0.20f, throttleState = ThrottleState.NOMINAL)
        )
        val belowParams = HologramProjectionCalculator.computeParameters(belowSnapshot)
        assertFalse("0.899 CPU load must NOT trigger meltdown", belowParams.isMeltdownAlert)
        assertEquals("Outer color must be Cyan", NeonPalette.CyanCpu, belowParams.outerColor)
        assertEquals("Status must be SYSTEM BOOST ACTIVE", "SYSTEM BOOST ACTIVE", belowParams.systemStatusLabel)

        val atSnapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.900f, throttleState = ThrottleState.NOMINAL),
            ram = MetricValue(smoothedValue = 0.20f, throttleState = ThrottleState.NOMINAL)
        )
        val atParams = HologramProjectionCalculator.computeParameters(atSnapshot)
        assertTrue("0.900 CPU load MUST trigger meltdown", atParams.isMeltdownAlert)
        assertEquals("Outer color must be MeltdownRed", NeonPalette.MeltdownRed, atParams.outerColor)
        assertEquals("Status must be CRITICAL MELTDOWN", "SYSTEM PEAK LOAD [CRITICAL MELTDOWN]", atParams.systemStatusLabel)
    }

    @Test
    fun `boundary test at boost threshold 0_70`() {
        val belowSnapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.699f),
            ram = MetricValue(smoothedValue = 0.699f),
            network = MetricValue(smoothedValue = 0.699f)
        )
        val belowParams = HologramProjectionCalculator.computeParameters(belowSnapshot)
        assertTrue("Status must be NOMINAL below 0.70", belowParams.systemStatusLabel.startsWith("SYSTEM NOMINAL"))

        val cpuBoostSnapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.700f),
            ram = MetricValue(smoothedValue = 0.20f),
            network = MetricValue(smoothedValue = 0.20f)
        )
        val cpuBoostParams = HologramProjectionCalculator.computeParameters(cpuBoostSnapshot)
        assertEquals("SYSTEM BOOST ACTIVE", cpuBoostParams.systemStatusLabel)

        val ramBoostSnapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.20f),
            ram = MetricValue(smoothedValue = 0.700f),
            network = MetricValue(smoothedValue = 0.20f)
        )
        val ramBoostParams = HologramProjectionCalculator.computeParameters(ramBoostSnapshot)
        assertEquals("SYSTEM BOOST ACTIVE", ramBoostParams.systemStatusLabel)

        val netBoostSnapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.20f),
            ram = MetricValue(smoothedValue = 0.20f),
            network = MetricValue(smoothedValue = 0.700f)
        )
        val netBoostParams = HologramProjectionCalculator.computeParameters(netBoostSnapshot)
        assertEquals("SYSTEM BOOST ACTIVE", netBoostParams.systemStatusLabel)
    }

    // -------------------------------------------------------------
    // Custom Configuration & SPI ParameterMapper Overrides
    // -------------------------------------------------------------

    @Test
    fun `custom config overrides thresholds and colors properly`() {
        val customColorMeltdown = Color(0xFFFF0055)
        val customColorPurple = Color(0xFF9C27B0)
        val customColorAmber = Color(0xFFFF9800)

        val customConfig = HologramBehaviorConfig(
            meltdownThreshold = 0.80f,
            boostThreshold = 0.50f,
            alertMeltdownColor = customColorMeltdown,
            alertThrashPurpleColor = customColorPurple,
            alertAmberColor = customColorAmber
        )

        // Meltdown at 0.82 with custom threshold 0.80
        val snapshotMeltdown = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.82f, throttleState = ThrottleState.NOMINAL)
        )
        val paramsMeltdown = HologramProjectionCalculator.computeParameters(snapshotMeltdown, customConfig)
        assertTrue("Meltdown should trigger at 0.82 when threshold is 0.80", paramsMeltdown.isMeltdownAlert)
        assertEquals(customColorMeltdown, paramsMeltdown.outerColor)
        assertEquals(customColorMeltdown, paramsMeltdown.middleColor)
        assertEquals(customColorMeltdown, paramsMeltdown.innerColor)

        // RAM thrash with custom purple
        val snapshotThrash = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.20f, throttleState = ThrottleState.NOMINAL),
            ram = MetricValue(smoothedValue = 0.60f, throttleState = ThrottleState.CRITICAL_THROTTLED)
        )
        val paramsThrash = HologramProjectionCalculator.computeParameters(snapshotThrash, customConfig)
        assertEquals(customColorPurple, paramsThrash.middleColor)

        // Cellular warning with custom amber
        val snapshotCell = SystemTelemetrySnapshot(
            cellularQuality = MetricValue(smoothedValue = 0.30f, throttleState = ThrottleState.WARNING_BOOST)
        )
        val paramsCell = HologramProjectionCalculator.computeParameters(snapshotCell, customConfig)
        assertEquals(customColorAmber, paramsCell.innerColor)
    }

    @Test
    fun `plugin parameterMapper delegates correctly through SPI`() {
        val snapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.20f, throttleState = ThrottleState.NOMINAL),
            ram = MetricValue(smoothedValue = 0.80f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            cellularQuality = MetricValue(smoothedValue = 0.05f, throttleState = ThrottleState.CRITICAL_THROTTLED),
            worstThrottleState = ThrottleState.CRITICAL_THROTTLED
        )

        val params = plugin.parameterMapper.map(snapshot, emptyMap())
        assertFalse(params.isMeltdownAlert)
        assertEquals(NeonPalette.CyanCpu, params.outerColor)
        assertEquals(NeonPalette.MemoryThrashPurple, params.middleColor)
        assertEquals(NeonPalette.MeltdownRed, params.innerColor)
        assertEquals("CELLULAR LINK LOST", params.systemStatusLabel)
    }
}
