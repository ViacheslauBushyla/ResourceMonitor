package com.vnazarov.resourcemonitor.core.designsystem.cutout

/**
 * Immutable spatial representation of a device's display cutout / camera punch-hole.
 *
 * @property centerXPx Horizontal center of the camera cutout in pixels relative to display window.
 * @property centerYPx Vertical center of the camera cutout in pixels relative to display window.
 * @property cutoutDiameterPx Outer physical diameter of the camera punch-hole bounding box in pixels.
 * @property statusBarHeightPx Height of the system status bar in pixels.
 * @property isCutoutDetected True if a hardware display cutout was detected; false if using fallback geometry.
 */
data class CutoutGeometry(
    val centerXPx: Float,
    val centerYPx: Float,
    val cutoutDiameterPx: Float,
    val statusBarHeightPx: Int,
    val isCutoutDetected: Boolean
)
