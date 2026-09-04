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
    val expectedOuterSpeedRps: Float = 0.200f,
    val expectedMiddleSpeedRps: Float = 0.200f,
    val expectedInnerSpeedRps: Float = 0.200f,
    val expectedOuterColor: Color = NeonPalette.CyanCpu,
    val expectedMiddleColor: Color = NeonPalette.OrangeRam,
    val expectedInnerColor: Color = NeonPalette.MagentaGpuNet,
    val expectedIsMeltdown: Boolean = false,
    val expectedStatusSubstring: String = "SYSTEM NOMINAL",
    val expectedEnergyLabel: String = "ENERGY OUTPUT: NOMINAL",
    val expectedR1SpeedRps: Float = expectedOuterSpeedRps,
    val expectedR2SpeedRps: Float = expectedMiddleSpeedRps,
    val expectedR3SpeedRps: Float = expectedInnerSpeedRps,
    val expectedR4SpeedRps: Float = 0.200f,
    val expectedR5SpeedRps: Float = 0.200f,
    val expectedR1Color: Color = expectedOuterColor,
    val expectedR2Color: Color = expectedMiddleColor,
    val expectedR3Color: Color = expectedInnerColor,
    val expectedR4Color: Color = NeonPalette.IceBlueStorage,
    val expectedR5Color: Color = NeonPalette.EmeraldGpu,
    val expectedIsStorageStall: Boolean = false,
    val expectedIsMemoryThrash: Boolean = false,
    val expectedIsCellularDegraded: Boolean = false
) {
    constructor(
        id: Int,
        name: String,
        group: String,
        description: String,
        snapshot: SystemTelemetrySnapshot,
        r1SpeedRps: Float,
        r2SpeedRps: Float,
        r3SpeedRps: Float,
        r4SpeedRps: Float,
        r5SpeedRps: Float,
        r1Color: Color = NeonPalette.CyanCpu,
        r2Color: Color = NeonPalette.OrangeRam,
        r3Color: Color = NeonPalette.MagentaGpuNet,
        r4Color: Color = NeonPalette.IceBlueStorage,
        r5Color: Color = NeonPalette.EmeraldGpu,
        isMeltdown: Boolean = false,
        isStorageStall: Boolean = false,
        isMemoryThrash: Boolean = false,
        isCellularDegraded: Boolean = false,
        statusSubstring: String = "SYSTEM NOMINAL",
        energyLabel: String = "ENERGY OUTPUT: NOMINAL"
    ) : this(
        id = id,
        name = name,
        group = group,
        description = description,
        snapshot = snapshot,
        expectedOuterSpeedRps = r1SpeedRps,
        expectedMiddleSpeedRps = r2SpeedRps,
        expectedInnerSpeedRps = r3SpeedRps,
        expectedOuterColor = r1Color,
        expectedMiddleColor = r2Color,
        expectedInnerColor = r3Color,
        expectedIsMeltdown = isMeltdown,
        expectedStatusSubstring = statusSubstring,
        expectedEnergyLabel = energyLabel,
        expectedR1SpeedRps = r1SpeedRps,
        expectedR2SpeedRps = r2SpeedRps,
        expectedR3SpeedRps = r3SpeedRps,
        expectedR4SpeedRps = r4SpeedRps,
        expectedR5SpeedRps = r5SpeedRps,
        expectedR1Color = r1Color,
        expectedR2Color = r2Color,
        expectedR3Color = r3Color,
        expectedR4Color = r4Color,
        expectedR5Color = r5Color,
        expectedIsStorageStall = isStorageStall,
        expectedIsMemoryThrash = isMemoryThrash,
        expectedIsCellularDegraded = isCellularDegraded
    )

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
            isWifiActive = rsrp >= -80,
            gpuLoadPercentage = snapshot.gpu.smoothedValue * 100f,
            gpuTemperatureMilliC = if (snapshot.gpu.throttleState == ThrottleState.CRITICAL_THROTTLED) 65_000 else null,
            isStorageStall = expectedIsStorageStall || snapshot.storageIo.throttleState == ThrottleState.CRITICAL_THROTTLED,
            thermalStatusLevel = if (expectedIsMeltdown) 5 else 0
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

    private fun createExpandedCase(
        id: Int,
        name: String,
        group: String,
        description: String,
        cpuLoad: Float,
        ramLoad: Float,
        netLoad: Float,
        ssdLoad: Float = 0.0f,
        gpuLoad: Float = 0.0f,
        cellularQuality: Float = 1.0f,
        rsrpDbm: Int = -80,
        worstThrottle: ThrottleState = ThrottleState.NOMINAL,
        cpuThrottle: ThrottleState = ThrottleState.NOMINAL,
        ramThrottle: ThrottleState = ThrottleState.NOMINAL,
        netThrottle: ThrottleState = ThrottleState.NOMINAL,
        ssdThrottle: ThrottleState = ThrottleState.NOMINAL,
        gpuThrottle: ThrottleState = ThrottleState.NOMINAL,
        cellThrottle: ThrottleState = ThrottleState.NOMINAL,
        r1Speed: Float,
        r2Speed: Float,
        r3Speed: Float,
        r4Speed: Float,
        r5Speed: Float,
        r1Color: Color = NeonPalette.CyanCpu,
        r2Color: Color = NeonPalette.OrangeRam,
        r3Color: Color = NeonPalette.MagentaGpuNet,
        r4Color: Color = NeonPalette.IceBlueStorage,
        r5Color: Color = NeonPalette.EmeraldGpu,
        isMeltdown: Boolean = false,
        isStorageStall: Boolean = false,
        isMemoryThrash: Boolean = false,
        isCellularDegraded: Boolean = false,
        statusSubstring: String = "SYSTEM NOMINAL",
        energyLabel: String = "ENERGY OUTPUT: NOMINAL"
    ): HologramInvariantCase {
        val snap = SystemTelemetrySnapshot(
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
                throttleState = netThrottle,
                displayLabel = "NET ${(netLoad * 100).toInt()}%"
            ),
            cellularQuality = MetricValue(
                rawNormalized = cellularQuality,
                smoothedValue = cellularQuality,
                throttleState = cellThrottle,
                displayLabel = "$rsrpDbm dBm"
            ),
            storageIo = MetricValue(
                rawNormalized = ssdLoad,
                smoothedValue = ssdLoad,
                throttleState = ssdThrottle,
                displayLabel = "SSD ${(ssdLoad * 100).toInt()}%"
            ),
            gpu = MetricValue(
                rawNormalized = gpuLoad,
                smoothedValue = gpuLoad,
                throttleState = gpuThrottle,
                displayLabel = "GPU ${(gpuLoad * 100).toInt()}%"
            ),
            worstThrottleState = worstThrottle
        )
        return HologramInvariantCase(
            id = id,
            name = name,
            group = group,
            description = description,
            snapshot = snap,
            r1SpeedRps = r1Speed,
            r2SpeedRps = r2Speed,
            r3SpeedRps = r3Speed,
            r4SpeedRps = r4Speed,
            r5SpeedRps = r5Speed,
            r1Color = r1Color,
            r2Color = r2Color,
            r3Color = r3Color,
            r4Color = r4Color,
            r5Color = r5Color,
            isMeltdown = isMeltdown,
            isStorageStall = isStorageStall,
            isMemoryThrash = isMemoryThrash,
            isCellularDegraded = isCellularDegraded,
            statusSubstring = statusSubstring,
            energyLabel = energyLabel
        )
    }

    val EXPANDED_CASES: List<HologramInvariantCase> = listOf(
        // Case 1: Absolute Zero
        createExpandedCase(
            id = 1,
            name = "Absolute Zero",
            group = "Baseline / Idle",
            description = "All telemetry loads at zero, ideal cellular signal",
            cpuLoad = 0.00f, ramLoad = 0.00f, netLoad = 0.00f, ssdLoad = 0.00f, gpuLoad = 0.00f,
            r1Speed = 0.200f, r2Speed = 0.200f, r3Speed = 0.200f, r4Speed = 0.200f, r5Speed = 0.200f,
            statusSubstring = "SYSTEM NOMINAL",
            energyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        // Case 2: Idle Calm
        createExpandedCase(
            id = 2,
            name = "Idle Calm",
            group = "Baseline / Idle",
            description = "Idle background load with 15% CPU, 25% RAM, 5% Net",
            cpuLoad = 0.15f, ramLoad = 0.25f, netLoad = 0.05f, ssdLoad = 0.00f, gpuLoad = 0.00f,
            r1Speed = 0.308f, r2Speed = 0.500f, r3Speed = 0.212f, r4Speed = 0.200f, r5Speed = 0.200f,
            statusSubstring = "SYSTEM NOMINAL",
            energyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        // Case 3: Zero Network
        createExpandedCase(
            id = 3,
            name = "Zero Network",
            group = "Baseline / Idle",
            description = "CPU 10%, RAM 20%, Net 0%, SSD 10%, GPU 0%",
            cpuLoad = 0.10f, ramLoad = 0.20f, netLoad = 0.00f, ssdLoad = 0.10f, gpuLoad = 0.00f,
            r1Speed = 0.248f, r2Speed = 0.392f, r3Speed = 0.200f, r4Speed = 0.248f, r5Speed = 0.200f,
            statusSubstring = "SYSTEM NOMINAL",
            energyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        // Case 4: Zero RAM Fallback
        createExpandedCase(
            id = 4,
            name = "Zero RAM Fallback",
            group = "Baseline / Idle",
            description = "Fallback state where RAM reports zero usage: CPU 20%, RAM 0%, Net 10%",
            cpuLoad = 0.20f, ramLoad = 0.00f, netLoad = 0.10f, ssdLoad = 0.00f, gpuLoad = 0.00f,
            r1Speed = 0.392f, r2Speed = 0.200f, r3Speed = 0.248f, r4Speed = 0.200f, r5Speed = 0.200f,
            statusSubstring = "SYSTEM NOMINAL",
            energyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        // Case 5: Idle Boundary 29%
        createExpandedCase(
            id = 5,
            name = "Idle Boundary 29%",
            group = "Baseline / Idle",
            description = "Upper boundary for nominal baseline at 29% across all sensors",
            cpuLoad = 0.29f, ramLoad = 0.29f, netLoad = 0.29f, ssdLoad = 0.29f, gpuLoad = 0.29f,
            r1Speed = 0.604f, r2Speed = 0.604f, r3Speed = 0.604f, r4Speed = 0.604f, r5Speed = 0.604f,
            statusSubstring = "SYSTEM NOMINAL",
            energyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        // Case 6: High CPU 80%
        createExpandedCase(
            id = 6,
            name = "High CPU 80%",
            group = "Single-Metric Isolation",
            description = "Isolated CPU compute spike at 80%",
            cpuLoad = 0.80f, ramLoad = 0.15f, netLoad = 0.10f, ssdLoad = 0.00f, gpuLoad = 0.10f,
            worstThrottle = ThrottleState.WARNING_BOOST,
            cpuThrottle = ThrottleState.WARNING_BOOST,
            r1Speed = 3.272f, r2Speed = 0.308f, r3Speed = 0.248f, r4Speed = 0.200f, r5Speed = 0.248f,
            statusSubstring = "SYSTEM BOOST ACTIVE",
            energyLabel = "ENERGY OUTPUT: HIGH"
        ),
        // Case 7: Peak CPU Spike 100%
        createExpandedCase(
            id = 7,
            name = "Peak CPU Spike 100%",
            group = "Single-Metric Isolation",
            description = "Saturated CPU core execution at 100% triggering meltdown",
            cpuLoad = 1.00f, ramLoad = 0.00f, netLoad = 0.00f, ssdLoad = 0.00f, gpuLoad = 0.00f,
            worstThrottle = ThrottleState.CRITICAL_THROTTLED,
            cpuThrottle = ThrottleState.CRITICAL_THROTTLED,
            r1Speed = 5.000f, r2Speed = 0.200f, r3Speed = 0.200f, r4Speed = 0.200f, r5Speed = 0.200f,
            r1Color = NeonPalette.MeltdownRed,
            r2Color = NeonPalette.MeltdownRed,
            r3Color = NeonPalette.MeltdownRed,
            r4Color = NeonPalette.MeltdownRed,
            r5Color = NeonPalette.MeltdownRed,
            isMeltdown = true,
            statusSubstring = "CRITICAL MELTDOWN",
            energyLabel = "ENERGY OUTPUT: MAX EXCEEDED"
        ),
        // Case 8: High RAM Only 80%
        createExpandedCase(
            id = 8,
            name = "High RAM Only 80%",
            group = "Single-Metric Isolation",
            description = "Isolated memory allocation surge at 80%",
            cpuLoad = 0.00f, ramLoad = 0.80f, netLoad = 0.00f, ssdLoad = 0.00f, gpuLoad = 0.00f,
            worstThrottle = ThrottleState.WARNING_BOOST,
            r1Speed = 0.200f, r2Speed = 3.272f, r3Speed = 0.200f, r4Speed = 0.200f, r5Speed = 0.200f,
            statusSubstring = "SYSTEM BOOST ACTIVE",
            energyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        // Case 9: Saturated RAM 100%
        createExpandedCase(
            id = 9,
            name = "Saturated RAM 100%",
            group = "Single-Metric Isolation",
            description = "Saturated physical memory exhaustion at 100%",
            cpuLoad = 0.00f, ramLoad = 1.00f, netLoad = 0.00f, ssdLoad = 0.00f, gpuLoad = 0.00f,
            worstThrottle = ThrottleState.WARNING_BOOST,
            r1Speed = 0.200f, r2Speed = 5.000f, r3Speed = 0.200f, r4Speed = 0.200f, r5Speed = 0.200f,
            statusSubstring = "SYSTEM BOOST ACTIVE",
            energyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        // Case 10: High Network Burst 80%
        createExpandedCase(
            id = 10,
            name = "High Network Burst 80%",
            group = "Single-Metric Isolation",
            description = "Isolated network bandwidth surge at 80%",
            cpuLoad = 0.00f, ramLoad = 0.00f, netLoad = 0.80f, ssdLoad = 0.00f, gpuLoad = 0.00f,
            worstThrottle = ThrottleState.WARNING_BOOST,
            r1Speed = 0.200f, r2Speed = 0.200f, r3Speed = 3.272f, r4Speed = 0.200f, r5Speed = 0.200f,
            statusSubstring = "SYSTEM BOOST ACTIVE",
            energyLabel = "ENERGY OUTPUT: HIGH"
        ),
        // Case 11: Saturated Net 100%
        createExpandedCase(
            id = 11,
            name = "Saturated Net 100%",
            group = "Single-Metric Isolation",
            description = "Saturated network pipe at 100%",
            cpuLoad = 0.00f, ramLoad = 0.00f, netLoad = 1.00f, ssdLoad = 0.00f, gpuLoad = 0.00f,
            worstThrottle = ThrottleState.WARNING_BOOST,
            r1Speed = 0.200f, r2Speed = 0.200f, r3Speed = 5.000f, r4Speed = 0.200f, r5Speed = 0.200f,
            statusSubstring = "SYSTEM BOOST ACTIVE",
            energyLabel = "ENERGY OUTPUT: HIGH"
        ),
        // Case 12: Heavy SSD Write 85%
        createExpandedCase(
            id = 12,
            name = "Heavy SSD Write 85%",
            group = "Single-Metric Isolation",
            description = "Isolated storage write flush spike at 85%",
            cpuLoad = 0.00f, ramLoad = 0.00f, netLoad = 0.00f, ssdLoad = 0.85f, gpuLoad = 0.00f,
            worstThrottle = ThrottleState.WARNING_BOOST,
            r1Speed = 0.200f, r2Speed = 0.200f, r3Speed = 0.200f, r4Speed = 3.668f, r5Speed = 0.200f,
            statusSubstring = "SYSTEM BOOST ACTIVE",
            energyLabel = "ENERGY OUTPUT: HIGH"
        ),
        // Case 13: GPU 3D Rendering 85%
        createExpandedCase(
            id = 13,
            name = "GPU 3D Rendering 85%",
            group = "Single-Metric Isolation",
            description = "Isolated GPU pipeline utilization surge at 85%",
            cpuLoad = 0.00f, ramLoad = 0.00f, netLoad = 0.00f, ssdLoad = 0.00f, gpuLoad = 0.85f,
            worstThrottle = ThrottleState.WARNING_BOOST,
            r1Speed = 0.200f, r2Speed = 0.200f, r3Speed = 0.200f, r4Speed = 0.200f, r5Speed = 3.668f,
            statusSubstring = "SYSTEM BOOST ACTIVE",
            energyLabel = "ENERGY OUTPUT: HIGH"
        ),
        // Case 14: Heavy Gaming
        createExpandedCase(
            id = 14,
            name = "Heavy Gaming",
            group = "Dual-Metric Profiles",
            description = "Dual CPU/RAM gaming load: CPU 85%, RAM 80%, Net 10%, SSD 0%, GPU 10%",
            cpuLoad = 0.85f, ramLoad = 0.80f, netLoad = 0.10f, ssdLoad = 0.00f, gpuLoad = 0.10f,
            worstThrottle = ThrottleState.WARNING_BOOST,
            cpuThrottle = ThrottleState.WARNING_BOOST,
            r1Speed = 3.668f, r2Speed = 3.272f, r3Speed = 0.248f, r4Speed = 0.200f, r5Speed = 0.248f,
            statusSubstring = "SYSTEM BOOST ACTIVE",
            energyLabel = "ENERGY OUTPUT: HIGH"
        ),
        // Case 15: Large Download
        createExpandedCase(
            id = 15,
            name = "Large Download",
            group = "Dual-Metric Profiles",
            description = "Heavy file transfer: CPU 20%, RAM 25%, Net 90%, SSD 80%, GPU 0%",
            cpuLoad = 0.20f, ramLoad = 0.25f, netLoad = 0.90f, ssdLoad = 0.80f, gpuLoad = 0.00f,
            worstThrottle = ThrottleState.WARNING_BOOST,
            r1Speed = 0.392f, r2Speed = 0.500f, r3Speed = 4.088f, r4Speed = 3.272f, r5Speed = 0.200f,
            statusSubstring = "SYSTEM BOOST ACTIVE",
            energyLabel = "ENERGY OUTPUT: HIGH"
        ),
        // Case 16: Multi-Active Uniform 70%
        createExpandedCase(
            id = 16,
            name = "Uniform 70%",
            group = "Multi-Active",
            description = "Uniform active load at 70% across all 5 channels",
            cpuLoad = 0.70f, ramLoad = 0.70f, netLoad = 0.70f, ssdLoad = 0.70f, gpuLoad = 0.70f,
            worstThrottle = ThrottleState.WARNING_BOOST,
            cpuThrottle = ThrottleState.WARNING_BOOST,
            r1Speed = 2.552f, r2Speed = 2.552f, r3Speed = 2.552f, r4Speed = 2.552f, r5Speed = 2.552f,
            statusSubstring = "SYSTEM BOOST ACTIVE",
            energyLabel = "ENERGY OUTPUT: HIGH"
        ),
        // Case 17: All Low 20%
        createExpandedCase(
            id = 17,
            name = "All Low 20%",
            group = "Balanced Tiers",
            description = "Uniform low system activity across all 5 channels at 20%",
            cpuLoad = 0.20f, ramLoad = 0.20f, netLoad = 0.20f, ssdLoad = 0.20f, gpuLoad = 0.20f,
            r1Speed = 0.392f, r2Speed = 0.392f, r3Speed = 0.392f, r4Speed = 0.392f, r5Speed = 0.392f,
            statusSubstring = "SYSTEM NOMINAL",
            energyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        // Case 18: All Mid 50%
        createExpandedCase(
            id = 18,
            name = "All Mid 50%",
            group = "Balanced Tiers",
            description = "Uniform moderate load at 50% across all 5 channels",
            cpuLoad = 0.50f, ramLoad = 0.50f, netLoad = 0.50f, ssdLoad = 0.50f, gpuLoad = 0.50f,
            r1Speed = 1.400f, r2Speed = 1.400f, r3Speed = 1.400f, r4Speed = 1.400f, r5Speed = 1.400f,
            statusSubstring = "SYSTEM NOMINAL",
            energyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        // Case 19: All High 75%
        createExpandedCase(
            id = 19,
            name = "All High 75%",
            group = "Balanced Tiers",
            description = "Uniform high activity at 75% across all 5 channels",
            cpuLoad = 0.75f, ramLoad = 0.75f, netLoad = 0.75f, ssdLoad = 0.75f, gpuLoad = 0.75f,
            worstThrottle = ThrottleState.WARNING_BOOST,
            cpuThrottle = ThrottleState.WARNING_BOOST,
            r1Speed = 2.900f, r2Speed = 2.900f, r3Speed = 2.900f, r4Speed = 2.900f, r5Speed = 2.900f,
            statusSubstring = "SYSTEM BOOST ACTIVE",
            energyLabel = "ENERGY OUTPUT: HIGH"
        ),
        // Case 20: All Peak 100%
        createExpandedCase(
            id = 20,
            name = "All Peak 100%",
            group = "Balanced Tiers",
            description = "All telemetry metrics saturated at 100%",
            cpuLoad = 1.00f, ramLoad = 1.00f, netLoad = 1.00f, ssdLoad = 1.00f, gpuLoad = 1.00f,
            worstThrottle = ThrottleState.CRITICAL_THROTTLED,
            cpuThrottle = ThrottleState.CRITICAL_THROTTLED,
            r1Speed = 5.000f, r2Speed = 5.000f, r3Speed = 5.000f, r4Speed = 5.000f, r5Speed = 5.000f,
            r1Color = NeonPalette.MeltdownRed,
            r2Color = NeonPalette.MeltdownRed,
            r3Color = NeonPalette.MeltdownRed,
            r4Color = NeonPalette.MeltdownRed,
            r5Color = NeonPalette.MeltdownRed,
            isMeltdown = true,
            statusSubstring = "CRITICAL MELTDOWN",
            energyLabel = "ENERGY OUTPUT: MAX EXCEEDED"
        ),
        // Case 21: Bad Cellular RSRP -125
        createExpandedCase(
            id = 21,
            name = "Bad Cellular RSRP -125",
            group = "Cellular",
            description = "Degraded cellular link (RSRP -125 dBm), Net 10%, CPU 20%, RAM 30%, SSD 0%",
            cpuLoad = 0.20f, ramLoad = 0.30f, netLoad = 0.10f, ssdLoad = 0.00f, gpuLoad = 0.00f,
            cellularQuality = 0.20f, rsrpDbm = -125,
            cellThrottle = ThrottleState.WARNING_BOOST,
            r1Speed = 0.392f, r2Speed = 0.632f, r3Speed = 0.248f, r4Speed = 0.200f, r5Speed = 0.200f,
            r1Color = NeonPalette.CyanCpu,
            r2Color = NeonPalette.OrangeRam,
            r3Color = NeonPalette.WarningAmber,
            r4Color = NeonPalette.IceBlueStorage,
            r5Color = NeonPalette.EmeraldGpu,
            isCellularDegraded = true,
            statusSubstring = "CELLULAR SIGNAL DEGRADED",
            energyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        // Case 22: Dead Zone RSRP -140
        createExpandedCase(
            id = 22,
            name = "Dead Zone RSRP -140",
            group = "Cellular",
            description = "Complete cellular dead zone (RSRP -140 dBm), Net 0%, CPU 10%, RAM 20%, SSD 0%",
            cpuLoad = 0.10f, ramLoad = 0.20f, netLoad = 0.00f, ssdLoad = 0.00f, gpuLoad = 0.00f,
            cellularQuality = 0.00f, rsrpDbm = -140,
            cellThrottle = ThrottleState.CRITICAL_THROTTLED,
            r1Speed = 0.248f, r2Speed = 0.392f, r3Speed = 0.200f, r4Speed = 0.200f, r5Speed = 0.200f,
            r1Color = NeonPalette.CyanCpu,
            r2Color = NeonPalette.OrangeRam,
            r3Color = NeonPalette.MeltdownRed,
            r4Color = NeonPalette.IceBlueStorage,
            r5Color = NeonPalette.EmeraldGpu,
            isCellularDegraded = true,
            statusSubstring = "CELLULAR LINK LOST",
            energyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        // Case 23: Kernel iowait Stall
        createExpandedCase(
            id = 23,
            name = "Kernel iowait Stall",
            group = "Storage Stall",
            description = "Kernel iowait stall, SSD 95%, CPU 90%",
            cpuLoad = 0.90f, ramLoad = 0.40f, netLoad = 0.10f, ssdLoad = 0.90f, gpuLoad = 0.10f,
            worstThrottle = ThrottleState.CRITICAL_THROTTLED,
            cpuThrottle = ThrottleState.CRITICAL_THROTTLED,
            ssdThrottle = ThrottleState.CRITICAL_THROTTLED,
            r1Speed = 4.088f, r2Speed = 0.968f, r3Speed = 0.248f, r4Speed = 4.088f, r5Speed = 0.248f,
            r1Color = NeonPalette.MeltdownRed,
            r2Color = NeonPalette.MeltdownRed,
            r3Color = NeonPalette.MeltdownRed,
            r4Color = NeonPalette.StorageStallWhite,
            r5Color = NeonPalette.MeltdownRed,
            isMeltdown = true,
            isStorageStall = true,
            statusSubstring = "IO_WAIT_STALL",
            energyLabel = "ENERGY OUTPUT: MAX EXCEEDED"
        ),
        // Case 24: CPU Clock-Capped 95%
        createExpandedCase(
            id = 24,
            name = "CPU Clock-Capped 95%",
            group = "Thermal Crisis",
            description = "CPU 95% throttled to 394 MHz base clock, RAM 40%, Net 20%",
            cpuLoad = 0.95f, ramLoad = 0.40f, netLoad = 0.20f, ssdLoad = 0.00f, gpuLoad = 0.10f,
            worstThrottle = ThrottleState.CRITICAL_THROTTLED,
            cpuThrottle = ThrottleState.CRITICAL_THROTTLED,
            r1Speed = 4.532f, r2Speed = 0.968f, r3Speed = 0.392f, r4Speed = 0.200f, r5Speed = 0.248f,
            r1Color = NeonPalette.MeltdownRed,
            r2Color = NeonPalette.MeltdownRed,
            r3Color = NeonPalette.MeltdownRed,
            r4Color = NeonPalette.MeltdownRed,
            r5Color = NeonPalette.MeltdownRed,
            isMeltdown = true,
            statusSubstring = "CRITICAL MELTDOWN",
            energyLabel = "ENERGY OUTPUT: MAX EXCEEDED"
        ),
        // Case 25: Severe OS Throttling
        createExpandedCase(
            id = 25,
            name = "Severe OS Throttling",
            group = "Thermal Crisis",
            description = "CPU 60% with OS Severe Throttling, GPU 60%",
            cpuLoad = 0.60f, ramLoad = 0.40f, netLoad = 0.20f, ssdLoad = 0.00f, gpuLoad = 0.60f,
            worstThrottle = ThrottleState.CRITICAL_THROTTLED,
            cpuThrottle = ThrottleState.CRITICAL_THROTTLED,
            gpuThrottle = ThrottleState.CRITICAL_THROTTLED,
            r1Speed = 1.928f, r2Speed = 0.968f, r3Speed = 0.392f, r4Speed = 0.200f, r5Speed = 3.000f,
            r1Color = NeonPalette.MeltdownRed,
            r2Color = NeonPalette.MeltdownRed,
            r3Color = NeonPalette.MeltdownRed,
            r4Color = NeonPalette.MeltdownRed,
            r5Color = NeonPalette.MeltdownRed,
            isMeltdown = true,
            statusSubstring = "CRITICAL MELTDOWN",
            energyLabel = "ENERGY OUTPUT: MAX EXCEEDED"
        ),
        // Case 26: Boosted 88% at 2.9 GHz
        createExpandedCase(
            id = 26,
            name = "Boosted 88% at 2.9 GHz",
            group = "Thermal Boost",
            description = "CPU 88% boosted at 2.9 GHz without thermal throttling",
            cpuLoad = 0.88f, ramLoad = 0.50f, netLoad = 0.30f, ssdLoad = 0.00f, gpuLoad = 0.10f,
            worstThrottle = ThrottleState.WARNING_BOOST,
            cpuThrottle = ThrottleState.WARNING_BOOST,
            r1Speed = 3.917f, r2Speed = 1.400f, r3Speed = 0.632f, r4Speed = 0.200f, r5Speed = 0.248f,
            statusSubstring = "SYSTEM BOOST ACTIVE",
            energyLabel = "ENERGY OUTPUT: HIGH"
        ),
        // Case 27: zRAM Thrash & Stalls
        createExpandedCase(
            id = 27,
            name = "zRAM Thrash & Stalls",
            group = "Memory Thrash",
            description = "RAM 92% + compact_stalls > 200, CPU 40%, Net 10%",
            cpuLoad = 0.40f, ramLoad = 0.92f, netLoad = 0.10f, ssdLoad = 0.00f, gpuLoad = 0.00f,
            ramThrottle = ThrottleState.CRITICAL_THROTTLED,
            r1Speed = 0.968f, r2Speed = 4.263f, r3Speed = 0.248f, r4Speed = 0.200f, r5Speed = 0.200f,
            r1Color = NeonPalette.CyanCpu,
            r2Color = NeonPalette.MemoryThrashPurple,
            r3Color = NeonPalette.MagentaGpuNet,
            r4Color = NeonPalette.IceBlueStorage,
            r5Color = NeonPalette.EmeraldGpu,
            isMemoryThrash = true,
            statusSubstring = "SYSTEM BOOST ACTIVE",
            energyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        // Case 28: Download + SSD Install
        createExpandedCase(
            id = 28,
            name = "Download + SSD Install",
            group = "Storage + Net",
            description = "Net 95% (Download) + SSD 90% (Install), CPU 20%, RAM 30%",
            cpuLoad = 0.20f, ramLoad = 0.30f, netLoad = 0.95f, ssdLoad = 0.90f, gpuLoad = 0.00f,
            worstThrottle = ThrottleState.WARNING_BOOST,
            r1Speed = 0.392f, r2Speed = 0.632f, r3Speed = 4.532f, r4Speed = 4.088f, r5Speed = 0.200f,
            statusSubstring = "SYSTEM BOOST ACTIVE",
            energyLabel = "ENERGY OUTPUT: HIGH"
        ),
        // Case 29: Negative Loads (-20%)
        createExpandedCase(
            id = 29,
            name = "Negative Loads (-20%)",
            group = "Mathematical Robustness",
            description = "All metrics at -0.20 (Underflow Guard)",
            cpuLoad = -0.20f, ramLoad = -0.20f, netLoad = -0.20f, ssdLoad = -0.20f, gpuLoad = -0.20f,
            r1Speed = 0.200f, r2Speed = 0.200f, r3Speed = 0.200f, r4Speed = 0.200f, r5Speed = 0.200f,
            statusSubstring = "SYSTEM NOMINAL",
            energyLabel = "ENERGY OUTPUT: NOMINAL"
        ),
        // Case 30: Overflow Loads (250%)
        createExpandedCase(
            id = 30,
            name = "Overflow Loads (250%)",
            group = "Mathematical Robustness",
            description = "All metrics at 2.50 (Overflow Guard)",
            cpuLoad = 2.50f, ramLoad = 2.50f, netLoad = 2.50f, ssdLoad = 2.50f, gpuLoad = 2.50f,
            worstThrottle = ThrottleState.CRITICAL_THROTTLED,
            cpuThrottle = ThrottleState.CRITICAL_THROTTLED,
            r1Speed = 5.000f, r2Speed = 5.000f, r3Speed = 5.000f, r4Speed = 5.000f, r5Speed = 5.000f,
            r1Color = NeonPalette.MeltdownRed,
            r2Color = NeonPalette.MeltdownRed,
            r3Color = NeonPalette.MeltdownRed,
            r4Color = NeonPalette.MeltdownRed,
            r5Color = NeonPalette.MeltdownRed,
            isMeltdown = true,
            statusSubstring = "CRITICAL MELTDOWN",
            energyLabel = "ENERGY OUTPUT: MAX EXCEEDED"
        )
    )

    val EXPANDED_ALL_CASES: List<HologramInvariantCase> get() = EXPANDED_CASES

    fun getExpandedById(id: Int): HologramInvariantCase? = EXPANDED_CASES.find { it.id == id }

    fun getExpandedByGroup(group: String): List<HologramInvariantCase> = EXPANDED_CASES.filter { it.group == group }
}
