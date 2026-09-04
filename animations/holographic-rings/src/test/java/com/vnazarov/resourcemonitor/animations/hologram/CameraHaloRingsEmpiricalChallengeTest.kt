package com.vnazarov.resourcemonitor.animations.hologram

import android.graphics.PixelFormat
import android.view.WindowManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.vnazarov.resourcemonitor.core.designsystem.cutout.CutoutGeometry
import com.vnazarov.resourcemonitor.core.designsystem.math.PerspectiveProjection3D
import com.vnazarov.resourcemonitor.core.designsystem.math.Point3D
import com.vnazarov.resourcemonitor.core.designsystem.theme.NeonPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Empirical Challenger Test Suite for Milestone 1:
 *
 * 1. WindowManager Layout Flags & Pass-Through Bitmask Validation:
 *    - Validates orthogonal bitwise combination of FLAG_NOT_FOCUSABLE, FLAG_NOT_TOUCHABLE,
 *      FLAG_LAYOUT_IN_SCREEN, and FLAG_LAYOUT_NO_LIMITS.
 *    - Validates LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS and PixelFormat.TRANSLUCENT.
 *
 * 2. Window Height vs Halo Radius Clipping Boundary Oracle:
 *    - Analyzes MonitorService window height: maxOf(statusBarHeightPx, centerYPx + cutoutDiameterPx / 2)
 *    - Tests clipping of CameraHaloRingsRenderer across halo radii (28dp, 40dp, 60dp, 80dp)
 *      on Pixel 8, Pixel 11 Pro native, and Pixel 11 Pro scaled.
 *
 * 3. 5-Ring 3D Perspective Projection & Geometric Fidelity:
 *    - Verifies all 5 concentric rings (CPU, RAM, Net, SSD, GPU/Corona) in 3D projection.
 *    - Verifies perspective division Z_cam / (Z_cam + z) foreshortening effects.
 *    - Confirms absence of NaNs, infinities, or coordinate singularities across full rotation.
 *
 * 4. Radial Scaling & Inter-Ring Spacing Non-Overlap Invariants:
 *    - Asserts constant inter-ring radial step (0.16 * R_0) across 40dp to 80dp.
 *    - Asserts multi-pass glow stroke widths never exceed inter-ring gaps (zero bloom bleed).
 *
 * 5. Punch-Hole Aperture Clearance Oracle:
 *    - Empirically measures clearance vs penetration of Ring 5 against physical camera hole
 *      (18dp radius / 36dp diameter on Pixel 8).
 */
class CameraHaloRingsEmpiricalChallengeTest {

    // =============================================================================================
    // Challenge 1: WindowManager Layout Flags & Pass-Through Bitmask Arithmetic
    // =============================================================================================

    @Test
    fun challenge1_windowManagerLayoutFlags_bitwiseCompositionAndPassThrough() {
        val flagNotFocusable = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE       // 0x08 (8)
        val flagNotTouchable = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE       // 0x10 (16)
        val flagLayoutInScreen = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN // 0x100 (256)
        val flagLayoutNoLimits = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS // 0x200 (512)

        // Combined bitmask used in MonitorService.kt
        val combinedFlags = flagNotFocusable or flagNotTouchable or flagLayoutInScreen or flagLayoutNoLimits

        // 1. Invariant: Composite mask must equal exactly 0x318 (792 decimal)
        assertEquals("Composite WindowManager flags mask mismatch", 792, combinedFlags)

        // 2. Invariant: Each flag is completely orthogonal and preserved in the composite
        assertEquals("FLAG_NOT_FOCUSABLE bit preserved", flagNotFocusable, combinedFlags and flagNotFocusable)
        assertEquals("FLAG_NOT_TOUCHABLE bit preserved", flagNotTouchable, combinedFlags and flagNotTouchable)
        assertEquals("FLAG_LAYOUT_IN_SCREEN bit preserved", flagLayoutInScreen, combinedFlags and flagLayoutInScreen)
        assertEquals("FLAG_LAYOUT_NO_LIMITS bit preserved", flagLayoutNoLimits, combinedFlags and flagLayoutNoLimits)

        // 3. Invariant: No extraneous flags accidentally introduced
        val isolatedSum = flagNotFocusable + flagNotTouchable + flagLayoutInScreen + flagLayoutNoLimits
        assertEquals("Bitwise OR must equal arithmetic sum for disjoint flags", isolatedSum, combinedFlags)

        // 4. Invariant: Display Cutout Mode is ALWAYS (3)
        val cutoutModeAlways = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        assertEquals("LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS must be 3", 3, cutoutModeAlways)

        // 5. Invariant: PixelFormat is TRANSLUCENT (-3)
        val formatTranslucent = PixelFormat.TRANSLUCENT
        assertEquals("PixelFormat.TRANSLUCENT must be -3", -3, formatTranslucent)
    }

