package com.vnazarov.resourcemonitor.presentation.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vnazarov.resourcemonitor.animations.hologram.HologramBehaviorConfig
import com.vnazarov.resourcemonitor.animations.hologram.HologramInvariantCase
import com.vnazarov.resourcemonitor.animations.hologram.HologramInvariantCases
import com.vnazarov.resourcemonitor.animations.hologram.HologramProjectionCalculator
import com.vnazarov.resourcemonitor.animations.hologram.HolographicRingsPlugin
import com.vnazarov.resourcemonitor.animations.hologram.SpeedCurve
import com.vnazarov.resourcemonitor.core.designsystem.theme.NeonPalette
import com.vnazarov.resourcemonitor.core.telemetry.fusion.TelemetryFusionEngine
import com.vnazarov.resourcemonitor.core.telemetry.mock.FakeTelemetrySource
import com.vnazarov.resourcemonitor.core.telemetry.mock.SimulationScenario
import java.util.Locale
import kotlin.math.roundToInt
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StartScreen(
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues.Zero,
    onRegisterChange: () -> Unit = {},
    fakeSource: FakeTelemetrySource = koinInject(),
    fusionEngine: TelemetryFusionEngine = koinInject(),
    plugin: HolographicRingsPlugin = koinInject()
) {
    var config by remember { mutableStateOf(HologramBehaviorConfig()) }
    var selectedCaseId by remember { mutableIntStateOf(1) }
    var selectedCategory by remember { mutableStateOf("All") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val snapshot by fusionEngine.snapshot.collectAsState()
    val currentParams = remember(snapshot, config) {
        HologramProjectionCalculator.computeParameters(snapshot, config)
    }

    val selectCase: (HologramInvariantCase) -> Unit = { case ->
        selectedCaseId = case.id
        fusionEngine.injectPacket(case.toRawPacket())
        when (case.id) {
            2 -> fakeSource.setScenario(SimulationScenario.IdleCalm)
            12 -> fakeSource.setScenario(SimulationScenario.PeakGaming)
            25 -> fakeSource.setScenario(SimulationScenario.ThermalMeltdown)
            21, 22, 23 -> fakeSource.setScenario(SimulationScenario.CellularDrop)
            else -> {}
        }
    }

    LaunchedEffect(Unit) {
        val initialCase = HologramInvariantCases.getById(selectedCaseId) ?: HologramInvariantCases.ALL_CASES.first()
        selectCase(initialCase)
    }

    val filteredCases = remember(selectedCategory) {
        when {
            selectedCategory.startsWith("All") -> HologramInvariantCases.ALL_CASES
            selectedCategory == "Baseline" -> HologramInvariantCases.ALL_CASES.filter { it.group.startsWith("Baseline") }
            selectedCategory == "Single-Metric" -> HologramInvariantCases.ALL_CASES.filter { it.group.startsWith("Single-Metric") }
            selectedCategory == "Dual-Metric" -> HologramInvariantCases.ALL_CASES.filter { it.group.startsWith("Dual-Metric") }
            selectedCategory == "Balanced" -> HologramInvariantCases.ALL_CASES.filter { it.group.startsWith("Balanced") }
            selectedCategory == "Cellular" -> HologramInvariantCases.ALL_CASES.filter { it.group.startsWith("Cellular") }
            selectedCategory == "Thermal" -> HologramInvariantCases.ALL_CASES.filter { it.group.startsWith("Thermal") }
            selectedCategory == "Robustness" -> HologramInvariantCases.ALL_CASES.filter { it.group.startsWith("Mathematical") || it.group.contains("Robustness") }
            else -> HologramInvariantCases.ALL_CASES
        }
    }

    val currentCase = remember(selectedCaseId) {
        HologramInvariantCases.getById(selectedCaseId) ?: HologramInvariantCases.ALL_CASES.first()
    }

    val currentIndex = filteredCases.indexOfFirst { it.id == selectedCaseId }
    val prevCase = if (currentIndex > 0) {
        filteredCases[currentIndex - 1]
    } else {
        filteredCases.lastOrNull() ?: HologramInvariantCases.ALL_CASES.first()
    }
    val nextCase = if (currentIndex >= 0 && currentIndex < filteredCases.size - 1) {
        filteredCases[currentIndex + 1]
    } else {
        filteredCases.firstOrNull() ?: HologramInvariantCases.ALL_CASES.first()
    }

    Column(
        modifier = modifier
            .padding(paddingValues)
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Antigravity HUD Controller",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onRegisterChange() }
        ) {
            Text(
                text = "Toggle Overlay Service",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "3D Holographic Concentric Rings HUD",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        plugin.Render(
            parameters = currentParams,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Live Computed Speed & Status Badges",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SpeedBadge(
                label = "Outer (CPU)",
                speedRps = currentParams.outerRingSpeedRps,
                color = currentParams.outerColor,
                modifier = Modifier.weight(1f)
            )
            SpeedBadge(
                label = "Middle (RAM)",
                speedRps = currentParams.middleRingSpeedRps,
                color = currentParams.middleColor,
                modifier = Modifier.weight(1f)
            )
            SpeedBadge(
                label = "Inner (Net)",
                speedRps = currentParams.innerRingSpeedRps,
                color = currentParams.innerColor,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (currentParams.isMeltdownAlert) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.weight(1.2f)
            ) {
                Text(
                    text = currentParams.systemStatusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (currentParams.isMeltdownAlert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = currentParams.energyOutputLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "30-Case Invariant Selector",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Select invariant permutations to test rotational speeds & alert states",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        val categories = listOf(
            "All (30)",
            "Baseline",
            "Single-Metric",
            "Dual-Metric",
            "Balanced",
            "Cellular",
            "Thermal",
            "Robustness"
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { category ->
                val isSelected = if (category == "All (30)") selectedCategory.startsWith("All") else selectedCategory == category
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedCategory = if (category == "All (30)") "All" else category
                        val newFiltered = when {
                            category.startsWith("All") -> HologramInvariantCases.ALL_CASES
                            category == "Baseline" -> HologramInvariantCases.ALL_CASES.filter { it.group.startsWith("Baseline") }
                            category == "Single-Metric" -> HologramInvariantCases.ALL_CASES.filter { it.group.startsWith("Single-Metric") }
                            category == "Dual-Metric" -> HologramInvariantCases.ALL_CASES.filter { it.group.startsWith("Dual-Metric") }
                            category == "Balanced" -> HologramInvariantCases.ALL_CASES.filter { it.group.startsWith("Balanced") }
                            category == "Cellular" -> HologramInvariantCases.ALL_CASES.filter { it.group.startsWith("Cellular") }
                            category == "Thermal" -> HologramInvariantCases.ALL_CASES.filter { it.group.startsWith("Thermal") }
                            category == "Robustness" -> HologramInvariantCases.ALL_CASES.filter { it.group.startsWith("Mathematical") || it.group.contains("Robustness") }
                            else -> HologramInvariantCases.ALL_CASES
                        }
                        if (newFiltered.none { it.id == selectedCaseId } && newFiltered.isNotEmpty()) {
                            selectCase(newFiltered.first())
                        }
                    },
                    label = { Text(category, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { selectCase(prevCase) },
                modifier = Modifier.weight(0.28f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("◀ Prev", fontSize = 12.sp, maxLines = 1)
            }

            Box(
                modifier = Modifier.weight(0.44f),
                contentAlignment = Alignment.Center
            ) {
                OutlinedButton(
                    onClick = { dropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "C${currentCase.id}: ${currentCase.name} ▼",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    filteredCases.forEach { c ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Case ${c.id}: ${c.name} (${c.group})",
                                    fontSize = 13.sp,
                                    fontWeight = if (c.id == selectedCaseId) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                selectCase(c)
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { selectCase(nextCase) },
                modifier = Modifier.weight(0.28f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("Next ▶", fontSize = 12.sp, maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Case ${currentCase.id}: ${currentCase.name}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = currentCase.group,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentCase.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("CPU", style = MaterialTheme.typography.labelSmall, color = NeonPalette.CyanCpu, fontWeight = FontWeight.Bold)
                        Text(
                            text = currentCase.snapshot.cpu.displayLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("RAM", style = MaterialTheme.typography.labelSmall, color = NeonPalette.OrangeRam, fontWeight = FontWeight.Bold)
                        Text(
                            text = currentCase.snapshot.ram.displayLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NET", style = MaterialTheme.typography.labelSmall, color = NeonPalette.MagentaGpuNet, fontWeight = FontWeight.Bold)
                        Text(
                            text = currentCase.snapshot.network.displayLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("CELLULAR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = currentCase.snapshot.cellularQuality.displayLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Live Parameter Controls",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Dynamic overrides for HUD speed models, curves & sensitivities",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Min Speed (V_min)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(String.format(Locale.US, "%.2f RPS", config.vMinRps), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = config.vMinRps,
            onValueChange = { config = config.copy(vMinRps = (it * 20f).roundToInt() / 20f) },
            valueRange = 0.0f..2.0f,
            steps = 39
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Max Speed (V_max)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(String.format(Locale.US, "%.2f RPS", config.vMaxRps), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = config.vMaxRps,
            onValueChange = { config = config.copy(vMaxRps = (it * 10f).roundToInt() / 10f) },
            valueRange = 2.0f..10.0f,
            steps = 79
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text("Speed Curve Model", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(SpeedCurve.QUADRATIC, SpeedCurve.LINEAR, SpeedCurve.SIGMOID).forEach { curve ->
                FilterChip(
                    selected = config.speedCurve == curve,
                    onClick = { config = config.copy(speedCurve = curve) },
                    label = {
                        Text(
                            text = curve.name,
                            fontSize = 12.sp,
                            fontWeight = if (config.speedCurve == curve) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Outer Sensitivity (CPU)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(String.format(Locale.US, "%.2fx", config.outerSensitivity), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = config.outerSensitivity,
            onValueChange = { config = config.copy(outerSensitivity = (it * 20f).roundToInt() / 20f) },
            valueRange = 0.1f..2.0f,
            steps = 37
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Middle Sensitivity (RAM)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(String.format(Locale.US, "%.2fx", config.middleSensitivity), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = config.middleSensitivity,
            onValueChange = { config = config.copy(middleSensitivity = (it * 20f).roundToInt() / 20f) },
            valueRange = 0.1f..2.0f,
            steps = 37
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Inner Sensitivity (Net)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(String.format(Locale.US, "%.2fx", config.innerSensitivity), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = config.innerSensitivity,
            onValueChange = { config = config.copy(innerSensitivity = (it * 20f).roundToInt() / 20f) },
            valueRange = 0.1f..2.0f,
            steps = 37
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { config = HologramBehaviorConfig() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset Defaults", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SpeedBadge(
    label: String,
    speedRps: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.5.dp, color)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = String.format(Locale.US, "%.2f RPS", speedRps),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}