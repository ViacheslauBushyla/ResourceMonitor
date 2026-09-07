# ResourceMonitor — Application Description

> **Audience:** This document is a self-contained technical and product description intended for an AI agent onboarding to this codebase. It covers the business idea, user-visible functionality, hardware metric pipeline, and module architecture. It does **not** describe test suites.

---

## Business Idea

Modern Android flagship phones (Pixel 8, Pixel 11 Pro, etc.) have a front-camera punch-hole in the display that is permanently dead UI space — invisible in every app, every game, every launcher. ResourceMonitor turns that dead zone into a persistent, ambient system-health indicator: a 5-ring holographic HUD that wraps concentrically around the camera aperture and glows, rotates, and pulses in real time with actual hardware telemetry.

The product vision is a **non-intrusive ambient display layer** — always visible above all other apps as a transparent system overlay, but never blocking touch, never requiring the device owner to switch apps, and never adding meaningful battery or CPU cost. The HUD is the device's vital signs made visible at a glance, embodied in the one screen element users already unconsciously notice: the camera hole.

---

## User-Visible Functionality

### Holographic HUD Overlay
- A `TYPE_APPLICATION_OVERLAY` WindowManager service (`MonitorService`) renders a transparent Compose surface directly above every other app on the display.
- Five concentric neon rings orbit the front camera punch-hole. Each ring's rotation speed, tilt angle, opacity, stroke width, and color saturation are driven by a distinct live hardware metric.
- Touch events pass through (`FLAG_NOT_TOUCHABLE`) — the overlay never interrupts interaction with the underlying app.
- The HUD is precisely centered on the physical camera aperture by reading `WindowInsets.displayCutout.boundingRectTop` at runtime via `CutoutGeometryResolver`. This works without hard-coded offsets across different device models and screen sizes.
- Default halo diameter: **104 dp** — the inner Ring 5 clears the Pixel 8 (18 dp aperture) and Pixel 11 Pro (20 dp aperture) without obscuring the lens.

### Companion App
- `StartScreen` (Jetpack Compose) shows live numeric metric badges for all 5 channels, updated in real time.
- Sliders and toggles let the user control: overlay on/off, halo diameter, per-channel sensitivity, animation speed curves.
- All settings persist across reboots via **Jetpack DataStore** (Proto preferences), managed by `DataStoreHudSettingsRepository`.

### Verified Devices
| Device | Screen | Camera aperture |
|---|---|---|
| Google Pixel 8 (`shiba`) | 1080 × 2400, Android 15 | 18 dp |
| Google Pixel 11 Pro (`grizzly`) | 1280 × 2856, Android 15 | 20 dp |

---

## 5 Metric Channels

Each ring maps 1-to-1 to a hardware channel. All data collection is **non-root** — sources are public Linux sysfs, procfs, Android SDK APIs, and file system stats.

### Ring 1 — CPU (Outer, Cyan `#00FFFF`)
- **What it measures:** CPU governor frequency scaling and per-core load.
- **Data source:** `/sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq` and `cpuinfo_max_freq` (world-readable sysfs). All available cores are polled; the aggregate normalized load drives ring speed.
- **Collector:** `CpuFreqCollector`

### Ring 2 — RAM (Orange `#FF8C00`)
- **What it measures:** Physical memory pressure and zRAM swap compression ratio.
- **Data source:** `/proc/meminfo` fields `MemTotal`, `MemAvailable`, `SwapTotal`, `SwapFree`. Pressure = `1 - MemAvailable/MemTotal`; swap compression stress is derived from swap utilization.
- **Collector:** `RamMeminfoCollector`

### Ring 3 — Network (Magenta `#FF00FF`)
- **What it measures:** Real-time aggregate network throughput (Wi-Fi + cellular combined); qualitative signal strength is a secondary factor.
- **Data source:** `android.net.TrafficStats.getTotalRxBytes()` and `getTotalTxBytes()` — delta between consecutive polls gives bytes/s. Wi-Fi RSSI and cellular RSRP are read from `ConnectivityManager` / `TelephonyManager`.
- **Collector:** `NetworkTrafficCollector`

### Ring 4 — Storage I/O (Ice Blue `#80D8FF`)
- **What it measures:** SSD I/O wait pressure and disk write flush latency.
- **Data source:** `android.os.StatFs` on the data partition — available block deltas between polls approximate I/O pressure. Sustained write cycles elevate ring speed.
- **Collector:** `StorageIoCollector`

