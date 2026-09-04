package com.vnazarov.resourcemonitor.core.model

data class SystemTelemetrySnapshot(
    val timestampMs: Long = 0L,
    val cpu: MetricValue = MetricValue(),
    val ram: MetricValue = MetricValue(),
    val network: MetricValue = MetricValue(),
    val cellularQuality: MetricValue = MetricValue(),
    val storageIo: MetricValue = MetricValue(),
    val gpu: MetricValue = MetricValue(),
    val worstThrottleState: ThrottleState = ThrottleState.NOMINAL
)