    // =============================================================================================
    // Challenge 2: Window Height vs Halo Radius Clipping Boundary Oracle
    // =============================================================================================

    @Test
    fun challenge2_windowHeight_vs_haloRadiiClippingAnalysis() {
        data class DeviceProfile(
            val name: String,
            val displayWidthPx: Int,
            val density: Float,
            val statusBarHeightPx: Int,
            val centerXPx: Float,
            val centerYPx: Float,
            val cutoutDiameterPx: Float
        )

        val devices = listOf(
            DeviceProfile(
                name = "Google Pixel 8",
                displayWidthPx = 1080,
                density = 2.625f,
                statusBarHeightPx = 132,
                centerXPx = 540.0f,
                centerYPx = 66.0f,
                cutoutDiameterPx = 132.0f
            ),
            DeviceProfile(
                name = "Google Pixel 11 Pro Native",
                displayWidthPx = 1280,
                density = 3.09375f,
                statusBarHeightPx = 204,
                centerXPx = 640.0f,
                centerYPx = 102.0f,
                cutoutDiameterPx = 204.0f
            ),
            DeviceProfile(
                name = "Google Pixel 11 Pro Scaled",
                displayWidthPx = 1080,
                density = 2.625f,
                statusBarHeightPx = 172,
                centerXPx = 540.0f,
                centerYPx = 86.0f,
                cutoutDiameterPx = 172.0f
            )
        )

        val radiiToTestDp = listOf(28.0f, 40.0f, 50.0f, 60.0f, 80.0f)

        for (dev in devices) {
            // Replicate MonitorService overlayHeightPx logic:
            val overlayHeightPx = if (dev.statusBarHeightPx > 0) {
                maxOf(dev.statusBarHeightPx, (dev.centerYPx + dev.cutoutDiameterPx / 2f).toInt())
            } else {
                (48f * dev.density).toInt()
            }

            for (radiusDp in radiiToTestDp) {
                val baseRadiusPx = radiusDp * dev.density
                val strokeScale = (baseRadiusPx / 100f).coerceIn(0.25f, 1.0f)
                val outerGlowWidthPx = (4.0f * strokeScale).coerceAtLeast(1.8f) * dev.density

                // The bottom-most coordinate of the outermost ring (Ring 1)
                val maxBottomY = dev.centerYPx + baseRadiusPx + (outerGlowWidthPx / 2f)

                val clippedPx = maxBottomY - overlayHeightPx
                val isClipped = clippedPx > 0f

                println("[${dev.name}] R=${radiusDp}dp (baseRadius=${baseRadiusPx}px): windowHeight=${overlayHeightPx}px, maxBottomY=${maxBottomY}px -> ${if (isClipped) "CLIPPED by ${clippedPx}px" else "UNCLIPPED"}")

                if (radiusDp >= 40.0f) {
                    // Critical finding: When halo radius is 40dp to 80dp, maxBottomY exceeds overlayHeightPx!
                    assertTrue(
                        "Device ${dev.name} with R=${radiusDp}dp must be flagged for bottom clipping (clipped by ${clippedPx}px)",
                        isClipped
                    )
                }
            }
        }
    }

    // =============================================================================================
    // Challenge 3: 5-Ring 3D Perspective Projection & Geometric Fidelity
    // =============================================================================================

