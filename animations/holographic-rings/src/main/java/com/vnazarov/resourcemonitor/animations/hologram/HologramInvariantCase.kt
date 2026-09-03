package com.vnazarov.resourcemonitor.animations.hologram

import androidx.compose.ui.graphics.Color
import com.vnazarov.resourcemonitor.core.designsystem.theme.NeonPalette
import com.vnazarov.resourcemonitor.core.model.MetricValue
import com.vnazarov.resourcemonitor.core.model.RawTelemetryPacket
import com.vnazarov.resourcemonitor.core.model.SystemTelemetrySnapshot
import com.vnazarov.resourcemonitor.core.model.ThrottleState

data class HologramInvariantCase(
    val id: Int,
    val name: String,
    val group: String,
    val description: String,
    val snapshot: SystemTelemetrySnapshot,
    val expectedOuterSpeedRps: Float,
    val expectedMiddleSpeedRps: Float,
    val expectedInnerSpeedRps: Float,
    val expectedOuterColor: Color,
    val expectedMiddleColor: Color,
    val expectedInnerColor: Color,
    val expectedIsMeltdown: Boolean,
    val expectedStatusSubstring: String,
    val expectedEnergyLabel: String
) {
    fun toRawPacket(): RawTelemetryPacket {
        val cpuLoad = snapshot.cpu.smoothedValue
        val ramLoad = snapshot.ram.smoothedValue
        val netLoad = snapshot.network.smoothedValue

        val cpuLoadClamped = cpuLoad.coerceIn(0f, 1f)
        val ramLoadClamped = ramLoad.coerceIn(0f, 1f)
        val netLoadClamped = netLoad.coerceIn(0f, 1f)

        val maxFreq = 3_200_000L
        val cpuFreq = when (snapshot.cpu.throttleState) {
            ThrottleState.CRITICAL_THROTTLED -> 394_000L
            ThrottleState.WARNING_BOOST -> 2_900_000L
            ThrottleState.NOMINAL -> 1_800_000L
        }

        val totalRam = 16_000_000_000L
        val availableRam = (totalRam * (1f - ramLoadClamped)).toLong()
        val (zRam, compactStalls) = when (snapshot.ram.throttleState) {
            ThrottleState.CRITICAL_THROTTLED -> 3_000_000_000L to 450L
            else -> 0L to 0L
        }

        val rxBytes = if (netLoadClamped > 0f) {
            kotlin.math.exp(netLoadClamped * kotlin.math.ln(10_000_000.0)).toLong().coerceAtLeast(2048L)
        } else {
            0L
        }

        val rsrp = when (snapshot.cellularQuality.throttleState) {
            ThrottleState.CRITICAL_THROTTLED -> if (id == 21) -125 else if (id == 22) -116 else -140
            else -> if (id == 24) -70 else if (id == 2 || id == 13) -75 else -80
        }

        return RawTelemetryPacket(
            timestampNs = System.nanoTime(),
            cpuFrequenciesKhz = listOf(cpuFreq),
            cpuMaxFrequencyKhz = maxFreq,
            cpuLoadPercentage = cpuLoad * 100f,
            cpuTemperatureMilliC = if (snapshot.cpu.throttleState == ThrottleState.CRITICAL_THROTTLED) 55_000 else 40_000,
            ramTotalBytes = totalRam,
            ramAvailableBytes = availableRam,
            zRamUsedBytes = zRam,
            compactStallsCount = compactStalls,
            rxBytesPerSec = rxBytes,
            txBytesPerSec = 0L,
            rsrpDbm = rsrp,
            isWifiActive = rsrp >= -80
        )
    }
}

object HologramInvariantCases {

