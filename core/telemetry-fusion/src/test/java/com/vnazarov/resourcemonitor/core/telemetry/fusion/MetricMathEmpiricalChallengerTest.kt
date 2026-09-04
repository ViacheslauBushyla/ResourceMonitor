package com.vnazarov.resourcemonitor.core.telemetry.fusion

import com.vnazarov.resourcemonitor.core.model.RawTelemetryPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricMathEmpiricalChallengerTest {

    // =========================================================================
    // 1. CPU LOAD NORMALIZATION INVARIANT TESTS
    // =========================================================================

    @Test
    fun normalizeCpu_boundaryAndNominalValues_strictlyWithinZeroAndOne() {
        val testCases = listOf(
            0f to 0.0f,
            25.5f to 0.255f,
            50f to 0.5f,
            75f to 0.75f,
            100f to 1.0f
        )

        for ((input, expected) in testCases) {
            val packet = RawTelemetryPacket(cpuLoadPercentage = input)
            val normalized = TelemetryNormalizer.normalizeCpu(packet)
            assertEquals("Normalized CPU for $input% must match expected", expected, normalized, 0.001f)
            assertTrue("Normalized CPU must be in [0.0..1.0]", normalized in 0.0f..1.0f)
            assertFalse("Normalized CPU must not be NaN", normalized.isNaN())
            assertFalse("Normalized CPU must not be infinite", normalized.isInfinite())
        }
    }

    @Test
    fun normalizeCpu_pathologicalNegativeAndOverflowInputs_clampedSafely() {
        val pathologicalCases = listOf(
            -100f to 0.0f,
            -0.001f to 0.0f,
            -999999f to 0.0f,
            100.001f to 1.0f,
            150f to 1.0f,
            999999f to 1.0f,
            Float.POSITIVE_INFINITY to 1.0f,
            Float.NEGATIVE_INFINITY to 0.0f
        )

        for ((input, expected) in pathologicalCases) {
            val packet = RawTelemetryPacket(cpuLoadPercentage = input)
            val normalized = TelemetryNormalizer.normalizeCpu(packet)
            assertEquals("Normalized CPU for pathological $input must clamp to $expected", expected, normalized, 0.001f)
            assertTrue("Normalized CPU must be in [0.0..1.0]", normalized in 0.0f..1.0f)
            assertFalse("Normalized CPU must not be NaN", normalized.isNaN())
            assertFalse("Normalized CPU must not be infinite", normalized.isInfinite())
        }
    }

    // =========================================================================
    // 2. RAM NORMALIZATION INVARIANT TESTS
    // =========================================================================

    @Test
    fun normalizeRam_nominalAndBoundaryValues_strictlyWithinZeroAndOne() {
        val eightGb = 8L * 1024L * 1024L * 1024L

        // Case 1: Zero RAM used (all available)
        val p1 = RawTelemetryPacket(ramTotalBytes = eightGb, ramAvailableBytes = eightGb)
        assertEquals(0.0f, TelemetryNormalizer.normalizeRam(p1), 0.001f)

        // Case 2: 50% RAM used (4GB available)
        val p2 = RawTelemetryPacket(ramTotalBytes = eightGb, ramAvailableBytes = fourGb())
        assertEquals(0.5f, TelemetryNormalizer.normalizeRam(p2), 0.001f)

        // Case 3: 100% RAM used (0 available)
        val p3 = RawTelemetryPacket(ramTotalBytes = eightGb, ramAvailableBytes = 0L)
        assertEquals(1.0f, TelemetryNormalizer.normalizeRam(p3), 0.001f)
    }

    @Test
    fun normalizeRam_pathologicalInputs_zeroTotalOrAvailableExceedingTotal_clampedSafely() {
        val eightGb = 8L * 1024L * 1024L * 1024L

        // Case 1: Total RAM is 0 or negative
        val p0 = RawTelemetryPacket(ramTotalBytes = 0L, ramAvailableBytes = 0L)
        assertEquals("Total RAM 0 must normalize to 0.0f", 0.0f, TelemetryNormalizer.normalizeRam(p0), 0.001f)

        val pNegTotal = RawTelemetryPacket(ramTotalBytes = -1024L, ramAvailableBytes = 0L)
        assertEquals("Negative Total RAM must normalize to 0.0f", 0.0f, TelemetryNormalizer.normalizeRam(pNegTotal), 0.001f)

        // Case 2: Available RAM > Total RAM (anomalous procfs)
        val pAnomalous = RawTelemetryPacket(ramTotalBytes = eightGb, ramAvailableBytes = eightGb * 2)
        val normAnom = TelemetryNormalizer.normalizeRam(pAnomalous)
        assertEquals("When available > total, used is 0, ratio must be 0.0f", 0.0f, normAnom, 0.001f)

        // Case 3: Negative available RAM
        val pNegAvail = RawTelemetryPacket(ramTotalBytes = eightGb, ramAvailableBytes = -1024L)
        val normNegAvail = TelemetryNormalizer.normalizeRam(pNegAvail)
        assertEquals("When available < 0, ratio must clamp to 1.0f", 1.0f, normNegAvail, 0.001f)

        // Case 4: Long.MAX_VALUE total RAM
        val pMax = RawTelemetryPacket(ramTotalBytes = Long.MAX_VALUE, ramAvailableBytes = 0L)
        val normMax = TelemetryNormalizer.normalizeRam(pMax)
        assertTrue("Normalized RAM must be in [0.0..1.0]", normMax in 0.0f..1.0f)
        assertFalse("Normalized RAM must not be NaN", normMax.isNaN())
    }

    // =========================================================================
    // 3. NETWORK NORMALIZATION INVARIANT TESTS
    // =========================================================================

    @Test
    fun normalizeNetwork_idleAndSubNoiseFloor_returnsZero() {
        val pZero = RawTelemetryPacket(rxBytesPerSec = 0L, txBytesPerSec = 0L)
        assertEquals(0.0f, TelemetryNormalizer.normalizeNetwork(pZero), 0.001f)

        // Below 1024 B/s noise floor
        val pNoise = RawTelemetryPacket(rxBytesPerSec = 500L, txBytesPerSec = 500L)
        assertEquals("Speeds <= 1024 B/s should be ignored as noise floor", 0.0f, TelemetryNormalizer.normalizeNetwork(pNoise), 0.001f)
    }

    @Test
    fun normalizeNetwork_dynamicThroughput_logarithmicScalingUpTo10MBs() {
        // Exactly 10 MB/s (10,000,000 B/s) should be 1.0f
        val p10M = RawTelemetryPacket(rxBytesPerSec = 7_000_000L, txBytesPerSec = 3_000_000L)
        val norm10M = TelemetryNormalizer.normalizeNetwork(p10M)
        assertEquals(1.0f, norm10M, 0.005f)

        // 1 MB/s (1,000,000 B/s)
        val p1M = RawTelemetryPacket(rxBytesPerSec = 1_000_000L, txBytesPerSec = 0L)
        val norm1M = TelemetryNormalizer.normalizeNetwork(p1M)
        assertTrue("1 MB/s should be ~0.85 on log scale: $norm1M", norm1M in 0.80f..0.90f)
    }

    @Test
    fun normalizeNetwork_massiveGigabyteSpikes_clampedStrictlyTo1fWithoutOverflow() {
        val spikes = listOf(
            20_000_000L,          // 20 MB/s
            100_000_000L,         // 100 MB/s
            1_000_000_000L,       // 1 GB/s
            10_000_000_000L,      // 10 GB/s
            100_000_000_000L      // 100 GB/s
        )

        for (spike in spikes) {
            val packet = RawTelemetryPacket(rxBytesPerSec = spike, txBytesPerSec = 0L)
            val normalized = TelemetryNormalizer.normalizeNetwork(packet)
            assertEquals("Traffic above 10 MB/s must clamp strictly to 1.0f: $normalized", 1.0f, normalized, 0.001f)
            assertTrue("Must be in [0.0..1.0]", normalized in 0.0f..1.0f)
            assertFalse(normalized.isNaN())
            assertFalse(normalized.isInfinite())
        }
    }

    // =========================================================================
    // 4. CELLULAR QUALITY NORMALIZATION INVARIANT TESTS
    // =========================================================================

    @Test
    fun normalizeCellularQuality_nominalAndBoundaryValues_strictlyWithinZeroAndOne() {
        // Case 1: Missing or Wi-Fi (null RSRP) -> 1.0f (nominal)
        val pNull = RawTelemetryPacket(rsrpDbm = null)
        assertEquals(1.0f, TelemetryNormalizer.normalizeCellularQuality(pNull), 0.001f)

        // Case 2: -140 dBm (dead zone boundary) -> 0.0f
        val pDead = RawTelemetryPacket(rsrpDbm = -140)
        assertEquals(0.0f, TelemetryNormalizer.normalizeCellularQuality(pDead), 0.001f)

        // Case 3: -65 dBm (excellent signal boundary) -> 1.0f
        val pExcellent = RawTelemetryPacket(rsrpDbm = -65)
        assertEquals(1.0f, TelemetryNormalizer.normalizeCellularQuality(pExcellent), 0.001f)

        // Case 4: Mid-tier signals
        val pMid = RawTelemetryPacket(rsrpDbm = -102) // (-102 + 140) / 75 = 38 / 75 = 0.506f
        val normMid = TelemetryNormalizer.normalizeCellularQuality(pMid)
        assertEquals(38f / 75f, normMid, 0.01f)
        assertTrue(normMid in 0.0f..1.0f)
    }

    @Test
    fun normalizeCellularQuality_pathologicalOutliers_clampedSafely() {
        val extremeCases = listOf(
            -200 to 0.0f,
            -141 to 0.0f,
            -64 to 1.0f,
            0 to 1.0f,
            100 to 1.0f,
            Int.MIN_VALUE to 0.0f,
            Int.MAX_VALUE to 1.0f
        )

        for ((input, expected) in extremeCases) {
            val packet = RawTelemetryPacket(rsrpDbm = input)
            val normalized = TelemetryNormalizer.normalizeCellularQuality(packet)
            assertEquals("RSRP $input must clamp to $expected", expected, normalized, 0.001f)
            assertTrue("Must be in [0.0..1.0]", normalized in 0.0f..1.0f)
            assertFalse(normalized.isNaN())
            assertFalse(normalized.isInfinite())
        }
    }

    private fun fourGb(): Long = 4L * 1024L * 1024L * 1024L
}
