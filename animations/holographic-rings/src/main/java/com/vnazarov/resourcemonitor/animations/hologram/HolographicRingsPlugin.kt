package com.vnazarov.resourcemonitor.animations.hologram

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.vnazarov.resourcemonitor.core.animation.contract.AnimationManifest
import com.vnazarov.resourcemonitor.core.animation.contract.AnimationParameterMapper
import com.vnazarov.resourcemonitor.core.animation.contract.ConfigPropertyDefinition
import com.vnazarov.resourcemonitor.core.animation.contract.HudAnimationPlugin
import com.vnazarov.resourcemonitor.core.animation.contract.SensorType
import com.vnazarov.resourcemonitor.core.designsystem.theme.NeonPalette
import com.vnazarov.resourcemonitor.core.model.SystemTelemetrySnapshot

class HolographicRingsPlugin : HudAnimationPlugin<HolographicRingsParams> {

    companion object {
        const val ID = "holographic_concentric_rings"
        const val V_MIN_RPS = 0.2f
        const val V_MAX_RPS = 5.0f

        /**
         * Quadratic speed formula: V_rot = V_min + (L / 100)^2 * (V_max - V_min)
         * normalizedLoad in [0.0..1.0]
         */
        fun calculateQuadraticSpeed(normalizedLoad: Float): Float {
            return HologramProjectionCalculator.calculateSpeed(
                normalizedLoad = normalizedLoad,
                sensitivity = 1.0f,
                config = HologramBehaviorConfig(speedCurve = SpeedCurve.QUADRATIC)
            )
        }

        fun parseConfig(config: Map<String, Any>): HologramBehaviorConfig {
            val defaultConfig = HologramBehaviorConfig()

            val vMinRps = (config["vMinRps"] as? Number)?.toFloat() ?: defaultConfig.vMinRps
            val vMaxRps = (config["vMaxRps"] as? Number)?.toFloat() ?: defaultConfig.vMaxRps
            val speedCurve = when (val curve = config["speedCurve"]) {
                is SpeedCurve -> curve
                is String -> try {
                    SpeedCurve.valueOf(curve.uppercase())
                } catch (e: IllegalArgumentException) {
                    defaultConfig.speedCurve
                }
                else -> defaultConfig.speedCurve
            }
            val outerSensitivity = (config["outerSensitivity"] as? Number)?.toFloat() ?: defaultConfig.outerSensitivity
            val middleSensitivity = (config["middleSensitivity"] as? Number)?.toFloat() ?: defaultConfig.middleSensitivity
            val innerSensitivity = (config["innerSensitivity"] as? Number)?.toFloat() ?: defaultConfig.innerSensitivity
            val boostThreshold = (config["boostThreshold"] as? Number)?.toFloat() ?: defaultConfig.boostThreshold
            val meltdownThreshold = (config["meltdownThreshold"] as? Number)?.toFloat() ?: defaultConfig.meltdownThreshold

            val outerBaseColor = (config["outerBaseColor"] as? Color) ?: defaultConfig.outerBaseColor
            val middleBaseColor = (config["middleBaseColor"] as? Color) ?: defaultConfig.middleBaseColor
            val innerBaseColor = (config["innerBaseColor"] as? Color) ?: defaultConfig.innerBaseColor
            val alertAmberColor = (config["alertAmberColor"] as? Color) ?: defaultConfig.alertAmberColor
            val alertMeltdownColor = (config["alertMeltdownColor"] as? Color) ?: defaultConfig.alertMeltdownColor
            val alertThrashPurpleColor = (config["alertThrashPurpleColor"] as? Color) ?: defaultConfig.alertThrashPurpleColor

            return HologramBehaviorConfig(
                vMinRps = vMinRps,
                vMaxRps = vMaxRps,
                speedCurve = speedCurve,
                outerSensitivity = outerSensitivity,
                middleSensitivity = middleSensitivity,
                innerSensitivity = innerSensitivity,
                boostThreshold = boostThreshold,
                meltdownThreshold = meltdownThreshold,
                outerBaseColor = outerBaseColor,
                middleBaseColor = middleBaseColor,
                innerBaseColor = innerBaseColor,
                alertAmberColor = alertAmberColor,
                alertMeltdownColor = alertMeltdownColor,
                alertThrashPurpleColor = alertThrashPurpleColor
            )
        }
    }

