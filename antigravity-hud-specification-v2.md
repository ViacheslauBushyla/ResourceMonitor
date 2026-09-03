# Antigravity: Ambient Hardware & Throttling Status Monitor
## Comprehensive Engineering and UX Specification for Mobile Telemetry HUD


## 1. Executive Summary & Core Objectives

Modern high-end smartphones (such as Google Pixel series devices) exhibit complex performance profiles governed by tight thermal limits, multi-core frequency scaling, dynamic zRAM compaction, and baseband modem throttling. Traditional floating monitoring tools or persistent raw notification numbers create substantial visual clutter and disrupt everyday smartphone usage. The goal of the Antigravity architecture is to design a unified, ambient, real-time status bar telemetry HUD that monitors CPU/GPU load, thermal clock-capping, RAM pressure, storage I/O wait, and cellular/Wi-Fi link degradation concurrently through minimalist edge animations and micro-indicators without obstructing native status bar icons. By translating complex multi-metric system events into non-distracting visual cues, users can immediately identify hardware bottlenecks and understand throttling behavior under peak loads.


## 2. Core Telemetry Subsystems & Throttling Triggers

To accurately discern genuine hardware starvation and throttling from standard transient load, the Antigravity monitor tracks five fundamental telemetry subsystems:

* CPU / GPU Utilization & Thermal Governor Step-Down: Compares active core load percentage against the current scaled governor frequencies (e.g., in /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq). A condition where CPU utilization approaches 90–100% while operating frequencies are pinned at minimum base clocks (e.g., 394–450 MHz) triggers an explicit thermal throttling state rather than a standard boost indicator.
* Memory Headroom & zRAM Compression Thrashing: Evaluates physical RAM consumption alongside active kernel swap-out/swap-in rates. High memory consumption paired with sustained zRAM compression and direct page reclaim indicates active memory thrashing.
* Cellular RF Link & Modem Backoff: Samples real-time radio metrics including Reference Signal Received Power (RSRP), Reference Signal Received Quality (RSRQ), and Signal-to-Interference-plus-Noise Ratio (SINR). Degradation below critical thresholds (e.g., RSRP worse than -115 dBm or RSRQ degrading beyond -15 dB) or baseband thermal fallback from 5G to LTE signals link-layer throttling or edge-of-cell degradation.
* Wi-Fi Airtime Starvation & Co-Channel Contention: Measures packet retry rate, link negotiation speed, and transport latency spikes to flag upstream bufferbloat or channel interference.
* Storage Controller / SSD I/O Wait: Interrogates iowait kernel cycles and write queue depths to visualize storage stalls during intensive I/O operations such as app installations or large media flushes.

## 3. Spatial Allocation & UI Overlay Architecture

To avoid cluttering the native Android status bar, each hardware metric is assigned its own non-intrusive visual layer or screen boundary. By utilizing the screen bezels, under-icon areas, and gaps between text labels, the application ensures clear multi-metric visibility without overlapping existing icons or notification badges.

```
Status Bar HUD Interface Layout Map:
```
```
[  RAM / zRAM Edge Line  ]                  [  CPU/GPU Thermal Bezel Bar  ]
  +-------------------------------------------------------------------------+
  | 19:49  [~1.2MB/s~]        ( O )                5G  ||||  [56%]  [  SSD  ]
  +-------------------------------------------------------------------------+
             ^                                      ^                ^
     Network Sine-Wave                         Signal Aura      I/O Tracer
```

## 4. All-in-One Multi-Metric Behavior Matrix

To ensure clarity at a glance, the interface uses two distinct visual dimensions:
1) Stroke Size/Length = Raw Utilization (how much work the hardware is handling).
2) Color Temperature & Pulse Rate = Throttling/Health State (whether the component is throttled).

The following matrix maps the exact behavioral rules across nominal, peak, and critical throttled states:


## 5. Android System Architecture & Low-Level API Integration

To achieve sub-second diagnostic fidelity without draining battery or requiring system framework changes, the Antigravity engine relies on dual implementation strategies depending on device permissions:


### 5.1 Non-Root WindowManager Overlay Service