    private fun createSnapshot(
        cpuLoad: Float,
        ramLoad: Float,
        netLoad: Float,
        cellularQuality: Float = 1.0f,
        rsrpDbm: Int = -80,
        worstThrottle: ThrottleState = ThrottleState.NOMINAL,
        cpuThrottle: ThrottleState = ThrottleState.NOMINAL,
        ramThrottle: ThrottleState = ThrottleState.NOMINAL,
        cellThrottle: ThrottleState = ThrottleState.NOMINAL
    ): SystemTelemetrySnapshot {
        return SystemTelemetrySnapshot(
            timestampMs = 1_000L,
            cpu = MetricValue(
                rawNormalized = cpuLoad,
                smoothedValue = cpuLoad,
                throttleState = cpuThrottle,
                displayLabel = "CPU ${(cpuLoad * 100).toInt()}%"
            ),
            ram = MetricValue(
                rawNormalized = ramLoad,
                smoothedValue = ramLoad,
                throttleState = ramThrottle,
                displayLabel = "RAM ${(ramLoad * 100).toInt()}%"
            ),
            network = MetricValue(
                rawNormalized = netLoad,
                smoothedValue = netLoad,
                throttleState = ThrottleState.NOMINAL,
                displayLabel = "NET ${(netLoad * 100).toInt()}%"
            ),
            cellularQuality = MetricValue(
                rawNormalized = cellularQuality,
                smoothedValue = cellularQuality,
                throttleState = cellThrottle,
                displayLabel = "$rsrpDbm dBm"
            ),
            storageIo = MetricValue(),
            worstThrottleState = worstThrottle
        )
    }

