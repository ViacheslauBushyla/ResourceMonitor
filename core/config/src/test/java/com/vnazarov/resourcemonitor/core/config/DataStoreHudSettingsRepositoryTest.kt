package com.vnazarov.resourcemonitor.core.config

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreHudSettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var repository: DataStoreHudSettingsRepository

    @Before
    fun setUp() {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tempFolder.newFile("test_hud_settings.preferences_pb") }
        )
        repository = DataStoreHudSettingsRepository(testDataStore)
    }

    @Test
    fun defaultSettings_returnsCorrectInitialValues() = runTest(testDispatcher) {
        val settings = repository.getSettings()

        assertFalse(settings.isOverlayEnabled)
        assertEquals("CAMERA_HALO", settings.overlayMode)
        assertEquals(104.0f, settings.haloDiameterDp, 0.001f)
        assertEquals(0.2f, settings.vMinRps, 0.001f)
        assertEquals(5.0f, settings.vMaxRps, 0.001f)
        assertEquals("QUADRATIC", settings.speedCurve)
        assertEquals(1.0f, settings.outerSensitivity, 0.001f)
        assertEquals(1.0f, settings.middleSensitivity, 0.001f)
        assertEquals(1.0f, settings.innerSensitivity, 0.001f)
        assertEquals(0.90f, settings.meltdownThreshold, 0.001f)
        assertEquals("RING_1", settings.channelCpuMapping)
        assertEquals("REAL", settings.telemetrySourceMode)
        assertEquals(250L, settings.activePollingMs)
        assertEquals(2000L, settings.idlePollingMs)
    }

    @Test
    fun updateOverlayEnabled_updatesAndEmitsNewValue() = runTest(testDispatcher) {
        repository.updateOverlayEnabled(true)
        val updated = repository.getSettings()
        assertTrue(updated.isOverlayEnabled)
    }

    @Test
    fun updateOverlayMode_persistsNewMode() = runTest(testDispatcher) {
        repository.updateOverlayMode("STATUS_BAR")
        assertEquals("STATUS_BAR", repository.getSettings().overlayMode)
    }

    @Test
    fun updateHaloDiameter_persistsNewDiameter() = runTest(testDispatcher) {
        repository.updateHaloDiameter(68.5f)
        assertEquals(68.5f, repository.getSettings().haloDiameterDp, 0.001f)
    }

    @Test
    fun updateSpeedRange_persistsMinAndMaxRps() = runTest(testDispatcher) {
        repository.updateSpeedRange(0.5f, 8.0f)
        val settings = repository.getSettings()
        assertEquals(0.5f, settings.vMinRps, 0.001f)
        assertEquals(8.0f, settings.vMaxRps, 0.001f)
    }

    @Test
    fun updateSpeedCurve_persistsCurveName() = runTest(testDispatcher) {
        repository.updateSpeedCurve("LINEAR")
        assertEquals("LINEAR", repository.getSettings().speedCurve)
    }

    @Test
    fun updateSensitivities_persistsAllThreeRingSensitivities() = runTest(testDispatcher) {
        repository.updateSensitivities(0.8f, 1.2f, 1.5f)
        val settings = repository.getSettings()
        assertEquals(0.8f, settings.outerSensitivity, 0.001f)
        assertEquals(1.2f, settings.middleSensitivity, 0.001f)
        assertEquals(1.5f, settings.innerSensitivity, 0.001f)
    }

    @Test
    fun updateMeltdownThreshold_persistsThreshold() = runTest(testDispatcher) {
        repository.updateMeltdownThreshold(0.85f)
        assertEquals(0.85f, repository.getSettings().meltdownThreshold, 0.001f)
    }

    @Test
    fun updateChannelCpuMapping_persistsMapping() = runTest(testDispatcher) {
        repository.updateChannelCpuMapping("RING_2")
        assertEquals("RING_2", repository.getSettings().channelCpuMapping)
    }

    @Test
    fun updateTelemetrySourceMode_persistsMode() = runTest(testDispatcher) {
        repository.updateTelemetrySourceMode("SIMULATOR")
        assertEquals("SIMULATOR", repository.getSettings().telemetrySourceMode)
    }

    @Test
    fun updatePollingIntervals_persistsActiveAndIdle() = runTest(testDispatcher) {
        repository.updatePollingIntervals(100L, 1500L)
        val settings = repository.getSettings()
        assertEquals(100L, settings.activePollingMs)
        assertEquals(1500L, settings.idlePollingMs)
    }

    @Test
    fun resetDefaults_clearsAllOverridesBackToDefaults() = runTest(testDispatcher) {
        repository.updateOverlayEnabled(true)
        repository.updateSpeedRange(1.0f, 10.0f)
        repository.updateSpeedCurve("SIGMOID")
        repository.updateMeltdownThreshold(0.95f)

        repository.resetDefaults()
        val settings = repository.getSettings()

        assertFalse(settings.isOverlayEnabled)
        assertEquals(0.2f, settings.vMinRps, 0.001f)
        assertEquals(5.0f, settings.vMaxRps, 0.001f)
        assertEquals("QUADRATIC", settings.speedCurve)
        assertEquals(0.90f, settings.meltdownThreshold, 0.001f)
    }
}
