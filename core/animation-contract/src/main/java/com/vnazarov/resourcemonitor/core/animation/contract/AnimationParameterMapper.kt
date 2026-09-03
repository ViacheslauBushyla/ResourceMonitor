package com.vnazarov.resourcemonitor.core.animation.contract

import com.vnazarov.resourcemonitor.core.model.SystemTelemetrySnapshot

interface AnimationParameterMapper<T> {
    fun map(snapshot: SystemTelemetrySnapshot, config: Map<String, Any>): T
}