For standard consumer installation, the HUD runs as a background Android Service utilizing WindowManager:
• Overlay Configuration: Configured via TYPE_APPLICATION_OVERLAY window parameters combined with FLAG_NOT_FOCUSABLE, FLAG_NOT_TOUCHABLE, and FLAG_LAYOUT_IN_SCREEN to align with physical notch boundaries.
• Network Diagnostics: Reads throughput statistics directly via android.net.TrafficStats and listens for RSRP/RSRQ/SINR fluctuations utilizing TelephonyCallback.registerTelephonyCallback().
• UI Vector Compositing: Draws real-time indicators via a custom android.graphics.Canvas using Jetpack Compose, applying PorterDuff.Mode.SRC_ATOP composition for soft, anti-aliased status-icon underglows.


### 5.2 Shizuku / Low-Level Kernel Telemetry Engine

For advanced users (such as Google Pixel 11 Pro developers), the app requests low-level sysfs access facilitated by root or Shizuku shell bindings:
• Governor Tracking: Direct access to /sys/class/thermal/thermal_zone*/temp and /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq to calculate the real-time thermal throttling ratio: (Actual Frequency / Maximum Available Governor Frequency).
• Virtual Memory Pressure: Samples /proc/vmstat at sub-second frequencies, checking zram_stored_pages, pgpgin/pgpgout, and compact_stall events to dynamically render real-time zRAM compression rates.


## 6. Companion App Architecture & Material 3 Control Panel

To make the telemetry monitor user-friendly, the management app provides an intuitive dashboard. The companion application is organized into modular cards, emphasizing simple controls, zero-restart layout refreshes, and an interactive testing sandbox.

* Card 1: Master Engine & Permissions: Controls the primary background service overlay switch, 'Start On Boot' toggles, and tracks necessary permissions status (Draw Over Other Apps [✓], Shizuku Server Connection [✓], Post Notifications [✓]).
* Card 2: Subsystem Metric Toggles: Allows the user to individually enable/disable telemetry layers. Subsystems feature expandable configurations (e.g., selecting CPU cluster tracking like Big-Core vs Core-Average, or adjusting network RF metrics).
* Card 3: Auto-Hide & Ambient Rules (Calm State): To keep the UI stock when idle, users can toggle 'Hide When Idle'. The overlay automatically fades out when CPU load drops below 30% and signal quality remains nominal. An application exclusion whitelist automatically suspends the overlay when fullscreen media apps or security-sensitive applications are active.
* Card 4: Sandbox Live Simulator: Tapping simulation chips (e.g., 'Test CPU Throttle', 'Test 5G Drop', 'Test RAM Thrash') forces the overlay into specified throttling styles for 5 seconds, allowing users to verify their visual configuration immediately.
Adaptive Polling Logic for Efficient Operation:

To minimize power drain, Antigravity uses a dynamic polling daemon:
• 250ms High-Precision Sampling: Automatically triggered only when high network throughput is active, heavy gaming is launched, or hardware triggers a throttled state.
• 2000ms Low-Power Decay: Fades out polling intervals when the device is idle, locked, or running minimal background threads.


## 7. Ambient Theme Gallery: 3D Holographic Rings HUD

A key design pillar of the Antigravity application is the 'Ambient Animation Gallery'. Rather than being locked into a single status bar style, users can choose from a library of available visual themes. The '3D Holographic Rings' HUD is an alternative three-dimensional visualization of real-time hardware status. It translates critical hardware load into the physics of rotating concentric neon rings.


### 7.1 Visual Assets & Ring Mapping

