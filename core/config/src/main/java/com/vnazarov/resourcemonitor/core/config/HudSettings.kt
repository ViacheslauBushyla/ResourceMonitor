package com.vnazarov.resourcemonitor.core.config

data class HudSettings(
    val isOverlayEnabled: Boolean = false,
    val overlayMode: String = "CAMERA_HALO",
    val haloDiameterDp: Float = 104.0f,
    val vMinRps: Float = 0.2f,
    val vMaxRps: Float = 5.0f,
    val speedCurve: String = "QUADRATIC",
    val outerSensitivity: Float = 1.0f,
    val middleSensitivity: Float = 1.0f,
    val innerSensitivity: Float = 1.0f,
    val meltdownThreshold: Float = 0.90f,
    val channelCpuMapping: String = "RING_1",
    val telemetrySourceMode: String = "REAL",
    val activePollingMs: Long = 250L,
    val idlePollingMs: Long = 2000L
)
