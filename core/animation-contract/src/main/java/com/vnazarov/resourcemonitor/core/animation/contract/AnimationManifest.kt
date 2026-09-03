package com.vnazarov.resourcemonitor.core.animation.contract

data class AnimationManifest(
    val id: String,
    val displayName: String,
    val description: String,
    val author: String,
    val version: Int,
    val requiredSensors: Set<SensorType>,
    val previewThumbnailResId: Int = 0,
    val configProperties: List<ConfigPropertyDefinition<*>> = emptyList()
)
