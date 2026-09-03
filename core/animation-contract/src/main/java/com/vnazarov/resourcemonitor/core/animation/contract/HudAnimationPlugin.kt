package com.vnazarov.resourcemonitor.core.animation.contract

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface HudAnimationPlugin<T> {
    val manifest: AnimationManifest
    val parameterMapper: AnimationParameterMapper<T>

    @Composable
    fun Render(
        parameters: T,
        modifier: Modifier
    )

    @Composable
    fun Preview(modifier: Modifier)

    @Composable
    fun ConfigUi(
        config: Map<String, Any>,
        onConfigChanged: (key: String, value: Any) -> Unit,
        modifier: Modifier
    )
}
