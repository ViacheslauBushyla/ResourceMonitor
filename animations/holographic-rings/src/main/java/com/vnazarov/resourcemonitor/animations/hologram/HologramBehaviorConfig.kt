package com.vnazarov.resourcemonitor.animations.hologram

import androidx.compose.ui.graphics.Color
import com.vnazarov.resourcemonitor.core.designsystem.theme.NeonPalette

data class HologramBehaviorConfig(
    val vMinRps: Float = 0.2f,
    val vMaxRps: Float = 5.0f,
    val speedCurve: SpeedCurve = SpeedCurve.QUADRATIC,
    val outerSensitivity: Float = 1.0f,   // Ring 1 (CPU)
    val middleSensitivity: Float = 1.0f,  // Ring 2 (RAM)
    val innerSensitivity: Float = 1.0f,   // Ring 3 (Network)
    val storageSensitivity: Float = 1.0f, // Ring 4 (Storage SSD)
    val gpuSensitivity: Float = 1.0f,     // Ring 5 (GPU/Thermal)
    val boostThreshold: Float = 0.70f,
    val meltdownThreshold: Float = 0.90f,
    val ring1BaseColor: Color = NeonPalette.CyanCpu,
    val ring2BaseColor: Color = NeonPalette.OrangeRam,
    val ring3BaseColor: Color = NeonPalette.MagentaGpuNet,
    val ring4BaseColor: Color = NeonPalette.IceBlueStorage,
    val ring5BaseColor: Color = NeonPalette.EmeraldGpu,
    val alertAmberColor: Color = NeonPalette.WarningAmber,
    val alertMeltdownColor: Color = NeonPalette.MeltdownRed,
    val alertThrashPurpleColor: Color = NeonPalette.MemoryThrashPurple,
    val alertStorageFlareColor: Color = NeonPalette.StorageStallWhite,
    // Backward compatibility aliases
    val outerBaseColor: Color = ring1BaseColor,
    val middleBaseColor: Color = ring2BaseColor,
    val innerBaseColor: Color = ring3BaseColor
)
