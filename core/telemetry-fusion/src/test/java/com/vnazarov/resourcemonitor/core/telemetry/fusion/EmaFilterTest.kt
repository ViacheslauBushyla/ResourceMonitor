package com.vnazarov.resourcemonitor.core.telemetry.fusion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmaFilterTest {

    @Test
    fun `initial value is retained before filtering`() {
        val filter = EmaFilter(alpha = 0.5f, initialValue = 0.2f)
        assertEquals(0.2f, filter.value, 0.001f)
    }

    @Test
    fun `filter dampens sudden step jump`() {
        val filter = EmaFilter(alpha = 0.25f, initialValue = 0f)
        // Step input from 0 to 1.0
        val step1 = filter.filter(1.0f)
        assertEquals(0.25f, step1, 0.001f)

        val step2 = filter.filter(1.0f)
        // 0.25 * 1.0 + 0.75 * 0.25 = 0.4375
        assertEquals(0.4375f, step2, 0.001f)

        // After multiple steps, it approaches 1.0
        repeat(20) { filter.filter(1.0f) }
        assertTrue(filter.value > 0.99f)
    }
}
