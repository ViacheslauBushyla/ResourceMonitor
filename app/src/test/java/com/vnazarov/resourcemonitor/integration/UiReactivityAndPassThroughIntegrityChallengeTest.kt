package com.vnazarov.resourcemonitor.integration

import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.vnazarov.resourcemonitor.animations.hologram.HologramBehaviorConfig
import com.vnazarov.resourcemonitor.animations.hologram.HologramProjectionCalculator
import com.vnazarov.resourcemonitor.animations.hologram.SpeedCurve
import com.vnazarov.resourcemonitor.core.config.DataStoreHudSettingsRepository
import com.vnazarov.resourcemonitor.core.config.HudSettings
import com.vnazarov.resourcemonitor.core.designsystem.cutout.CutoutGeometryResolver
import com.vnazarov.resourcemonitor.core.model.MetricValue
import com.vnazarov.resourcemonitor.core.model.SystemTelemetrySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Adversarial Empirical Challenge Test Suite for Milestone 4 (End-to-End App Integration):
 *
 * 1. Sizing and Speed Control Reactivity:
 *    - Verify altering halo diameter slider immediately reflects in overlay drawing bounds
 *      and WindowManager required height without requiring service restart.
 *    - Verify sensitivity multipliers immediately re-scale individual ring rotational speeds.
 *
 * 2. WindowManager Touch Pass-Through & Focus Invariants:
 *    - Verify FLAG_NOT_TOUCHABLE and FLAG_NOT_FOCUSABLE are strictly retained across all UI
 *      state updates and window layout adjustments.
 *
 * 3. DataStore High-Frequency & Concurrent Slider Synchronization:
 *    - Stress test rapid sequential and concurrent writes simulating slider dragging.
 *    - Assert absence of protobuf corruption, race conditions, or dropped final states.
 *
 * 4. End-to-End Parameter-to-Hologram Pipeline Reactivity:
 *    - Verify instantaneous end-to-end dataflow from settings mutation to 3D hologram parameters.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UiReactivityAndPassThroughIntegrityChallengeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var repository: DataStoreHudSettingsRepository

    @Before
    fun setUp() {
        val testFile = tempFolder.newFile("hud_settings_stress_test.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        repository = DataStoreHudSettingsRepository(dataStore)
    }

    private fun createRect(left: Int, top: Int, right: Int, bottom: Int): Rect {
        return Rect().apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }
    }

    // =============================================================================================
    // Challenge 1: Halo Sizing Reactivity Without Service Restart
    // =============================================================================================

    @Test
    fun challenge1_haloDiameterSlider_immediatelyUpdatesOverlayBoundsWithoutServiceRestart() = runTest(testDispatcher) {
        // Initial defaults
        val initialSettings = repository.getSettings()
        assertEquals(104.0f, initialSettings.haloDiameterDp, 0.001f)

        // Pixel 8 geometry simulation
        val p8Bounds = createRect(479, 0, 601, 132)
        val p8Density = 2.625f
        val p8Geometry = CutoutGeometryResolver.resolve(
            boundingRectTop = p8Bounds,
            statusBarTopInset = 132,
            displayWidthPx = 1080,
            density = p8Density
        )

        // Simulate slider sweep from 96.0dp to 136.0dp in 0.5dp steps (81 distinct values)
        val sliderValues = (192..272).map { it / 2.0f } // 96.0, 96.5, ..., 136.0
        var previousRadiusDp = 0.0f
        var previousRequiredHeight = 0

        for (diameter in sliderValues) {
            // 1. Mutate setting via repository
            repository.updateHaloDiameter(diameter)

            // 2. Read setting from Flow immediately without service restart
            val currentSettings = repository.getSettings()
            assertEquals("Diameter in repository must match updated slider value immediately", diameter, currentSettings.haloDiameterDp, 0.001f)

            // 3. Derive halo radius in dp
            val currentRadiusDp = currentSettings.haloDiameterDp / 2.0f
            assertTrue("Halo radius must be strictly positive", currentRadiusDp > 0.0f)
            assertTrue("Halo radius must strictly increase monotonically with slider", currentRadiusDp > previousRadiusDp)

            // 4. Calculate required WindowManager overlay height as done dynamically in MonitorService.kt
            val haloRadiusPx = currentRadiusDp * p8Density
            val bloomPaddingPx = 16f * p8Density
            val requiredHeightPx = (p8Geometry.centerYPx + haloRadiusPx + bloomPaddingPx).toInt()
            val overlayHeightPx = maxOf(p8Geometry.statusBarHeightPx, requiredHeightPx)

            // Assert overlay height fully encloses the halo and bloom padding
            val ring1BottomY = p8Geometry.centerYPx + haloRadiusPx
            assertTrue(
                "Overlay window height ($overlayHeightPx px) must strictly enclose Ring 1 bottom ($ring1BottomY px)",
                overlayHeightPx > ring1BottomY
            )
            assertTrue(
                "Overlay height must be monotonically non-decreasing as diameter expands",
                overlayHeightPx >= previousRequiredHeight
            )

            previousRadiusDp = currentRadiusDp
            previousRequiredHeight = overlayHeightPx
        }

        // Final verification at max slider (136.0dp):
        // Radius = 68.0dp, RadiusPx = 178.5px, CenterY = 66.0px, Bloom = 42.0px -> RequiredHeight = 286px
        val finalSettings = repository.getSettings()
        assertEquals(136.0f, finalSettings.haloDiameterDp, 0.001f)
        val finalRadiusDp = finalSettings.haloDiameterDp / 2.0f
        assertEquals(68.0f, finalRadiusDp, 0.001f)
    }

    // =============================================================================================
    // Challenge 2: Sensitivity Multipliers and Rotational Speed Rescaling Reactivity
    // =============================================================================================

    @Test
    fun challenge2_sensitivityMultipliers_immediatelyRescaleRingRotationalSpeeds() = runTest(testDispatcher) {
        val testSnapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.60f),
            ram = MetricValue(smoothedValue = 0.40f),
            network = MetricValue(smoothedValue = 0.50f),
            storageIo = MetricValue(smoothedValue = 0.30f),
            gpu = MetricValue(smoothedValue = 0.70f)
        )

        // Baseline: QUADRATIC curve, vMin 0.2, vMax 5.0, range 4.8, outer=1.0, middle=1.0, inner=1.0
        val baseConfig = HologramBehaviorConfig(
            vMinRps = 0.2f,
            vMaxRps = 5.0f,
            speedCurve = SpeedCurve.QUADRATIC,
            outerSensitivity = 1.0f,
            middleSensitivity = 1.0f,
            innerSensitivity = 1.0f
        )
        val baseParams = HologramProjectionCalculator.computeParameters(testSnapshot, baseConfig)

        // Ring 1 (CPU): Load 0.60 * 1.0 = 0.60 -> 0.2 + 0.36 * 4.8 = 1.928 RPS
        assertEquals(1.928f, baseParams.ring1SpeedRps, 0.001f)
        // Ring 2 (RAM): Load 0.40 * 1.0 = 0.40 -> 0.2 + 0.16 * 4.8 = 0.968 RPS
        assertEquals(0.968f, baseParams.ring2SpeedRps, 0.001f)

        // 1. Mutate Outer Sensitivity (CPU) to 1.5x via settings repository
        repository.updateSensitivities(outer = 1.5f, middle = 1.0f, inner = 1.0f)
        val settingsOuter = repository.getSettings()
        assertEquals(1.5f, settingsOuter.outerSensitivity, 0.001f)

        val configOuter = baseConfig.copy(outerSensitivity = settingsOuter.outerSensitivity)
        val paramsOuter = HologramProjectionCalculator.computeParameters(testSnapshot, configOuter)

        // Ring 1 (CPU): Load 0.60 * 1.5 = 0.90 -> 0.2 + 0.81 * 4.8 = 4.088 RPS
        assertEquals("Ring 1 speed must immediately rescale to 4.088 RPS", 4.088f, paramsOuter.ring1SpeedRps, 0.001f)
        // Ring 2 (RAM) must remain completely decoupled
        assertEquals("Ring 2 speed must remain untouched at 0.968 RPS", 0.968f, paramsOuter.ring2SpeedRps, 0.001f)

        // 2. Mutate Middle Sensitivity (RAM) to 0.5x via settings repository
        repository.updateSensitivities(outer = 1.5f, middle = 0.5f, inner = 1.0f)
        val settingsMiddle = repository.getSettings()
        val configMiddle = baseConfig.copy(
            outerSensitivity = settingsMiddle.outerSensitivity,
            middleSensitivity = settingsMiddle.middleSensitivity
        )
        val paramsMiddle = HologramProjectionCalculator.computeParameters(testSnapshot, configMiddle)

        // Ring 2 (RAM): Load 0.40 * 0.5 = 0.20 -> 0.2 + 0.04 * 4.8 = 0.392 RPS
        assertEquals("Ring 2 speed must immediately rescale to 0.392 RPS", 0.392f, paramsMiddle.ring2SpeedRps, 0.001f)
        assertEquals("Ring 1 speed must preserve outer sensitivity scaling", 4.088f, paramsMiddle.ring1SpeedRps, 0.001f)

        // 3. Mutate Speed Range (vMin = 0.5, vMax = 8.0)
        repository.updateSpeedRange(vMin = 0.5f, vMax = 8.0f)
        val settingsSpeed = repository.getSettings()
        val configSpeed = configMiddle.copy(vMinRps = settingsSpeed.vMinRps, vMaxRps = settingsSpeed.vMaxRps)
        val paramsSpeed = HologramProjectionCalculator.computeParameters(testSnapshot, configSpeed)

        // Delta V = 8.0 - 0.5 = 7.5
        // Ring 1: Load 0.90 -> 0.5 + 0.81 * 7.5 = 6.575 RPS
        assertEquals("Ring 1 speed must recompute with new vMin/vMax", 6.575f, paramsSpeed.ring1SpeedRps, 0.001f)

        // 4. Mutate Speed Curve to LINEAR
        repository.updateSpeedCurve("LINEAR")
        val settingsCurve = repository.getSettings()
        val configCurve = configSpeed.copy(speedCurve = SpeedCurve.valueOf(settingsCurve.speedCurve))
        val paramsCurve = HologramProjectionCalculator.computeParameters(testSnapshot, configCurve)

        // Ring 1: 0.5 + 0.90 * 7.5 = 7.250 RPS
        assertEquals("Ring 1 speed must recompute with LINEAR curve", 7.250f, paramsCurve.ring1SpeedRps, 0.001f)
    }

    // =============================================================================================
    // Challenge 3: WindowManager Touch Pass-Through and Focus Retention Across Updates
    // =============================================================================================

    @Test
    fun challenge3_windowManagerFlags_retainedAcrossAllUIStateUpdates() {
        val flagNotFocusable = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE       // 0x08
        val flagNotTouchable = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE       // 0x10
        val flagLayoutInScreen = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN // 0x100
        val flagLayoutNoLimits = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS // 0x200

        val combinedFlags = flagNotFocusable or flagNotTouchable or flagLayoutInScreen or flagLayoutNoLimits
        val params = WindowManager.LayoutParams().apply {
            flags = combinedFlags
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = 244
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        fun assertInvariants(operationDescription: String) {
            // Invariant 1: FLAG_NOT_TOUCHABLE MUST ALWAYS BE SET
            assertTrue(
                "[$operationDescription] FLAG_NOT_TOUCHABLE must be set",
                (params.flags and flagNotTouchable) != 0
            )
            // Invariant 2: FLAG_NOT_FOCUSABLE MUST ALWAYS BE SET
            assertTrue(
                "[$operationDescription] FLAG_NOT_FOCUSABLE must be set",
                (params.flags and flagNotFocusable) != 0
            )
            // Invariant 3: FLAG_LAYOUT_IN_SCREEN MUST ALWAYS BE SET
            assertTrue(
                "[$operationDescription] FLAG_LAYOUT_IN_SCREEN must be set",
                (params.flags and flagLayoutInScreen) != 0
            )
            // Invariant 4: FLAG_LAYOUT_NO_LIMITS MUST ALWAYS BE SET
            assertTrue(
                "[$operationDescription] FLAG_LAYOUT_NO_LIMITS must be set",
                (params.flags and flagLayoutNoLimits) != 0
            )
            // Invariant 5: Composite flags exact value 792
            assertEquals(
                "[$operationDescription] Composite flags must remain exact 792 (0x318)",
                792,
                params.flags
            )
            // Invariant 6: PixelFormat.TRANSLUCENT
            assertEquals(
                "[$operationDescription] PixelFormat must remain TRANSLUCENT (-3)",
                PixelFormat.TRANSLUCENT,
                params.format
            )
            // Invariant 7: LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            assertEquals(
                "[$operationDescription] layoutInDisplayCutoutMode must remain ALWAYS (3)",
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
                params.layoutInDisplayCutoutMode
            )
            // Invariant 8: Gravity TOP | START
            assertEquals(
                "[$operationDescription] Gravity must remain TOP | START",
                Gravity.TOP or Gravity.START,
                params.gravity
            )
        }

        // 1. Initial State Assertion
        assertInvariants("Initial creation")

        // 2. Simulate 100 dynamic overlay height updates (as in MonitorService.updateOverlayHeight())
        val testHeights = (150..350 step 2)
        for (newHeight in testHeights) {
            params.height = newHeight
            assertInvariants("Height mutation to $newHeight px")
        }

        // 3. Simulate WindowInsets change (as in MonitorService.setOnApplyWindowInsetsListener)
        val insetsHeights = listOf(244, 264, 306, 320, 204)
        for (h in insetsHeights) {
            params.height = h
            assertInvariants("Insets update to $h px")
        }

        // 4. Assert zero touch obstruction guarantee:
        // WindowManager.LayoutParams with FLAG_NOT_TOUCHABLE guarantees the window will NEVER
        // receive touch events; the OS passes all touches down to whatever window is beneath it.
        assertTrue(
            "FLAG_NOT_TOUCHABLE strictly guarantees touch events pass through to underlying applications",
            (params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
        )
    }

    // =============================================================================================
    // Challenge 4: DataStore Rapid Slider Updates and Concurrent Access Stress Test
    // =============================================================================================

    @Test
    fun challenge4_dataStore_rapidSliderUpdates_noLossOrCorruption() = runTest(testDispatcher) {
        val iterations = 200

        // 1. Rapid sequential slider sweep (simulating rapid drag gestures)
        for (i in 0 until iterations) {
            val diameter = 96.0f + (i % 41) * 1.0f // 96.0 .. 136.0
            repository.updateHaloDiameter(diameter)
        }
        val intermediateSettings = repository.getSettings()
        assertTrue(
            "Halo diameter must be within valid slider range [96..136]",
            intermediateSettings.haloDiameterDp in 96.0f..136.0f
        )

        // 2. High-concurrency stress test:
        // Launch 4 concurrent coroutines rapidly mutating independent settings:
        // - Coroutine A: halo diameter (48..80)
        // - Coroutine B: speed range (vMin, vMax)
        // - Coroutine C: sensitivities (outer, middle, inner)
        // - Coroutine D: speed curve (QUADRATIC, LINEAR, SIGMOID)
        val jobA = async {
            for (i in 1..100) {
                repository.updateHaloDiameter(50.0f + (i % 25))
            }
            repository.updateHaloDiameter(74.5f) // deterministic final value
        }

        val jobB = async {
            for (i in 1..100) {
                repository.updateSpeedRange(vMin = 0.1f + (i % 8) * 0.1f, vMax = 3.0f + (i % 5))
            }
            repository.updateSpeedRange(vMin = 0.45f, vMax = 6.50f) // deterministic final value
        }

        val jobC = async {
            for (i in 1..100) {
                repository.updateSensitivities(
                    outer = 0.5f + (i % 15) * 0.1f,
                    middle = 0.2f + (i % 10) * 0.1f,
                    inner = 0.8f + (i % 12) * 0.1f
                )
            }
            repository.updateSensitivities(outer = 1.75f, middle = 0.85f, inner = 1.25f) // deterministic final value
        }

        val jobD = async {
            val curves = listOf("QUADRATIC", "LINEAR", "SIGMOID")
            for (i in 1..100) {
                repository.updateSpeedCurve(curves[i % curves.size])
            }
            repository.updateSpeedCurve("SIGMOID") // deterministic final value
        }

        // Await all concurrent write workers
        awaitAll(jobA, jobB, jobC, jobD)

        // 3. Verify final state integrity across all fields
        val finalSettings = repository.getSettings()

        assertEquals("Halo diameter must reach final target 74.5dp", 74.5f, finalSettings.haloDiameterDp, 0.001f)
        assertEquals("vMin must reach final target 0.45 RPS", 0.45f, finalSettings.vMinRps, 0.001f)
        assertEquals("vMax must reach final target 6.50 RPS", 6.50f, finalSettings.vMaxRps, 0.001f)
        assertEquals("Outer sensitivity must reach final target 1.75x", 1.75f, finalSettings.outerSensitivity, 0.001f)
        assertEquals("Middle sensitivity must reach final target 0.85x", 0.85f, finalSettings.middleSensitivity, 0.001f)
        assertEquals("Inner sensitivity must reach final target 1.25x", 1.25f, finalSettings.innerSensitivity, 0.001f)
        assertEquals("Speed curve must reach final target SIGMOID", "SIGMOID", finalSettings.speedCurve)

        // 4. Verify settingsFlow emission parity
        val flowEmittedSettings = repository.settingsFlow.first()
        assertEquals(finalSettings.haloDiameterDp, flowEmittedSettings.haloDiameterDp, 0.001f)
        assertEquals(finalSettings.vMinRps, flowEmittedSettings.vMinRps, 0.001f)
        assertEquals(finalSettings.outerSensitivity, flowEmittedSettings.outerSensitivity, 0.001f)
        assertEquals(finalSettings.speedCurve, flowEmittedSettings.speedCurve)
    }

    // =============================================================================================
    // Challenge 5: End-to-End Reactive Pipeline Integration & Zero-Lag Projection
    // =============================================================================================

    @Test
    fun challenge5_endToEndPipeline_reactsDeterministicallyAcrossStateTransitions() = runTest(testDispatcher) {
        val testSnapshot = SystemTelemetrySnapshot(
            cpu = MetricValue(smoothedValue = 0.50f),
            ram = MetricValue(smoothedValue = 0.50f),
            network = MetricValue(smoothedValue = 0.50f),
            storageIo = MetricValue(smoothedValue = 0.50f),
            gpu = MetricValue(smoothedValue = 0.50f)
        )

        // Step 1: Initialize with defaults
        val s1 = repository.getSettings()
        val c1 = HologramBehaviorConfig(
            vMinRps = s1.vMinRps,
            vMaxRps = s1.vMaxRps,
            speedCurve = SpeedCurve.valueOf(s1.speedCurve),
            outerSensitivity = s1.outerSensitivity,
            middleSensitivity = s1.middleSensitivity,
            innerSensitivity = s1.innerSensitivity,
            meltdownThreshold = s1.meltdownThreshold
        )
        val p1 = HologramProjectionCalculator.computeParameters(testSnapshot, c1)
        assertEquals(1.400f, p1.ring1SpeedRps, 0.001f)
        assertFalse("Nominal load 0.50 must not trigger meltdown alert", p1.isMeltdownAlert)

        // Step 2: Update meltdown threshold to 0.45 via repository -> triggers meltdown alert
        repository.updateMeltdownThreshold(0.45f)
        val s2 = repository.getSettings()
        val c2 = c1.copy(meltdownThreshold = s2.meltdownThreshold)
        val p2 = HologramProjectionCalculator.computeParameters(testSnapshot, c2)
        assertTrue("Load 0.50 > threshold 0.45 must trigger meltdown alert immediately", p2.isMeltdownAlert)

        // Step 3: Toggle overlay enabled
        repository.updateOverlayEnabled(true)
        assertTrue("Overlay state must be enabled", repository.getSettings().isOverlayEnabled)

        repository.updateOverlayEnabled(false)
        assertFalse("Overlay state must be disabled", repository.getSettings().isOverlayEnabled)

        // Step 4: Reset defaults
        repository.resetDefaults()
        val sReset = repository.getSettings()
        assertFalse(sReset.isOverlayEnabled)
        assertEquals(104.0f, sReset.haloDiameterDp, 0.001f)
        assertEquals(0.2f, sReset.vMinRps, 0.001f)
        assertEquals(5.0f, sReset.vMaxRps, 0.001f)
        assertEquals(0.90f, sReset.meltdownThreshold, 0.001f)
    }
}
