package com.vnazarov.resourcemonitor.core.animation.contract

sealed class ConfigPropertyDefinition<T>(
    open val key: String,
    open val displayName: String,
    open val description: String,
    open val defaultValue: T
) {
    data class BooleanProperty(
        override val key: String,
        override val displayName: String,
        override val description: String,
        override val defaultValue: Boolean
    ) : ConfigPropertyDefinition<Boolean>(key, displayName, description, defaultValue)

    data class FloatRangeProperty(
        override val key: String,
        override val displayName: String,
        override val description: String,
        override val defaultValue: Float,
        val minValue: Float,
        val maxValue: Float,
        val step: Float = 0.05f
    ) : ConfigPropertyDefinition<Float>(key, displayName, description, defaultValue)

    data class ChoiceProperty(
        override val key: String,
        override val displayName: String,
        override val description: String,
        override val defaultValue: String,
        val options: List<String>
    ) : ConfigPropertyDefinition<String>(key, displayName, description, defaultValue)
}
