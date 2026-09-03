package com.vnazarov.resourcemonitor.animations.hologram

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vnazarov.resourcemonitor.core.designsystem.math.PerspectiveProjection3D
import com.vnazarov.resourcemonitor.core.designsystem.math.Point3D
import com.vnazarov.resourcemonitor.core.designsystem.theme.NeonPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HolographicRingsRenderer(
    params: HolographicRingsParams,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "HologramTransition")

    // Continuous time ticker (0 to 1 over 10 seconds)
    val timeTick by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "TimeTick"
    )

    // Meltdown pulse (rapid heartbeat pulse)
    val pulseTick by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseTick"
    )

    Box(
        modifier = modifier
            .background(NeonPalette.HudBackground)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Top Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = params.systemStatusLabel,
                color = if (params.isMeltdownAlert) NeonPalette.MeltdownRed else NeonPalette.CyanCpu,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
        }

        // Central Holographic 3D Rings Canvas
        Canvas(
            modifier = Modifier
                .size(280.dp)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.width * 0.38f

            // 1. Draw central energy aura / core
            val pulseMultiplier = if (params.isMeltdownAlert) pulseTick else 1.0f
            val coreRadius = baseRadius * 0.45f * pulseMultiplier
            val coreColor = if (params.isMeltdownAlert) NeonPalette.MeltdownRed else params.innerColor

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = 0.55f * params.bloomIntensity),
                        coreColor.copy(alpha = 0.15f * params.bloomIntensity),
                        Color.Transparent
                    ),
                    center = center,
                    radius = coreRadius * 1.5f
                ),
                radius = coreRadius * 1.5f,
                center = center
            )

            // Calculate rotational angles based on speed RPS and time elapsed
            val elapsedSec = timeTick * 10f
            val outerAngle = elapsedSec * params.outerRingSpeedRps * 2f * PI.toFloat()
            val middleAngle = elapsedSec * params.middleRingSpeedRps * 2f * PI.toFloat()
            val innerAngle = elapsedSec * params.innerRingSpeedRps * 2f * PI.toFloat()

            // Outer Ring: Cyan CPU, rotates around Y with 15 deg tilt
            val outerColor = if (params.isMeltdownAlert) NeonPalette.MeltdownRed else params.outerColor
            drawProjectedRing(
                center = center,
                radius = baseRadius,
                rotX = 0.26f, // ~15 deg tilt
                rotY = outerAngle,
                rotZ = 0f,
                color = outerColor,
                bloomIntensity = params.bloomIntensity
            )

            // Middle Ring: Orange RAM, rotates around X with 15 deg tilt
            val middleColor = if (params.isMeltdownAlert) NeonPalette.MeltdownRed else params.middleColor
            drawProjectedRing(
                center = center,
                radius = baseRadius * 0.82f,
                rotX = middleAngle,
                rotY = 0.26f,
                rotZ = 0.15f,
                color = middleColor,
                bloomIntensity = params.bloomIntensity
            )

            // Inner Ring: Magenta GPU/Net, tilted diagonal 45 deg
            val innerRingColor = if (params.isMeltdownAlert) NeonPalette.MeltdownRed else params.innerColor
            drawProjectedRing(
                center = center,
                radius = baseRadius * 0.65f,
                rotX = innerAngle * 0.7f,
                rotY = innerAngle,
                rotZ = 0.78f, // ~45 deg
                color = innerRingColor,
                bloomIntensity = params.bloomIntensity
            )

            // Ambient Sci-Fi brackets and tick marks
            drawHudBrackets(center, baseRadius * 1.25f, params.isMeltdownAlert)
        }

        // Bottom Footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = params.energyOutputLabel,
                color = if (params.isMeltdownAlert) NeonPalette.MeltdownRed else NeonPalette.NominalGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp
            )
        }
    }
}

/**
 * Draws a 3D ring with neon glow by sampling points along the circumference,
 * transforming via 3D rotation, and projecting onto 2D canvas with depth-buffered styling.
 */
private fun DrawScope.drawProjectedRing(
    center: Offset,
    radius: Float,
    rotX: Float,
    rotY: Float,
    rotZ: Float,
    color: Color,
    bloomIntensity: Float,
    segments: Int = 72
) {
    val points2D = ArrayList<Offset>(segments + 1)

    for (i in 0..segments) {
        val theta = (i.toFloat() / segments.toFloat()) * 2f * PI.toFloat()
        // Ring in local XY plane
        val localPoint = Point3D(
            x = radius * cos(theta),
            y = radius * sin(theta),
            z = 0f
        )
        val rotated = PerspectiveProjection3D.rotate3D(localPoint, rotX, rotY, rotZ)
        val projected = PerspectiveProjection3D.projectTo2D(rotated, center, cameraDistance = 500f)
        points2D.add(projected)
    }

    val ringPath = Path().apply {
        if (points2D.isNotEmpty()) {
            moveTo(points2D[0].x, points2D[0].y)
            for (idx in 1 until points2D.size) {
                lineTo(points2D[idx].x, points2D[idx].y)
            }
            close()
        }
    }

    // Outer glow pass
    drawPath(
        path = ringPath,
        color = color.copy(alpha = 0.20f * bloomIntensity),
        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
    )

    // Mid glow pass
    drawPath(
        path = ringPath,
        color = color.copy(alpha = 0.55f * bloomIntensity),
        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
    )

    // Inner sharp core pass
    drawPath(
        path = ringPath,
        color = Color.White.copy(alpha = 0.85f),
        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
    )
}

/**
 * Draws futuristic HUD brackets and ticks around the sphere
 */
private fun DrawScope.drawHudBrackets(
    center: Offset,
    outerRadius: Float,
    isAlert: Boolean
) {
    val bracketColor = if (isAlert) NeonPalette.MeltdownRed.copy(alpha = 0.5f) else NeonPalette.HudBorder
    val strokeWidth = 2.dp.toPx()

    // Left bracket arc
    drawArc(
        color = bracketColor,
        startAngle = 135f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
        size = androidx.compose.ui.geometry.Size(outerRadius * 2f, outerRadius * 2f),
        style = Stroke(width = strokeWidth)
    )

    // Right bracket arc
    drawArc(
        color = bracketColor,
        startAngle = 315f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
        size = androidx.compose.ui.geometry.Size(outerRadius * 2f, outerRadius * 2f),
        style = Stroke(width = strokeWidth)
    )
}

@Preview
@Composable
fun HolographicRingsIdlePreview() {
    HolographicRingsRenderer(
        params = HolographicRingsParams(
            outerRingSpeedRps = 0.2f,
            middleRingSpeedRps = 0.25f,
            innerRingSpeedRps = 0.3f,
            systemStatusLabel = "SYSTEM NOMINAL [15% LOAD]",
            energyOutputLabel = "ENERGY OUTPUT: BALANCED"
        ),
        modifier = Modifier.size(320.dp)
    )
}

@Preview
@Composable
fun HolographicRingsMeltdownPreview() {
    HolographicRingsRenderer(
        params = HolographicRingsParams(
            outerRingSpeedRps = 5.0f,
            middleRingSpeedRps = 4.2f,
            innerRingSpeedRps = 4.8f,
            isMeltdownAlert = true,
            bloomIntensity = 1.8f,
            systemStatusLabel = "SYSTEM PEAK LOAD [CRITICAL MELTDOWN]",
            energyOutputLabel = "ENERGY OUTPUT: MAX EXCEEDED"
        ),
        modifier = Modifier.size(320.dp)
    )
}
