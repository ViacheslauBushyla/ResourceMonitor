package com.vnazarov.resourcemonitor.integration

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.vnazarov.resourcemonitor.animations.hologram.HologramBehaviorConfig
import com.vnazarov.resourcemonitor.animations.hologram.HologramProjectionCalculator
import com.vnazarov.resourcemonitor.animations.hologram.SpeedCurve
import com.vnazarov.resourcemonitor.core.config.DataStoreHudSettingsRepository
import com.vnazarov.resourcemonitor.core.config.HudSettings
import com.vnazarov.resourcemonitor.core.model.MetricValue
import com.vnazarov.resourcemonitor.core.model.SystemTelemetrySnapshot
import com.vnazarov.resourcemonitor.core.telemetry.fusion.TelemetryFusionEngine
import com.vnazarov.resourcemonitor.core.telemetry.mock.FakeTelemetrySource
import com.vnazarov.resourcemonitor.core.telemetry.system.RealTelemetrySource
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.CpuFreqCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.NetworkTrafficCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.RamMeminfoCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.StorageIoCollector
import com.vnazarov.resourcemonitor.core.telemetry.system.collector.ThermalSystemCollector
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
class HudSettingsStateAndProjectionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var repository: DataStoreHudSettingsRepository
    private lateinit var realSource: RealTelemetrySource
    private lateinit var fakeSource: FakeTelemetrySource
    private lateinit var fusionEngine: TelemetryFusionEngine

    @Before
    fun setUp() {
        val testFile = tempFolder.newFile("hud_settings_test.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        repository = DataStoreHudSettingsRepository(dataStore)

        realSource = RealTelemetrySource(
            cpuCollector = CpuFreqCollector(context = null, cpuBaseDir = tempFolder.root),
            ramCollector = RamMeminfoCollector(context = null),
            networkCollector = NetworkTrafficCollector(context = null, rxBytesProvider = { 0L }, txBytesProvider = { 0L }),
            storageCollector = StorageIoCollector(context = null, probeDirectory = tempFolder.root, storageDirectory = tempFolder.root),
            thermalCollector = ThermalSystemCollector(context = null)
        )
        fakeSource = FakeTelemetrySource()

        fusionEngine = TelemetryFusionEngine(
            telemetrySource = realSource,
            realSource = realSource,
            fakeSource = fakeSource
        )
    }

    @Test
    fun testSettingsRepository_persistsAndRestoresAllControls() = runTest(testDispatcher) {
        val initial = repository.getSettings()
        assertFalse("Overlay must initially be disabled", initial.isOverlayEnabled)
        assertEquals("REAL", initial.telemetrySourceMode)
        assertEquals(104.0f, initial.haloDiameterDp, 0.01f)

        // 1. Update Overlay Enabled
        repository.updateOverlayEnabled(true)
        assertTrue(repository.getSettings().isOverlayEnabled)

        // 2. Update Telemetry Source Mode
        repository.updateTelemetrySourceMode("MOCK")
        assertEquals("MOCK", repository.getSettings().telemetrySourceMode)

        // 3. Update Halo Diameter
        repository.updateHaloDiameter(72.0f)
        assertEquals(72.0f, repository.getSettings().haloDiameterDp, 0.01f)

        // 4. Update Speed Range
        repository.updateSpeedRange(vMin = 0.5f, vMax = 8.0f)
        val sSpeed = repository.getSettings()
        assertEquals(0.5f, sSpeed.vMinRps, 0.01f)
        assertEquals(8.0f, sSpeed.vMaxRps, 0.01f)

        // 5. Update Speed Curve
        repository.updateSpeedCurve("LINEAR")
        assertEquals("LINEAR", repository.getSettings().speedCurve)

        // 6. Update Sensitivities
        repository.updateSensitivities(outer = 1.5f, middle = 0.8f, inner = 1.2f)
        val sSens = repository.getSettings()
        assertEquals(1.5f, sSens.outerSensitivity, 0.01f)
        assertEquals(0.8f, sSens.middleSensitivity, 0.01f)
        assertEquals(1.2f, sSens.innerSensitivity, 0.01f)

        // 7. Reset Defaults
        repository.resetDefaults()
        val reset = repository.getSettings()
        assertFalse(reset.isOverlayEnabled)
        assertEquals("REAL", reset.telemetrySourceMode)
        assertEquals(104.0f, reset.haloDiameterDp, 0.01f)
        assertEquals(0.2f, reset.vMinRps, 0.01f)
        assertEquals(5.0f, reset.vMaxRps, 0.01f)
    }

    @Test
    fun testHologramProjection_adaptsDeterministicallyToSettings() = runTest(testDispatcher) {
        val snapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.50f),
            ram = MetricValue(smoothedValue = 0.50f),
            network = MetricValue(smoothedValue = 0.50f),
            storageIo = MetricValue(smoothedValue = 0.50f),
            gpu = MetricValue(smoothedValue = 0.50f)
        )

        // Default: QUADRATIC, vMin 0.2, vMax 5.0, range 4.8 -> 0.2 + 0.25 * 4.8 = 1.40 RPS
        val defaultConfig = HologramBehaviorConfig()
        val defaultParams = HologramProjectionCalculator.computeParameters(snapshot, defaultConfig)
        assertEquals(1.400f, defaultParams.ring1SpeedRps, 0.01f)
        assertEquals(1.400f, defaultParams.ring2SpeedRps, 0.01f)

        // Modified: LINEAR curve -> 0.2 + 0.50 * 4.8 = 2.60 RPS
        val linearConfig = defaultConfig.copy(speedCurve = SpeedCurve.LINEAR)
        val linearParams = HologramProjectionCalculator.computeParameters(snapshot, linearConfig)
        assertEquals(2.600f, linearParams.ring1SpeedRps, 0.01f)

        // Modified: Custom Speed Range (vMin = 0.5, vMax = 8.5, range = 8.0)
        // QUADRATIC at 0.50 load: 0.5 + 0.25 * 8.0 = 2.50 RPS
        val rangeConfig = defaultConfig.copy(vMinRps = 0.5f, vMaxRps = 8.5f)
        val rangeParams = HologramProjectionCalculator.computeParameters(snapshot, rangeConfig)
        assertEquals(2.500f, rangeParams.ring1SpeedRps, 0.01f)

        // Modified: Sensitivity Multipliers (outer = 1.5x, middle = 0.5x)
        // Outer clamped = 0.50 * 1.5 = 0.75 -> 0.2 + 0.5625 * 4.8 = 2.90 RPS
        // Middle clamped = 0.50 * 0.5 = 0.25 -> 0.2 + 0.0625 * 4.8 = 0.50 RPS
        val sensConfig = defaultConfig.copy(outerSensitivity = 1.5f, middleSensitivity = 0.5f)
        val sensParams = HologramProjectionCalculator.computeParameters(snapshot, sensConfig)
        assertEquals(2.900f, sensParams.ring1SpeedRps, 0.01f)
        assertEquals(0.500f, sensParams.ring2SpeedRps, 0.01f)
    }

    @Test
    fun testHaloRadiusDerivation_fromSettingsDiameter() {
        val testDiameters = listOf(48f to 24f, 56f to 28f, 64f to 32f, 72f to 36f, 80f to 40f)
        for ((diameter, expectedRadius) in testDiameters) {
            val settings = HudSettings(haloDiameterDp = diameter)
            val derivedRadius = settings.haloDiameterDp / 2f
            assertEquals(expectedRadius, derivedRadius, 0.001f)
        }
    }

    @Test
    fun testTelemetryFusionEngine_dynamicSourceSwitching() {
        assertEquals("REAL", fusionEngine.sourceMode)
        assertTrue(fusionEngine.getTelemetrySource() is RealTelemetrySource)

        fusionEngine.switchSource("MOCK")
        assertEquals("MOCK", fusionEngine.sourceMode)
        assertTrue(fusionEngine.getTelemetrySource() is FakeTelemetrySource)

        fusionEngine.switchSource("REAL")
        assertEquals("REAL", fusionEngine.sourceMode)
        assertTrue(fusionEngine.getTelemetrySource() is RealTelemetrySource)
    }
}
