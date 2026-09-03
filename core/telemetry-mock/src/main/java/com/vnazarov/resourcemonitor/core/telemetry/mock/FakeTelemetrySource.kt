package com.vnazarov.resourcemonitor.core.telemetry.mock

import com.vnazarov.resourcemonitor.core.model.RawTelemetryPacket
import com.vnazarov.resourcemonitor.core.telemetry.api.TelemetrySource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

class FakeTelemetrySource(
    initialScenario: SimulationScenario = SimulationScenario.IdleCalm
) : TelemetrySource {

    override val isAvailable: Boolean = true

    private val currentScenario = MutableStateFlow<SimulationScenario>(initialScenario)

    fun setScenario(scenario: SimulationScenario) {
        currentScenario.value = scenario
    }

    override fun poll(): RawTelemetryPacket {
        return currentScenario.value.generatePacket()
    }

    override fun observe(intervalMs: Long): Flow<RawTelemetryPacket> = flow {
        while (true) {
            emit(poll())
            delay(intervalMs)
        }
    }
}
