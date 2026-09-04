package com.vnazarov.resourcemonitor.animations.hologram

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vnazarov.resourcemonitor.core.designsystem.cutout.CutoutGeometry
import com.vnazarov.resourcemonitor.core.designsystem.theme.NeonPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield

private const val DEFAULT_HALO_SEGMENTS = 32

// Pre-computed unit circle lookup table (zero trigonometric calls at runtime)
private val COS_TABLE_32 = FloatArray(DEFAULT_HALO_SEGMENTS + 1) { i ->
    cos((i.toFloat() / DEFAULT_HALO_SEGMENTS.toFloat()) * 2f * PI.toFloat())
}
private val SIN_TABLE_32 = FloatArray(DEFAULT_HALO_SEGMENTS + 1) { i ->
    sin((i.toFloat() / DEFAULT_HALO_SEGMENTS.toFloat()) * 2f * PI.toFloat())
}

/**
 * Cached stroke styles to eliminate per-frame Stroke allocations.
 */
private class HaloStrokeCache(
    val outerGlow: Stroke,
    val midGlow: Stroke,
    val innerCore: Stroke
)

/**
 * Frame-paced time ticker that updates at [targetFps] (default 30 FPS for ambient overlay).
 * Operates on wall-clock time and coroutine delay, sleeping cleanly between frames
 * to eliminate 120Hz Choreographer thrashing.
 */
@Composable
fun rememberPacedTimeTicker(
    targetFps: Int = 1,
    periodSeconds: Float = 10.0f
): State<Float> {
    val timeState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(targetFps) {
        val frameIntervalMs = 1000L / targetFps.coerceIn(1, 60)
        val startTime = SystemClock.uptimeMillis()
        while (isActive) {
            val frameStart = SystemClock.uptimeMillis()
            val elapsedSec = (frameStart - startTime) / 1000f
            timeState.floatValue = (elapsedSec % periodSeconds) / periodSeconds

            val computeTime = SystemClock.uptimeMillis() - frameStart
            val sleepTime = frameIntervalMs - computeTime
            if (sleepTime > 0) {
                delay(sleepTime)
            } else {
                yield()
            }
        }
    }
    return timeState
}

/**
 * Lightweight, transparent Canvas composable that renders the 5 concentric 3D gyroscopic rings
 * centered directly concentric to the front camera punch-hole lens.
 *
 * Provides pure rings with neon bloom shaders and zero background card or telemetry text overlays,
 * optimized for zero per-frame allocations and paced at 30 FPS for ambient overlay mode.
 */
