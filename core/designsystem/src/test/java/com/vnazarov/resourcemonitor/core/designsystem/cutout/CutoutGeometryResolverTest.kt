package com.vnazarov.resourcemonitor.core.designsystem.cutout

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CutoutGeometryResolverTest {

    private val tolerance = 0.001f

    private fun createRect(left: Int, top: Int, right: Int, bottom: Int): Rect {
        return Rect().apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }
    }

    @Test
    fun testPixel8_CutoutBounds_ExactCenterAndDiameter() {
        // Pixel 8 shiba: 1080x2400, density = 2.625 (420 dpi), status bar = 132px
        val pixel8Bounds = createRect(479, 0, 601, 132)
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = pixel8Bounds,
            statusBarTopInset = 132,
            displayWidthPx = 1080,
            density = 2.625f
        )

        assertEquals("Pixel 8 center X must be exact 540.0px", 540.0f, geometry.centerXPx, tolerance)
        assertEquals("Pixel 8 center Y must be exact 66.0px", 66.0f, geometry.centerYPx, tolerance)
        assertEquals("Pixel 8 cutout diameter must match bounding height 132.0px", 132.0f, geometry.cutoutDiameterPx, tolerance)
        assertEquals("Pixel 8 status bar height must be 132px", 132, geometry.statusBarHeightPx)
        assertTrue("Pixel 8 hardware cutout must be detected", geometry.isCutoutDetected)
    }

    @Test
    fun testPixel11Pro_NativeBounds_ExactCenterAndDiameter() {
        // Pixel 11 Pro native: 1280x2856, density = 3.0, status bar = 204px
        val pixel11ProNativeBounds = createRect(585, 0, 695, 204)
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = pixel11ProNativeBounds,
            statusBarTopInset = 204,
            displayWidthPx = 1280,
            density = 3.0f
        )

        assertEquals("Pixel 11 Pro native center X must be exact 640.0px", 640.0f, geometry.centerXPx, tolerance)
        assertEquals("Pixel 11 Pro native center Y must be exact 102.0px", 102.0f, geometry.centerYPx, tolerance)
        assertEquals("Pixel 11 Pro native cutout diameter must match bounding height 204.0px", 204.0f, geometry.cutoutDiameterPx, tolerance)
        assertEquals("Pixel 11 Pro status bar height must be 204px", 204, geometry.statusBarHeightPx)
        assertTrue("Pixel 11 Pro hardware cutout must be detected", geometry.isCutoutDetected)
    }

    @Test
    fun testPixel11Pro_ScaledBounds_ExactCenterAndDiameter() {
        // Pixel 11 Pro scaled mode 2: 1080x2410, density = 2.625, status bar = 172px
        val pixel11ProScaledBounds = createRect(494, 0, 586, 172)
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = pixel11ProScaledBounds,
            statusBarTopInset = 172,
            displayWidthPx = 1080,
            density = 2.625f
        )

        assertEquals("Pixel 11 Pro scaled center X must be exact 540.0f", 540.0f, geometry.centerXPx, tolerance)
        assertEquals("Pixel 11 Pro scaled center Y must be exact 86.0f", 86.0f, geometry.centerYPx, tolerance)
        assertEquals("Pixel 11 Pro scaled diameter must match bounding height 172.0f", 172.0f, geometry.cutoutDiameterPx, tolerance)
        assertEquals("Pixel 11 Pro status bar height must be 172px", 172, geometry.statusBarHeightPx)
        assertTrue("Pixel 11 Pro hardware cutout must be detected", geometry.isCutoutDetected)
    }

    @Test
    fun testEmptyCutout_FallbackGeometry() {
        // Fallback for null cutout: 1080 display width, density = 2.625, statusBar = 0
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = null,
            statusBarTopInset = 0,
            displayWidthPx = 1080,
            density = 2.625f
        )

        assertEquals("Fallback center X must be half of screen width (540.0px)", 540.0f, geometry.centerXPx, tolerance)
        assertEquals("Fallback center Y must be half of 28dp status bar (36.75px)", 36.75f, geometry.centerYPx, tolerance)
        assertEquals("Fallback diameter must be 36dp * density (94.5px)", 94.5f, geometry.cutoutDiameterPx, tolerance)
        assertEquals("Fallback status bar must be 28dp * density = 73px", 73, geometry.statusBarHeightPx)
        assertFalse("Fallback must report cutout not detected", geometry.isCutoutDetected)
    }

    @Test
    fun testPixel8_OverlayHeight_UnclippedRingAndBloom() {
        val pixel8Bounds = createRect(479, 0, 601, 132)
        val density = 2.625f
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = pixel8Bounds,
            statusBarTopInset = 132,
            displayWidthPx = 1080,
            density = density
        )

        val overlayHeight = CutoutGeometryResolver.calculateOverlayHeightPx(geometry, density = density)
        // requiredHeight = (66.0 + 52 * 2.625 + 16 * 2.625).toInt() = (66.0 + 136.5 + 42.0) = 244
        assertEquals("Pixel 8 overlay height must be 244px to prevent bottom clipping", 244, overlayHeight)
        assertTrue("Overlay height must exceed status bar height (132px)", overlayHeight > geometry.statusBarHeightPx)

        // Verify Ring 1 outer edge + bloom stroke is strictly unclipped
        val ring1BottomWithBloom = geometry.centerYPx + (52f * density) + (4f * density)
        assertTrue("Ring 1 bottom edge must fall completely inside overlay window", ring1BottomWithBloom < overlayHeight)
    }

    @Test
    fun testPixel11Pro_Native_OverlayHeight_UnclippedRingAndBloom() {
        val pixel11ProBounds = createRect(585, 0, 695, 204)
        val density = 3.0f
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = pixel11ProBounds,
            statusBarTopInset = 204,
            displayWidthPx = 1280,
            density = density
        )

        val overlayHeight = CutoutGeometryResolver.calculateOverlayHeightPx(geometry, density = density)
        // requiredHeight = (102.0 + 52 * 3.0 + 16 * 3.0).toInt() = (102.0 + 156.0 + 48.0) = 306
        assertEquals("Pixel 11 Pro native overlay height must be 306px to prevent clipping", 306, overlayHeight)
        assertTrue("Overlay height must exceed status bar height (204px)", overlayHeight > geometry.statusBarHeightPx)

        val ring1BottomWithBloom = geometry.centerYPx + (52f * density) + (4f * density)
        assertTrue("Ring 1 bottom edge must fall completely inside overlay window", ring1BottomWithBloom < overlayHeight)
    }

    @Test
    fun testFallback_OverlayHeight_UnclippedRingAndBloom() {
        val density = 2.625f
        val geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = null,
            statusBarTopInset = 0,
            displayWidthPx = 1080,
            density = density
        )

        val overlayHeight = CutoutGeometryResolver.calculateOverlayHeightPx(geometry, density = density)
        // requiredHeight = (36.75 + 136.5 + 42.0).toInt() = 215
        assertEquals("Fallback overlay height must be 215px to prevent clipping", 215, overlayHeight)
        assertTrue("Overlay height must exceed fallback status bar height (73px)", overlayHeight > geometry.statusBarHeightPx)
    }

    @Test
    fun testCameraHoleEncirclement_Pixel8AndPixel11Pro() {
        val haloRadiusDp = 52.0f
        val ringScaleFactors = listOf(1.00f, 0.85f, 0.70f, 0.55f, 0.40f)
        val ringRadiiDp = ringScaleFactors.map { it * haloRadiusDp }

        val pixel8CameraRadiusDp = 18.0f
        val pixel11ProCameraRadiusDp = 20.0f

        val ring5RadiusDp = ringRadiiDp[4]
        assertEquals("Ring 5 radius must be exact 20.8dp", 20.8f, ring5RadiusDp, tolerance)

        assertTrue(
            "Ring 5 (20.8dp) must strictly encircle Pixel 8 camera lens (18.0dp)",
            ring5RadiusDp > pixel8CameraRadiusDp
        )
        assertTrue(
            "Ring 5 (20.8dp) must strictly encircle Pixel 11 Pro camera lens (20.0dp)",
            ring5RadiusDp > pixel11ProCameraRadiusDp
        )

        // Assert all rings strictly encircle both cutouts
        for ((idx, radius) in ringRadiiDp.withIndex()) {
            assertTrue("Ring ${idx + 1} ($radius dp) must exceed Pixel 8 camera lens", radius > pixel8CameraRadiusDp)
            assertTrue("Ring ${idx + 1} ($radius dp) must exceed Pixel 11 Pro camera lens", radius > pixel11ProCameraRadiusDp)
        }
    }
}