    override val manifest: AnimationManifest = AnimationManifest(
        id = ID,
        displayName = "3D Holographic Rings",
        description = "Futuristic 3D concentric neon rings rotating quadratically with hardware load and thermal meltdown alerts.",
        author = "Antigravity",
        version = 1,
        requiredSensors = setOf(
            SensorType.CPU_LOAD,
            SensorType.RAM_USAGE,
            SensorType.NETWORK_THROUGHPUT
        ),
        previewThumbnailResId = 0,
        configProperties = listOf(
            ConfigPropertyDefinition.FloatRangeProperty(
                key = "bloomIntensity",
                displayName = "Bloom Intensity",
                description = "Multiplier for glowing neon bloom radiance",
                defaultValue = 1.0f,
                minValue = 0.5f,
                maxValue = 2.5f,
                step = 0.05f
            ),
            ConfigPropertyDefinition.FloatRangeProperty(
                key = "vMinRps",
                displayName = "Min Speed (RPS)",
                description = "Base minimum rotational speed in RPS",
                defaultValue = 0.2f,
                minValue = 0.05f,
                maxValue = 2.0f,
                step = 0.05f
            ),
            ConfigPropertyDefinition.FloatRangeProperty(
                key = "vMaxRps",
                displayName = "Max Speed (RPS)",
                description = "Maximum rotational speed under peak load in RPS",
                defaultValue = 5.0f,
                minValue = 1.0f,
                maxValue = 10.0f,
                step = 0.1f
            ),
            ConfigPropertyDefinition.ChoiceProperty(
                key = "speedCurve",
                displayName = "Speed Curve",
                description = "Mathematical response curve for load-to-speed projection",
                defaultValue = "QUADRATIC",
                options = listOf("QUADRATIC", "LINEAR", "SIGMOID")
            ),
            ConfigPropertyDefinition.FloatRangeProperty(
                key = "outerSensitivity",
                displayName = "Outer Circle Sensitivity",
                description = "Sensitivity multiplier for CPU circle",
                defaultValue = 1.0f,
                minValue = 0.1f,
                maxValue = 2.0f,
                step = 0.05f
            ),
            ConfigPropertyDefinition.FloatRangeProperty(
                key = "middleSensitivity",
                displayName = "Middle Circle Sensitivity",
                description = "Sensitivity multiplier for RAM circle",
                defaultValue = 1.0f,
                minValue = 0.1f,
                maxValue = 2.0f,
                step = 0.05f
            ),
            ConfigPropertyDefinition.FloatRangeProperty(
                key = "innerSensitivity",
                displayName = "Inner Circle Sensitivity",
                description = "Sensitivity multiplier for Network circle",
                defaultValue = 1.0f,
                minValue = 0.1f,
                maxValue = 2.0f,
                step = 0.05f
            ),
            ConfigPropertyDefinition.FloatRangeProperty(
                key = "boostThreshold",
                displayName = "Boost Threshold",
                description = "Threshold for system boost state",
                defaultValue = 0.70f,
                minValue = 0.40f,
                maxValue = 0.90f,
                step = 0.05f
            ),
            ConfigPropertyDefinition.FloatRangeProperty(
                key = "meltdownThreshold",
                displayName = "Meltdown Threshold",
                description = "Threshold for thermal meltdown state",
                defaultValue = 0.90f,
                minValue = 0.70f,
                maxValue = 1.0f,
                step = 0.05f
            )
        )
    )

    override val parameterMapper: AnimationParameterMapper<HolographicRingsParams> =
        object : AnimationParameterMapper<HolographicRingsParams> {
            override fun map(
                snapshot: SystemTelemetrySnapshot,
                config: Map<String, Any>
            ): HolographicRingsParams {
                val behaviorConfig = parseConfig(config)
                val bloom = (config["bloomIntensity"] as? Number)?.toFloat() ?: 1.0f
                return HologramProjectionCalculator.computeParameters(
                    snapshot = snapshot,
                    config = behaviorConfig,
                    bloomMultiplier = bloom
                )
            }
        }

    @Composable
    override fun Render(parameters: HolographicRingsParams, modifier: Modifier) {
        HolographicRingsRenderer(params = parameters, modifier = modifier)
    }

    @Composable
    override fun Preview(modifier: Modifier) {
        HolographicRingsIdlePreview()
    }

    @Composable
    override fun ConfigUi(
        config: Map<String, Any>,
        onConfigChanged: (key: String, value: Any) -> Unit,
        modifier: Modifier
    ) {
        val currentBloom = (config["bloomIntensity"] as? Float) ?: 1.0f
        var sliderValue by remember(currentBloom) { mutableFloatStateOf(currentBloom) }

        Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Hologram Settings",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Bloom Radiance: ${String.format("%.1f", sliderValue)}x")
            }
            Slider(
                value = sliderValue,
                onValueChange = {
                    sliderValue = it
                    onConfigChanged("bloomIntensity", it)
                },
                valueRange = 0.5f..2.5f
            )
        }
    }
}