### Ring 5 — GPU / Thermal (Inner, Emerald `#00E676` nominal → Crimson `#FF1744` critical)
- **What it measures:** Thermal governor state and battery-junction temperature, used as a proxy for GPU and SoC thermal load.
- **Data source:** `android.os.PowerManager.getThermalHeadroom()`, `android.os.BatteryManager.EXTRA_TEMPERATURE` (battery temp in tenths of °C). Ring color transitions from emerald (cool) through amber to crimson (thermal throttling).
- **Collector:** `ThermalSystemCollector`

---

## Telemetry Pipeline

```
Hardware / Kernel / SDK
        │
        ▼
[RealTelemetrySource]         ← :core:telemetry-system
  5 × Collector impls
        │  RawTelemetryPacket (per poll)
        ▼
[TelemetryFusionEngine]       ← :core:telemetry-fusion
  EMA smoothing per channel
  Normalization (0.0–1.0)
  Throttling classification
  2 000 ms idle back-off
        │  SystemTelemetrySnapshot
        ▼
[HologramProjectionCalculator] ← :animations:holographic-rings
  Maps 5 floats → ring params
  (speed, tilt, opacity, width, color saturation)
        │  HolographicRingsParams
        ▼
[CameraHaloRingsRenderer]     ← :animations:holographic-rings
  Zero-allocation Canvas loop
  Precomputed trig LUT (32-point)
  path.rewind() reuse
  2 FPS ambient pacing
        │  Canvas draw calls
        ▼
[MonitorOverlay / WindowManager]  ← :app
  TYPE_APPLICATION_OVERLAY
  FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE
  LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
```

**EMA smoothing:** Each channel uses an exponential moving average with a configurable alpha (default 0.3) so sudden spikes don't cause jarring animation jumps — the rings respond smoothly.

**Idle back-off:** If all 5 channels have been below their alert thresholds for 2 000 ms, the telemetry poll rate drops automatically to reduce CPU wake-up frequency.

**Performance budget:** < 1% sustained CPU overhead on Tensor G3 (Pixel 8, measured 0.50–0.59%). Achieved via: zero heap allocation in the draw loop, 32-point precomputed cosine/sine tables, `path.rewind()` object reuse, and 2 FPS ambient frame pacing.

---

## Module Architecture

The project is a **9-module multi-Gradle Android project**. Modules are layered: pure data → API contracts → implementations → rendering → app shell.

```
:app
  ├── :animations:holographic-rings
  ├── :core:config
  ├── :core:telemetry-system
  ├── :core:telemetry-fusion
  ├── :core:telemetry-mock
  ├── :core:telemetry-api
  ├── :core:designsystem
  ├── :core:animation-contract
  └── :core:model
```

### `:core:model`
Pure Kotlin data classes. No Android deps.
- `RawTelemetryPacket` — 5-field snapshot from a single collector poll cycle (cpu, ram, network, storage, thermal as raw longs/floats).
- `SystemTelemetrySnapshot` — 5-field normalized (0.0–1.0) snapshot output from the fusion engine.

### `:core:telemetry-api`
Collector interfaces (`MetricCollector<T>`) — defines the contract that any real or mock collector must satisfy.

### `:core:telemetry-system`
Real hardware collector implementations:
- `CpuFreqCollector`, `RamMeminfoCollector`, `NetworkTrafficCollector`, `StorageIoCollector`, `ThermalSystemCollector`
- `RealTelemetrySource` — aggregates all 5 collectors into a single `Flow<RawTelemetryPacket>`.

### `:core:telemetry-mock`
Simulation scenarios used in the companion app's inspector screen (sine waves, stress spikes, idle baseline). Enables deterministic UI preview without real hardware.

### `:core:telemetry-fusion`
- `TelemetryFusionEngine` — subscribes to `RealTelemetrySource`, applies EMA per channel, emits `SystemTelemetrySnapshot` at the configured rate with idle back-off.
- `TelemetryNormalizer` — per-channel min/max clamping to [0.0, 1.0].
- `ThrottlingClassifier` — identifies whether CPU/thermal is in a throttled state and adjusts ring 5 color accordingly.

