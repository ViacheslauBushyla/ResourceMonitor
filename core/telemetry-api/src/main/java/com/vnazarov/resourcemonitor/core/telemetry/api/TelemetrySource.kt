package com.vnazarov.resourcemonitor.core.telemetry.api

import com.vnazarov.resourcemonitor.core.model.RawTelemetryPacket
import kotlinx.coroutines.flow.Flow

interface TelemetrySource {
    val isAvailable: Boolean
    fun poll(): RawTelemetryPacket
    fun observe(intervalMs: Long): Flow<RawTelemetryPacket>
}
