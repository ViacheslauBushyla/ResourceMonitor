package com.vnazarov.resourcemonitor.core.designsystem.cutout

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Empirical adversarial stress test harness for [CutoutGeometryResolver].
 *
 * Challenges spatial allocation, coordinate calculation, density scaling,
 * degenerate inputs, boundary conditions, and exotic display form factors.
 */
class CutoutGeometryResolverStressTest {

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
    // Scenario 1: Ultra-Wide Cutouts (Pill, Dynamic Island, Full Status Bezel)
    // =============================================================================================

    @Test
    fun testUltraWidePillCutout_StrictlyFinitePositiveNonNaN() {
        // Cutout width = 880px, height = 120px on 1080px display
        val ultraWideBounds = createRect(100, 0, 980, 120)
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = ultraWideBounds,
            statusBarTopInset = 120,
            displayWidthPx = 1080,
            density = 2.625f
        )

        assertEquals("Center X must be midpoint (540px)", 540.0f, geometry.centerXPx, tolerance)
        assertEquals("Center Y must be midpoint (60px)", 60.0f, geometry.centerYPx, tolerance)
        assertEquals("Cutout diameter must equal maximum dimension (880px)", 880.0f, geometry.cutoutDiameterPx, tolerance)
        assertEquals("Status bar height must equal inset (120px)", 120, geometry.statusBarHeightPx)
        assertTrue("Hardware cutout must be detected", geometry.isCutoutDetected)

