package com.vnazarov.resourcemonitor.core.designsystem.cutout

import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import com.vnazarov.resourcemonitor.core.designsystem.math.PerspectiveProjection3D
import com.vnazarov.resourcemonitor.core.designsystem.math.Point3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Empirical Challenge Test Suite for Milestone 1 Iteration 2.
 *
 * Adversarial stress-testing of CutoutGeometryResolver and calculateOverlayHeightPx:
 * 1. calculateOverlayHeightPx across continuous density sweeps (1.0f to 4.0f) and extreme densities.
 * 2. Extreme status bar insets (zero, negative, tiny, huge).
 * 3. Exotic cutout geometries and boundary conditions (zero/negative offsets, off-screen, pills, notches).
 * 4. Exhaustive 3D perspective projection clipping oracle (Ring 1 outer glow vs window boundary).
 * 5. Meltdown corona aura boundary bounds.
 * 6. Ring 5 hardware punch-hole aperture encirclement & clearance margin.
 * 7. Halo radius upper clipping breakdown boundary.
 */
class OverlayHeightEmpiricalChallengeTest {

    private val tolerance = 0.001f

    private fun createRect(left: Int, top: Int, right: Int, bottom: Int): Rect {
        return Rect().apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }
    }

    // =============================================================================================
    // Challenge 1: Density Sweeps (1.0 to 4.0 and beyond)
    // =============================================================================================

    @Test
    fun challenge1_densitySweep_overlayHeightScalesMonotonicallyAndMaintainsHeadroom() {
        val testDensities = listOf(
            0.75f,  // ldpi
            1.0f,   // mdpi
            1.25f,  // non-standard
            1.33f,  // tvdpi
            1.5f,   // hdpi
            1.75f,  // non-standard
            2.0f,   // xhdpi
            2.25f,  // non-standard
            2.625f, // Pixel 8 (420 dpi)
            3.0f,   // Pixel 11 Pro / xxhdpi
            3.09375f, // Pixel 11 Pro native
            3.5f,   // non-standard
            4.0f,   // xxxhdpi
            5.0f    // extreme synthetic
        )

        var previousHeight = 0

        for (density in testDensities) {
            // Pixel 8 cutout proportions at this density:
            // Status bar = 50.28dp * density, center Y = 25.14dp * density
            val statusBarPx = (50.28f * density).toInt()
            val centerYPx = 25.14f * density
            val geometry = CutoutGeometry(
                centerXPx = 540f,
                centerYPx = centerYPx,
                cutoutDiameterPx = 50.28f * density,
                statusBarHeightPx = statusBarPx,
                isCutoutDetected = true
            )

            val overlayHeight = CutoutGeometryResolver.calculateOverlayHeightPx(
                geometry = geometry,
                haloRadiusDp = 52f,
                bloomPaddingDp = 16f,
                density = density
            )

            // 1. Invariant: Overlay height must strictly exceed status bar height
            assertTrue(
                "Overlay height ($overlayHeight px) must exceed status bar ($statusBarPx px) at density $density",
                overlayHeight > statusBarPx
            )

            // 2. Invariant: Overlay height must scale monotonically with density
            assertTrue(
                "Overlay height ($overlayHeight px) must be >= previous ($previousHeight px) as density increases",
                overlayHeight >= previousHeight
            )
            previousHeight = overlayHeight

            // 3. Invariant: Minimum clearance between overlay boundary and Ring 1 base radius
            val ring1BaseBottom = centerYPx + (52f * density)
            val headroom = overlayHeight - ring1BaseBottom
            val expectedMinHeadroom = 16f * density - 1f // accounting for .toInt() truncation
            assertTrue(
                "Headroom ($headroom px) must be >= expected minimum ($expectedMinHeadroom px) at density $density",
                headroom >= expectedMinHeadroom
            )
        }
    }

    // =============================================================================================
    // Challenge 2: Status Bar Inset Extremes & Negative/Zero Offsets
    // =============================================================================================

    @Test
    fun challenge2_extremeStatusBarInsets_preservesRobustBoundsWithoutUnderflow() {
        val density = 2.625f
        val haloRadiusDp = 52f
        val bloomPaddingDp = 16f
        val haloRadiusPx = haloRadiusDp * density     // 136.5 px
        val bloomPaddingPx = bloomPaddingDp * density // 42.0 px
        val centerYPx = 66f

        val requiredHeight = (centerYPx + haloRadiusPx + bloomPaddingPx).toInt() // 244 px

        val testInsets = listOf(-500, -100, -1, 0, 1, 50, 132, 200, 244, 300, 500, 1000)

        for (inset in testInsets) {
            val geometry = CutoutGeometry(
                centerXPx = 540f,
                centerYPx = centerYPx,
                cutoutDiameterPx = 132f,
                statusBarHeightPx = inset,
                isCutoutDetected = true
            )

            val overlayHeight = CutoutGeometryResolver.calculateOverlayHeightPx(
                geometry = geometry,
                haloRadiusDp = haloRadiusDp,
                bloomPaddingDp = bloomPaddingDp,
                density = density
            )

            // When inset <= requiredHeight, height must be exactly requiredHeight
            if (inset <= requiredHeight) {
                assertEquals(
                    "When statusBarTopInset ($inset) <= requiredHeight ($requiredHeight), overlayHeight must be requiredHeight",
                    requiredHeight,
                    overlayHeight
                )
            } else {
                // When inset > requiredHeight, height must expand to accommodate large status bar
                assertEquals(
                    "When statusBarTopInset ($inset) > requiredHeight ($requiredHeight), overlayHeight must equal inset",
                    inset,
                    overlayHeight
                )
            }

            assertTrue("Overlay height must always be positive", overlayHeight > 0)
        }
    }

    @Test
    fun challenge2_zeroAndNegativeCenterY_evaluatesDeterministically() {
        val density = 2.625f
        val requiredHaloAndBloom = (52f * density + 16f * density).toInt() // 178 px

        // Center Y = 0 (cutout centered on screen top edge)
        val geometryZeroCenter = CutoutGeometry(
            centerXPx = 540f,
            centerYPx = 0f,
            cutoutDiameterPx = 100f,
            statusBarHeightPx = 100,
            isCutoutDetected = true
        )
        val heightZeroCenter = CutoutGeometryResolver.calculateOverlayHeightPx(geometryZeroCenter, density = density)
        assertEquals("When centerY = 0, height must equal halo + bloom (178px)", requiredHaloAndBloom, heightZeroCenter)

        // Center Y negative (transient offset or unusual coordinate system)
        val geometryNegativeCenter = CutoutGeometry(
            centerXPx = 540f,
            centerYPx = -30f,
            cutoutDiameterPx = 100f,
            statusBarHeightPx = 100,
            isCutoutDetected = true
        )
        val heightNegativeCenter = CutoutGeometryResolver.calculateOverlayHeightPx(geometryNegativeCenter, density = density)
        val expectedHeight = ( -30f + 136.5f + 42f ).toInt() // 148 px
        assertEquals("Negative center Y must subtract cleanly without arithmetic overflow", expectedHeight, heightNegativeCenter)
    }

    // =============================================================================================
    // Challenge 3: Exotic Hardware Cutout Geometries
    // =============================================================================================

    @Test
    fun challenge3_exoticCutouts_correctCenterAndHeightResolution() {
        data class ExoticScenario(
            val name: String,
            val bounds: Rect,
            val statusBarInset: Int,
            val displayWidthPx: Int,
            val density: Float,
            val expectedCenterX: Float,
            val expectedCenterY: Float
        )

        val scenarios = listOf(
            // Left corner punch-hole (Pixel 5, OnePlus Nord)
            ExoticScenario("Left Corner", createRect(36, 0, 132, 96), 96, 1080, 2.625f, 84.0f, 48.0f),
            // Right corner punch-hole (Galaxy S10)
            ExoticScenario("Right Corner", createRect(940, 0, 1040, 100), 100, 1080, 2.625f, 990.0f, 50.0f),
            // Floating Dynamic Island / Pill (not touching top border: top = 16, bottom = 116)
            ExoticScenario("Floating Pill", createRect(400, 16, 680, 116), 116, 1080, 2.625f, 540.0f, 66.0f),
            // Deep waterdrop notch (height = 160px)
            ExoticScenario("Deep Waterdrop", createRect(490, 0, 590, 160), 160, 1080, 2.625f, 540.0f, 80.0f),
            // Asymmetric rectangular notch
            ExoticScenario("Asymmetric Rect", createRect(200, 0, 880, 90), 90, 1080, 2.625f, 540.0f, 45.0f)
        )

        for (s in scenarios) {
            val geometry = CutoutGeometryResolver.resolve(
                boundingRectTop = s.bounds,
                statusBarTopInset = s.statusBarInset,
                displayWidthPx = s.displayWidthPx,
                density = s.density
            )

            assertTrue("[${s.name}] Cutout must be detected", geometry.isCutoutDetected)
            assertEquals("[${s.name}] Center X mismatch", s.expectedCenterX, geometry.centerXPx, tolerance)
            assertEquals("[${s.name}] Center Y mismatch", s.expectedCenterY, geometry.centerYPx, tolerance)

            val overlayHeight = CutoutGeometryResolver.calculateOverlayHeightPx(geometry, density = s.density)
            val ring1Bottom = geometry.centerYPx + (52f * s.density)
            assertTrue(
                "[${s.name}] Overlay height ($overlayHeight px) must exceed Ring 1 bottom ($ring1Bottom px)",
                overlayHeight > ring1Bottom
            )
        }
    }

    // =============================================================================================
    // Challenge 4: Exhaustive 3D Perspective Projection Clipping Oracle
    // =============================================================================================

    @Test
    fun challenge4_exhaustive3DPerspectiveProjection_ring1AndGlowMathematicallyUnclipped() {
        val testDensities = listOf(1.0f, 1.5f, 2.0f, 2.625f, 3.0f, 3.5f, 4.0f)
        val cameraDistance = 500f
        val haloRadiusDp = 52f
        val bloomPaddingDp = 16f

        // Sample 128 circumference points and 72 rotation angles per ring
        val thetaSteps = 128
        val angleSteps = 72

        for (density in testDensities) {
            val baseRadius = haloRadiusDp * density
            val strokeScale = (baseRadius / 100f).coerceIn(0.25f, 1.0f)
            // Outer glow stroke width in pixels
            val outerGlowStrokePx = (4.0f * strokeScale).coerceAtLeast(1.8f) * density
            val halfGlowStrokePx = outerGlowStrokePx / 2f

            val geometry = CutoutGeometry(
                centerXPx = 540f,
                centerYPx = 25.14f * density, // representative center Y
                cutoutDiameterPx = 50.28f * density,
                statusBarHeightPx = (50.28f * density).toInt(),
                isCutoutDetected = true
            )

            val overlayHeightPx = CutoutGeometryResolver.calculateOverlayHeightPx(
                geometry = geometry,
                haloRadiusDp = haloRadiusDp,
                bloomPaddingDp = bloomPaddingDp,
                density = density
            )

            // Test all 5 concentric rings under genuine 3D perspective projection
            // Scale factors from CameraHaloRingsRenderer:
            val ringConfigs = listOf(
                // Ring 1 (CPU): radius = 1.00 R_0, rotX = 0.26f, rotY = angle, rotZ = 0
                Triple(1.00f, "Ring 1 (CPU)", { a: Float -> Triple(0.26f, a, 0f) }),
                // Ring 2 (RAM): radius = 0.85 R_0, rotX = angle, rotY = 0.26f, rotZ = 0.15f
                Triple(0.85f, "Ring 2 (RAM)", { a: Float -> Triple(a, 0.26f, 0.15f) }),
                // Ring 3 (Net): radius = 0.70 R_0, rotX = a*0.707, rotY = a*0.707, rotZ = 0.785f
                Triple(0.70f, "Ring 3 (Net)", { a: Float -> Triple(a * 0.707f, a * 0.707f, 0.785f) }),
                // Ring 4 (SSD): radius = 0.55 R_0, rotX = a*0.707, rotY = -a*0.707, rotZ = -0.785f
                Triple(0.55f, "Ring 4 (SSD)", { a: Float -> Triple(a * 0.707f, -a * 0.707f, -0.785f) }),
                // Ring 5 (GPU): radius = 0.40 R_0, rotX = sin(a*0.5)*0.09, rotY = cos(a*0.5)*0.09, rotZ = a
                Triple(0.40f, "Ring 5 (GPU)", { a: Float -> Triple(sin(a * 0.5f) * 0.09f, cos(a * 0.5f) * 0.09f, a) })
            )

            var globalMaxY = Float.MIN_VALUE
            var worstRingName = ""

            for ((ringScale, ringName, angleFunc) in ringConfigs) {
                val ringRadius = baseRadius * ringScale

                for (aIdx in 0..angleSteps) {
                    val angle = (aIdx.toFloat() / angleSteps.toFloat()) * 2f * PI.toFloat()
                    val (rotX, rotY, rotZ) = angleFunc(angle)

                    for (tIdx in 0..thetaSteps) {
                        val theta = (tIdx.toFloat() / thetaSteps.toFloat()) * 2f * PI.toFloat()
                        val localPoint = Point3D(
                            x = ringRadius * cos(theta),
                            y = ringRadius * sin(theta),
                            z = 0f
                        )

                        val rotated = PerspectiveProjection3D.rotate3D(localPoint, rotX, rotY, rotZ)
                        val projected = PerspectiveProjection3D.projectTo2D(
                            point = rotated,
                            center = Offset(geometry.centerXPx, geometry.centerYPx),
                            cameraDistance = cameraDistance
                        )

                        val bottomEdgeWithGlow = projected.y + halfGlowStrokePx

                        if (bottomEdgeWithGlow > globalMaxY) {
                            globalMaxY = bottomEdgeWithGlow
                            worstRingName = ringName
                        }

                        // INVARIANT: Every projected point edge must fall strictly within the window overlay height
                        assertTrue(
                            "[$ringName @ density=$density] Point bottom edge ($bottomEdgeWithGlow px) must not exceed overlayHeight ($overlayHeightPx px)",
                            bottomEdgeWithGlow < overlayHeightPx.toFloat()
                        )
                    }
                }
            }

            val marginPx = overlayHeightPx - globalMaxY
            val marginDp = marginPx / density

            println("[Density $density] Max bottom Y = ${String.format("%.2f", globalMaxY)} px ($worstRingName) vs Window Height = $overlayHeightPx px -> Clearance Headroom = ${String.format("%.2f", marginPx)} px (${String.format("%.2f", marginDp)} dp)")

            // Headroom must be strictly positive and substantial (>= 8dp at all densities)
            assertTrue(
                "Clearance headroom (${marginDp}dp) must be at least 8dp across all 3D rotations",
                marginDp >= 8.0f
            )
        }
    }

    // =============================================================================================
    // Challenge 5: Meltdown Corona Aura Bounding Bounds
    // =============================================================================================

    @Test
    fun challenge5_meltdownCoronaAura_unclippedUnderMaximumPulse() {
        val testDensities = listOf(1.0f, 2.0f, 2.625f, 3.0f, 4.0f)
        val haloRadiusDp = 52f
        val pulseTickMax = 1.25f // from CameraHaloRingsRenderer.kt line 61

        for (density in testDensities) {
            val baseRadiusPx = haloRadiusDp * density
            val coronaRadius = maxOf(baseRadiusPx * 0.45f, 22f * density) * pulseTickMax
            val radialGradientRadiusPx = coronaRadius * 1.6f

            val geometry = CutoutGeometry(
                centerXPx = 540f,
                centerYPx = 25.14f * density,
                cutoutDiameterPx = 50.28f * density,
                statusBarHeightPx = (50.28f * density).toInt(),
                isCutoutDetected = true
            )

            val overlayHeightPx = CutoutGeometryResolver.calculateOverlayHeightPx(geometry, density = density)
            val maxCoronaY = geometry.centerYPx + radialGradientRadiusPx

            assertTrue(
                "Meltdown corona aura bottom ($maxCoronaY px) must fall strictly inside overlay window ($overlayHeightPx px)",
                maxCoronaY < overlayHeightPx
            )

            val coronaHeadroomPx = overlayHeightPx - maxCoronaY
            assertTrue("Corona headroom must be strictly positive", coronaHeadroomPx > 0f)
        }
    }

    // =============================================================================================
    // Challenge 6: Ring 5 Hardware Camera Aperture Encirclement & Multi-Pass Stroke Clearance
    // =============================================================================================

    @Test
    fun challenge6_ring5ApertureEncirclement_Pixel8AndPixel11Pro() {
        val haloRadiusDp = 52.0f
        val ring5RadiusDp = haloRadiusDp * 0.40f // 20.8 dp

        // Physical camera lens radius on hardware:
        val pixel8LensRadiusDp = 18.0f   // 36dp diameter
        val pixel11ProLensRadiusDp = 20.0f // 40dp diameter

        // Ring 5 inner core stroke width
        val density = 2.625f
        val baseRadiusPx = haloRadiusDp * density
        val strokeScale = (baseRadiusPx / 100f).coerceIn(0.25f, 1.0f)
        val innerCoreStrokeDp = (1.0f * strokeScale).coerceAtLeast(0.6f)
        val ring5InnerEdgeDp = ring5RadiusDp - (innerCoreStrokeDp / 2f)

        // 1. Invariant: Ring 5 radius strictly exceeds physical lens on Pixel 8
        assertTrue(
            "Ring 5 radius ($ring5RadiusDp dp) must exceed Pixel 8 camera lens ($pixel8LensRadiusDp dp)",
            ring5RadiusDp > pixel8LensRadiusDp
        )
        // 2. Invariant: Ring 5 inner stroke edge clears Pixel 8 lens
        assertTrue(
            "Ring 5 inner stroke edge ($ring5InnerEdgeDp dp) must clear Pixel 8 lens ($pixel8LensRadiusDp dp)",
            ring5InnerEdgeDp > pixel8LensRadiusDp
        )

        // 3. Invariant: Ring 5 radius strictly exceeds physical lens on Pixel 11 Pro
        assertTrue(
            "Ring 5 radius ($ring5RadiusDp dp) must exceed Pixel 11 Pro camera lens ($pixel11ProLensRadiusDp dp)",
            ring5RadiusDp > pixel11ProLensRadiusDp
        )
        // 4. Invariant: Ring 5 inner stroke edge clears Pixel 11 Pro lens
        assertTrue(
            "Ring 5 inner stroke edge ($ring5InnerEdgeDp dp) must clear Pixel 11 Pro lens ($pixel11ProLensRadiusDp dp)",
            ring5InnerEdgeDp > pixel11ProLensRadiusDp
        )

        // 5. Invariant: Monotonic ring hierarchy (0.40, 0.55, 0.70, 0.85, 1.00)
        val ringScales = listOf(1.00f, 0.85f, 0.70f, 0.55f, 0.40f)
        val interRingGapDp = 0.15f * haloRadiusDp // 7.8 dp

        for (i in 0 until ringScales.size - 1) {
            val outerDp = ringScales[i] * haloRadiusDp
            val innerDp = ringScales[i + 1] * haloRadiusDp
            val gap = outerDp - innerDp
            assertEquals("Uniform radial gap between ring $i and ring ${i + 1}", interRingGapDp, gap, tolerance)
        }
    }

    // =============================================================================================
    // Challenge 7: Halo Radius Parameter Sensitivity & Upper Breakdown Boundary
    // =============================================================================================

    @Test
    fun challenge7_haloRadiusParameterSensitivity_demonstratesSafeOperatingRegime() {
        val density = 2.625f
        val bloomPaddingDp = 16f
        val centerYPx = 66f
        val cameraDistance = 500f

        // Test halo radii from 30dp up to 90dp
        for (rDp in 30..90 step 5) {
            val haloRadiusDp = rDp.toFloat()
            val baseRadiusPx = haloRadiusDp * density

            val geometry = CutoutGeometry(
                centerXPx = 540f,
                centerYPx = centerYPx,
                cutoutDiameterPx = 132f,
                statusBarHeightPx = 132,
                isCutoutDetected = true
            )

            val overlayHeight = CutoutGeometryResolver.calculateOverlayHeightPx(
                geometry = geometry,
                haloRadiusDp = haloRadiusDp,
                bloomPaddingDp = bloomPaddingDp,
                density = density
            )

            // Calculate worst-case 3D projected bottom of Ring 1 (rotX = 0.26, rotY = PI, theta = PI/2)
            val rotY = PI.toFloat()
            val theta = (PI / 2).toFloat()
            val local = Point3D(baseRadiusPx * cos(theta), baseRadiusPx * sin(theta), 0f)
            val rotated = PerspectiveProjection3D.rotate3D(local, 0.26f, rotY, 0f)
            val projected = PerspectiveProjection3D.projectTo2D(rotated, Offset(540f, centerYPx), cameraDistance = cameraDistance)

            val strokeScale = (baseRadiusPx / 100f).coerceIn(0.25f, 1.0f)
            val outerGlowWidthPx = (4.0f * strokeScale).coerceAtLeast(1.8f) * density
            val worstCaseBottom = projected.y + outerGlowWidthPx / 2f

            val margin = overlayHeight - worstCaseBottom
            println("Radius ${rDp}dp: windowHeight=${overlayHeight}px, worstBottom=${String.format("%.2f", worstCaseBottom)}px, margin=${String.format("%.2f", margin)}px")

            // For the production default 52dp, margin must be positive and >= 30px
            if (rDp == 50 || rDp == 55) {
                assertTrue("Default halo regime (~52dp) must have >= 30px margin", margin >= 30f)
            }
        }
    }
}
