# Project: Antigravity HUD 3D Holographic Rings & Invariant Verification

## Architecture
Antigravity HUD is an ambient telemetry monitor for Android built with Jetpack Compose, Koin, and modular Gradle components.
- `:core:model`: Pure Kotlin data models (`SystemTelemetrySnapshot`, `MetricValue`, `ThrottleState`, `RawTelemetryPacket`).
- `:core:designsystem`: Design tokens (`NeonPalette`) and 3D projection math (`PerspectiveProjection3D`).
- `:core:telemetry-fusion`: Telemetry fusion engine (`TelemetryFusionEngine`), EMA filters, and throttle classification.
- `:core:animation-contract`: SPI plugin interfaces (`HudAnimationPlugin`, `AnimationParameterMapper`).
- `:animations:holographic-rings`: 3D Holographic Concentric Rings HUD module with parameterizable speed models, bloom shaders, and Canvas rendering.
- `:app`: Aggregator Android application, foreground `MonitorService`, `MonitorOverlay`, and Material 3 companion dashboard (`StartScreen`).

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Speed Model Parameterization | Configurable `vMinRps`, `vMaxRps`, `QUADRATIC`, `LINEAR`, `SIGMOID` speed curves | M1 | Survey (Explorer 1) / ORIGINAL_REQUEST §R1 |
| 2 | Sensitivity Multipliers | Per-circle sensitivity multipliers (`outerSensitivity`, `middleSensitivity`, `innerSensitivity` [0.1..2.0]) | M1 | Survey (Explorer 1) / ORIGINAL_REQUEST §R1 |
| 3 | Thresholds & Alert Palettes | Configurable `boostThreshold`, `meltdownThreshold`, Nominal vs Alert colors, `MemoryThrashPurple` token | M1 | Survey (Explorer 1) / ORIGINAL_REQUEST §R1 |
| 4 | Decoupled Meltdown & Precedence | Separate CPU meltdown from RAM thrashing and cellular degradation; fix status label precedence | M1 | Survey (Explorer 1) |
| 5 | SPI ParameterMapper Wiring | Wire `HolographicRingsPlugin.parameterMapper` to `HologramProjectionCalculator` | M1 | Survey (Explorer 1) |
| 6 | Shared Invariant Data Architecture | Public data model `HologramInvariantCase` and 30 master definitions in `:animations:holographic-rings` | M2 | Survey (Explorer 2) / ORIGINAL_REQUEST §R2 |
| 7 | 30-Case Invariant Test Matrix | JUnit 4 test suite (`HologramInvariantMatrixTest.kt`) covering all 30 combinatorial cases (+/- 0.01 RPS) | M2 | Survey (Explorer 2) / ORIGINAL_REQUEST §R2 |
| 8 | Parameter Override Tests | Dynamic configuration mutation tests (speeds, curves, sensitivities, thresholds) | M2 | Survey (Explorer 2) / ORIGINAL_REQUEST §R2 |
| 9 | Telemetry Sync Injection | Add `injectPacket()` to `TelemetryFusionEngine` for zero-latency screen and overlay sync | M3 | Survey (Explorer 3) / ORIGINAL_REQUEST §R3 |
| 10 | Interactive 30-Case UI Selector | 7-category filter chips, steppers `[◀ Prev]` `[Next ▶]`, dropdown, and telemetry details in `StartScreen.kt` | M3 | Survey (Explorer 3) / ORIGINAL_REQUEST §R3 |
| 11 | Live Parameter UI Controls | $V_{min}$, $V_{max}$, speed curve toggle, sensitivity sliders, and reset defaults in `StartScreen.kt` | M3 | Survey (Explorer 3) / ORIGINAL_REQUEST §R3 |
| 12 | Live Computed Speed Badges | Real-time readout of outer, middle, and inner circle RPS and alert gamuts in `StartScreen.kt` | M3 | Survey (Explorer 3) / ORIGINAL_REQUEST §R3 |
| 13 | Pixel 8 Debug Deployment | Automated install and launch of debug build on physical Google Pixel 8 via ADB TLS | M4 | Survey (Explorer 3) / ORIGINAL_REQUEST §R4 |
| 14 | Pixel 8 Stability & ANR Verification | Verify zero crashes in `logcat -b crash` and zero ANRs in `dumpsys activity exit-info` | M4 | Survey (Explorer 3) / ORIGINAL_REQUEST §R4 |
| 15 | Physical Screen Capture | Capture high-resolution screenshot from Google Pixel 8 verifying visual rendering of HUD inspector | M4 | Survey (Explorer 3) / ORIGINAL_REQUEST §R4 |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Circle Parameterization & Deterministic Projection Engine | `:animations:holographic-rings`, `:core:designsystem` (`HologramBehaviorConfig`, `HologramProjectionCalculator`, `NeonPalette`, `HolographicRingsPlugin`) | none | DONE |
| M2 | Automated 30-Case Invariant Test Matrix | `:animations:holographic-rings` (`HologramInvariantCase.kt`, `HologramInvariantMatrixTest.kt`) | M1 | DONE |
| M3 | Interactive Invariant Inspector & Live Parameter Controls | `:core:telemetry-fusion`, `:app` (`TelemetryFusionEngine.kt`, `StartScreen.kt`) | M1, M2 | DONE |
| M4 | Automated Physical Device Verification on Google Pixel 8 | Deploy to `adb-38151FDJH002CF-aO9qmw._adb-tls-connect._tcp`, verify stability, capture screenshot | M1, M2, M3 | DONE |

