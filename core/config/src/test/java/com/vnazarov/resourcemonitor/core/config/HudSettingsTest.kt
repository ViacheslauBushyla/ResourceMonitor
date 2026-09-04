package com.vnazarov.resourcemonitor.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HudSettingsTest {

    private val tolerance = 0.001f

    @Test
    fun testDefaultSettings_canonicalValues() {
        val settings = HudSettings()

        assertFalse(settings.isOverlayEnabled)
        assertEquals("CAMERA_HALO", settings.overlayMode)
        assertEquals(104.0f, settings.haloDiameterDp, tolerance)
        assertEquals(0.2f, settings.vMinRps, tolerance)
        assertEquals(5.0f, settings.vMaxRps, tolerance)
        assertEquals("QUADRATIC", settings.speedCurve)
        assertEquals(1.0f, settings.outerSensitivity, tolerance)
        assertEquals(1.0f, settings.middleSensitivity, tolerance)
        assertEquals(1.0f, settings.innerSensitivity, tolerance)
        assertEquals(0.90f, settings.meltdownThreshold, tolerance)
        assertEquals("RING_1", settings.channelCpuMapping)
        assertEquals("REAL", settings.telemetrySourceMode)
        assertEquals(250L, settings.activePollingMs)
        assertEquals(2000L, settings.idlePollingMs)
    }

    @Test
    fun testDefaultHaloDiameter_guaranteesApertureEncirclement() {
        val settings = HudSettings()
        val derivedBaseRadiusDp = settings.haloDiameterDp / 2f
        assertEquals(52.0f, derivedBaseRadiusDp, tolerance)

        val ring5Scale = 0.40f
        val ring5RadiusDp = derivedBaseRadiusDp * ring5Scale
        assertEquals(20.8f, ring5RadiusDp, tolerance)

        // Physical camera lens specs
        val pixel8CameraRadiusDp = 18.0f
        val pixel11ProCameraRadiusDp = 20.0f

        val pixel8ClearanceDp = ring5RadiusDp - pixel8CameraRadiusDp
        assertTrue("Ring 5 must strictly exceed Pixel 8 camera lens radius", ring5RadiusDp > pixel8CameraRadiusDp)
        assertEquals(2.8f, pixel8ClearanceDp, tolerance)

        val pixel11ProClearanceDp = ring5RadiusDp - pixel11ProCameraRadiusDp
        assertTrue("Ring 5 must strictly exceed Pixel 11 Pro camera lens radius", ring5RadiusDp > pixel11ProCameraRadiusDp)
        assertEquals(0.8f, pixel11ProClearanceDp, tolerance)
    }
}
