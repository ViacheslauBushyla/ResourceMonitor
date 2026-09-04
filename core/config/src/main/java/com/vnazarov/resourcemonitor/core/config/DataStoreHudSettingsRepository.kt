package com.vnazarov.resourcemonitor.core.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

class DataStoreHudSettingsRepository(
    private val dataStore: DataStore<Preferences>
) : HudSettingsRepository {

    override val settingsFlow: Flow<HudSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            mapPreferencesToSettings(prefs)
        }

    override suspend fun getSettings(): HudSettings = settingsFlow.first()

    override suspend fun updateOverlayEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[HudPreferencesKeys.OVERLAY_ENABLED] = enabled
        }
    }

    override suspend fun updateOverlayMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[HudPreferencesKeys.OVERLAY_MODE] = mode
        }
    }

    override suspend fun updateHaloDiameter(diameterDp: Float) {
        dataStore.edit { prefs ->
            prefs[HudPreferencesKeys.HALO_DIAMETER_DP] = diameterDp
        }
    }

    override suspend fun updateSpeedRange(vMin: Float, vMax: Float) {
        dataStore.edit { prefs ->
            prefs[HudPreferencesKeys.SPEED_V_MIN] = vMin
            prefs[HudPreferencesKeys.SPEED_V_MAX] = vMax
        }
    }

    override suspend fun updateSpeedCurve(curve: String) {
        dataStore.edit { prefs ->
            prefs[HudPreferencesKeys.SPEED_CURVE] = curve
        }
    }

    override suspend fun updateSensitivities(outer: Float, middle: Float, inner: Float) {
        dataStore.edit { prefs ->
            prefs[HudPreferencesKeys.OUTER_SENSITIVITY] = outer
            prefs[HudPreferencesKeys.MIDDLE_SENSITIVITY] = middle
            prefs[HudPreferencesKeys.INNER_SENSITIVITY] = inner
        }
    }

    override suspend fun updateMeltdownThreshold(threshold: Float) {
        dataStore.edit { prefs ->
            prefs[HudPreferencesKeys.MELTDOWN_THRESHOLD] = threshold
        }
    }

    override suspend fun updateChannelCpuMapping(mapping: String) {
        dataStore.edit { prefs ->
            prefs[HudPreferencesKeys.CHANNEL_CPU_MAPPING] = mapping
        }
    }

    override suspend fun updateTelemetrySourceMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[HudPreferencesKeys.TELEMETRY_SOURCE_MODE] = mode
        }
    }

    override suspend fun updatePollingIntervals(activeMs: Long, idleMs: Long) {
        dataStore.edit { prefs ->
            prefs[HudPreferencesKeys.ACTIVE_POLLING_MS] = activeMs
            prefs[HudPreferencesKeys.IDLE_POLLING_MS] = idleMs
        }
    }

    override suspend fun resetDefaults() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    private fun mapPreferencesToSettings(prefs: Preferences): HudSettings {
        val defaults = HudSettings()
        return HudSettings(
            isOverlayEnabled = prefs[HudPreferencesKeys.OVERLAY_ENABLED] ?: defaults.isOverlayEnabled,
            overlayMode = prefs[HudPreferencesKeys.OVERLAY_MODE] ?: defaults.overlayMode,
            haloDiameterDp = prefs[HudPreferencesKeys.HALO_DIAMETER_DP] ?: defaults.haloDiameterDp,
            vMinRps = prefs[HudPreferencesKeys.SPEED_V_MIN] ?: defaults.vMinRps,
            vMaxRps = prefs[HudPreferencesKeys.SPEED_V_MAX] ?: defaults.vMaxRps,
            speedCurve = prefs[HudPreferencesKeys.SPEED_CURVE] ?: defaults.speedCurve,
            outerSensitivity = prefs[HudPreferencesKeys.OUTER_SENSITIVITY] ?: defaults.outerSensitivity,
            middleSensitivity = prefs[HudPreferencesKeys.MIDDLE_SENSITIVITY] ?: defaults.middleSensitivity,
            innerSensitivity = prefs[HudPreferencesKeys.INNER_SENSITIVITY] ?: defaults.innerSensitivity,
            meltdownThreshold = prefs[HudPreferencesKeys.MELTDOWN_THRESHOLD] ?: defaults.meltdownThreshold,
            channelCpuMapping = prefs[HudPreferencesKeys.CHANNEL_CPU_MAPPING] ?: defaults.channelCpuMapping,
            telemetrySourceMode = prefs[HudPreferencesKeys.TELEMETRY_SOURCE_MODE] ?: defaults.telemetrySourceMode,
            activePollingMs = prefs[HudPreferencesKeys.ACTIVE_POLLING_MS] ?: defaults.activePollingMs,
            idlePollingMs = prefs[HudPreferencesKeys.IDLE_POLLING_MS] ?: defaults.idlePollingMs
        )
    }
}
