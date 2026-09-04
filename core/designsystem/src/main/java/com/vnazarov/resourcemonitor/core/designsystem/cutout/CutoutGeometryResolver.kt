package com.vnazarov.resourcemonitor.core.designsystem.cutout

import android.graphics.Rect
import android.view.DisplayCutout
import android.view.WindowInsets

/**
 * Resolves physical camera-hole cutout geometry from Android WindowInsets and DisplayCutout.
 *
 * Supports centered cutouts on Google Pixel 8 (1080x2400) and Google Pixel 11 Pro (1280x2856),
 * and provides deterministic fallbacks when running on devices or emulators without hardware cutouts.
 */
object CutoutGeometryResolver {

    /**
     * Resolves [CutoutGeometry] from a [DisplayCutout] instance.
     */
    fun resolve(
        cutout: DisplayCutout?,
        statusBarTopInset: Int = 0,
        displayWidthPx: Int = 1080,
        density: Float = 2.625f
    ): CutoutGeometry {
        val topRect = cutout?.boundingRectTop
        return resolve(topRect, statusBarTopInset, displayWidthPx, density)
    }

    /**
     * Resolves [CutoutGeometry] from a top bounding [Rect] (e.g. [DisplayCutout.getBoundingRectTop]).
     */
    fun resolve(
        boundingRectTop: Rect?,
        statusBarTopInset: Int = 0,
        displayWidthPx: Int = 1080,
        density: Float = 2.625f
    ): CutoutGeometry {
        val topRect = boundingRectTop

        return if (topRect != null && topRect.right > topRect.left && topRect.bottom > topRect.top) {
            val centerX = (topRect.left + topRect.right) / 2f
            val centerY = (topRect.top + topRect.bottom) / 2f
            val width = (topRect.right - topRect.left).toFloat()
            val height = (topRect.bottom - topRect.top).toFloat()
            val diameter = maxOf(width, height)

            CutoutGeometry(
                centerXPx = centerX,
                centerYPx = centerY,
                cutoutDiameterPx = diameter,
                statusBarHeightPx = if (statusBarTopInset > 0) statusBarTopInset else topRect.bottom,
                isCutoutDetected = true
            )
        } else {
            // Deterministic fallback for devices/emulators without punch-hole cutout
            val defaultStatusBarPx = (28f * density).toInt()
            val centerY = if (statusBarTopInset > 0) statusBarTopInset / 2f else (28f * density) / 2f

            CutoutGeometry(
                centerXPx = displayWidthPx / 2f,
                centerYPx = centerY,
                cutoutDiameterPx = 36f * density,
                statusBarHeightPx = if (statusBarTopInset > 0) statusBarTopInset else defaultStatusBarPx,
                isCutoutDetected = false
            )
        }
    }

    /**
     * Resolves [CutoutGeometry] directly from root [WindowInsets].
     */
    fun resolve(
        windowInsets: WindowInsets?,
        displayWidthPx: Int = 1080,
        density: Float = 2.625f
    ): CutoutGeometry {
        val cutout = windowInsets?.displayCutout
        val statusBarTop = windowInsets?.getInsets(WindowInsets.Type.statusBars())?.top ?: 0
        return resolve(cutout, statusBarTop, displayWidthPx, density)
    }

    /**
     * Calculates the required WindowManager overlay height to ensure the outer holographic ring
     * (Ring 1) and its neon bloom glow are completely unclipped by the window surface boundary.
     */
    fun calculateOverlayHeightPx(
        geometry: CutoutGeometry,
        haloRadiusDp: Float = 52f,
        bloomPaddingDp: Float = 16f,
        density: Float = 2.625f
    ): Int {
        val haloRadiusPx = haloRadiusDp * density
        val bloomPaddingPx = bloomPaddingDp * density
        val requiredHeight = (geometry.centerYPx + haloRadiusPx + bloomPaddingPx).toInt()
        return maxOf(geometry.statusBarHeightPx, requiredHeight)
    }
}