### `:core:config`
Jetpack DataStore (Proto) persistence layer.
- `HudSettings` — data class: overlay enabled, halo diameter (dp), per-channel sensitivity (0.0–1.0), animation speed curve enum, ambient brightness.
- `HudSettingsRepository` / `DataStoreHudSettingsRepository` — reactive read/write API. Settings survive process death and reboots.
- `HudPreferencesKeys` — typed DataStore preference keys.

### `:core:designsystem`
- `NeonPalette` — 5-color neon palette (cyan, orange, magenta, ice blue, emerald/crimson) with alpha variants for glow layers.
- `CutoutGeometryResolver` — reads `WindowInsets.displayCutout` to produce a `CutoutGeometry` (center X/Y, radius) that the overlay service uses to position Ring 5 concentric with the camera aperture.
- `CutoutGeometry` — value class: `centerX`, `centerY`, `radiusPx`.

### `:core:animation-contract`
Service Provider Interface (SPI) for animation themes. Defines the plugin interface any animation module must implement to register its: required metric inputs, parameter schema, and Compose preview renderer. Enables future animation modules to be added without modifying `:app`.

### `:animations:holographic-rings`
The primary animation module.
- `CameraHaloRingsRenderer` — Android Canvas renderer. Draws 5 concentric rings with independent Euler tilt angles simulating 3D gyroscopic rotation. Uses a 32-point precomputed trig LUT and zero heap allocation per frame.
- `HolographicRingsParams` — 5-element parameter vector (one `RingParams` per ring: speed, tiltX, tiltY, strokeWidth, alpha, color).
- `HologramProjectionCalculator` — maps a `SystemTelemetrySnapshot` to `HolographicRingsParams`.
- `HologramBehaviorConfig` — configuration of per-ring speed/sensitivity/color curves.
- `HologramInvariantCase` — 5-channel enum of boundary/stress/nominal/idle cases for the inspector.

### `:app`
- `MonitorService` — `ForegroundService` that creates a `TYPE_APPLICATION_OVERLAY` `WindowManager` window with flags `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS` and `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`. Hosts `MonitorOverlay` inside a Compose window.
- `MonitorOverlay` — Compose host that positions `CameraHaloRingsRenderer` centered on `CutoutGeometry`.
- `StartScreen` — Companion UI: live metric badges, invariant inspector, settings sliders, toggle overlay button.
- `DataModule` (Koin) — DI graph wiring `RealTelemetrySource` → `TelemetryFusionEngine` → `HologramProjectionCalculator`.
- `MainActivity` — single-activity host; initializes DataStore on first launch.

---

## Key Design Decisions

| Decision | Rationale |
|---|---|
| `TYPE_APPLICATION_OVERLAY` WindowManager | Only overlay type that draws above all apps including system UI, without requiring root |
| `FLAG_NOT_TOUCHABLE` | Mandatory — HUD must never intercept touch events |
| `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS` | Required to render into the cutout area on notch/hole-punch devices |
| `WindowInsets.displayCutout` for positioning | Runtime centering works across any device; no hard-coded per-device offsets |
| Zero-allocation Canvas draw loop | Background service must not trigger GC pauses; `path.rewind()` + precomputed LUT achieves this |
| EMA smoothing (α = 0.3) | Prevents jarring animation jumps from transient metric spikes |
| 2 FPS ambient pacing | Sufficient for ambient ambient telemetry; eliminates unnecessary Choreographer wake-ups |
| Non-root sysfs/procfs reads | `/sys/devices/system/cpu/*/cpufreq/` and `/proc/meminfo` are world-readable on AOSP; no shell or root required |
| Jetpack DataStore (Proto) | Settings survive process death; type-safe; async-only API prevents main-thread I/O |
| Multi-module Gradle | Clean separation of concerns; `:core:telemetry-system` can be replaced with a mock for screenshots; `:animations:holographic-rings` can be swapped for a future animation theme |

---

## Repository

- **GitHub:** `https://github.com/ViacheslauBushyla/ResourceMonitor/` (fork)
- **Main branch after merge:** All changes from `feat/camera-hole-5-ring-telemetry-hud`
- **PR:** `https://github.com/vladimirnazarov/ResourceMonitor/pull/2`
- **Working directory:** `/Users/viacheslau_bushyla/development/android/ResourceMonitor`
- **Build:** Android Gradle Plugin, Kotlin, Compose BOM, Koin, DataStore Proto, minSdk 26, targetSdk 37 (Android 15)
