package com.vnazarov.resourcemonitor.core.telemetry.system.collector

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import com.vnazarov.resourcemonitor.core.telemetry.api.ThermalCollector

class ThermalSystemCollector(
    private val context: Context? = null,
    private val thermalStatusProvider: (() -> Int)? = null,
    private val batteryTempProvider: (() -> Int?)? = null
) : ThermalCollector {

    private val powerManager: PowerManager? by lazy {
        context?.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    override fun getThermalStatusLevel(): Int {
        thermalStatusProvider?.let { return it() }
        return powerManager?.currentThermalStatus ?: 0
    }

    override fun getBatteryTemperatureMilliC(): Int? {
        batteryTempProvider?.let { return it() }

        val ctx = context ?: return null
        return try {
            val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            if (tenths != null && tenths > 0) {
                tenths * 100 // Convert tenths of °C (e.g. 350 = 35.0°C) to millidegrees C (35000)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun isThrottled(): Boolean = getThermalStatusLevel() >= 3
    fun isCritical(): Boolean = getThermalStatusLevel() >= 4
}
