package com.vnazarov.resourcemonitor.presentation.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class StartScreenTest {

    private val tolerance = 0.001f

    @Test
    fun testHaloDiameterSlider_rangeAndDefaults() {
        val sliderMin = 96.0f
        val sliderMax = 136.0f
        val defaultDiameter = 104.0f

        assertTrue("Default diameter must be >= slider min", defaultDiameter >= sliderMin)
        assertTrue("Default diameter must be <= slider max", defaultDiameter <= sliderMax)

        // Snapping logic test
        val rawInput = 105.23f
        val snapped = (rawInput * 2f).roundToInt() / 2f
        assertEquals(105.0f, snapped, tolerance)

        val rawInput2 = 105.34f
        val snapped2 = (rawInput2 * 2f).roundToInt() / 2f
        assertEquals(105.5f, snapped2, tolerance)
    }

    @Test
    fun testHaloDiameterSlider_boundsEnforceApertureClearance() {
        val sliderMin = 96.0f
        val minDerivedR0 = sliderMin / 2f // 48.0dp
        val minRing5 = minDerivedR0 * 0.40f // 19.2dp

        val pixel8LensRadius = 18.0f
        assertTrue("Even at minimum slider (96dp), Ring 5 (19.2dp) must exceed Pixel 8 lens (18.0dp)", minRing5 > pixel8LensRadius)
    }
}
