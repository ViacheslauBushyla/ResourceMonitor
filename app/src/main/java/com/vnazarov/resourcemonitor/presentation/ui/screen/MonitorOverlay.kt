package com.vnazarov.resourcemonitor.presentation.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vnazarov.resourcemonitor.animations.hologram.CameraHaloRingsRenderer
import com.vnazarov.resourcemonitor.animations.hologram.HologramBehaviorConfig
import com.vnazarov.resourcemonitor.animations.hologram.HologramProjectionCalculator
import com.vnazarov.resourcemonitor.animations.hologram.HolographicRingsPlugin
import com.vnazarov.resourcemonitor.animations.hologram.SpeedCurve
import com.vnazarov.resourcemonitor.core.config.HudSettings
import com.vnazarov.resourcemonitor.core.config.HudSettingsRepository
import com.vnazarov.resourcemonitor.core.designsystem.cutout.CutoutGeometry
import com.vnazarov.resourcemonitor.core.designsystem.cutout.CutoutGeometryResolver
import com.vnazarov.resourcemonitor.core.telemetry.fusion.TelemetryFusionEngine
import org.koin.compose.koinInject

@Composable
fun MonitorOverlay(
    modifier: Modifier = Modifier,
    cutoutGeometry: CutoutGeometry? = null,
    haloRadiusDp: Dp = 52.dp,
    hudSettings: HudSettings? = null,
    fusionEngine: TelemetryFusionEngine = koinInject(),
    plugin: HolographicRingsPlugin = koinInject(),
    settingsRepository: HudSettingsRepository = koinInject()
) {
    val snapshot by fusionEngine.snapshot.collectAsState()
    val settingsState by settingsRepository.settingsFlow.collectAsState(initial = hudSettings ?: HudSettings())
    val effectiveSettings = hudSettings ?: settingsState

    val behaviorConfig = remember(effectiveSettings) {
        HologramBehaviorConfig(
            vMinRps = effectiveSettings.vMinRps,
            vMaxRps = effectiveSettings.vMaxRps,
            speedCurve = try {
                SpeedCurve.valueOf(effectiveSettings.speedCurve.uppercase())
            } catch (_: Exception) {
                SpeedCurve.QUADRATIC
            },
            outerSensitivity = effectiveSettings.outerSensitivity,
            middleSensitivity = effectiveSettings.middleSensitivity,
            innerSensitivity = effectiveSettings.innerSensitivity,
            meltdownThreshold = effectiveSettings.meltdownThreshold
        )
    }

    val params = remember(snapshot, behaviorConfig) {
        HologramProjectionCalculator.computeParameters(snapshot, behaviorConfig)
    }

    val view = LocalView.current
    val density = LocalDensity.current.density
    val displayMetrics = LocalContext.current.resources.displayMetrics
    val resolvedGeometry = cutoutGeometry ?: CutoutGeometryResolver.resolve(
        windowInsets = view.rootWindowInsets,
        displayWidthPx = displayMetrics.widthPixels,
        density = density
    )

    val effectiveRadius = if (hudSettings != null) {
        haloRadiusDp
    } else {
        (effectiveSettings.haloDiameterDp / 2f).dp
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        CameraHaloRingsRenderer(
            params = params,
            centerXPx = resolvedGeometry.centerXPx,
            centerYPx = resolvedGeometry.centerYPx,
            modifier = Modifier.fillMaxSize(),
            haloRadiusDp = effectiveRadius
        )
    }
}