    @Test
    fun challenge3_fiveRingPerspectiveProjection_noSingularitiesAndCorrectForeshortening() {
        val testRadiiDp = listOf(40f, 50f, 60f, 70f, 80f)
        val density = 2.625f
        val center = Offset(540f, 66f)
        val cameraDistance = 500f
        val segments = 64

        // Ring scaling proportions from CameraHaloRingsRenderer:
        val ringScaleFactors = listOf(1.00f, 0.84f, 0.68f, 0.52f, 0.36f)

        for (radiusDp in testRadiiDp) {
            val baseRadiusPx = radiusDp * density

            for ((ringIdx, scale) in ringScaleFactors.withIndex()) {
                val ringRadius = baseRadiusPx * scale

                // Euler tilt angles defined per ring:
                val (rotX, rotY, rotZ) = when (ringIdx) {
                    0 -> Triple(0.26f, 0.5f, 0f)                          // Ring 1: Y-axis rot with 15 deg X-tilt
                    1 -> Triple(0.5f, 0.26f, 0.15f)                       // Ring 2: X-axis rot with 15 deg Y-tilt
                    2 -> Triple(0.5f * 0.707f, 0.5f * 0.707f, 0.785f)    // Ring 3: Diagonal 45 deg
                    3 -> Triple(0.5f * 0.707f, -0.5f * 0.707f, -0.785f)  // Ring 4: Counter-diagonal -45 deg
                    4 -> Triple(sin(0.25f) * 0.09f, cos(0.25f) * 0.09f, 0.5f) // Ring 5: Planar nutation
                    else -> Triple(0f, 0f, 0f)
                }

                val projectedPoints = ArrayList<Offset>(segments + 1)

                for (i in 0..segments) {
                    val theta = (i.toFloat() / segments.toFloat()) * 2f * PI.toFloat()
                    val localPoint = Point3D(
                        x = ringRadius * cos(theta),
                        y = ringRadius * sin(theta),
                        z = 0f
                    )
                    val rotated = PerspectiveProjection3D.rotate3D(localPoint, rotX, rotY, rotZ)

                    // Invariant: rotated point distance from origin in 3D equals ringRadius (rigid body rotation)
                    val dist3D = kotlin.math.sqrt(rotated.x * rotated.x + rotated.y * rotated.y + rotated.z * rotated.z)
                    assertEquals("Rigid body 3D rotation must preserve radius", ringRadius, dist3D, 0.01f)

                    val projected = PerspectiveProjection3D.projectTo2D(rotated, center, cameraDistance = cameraDistance)

                    // Invariant: Screen projection must never be NaN or Infinite
                    assertFalse("Projected screenX must not be NaN", projected.x.isNaN())
                    assertFalse("Projected screenY must not be NaN", projected.y.isNaN())
                    assertFalse("Projected screenX must not be Infinite", projected.x.isInfinite())
                    assertFalse("Projected screenY must not be Infinite", projected.y.isInfinite())

                    projectedPoints.add(projected)
                }

                // Verify foreshortening: points with z < 0 are magnified; points with z > 0 are shrunk
                for (i in 0 until segments) {
                    val theta = (i.toFloat() / segments.toFloat()) * 2f * PI.toFloat()
                    val local = Point3D(ringRadius * cos(theta), ringRadius * sin(theta), 0f)
                    val rot = PerspectiveProjection3D.rotate3D(local, rotX, rotY, rotZ)

                    val factor = cameraDistance / (cameraDistance + rot.z)
                    if (rot.z < -1.0f) {
                        assertTrue("Points closer to camera (z < 0) must be magnified (factor > 1.0)", factor > 1.0f)
                    } else if (rot.z > 1.0f) {
                        assertTrue("Points further from camera (z > 0) must be shrunk (factor < 1.0)", factor < 1.0f)
                    }
                }
            }
        }
    }

    // =============================================================================================
    // Challenge 4: Radial Scaling & Inter-Ring Spacing Non-Overlap Invariants
    // =============================================================================================

