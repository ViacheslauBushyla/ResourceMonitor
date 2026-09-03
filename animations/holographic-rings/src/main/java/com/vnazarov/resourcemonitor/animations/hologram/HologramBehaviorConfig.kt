package com.vnazarov.resourcemonitor.animations.hologram

import androidx.compose.ui.graphics.Color
import com.vnazarov.resourcemonitor.core.designsystem.theme.NeonPalette

data class HologramBehaviorConfig(
    val vMinRps: Float = 0.2f,
    val vMaxRps: Float = 5.0f,
    val speedCurve: SpeedCurve = SpeedCurve.QUADRATIC,
    val outerSensitivity: Float = 1.0f,
    val middleSensitivity: Float = 1.0f,
    val innerSensitivity: Float = 1.0f,
    val boostThreshold: Float = 0.70f,
    val meltdownThreshold: Float = 0.90f,
    val outerBaseColor: Color = NeonPalette.CyanCpu,
    val middleBaseColor: Color = NeonPalette.OrangeRam,
    val innerBaseColor: Color = NeonPalette.MagentaGpuNet,
    val alertAmberColor: Color = NeonPalette.WarningAmber,
    val alertMeltdownColor: Color = NeonPalette.MeltdownRed,
    val alertThrashPurpleColor: Color = NeonPalette.MemoryThrashPurple
)