@Composable
fun CameraHaloRingsRenderer(
    params: HolographicRingsParams,
    centerXPx: Float,
    centerYPx: Float,
    modifier: Modifier = Modifier,
    haloRadiusDp: Dp = 52.dp,
    targetFps: Int = 1
) {
    val timeTicker = rememberPacedTimeTicker(targetFps = targetFps)
    val density = LocalDensity.current.density

    // Reusable Path pool: 5 paths for 5 concentric rings, allocated once per composition
    val ringPaths = remember { Array(5) { Path() } }

    // Pre-cache stroke styles across compositions
    val strokeCache = remember(haloRadiusDp, density) {
        val rPx = if (haloRadiusDp > 0.dp) haloRadiusDp.value * density else 52f * density
        val strokeScale = (rPx / 100f).coerceIn(0.25f, 1.0f)
        val outerWidth = (4.0f * strokeScale).coerceAtLeast(1.8f) * density
        val midWidth = (2.2f * strokeScale).coerceAtLeast(1.0f) * density
        val coreWidth = (1.0f * strokeScale).coerceAtLeast(0.6f) * density
        HaloStrokeCache(
            outerGlow = Stroke(width = outerWidth, cap = StrokeCap.Round),
            midGlow = Stroke(width = midWidth, cap = StrokeCap.Round),
            innerCore = Stroke(width = coreWidth, cap = StrokeCap.Round)
        )
    }

    // Pre-cache meltdown gradient colors
    val meltdownColors = remember(params.bloomIntensity) {
        listOf(
            NeonPalette.MeltdownRed.copy(alpha = 0.45f * params.bloomIntensity),
            NeonPalette.MeltdownRed.copy(alpha = 0.15f * params.bloomIntensity),
            Color.Transparent
        )
    }

    Canvas(modifier = modifier) {
        val center = if (centerXPx > 0f && centerYPx > 0f) {
            Offset(centerXPx, centerYPx)
        } else {
            Offset(size.width / 2f, size.height / 2f)
        }

        val baseRadius = if (haloRadiusDp > 0.dp) {
            haloRadiusDp.toPx()
        } else {
            minOf(size.width, size.height) * 0.38f
        }

        val elapsedSec = timeTicker.value * 10f

        // Meltdown corona aura pulse expanding outside the camera hole
        if (params.isMeltdownAlert) {
            val pulsePhase = (elapsedSec % 0.8f) / 0.8f
            val pulseTick = if (pulsePhase < 0.5f) {
                0.8f + (pulsePhase * 2f) * 0.45f
            } else {
                1.25f - ((pulsePhase - 0.5f) * 2f) * 0.45f
            }
            val coronaRadius = maxOf(baseRadius * 0.45f, 22.dp.toPx()) * pulseTick
            drawCircle(
                brush = Brush.radialGradient(
                    colors = meltdownColors,
                    center = center,
                    radius = coronaRadius * 1.6f
                ),
                radius = coronaRadius * 1.6f,
                center = center
            )
        }

        // Rotational angles for the 5 channels
        val angle1 = elapsedSec * params.ring1SpeedRps * 2f * PI.toFloat()
        val angle2 = elapsedSec * params.ring2SpeedRps * 2f * PI.toFloat()
        val angle3 = elapsedSec * params.ring3SpeedRps * 2f * PI.toFloat()
        val angle4 = elapsedSec * params.ring4SpeedRps * 2f * PI.toFloat()
        val angle5 = elapsedSec * params.ring5SpeedRps * 2f * PI.toFloat()

        // Ring 1 (CPU): Outer, radius 1.00 R_0 (~52dp), Y-axis rotation with 15 deg tilt
        buildProjectedRingPath(
            path = ringPaths[0],
            centerX = center.x,
            centerY = center.y,
            radius = baseRadius * 1.00f,
            rotX = 0.26f,
            rotY = angle1,
            rotZ = 0f
        )
        drawHaloPasses(ringPaths[0], params.ring1Color, params.bloomIntensity, strokeCache)

        // Ring 2 (RAM): Radius 0.85 R_0 (~44.2dp), X-axis rotation with 15 deg tilt
        buildProjectedRingPath(
            path = ringPaths[1],
            centerX = center.x,
            centerY = center.y,
            radius = baseRadius * 0.85f,
            rotX = angle2,
            rotY = 0.26f,
            rotZ = 0.15f
        )
        drawHaloPasses(ringPaths[1], params.ring2Color, params.bloomIntensity, strokeCache)

        // Ring 3 (Network): Radius 0.70 R_0 (~36.4dp), diagonal 45 deg rotation
        buildProjectedRingPath(
            path = ringPaths[2],
            centerX = center.x,
            centerY = center.y,
            radius = baseRadius * 0.70f,
            rotX = angle3 * 0.707f,
            rotY = angle3 * 0.707f,
            rotZ = 0.785f
        )
        drawHaloPasses(ringPaths[2], params.ring3Color, params.bloomIntensity, strokeCache)

        // Ring 4 (Storage SSD): Radius 0.55 R_0 (~28.6dp), counter-diagonal -45 deg rotation
        buildProjectedRingPath(
            path = ringPaths[3],
            centerX = center.x,
            centerY = center.y,
            radius = baseRadius * 0.55f,
            rotX = angle4 * 0.707f,
            rotY = -angle4 * 0.707f,
            rotZ = -0.785f
        )
        drawHaloPasses(ringPaths[3], params.ring4Color, params.bloomIntensity, strokeCache)

        // Ring 5 (GPU/Thermal Corona): Radius 0.40 R_0 (~20.8dp), planar with 5 deg nutation
        // Strictly encircles 18dp punch-hole on Pixel 8 and 20dp punch-hole on Pixel 11 Pro
        buildProjectedRingPath(
            path = ringPaths[4],
            centerX = center.x,
            centerY = center.y,
            radius = baseRadius * 0.40f,
            rotX = sin(angle5 * 0.5f) * 0.09f,
            rotY = cos(angle5 * 0.5f) * 0.09f,
            rotZ = angle5
        )
        drawHaloPasses(ringPaths[4], params.ring5Color, params.bloomIntensity, strokeCache)
    }
}

