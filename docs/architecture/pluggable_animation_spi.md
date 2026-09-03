# Antigravity HUD: Pluggable Animation SPI Guide
**Document Version:** 1.0  
**Package:** `com.vnazarov.resourcemonitor.animation.spi`

---

## 1. Motivation & Purpose

To enable modularization, monetize visual animations, and allow independent testing, Antigravity HUD decouples all graphics rendering into isolated modules conforming to the `HudAnimationPlugin` SPI. Animation modules have zero direct dependencies on Android system APIs, kernel nodes, or hardware collectors.

---

## 2. Core SPI Contracts

### 2.1 The Plugin Interface
```kotlin
package com.vnazarov.resourcemonitor.animation.spi

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

interface HudAnimationPlugin {
    val manifest: AnimationManifest
    val parameterMapper: AnimationParameterMapper

    @Composable
    fun Render(
        parameters: Any,
        modifier: Modifier
    )

    @Composable
    fun Preview(modifier: Modifier = Modifier)

    @Composable
    fun ConfigUi(
        config: Map<String, Any>,
        onConfigChanged: (key: String, value: Any) -> Unit,
        modifier: Modifier = Modifier
    )
}
```

### 2.2 Animation Manifest
```kotlin
data class AnimationManifest(
    val id: String,
    val displayName: String,
    val description: String,
    val author: String,
    val version: Int,
    val requiredSensors: Set<SensorType>,
    val previewThumbnailResId: Int,
    val configParameters: List<ConfigPropertyDefinition<*>> = emptyList()
)

enum class SensorType {
    CPU_LOAD,
    CPU_FREQUENCY,
    CPU_TEMPERATURE,
    RAM_USAGE,
    ZRAM_THRASHING,
    NETWORK_THROUGHPUT,
    CELLULAR_RF_QUALITY,
    STORAGE_IO_WAIT
}
```

### 2.3 Parameter Mapper
```kotlin
interface AnimationParameterMapper {
    fun map(snapshot: SystemTelemetrySnapshot, config: Map<String, Any>): Any
}
```

---

## 3. Creating a New Animation Module: Step-by-Step

### Step 1: Create the Gradle Module
Add `:animations:my-theme` in `settings.gradle.kts`:
```kotlin
include(":animations:my-theme")
```
In `animations/my-theme/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:animation-contract"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
```

### Step 2: Define the Animation Parameter Contract
```kotlin
data class MyThemeParameters(
    val rotationSpeed: Float,
    val glowColor: Long,
    val isAlertActive: Boolean
)
```

### Step 3: Implement `HudAnimationPlugin`
```kotlin
class MyThemePlugin : HudAnimationPlugin {
    override val manifest = AnimationManifest(
        id = "my_theme",
        displayName = "My Futuristic Theme",
        description = "Custom kinetic visual theme",
        author = "Author Name",
        version = 1,
        requiredSensors = setOf(SensorType.CPU_LOAD, SensorType.RAM_USAGE),
        previewThumbnailResId = R.drawable.preview_my_theme
    )

    override val parameterMapper = object : AnimationParameterMapper {
        override fun map(snapshot: SystemTelemetrySnapshot, config: Map<String, Any>): MyThemeParameters {
            val cpu = snapshot.cpu.smoothedValue
            return MyThemeParameters(
                rotationSpeed = 0.2f + (cpu * cpu) * 4.8f,
                glowColor = if (snapshot.worstThrottleState == ThrottleState.CRITICAL_THROTTLED) 0xFFFF0033 else 0xFF00FFFF,
                isAlertActive = snapshot.worstThrottleState == ThrottleState.CRITICAL_THROTTLED
            )
        }
    }

    @Composable
    override fun Render(parameters: Any, modifier: Modifier) {
        val params = parameters as? MyThemeParameters ?: return
        // Stateless Compose Canvas / AGSL drawing code
    }

    @Composable
    override fun Preview(modifier: Modifier) {
        Render(
            parameters = MyThemeParameters(rotationSpeed = 1.5f, glowColor = 0xFF00FFFF, isAlertActive = false),
            modifier = modifier
        )
    }

    @Composable
    override fun ConfigUi(config: Map<String, Any>, onConfigChanged: (String, Any) -> Unit, modifier: Modifier) {
        // Optional sliders/toggles for theme properties
    }
}
```

### Step 4: Register via Koin
```kotlin
val myThemeModule = module {
    single<HudAnimationPlugin> { MyThemePlugin() }
}
```

---

## 4. Testing the Animation Module in Complete Isolation

Because the plugin is purely driven by `MyThemeParameters`:
1. **Compose Previews:** Add `@Preview` functions supplying different parameter combinations directly in Android Studio.
2. **Roborazzi / Paparazzi Snapshot Tests:** Write JVM snapshot tests passing deterministic parameter bundles and asserting golden master image diffs.
3. **No Android OS Telemetry Dependencies:** Zero mocking of `/proc`, `/sys`, or `TelephonyCallback` required to verify visual correctness.
