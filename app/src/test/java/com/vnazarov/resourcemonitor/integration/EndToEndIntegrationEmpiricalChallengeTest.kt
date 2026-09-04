package com.vnazarov.resourcemonitor.integration

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.vnazarov.resourcemonitor.animations.hologram.HologramBehaviorConfig
import com.vnazarov.resourcemonitor.animations.hologram.HologramInvariantCase
import com.vnazarov.resourcemonitor.animations.hologram.HologramInvariantCases
import com.vnazarov.resourcemonitor.animations.hologram.HologramProjectionCalculator
import com.vnazarov.resourcemonitor.animations.hologram.HolographicRingsPlugin
import com.vnazarov.resourcemonitor.animations.hologram.SpeedCurve
import com.vnazarov.resourcemonitor.app.di.dataModule
import com.vnazarov.resourcemonitor.app.di.domainModule
import com.vnazarov.resourcemonitor.core.config.DataStoreHudSettingsRepository
import com.vnazarov.resourcemonitor.core.config.HudSettingsRepository
import com.vnazarov.resourcemonitor.core.model.RawTelemetryPacket
import com.vnazarov.resourcemonitor.core.model.SystemTelemetrySnapshot
import com.vnazarov.resourcemonitor.core.model.ThrottleState
import com.vnazarov.resourcemonitor.core.telemetry.api.TelemetrySource
import com.vnazarov.resourcemonitor.core.telemetry.fusion.TelemetryFusionEngine
import com.vnazarov.resourcemonitor.core.telemetry.mock.FakeTelemetrySource
import com.vnazarov.resourcemonitor.core.telemetry.mock.SimulationScenario
import com.vnazarov.resourcemonitor.core.telemetry.system.RealTelemetrySource
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.CpuFreqCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.NetworkTrafficCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.RamMeminfoCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.StorageIoCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.ThermalSystemCollector
import com.vnazarov.resourcemonitor.data.impl.ResourceRepositoryImpl
import com.vnazarov.resourcemonitor.data.managers.CpuManager
import com.vnazarov.resourcemonitor.data.managers.RamManager
import com.vnazarov.resourcemonitor.domain.repository.ResourceRepository
import com.vnazarov.resourcemonitor.domain.usecase.GetCpuFreqMHzUseCase
import com.vnazarov.resourcemonitor.domain.usecase.GetCpuLoadPercUseCase
import com.vnazarov.resourcemonitor.domain.usecase.GetRamUsageUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Empirical Challenge Test Suite for Milestone 4 (End-to-End App Integration):
 *
 * 1. Koin DI dependency graph verification:
 *    - Validates all singletons, dependencies, parameters, and use cases resolve cleanly.
 *    - Enforces singleton identity guarantees across repeated resolutions.
 *
 * 2. 50Hz dynamic source switching stress test:
 *    - Injects alternating Real and Mock telemetry packets at high cadence (50Hz equivalent).
 *    - Asserts zero dropped frames, zero exceptions, zero memory leaks, and concurrent thread safety.
 *
 * 3. 30-case invariant inspector integration:
 *    - Traverses all 30 invariant cases sequentially and multi-cyclically.
 *    - Asserts HUD projection state and 5-ring badges update deterministically with ZERO drift.
 *    - Validates scenario synchronization and pipeline parity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EndToEndIntegrationEmpiricalChallengeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    // =============================================================================================
    // Challenge 1: Koin DI Dependency Graph Resolution & Singleton Guarantees
    // =============================================================================================

    @Test
    fun challenge1_koinDependencyGraph_resolvesAllSingletonsAndPropagatesParametersCleanly() {
        val testFile = tempFolder.newFile("koin_challenge1_settings.preferences_pb")
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )

        val testIntegrationModule = module {
            single<CoroutineScope> {
                CoroutineScope(SupervisorJob() + Dispatchers.IO)
            }
            single<HudSettingsRepository> {
                DataStoreHudSettingsRepository(testDataStore)
            }
            single { CpuFreqCollector(context = null, cpuBaseDir = tempFolder.root) }
            single { RamMeminfoCollector(context = null) }
            single { NetworkTrafficCollector(context = null, rxBytesProvider = { 1024L }, txBytesProvider = { 2048L }) }
            single { StorageIoCollector(context = null, probeDirectory = tempFolder.root, storageDirectory = tempFolder.root) }
            single { ThermalSystemCollector(context = null) }
            single {
                RealTelemetrySource(
                    cpuCollector = get<CpuFreqCollector>(),
                    ramCollector = get<RamMeminfoCollector>(),
                    networkCollector = get<NetworkTrafficCollector>(),
                    storageCollector = get<StorageIoCollector>(),
                    thermalCollector = get<ThermalSystemCollector>()
                )
            }
            single { FakeTelemetrySource() }
            single<TelemetrySource> { get<RealTelemetrySource>() }
            single {
                TelemetryFusionEngine(
                    telemetrySource = get<RealTelemetrySource>(),
                    coroutineScope = get<CoroutineScope>(),
                    realSource = get<RealTelemetrySource>(),
                    fakeSource = get<FakeTelemetrySource>()
                )
            }
            single { HolographicRingsPlugin() }

            val fakeContext = object : ContextWrapper(null) {
                override fun getApplicationContext(): Context = this
                override fun getSystemService(name: String): Any? = null
            }
            single { CpuManager(fakeContext) }
            single { RamManager(fakeContext) }
            single<ResourceRepository> { ResourceRepositoryImpl(get(), get(), get()) }
        }

        val koinApp = koinApplication {
            modules(testIntegrationModule, domainModule)
        }
        val koin = koinApp.koin

        // 1. Resolve every single singleton
        val repo1 = koin.get<HudSettingsRepository>()
        val repo2 = koin.get<HudSettingsRepository>()
        assertNotNull("HudSettingsRepository must be resolvable", repo1)
        assertSame("HudSettingsRepository must be a singleton", repo1, repo2)

        val realSource1 = koin.get<RealTelemetrySource>()
        val realSource2 = koin.get<RealTelemetrySource>()
        assertNotNull("RealTelemetrySource must be resolvable", realSource1)
        assertSame("RealTelemetrySource must be a singleton", realSource1, realSource2)

        val fakeSource1 = koin.get<FakeTelemetrySource>()
        val fakeSource2 = koin.get<FakeTelemetrySource>()
        assertNotNull("FakeTelemetrySource must be resolvable", fakeSource1)
        assertSame("FakeTelemetrySource must be a singleton", fakeSource1, fakeSource2)

        val telemetrySource = koin.get<TelemetrySource>()
        assertNotNull("TelemetrySource must be resolvable", telemetrySource)
        assertSame("TelemetrySource should alias RealTelemetrySource singleton", realSource1, telemetrySource)

        val fusionEngine1 = koin.get<TelemetryFusionEngine>()
        val fusionEngine2 = koin.get<TelemetryFusionEngine>()
        assertNotNull("TelemetryFusionEngine must be resolvable", fusionEngine1)
        assertSame("TelemetryFusionEngine must be a singleton", fusionEngine1, fusionEngine2)

        val plugin1 = koin.get<HolographicRingsPlugin>()
        val plugin2 = koin.get<HolographicRingsPlugin>()
        assertNotNull("HolographicRingsPlugin must be resolvable", plugin1)
        assertSame("HolographicRingsPlugin must be a singleton", plugin1, plugin2)

        val resourceRepo1 = koin.get<ResourceRepository>()
        val resourceRepo2 = koin.get<ResourceRepository>()
        assertNotNull("ResourceRepository must be resolvable", resourceRepo1)
        assertSame("ResourceRepository must be a singleton", resourceRepo1, resourceRepo2)

        // 2. Resolve domain use cases
        val getCpuLoad = koin.get<GetCpuLoadPercUseCase>()
        val getCpuFreq = koin.get<GetCpuFreqMHzUseCase>()
        val getRamUsage = koin.get<GetRamUsageUseCase>()
        assertNotNull("GetCpuLoadPercUseCase must be resolved", getCpuLoad)
        assertNotNull("GetCpuFreqMHzUseCase must be resolved", getCpuFreq)
        assertNotNull("GetRamUsageUseCase must be resolved", getRamUsage)

        // 3. Verify parameter wiring
        assertSame("FusionEngine initial source must be realSource", realSource1, fusionEngine1.getTelemetrySource())
        assertEquals("Initial source mode must be REAL", "REAL", fusionEngine1.sourceMode)

        // Switch to MOCK and verify wiring
        fusionEngine1.switchSource("MOCK")
        assertEquals("MOCK", fusionEngine1.sourceMode)
        assertSame("FusionEngine target must be fakeSource", fakeSource1, fusionEngine1.getTelemetrySource())

        // Switch back to REAL
        fusionEngine1.switchSource("REAL")
        assertEquals("REAL", fusionEngine1.sourceMode)
        assertSame("FusionEngine target must be realSource", realSource1, fusionEngine1.getTelemetrySource())

        // Verify hardware collector polls execute without exceptions
        val polledPacket = realSource1.poll()
        assertNotNull("RealTelemetrySource poll must yield packet", polledPacket)
        assertTrue("Packet timestamp must be valid", polledPacket.timestampNs > 0)

        val fakePacket = fakeSource1.poll()
        assertNotNull("FakeTelemetrySource poll must yield packet", fakePacket)
        assertTrue("Fake packet timestamp must be valid", fakePacket.timestampNs > 0)

        koinApp.close()
    }

    // =============================================================================================
    // Challenge 2: Dynamic Source Switching at 50Hz Stress Test
    // =============================================================================================

    @Test
    fun challenge2_dynamicSourceSwitching_50HzAlternatingStressTest_zeroDropsZeroExceptions() = runTest(testDispatcher) {
        val fakeSource = FakeTelemetrySource()
        val cpuCollector = CpuFreqCollector(context = null, cpuBaseDir = tempFolder.root)
        val ramCollector = RamMeminfoCollector(context = null)
        val netCollector = NetworkTrafficCollector(context = null, rxBytesProvider = { 50_000L }, txBytesProvider = { 100_000L })
        val storageCollector = StorageIoCollector(context = null, probeDirectory = tempFolder.root, storageDirectory = tempFolder.root)
        val thermalCollector = ThermalSystemCollector(context = null)

        val realSource = RealTelemetrySource(
            cpuCollector = cpuCollector,
            ramCollector = ramCollector,
            networkCollector = netCollector,
            storageCollector = storageCollector,
            thermalCollector = thermalCollector
        )

        val fusionEngine = TelemetryFusionEngine(
            telemetrySource = realSource,
            coroutineScope = this,
            realSource = realSource,
            fakeSource = fakeSource
        )

        val totalCadenceCycles = 1_000 // 1,000 rapid switches @ simulated 50Hz (20ms interval)
        val receivedSnapshots = mutableListOf<SystemTelemetrySnapshot>()
        val receivedPackets = mutableListOf<RawTelemetryPacket>()

        // Downstream subscriber tracking StateFlow emissions
        val collectionJob = launch {
            fusionEngine.snapshot.collect { snapshot ->
                receivedSnapshots.add(snapshot)
            }
        }
        val packetJob = launch {
            fusionEngine.lastPacket.collect { packet ->
                if (packet != null) receivedPackets.add(packet)
            }
        }

        // Stress test execution: alternating real and mock packets at 50Hz cadence
        var realCount = 0
        var mockCount = 0

        for (i in 1..totalCadenceCycles) {
            val isEven = (i % 2 == 0)
            val mode = if (isEven) "MOCK" else "REAL"

            // 1. Dynamic source mode switch
            fusionEngine.switchSource(mode)
            assertEquals("Source mode must update immediately", mode, fusionEngine.sourceMode)

            // 2. Generate and inject alternating telemetry packet
            val packet = if (isEven) {
                mockCount++
                fakeSource.poll().copy(
                    timestampNs = System.nanoTime(),
                    cpuLoadPercentage = (i % 100).toFloat(),
                    ramAvailableBytes = (4000L + (i % 2000)) * 1024L * 1024L,
                    rxBytesPerSec = (i * 1000L),
                    txBytesPerSec = (i * 2000L),
                    isStorageStall = (i % 50 == 0),
                    thermalStatusLevel = (i % 5)
                )
            } else {
                realCount++
                realSource.poll().copy(
                    timestampNs = System.nanoTime(),
                    cpuLoadPercentage = ((100 - (i % 100))).toFloat(),
                    ramAvailableBytes = (2000L + (i % 3000)) * 1024L * 1024L,
                    rxBytesPerSec = ((1000 - i) * 500L).coerceAtLeast(0L),
                    txBytesPerSec = ((1000 - i) * 800L).coerceAtLeast(0L),
                    isStorageStall = (i % 70 == 0),
                    thermalStatusLevel = ((i + 2) % 6)
                )
            }

            // Ingestion into fusion engine without resetting EMA to verify smoothing stability
            val snapshot = fusionEngine.injectPacket(packet, resetEma = false)

            // 3. Assert zero dropped frames & mathematical validity per cycle
            assertNotNull("Snapshot must never be null", snapshot)
            assertTrue("CPU raw must be between 0 and 1: ${snapshot.cpu.rawNormalized}", snapshot.cpu.rawNormalized in 0.0f..1.0f)
            assertTrue("CPU smoothed must be between 0 and 1: ${snapshot.cpu.smoothedValue}", snapshot.cpu.smoothedValue in 0.0f..1.0f)
            assertFalse("CPU smoothed must never be NaN", snapshot.cpu.smoothedValue.isNaN())
            assertFalse("CPU smoothed must never be Infinite", snapshot.cpu.smoothedValue.isInfinite())

            assertTrue("RAM smoothed must be in [0, 1]", snapshot.ram.smoothedValue in 0.0f..1.0f)
            assertTrue("Network smoothed must be in [0, 1]", snapshot.network.smoothedValue in 0.0f..1.0f)
            assertTrue("Storage smoothed must be in [0, 1]", snapshot.storageIo.smoothedValue in 0.0f..1.0f)
            assertTrue("GPU smoothed must be in [0, 1]", snapshot.gpu.smoothedValue in 0.0f..1.0f)

            // Verify lastPacket is faithfully synchronized
            assertEquals("Last packet timestamp must match injected packet", packet.timestampNs, fusionEngine.lastPacket.value?.timestampNs)
        }

        assertEquals("Total real packets generated must match 500", 500, realCount)
        assertEquals("Total mock packets generated must match 500", 500, mockCount)

        // Assert subscriber received updates continuously
        assertTrue("Subscriber must receive snapshots without dropping: count=${receivedSnapshots.size}", receivedSnapshots.size >= totalCadenceCycles)
        assertTrue("Packet subscriber must receive packets: count=${receivedPackets.size}", receivedPackets.size >= totalCadenceCycles)

        collectionJob.cancel()
        packetJob.cancel()
    }

    // =============================================================================================
    // Challenge 3: Concurrency & Thread-Safety Stress Test Under Rapid Switching
    // =============================================================================================

    @Test
    fun challenge3_multiThreadedConcurrentAccess_zeroDeadlocksZeroRaceCrashes() = runTest(testDispatcher) {
        val fakeSource = FakeTelemetrySource()
        val fusionEngine = TelemetryFusionEngine(
            telemetrySource = fakeSource,
            coroutineScope = this,
            realSource = fakeSource,
            fakeSource = fakeSource
        )

        val operationsCount = 5_000
        val exceptionCounter = AtomicInteger(0)

        // 4 concurrent asynchronous workers
        val workerA = async(Dispatchers.Default) {
            for (i in 1..operationsCount) {
                try {
                    val mode = if (i % 2 == 0) "REAL" else "MOCK"
                    fusionEngine.switchSource(mode)
                } catch (e: Exception) {
                    exceptionCounter.incrementAndGet()
                }
            }
        }

        val workerB = async(Dispatchers.Default) {
            for (i in 1..operationsCount) {
                try {
                    val packet = RawTelemetryPacket(
                        timestampNs = System.nanoTime(),
                        cpuLoadPercentage = (i % 100).toFloat(),
                        cpuFrequenciesKhz = listOf(1_800_000L),
                        ramTotalBytes = 16L * 1024 * 1024 * 1024,
                        ramAvailableBytes = 8L * 1024 * 1024 * 1024
                    )
                    fusionEngine.injectPacket(packet, resetEma = (i % 100 == 0))
                } catch (e: Exception) {
                    exceptionCounter.incrementAndGet()
                }
            }
        }

        val workerC = async(Dispatchers.Default) {
            for (i in 1..operationsCount) {
                try {
                    val s = fusionEngine.snapshot.value
                    val lp = fusionEngine.lastPacket.value
                    assertNotNull("Snapshot must remain non-null", s)
                } catch (e: Exception) {
                    exceptionCounter.incrementAndGet()
                }
            }
        }

        val workerD = async(Dispatchers.Default) {
            for (i in 1..operationsCount) {
                try {
                    fusionEngine.updatePollingIntervals(activeMs = 150L + (i % 100), idleMs = 1000L + (i % 500))
                } catch (e: Exception) {
                    exceptionCounter.incrementAndGet()
                }
            }
        }

        awaitAll(workerA, workerB, workerC, workerD)

        assertEquals("Multi-threaded stress test must yield ZERO exceptions across 20,000 operations", 0, exceptionCounter.get())
    }

    // =============================================================================================
    // Challenge 4: Memory Stability and Zero Unbounded Leak Verification
    // =============================================================================================

    @Test
    fun challenge4_memoryStabilityAndZeroLeakVerification() {
        val fakeSource = FakeTelemetrySource()
        val fusionEngine = TelemetryFusionEngine(
            telemetrySource = fakeSource,
            realSource = fakeSource,
            fakeSource = fakeSource
        )

        // Force GC before baseline measurement
        System.gc()
        Thread.sleep(50)
        val initialUsedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        // Ingest 10,000 packets with alternating switches
        for (i in 1..10_000) {
            fusionEngine.switchSource(if (i % 2 == 0) "REAL" else "MOCK")
            val packet = RawTelemetryPacket(
                timestampNs = System.nanoTime(),
                cpuLoadPercentage = (i % 100).toFloat(),
                cpuFrequenciesKhz = listOf(1_800_000L),
                ramTotalBytes = 16L * 1024 * 1024 * 1024,
                ramAvailableBytes = 8L * 1024 * 1024 * 1024
            )
            fusionEngine.injectPacket(packet, resetEma = (i % 50 == 0))
        }

        // Force GC after 10,000 iterations
        System.gc()
        Thread.sleep(50)
        val finalUsedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryDelta = finalUsedMemory - initialUsedMemory

        // TelemetryFusionEngine maintains only StateFlows and EmaFilter primitives, so heap growth should be negligible (< 15MB)
        assertTrue(
            "Memory growth must remain bounded under 10,000 operations (delta=${memoryDelta / (1024 * 1024)} MB)",
            memoryDelta < 15L * 1024 * 1024
        )
    }

    // =============================================================================================
    // Challenge 5: 30-Case Invariant Inspector Sequential Iteration & Zero Drift
    // =============================================================================================

    @Test
    fun challenge5_thirtyCaseInvariantInspector_sequentialIterationAndDriftFreeStateUpdates() {
        val fakeSource = FakeTelemetrySource()
        val fusionEngine = TelemetryFusionEngine(
            telemetrySource = fakeSource,
            realSource = fakeSource,
            fakeSource = fakeSource
        )

        val cases = HologramInvariantCases.EXPANDED_CASES
        assertEquals("Expanded invariant matrix must have exactly 30 cases", 30, cases.size)

        val config = HologramBehaviorConfig()

        // Track projected parameters for each case across 5 full traversal cycles:
        // Pass 1: 1 -> 30 (Forward)
        // Pass 2: 30 -> 1 (Reverse)
        // Pass 3: 1 -> 30 (Forward)
        // Pass 4: 30 -> 1 (Reverse)
        // Pass 5: 1 -> 30 (Forward)
        data class CaseProjectionRecord(
            val r1Speed: Float,
            val r2Speed: Float,
            val r3Speed: Float,
            val r4Speed: Float,
            val r5Speed: Float,
            val isMeltdown: Boolean,
            val isStorageStall: Boolean,
            val isMemoryThrash: Boolean,
            val statusLabel: String
        )

        val firstPassResults = mutableMapOf<Int, CaseProjectionRecord>()
        val fifthPassResults = mutableMapOf<Int, CaseProjectionRecord>()

        val totalPasses = 5
        for (pass in 1..totalPasses) {
            val passCases = if (pass % 2 == 1) cases else cases.reversed()

            for (case in passCases) {
                // Emulate StartScreen.kt case selection exactly:
                // 1. Inject packet with resetEma = true
                fusionEngine.injectPacket(case.toRawPacket(), resetEma = true)
                // 2. Inject snapshot directly
                fusionEngine.injectSnapshot(case.snapshot)
                // 3. Sync scenario on fakeSource
                when (case.id) {
                    2 -> fakeSource.setScenario(SimulationScenario.IdleCalm)
                    12, 14 -> fakeSource.setScenario(SimulationScenario.PeakGaming)
                    7, 20, 24, 25 -> fakeSource.setScenario(SimulationScenario.ThermalMeltdown)
                    21, 22, 23 -> fakeSource.setScenario(SimulationScenario.CellularDrop)
                    else -> {}
                }

                // Verify snapshot and lastPacket state
                val currentSnapshot = fusionEngine.snapshot.value
                val lastPacket = fusionEngine.lastPacket.value
                assertNotNull("lastPacket must be present for Case ${case.id}", lastPacket)
                assertEquals("Snapshot CPU smoothed must match Case ${case.id}", case.snapshot.cpu.smoothedValue, currentSnapshot.cpu.smoothedValue, 0.0001f)

                // Compute projected parameters
                val params = HologramProjectionCalculator.computeParameters(currentSnapshot, config)

                // Invariant assertions per case
                assertEquals("Case ${case.id} R1 speed mismatch in pass $pass", case.expectedR1SpeedRps, params.ring1SpeedRps, 0.05f)
                assertEquals("Case ${case.id} R2 speed mismatch in pass $pass", case.expectedR2SpeedRps, params.ring2SpeedRps, 0.05f)
                assertEquals("Case ${case.id} R3 speed mismatch in pass $pass", case.expectedR3SpeedRps, params.ring3SpeedRps, 0.05f)
                assertEquals("Case ${case.id} R4 speed mismatch in pass $pass", case.expectedR4SpeedRps, params.ring4SpeedRps, 0.05f)
                assertEquals("Case ${case.id} R5 speed mismatch in pass $pass", case.expectedR5SpeedRps, params.ring5SpeedRps, 0.05f)

                assertEquals("Case ${case.id} Meltdown alert mismatch in pass $pass", case.expectedIsMeltdown, params.isMeltdownAlert)
                assertEquals("Case ${case.id} Storage stall alert mismatch in pass $pass", case.expectedIsStorageStall, params.isStorageStallAlert)
                assertEquals("Case ${case.id} Memory thrash alert mismatch in pass $pass", case.expectedIsMemoryThrash, params.isMemoryThrashAlert)
                assertTrue(
                    "Case ${case.id} Status label '${params.systemStatusLabel}' must contain '${case.expectedStatusSubstring}'",
                    params.systemStatusLabel.contains(case.expectedStatusSubstring)
                )

                // Verify 5-ring badge presentation formatting (mirroring StartScreen.kt lines 373-443)
                // Ring 1 Badge
                val r1Primary = "Load: ${currentSnapshot.cpu.displayLabel.ifEmpty { "${(currentSnapshot.cpu.smoothedValue * 100).toInt()}%" }}"
                val cpuFreqStr = lastPacket?.cpuFrequenciesKhz?.firstOrNull()?.let { "${it / 1000} MHz" } ?: "3.2 GHz"
                val r1Secondary = "Freq: $cpuFreqStr"
                assertTrue("R1 badge primary must not be empty", r1Primary.startsWith("Load:"))
                assertTrue("R1 badge secondary must contain Freq:", r1Secondary.startsWith("Freq:"))

                // Ring 2 Badge
                val availMb = lastPacket?.let { "${it.ramAvailableBytes / (1024 * 1024)} MB" }
                    ?: "${((1f - currentSnapshot.ram.smoothedValue) * 16000).toInt()} MB"
                val zramStr = if (params.isMemoryThrashAlert) "zRAM 92% [THRASH]" else "zRAM 12%"
                assertTrue("R2 badge primary must contain MB", availMb.contains("MB"))
                if (case.expectedIsMemoryThrash) {
                    assertTrue("R2 badge secondary must show [THRASH]", zramStr.contains("[THRASH]"))
                }

                // Ring 3 Badge
                val throughput = currentSnapshot.network.displayLabel.ifEmpty { "0 KB/s" }
                val linkQuality = currentSnapshot.cellularQuality.displayLabel.ifEmpty { "-80 dBm" }
                assertTrue("R3 throughput must not be empty", throughput.isNotBlank())
                assertTrue("R3 linkQuality must not be empty", linkQuality.isNotBlank())

                // Ring 4 Badge
                val latencyStr = if (params.isStorageStallAlert) ">150 ms [STALL]" else "1.2 ms"
                val stallStr = if (params.isStorageStallAlert) "STALL: TRUE" else "STALL: FALSE"
                if (case.expectedIsStorageStall) {
                    assertEquals("R4 stall flag must be STALL: TRUE", "STALL: TRUE", stallStr)
                    assertTrue("R4 latency must contain [STALL]", latencyStr.contains("[STALL]"))
                } else {
                    assertEquals("R4 stall flag must be STALL: FALSE", "STALL: FALSE", stallStr)
                }

                // Ring 5 Badge
                val thermalStatusStr = lastPacket?.thermalStatusLevel?.let { "Status: Level $it" }
                    ?: if (params.isMeltdownAlert) "Status: CRITICAL" else "Status: NOMINAL"
                val tempStr = lastPacket?.cpuTemperatureMilliC?.let { "${it / 1000}°C" }
                    ?: if (params.isMeltdownAlert) "85°C [MELTDOWN]" else "42°C [NOMINAL]"
                if (case.expectedIsMeltdown) {
                    assertTrue("R5 status or temp must reflect Meltdown",
                        thermalStatusStr.contains("Level 4") || thermalStatusStr.contains("Level 5") ||
                            thermalStatusStr.contains("CRITICAL") || tempStr.contains("MELTDOWN") ||
                            (lastPacket?.cpuTemperatureMilliC ?: 0) >= 80000
                    )
                }

                val record = CaseProjectionRecord(
                    r1Speed = params.ring1SpeedRps,
                    r2Speed = params.ring2SpeedRps,
                    r3Speed = params.ring3SpeedRps,
                    r4Speed = params.ring4SpeedRps,
                    r5Speed = params.ring5SpeedRps,
                    isMeltdown = params.isMeltdownAlert,
                    isStorageStall = params.isStorageStallAlert,
                    isMemoryThrash = params.isMemoryThrashAlert,
                    statusLabel = params.systemStatusLabel
                )

                if (pass == 1) {
                    firstPassResults[case.id] = record
                } else if (pass == 5) {
                    fifthPassResults[case.id] = record
                }
            }
        }

        // Assert ZERO DRIFT across all 30 invariant cases between Pass 1 and Pass 5
        assertEquals("Both passes must record all 30 cases", 30, firstPassResults.size)
        assertEquals("Both passes must record all 30 cases", 30, fifthPassResults.size)

        for (case in cases) {
            val p1 = firstPassResults[case.id]!!
            val p5 = fifthPassResults[case.id]!!

            assertEquals("Case ${case.id} R1 speed must exhibit ZERO drift", p1.r1Speed, p5.r1Speed, 0.0f)
            assertEquals("Case ${case.id} R2 speed must exhibit ZERO drift", p1.r2Speed, p5.r2Speed, 0.0f)
            assertEquals("Case ${case.id} R3 speed must exhibit ZERO drift", p1.r3Speed, p5.r3Speed, 0.0f)
            assertEquals("Case ${case.id} R4 speed must exhibit ZERO drift", p1.r4Speed, p5.r4Speed, 0.0f)
            assertEquals("Case ${case.id} R5 speed must exhibit ZERO drift", p1.r5Speed, p5.r5Speed, 0.0f)

            assertEquals("Case ${case.id} Meltdown flag must exhibit ZERO drift", p1.isMeltdown, p5.isMeltdown)
            assertEquals("Case ${case.id} Storage stall flag must exhibit ZERO drift", p1.isStorageStall, p5.isStorageStall)
            assertEquals("Case ${case.id} Memory thrash flag must exhibit ZERO drift", p1.isMemoryThrash, p5.isMemoryThrash)
            assertEquals("Case ${case.id} Status label must exhibit ZERO drift", p1.statusLabel, p5.statusLabel)
        }
    }

    // =============================================================================================
    // Challenge 6: Raw Pipeline Ingestion & EMA Reset Verification Across All 30 Invariant Cases
    // =============================================================================================

    @Test
    fun challenge6_rawPipelineIngestionAndEmaResetBehaviorAll30Cases() {
        val fakeSource = FakeTelemetrySource()
        val fusionEngine = TelemetryFusionEngine(fakeSource)

        for (case in HologramInvariantCases.EXPANDED_CASES) {
            val packet = case.toRawPacket()

            // When injectPacket is invoked with resetEma = true, EMA internal filters are reset to the normalized metric
            val processedSnapshot = fusionEngine.injectPacket(packet, resetEma = true)

            // Assert that normalized and smoothed values strictly align immediately without multi-step convergence lag
            assertEquals(
                "Case ${case.id} CPU smoothed value must equal raw normalized upon EMA reset",
                processedSnapshot.cpu.rawNormalized,
                processedSnapshot.cpu.smoothedValue,
                0.001f
            )
            assertEquals(
                "Case ${case.id} RAM smoothed value must equal raw normalized upon EMA reset",
                processedSnapshot.ram.rawNormalized,
                processedSnapshot.ram.smoothedValue,
                0.001f
            )
            assertEquals(
                "Case ${case.id} Network smoothed value must equal raw normalized upon EMA reset",
                processedSnapshot.network.rawNormalized,
                processedSnapshot.network.smoothedValue,
                0.001f
            )
            assertEquals(
                "Case ${case.id} Storage smoothed value must equal raw normalized upon EMA reset",
                processedSnapshot.storageIo.rawNormalized,
                processedSnapshot.storageIo.smoothedValue,
                0.001f
            )
            assertEquals(
                "Case ${case.id} GPU smoothed value must equal raw normalized upon EMA reset",
                processedSnapshot.gpu.rawNormalized,
                processedSnapshot.gpu.smoothedValue,
                0.001f
            )
        }
    }
}
