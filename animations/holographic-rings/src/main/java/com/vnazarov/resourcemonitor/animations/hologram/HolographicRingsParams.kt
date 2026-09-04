package com.vnazarov.resourcemonitor.animations.hologram

import androidx.compose.ui.graphics.Color
import com.vnazarov.resourcemonitor.core.designsystem.theme.NeonPalette

data class HolographicRingsParams(
    // 5-Channel Speed Parameters
    val ring1SpeedRps: Float = 0.2f, // CPU
    val ring2SpeedRps: Float = 0.2f, // RAM
    val ring3SpeedRps: Float = 0.2f, // Network
    val ring4SpeedRps: Float = 0.2f, // Storage SSD
    val ring5SpeedRps: Float = 0.2f, // GPU / Thermal Corona

    // 5-Channel Colors
    val ring1Color: Color = NeonPalette.CyanCpu,
    val ring2Color: Color = NeonPalette.OrangeRam,
    val ring3Color: Color = NeonPalette.MagentaGpuNet,
    val ring4Color: Color = NeonPalette.IceBlueStorage,
    val ring5Color: Color = NeonPalette.EmeraldGpu,

    // Alert Flags
    val isMeltdownAlert: Boolean = false,
    val isStorageStallAlert: Boolean = false,
    val isMemoryThrashAlert: Boolean = false,
    val isCellularDegradedAlert: Boolean = false,

    // Visual & Header Badges
    val bloomIntensity: Float = 1.0f,
    val systemStatusLabel: String = "SYSTEM NOMINAL",
    val energyOutputLabel: String = "ENERGY OUTPUT: NOMINAL",

    // Backward Compatibility Properties
    val outerRingSpeedRps: Float = ring1SpeedRps,
    val middleRingSpeedRps: Float = ring2SpeedRps,
    val innerRingSpeedRps: Float = ring3SpeedRps,
    val outerColor: Color = ring1Color,
    val middleColor: Color = ring2Color,
    val innerColor: Color = ring3Color
)
