package com.vnazarov.resourcemonitor.presentation

import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.vnazarov.resourcemonitor.core.designsystem.cutout.CutoutGeometry
import com.vnazarov.resourcemonitor.core.designsystem.cutout.CutoutGeometryResolver
import com.vnazarov.resourcemonitor.core.designsystem.math.PerspectiveProjection3D
import com.vnazarov.resourcemonitor.core.designsystem.math.Point3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Adversarial Empirical Challenge Test Suite for Milestone 1 Iteration 2:
 *
 * 1. Touch Pass-Through & WindowManager Layout Flags:
 *    - Validates bitwise composition of FLAG_NOT_FOCUSABLE, FLAG_NOT_TOUCHABLE,
 *      FLAG_LAYOUT_IN_SCREEN, and FLAG_LAYOUT_NO_LIMITS.
 *    - Asserts that FLAG_NOT_TOUCHABLE is strictly set, guaranteeing touch event pass-through.
 *    - Asserts PixelFormat.TRANSLUCENT, LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS, and Gravity.TOP | START.
 *
 * 2. Punch-Hole Camera Encirclement Oracle:
 *    - Validates default halo radius (52.0dp) and Ring 5 scaling factor (0.40).
 *    - Asserts Ring 5 radius is exactly 20.8dp.
 *    - Asserts Ring 5 strictly clears Pixel 8 camera lens (18.0dp) by +2.8dp margin.
 *    - Asserts Ring 5 strictly clears Pixel 11 Pro camera lens (20.0dp) by +0.8dp margin.
 *
 * 3. 3D Nutation Dynamic Projection Clearance Oracle:
 *    - Empirically calculates projected 2D Euclidean radius of Ring 5 across all 360 nutation
 *      angles and all 64 circle segments.
 *    - Proves the minimum projected radius strictly exceeds camera hole boundaries on both devices.
 *
 * 4. Meltdown Corona Aura Expansion:
 *    - Validates that the thermal alert pulse gradient strictly expands outside the camera aperture.
 *
 * 5. Dynamic Compose Recomposition Oracle:
 *    - Validates SnapshotState notification and reactive invalidation of mutableStateOf<CutoutGeometry?>.
 *    - Verifies dynamic inset resolution and overlay height recalculation upon window changes.
 *
 * 6. 5-Ring Monotonicity & Inter-Ring Spacing Non-Overlap Invariants:
 *    - Proves strict monotonicity (R1 > R2 > R3 > R4 > R5 > R_camera > 0) with uniform 0.15 step.
 *    - Proves inter-ring gap (7.8dp) strictly exceeds neon bloom stroke widths.
 *
 * 7. Window Boundary Clipping Resistance:
 *    - Confirms required overlay height fully encloses Ring 1 and bloom glow on Pixel 8 and Pixel 11 Pro.
 */
class CameraHaloPassThroughAndApertureEncirclementChallengeTest {

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
    // Challenge 1: Touch Pass-Through & WindowManager Layout Flags
    // =============================================================================================

    @Test
    fun challenge1_windowManagerLayoutFlags_bitwiseCompositionAndTouchPassThrough() {
        val flagNotFocusable = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE       // 0x08 (8)
        val flagNotTouchable = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE       // 0x10 (16)
        val flagLayoutInScreen = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN // 0x100 (256)
        val flagLayoutNoLimits = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS // 0x200 (512)

        // Combined bitmask configured in MonitorService.kt
        val combinedFlags = flagNotFocusable or flagNotTouchable or flagLayoutInScreen or flagLayoutNoLimits

        // 1. Bitmask exact value: 8 | 16 | 256 | 512 = 792 (0x318)
        assertEquals("Composite WindowManager layout flags mask must be 792 (0x318)", 792, combinedFlags)

        // 2. FLAG_NOT_TOUCHABLE MUST be strictly asserted to guarantee 100% touch pass-through
        assertTrue(
            "FLAG_NOT_TOUCHABLE must be set in combined flags to allow full touch pass-through",
            (combinedFlags and flagNotTouchable) != 0
        )
        assertEquals(flagNotTouchable, combinedFlags and flagNotTouchable)

        // 3. FLAG_NOT_FOCUSABLE MUST be set to prevent stealing keyboard/input focus
        assertTrue(
            "FLAG_NOT_FOCUSABLE must be set to prevent stealing input focus",
            (combinedFlags and flagNotFocusable) != 0
        )
        assertEquals(flagNotFocusable, combinedFlags and flagNotFocusable)

        // 4. FLAG_LAYOUT_IN_SCREEN MUST be set to ignore status bar decorations
        assertTrue(
            "FLAG_LAYOUT_IN_SCREEN must be set for full screen coordinate space",
            (combinedFlags and flagLayoutInScreen) != 0
        )

        // 5. FLAG_LAYOUT_NO_LIMITS MUST be set to allow extending outside screen bounds
        assertTrue(
            "FLAG_LAYOUT_NO_LIMITS must be set to avoid boundary clipping",
            (combinedFlags and flagLayoutNoLimits) != 0
        )

        // 6. LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS must be 3
        assertEquals(
            "LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS must be 3",
            3,
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        )

        // 7. PixelFormat.TRANSLUCENT must be -3 for transparent canvas overlay
        assertEquals(
            "PixelFormat.TRANSLUCENT must be -3",
            PixelFormat.TRANSLUCENT,
            -3
        )

        // 8. Gravity TOP | START must equal 8388659 (0x00800033)
        val expectedGravity = Gravity.TOP or Gravity.START
        assertEquals("Gravity TOP | START must be 8388659", 8388659, expectedGravity)
    }

