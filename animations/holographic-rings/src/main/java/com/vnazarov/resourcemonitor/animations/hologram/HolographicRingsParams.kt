package com.vnazarov.resourcemonitor.animations.hologram

import androidx.compose.ui.graphics.Color
import com.vnazarov.resourcemonitor.core.designsystem.theme.NeonPalette

data class HolographicRingsParams(
    val outerRingSpeedRps: Float = 0.2f,
    val middleRingSpeedRps: Float = 0.2f,
    val innerRingSpeedRps: Float = 0.2f,
    val outerColor: Color = NeonPalette.CyanCpu,
    val middleColor: Color = NeonPalette.OrangeRam,
    val innerColor: Color = NeonPalette.MagentaGpuNet,
    val isMeltdownAlert: Boolean = false,
    val bloomIntensity: Float = 1.0f,
    val systemStatusLabel: String = "SYSTEM NOMINAL",
    val energyOutputLabel: String = "ENERGY OUTPUT: NOMINAL"
)
