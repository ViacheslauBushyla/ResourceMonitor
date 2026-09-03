# AGENTS.md: Antigravity HUD Engineering & Operational Guide

## 1. Project Overview & Multi-Module Architecture

**Antigravity HUD** is an ambient mobile telemetry monitor for Android built with Jetpack Compose, Koin, and modular Gradle components.

### Module Structure
- `:core:model` — Pure Kotlin models (`SystemTelemetrySnapshot`, `MetricValue`, `ThrottleState`, `RawTelemetryPacket`).
- `:core:animation-contract` — SPI interfaces (`HudAnimationPlugin`, `AnimationManifest`, `AnimationParameterMapper`, `SensorType`).
- `:core:telemetry-api` — Collector abstractions (`TelemetrySource`, `CpuCollector`, `RamCollector`, `NetworkCollector`).
- `:core:telemetry-mock` — Synthetic generator (`FakeTelemetrySource`) and scripted test scenarios (`SimulationScenario`).
- `:core:telemetry-fusion` — Low-pass filtering (`EmaFilter`), throttle detection (`ThrottlingClassifier`), and adaptive polling daemon (`TelemetryFusionEngine`).
- `:core:designsystem` — Neon color tokens (`NeonPalette`) and 3D perspective math (`PerspectiveProjection3D`).
- `:animations:holographic-rings` — 3D Holographic Concentric Rings HUD module with quadratic speed model ($V_{rot} = 0.2 + (L/100)^2 \cdot 4.8$) and bloom shaders.
- `:app` — Aggregator application, foreground `MonitorService`, and Material 3 companion dashboard (`StartScreen`).

---

## 2. Testing Directives & Device Prioritization

> [!IMPORTANT]
> **PHYSICAL DEVICE PRIORITY: PREFER GOOGLE PIXEL 8**
> When performing physical on-device testing, APK installation, wireless debugging, runtime stability verification, or screenshot regression capture:
> 1. **Primary / Preferred Target:** **Google Pixel 8 (`shiba`)**.
>    - Always select the Pixel 8 instance when multiple devices are attached.
>    - Example: `adb -s <pixel_8_device_id> install -r -g ...`
> 2. **Secondary Target:** Google Pixel 11 Pro (`caiman` / `grizzly`).
>    - Retained for cross-device validation and future Android OS release checks.

---

## 3. Development Workflow

- **Unit Testing:** Run `./gradlew testDebugUnitTest` across all modules.
- **Assembly:** Run `./gradlew assembleDebug`.
- **Decoupled Animation Preview:** Use Compose Previews inside `:animations:*` modules without device hardware.
- **On-Device Sandbox:** Use the companion app's live simulator chips to test stress scenarios ("Meltdown", "Gaming Load", "Cellular Drop") without heating physical hardware.
