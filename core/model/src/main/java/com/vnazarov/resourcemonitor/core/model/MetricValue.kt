package com.vnazarov.resourcemonitor.core.model

data class MetricValue(
    val rawNormalized: Float = 0f,
    val smoothedValue: Float = 0f,
    val throttleState: ThrottleState = ThrottleState.NOMINAL,
    val displayLabel: String = ""
)