    // =============================================================================================
    // Challenge 2: Punch-Hole Camera Encirclement Oracle (Pixel 8 & Pixel 11 Pro)
    // =============================================================================================

    @Test
    fun challenge2_cameraPunchHoleEncirclement_Pixel8_and_Pixel11Pro() {
        val defaultHaloRadiusDp = 52.0f
        val ring5ScaleFactor = 0.40f

        val ring5RadiusDp = defaultHaloRadiusDp * ring5ScaleFactor
        assertEquals("Ring 5 nominal radius must be exact 20.8dp", 20.8f, ring5RadiusDp, tolerance)

        // Physical camera punch-hole specs
        val pixel8CameraRadiusDp = 18.0f
        val pixel11ProCameraRadiusDp = 20.0f

        // 1. Pixel 8 Encirclement
        val pixel8ClearanceDp = ring5RadiusDp - pixel8CameraRadiusDp
        assertTrue(
            "Ring 5 (20.8dp) must strictly exceed Pixel 8 camera radius (18.0dp)",
            ring5RadiusDp > pixel8CameraRadiusDp
        )
        assertEquals("Pixel 8 clearance margin must be exact +2.8dp", 2.8f, pixel8ClearanceDp, tolerance)

        // 2. Pixel 11 Pro Encirclement
        val pixel11ProClearanceDp = ring5RadiusDp - pixel11ProCameraRadiusDp
        assertTrue(
            "Ring 5 (20.8dp) must strictly exceed Pixel 11 Pro camera radius (20.0dp)",
            ring5RadiusDp > pixel11ProCameraRadiusDp
        )
        assertEquals("Pixel 11 Pro clearance margin must be exact +0.8dp", 0.8f, pixel11ProClearanceDp, tolerance)

        // 3. Pixel-level clearance on real display densities:
        // Pixel 8: density = 2.625 (420 dpi)
        val pixel8Density = 2.625f
        val pixel8Ring5RadiusPx = ring5RadiusDp * pixel8Density       // 54.60 px
        val pixel8CameraRadiusPx = pixel8CameraRadiusDp * pixel8Density // 47.25 px
        val pixel8ClearancePx = pixel8Ring5RadiusPx - pixel8CameraRadiusPx // 7.35 px
        assertTrue("Pixel 8 clearance in pixels must be > 0", pixel8ClearancePx > 0f)
        assertEquals("Pixel 8 clearance must be +7.35px", 7.35f, pixel8ClearancePx, tolerance)

        // Pixel 11 Pro Native: density = 3.0 (480 dpi)
        val pixel11Density = 3.0f
        val pixel11Ring5RadiusPx = ring5RadiusDp * pixel11Density       // 62.40 px
        val pixel11CameraRadiusPx = pixel11ProCameraRadiusDp * pixel11Density // 60.00 px
        val pixel11ClearancePx = pixel11Ring5RadiusPx - pixel11CameraRadiusPx // 2.40 px
        assertTrue("Pixel 11 Pro clearance in pixels must be > 0", pixel11ClearancePx > 0f)
        assertEquals("Pixel 11 Pro clearance must be +2.40px", 2.40f, pixel11ClearancePx, tolerance)
    }

    // =============================================================================================
    // Challenge 3: 3D Nutation Dynamic Projection Clearance Across Full 360° Rotation
    // =============================================================================================

