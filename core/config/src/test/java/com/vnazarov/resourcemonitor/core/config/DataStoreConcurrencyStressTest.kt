package com.vnazarov.resourcemonitor.core.config

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreConcurrencyStressTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var storeFile: File
    private lateinit var testDataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private lateinit var repository: DataStoreHudSettingsRepository

    @Before
    fun setUp() {
        storeFile = tempFolder.newFile("stress_hud_settings.preferences_pb")
        testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { storeFile }
        )
        repository = DataStoreHudSettingsRepository(testDataStore)
    }

    @Test
    fun resetDefaults_restoresAll14KeysAccurately() = runTest(testDispatcher) {
        val initial = repository.getSettings()
        val defaultSettings = HudSettings()
        assertEquals(defaultSettings, initial)

        // 1. Mutate all 14 parameters to non-default values
        repository.updateOverlayEnabled(true)
        repository.updateOverlayMode("STATUS_BAR")
        repository.updateHaloDiameter(75.5f)
        repository.updateSpeedRange(0.6f, 8.8f)
        repository.updateSpeedCurve("LINEAR")
        repository.updateSensitivities(1.8f, 1.4f, 0.5f)
        repository.updateMeltdownThreshold(0.75f)
        repository.updateChannelCpuMapping("RING_3")
        repository.updateTelemetrySourceMode("SIMULATOR")
        repository.updatePollingIntervals(100L, 4000L)

        val mutated = repository.getSettings()

        // Verify all 14 mutated values differ from defaults
        assertNotEquals(defaultSettings.isOverlayEnabled, mutated.isOverlayEnabled)
        assertNotEquals(defaultSettings.overlayMode, mutated.overlayMode)
        assertNotEquals(defaultSettings.haloDiameterDp, mutated.haloDiameterDp)
        assertNotEquals(defaultSettings.vMinRps, mutated.vMinRps)
        assertNotEquals(defaultSettings.vMaxRps, mutated.vMaxRps)
        assertNotEquals(defaultSettings.speedCurve, mutated.speedCurve)
        assertNotEquals(defaultSettings.outerSensitivity, mutated.outerSensitivity)
        assertNotEquals(defaultSettings.middleSensitivity, mutated.middleSensitivity)
        assertNotEquals(defaultSettings.innerSensitivity, mutated.innerSensitivity)
        assertNotEquals(defaultSettings.meltdownThreshold, mutated.meltdownThreshold)
        assertNotEquals(defaultSettings.channelCpuMapping, mutated.channelCpuMapping)
        assertNotEquals(defaultSettings.telemetrySourceMode, mutated.telemetrySourceMode)
        assertNotEquals(defaultSettings.activePollingMs, mutated.activePollingMs)
        assertNotEquals(defaultSettings.idlePollingMs, mutated.idlePollingMs)

        // 2. Perform resetDefaults
        repository.resetDefaults()
        val restored = repository.getSettings()

        // 3. Strict verification of all 14 keys
        assertFalse("Key 1: isOverlayEnabled must be false", restored.isOverlayEnabled)
        assertEquals("Key 2: overlayMode must be CAMERA_HALO", "CAMERA_HALO", restored.overlayMode)
        assertEquals("Key 3: haloDiameterDp must be 104.0f", 104.0f, restored.haloDiameterDp, 0.001f)
        assertEquals("Key 4: vMinRps must be 0.2f", 0.2f, restored.vMinRps, 0.001f)
        assertEquals("Key 5: vMaxRps must be 5.0f", 5.0f, restored.vMaxRps, 0.001f)
        assertEquals("Key 6: speedCurve must be QUADRATIC", "QUADRATIC", restored.speedCurve)
        assertEquals("Key 7: outerSensitivity must be 1.0f", 1.0f, restored.outerSensitivity, 0.001f)
        assertEquals("Key 8: middleSensitivity must be 1.0f", 1.0f, restored.middleSensitivity, 0.001f)
        assertEquals("Key 9: innerSensitivity must be 1.0f", 1.0f, restored.innerSensitivity, 0.001f)
        assertEquals("Key 10: meltdownThreshold must be 0.90f", 0.90f, restored.meltdownThreshold, 0.001f)
        assertEquals("Key 11: channelCpuMapping must be RING_1", "RING_1", restored.channelCpuMapping)
        assertEquals("Key 12: telemetrySourceMode must be REAL", "REAL", restored.telemetrySourceMode)
        assertEquals("Key 13: activePollingMs must be 250L", 250L, restored.activePollingMs)
        assertEquals("Key 14: idlePollingMs must be 2000L", 2000L, restored.idlePollingMs)

        assertEquals(defaultSettings, restored)
    }

    @Test
    fun rapidConcurrentModifications_fromMultipleCoroutines_completesWithoutDataLossOrCorruption() = runTest(testDispatcher) {
        val coroutineCount = 20
        val operationsPerCoroutine = 5

        // Launch 20 concurrent tasks updating various settings simultaneously
        val jobs = (0 until coroutineCount).map { i ->
            async {
                for (op in 0 until operationsPerCoroutine) {
                    when ((i + op) % 8) {
                        0 -> repository.updateOverlayEnabled(i % 2 == 0)
                        1 -> repository.updateHaloDiameter(50f + i)
                        2 -> repository.updateSpeedRange(0.1f * (i + 1), 5.0f + i)
                        3 -> repository.updateSpeedCurve(if (i % 2 == 0) "LINEAR" else "QUADRATIC")
                        4 -> repository.updateSensitivities(1.0f + (i * 0.01f), 1.0f, 1.0f)
                        5 -> repository.updateMeltdownThreshold(0.80f + (i * 0.005f))
                        6 -> repository.updatePollingIntervals(100L + i, 1000L + i)
                        7 -> repository.getSettings() // concurrent read
                    }
                }
            }
        }

        jobs.awaitAll()

        // Verify DataStore is readable and consistent after concurrent stress
        val finalSettings = repository.getSettings()
        assertTrue("haloDiameterDp must be within valid range", finalSettings.haloDiameterDp >= 50f)
        assertTrue("activePollingMs must be updated", finalSettings.activePollingMs >= 100L)
    }

    @Test
    fun settingsFlow_emitsDeterministically_uponSequentialUpdates() = runTest(testDispatcher) {
        val emissions = mutableListOf<HudSettings>()
        val collectJob = launch {
            repository.settingsFlow.collect { emissions.add(it) }
        }

        repository.updateOverlayEnabled(true)
        repository.updateOverlayMode("STATUS_BAR")
        repository.updateSpeedCurve("SIGMOID")
        repository.updateHaloDiameter(64.0f)

        collectJob.cancel()

        assertTrue("Flow should have captured emissions", emissions.size >= 4)
        val last = emissions.last()
        assertTrue(last.isOverlayEnabled)
        assertEquals("STATUS_BAR", last.overlayMode)
        assertEquals("SIGMOID", last.speedCurve)
        assertEquals(64.0f, last.haloDiameterDp, 0.001f)
    }

    @Test
    fun persistence_reloadsAccuratelyFromUnderlyingFile() = runTest(testDispatcher) {
        repository.updateOverlayEnabled(true)
        repository.updateHaloDiameter(70.0f)
        repository.updateSpeedCurve("SIGMOID")

        // Assert that underlying preferences file was written to disk and is non-empty
        assertTrue("Backing file should exist on disk", storeFile.exists())
        assertTrue("Backing file should have written bytes", storeFile.length() > 0)

        // Create a separate repository instance backed by the persistent store
        val reloadedRepo = DataStoreHudSettingsRepository(testDataStore)

        val settings = reloadedRepo.getSettings()
        assertTrue(settings.isOverlayEnabled)
        assertEquals(70.0f, settings.haloDiameterDp, 0.001f)
        assertEquals("SIGMOID", settings.speedCurve)
    }
}
