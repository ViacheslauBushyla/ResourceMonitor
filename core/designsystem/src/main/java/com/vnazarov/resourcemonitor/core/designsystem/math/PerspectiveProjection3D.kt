package com.vnazarov.resourcemonitor.core.designsystem.math

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

data class Point3D(val x: Float, val y: Float, val z: Float)

object PerspectiveProjection3D {

    /**
     * Rotates a point (x, y, z) in 3D around X, Y, and Z axes (in radians).
     */
    fun rotate3D(point: Point3D, rotX: Float, rotY: Float, rotZ: Float): Point3D {
        // Rotate around X
        val cosX = cos(rotX)
        val sinX = sin(rotX)
        val y1 = point.y * cosX - point.z * sinX
        val z1 = point.y * sinX + point.z * cosX

        // Rotate around Y
        val cosY = cos(rotY)
        val sinY = sin(rotY)
        val x2 = point.x * cosY + z1 * sinY
        val z2 = -point.x * sinY + z1 * cosY

        // Rotate around Z
        val cosZ = cos(rotZ)
        val sinZ = sin(rotZ)
        val x3 = x2 * cosZ - y1 * sinZ
        val y3 = x2 * sinZ + y1 * cosZ

        return Point3D(x3, y3, z2)
    }

    /**
     * Projects a 3D point onto 2D screen coordinates with perspective division.
     * cameraDistance: distance from camera to origin
     */
    fun projectTo2D(
        point: Point3D,
        center: Offset,
        cameraDistance: Float = 600f
    ): Offset {
        val distance = cameraDistance + point.z
        val factor = if (distance > 0.1f) cameraDistance / distance else 1f
        val screenX = center.x + point.x * factor
        val screenY = center.y + point.y * factor
        return Offset(screenX, screenY)
    }
}
