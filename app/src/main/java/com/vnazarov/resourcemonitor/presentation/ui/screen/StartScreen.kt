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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.vnazarov.resourcemonitor.core.config.HudSettings
import com.vnazarov.resourcemonitor.core.config.HudSettingsRepository
import com.vnazarov.resourcemonitor.core.designsystem.theme.NeonPalette
import com.vnazarov.resourcemonitor.core.telemetry.fusion.TelemetryFusionEngine
import com.vnazarov.resourcemonitor.core.telemetry.mock.FakeTelemetrySource
import com.vnazarov.resourcemonitor.core.telemetry.mock.SimulationScenario
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StartScreen(
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues.Zero,
    onRegisterChange: () -> Unit = {},
    fakeSource: FakeTelemetrySource = koinInject(),
    fusionEngine: TelemetryFusionEngine = koinInject(),
    plugin: HolographicRingsPlugin = koinInject(),
    settingsRepo: HudSettingsRepository = koinInject()
) {
    val coroutineScope = rememberCoroutineScope()
    val settings by settingsRepo.settingsFlow.collectAsState(initial = HudSettings())
    val snapshot by fusionEngine.snapshot.collectAsState()
    val lastPacket by fusionEngine.lastPacket.collectAsState()

    var selectedCaseId by remember { mutableIntStateOf(1) }
    var selectedCategory by remember { mutableStateOf("All") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val config = remember(settings) {
        HologramBehaviorConfig(
            vMinRps = settings.vMinRps,
            vMaxRps = settings.vMaxRps,
            speedCurve = try {
                SpeedCurve.valueOf(settings.speedCurve.uppercase())
            } catch (_: Exception) {
                SpeedCurve.QUADRATIC
            },
            outerSensitivity = settings.outerSensitivity,
            middleSensitivity = settings.middleSensitivity,
            innerSensitivity = settings.innerSensitivity,
            meltdownThreshold = settings.meltdownThreshold
        )
    }

    val currentParams = remember(snapshot, config) {
        HologramProjectionCalculator.computeParameters(snapshot, config)
    }

    val selectCase: (HologramInvariantCase) -> Unit = { case ->
        selectedCaseId = case.id
        fusionEngine.injectPacket(case.toRawPacket(), resetEma = true)
        fusionEngine.injectSnapshot(case.snapshot)
        when (case.id) {
            2 -> fakeSource.setScenario(SimulationScenario.IdleCalm)
            12, 14 -> fakeSource.setScenario(SimulationScenario.PeakGaming)
            7, 20, 24, 25 -> fakeSource.setScenario(SimulationScenario.ThermalMeltdown)
            21, 22, 23 -> fakeSource.setScenario(SimulationScenario.CellularDrop)
            else -> {}
        }
    }

    LaunchedEffect(Unit) {
        val initialCase = HologramInvariantCases.EXPANDED_CASES.find { it.id == selectedCaseId }
            ?: HologramInvariantCases.ALL_CASES.first()
        selectCase(initialCase)
    }

    val allCases = if (HologramInvariantCases.EXPANDED_CASES.isNotEmpty()) {
        HologramInvariantCases.EXPANDED_CASES
    } else {
        HologramInvariantCases.ALL_CASES
    }

    val filteredCases = remember(selectedCategory) {
        when {
            selectedCategory.startsWith("All") -> allCases
            selectedCategory == "Baseline" -> allCases.filter { it.group.startsWith("Baseline") }
            selectedCategory == "Single-Metric" -> allCases.filter { it.group.startsWith("Single-Metric") }
            selectedCategory == "Dual-Metric" -> allCases.filter { it.group.startsWith("Dual-Metric") }
            selectedCategory == "Balanced" -> allCases.filter { it.group.startsWith("Balanced") }
            selectedCategory == "Cellular" -> allCases.filter { it.group.startsWith("Cellular") }
            selectedCategory == "Thermal" -> allCases.filter { it.group.startsWith("Thermal") }
            selectedCategory == "Robustness" -> allCases.filter { it.group.startsWith("Mathematical") || it.group.contains("Robustness") }
            else -> allCases
        }
    }

    val currentCase = remember(selectedCaseId) {
        allCases.find { it.id == selectedCaseId } ?: allCases.first()
    }

    val currentIndex = filteredCases.indexOfFirst { it.id == selectedCaseId }
    val prevCase = if (currentIndex > 0) {
        filteredCases[currentIndex - 1]
    } else {
        filteredCases.lastOrNull() ?: allCases.first()
    }
    val nextCase = if (currentIndex >= 0 && currentIndex < filteredCases.size - 1) {
        filteredCases[currentIndex + 1]
    } else {
        filteredCases.firstOrNull() ?: allCases.first()
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

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Overlay & Telemetry Source Switch
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Camera-Hole HUD Overlay",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (settings.isOverlayEnabled) "Status: ACTIVE (Cutout Penetration)" else "Status: INACTIVE",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (settings.isOverlayEnabled) NeonPalette.EmeraldGpu else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            val newEnabled = !settings.isOverlayEnabled
                            coroutineScope.launch {
                                settingsRepo.updateOverlayEnabled(newEnabled)
                            }
                            onRegisterChange()
                        }
                    ) {
                        Text(if (settings.isOverlayEnabled) "Turn OFF" else "Turn ON", fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Telemetry Source Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (settings.telemetrySourceMode == "REAL") "Real sysfs non-root collectors" else "Deterministic synthetic scenario",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilterChip(
                        selected = settings.telemetrySourceMode == "REAL",
                        onClick = {
                            val nextMode = if (settings.telemetrySourceMode == "REAL") "MOCK" else "REAL"
                            coroutineScope.launch {
                                settingsRepo.updateTelemetrySourceMode(nextMode)
                            }
                        },
                        label = {
                            Text(
                                text = if (settings.telemetrySourceMode == "REAL") "REAL [SYSFS]" else "MOCK [SYNTH]",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Halo Sizing Slider
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Camera Cutout Halo Sizing",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${settings.haloDiameterDp.roundToInt()} dp (R0 = ${(settings.haloDiameterDp / 2f).roundToInt()} dp)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = NeonPalette.CyanCpu
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Adjust concentric halo outer diameter to fit front punch-hole aperture (96dp - 136dp)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = settings.haloDiameterDp.coerceIn(96f, 136f),
                    onValueChange = {
                        val snapped = (it * 2f).roundToInt() / 2f
                        coroutineScope.launch {
                            settingsRepo.updateHaloDiameter(snapped)
                        }
                    },
                    valueRange = 96f..136f,
                    steps = 79
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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

        Spacer(modifier = Modifier.height(12.dp))

        // Status & Energy Banners
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

        Spacer(modifier = Modifier.height(18.dp))

        // 4. Live 5-Ring HUD Telemetry Badges
        Text(
            text = "Live 5-Ring HUD Telemetry Badges",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Real-time metrics, rotational speeds (RPS), and alert states across all 5 gyroscopic orbits",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Ring 1 (CPU): Load %, Freq, RPS
            val cpuFreqStr = lastPacket?.cpuFrequenciesKhz?.firstOrNull()?.let { "${it / 1000} MHz" } ?: "3.2 GHz"
            RingBadge(
                ringName = "R1: CPU Governor",
                primaryMetric = "Load: ${snapshot.cpu.displayLabel.ifEmpty { "${(snapshot.cpu.smoothedValue * 100).toInt()}%" }}",
                secondaryMetric = "Freq: $cpuFreqStr",
                speedRps = currentParams.ring1SpeedRps,
                color = currentParams.ring1Color,
                modifier = Modifier.weight(1f)
            )

            // Ring 2 (RAM): Available MB, zRAM Swap %, RPS
            val availMb = lastPacket?.let { "${it.ramAvailableBytes / (1024 * 1024)} MB" }
                ?: "${((1f - snapshot.ram.smoothedValue) * 16000).toInt()} MB"
            val zramStr = if (currentParams.isMemoryThrashAlert) "zRAM 92% [THRASH]" else "zRAM 12%"
            RingBadge(
                ringName = "R2: RAM & zRAM",
                primaryMetric = "Avail: $availMb",
                secondaryMetric = zramStr,
                speedRps = currentParams.ring2SpeedRps,
                color = currentParams.ring2Color,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Ring 3 (Net): KB/s Throughput, Link Quality, RPS
            val throughput = snapshot.network.displayLabel.ifEmpty { "0 KB/s" }
            val linkQuality = snapshot.cellularQuality.displayLabel.ifEmpty { "-80 dBm" }
            RingBadge(
                ringName = "R3: Network / RF",
                primaryMetric = "Rate: $throughput",
                secondaryMetric = "Link: $linkQuality",
                speedRps = currentParams.ring3SpeedRps,
                color = currentParams.ring3Color,
                modifier = Modifier.weight(1f)
            )

            // Ring 4 (SSD): Write Latency ms, Stall Flag, RPS
            val latencyStr = if (currentParams.isStorageStallAlert) ">150 ms [STALL]" else "1.2 ms"
            val stallStr = if (currentParams.isStorageStallAlert) "STALL: TRUE" else "STALL: FALSE"
            RingBadge(
                ringName = "R4: Storage SSD",
                primaryMetric = "Sync: $latencyStr",
                secondaryMetric = stallStr,
                speedRps = currentParams.ring4SpeedRps,
                color = currentParams.ring4Color,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Ring 5 (GPU/Thermal): Thermal Status, Temperature °C, RPS, Meltdown Flag
        val thermalStatusStr = lastPacket?.thermalStatusLevel?.let { "Status: Level $it" }
            ?: if (currentParams.isMeltdownAlert) "Status: CRITICAL" else "Status: NOMINAL"
        val tempStr = lastPacket?.cpuTemperatureMilliC?.let { "${it / 1000}°C" }
            ?: if (currentParams.isMeltdownAlert) "85°C [MELTDOWN]" else "42°C [NOMINAL]"
        RingBadge(
            ringName = "R5: GPU & Thermal Corona",
            primaryMetric = thermalStatusStr,
            secondaryMetric = "Temp: $tempStr",
            speedRps = currentParams.ring5SpeedRps,
            color = currentParams.ring5Color,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 5. 30-Case Invariant Inspector
        Text(
            text = "30-Case Invariant Inspector",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Select invariant permutations to test rotational speeds & alert states live in HUD",
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
                            category.startsWith("All") -> allCases
                            category == "Baseline" -> allCases.filter { it.group.startsWith("Baseline") }
                            category == "Single-Metric" -> allCases.filter { it.group.startsWith("Single-Metric") }
                            category == "Dual-Metric" -> allCases.filter { it.group.startsWith("Dual-Metric") }
                            category == "Balanced" -> allCases.filter { it.group.startsWith("Balanced") }
                            category == "Cellular" -> allCases.filter { it.group.startsWith("Cellular") }
                            category == "Thermal" -> allCases.filter { it.group.startsWith("Thermal") }
                            category == "Robustness" -> allCases.filter { it.group.startsWith("Mathematical") || it.group.contains("Robustness") }
                            else -> allCases
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
                        Text("SSD", style = MaterialTheme.typography.labelSmall, color = NeonPalette.IceBlueStorage, fontWeight = FontWeight.Bold)
                        Text(
                            text = currentCase.snapshot.storageIo.displayLabel.ifEmpty { "0%" },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("GPU", style = MaterialTheme.typography.labelSmall, color = NeonPalette.EmeraldGpu, fontWeight = FontWeight.Bold)
                        Text(
                            text = currentCase.snapshot.gpu.displayLabel.ifEmpty { "0%" },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Speed & Sensitivity Tuning
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

        // V_min slider (0.1 .. 1.0 RPS)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Min Speed (V_min)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(String.format(Locale.US, "%.2f RPS", settings.vMinRps), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = settings.vMinRps.coerceIn(0.1f, 1.0f),
            onValueChange = {
                val rounded = (it * 20f).roundToInt() / 20f
                coroutineScope.launch {
                    settingsRepo.updateSpeedRange(vMin = rounded, vMax = settings.vMaxRps)
                }
            },
            valueRange = 0.1f..1.0f,
            steps = 17
        )

        // V_max slider (2.0 .. 8.0 RPS)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Max Speed (V_max)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(String.format(Locale.US, "%.2f RPS", settings.vMaxRps), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = settings.vMaxRps.coerceIn(2.0f, 8.0f),
            onValueChange = {
                val rounded = (it * 10f).roundToInt() / 10f
                coroutineScope.launch {
                    settingsRepo.updateSpeedRange(vMin = settings.vMinRps, vMax = rounded)
                }
            },
            valueRange = 2.0f..8.0f,
            steps = 59
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
                    selected = settings.speedCurve.equals(curve.name, ignoreCase = true),
                    onClick = {
                        coroutineScope.launch {
                            settingsRepo.updateSpeedCurve(curve.name)
                        }
                    },
                    label = {
                        Text(
                            text = curve.name,
                            fontSize = 12.sp,
                            fontWeight = if (settings.speedCurve.equals(curve.name, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Outer Sensitivity (CPU)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Outer Sensitivity (CPU)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(String.format(Locale.US, "%.2fx", settings.outerSensitivity), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = settings.outerSensitivity.coerceIn(0.1f, 2.0f),
            onValueChange = {
                val rounded = (it * 20f).roundToInt() / 20f
                coroutineScope.launch {
                    settingsRepo.updateSensitivities(
                        outer = rounded,
                        middle = settings.middleSensitivity,
                        inner = settings.innerSensitivity
                    )
                }
            },
            valueRange = 0.1f..2.0f,
            steps = 37
        )

        // Middle Sensitivity (RAM/Net)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Middle Sensitivity (RAM)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(String.format(Locale.US, "%.2fx", settings.middleSensitivity), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = settings.middleSensitivity.coerceIn(0.1f, 2.0f),
            onValueChange = {
                val rounded = (it * 20f).roundToInt() / 20f
                coroutineScope.launch {
                    settingsRepo.updateSensitivities(
                        outer = settings.outerSensitivity,
                        middle = rounded,
                        inner = settings.innerSensitivity
                    )
                }
            },
            valueRange = 0.1f..2.0f,
            steps = 37
        )

        // Inner Sensitivity (GPU/Thermal)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Inner Sensitivity (Net/GPU)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(String.format(Locale.US, "%.2fx", settings.innerSensitivity), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = settings.innerSensitivity.coerceIn(0.1f, 2.0f),
            onValueChange = {
                val rounded = (it * 20f).roundToInt() / 20f
                coroutineScope.launch {
                    settingsRepo.updateSensitivities(
                        outer = settings.outerSensitivity,
                        middle = settings.middleSensitivity,
                        inner = rounded
                    )
                }
            },
            valueRange = 0.1f..2.0f,
            steps = 37
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                coroutineScope.launch {
                    settingsRepo.resetDefaults()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset Defaults", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RingBadge(
    ringName: String,
    primaryMetric: String,
    secondaryMetric: String,
    speedRps: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.5.dp, color)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ringName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = String.format(Locale.US, "%.2f RPS", speedRps),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = primaryMetric,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = secondaryMetric,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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