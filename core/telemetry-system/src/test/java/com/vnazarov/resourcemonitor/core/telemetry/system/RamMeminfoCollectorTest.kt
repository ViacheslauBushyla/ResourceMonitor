package com.vnazarov.resourcemonitor.core.telemetry.system

import com.vnazarov.resourcemonitor.core.telemetry.system.collector.RamMeminfoCollector
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RamMeminfoCollectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun parseMeminfo_extractsTotalAndAvailableBytesAccurately() {
        val meminfoFile = tempFolder.newFile("meminfo_pixel8")
        meminfoFile.writeText(
            """
            MemTotal:        7754764 kB
            MemFree:          456789 kB
            MemAvailable:     859348 kB
            Buffers:          123456 kB
            Cached:          1234567 kB
            SwapTotal:       3877376 kB
            SwapFree:         108348 kB
            """.trimIndent()
        )

        val collector = RamMeminfoCollector(meminfoFile = meminfoFile)

        assertEquals(7754764L * 1024L, collector.getTotalBytes())
        assertEquals(859348L * 1024L, collector.getAvailableBytes())
        assertEquals((3877376L - 108348L) * 1024L, collector.getZramUsedBytes())
        assertEquals(250L, collector.getCompactStallsCount()) // High swap pressure > 85%
    }

    @Test
    fun lowSwapUsage_indicatesZeroCompactionStalls() {
        val meminfoFile = tempFolder.newFile("meminfo_calm")
        meminfoFile.writeText(
            """
            MemTotal:        8000000 kB
            MemAvailable:    6000000 kB
            SwapTotal:       4000000 kB
            SwapFree:        3500000 kB
            """.trimIndent()
        )

        val collector = RamMeminfoCollector(meminfoFile = meminfoFile)

        assertEquals((4000000L - 3500000L) * 1024L, collector.getZramUsedBytes())
        assertEquals(0L, collector.getCompactStallsCount())
    }

    @Test
    fun missingFile_returnsFallbackZeroWithoutCrash() {
        val nonExistentFile = tempFolder.newFolder("empty").resolve("missing_meminfo")
        val collector = RamMeminfoCollector(meminfoFile = nonExistentFile)

        assertEquals(0L, collector.getTotalBytes())
        assertEquals(0L, collector.getAvailableBytes())
        assertEquals(0L, collector.getZramUsedBytes())
        assertEquals(0L, collector.getCompactStallsCount())
    }
}
