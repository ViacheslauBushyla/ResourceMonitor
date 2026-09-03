package com.vnazarov.resourcemonitor.presentation.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vnazarov.resourcemonitor.animations.hologram.HolographicRingsPlugin
import com.vnazarov.resourcemonitor.core.telemetry.fusion.TelemetryFusionEngine
import org.koin.compose.koinInject

@Composable
fun MonitorOverlay(
    modifier: Modifier = Modifier,
    fusionEngine: TelemetryFusionEngine = koinInject(),
    plugin: HolographicRingsPlugin = koinInject()
) {
    val snapshot by fusionEngine.snapshot.collectAsState()
    val params = plugin.parameterMapper.map(snapshot, emptyMap())

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp),
        contentAlignment = Alignment.Center
    ) {
        plugin.Render(
            parameters = params,
            modifier = Modifier.fillMaxWidth()
        )
    }
}