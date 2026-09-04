package com.vnazarov.resourcemonitor.core.config

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object HudPreferencesKeys {
    val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
    val OVERLAY_MODE = stringPreferencesKey("overlay_mode")
    val HALO_DIAMETER_DP = floatPreferencesKey("halo_diameter_dp")
    val SPEED_V_MIN = floatPreferencesKey("speed_v_min")
    val SPEED_V_MAX = floatPreferencesKey("speed_v_max")
    val SPEED_CURVE = stringPreferencesKey("speed_curve")
    val OUTER_SENSITIVITY = floatPreferencesKey("outer_sensitivity")
    val MIDDLE_SENSITIVITY = floatPreferencesKey("middle_sensitivity")
    val INNER_SENSITIVITY = floatPreferencesKey("inner_sensitivity")
    val MELTDOWN_THRESHOLD = floatPreferencesKey("meltdown_threshold")
    val CHANNEL_CPU_MAPPING = stringPreferencesKey("channel_cpu_mapping")
    val TELEMETRY_SOURCE_MODE = stringPreferencesKey("telemetry_source_mode")
    val ACTIVE_POLLING_MS = longPreferencesKey("active_polling_ms")
    val IDLE_POLLING_MS = longPreferencesKey("idle_polling_ms")
}
