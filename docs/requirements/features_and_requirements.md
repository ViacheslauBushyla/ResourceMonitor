# Antigravity HUD: Features & Requirements Specification
**Document Version:** 1.0  
**Status:** Approved  
**Target:** Android 12+ (API 31–37)

---

## 1. Product Vision & Goals

Antigravity HUD is an ambient mobile telemetry monitor designed to surface real-time hardware bottlenecks, throttling events, and network degradation. Rather than cluttering the screen with dense graphs and raw numbers, it presents system telemetry through:
1. **Ambient Edge Lines & Status Bar Micro-Indicators** that blend seamlessly around the screen bezels and native status icons.
2. **Pluggable Futuristic Visual Themes** (e.g. 3D Holographic Concentric Rings HUD) where system metrics physically drive kinetic animations, particle fields, and glowing neon shaders.
3. **Monetizable & Extensible Architecture** allowing new visual animations to be created, tested, and distributed as independent modules.

---

## 2. Functional Requirements (FR)

### 2.1 Hardware Telemetry Ingestion (Tier 1)
- **FR-1.1 (CPU Governor & Load):** Sample overall CPU load (%) and individual cluster frequencies (`/sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq`).
- **FR-1.2 (Thermal Throttling Detection):** Compare active core load against scaled frequency. Flag `CriticalMeltdown` when load is $>90\%$ while frequency is pinned to minimum base clock.
- **FR-1.3 (Memory Pressure & zRAM):** Track physical RAM usage via `ActivityManager.MemoryInfo` and zRAM swap/compaction activity via `/proc/vmstat` (`zram_stored_pages`, `compact_stall`).
- **FR-1.4 (Cellular RF Link):** Listen for baseband updates via `TelephonyCallback` (`SignalStrength`: RSRP, RSRQ, SINR). Detect edge-of-cell degradation ($RSRP < -115\text{ dBm}$) and thermal fallback (5G to LTE).
- **FR-1.5 (Wi-Fi Link Integrity):** Interrogate `WifiManager` for link speed, channel contention, and packet retry spikes.
- **FR-1.6 (Storage I/O Stalls):** Monitor kernel `/proc/stat` `iowait` cycles to detect persistent flash write bottlenecks.
- **FR-1.7 (Simulation Engine):** Provide a `FakeTelemetrySource` that generates deterministic synthetic metrics and scripted scenarios ("Meltdown", "5G Drop", "RAM Thrash") for headless testing and user sandbox preview.

### 2.2 Telemetry Fusion & Smoothing (Tier 2)
- **FR-2.1 (Normalization):** Transform all disparate hardware metrics into normalized unit floats $[0.0, 1.0]$.
- **FR-2.2 (Exponential Moving Average Filter):** Apply low-pass smoothing ($S_t = \alpha Y_t + (1-\alpha)S_{t-1}$) to suppress transient jitter while preserving responsiveness to major load spikes.
- **FR-2.3 (State Classification):** Classify system health into `Nominal`, `Peak`, and `CriticalThrottled`.
- **FR-2.4 (Adaptive Polling Daemon):** Dynamically transition between:
  - **High-Precision Active Mode (250ms):** When load is high ($>30\%$), gaming is active, high network throughput occurs, or throttling is detected.
  - **Low-Power Decay Mode (2000ms):** When device is idle ($<30\%$), screen is locked, or system remains calm for $>5$ seconds.

### 2.3 Animation Parameter Translation (Tier 3)
- **FR-3.1 (Parameter Contract):** Translate fused `SystemTelemetrySnapshot` into strongly-typed, immutable `AnimationParameterBundle` contracts tailored for specific animations.
- **FR-3.2 (Quadratic Speed Calculation):** Implement the rotational physics formula for kinetic animations:
  $$V_{rot} = V_{min} + \left(\frac{L}{100}\right)^2 \cdot (V_{max} - V_{min})$$
  with $V_{min} = 0.2\text{ RPS}$ and $V_{max} = 5.0\text{ RPS}$.
- **FR-3.3 (Alert Overrides):** Dynamically scale bloom intensity, motion blur, and color gamut shifts during throttling/meltdown events.

### 2.4 Pluggable Animation Modules (Tier 4)
- **FR-4.1 (Independent Modules):** Every visual animation theme must reside in its own Gradle module implementing `HudAnimationPlugin`.
- **FR-4.2 (Zero Hardware Coupling):** Animation renderers must have zero direct dependencies on Android system APIs, permissions, or raw telemetry files.
- **FR-4.3 (Holographic Rings HUD):** Deliver the first animation module featuring 3 concentric neon rings floating in 3D perspective:
  - Outer ring: Cyan `#00FFFF` (CPU load).
  - Middle ring: Orange `#FF8C00` (RAM status).
  - Inner ring: Magenta `#FF00FF` (GPU / Network).
  - High-load bloom & red pulsing spheroid meltdown alert at $\ge 90\%$ load or thermal throttling.

### 2.5 Multi-Tier Settings & Companion Dashboard
- **FR-5.1 (Global Settings):** Master overlay switch, start on boot, active animation theme selector, app exclusion whitelist, calm state auto-hide.
- **FR-5.2 (Metric Settings):** Per-channel toggles, smoothing factor adjustment, thermal threshold calibration.
- **FR-5.3 (Animation Settings):** Dynamic parameter schema allowing users to adjust ring scale, glow intensity, and color palettes per animation.
- **FR-5.4 (Sandbox Live Simulator):** Companion dashboard featuring interactive chips to test throttling scenarios for 5 seconds without stressing physical hardware.

---

## 3. Non-Functional Requirements (NFR)

- **NFR-1 (Battery & Energy Overhead):** The background service and overlay must consume $<2\%$ average CPU and $<60\text{ mW}$ additional power on sustained idle.
- **NFR-2 (Frame Rate):** Animation renderers must achieve consistent 60 FPS / 120 FPS on supported refresh rates without dropped frames.
- **NFR-3 (Overlay Safety):** Overlay window must use `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_FOCUSABLE` and `FLAG_NOT_TOUCHABLE` to never intercept user touch events or interfere with system interactions.
- **NFR-4 (Independent Testability):**
  - Metric collection logic must have 100% headless unit test coverage using mock file/system doubles.
  - Animation renderers must support isolated Compose `@Preview` and headless screenshot/snapshot regression testing.

---

## 4. Target Hardware & QA Testing Environment

- **Primary / Preferred Test Target:** **Google Pixel 8 (`shiba`)**
  - All interactive on-device testing, battery overhead measurement, live overlay verification, and visual fidelity checks must prioritize Google Pixel 8.
  - Target when executing ADB commands: `adb -s <pixel_8_serial_or_mdns_id> ...`.
- **Secondary Reference Device:** Google Pixel 11 Pro (`grizzly` / `caiman`)
  - Used for forward-looking API validation (Android 16/17, API 36/37) and high-tier thermal step-down calibration.

