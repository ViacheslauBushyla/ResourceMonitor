package com.vnazarov.resourcemonitor.core.config

import kotlinx.coroutines.flow.Flow

interface HudSettingsRepository {
    val settingsFlow: Flow<HudSettings>
    suspend fun getSettings(): HudSettings
    suspend fun updateOverlayEnabled(enabled: Boolean)
    suspend fun updateOverlayMode(mode: String)
    suspend fun updateHaloDiameter(diameterDp: Float)
    suspend fun updateSpeedRange(vMin: Float, vMax: Float)
    suspend fun updateSpeedCurve(curve: String)
    suspend fun updateSensitivities(outer: Float, middle: Float, inner: Float)
    suspend fun updateMeltdownThreshold(threshold: Float)
    suspend fun updateChannelCpuMapping(mapping: String)
    suspend fun updateTelemetrySourceMode(mode: String)
    suspend fun updatePollingIntervals(activeMs: Long, idleMs: Long)
    suspend fun resetDefaults()
}
