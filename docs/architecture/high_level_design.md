# High-Level Architecture & Technical Design: Antigravity HUD
**Document Version:** 1.0  
**Project:** Antigravity Ambient Telemetry HUD & Resource Monitor  
**Target Platform:** Android 12+ (API 31–37)

---

## 1. System Architecture Overview

Antigravity HUD decomposes ambient device monitoring into a clean 4-tier pipeline where device telemetry acquisition, data fusion & smoothing, animation parameter translation, and pure graphics rendering are strictly isolated.

```
+-----------------------------------------------------------------------------------+
| Tier 1: Hardware Telemetry Ingestion Layer (:core:telemetry-system / -mock)       |
|   - Real Collectors: CPU (/proc/stat, /sys), RAM (ActivityManager), TrafficStats,  |
|     TelephonyCallback, WifiManager, HardwarePropertiesManager                     |
|   - Virtual Collectors: FakeTelemetrySource (scripted stress/idle scenarios)      |
+-----------------------------------------+-----------------------------------------+
                                          | RawTelemetryPacket (raw timestamps, values)
                                          v
+-----------------------------------------------------------------------------------+
| Tier 2: Telemetry Fusion, Normalization & Throttling Engine (:core:telemetry-fusion) |
|   - Calibration: Raw units (MHz, KB/s, dBm) -> NormalizedFloat [0.0..1.0]        |
|   - Smoothing: Exponential Moving Average (EMA) / low-pass filtering              |
|   - Throttling Evaluation: Governor step-down ratio, zRAM thrash detection        |
|   - Adaptive Polling Daemon: 250ms (peak/gaming/bursts) <-> 2000ms (idle)         |
+-----------------------------------------+-----------------------------------------+
                                          | SystemTelemetrySnapshot (StateFlow)
                                          v
+-----------------------------------------------------------------------------------+
| Tier 3: Animation Parameter Translation Layer (:core:animation-contract)          |
|   - Converts SystemTelemetrySnapshot into Animation-specific parameter contracts   |
|   - Applies mathematical response curves (Quadratic, Sigmoid, Linear)             |
|   - Handles visual alert state transitions (meltdown pulse, bloom triggers)       |
+-----------------------------------------+-----------------------------------------+
                                          | AnimationParams Contract (pure data classes)
                                          v
+-----------------------------------------------------------------------------------+
| Tier 4: Graphics & Rendering Execution Layer (:animations:*)                      |
|   - Pure rendering engine receiving AnimationParams                               |
|   - Zero knowledge of Android hardware APIs or permissions                         |
|   - Renders via Compose Canvas / AGSL RuntimeShader / RenderEffect                |
+-----------------------------------------+-----------------------------------------+
```

---

## 2. Multi-Module Project Blueprint

```
ResourceMonitor/
├── app/                                  # Application assembly, Koin initialization
├── core/
│   ├── common/                           # Dispatchers, logging, EMA filter, common math
│   ├── model/                            # Pure Kotlin models (MetricValue, ThrottleState)
│   ├── telemetry-api/                    # Collector interfaces (ICpuCollector, IRamCollector, etc.)
│   ├── telemetry-system/                 # Android collectors (WindowManager, /sys, TrafficStats)
│   ├── telemetry-mock/                   # Simulation engine & scripted stress scenarios
│   ├── telemetry-fusion/                 # Aggregation, smoothing, throttle detection
│   ├── animation-contract/               # SPI: HudAnimationPlugin, AnimationManifest
│   ├── designsystem/                     # Neon palette, AGSL shaders, 3D math projections
│   └── settings/                         # DataStore preferences & multi-level configuration
├── feature/
│   ├── hud-overlay/                      # Foreground Service & WindowManager controller
│   └── companion/                        # Companion app: permissions, gallery, sandbox
└── animations/                           # INDEPENDENT ANIMATION MODULES
    ├── holographic-rings/                # 3D Holographic Concentric Rings HUD
    └── bezel-hud/                        # Minimalist bezel edge lines & micro indicators
```

---

## 3. Pluggable Animation SPI (Service Provider Interface)

Every visual theme resides in an independent module and implements `HudAnimationPlugin`:

```kotlin
interface HudAnimationPlugin {
    val manifest: AnimationManifest
    
    /** Maps unified telemetry snapshot to animation-specific parameters */
    val parameterMapper: AnimationParameterMapper
    
    /** Renders the animation inside Compose layout (Overlay or Companion Preview) */
    @Composable
    fun Render(
        parameters: Any,
        modifier: Modifier
    )
    
    /** Optional custom configuration UI rendered in the Companion Settings App */
    @Composable
    fun ConfigUi(
        config: Map<String, Any>,
        onConfigChanged: (key: String, value: Any) -> Unit,
        modifier: Modifier
    )
}

data class AnimationManifest(
    val id: String,                         // e.g. "holographic_concentric_rings"
    val displayName: String,                // "3D Holographic Rings"
    val description: String,
    val author: String,
    val version: Int,
    val requiredSensors: Set<SensorType>,   // CPU, RAM, GPU, NETWORK, IO
    val previewThumbnailResId: Int,
    val configParameters: List<ConfigPropertyDefinition<*>>
)
```

---

## 4. Multi-Tier Settings Hierarchy

1. **Level 1: Global Settings**:
   - Master overlay toggle, start on boot, active animation theme, adaptive polling toggle, auto-hide when calm ($<30\%$), app exclusion whitelist.
2. **Level 2: Metric-Level Settings**:
   - Per-channel enable/disable, Big-Core vs cluster average, EMA smoothing factor $\alpha$, thermal alert temperature ($45^\circ\text{C}$ default).
3. **Level 3: Animation-Specific Settings**:
   - Dynamically generated from `AnimationManifest.configParameters`: ring scale, bloom intensity multiplier, color gamut selection.

---

## 5. Rendering Engine Evaluation & AGSL Neon Shaders

- **Compose Canvas + AGSL (`RuntimeShader`) with 3D projection math:**
  - Concentric rings calculated via 3D perspective projection onto 2D Canvas.
  - Procedural neon radiance and bloom applied via AGSL fragment shaders on API 33+ (`RenderEffect.createRuntimeShaderEffect()`), with Compose blur mask fallback on API 31–32.
  - Maintains 60–120 FPS at negligible GPU power consumption ($<60\text{ mW}$).

---

## 6. Comprehensive Testing Strategy

- **Isolated Metric Testing (`:core:telemetry-fusion`)**: Headless unit testing of low-pass filters, throttle rule engine, and adaptive polling decay using `FakeTelemetrySource`.
- **Decoupled Animation Testing (`:animations:*`)**: Compose Previews for distinct states (`Idle`, `Nominal`, `Meltdown`), parameter math verification, and headless screenshot regression testing with Roborazzi/Paparazzi.
- **End-to-End Testing (`:feature:hud-overlay`)**: Testing overlay composable transitions when simulated metrics update via the Companion Sandbox.
- **Physical Device Target Guidelines**:
  - **Preferred Device:** **Google Pixel 8 (`shiba`)** must always be prioritized for live QA, hardware telemetry profiling, and animation performance runs.
  - **Secondary Reference:** Google Pixel 11 Pro (`caiman` / `grizzly`) for extended multi-device validation.