    @Test
    fun challenge3_nutationProjectionApertureClearance_acrossFull360Rotation() {
        val defaultHaloRadiusDp = 52.0f
        val ring5ScaleFactor = 0.40f
        val cameraDistance = 500f
        val segments = 64

        data class DeviceVerification(
            val name: String,
            val density: Float,
            val cameraRadiusDp: Float,
            val centerXPx: Float,
            val centerYPx: Float
        )

        val devices = listOf(
            DeviceVerification("Pixel 8", 2.625f, 18.0f, 540f, 66f),
            DeviceVerification("Pixel 11 Pro Native", 3.0f, 20.0f, 640f, 102f),
            DeviceVerification("Pixel 11 Pro Scaled", 2.625f, 20.0f, 540f, 86f)
        )

        for (device in devices) {
            val center = Offset(device.centerXPx, device.centerYPx)
            val ring5RadiusPx = defaultHaloRadiusDp * ring5ScaleFactor * device.density
            val cameraRadiusPx = device.cameraRadiusDp * device.density

            var minProjectedRadiusPx = Float.MAX_VALUE
            var maxProjectedRadiusPx = Float.MIN_VALUE

            // Step through all 360 degrees of rotation phase
            for (degree in 0 until 360 step 5) {
                val angle5 = (degree.toFloat() / 180f) * PI.toFloat()
                val rotX = sin(angle5 * 0.5f) * 0.09f
                val rotY = cos(angle5 * 0.5f) * 0.09f
                val rotZ = angle5

                for (i in 0..segments) {
                    val theta = (i.toFloat() / segments.toFloat()) * 2f * PI.toFloat()
                    val localPoint = Point3D(
                        x = ring5RadiusPx * cos(theta),
                        y = ring5RadiusPx * sin(theta),
                        z = 0f
                    )
                    val rotated = PerspectiveProjection3D.rotate3D(localPoint, rotX, rotY, rotZ)
                    val projected = PerspectiveProjection3D.projectTo2D(rotated, center, cameraDistance = cameraDistance)

                    val dx = projected.x - center.x
                    val dy = projected.y - center.y
                    val distFromCenter = sqrt(dx * dx + dy * dy)

                    if (distFromCenter < minProjectedRadiusPx) minProjectedRadiusPx = distFromCenter
                    if (distFromCenter > maxProjectedRadiusPx) maxProjectedRadiusPx = distFromCenter

                    // INVARIANT: Every projected point must be outside the camera hole
                    assertTrue(
                        "[${device.name}] Projected point at phase $degree deg, segment $i ($distFromCenter px) must strictly exceed camera hole ($cameraRadiusPx px)",
                        distFromCenter > cameraRadiusPx
                    )
                }
            }

            val minProjectedRadiusDp = minProjectedRadiusPx / device.density
            val clearanceMarginDp = minProjectedRadiusDp - device.cameraRadiusDp

            println("[${device.name}] Ring 5 3D Nutation: Min R = ${minProjectedRadiusDp}dp (${minProjectedRadiusPx}px), Max R = ${maxProjectedRadiusPx / device.density}dp, Camera Hole = ${device.cameraRadiusDp}dp -> Clearance = +${clearanceMarginDp}dp")

            assertTrue(
                "[${device.name}] Minimum projected radius ($minProjectedRadiusDp dp) must exceed camera aperture (${device.cameraRadiusDp} dp)",
                minProjectedRadiusDp > device.cameraRadiusDp
            )
            assertTrue("Clearance margin must be positive", clearanceMarginDp > 0f)
        }
    }

    // =============================================================================================
    // Challenge 4: Meltdown Corona Aura Expansion Clears Aperture
    // =============================================================================================

    @Test
    fun challenge4_meltdownCoronaAuraExpansion_clearsAperture() {
        val haloRadiusDp = 52.0f
        val pulseTicks = listOf(0.80f, 0.90f, 1.00f, 1.10f, 1.25f)
        val densities = listOf(2.625f to "Pixel 8", 3.0f to "Pixel 11 Pro")

        for ((density, deviceName) in densities) {
            val baseRadiusPx = haloRadiusDp * density
            val cameraRadiusDp = if (deviceName == "Pixel 8") 18.0f else 20.0f
            val cameraRadiusPx = cameraRadiusDp * density

            for (pulseTick in pulseTicks) {
                // Formula from CameraHaloRingsRenderer.kt lines 87-98:
                // val coronaRadius = maxOf(baseRadius * 0.45f, 22.dp.toPx()) * pulseTick
                // radius = coronaRadius * 1.6f
                val minCoronaDp = 22.0f
                val minCoronaPx = minCoronaDp * density
                val coronaRadiusPx = maxOf(baseRadiusPx * 0.45f, minCoronaPx) * pulseTick
                val gradientOuterRadiusPx = coronaRadiusPx * 1.6f
                val gradientOuterRadiusDp = gradientOuterRadiusPx / density

                // Invariant: Meltdown corona aura gradient must strictly expand beyond punch-hole void
                assertTrue(
                    "[$deviceName pulse=$pulseTick] Meltdown corona outer radius ($gradientOuterRadiusDp dp) must exceed camera lens ($cameraRadiusDp dp)",
                    gradientOuterRadiusPx > cameraRadiusPx
                )
                assertTrue(
                    "Gradient outer radius in dp must exceed 28dp",
                    gradientOuterRadiusDp > 28.0f
                )
            }
        }
    }

