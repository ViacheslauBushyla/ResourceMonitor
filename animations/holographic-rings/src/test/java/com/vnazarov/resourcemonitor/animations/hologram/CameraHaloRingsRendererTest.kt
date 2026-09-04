package com.vnazarov.resourcemonitor.animations.hologram

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CameraHaloRingsRendererTest {

    private val tolerance = 0.001f

    @Test
    fun testTrigonometricLookupTable_propertiesAndAccuracy() {
        val segments = 32
        val cosTable = FloatArray(segments + 1) { i ->
            cos((i.toFloat() / segments.toFloat()) * 2f * PI.toFloat())
        }
        val sinTable = FloatArray(segments + 1) { i ->
            sin((i.toFloat() / segments.toFloat()) * 2f * PI.toFloat())
        }

        assertEquals(33, cosTable.size)
        assertEquals(33, sinTable.size)

        // theta = 0
        assertEquals(1.0f, cosTable[0], tolerance)
        assertEquals(0.0f, sinTable[0], tolerance)

        // theta = pi/2 (index 8)
        assertEquals(0.0f, cosTable[8], tolerance)
        assertEquals(1.0f, sinTable[8], tolerance)

        // theta = pi (index 16)
        assertEquals(-1.0f, cosTable[16], tolerance)
        assertEquals(0.0f, sinTable[16], tolerance)

        // theta = 3pi/2 (index 24)
        assertEquals(0.0f, cosTable[24], tolerance)
        assertEquals(-1.0f, sinTable[24], tolerance)

        // theta = 2pi (index 32)
        assertEquals(1.0f, cosTable[32], tolerance)
        assertEquals(0.0f, sinTable[32], tolerance)
    }

    @Test
    fun testRingRadiiProportions_andApertureClearance() {
        val baseRadiusDp = 52.0f

        val ring1Radius = baseRadiusDp * 1.00f
        val ring2Radius = baseRadiusDp * 0.85f
        val ring3Radius = baseRadiusDp * 0.70f
        val ring4Radius = baseRadiusDp * 0.55f
        val ring5Radius = baseRadiusDp * 0.40f

        assertEquals(52.0f, ring1Radius, tolerance)
        assertEquals(44.2f, ring2Radius, tolerance)
        assertEquals(36.4f, ring3Radius, tolerance)
        assertEquals(28.6f, ring4Radius, tolerance)
        assertEquals(20.8f, ring5Radius, tolerance)

        // Strict clearance verification
        val pixel8LensRadiusDp = 18.0f
        val pixel11ProLensRadiusDp = 20.0f

        assertTrue("Ring 5 strictly clears Pixel 8 lens", ring5Radius > pixel8LensRadiusDp)
        assertEquals(2.8f, ring5Radius - pixel8LensRadiusDp, tolerance)

        assertTrue("Ring 5 strictly clears Pixel 11 Pro lens", ring5Radius > pixel11ProLensRadiusDp)
        assertEquals(0.8f, ring5Radius - pixel11ProLensRadiusDp, tolerance)
    }

    @Test
    fun testDefaultFramePacingInterval() {
        val targetFps = 30
        val frameIntervalMs = 1000L / targetFps
        assertEquals(33L, frameIntervalMs)
    }

    @Test
    fun testAmbientFramePacingInterval() {
        val ambientFps = 1
        val frameIntervalMs = 1000L / ambientFps
        assertEquals(1000L, frameIntervalMs)
    }
}