## Interface Contracts

### `:animations:holographic-rings` ↔ `:app` & Consumers
- **`HologramBehaviorConfig`**:
  ```kotlin
  data class HologramBehaviorConfig(
      val vMinRps: Float = 0.2f,
      val vMaxRps: Float = 5.0f,
      val speedCurve: SpeedCurve = SpeedCurve.QUADRATIC,
      val outerSensitivity: Float = 1.0f,
      val middleSensitivity: Float = 1.0f,
      val innerSensitivity: Float = 1.0f,
      val boostThreshold: Float = 0.70f,
      val meltdownThreshold: Float = 0.90f,
      val outerBaseColor: Color = NeonPalette.CyanCpu,
      val middleBaseColor: Color = NeonPalette.OrangeRam,
      val innerBaseColor: Color = NeonPalette.MagentaGpuNet,
      val alertAmberColor: Color = NeonPalette.WarningAmber,
      val alertMeltdownColor: Color = NeonPalette.MeltdownRed,
      val alertThrashPurpleColor: Color = NeonPalette.MemoryThrashPurple
  )
  ```
- **`HologramProjectionCalculator.computeParameters`**:
  ```kotlin
  fun computeParameters(
      snapshot: SystemTelemetrySnapshot,
      config: HologramBehaviorConfig = HologramBehaviorConfig(),
      bloomMultiplier: Float = 1.0f
  ): HolographicRingsParams
  ```
- **`HologramInvariantCase`**:
  ```kotlin
  data class HologramInvariantCase(
      val id: Int,
      val name: String,
      val group: String,
      val description: String,
      val snapshot: SystemTelemetrySnapshot,
      val expectedOuterSpeedRps: Float,
      val expectedMiddleSpeedRps: Float,
      val expectedInnerSpeedRps: Float,
      val expectedOuterColor: Color,
      val expectedMiddleColor: Color,
      val expectedInnerColor: Color,
      val expectedIsMeltdown: Boolean,
      val expectedStatusSubstring: String,
      val expectedEnergyLabel: String
  ) {
      fun toRawPacket(): RawTelemetryPacket
  }
  ```

### `:core:telemetry-fusion` ↔ `:app`
- **`TelemetryFusionEngine.injectPacket`**:
  ```kotlin
  fun injectPacket(packet: RawTelemetryPacket, resetEma: Boolean = true): SystemTelemetrySnapshot
  ```

## Code Layout
- `core/designsystem/src/main/java/com/vnazarov/resourcemonitor/core/designsystem/theme/NeonPalette.kt`
- `animations/holographic-rings/src/main/java/com/vnazarov/resourcemonitor/animations/hologram/HologramBehaviorConfig.kt`
- `animations/holographic-rings/src/main/java/com/vnazarov/resourcemonitor/animations/hologram/HologramProjectionCalculator.kt`
- `animations/holographic-rings/src/main/java/com/vnazarov/resourcemonitor/animations/hologram/HolographicRingsPlugin.kt`
- `animations/holographic-rings/src/main/java/com/vnazarov/resourcemonitor/animations/hologram/HologramInvariantCase.kt`
- `animations/holographic-rings/src/test/java/com/vnazarov/resourcemonitor/animations/hologram/HologramInvariantMatrixTest.kt`
- `core/telemetry-fusion/src/main/java/com/vnazarov/resourcemonitor/core/telemetry/fusion/TelemetryFusionEngine.kt`
- `app/src/main/java/com/vnazarov/resourcemonitor/presentation/ui/screen/StartScreen.kt`