/**
 * Convenience overload accepting [CutoutGeometry].
 */
@Composable
fun CameraHaloRingsRenderer(
    params: HolographicRingsParams,
    cutoutGeometry: CutoutGeometry,
    modifier: Modifier = Modifier,
    haloRadiusDp: Dp = 52.dp,
    targetFps: Int = 1
) {
    CameraHaloRingsRenderer(
        params = params,
        centerXPx = cutoutGeometry.centerXPx,
        centerYPx = cutoutGeometry.centerYPx,
        modifier = modifier,
        haloRadiusDp = haloRadiusDp,
        targetFps = targetFps
    )
}

/**
 * Builds 3D projected ring path directly into [path] without any heap allocations.
 * Inlines 3D Euler rotation and perspective division directly into primitive floats.
 */
private fun buildProjectedRingPath(
    path: Path,
    centerX: Float,
    centerY: Float,
    radius: Float,
    rotX: Float,
    rotY: Float,
    rotZ: Float,
    cameraDistance: Float = 500f,
    segments: Int = DEFAULT_HALO_SEGMENTS
) {
    path.rewind()

    val cosX = cos(rotX)
    val sinX = sin(rotX)
    val cosY = cos(rotY)
    val sinY = sin(rotY)
    val cosZ = cos(rotZ)
    val sinZ = sin(rotZ)

    val cosTable = if (segments == DEFAULT_HALO_SEGMENTS) COS_TABLE_32 else null
    val sinTable = if (segments == DEFAULT_HALO_SEGMENTS) SIN_TABLE_32 else null

    for (i in 0..segments) {
        val cosTheta = cosTable?.get(i) ?: cos((i.toFloat() / segments.toFloat()) * 2f * PI.toFloat())
        val sinTheta = sinTable?.get(i) ?: sin((i.toFloat() / segments.toFloat()) * 2f * PI.toFloat())

        val x0 = radius * cosTheta
        val y0 = radius * sinTheta

        // 3D rotation around X (z0 = 0)
        val y1 = y0 * cosX
        val z1 = y0 * sinX

        // 3D rotation around Y
        val x2 = x0 * cosY + z1 * sinY
        val z2 = -x0 * sinY + z1 * cosY

        // 3D rotation around Z
        val x3 = x2 * cosZ - y1 * sinZ
        val y3 = x2 * sinZ + y1 * cosZ

        // Perspective projection to 2D
        val distance = cameraDistance + z2
        val factor = if (distance > 0.1f) cameraDistance / distance else 1f
        val screenX = centerX + x3 * factor
        val screenY = centerY + y3 * factor

        if (i == 0) {
            path.moveTo(screenX, screenY)
        } else {
            path.lineTo(screenX, screenY)
        }
    }
    path.close()
}

/**
 * Draws the 3 glow passes using pre-cached Stroke instances.
 */
private fun DrawScope.drawHaloPasses(
    path: Path,
    color: Color,
    bloomIntensity: Float,
    strokeCache: HaloStrokeCache
) {
    // Outer bloom glow pass
    drawPath(
        path = path,
        color = color.copy(alpha = 0.50f * bloomIntensity),
        style = strokeCache.midGlow
    )

    // Inner sharp core pass
    drawPath(
        path = path,
        color = Color.White.copy(alpha = 0.85f),
        style = strokeCache.innerCore
    )
}