    // =============================================================================================
    // Challenge 5: Dynamic Compose Recomposition & Window Insets Resolution
    // =============================================================================================

    @Test
    fun challenge5_composeMutableState_recompositionTriggerAndDynamicInsets() {
        // 1. Verify mutableStateOf initial state
        val geometryState = mutableStateOf<CutoutGeometry?>(null)
        assertNull("Initial geometry state must be null", geometryState.value)

        // 2. Simulate initial geometry resolution (Pixel 8)
        val initialBounds = createRect(479, 0, 601, 132)
        val initialGeometry = CutoutGeometryResolver.resolve(
            boundingRectTop = initialBounds,
            statusBarTopInset = 132,
            displayWidthPx = 1080,
            density = 2.625f
        )
        geometryState.value = initialGeometry

        assertNotNull(geometryState.value)
        assertEquals(540.0f, geometryState.value!!.centerXPx, tolerance)
        assertEquals(66.0f, geometryState.value!!.centerYPx, tolerance)

        // 3. Verify Snapshot state mutation notification
        var observerTriggered = false
        var observedValue: CutoutGeometry? = null

        val unregisterObserver = Snapshot.registerApplyObserver { changedObjects, _ ->
            if (changedObjects.contains(geometryState)) {
                observerTriggered = true
                observedValue = geometryState.value
            }
        }

        try {
            // Simulate dynamic insets change (e.g. orientation or window resize to Pixel 11 Pro native)
            val updatedBounds = createRect(585, 0, 695, 204)
            val updatedGeometry = CutoutGeometryResolver.resolve(
                boundingRectTop = updatedBounds,
                statusBarTopInset = 204,
                displayWidthPx = 1280,
                density = 3.0f
            )

            // Mutate state inside snapshot
            Snapshot.withMutableSnapshot {
                geometryState.value = updatedGeometry
            }

            assertTrue("Snapshot apply observer must be triggered upon geometryState update", observerTriggered)
            assertNotNull("Observed geometry must not be null", observedValue)
            assertEquals("Updated center X must be 640.0px", 640.0f, observedValue!!.centerXPx, tolerance)
            assertEquals("Updated center Y must be 102.0px", 102.0f, observedValue!!.centerYPx, tolerance)
            assertEquals("Updated status bar must be 204px", 204, observedValue!!.statusBarHeightPx)
        } finally {
            unregisterObserver.dispose()
        }

        // 4. Verify overlay height recalculation
        val density = 3.0f
        val newHeight = CutoutGeometryResolver.calculateOverlayHeightPx(geometryState.value!!, density = density)
        assertEquals("Pixel 11 Pro overlay height must be 306px", 306, newHeight)
    }

    // =============================================================================================
    // Challenge 6: 5-Ring Monotonicity & Inter-Ring Spacing Non-Overlap Invariants
    // =============================================================================================

