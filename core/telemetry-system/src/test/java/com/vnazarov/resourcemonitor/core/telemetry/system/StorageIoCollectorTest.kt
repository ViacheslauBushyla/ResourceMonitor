package com.vnazarov.resourcemonitor.core.telemetry.system

import com.vnazarov.resourcemonitor.core.telemetry.system.collector.StorageIoCollector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageIoCollectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun getWriteLatencyMs_writesProbeFileAndMeasuresExecution() {
        val probeDir = tempFolder.newFolder("probe")
        val storageDir = tempFolder.newFolder("storage")

        val collector = StorageIoCollector(
            probeDirectory = probeDir,
            storageDirectory = storageDir
        )

        val latencyMs = collector.getWriteLatencyMs()
        val probeFile = File(probeDir, ".io_latency_probe")

        assertTrue("Probe file must be created on disk", probeFile.exists())
        assertEquals("Probe file must be exactly 4096 bytes", 4096L, probeFile.length())
        assertTrue("Latency must be positive", latencyMs >= 0f)
        assertFalse("Standard write should not trigger storage stall", collector.isStorageStall())
    }

    @Test
    fun getIoWaitCycles_scalesWithLatency() {
        val probeDir = tempFolder.newFolder("probe_cycles")
        val collector = StorageIoCollector(probeDirectory = probeDir)

        val cycles = collector.getIoWaitCycles()
        val expectedCycles = (collector.lastWriteLatencyMs * 10).toLong()

        assertEquals("Cycles should exactly match 10x measured latency", expectedCycles, cycles)
        assertTrue("Cycles must be non-negative", cycles >= 0L)
    }
}
