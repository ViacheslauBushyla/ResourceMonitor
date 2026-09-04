package com.vnazarov.resourcemonitor.core.telemetry.system

import com.vnazarov.resourcemonitor.core.telemetry.system.collector.NetworkTrafficCollector
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkTrafficCollectorTest {

    @Test
    fun sampleDelta_computesThroughputAccuratelyAcrossTimeDelta() {
        var simulatedRx = 10_000_000L
        var simulatedTx = 5_000_000L
        var simulatedTimeNs = 0L

        val collector = NetworkTrafficCollector(
            rxBytesProvider = { simulatedRx },
            txBytesProvider = { simulatedTx },
            nanoTimeProvider = { simulatedTimeNs }
        )

        // Advance 1 second and add 1,000,000 RX bytes and 500,000 TX bytes
        simulatedTimeNs = 1_000_000_000L
        simulatedRx += 1_000_000L
        simulatedTx += 500_000L

        collector.sampleDelta()

        assertEquals(1_000_000L, collector.getRxBytesPerSec())
        assertEquals(500_000L, collector.getTxBytesPerSec())
    }

    @Test
    fun sampleDelta_whenNoTraffic_reportsZeroThroughput() {
        var simulatedTimeNs = 0L
        val fixedRx = 50_000_000L
        val fixedTx = 25_000_000L

        val collector = NetworkTrafficCollector(
            rxBytesProvider = { fixedRx },
            txBytesProvider = { fixedTx },
            nanoTimeProvider = { simulatedTimeNs }
        )

        simulatedTimeNs = 1_000_000_000L
        collector.sampleDelta()

        assertEquals(0L, collector.getRxBytesPerSec())
        assertEquals(0L, collector.getTxBytesPerSec())
    }

    @Test
    fun sampleDelta_whenCounterRolloverOccurs_handlesGracefullyWithoutNegativeValues() {
        var simulatedRx = 100_000_000L
        var simulatedTx = 50_000_000L
        var simulatedTimeNs = 0L

        val collector = NetworkTrafficCollector(
            rxBytesProvider = { simulatedRx },
            txBytesProvider = { simulatedTx },
            nanoTimeProvider = { simulatedTimeNs }
        )

        // Interface resets / counters reset to 1000 bytes
        simulatedTimeNs = 1_000_000_000L
        simulatedRx = 1_000L
        simulatedTx = 500L

        collector.sampleDelta()

        assertEquals(0L, collector.getRxBytesPerSec())
        assertEquals(0L, collector.getTxBytesPerSec())
    }
}