        assertTrue("Center X must be positive", geometry.centerXPx > 0f)
        assertTrue("Center Y must be positive", geometry.centerYPx > 0f)
        assertTrue("Diameter must be positive", geometry.cutoutDiameterPx > 0f)
        assertTrue("Center X must be finite", geometry.centerXPx.isFinite() && !geometry.centerXPx.isNaN())
        assertTrue("Center Y must be finite", geometry.centerYPx.isFinite() && !geometry.centerYPx.isNaN())
        assertTrue("Diameter must be finite", geometry.cutoutDiameterPx.isFinite() && !geometry.cutoutDiameterPx.isNaN())
    }

    @Test
    fun testFullWidthStatusCutout_StrictlyFinitePositiveNonNaN() {
        // Cutout width = 1080px, height = 100px
        val fullWidthBounds = createRect(0, 0, 1080, 100)
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = fullWidthBounds,
            statusBarTopInset = 100,
            displayWidthPx = 1080,
            density = 2.625f
        )

        assertEquals(540.0f, geometry.centerXPx, tolerance)
        assertEquals(50.0f, geometry.centerYPx, tolerance)
        assertEquals(1080.0f, geometry.cutoutDiameterPx, tolerance)
        assertTrue(geometry.isCutoutDetected)
        assertTrue(geometry.centerXPx > 0f && geometry.centerYPx > 0f && geometry.cutoutDiameterPx > 0f)
    }

    // =============================================================================================
    // Scenario 2: Zero-Width, Zero-Height, and Degenerate Rects (Fallback Verification)
    // =============================================================================================

    @Test
    fun testZeroWidthCutout_TriggersDeterministicFallback() {
        val zeroWidthBounds = createRect(540, 0, 540, 120)
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = zeroWidthBounds,
            statusBarTopInset = 0,
            displayWidthPx = 1080,
            density = 2.625f
        )

        assertFalse("Degenerate zero-width cutout must not be detected", geometry.isCutoutDetected)
        assertEquals("Fallback center X must be display width / 2", 540.0f, geometry.centerXPx, tolerance)
        assertEquals("Fallback center Y must be status bar / 2", 36.75f, geometry.centerYPx, tolerance)
        assertEquals("Fallback diameter must be 36dp * density", 94.5f, geometry.cutoutDiameterPx, tolerance)
        assertEquals("Fallback status bar must be 28dp * density", 73, geometry.statusBarHeightPx)
        assertTrue(geometry.centerXPx > 0f && geometry.centerYPx > 0f && geometry.cutoutDiameterPx > 0f)
    }

    @Test
    fun testZeroHeightCutout_TriggersDeterministicFallback() {
        val zeroHeightBounds = createRect(450, 60, 630, 60)
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = zeroHeightBounds,
            statusBarTopInset = 0,
            displayWidthPx = 1080,
            density = 2.625f
        )

        assertFalse("Degenerate zero-height cutout must not be detected", geometry.isCutoutDetected)
        assertEquals(540.0f, geometry.centerXPx, tolerance)
        assertEquals(36.75f, geometry.centerYPx, tolerance)
        assertEquals(94.5f, geometry.cutoutDiameterPx, tolerance)
        assertTrue(geometry.centerXPx > 0f && geometry.centerYPx > 0f && geometry.cutoutDiameterPx > 0f)
    }

    @Test
    fun testInvertedCoordinates_RightLessThanLeft_TriggersFallback() {
        val invertedBounds = createRect(600, 0, 400, 100)
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = invertedBounds,
            statusBarTopInset = 0,
            displayWidthPx = 1080,
            density = 2.625f
        )

        assertFalse("Inverted rect (right < left) must not be detected", geometry.isCutoutDetected)
        assertEquals(540.0f, geometry.centerXPx, tolerance)
        assertTrue(geometry.centerXPx > 0f && geometry.centerYPx > 0f && geometry.cutoutDiameterPx > 0f)
    }

    @Test
    fun testInvertedCoordinates_BottomLessThanTop_TriggersFallback() {
        val invertedBounds = createRect(400, 120, 600, 40)
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = invertedBounds,
            statusBarTopInset = 0,
            displayWidthPx = 1080,
            density = 2.625f
        )

        assertFalse("Inverted rect (bottom < top) must not be detected", geometry.isCutoutDetected)
        assertEquals(540.0f, geometry.centerXPx, tolerance)
        assertTrue(geometry.centerXPx > 0f && geometry.centerYPx > 0f && geometry.cutoutDiameterPx > 0f)
    }

    @Test
    fun testCompletelyEmptyRect_TriggersFallback() {
        val emptyBounds = createRect(0, 0, 0, 0)
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = emptyBounds,
            statusBarTopInset = 0,
            displayWidthPx = 1080,
            density = 2.625f
        )

        assertFalse("Completely empty rect must trigger fallback", geometry.isCutoutDetected)
        assertEquals(540.0f, geometry.centerXPx, tolerance)
        assertEquals(36.75f, geometry.centerYPx, tolerance)
        assertEquals(94.5f, geometry.cutoutDiameterPx, tolerance)
        assertEquals(73, geometry.statusBarHeightPx)
    }

    // =============================================================================================
    // Scenario 3: Null Insets & DisplayCutout Nullability
    // =============================================================================================

    @Test
    fun testNullBoundingRect_WithNonZeroStatusBarInset() {
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = null,
            statusBarTopInset = 100,
            displayWidthPx = 1080,
            density = 2.625f
        )

        assertFalse(geometry.isCutoutDetected)
        assertEquals(540.0f, geometry.centerXPx, tolerance)
        assertEquals(50.0f, geometry.centerYPx, tolerance) // 100 / 2
        assertEquals(94.5f, geometry.cutoutDiameterPx, tolerance)
        assertEquals(100, geometry.statusBarHeightPx)
        assertTrue(geometry.centerXPx > 0f && geometry.centerYPx > 0f && geometry.cutoutDiameterPx > 0f)
    }

    @Test
    fun testNullBoundingRect_WithZeroStatusBarInset() {
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = null,
            statusBarTopInset = 0,
            displayWidthPx = 1080,
            density = 2.625f
        )

        assertFalse(geometry.isCutoutDetected)
        assertEquals(540.0f, geometry.centerXPx, tolerance)
        assertEquals(36.75f, geometry.centerYPx, tolerance)
        assertEquals(94.5f, geometry.cutoutDiameterPx, tolerance)
        assertEquals(73, geometry.statusBarHeightPx)
    }

    @Test
    fun testNegativeStatusBarTopInset_WithNullCutout_SafelyDefaults() {
        // Negative insets can occur in transient layout passes
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = null,
            statusBarTopInset = -50,
            displayWidthPx = 1080,
            density = 2.625f
        )

        assertFalse(geometry.isCutoutDetected)
        assertEquals("Negative inset must be ignored in favor of default status bar", 73, geometry.statusBarHeightPx)
        assertEquals(36.75f, geometry.centerYPx, tolerance)
        assertTrue(geometry.statusBarHeightPx > 0)
        assertTrue(geometry.centerYPx > 0f)
    }

    // =============================================================================================
    // Scenario 4: Density Scaling Range (0.75 to 4.0)
    // =============================================================================================

    @Test
    fun testDensitySweep_FallbackGeometryScalesProportionately() {
        val testDensities = listOf(0.75f, 1.0f, 1.5f, 2.0f, 2.625f, 3.0f, 3.5f, 4.0f)

        for (density in testDensities) {
            val geometry = CutoutGeometryResolver.resolve(
                boundingRectTop = null,
                statusBarTopInset = 0,
                displayWidthPx = 1080,
                density = density
            )

            assertFalse(geometry.isCutoutDetected)
            assertEquals("centerXPx must be half width", 540.0f, geometry.centerXPx, tolerance)

            val expectedStatusBar = (28f * density).toInt()
            val expectedCenterY = (28f * density) / 2f
            val expectedDiameter = 36f * density

            assertEquals("statusBarHeightPx mismatch at density $density", expectedStatusBar, geometry.statusBarHeightPx)
            assertEquals("centerYPx mismatch at density $density", expectedCenterY, geometry.centerYPx, tolerance)
            assertEquals("cutoutDiameterPx mismatch at density $density", expectedDiameter, geometry.cutoutDiameterPx, tolerance)

            assertTrue("centerXPx must be positive", geometry.centerXPx > 0f)
            assertTrue("centerYPx must be positive", geometry.centerYPx > 0f)
            assertTrue("cutoutDiameterPx must be positive", geometry.cutoutDiameterPx > 0f)
            assertTrue("values must be finite", geometry.centerXPx.isFinite() && geometry.centerYPx.isFinite() && geometry.cutoutDiameterPx.isFinite())
        }
    }

    // =============================================================================================
    // Scenario 5: Asymmetrical and Corner Punch-Holes (Galaxy S10, Pixel 4a, etc.)
    // =============================================================================================

    @Test
    fun testLeftCornerPunchHole_ExactCenterAndDiameter() {
        // Left camera hole at x: [40..120], y: [0..80]
        val leftBounds = createRect(40, 0, 120, 80)
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = leftBounds,
            statusBarTopInset = 80,
            displayWidthPx = 1080,
            density = 2.625f
        )

        assertTrue(geometry.isCutoutDetected)
        assertEquals(80.0f, geometry.centerXPx, tolerance)
        assertEquals(40.0f, geometry.centerYPx, tolerance)
        assertEquals(80.0f, geometry.cutoutDiameterPx, tolerance)
        assertEquals(80, geometry.statusBarHeightPx)
        assertTrue(geometry.centerXPx > 0f && geometry.centerYPx > 0f && geometry.cutoutDiameterPx > 0f)
    }

    @Test
    fun testRightCornerPunchHole_ExactCenterAndDiameter() {
        // Right camera hole at x: [960..1040], y: [0..80]
        val rightBounds = createRect(960, 0, 1040, 80)
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = rightBounds,
            statusBarTopInset = 80,
            displayWidthPx = 1080,
            density = 2.625f
        )

        assertTrue(geometry.isCutoutDetected)
        assertEquals(1000.0f, geometry.centerXPx, tolerance)
        assertEquals(40.0f, geometry.centerYPx, tolerance)
        assertEquals(80.0f, geometry.cutoutDiameterPx, tolerance)
        assertEquals(80, geometry.statusBarHeightPx)
        assertTrue(geometry.centerXPx > 0f && geometry.centerYPx > 0f && geometry.cutoutDiameterPx > 0f)
    }

    @Test
    fun testAsymmetricPillCutout_CornerPositioned() {
        // Pill cutout on top right: x: [800..1040], y: [0..80] -> width = 240px, height = 80px
        val pillBounds = createRect(800, 0, 1040, 80)
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = pillBounds,
            statusBarTopInset = 80,
            displayWidthPx = 1080,
            density = 2.625f
        )

        assertTrue(geometry.isCutoutDetected)
        assertEquals(920.0f, geometry.centerXPx, tolerance)
        assertEquals(40.0f, geometry.centerYPx, tolerance)
        assertEquals("Cutout diameter must be max(width, height) = 240px", 240.0f, geometry.cutoutDiameterPx, tolerance)
        assertTrue(geometry.centerXPx > 0f && geometry.centerYPx > 0f && geometry.cutoutDiameterPx > 0f)
    }

    // =============================================================================================
    // Scenario 6: Extreme Display Dimensions (Foldables, Tablets, 4K Displays)
    // =============================================================================================

    @Test
    fun testFoldableUnfoldedDisplayResolution() {
        // Pixel Fold unfolded: 2208 x 1840 px, density = 2.625f
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = null,
            statusBarTopInset = 0,
            displayWidthPx = 2208,
            density = 2.625f
        )

        assertFalse(geometry.isCutoutDetected)
        assertEquals(1104.0f, geometry.centerXPx, tolerance)
        assertTrue(geometry.centerXPx > 0f && geometry.centerYPx > 0f && geometry.cutoutDiameterPx > 0f)
    }

    @Test
    fun testUltraHighResolution4KDisplay() {
        // 3840 x 2160 px, density = 4.0f
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = null,
            statusBarTopInset = 0,
            displayWidthPx = 3840,
            density = 4.0f
        )

        assertFalse(geometry.isCutoutDetected)
        assertEquals(1920.0f, geometry.centerXPx, tolerance)
        assertEquals(56.0f, geometry.centerYPx, tolerance) // 28 * 4 / 2
        assertEquals(144.0f, geometry.cutoutDiameterPx, tolerance) // 36 * 4
        assertEquals(112, geometry.statusBarHeightPx) // 28 * 4
        assertTrue(geometry.centerXPx > 0f && geometry.centerYPx > 0f && geometry.cutoutDiameterPx > 0f)
    }

    // =============================================================================================
    // Scenario 7: Negative Coordinates Boundary Analysis & Empirical Characterization
    // =============================================================================================

    @Test
    fun testNegativeCoordinates_EmpiricalCharacterization() {
        // An offscreen rect with negative bounds: left = -100, top = -50, right = -20, bottom = -10
        val negativeBounds = createRect(-100, -50, -20, -10)
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = negativeBounds,
            statusBarTopInset = 0,
            displayWidthPx = 1080,
            density = 2.625f
        )

        // Mathematical verification of current implementation behavior:
        // topRect.right (-20) > topRect.left (-100) and topRect.bottom (-10) > topRect.top (-50)
        // Center X = (-100 + -20) / 2 = -60.0f
        // Center Y = (-50 + -10) / 2 = -30.0f
        // Diameter = max(80, 40) = 80.0f
        // StatusBar = -10
        assertEquals(-60.0f, geometry.centerXPx, tolerance)
        assertEquals(-30.0f, geometry.centerYPx, tolerance)
        assertEquals(80.0f, geometry.cutoutDiameterPx, tolerance)
        assertEquals(-10, geometry.statusBarHeightPx)
        assertTrue("Under current logic, relative bounds satisfy right > left and bottom > top", geometry.isCutoutDetected)

        // Verify values are strictly finite and non-NaN
        assertTrue(geometry.centerXPx.isFinite() && !geometry.centerXPx.isNaN())
        assertTrue(geometry.centerYPx.isFinite() && !geometry.centerYPx.isNaN())
        assertTrue(geometry.cutoutDiameterPx.isFinite() && !geometry.cutoutDiameterPx.isNaN())
    }

    @Test
    fun testCutoutAtDisplayOrigin_ZeroCenter() {
        // Cutout centered directly on origin: x: [-50..50], y: [0..100]
        val originBounds = createRect(-50, 0, 50, 100)
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = originBounds,
            statusBarTopInset = 100,
            displayWidthPx = 1080,
            density = 2.625f
        )

        assertEquals(0.0f, geometry.centerXPx, tolerance)
        assertEquals(50.0f, geometry.centerYPx, tolerance)
        assertEquals(100.0f, geometry.cutoutDiameterPx, tolerance)
        assertTrue(geometry.centerXPx.isFinite() && !geometry.centerXPx.isNaN())
        assertTrue(geometry.centerYPx.isFinite() && !geometry.centerYPx.isNaN())
    }
}