    val ALL_CASES: List<HologramInvariantCase> = listOf(
        // Cases 1–5 (Baseline / Idle)
        HologramInvariantCase(
            id = 1,
            name = "Absolute Zero",
            group = "Baseline / Idle",
            description = "All telemetry loads at zero, ideal cellular signal",
            snapshot = createSnapshot(
                cpuLoad = 0.00f,
                ramLoad = 0.00f,
                netLoad = 0.00f,
                cellularQuality = 1.00f,
                rsrpDbm = -70,
                worstThrottle = ThrottleState.NOMINAL,
                cpuThrottle = ThrottleState.NOMINAL,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 0.200f,
            expectedMiddleSpeedRps = 0.200f,
            expectedInnerSpeedRps = 0.200f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MagentaGpuNet,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "SYSTEM NOMINAL",
            expectedEnergyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        HologramInvariantCase(
            id = 2,
            name = "Idle Calm",
            group = "Baseline / Idle",
            description = "Idle background load with 15% CPU, 25% RAM, 5% Net",
            snapshot = createSnapshot(
                cpuLoad = 0.15f,
                ramLoad = 0.25f,
                netLoad = 0.05f,
                cellularQuality = 1.00f,
                rsrpDbm = -75,
                worstThrottle = ThrottleState.NOMINAL,
                cpuThrottle = ThrottleState.NOMINAL,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 0.308f,
            expectedMiddleSpeedRps = 0.500f,
            expectedInnerSpeedRps = 0.212f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MagentaGpuNet,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "SYSTEM NOMINAL",
            expectedEnergyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        HologramInvariantCase(
            id = 3,
            name = "Zero Network",
            group = "Baseline / Idle",
            description = "Moderate CPU/RAM load at 50% with zero network traffic",
            snapshot = createSnapshot(
                cpuLoad = 0.50f,
                ramLoad = 0.50f,
                netLoad = 0.00f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.NOMINAL,
                cpuThrottle = ThrottleState.NOMINAL,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 1.400f,
            expectedMiddleSpeedRps = 1.400f,
            expectedInnerSpeedRps = 0.200f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MagentaGpuNet,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "SYSTEM NOMINAL",
            expectedEnergyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        HologramInvariantCase(
            id = 4,
            name = "Zero RAM Fallback",
            group = "Baseline / Idle",
            description = "Fallback state where RAM reports zero usage",
            snapshot = createSnapshot(
                cpuLoad = 0.30f,
                ramLoad = 0.00f,
                netLoad = 0.20f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.NOMINAL,
                cpuThrottle = ThrottleState.NOMINAL,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 0.632f,
            expectedMiddleSpeedRps = 0.200f,
            expectedInnerSpeedRps = 0.392f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MagentaGpuNet,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "SYSTEM NOMINAL",
            expectedEnergyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        HologramInvariantCase(
            id = 5,
            name = "Idle Boundary 29%",
            group = "Baseline / Idle",
            description = "Upper boundary for nominal baseline below 30%",
            snapshot = createSnapshot(
                cpuLoad = 0.29f,
                ramLoad = 0.29f,
                netLoad = 0.29f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.NOMINAL,
                cpuThrottle = ThrottleState.NOMINAL,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 0.604f,
            expectedMiddleSpeedRps = 0.604f,
            expectedInnerSpeedRps = 0.604f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MagentaGpuNet,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "SYSTEM NOMINAL",
            expectedEnergyLabel = "ENERGY OUTPUT: NOMINAL"
        ),

        // Cases 6–11 (Single-Metric Isolation)
        HologramInvariantCase(
            id = 6,
            name = "High CPU 80%",
            group = "Single-Metric Isolation",
            description = "Isolated high CPU load at 80%",
            snapshot = createSnapshot(
                cpuLoad = 0.80f,
                ramLoad = 0.00f,
                netLoad = 0.00f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.WARNING_BOOST,
                cpuThrottle = ThrottleState.WARNING_BOOST,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 3.272f,
            expectedMiddleSpeedRps = 0.200f,
            expectedInnerSpeedRps = 0.200f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MagentaGpuNet,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "SYSTEM BOOST ACTIVE",
            expectedEnergyLabel = "ENERGY OUTPUT: HIGH"
        ),
        HologramInvariantCase(
            id = 7,
            name = "Peak CPU Spike 100%",
            group = "Single-Metric Isolation",
            description = "Peak instantaneous CPU spike at 100%",
            snapshot = createSnapshot(
                cpuLoad = 1.00f,
                ramLoad = 0.00f,
                netLoad = 0.00f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.WARNING_BOOST,
                cpuThrottle = ThrottleState.WARNING_BOOST,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 5.000f,
            expectedMiddleSpeedRps = 0.200f,
            expectedInnerSpeedRps = 0.200f,
            expectedOuterColor = NeonPalette.MeltdownRed,
            expectedMiddleColor = NeonPalette.MeltdownRed,
            expectedInnerColor = NeonPalette.MeltdownRed,
            expectedIsMeltdown = true,
            expectedStatusSubstring = "CRITICAL MELTDOWN",
            expectedEnergyLabel = "ENERGY OUTPUT: MAX EXCEEDED"
        ),
        HologramInvariantCase(
            id = 8,
            name = "High RAM Only 80%",
            group = "Single-Metric Isolation",
            description = "Isolated high memory utilization at 80%",
            snapshot = createSnapshot(
                cpuLoad = 0.00f,
                ramLoad = 0.80f,
                netLoad = 0.00f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.WARNING_BOOST,
                cpuThrottle = ThrottleState.NOMINAL,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 0.200f,
            expectedMiddleSpeedRps = 3.272f,
            expectedInnerSpeedRps = 0.200f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MagentaGpuNet,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "SYSTEM BOOST ACTIVE",
            expectedEnergyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        HologramInvariantCase(
            id = 9,
            name = "Saturated RAM 100%",
            group = "Single-Metric Isolation",
            description = "Saturated memory utilization at 100%",
            snapshot = createSnapshot(
                cpuLoad = 0.00f,
                ramLoad = 1.00f,
                netLoad = 0.00f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.WARNING_BOOST,
                cpuThrottle = ThrottleState.NOMINAL,
                ramThrottle = ThrottleState.WARNING_BOOST,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 0.200f,
            expectedMiddleSpeedRps = 5.000f,
            expectedInnerSpeedRps = 0.200f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MagentaGpuNet,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "SYSTEM BOOST ACTIVE",
            expectedEnergyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        HologramInvariantCase(
            id = 10,
            name = "High Network Burst 80%",
            group = "Single-Metric Isolation",
            description = "Isolated network bandwidth surge at 80%",
            snapshot = createSnapshot(
                cpuLoad = 0.00f,
                ramLoad = 0.00f,
                netLoad = 0.80f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.WARNING_BOOST,
                cpuThrottle = ThrottleState.NOMINAL,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 0.200f,
            expectedMiddleSpeedRps = 0.200f,
            expectedInnerSpeedRps = 3.272f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MagentaGpuNet,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "SYSTEM BOOST ACTIVE",
            expectedEnergyLabel = "ENERGY OUTPUT: HIGH"
        ),
        HologramInvariantCase(
            id = 11,
            name = "Saturated Network 100%",
            group = "Single-Metric Isolation",
            description = "Saturated network pipe at 100%",
            snapshot = createSnapshot(
                cpuLoad = 0.00f,
                ramLoad = 0.00f,
                netLoad = 1.00f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.WARNING_BOOST,
                cpuThrottle = ThrottleState.NOMINAL,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 0.200f,
            expectedMiddleSpeedRps = 0.200f,
            expectedInnerSpeedRps = 5.000f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MagentaGpuNet,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "SYSTEM BOOST ACTIVE",
            expectedEnergyLabel = "ENERGY OUTPUT: HIGH"
        ),

        // Cases 12–16 (Dual-Metric Profiles)
        HologramInvariantCase(
            id = 12,
            name = "Heavy Gaming",
            group = "Dual-Metric Profiles",
            description = "Intensive 3D gaming load (85% CPU, 80% RAM, 10% Net)",
            snapshot = createSnapshot(
                cpuLoad = 0.85f,
                ramLoad = 0.80f,
                netLoad = 0.10f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.WARNING_BOOST,
                cpuThrottle = ThrottleState.WARNING_BOOST,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 3.668f,
            expectedMiddleSpeedRps = 3.272f,
            expectedInnerSpeedRps = 0.248f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MagentaGpuNet,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "SYSTEM BOOST ACTIVE",
            expectedEnergyLabel = "ENERGY OUTPUT: HIGH"
        ),
        HologramInvariantCase(
            id = 13,
            name = "Large Download",
            group = "Dual-Metric Profiles",
            description = "Heavy file transfer (20% CPU, 25% RAM, 90% Net)",
            snapshot = createSnapshot(
                cpuLoad = 0.20f,
                ramLoad = 0.25f,
                netLoad = 0.90f,
                cellularQuality = 1.00f,
                rsrpDbm = -75,
                worstThrottle = ThrottleState.WARNING_BOOST,
                cpuThrottle = ThrottleState.NOMINAL,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 0.392f,
            expectedMiddleSpeedRps = 0.500f,
            expectedInnerSpeedRps = 4.088f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MagentaGpuNet,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "SYSTEM BOOST ACTIVE",
            expectedEnergyLabel = "ENERGY OUTPUT: HIGH"
        ),
        HologramInvariantCase(
            id = 14,
            name = "Video Stream",
            group = "Dual-Metric Profiles",
            description = "High resolution media streaming (30% CPU, 65% RAM, 70% Net)",
            snapshot = createSnapshot(
                cpuLoad = 0.30f,
                ramLoad = 0.65f,
                netLoad = 0.70f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.WARNING_BOOST,
                cpuThrottle = ThrottleState.NOMINAL,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 0.632f,
            expectedMiddleSpeedRps = 2.228f,
            expectedInnerSpeedRps = 2.552f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MagentaGpuNet,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "SYSTEM BOOST ACTIVE",
            expectedEnergyLabel = "ENERGY OUTPUT: HIGH"
        ),
        HologramInvariantCase(
            id = 15,
            name = "Compilation",
            group = "Dual-Metric Profiles",
            description = "Heavy parallel software build (92% CPU, 90% RAM, 5% Net)",
            snapshot = createSnapshot(
                cpuLoad = 0.92f,
                ramLoad = 0.90f,
                netLoad = 0.05f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.WARNING_BOOST,
                cpuThrottle = ThrottleState.WARNING_BOOST,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 4.263f,
            expectedMiddleSpeedRps = 4.088f,
            expectedInnerSpeedRps = 0.212f,
            expectedOuterColor = NeonPalette.MeltdownRed,
            expectedMiddleColor = NeonPalette.MeltdownRed,
            expectedInnerColor = NeonPalette.MeltdownRed,
            expectedIsMeltdown = true,
            expectedStatusSubstring = "CRITICAL MELTDOWN",
            expectedEnergyLabel = "ENERGY OUTPUT: MAX EXCEEDED"
        ),
        HologramInvariantCase(
            id = 16,
            name = "Backup Sync",
            group = "Dual-Metric Profiles",
            description = "Cloud synchronization and encryption (40% CPU, 85% RAM, 80% Net)",
            snapshot = createSnapshot(
                cpuLoad = 0.40f,
                ramLoad = 0.85f,
                netLoad = 0.80f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.WARNING_BOOST,
                cpuThrottle = ThrottleState.NOMINAL,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 0.968f,
            expectedMiddleSpeedRps = 3.668f,
            expectedInnerSpeedRps = 3.272f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MagentaGpuNet,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "SYSTEM BOOST ACTIVE",
            expectedEnergyLabel = "ENERGY OUTPUT: HIGH"
        ),

        // Cases 17–20 (Balanced Tiers)
        HologramInvariantCase(
            id = 17,
            name = "All Low 20%",
            group = "Balanced Tiers",
            description = "Uniform low system activity across all sensors",
            snapshot = createSnapshot(
                cpuLoad = 0.20f,
                ramLoad = 0.20f,
                netLoad = 0.20f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.NOMINAL,
                cpuThrottle = ThrottleState.NOMINAL,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 0.392f,
            expectedMiddleSpeedRps = 0.392f,
            expectedInnerSpeedRps = 0.392f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MagentaGpuNet,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "SYSTEM NOMINAL",
            expectedEnergyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        HologramInvariantCase(
            id = 18,
            name = "All Mid 50%",
            group = "Balanced Tiers",
            description = "Uniform moderate load at 50% across all sensors",
            snapshot = createSnapshot(
                cpuLoad = 0.50f,
                ramLoad = 0.50f,
                netLoad = 0.50f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.NOMINAL,
                cpuThrottle = ThrottleState.NOMINAL,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 1.400f,
            expectedMiddleSpeedRps = 1.400f,
            expectedInnerSpeedRps = 1.400f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MagentaGpuNet,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "SYSTEM NOMINAL",
            expectedEnergyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        HologramInvariantCase(
            id = 19,
            name = "All High 75%",
            group = "Balanced Tiers",
            description = "Uniform high activity at 75% across all sensors",
            snapshot = createSnapshot(
                cpuLoad = 0.75f,
                ramLoad = 0.75f,
                netLoad = 0.75f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.WARNING_BOOST,
                cpuThrottle = ThrottleState.WARNING_BOOST,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 2.900f,
            expectedMiddleSpeedRps = 2.900f,
            expectedInnerSpeedRps = 2.900f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MagentaGpuNet,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "SYSTEM BOOST ACTIVE",
            expectedEnergyLabel = "ENERGY OUTPUT: HIGH"
        ),
        HologramInvariantCase(
            id = 20,
            name = "All Peak 100%",
            group = "Balanced Tiers",
            description = "All telemetry metrics saturated at 100%",
            snapshot = createSnapshot(
                cpuLoad = 1.00f,
                ramLoad = 1.00f,
                netLoad = 1.00f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.CRITICAL_THROTTLED,
                cpuThrottle = ThrottleState.CRITICAL_THROTTLED,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 5.000f,
            expectedMiddleSpeedRps = 5.000f,
            expectedInnerSpeedRps = 5.000f,
            expectedOuterColor = NeonPalette.MeltdownRed,
            expectedMiddleColor = NeonPalette.MeltdownRed,
            expectedInnerColor = NeonPalette.MeltdownRed,
            expectedIsMeltdown = true,
            expectedStatusSubstring = "CRITICAL MELTDOWN",
            expectedEnergyLabel = "ENERGY OUTPUT: MAX EXCEEDED"
        ),

        // Cases 21–24 (Cellular RF Degradation)
        HologramInvariantCase(
            id = 21,
            name = "Bad Cellular RSRP -125 dBm",
            group = "Cellular RF Degradation",
            description = "Extremely poor cellular signal at -125 dBm triggering RF alert",
            snapshot = createSnapshot(
                cpuLoad = 0.20f,
                ramLoad = 0.30f,
                netLoad = 0.10f,
                cellularQuality = 0.20f,
                rsrpDbm = -125,
                worstThrottle = ThrottleState.NOMINAL,
                cpuThrottle = ThrottleState.NOMINAL,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.CRITICAL_THROTTLED
            ),
            expectedOuterSpeedRps = 0.392f,
            expectedMiddleSpeedRps = 0.632f,
            expectedInnerSpeedRps = 0.248f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MeltdownRed,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "CELLULAR LINK LOST",
            expectedEnergyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        HologramInvariantCase(
            id = 22,
            name = "Edge Weak RF -116 dBm",
            group = "Cellular RF Degradation",
            description = "Cellular signal on critical boundary at -116 dBm",
            snapshot = createSnapshot(
                cpuLoad = 0.25f,
                ramLoad = 0.35f,
                netLoad = 0.15f,
                cellularQuality = 0.32f,
                rsrpDbm = -116,
                worstThrottle = ThrottleState.NOMINAL,
                cpuThrottle = ThrottleState.NOMINAL,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.CRITICAL_THROTTLED
            ),
            expectedOuterSpeedRps = 0.500f,
            expectedMiddleSpeedRps = 0.788f,
            expectedInnerSpeedRps = 0.308f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MeltdownRed,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "CELLULAR LINK LOST",
            expectedEnergyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        HologramInvariantCase(
            id = 23,
            name = "Dead Zone -140 dBm",
            group = "Cellular RF Degradation",
            description = "Complete cellular dead zone at -140 dBm",
            snapshot = createSnapshot(
                cpuLoad = 0.10f,
                ramLoad = 0.20f,
                netLoad = 0.00f,
                cellularQuality = 0.00f,
                rsrpDbm = -140,
                worstThrottle = ThrottleState.NOMINAL,
                cpuThrottle = ThrottleState.NOMINAL,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.CRITICAL_THROTTLED
            ),
            expectedOuterSpeedRps = 0.248f,
            expectedMiddleSpeedRps = 0.392f,
            expectedInnerSpeedRps = 0.200f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MeltdownRed,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "CELLULAR LINK LOST",
            expectedEnergyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        HologramInvariantCase(
            id = 24,
            name = "Optimal 5G -70 dBm",
            group = "Cellular RF Degradation",
            description = "Optimal 5G cellular link at -70 dBm with normal loads",
            snapshot = createSnapshot(
                cpuLoad = 0.30f,
                ramLoad = 0.40f,
                netLoad = 0.50f,
                cellularQuality = 0.93f,
                rsrpDbm = -70,
                worstThrottle = ThrottleState.NOMINAL,
                cpuThrottle = ThrottleState.NOMINAL,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 0.632f,
            expectedMiddleSpeedRps = 0.968f,
            expectedInnerSpeedRps = 1.400f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MagentaGpuNet,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "SYSTEM NOMINAL",
            expectedEnergyLabel = "ENERGY OUTPUT: NOMINAL"
        ),

        // Cases 25–28 (Thermal & Throttling Crisis)
        HologramInvariantCase(
            id = 25,
            name = "CPU Thermal Clock-Capping 95% at 394 MHz",
            group = "Thermal Crisis",
            description = "Severe thermal throttling capping CPU clock at 394 MHz under 95% load",
            snapshot = createSnapshot(
                cpuLoad = 0.95f,
                ramLoad = 0.40f,
                netLoad = 0.20f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.CRITICAL_THROTTLED,
                cpuThrottle = ThrottleState.CRITICAL_THROTTLED,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 4.532f,
            expectedMiddleSpeedRps = 0.968f,
            expectedInnerSpeedRps = 0.392f,
            expectedOuterColor = NeonPalette.MeltdownRed,
            expectedMiddleColor = NeonPalette.MeltdownRed,
            expectedInnerColor = NeonPalette.MeltdownRed,
            expectedIsMeltdown = true,
            expectedStatusSubstring = "CRITICAL MELTDOWN",
            expectedEnergyLabel = "ENERGY OUTPUT: MAX EXCEEDED"
        ),
        HologramInvariantCase(
            id = 26,
            name = "Unthrottled Boost 88% at 2.9 GHz",
            group = "Thermal Crisis",
            description = "High-performance compute burst running unthrottled at 2.9 GHz",
            snapshot = createSnapshot(
                cpuLoad = 0.88f,
                ramLoad = 0.50f,
                netLoad = 0.30f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.WARNING_BOOST,
                cpuThrottle = ThrottleState.WARNING_BOOST,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 3.917f,
            expectedMiddleSpeedRps = 1.400f,
            expectedInnerSpeedRps = 0.632f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MagentaGpuNet,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "SYSTEM BOOST ACTIVE",
            expectedEnergyLabel = "ENERGY OUTPUT: HIGH"
        ),
        HologramInvariantCase(
            id = 27,
            name = "Forced OS Thermal Throttling",
            group = "Thermal Crisis",
            description = "OS enforces thermal throttle state despite moderate 50% CPU load",
            snapshot = createSnapshot(
                cpuLoad = 0.50f,
                ramLoad = 0.50f,
                netLoad = 0.20f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.CRITICAL_THROTTLED,
                cpuThrottle = ThrottleState.CRITICAL_THROTTLED,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 1.400f,
            expectedMiddleSpeedRps = 1.400f,
            expectedInnerSpeedRps = 0.392f,
            expectedOuterColor = NeonPalette.MeltdownRed,
            expectedMiddleColor = NeonPalette.MeltdownRed,
            expectedInnerColor = NeonPalette.MeltdownRed,
            expectedIsMeltdown = true,
            expectedStatusSubstring = "CRITICAL MELTDOWN",
            expectedEnergyLabel = "ENERGY OUTPUT: MAX EXCEEDED"
        ),
        HologramInvariantCase(
            id = 28,
            name = "zRAM Memory Thrashing",
            group = "Thermal Crisis",
            description = "High RAM pressure with heavy zRAM swap and page thrashing",
            snapshot = createSnapshot(
                cpuLoad = 0.40f,
                ramLoad = 0.90f,
                netLoad = 0.10f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.NOMINAL,
                cpuThrottle = ThrottleState.NOMINAL,
                ramThrottle = ThrottleState.CRITICAL_THROTTLED,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 0.968f,
            expectedMiddleSpeedRps = 4.088f,
            expectedInnerSpeedRps = 0.248f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.MemoryThrashPurple,
            expectedInnerColor = NeonPalette.MagentaGpuNet,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "SYSTEM BOOST ACTIVE",
            expectedEnergyLabel = "ENERGY OUTPUT: NOMINAL"
        ),

        // Cases 29–30 (Mathematical Robustness)
        HologramInvariantCase(
            id = 29,
            name = "Negative Inputs Clamped",
            group = "Mathematical Robustness",
            description = "Erroneous negative metric inputs clamped safely to V_min",
            snapshot = createSnapshot(
                cpuLoad = -0.50f,
                ramLoad = -10.00f,
                netLoad = -0.01f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.NOMINAL,
                cpuThrottle = ThrottleState.NOMINAL,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 0.200f,
            expectedMiddleSpeedRps = 0.200f,
            expectedInnerSpeedRps = 0.200f,
            expectedOuterColor = NeonPalette.CyanCpu,
            expectedMiddleColor = NeonPalette.OrangeRam,
            expectedInnerColor = NeonPalette.MagentaGpuNet,
            expectedIsMeltdown = false,
            expectedStatusSubstring = "SYSTEM NOMINAL",
            expectedEnergyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        HologramInvariantCase(
            id = 30,
            name = "Overflow Inputs Clamped",
            group = "Mathematical Robustness",
            description = "Extreme overflow loads (>100%) clamped safely to V_max",
            snapshot = createSnapshot(
                cpuLoad = 1.50f,
                ramLoad = 999.00f,
                netLoad = 1.05f,
                cellularQuality = 1.00f,
                rsrpDbm = -80,
                worstThrottle = ThrottleState.CRITICAL_THROTTLED,
                cpuThrottle = ThrottleState.CRITICAL_THROTTLED,
                ramThrottle = ThrottleState.NOMINAL,
                cellThrottle = ThrottleState.NOMINAL
            ),
            expectedOuterSpeedRps = 5.000f,
            expectedMiddleSpeedRps = 5.000f,
            expectedInnerSpeedRps = 5.000f,
            expectedOuterColor = NeonPalette.MeltdownRed,
            expectedMiddleColor = NeonPalette.MeltdownRed,
            expectedInnerColor = NeonPalette.MeltdownRed,
            expectedIsMeltdown = true,
            expectedStatusSubstring = "CRITICAL MELTDOWN",
            expectedEnergyLabel = "ENERGY OUTPUT: MAX EXCEEDED"
        )
    )

    fun getById(id: Int): HologramInvariantCase? = ALL_CASES.find { it.id == id }

    fun getByGroup(group: String): List<HologramInvariantCase> = ALL_CASES.filter { it.group == group }
}