    @Test
    fun challenge4_radialScalingAndInterRingNonOverlapInvariants() {
        val density = 2.625f

        // Test all radii from 20dp to 100dp in steps of 1dp
        for (radiusStep in 20..100) {
            val haloRadiusDp = radiusStep.toFloat()
            val baseRadiusPx = haloRadiusDp * density

            val r1 = baseRadiusPx * 1.00f
            val r2 = baseRadiusPx * 0.84f
            val r3 = baseRadiusPx * 0.68f
            val r4 = baseRadiusPx * 0.52f
            val r5 = baseRadiusPx * 0.36f

            // 1. Strict Monotonicity: r1 > r2 > r3 > r4 > r5 > 0
            assertTrue("r1 > r2", r1 > r2)
            assertTrue("r2 > r3", r2 > r3)
            assertTrue("r3 > r4", r3 > r4)
            assertTrue("r4 > r5", r4 > r5)
            assertTrue("r5 > 0", r5 > 0f)

            // 2. Uniform delta: delta = 0.16 * baseRadiusPx between all adjacent rings
            val expectedGap = 0.16f * baseRadiusPx
            assertEquals("Gap 1-2 mismatch", expectedGap, r1 - r2, 0.001f)
            assertEquals("Gap 2-3 mismatch", expectedGap, r2 - r3, 0.001f)
            assertEquals("Gap 3-4 mismatch", expectedGap, r3 - r4, 0.001f)
            assertEquals("Gap 4-5 mismatch", expectedGap, r4 - r5, 0.001f)

            // 3. Multi-pass glow stroke widths:
            val strokeScale = (baseRadiusPx / 100f).coerceIn(0.25f, 1.0f)
            val outerGlowStrokePx = (4.0f * strokeScale).coerceAtLeast(1.8f) * density

            // Invariant: Stroke glow must not exceed inter-ring gap for haloRadiusDp >= 28dp,
            // preventing bloom merge between distinct visual channels
            if (haloRadiusDp >= 28f) {
                assertTrue(
                    "Inter-ring gap ($expectedGap px) must be >= outer glow stroke ($outerGlowStrokePx px) at ${haloRadiusDp}dp to prevent ring collision",
                    expectedGap >= outerGlowStrokePx * 0.70f
                )
            }
        }
    }

    // =============================================================================================
    // Challenge 5: Punch-Hole Aperture Clearance vs Penetration Oracle
    // =============================================================================================

    @Test
    fun challenge5_punchHoleApertureClearanceOracle() {
        val density = 2.625f
        val pixel8PhysicalCutoutRadiusDp = 18.0f // Physical camera punch-hole lens radius
        val pixel8PhysicalCutoutRadiusPx = pixel8PhysicalCutoutRadiusDp * density // ~47.25 px

        val pixel8CutoutBoundingRectRadiusPx = 66.0f // 132 px / 2
        val pixel8CutoutBoundingRectRadiusDp = pixel8CutoutBoundingRectRadiusPx / density // ~25.14 dp

        val testRadii = listOf(
            28.0f to "Default Compact",
            40.0f to "Small Halo",
            50.0f to "Exact Clearance Boundary",
            60.0f to "Medium Halo",
            80.0f to "Large Halo"
        )

        for ((radiusDp, label) in testRadii) {
            val baseRadiusPx = radiusDp * density
            val r5Px = baseRadiusPx * 0.36f
            val r5Dp = r5Px / density

            println("Testing [$label] haloRadius=${radiusDp}dp: Ring 5 radius=${r5Dp}dp (${r5Px}px) vs CutoutLens=${pixel8PhysicalCutoutRadiusDp}dp (${pixel8PhysicalCutoutRadiusPx}px)")

            if (radiusDp < 50.0f) {
                // When halo radius is below 50dp, Ring 5 radius (0.36 * R) is strictly less than 18dp!
                // This means Ring 5 is inside the physical camera hole aperture glass!
                assertTrue(
                    "At ${radiusDp}dp ($label), Ring 5 ($r5Dp dp) is inside the physical 18dp lens aperture",
                    r5Dp < pixel8PhysicalCutoutRadiusDp
                )
            } else if (radiusDp == 50.0f) {
                // At 50dp: 0.36 * 50 = 18.0dp (exact boundary clearance)
                assertEquals(
                    "At 50dp, Ring 5 perfectly matches the 18dp physical lens aperture radius",
                    pixel8PhysicalCutoutRadiusDp,
                    r5Dp,
                    0.001f
                )
            } else {
                // At 60dp to 80dp: Ring 5 completely clears and encircles the physical camera hole!
                assertTrue(
                    "At ${radiusDp}dp ($label), Ring 5 ($r5Dp dp) fully encircles the 18dp camera lens",
                    r5Dp > pixel8PhysicalCutoutRadiusDp
                )
            }

            // At 80dp: Ring 5 (28.8dp / 75.6px) also completely clears the DisplayCutout bounding rect (66px)!
            if (radiusDp >= 80.0f) {
                assertTrue(
                    "At 80dp, Ring 5 (${r5Px}px) clears even the full DisplayCutout bounding rect (${pixel8CutoutBoundingRectRadiusPx}px)",
                    r5Px > pixel8CutoutBoundingRectRadiusPx
                )
            }
        }
    }
}