The 3D Holographic Rings HUD represents telemetry as three concentric neon rings floating in 3D space, as shown in the reference render below (available as telemetry_3d_hud_glowing_sphere.png in the project artifacts):
• Outer Ring (CPU Usage): Cyan (#00FFFF) — Rotates primarily around the Y-axis (horizontal with a 15-degree tilt).
• Middle Ring (RAM Status): Orange (#FF8C00) — Rotates primarily around the X-axis (vertical with a 15-degree tilt).
• Inner Ring (GPU / Network load): Magenta (#FF00FF) — Rotates around a custom diagonal axis tilted at 45 degrees.


*Figure 7.1: Real-time 3D Holographic Rings HUD visualization showing peak system load.*

![3D Holographic Rings HUD](telemetry_3d_hud_glowing_sphere.png)


### 7.2 Animation Physics & Speed Calculations

To provide an intuitive feel, the rotational speed of each ring is tied to the hardware load using a quadratic relationship. A quadratic model ensures that at lower loads, the rings maintain a slow, calming rotation that does not distract the user, while high loads result in a dramatic acceleration that is immediately recognizable.

The rotational speed (V_rot, in revolutions per second) is calculated as:


```
V_rot = V_min + ( L / 100 )^2 * ( V_max - V_min )
```


```
Where:
• L represents the current hardware load percentage (ranging from 0 to 100).
• V_min is set to 0.2 RPS (Revolutions Per Second), maintaining a relaxed, steady-state movement when idle.
• V_max is set to 5.0 RPS, establishing a highly kinetic blur at maximum system utilization.
```


### 7.3 Peak Load, Motion Blur, and Thermal Spheroid Meltdown

When any hardware component reaches the critical threshold of L >= 90%, or when the CPU enters a thermal throttling state, the 3D animation transforms to create a strong visual alarm:
1. Motion Blur Rendering: Due to the high rotation speed (V_max = 5.0 RPS), the individual boundaries of the concentric rings blur and intersect, visually merging into a single glowing sphere.
2. Bloom & Glow Shaders: The HUD engine triggers an intensive bloom shader overlay, increasing neon light intensity around the sphere.
3. Thermal Spheroid Meltdown Pulsing: If the peak load represents an active thermal throttling crisis, the sphere shifts its color gamut, pulsing in an aggressive red spectrum (600nm wavelength emission) to alert the user to take cooling action.


## 8. Hardware Sensors & Diagnostic Data Acquisition Specifications

To fuel the real-time status bar overlays and the 3D Holographic Rings HUD with sub-second telemetry, the Antigravity engine establishes a robust diagnostic layer. This layer aggregates and calibrates raw data from low-level hardware sensors, system APIs, and kernel interfaces. Depending on the device's permission state (Standard User vs. Shizuku Developer), the telemetry system dynamically balances sensor polling frequencies and data-retrieval overhead to ensure continuous operation without impacting battery life.


### 8.1 Low-Level Kernel File Nodes & Polling Paths

* CPU & GPU Thermal Sensors: The system reads hardware temperatures by interrogating the low-level kernel thermal zone trip points located at /sys/class/thermal/thermal_zone*/temp. These readings (typically formatted in millidegrees Celsius) are paired with active clock speed scaling tables parsed from /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq to calculate the precise throttling ratio (Actual Clock Speed / Maximum Governor Frequency) for both the Big-Core clusters and GPU cores.
* Virtual Memory Pressure & Swap (zRAM) Sensors: To track memory pressure, zRAM allocation, and kernel compaction cycles, the telemetry engine polls the /proc/vmstat file. The system specifically parses variables such as zram_stored_pages (indicating the active size of pages compressed in zRAM), compact_stall (indicating active memory allocator stalls and direct page reclaim), and pgpgin/pgpgout counts to calculate active thrashing thresholds. This prevents raw memory consumption percentages from misrepresenting actual system usability.
* Storage Controller / SSD I/O Queue Sensors: SSD performance and storage queue wait times are captured via /proc/diskstats and system-wide iowait cycle indicators from /proc/stat. Sustained queue depths and prolonged write queue locks generate an immediate iowait stall flag, bypassing standard background buffer flushes and warning the user of potential database or file system lockups.

### 8.2 Android Telephony and Wi-Fi API Mapping

* Cellular Link Quality Sensors (RSRP / RSRQ / SINR): For network diagnostics, the non-root WindowManager service registers a TelephonyCallback utilizing registerTelephonyCallback() to receive sub-second updates from the device's baseband modem. The system parses the SignalStrength callback payload to extract Reference Signal Received Power (RSRP), Reference Signal Received Quality (RSRQ), and Signal-to-Interference-plus-Noise Ratio (SINR). When the RSRP degrades below -115 dBm or the RSRQ degrades beyond -15 dB, the modem's thermal backoff protocol or edge-of-cell degradation is immediately reported to the visual overlay engine.
* Wi-Fi Link Integrity and Airtime Starvation: The Wi-Fi sensor layer interrogates Android's WifiManager to evaluate link-layer performance. Metrics tracked include co-channel interference levels across the 2.4 GHz and 5 GHz bands, link negotiation speed drops, packet retry rates, and round-trip transport latency jitter. High retry rates paired with zero bandwidth throughput signify airtime starvation or captive portal routing stalls.
* Network Socket Throughput Counters: Raw network speed is continuously computed via android.net.TrafficStats and NetworkStatsManager. By comparing current socket traffic rates against theoretical signal-layer bandwidth, the data fusion core isolates hardware-throttled bandwidth caps from standard upstream network congestion.

### 8.3 Dynamic Data Fusion & Sensor Polling Controls


```
Raw telemetry values from these sensors are channeled into a central fusion core before being dispatched to the HUD overlay and 3D Holographic Rings animation physics. The engine calibrates physical signal values and governor step-down states into standard normalized loads (0.0 to 1.0) and thermal threat matrices. For example, the 3D Holographic Rings speed calculation uses the CPU and RAM sensor outputs as direct parameters for its quadratic rotation formula: V_rot = V_min + (L/100)^2 * (V_max - V_min).
```

To eliminate idle battery drain, the telemetry daemon shifts sensor polling intervals dynamically based on device state:
1. Active State (250ms Polling): Triggered during intensive gaming, sustained network download bursts exceeding 1.0 MB/s, or when any hardware sensor reports critical throttling (e.g., CPU capped at base clocks or RSRP worse than -115 dBm).
2. Low-Power Decay State (2000ms Polling): Initiated when the device enters an idle state (CPU < 30%), the screen is locked, or when all telemetry indicators remain in their calm/nominal green status.


## Appendix: Reference Matrix & Tables

### Table 1

| Hardware Subsystem | Display Anchor / Location | Visual Overlay Element | Dimension Meaning |
| --- | --- | --- | --- |
| CPU / GPU & Thermal State | Top-Right Screen Bezel Edge | 2px–3px dynamic line flush against screen border | Length = Total load %; Color/Animation = Governor state & thermal throttling. |
| RAM & zRAM Compression | Top-Left Screen Bezel Edge | 2px dynamic line flush above clock | Length = RAM capacity allocated; Color/Strobe = Active swap/zRAM compression rate. |
| Network Throughput | Directly beneath numeric speed readout | Micro sine-wave / scrolling oscillation | Wave Amplitude = Transfer volume; Continuity = Socket stream stability. |
| Cellular / Wi-Fi Link | Directly behind / beneath 5G & Signal Bars | Ambient underglow aura / icon color accents | Color Hue = RSRP / SINR RF quality; Strobing = Modem backoff or packet loss. |
| Storage (SSD) I/O Wait | Top Center / Corner Radius Junction | Horizontal tracer flare / static pin marker | Sweeping flare = Active disk write flush; Static pin = Kernel iowait stall. |


### Table 2

| Telemetry Channel | State 1: Nominal / Idle | State 2: Peak / High Load | State 3: Critical Throttling |
| --- | --- | --- | --- |
| CPU / GPU Line | Thin lime-green line (1.5px), length < 25% | Solid vibrant orange bar (2.5px), length 70–85% (Active Governor Boost) | Bright crimson red line (3px), rapid breathing strobe (Frequency pinned at base clock) |
| RAM / zRAM Line | Subtle cyan stroke, normal steady length | Amber extension as paging initiates | Deep purple bar with rapid yellow flickers (Kernel direct reclaim & memory thrashing) |
| Cellular (5G / LTE) | Subtle emerald/cyan under-icon aura; solid signal bars (RSRP > -80 dBm) | Warm yellow aura; moderate bar fill (RSRP ~ -95 dBm) | Pulsing crimson halo; single flashing pip; alert indicator (RSRP < -115 dBm, RSRQ < -15 dB) |
| Wi-Fi Link | Static clean icon fill; stable latency | High throughput wave modulation | Strobing amber/red arc dashes on top wave curve (Packet drop > 15%, airtime starvation) |
| Network Speed Wave | Slow, shallow-amplitude green sine ripple | Rapid, high-amplitude orange wave under numeric readout | Dotted flatline / erratic sawtooth spikes (Socket timeouts, TCP stalls, bufferbloat) |
| Storage I/O | Inactive / dark | Occasional white/cyan horizontal light flare during buffer flushes | Stationary red pin marker at center bezel (Persistent iowait bottleneck) |


## 9. Target Hardware & Testing Guidelines

* **Primary / Preferred Test Target:** **Google Pixel 8 (`shiba`)**. All physical on-device QA, overlay performance verification, thermal profiling, and visual validation should default to Google Pixel 8.
* **Secondary Reference Target:** Google Pixel 11 Pro (`caiman`/`grizzly`). Used for multi-device scalability testing and future Android release validation.


