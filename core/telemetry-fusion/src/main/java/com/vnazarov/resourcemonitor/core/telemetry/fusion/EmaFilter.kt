package com.vnazarov.resourcemonitor.core.telemetry.fusion

class EmaFilter(
    private val alpha: Float = 0.25f,
    initialValue: Float = 0f
) {
    var value: Float = initialValue
        private set

    fun filter(target: Float): Float {
        value = (alpha * target) + ((1f - alpha) * value)
        return value
    }

    fun reset(newValue: Float = 0f) {
        value = newValue
    }
}