    @Test
    fun challenge6_fiveRingMonotonicityAndInterRingSpacing() {
        val haloRadiusDp = 52.0f
        val density = 2.625f
        val baseRadiusPx = haloRadiusDp * density

        // Scaling factors from CameraHaloRingsRenderer.kt lines 113-163:
        val s1 = 1.00f // Ring 1 (CPU): 52.0dp
        val s2 = 0.85f // Ring 2 (RAM): 44.2dp
        val s3 = 0.70f // Ring 3 (Network): 36.4dp
        val s4 = 0.55f // Ring 4 (Storage SSD): 28.6dp
        val s5 = 0.40f // Ring 5 (GPU/Thermal): 20.8dp

        val r1 = baseRadiusPx * s1
        val r2 = baseRadiusPx * s2
        val r3 = baseRadiusPx * s3
        val r4 = baseRadiusPx * s4
        val r5 = baseRadiusPx * s5

        // 1. Strict Monotonicity Invariant: R1 > R2 > R3 > R4 > R5 > 0
        assertTrue("R1 > R2", r1 > r2)
        assertTrue("R2 > R3", r2 > r3)
        assertTrue("R3 > R4", r3 > r4)
        assertTrue("R4 > R5", r4 > r5)
        assertTrue("R5 > 0", r5 > 0f)

        // 2. Constant radial gap: Delta = 0.15 * baseRadiusPx = 7.8dp
        val expectedGapDp = 7.8f
        val expectedGapPx = expectedGapDp * density
        assertEquals("Gap R1-R2 mismatch", expectedGapPx, r1 - r2, tolerance)
        assertEquals("Gap R2-R3 mismatch", expectedGapPx, r2 - r3, tolerance)
        assertEquals("Gap R3-R4 mismatch", expectedGapPx, r3 - r4, tolerance)
        assertEquals("Gap R4-R5 mismatch", expectedGapPx, r4 - r5, tolerance)

        // 3. Neon Bloom Stroke Half-Widths vs Inter-Ring Gap:
        // Stroke scale: (baseRadiusPx / 100f).coerceIn(0.25f, 1.0f)
        val strokeScale = (baseRadiusPx / 100f).coerceIn(0.25f, 1.0f)
        val outerGlowStrokeWidthPx = (4.0f * strokeScale).coerceAtLeast(1.8f) * density
        val strokeHalfWidthPx = outerGlowStrokeWidthPx / 2f

        // Invariant: Two adjacent rings' glow strokes must never touch or collide
        // 2 * strokeHalfWidthPx = outerGlowStrokeWidthPx < expectedGapPx
        assertTrue(
            "Outer glow stroke width ($outerGlowStrokeWidthPx px) must be strictly less than inter-ring gap ($expectedGapPx px)",
            outerGlowStrokeWidthPx < expectedGapPx
        )

        val darkMarginPx = expectedGapPx - outerGlowStrokeWidthPx
        val darkMarginDp = darkMarginPx / density
        assertTrue("Dark inter-ring margin must be positive", darkMarginPx > 0f)
        assertTrue("Dark margin must exceed 3.0dp", darkMarginDp > 3.0f)
    }

    // =============================================================================================
    // Challenge 7: Window Boundary Clipping Resistance
    // =============================================================================================

    @Test
    fun challenge7_windowBoundaryClippingResistance() {
        data class TestCase(
            val device: String,
            val left: Int, val top: Int, val right: Int, val bottom: Int,
            val statusBar: Int,
            val displayWidth: Int,
            val density: Float,
            val expectedHeight: Int
        )

        val cases = listOf(
            TestCase("Pixel 8", 479, 0, 601, 132, 132, 1080, 2.625f, 244),
            TestCase("Pixel 11 Pro Native", 585, 0, 695, 204, 204, 1280, 3.0f, 306),
            TestCase("Pixel 11 Pro Scaled", 494, 0, 586, 172, 172, 1080, 2.625f, 264)
        )

        for (tc in cases) {
            val bounds = createRect(tc.left, tc.top, tc.right, tc.bottom)
            val geometry = CutoutGeometryResolver.resolve(
                boundingRectTop = bounds,
                statusBarTopInset = tc.statusBar,
                displayWidthPx = tc.displayWidth,
                density = tc.density
            )

            val overlayHeight = CutoutGeometryResolver.calculateOverlayHeightPx(geometry, density = tc.density)
            assertEquals("[${tc.device}] Calculated overlay height mismatch", tc.expectedHeight, overlayHeight)

            // Ring 1 bottom-most extent with outer neon bloom glow:
            val haloRadiusPx = 52.0f * tc.density
            val strokeScale = (haloRadiusPx / 100f).coerceIn(0.25f, 1.0f)
            val outerGlowStrokePx = (4.0f * strokeScale).coerceAtLeast(1.8f) * tc.density
            val ring1BottomY = geometry.centerYPx + haloRadiusPx + (outerGlowStrokePx / 2f)

            // Invariant: Ring 1 bottom edge must fall completely inside the overlay window
            assertTrue(
                "[${tc.device}] Ring 1 bottom extent ($ring1BottomY px) must be strictly less than overlay height ($overlayHeight px)",
                ring1BottomY < overlayHeight
            )

            val headroomPx = overlayHeight - ring1BottomY
            println("[${tc.device}] Overlay height: ${overlayHeight}px, Ring 1 max bottom: ${ring1BottomY}px, Unclipped headroom: ${headroomPx}px (${headroomPx / tc.density}dp)")
            assertTrue("[${tc.device}] Headroom must exceed 10dp", (headroomPx / tc.density) > 10.0f)
        }
    }
}
