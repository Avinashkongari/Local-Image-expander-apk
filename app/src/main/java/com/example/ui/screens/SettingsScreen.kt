package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.prefs.StudioSettings
import com.example.domain.model.ExportFormat
import com.example.domain.model.OutpaintPreset
import com.example.ui.theme.AxionCyan
import com.example.ui.theme.AxionViolet
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    settings: StudioSettings,
    onUpdatePreset: (OutpaintPreset) -> Unit,
    onUpdateExportFormat: (ExportFormat) -> Unit,
    onUpdateMaxDimension: (Int) -> Unit,
    onUpdateAutoUnload: (Boolean) -> Unit,
    onUpdateThreads: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(StudioObsidian),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Studio Settings", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Text(
                "Tuned for Snapdragon 6 Gen 1 • 6GB RAM • Offline Processing",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        item {
            SettingsCard(title = "Default 16:9 Outpaint Resolution") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutpaintPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = settings.defaultOutpaintPreset == preset,
                            onClick = { onUpdatePreset(preset) },
                            label = { Text(if (preset == OutpaintPreset.HD_720P) "1280 × 720" else "1920 × 1080") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AxionCyan,
                                selectedLabelColor = StudioObsidian
                            )
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(title = "Default Export Format") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = settings.defaultExportFormat == fmt,
                            onClick = { onUpdateExportFormat(fmt) },
                            label = { Text(fmt.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AxionCyan,
                                selectedLabelColor = StudioObsidian
                            )
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(title = "Memory Protection (6GB RAM Safe Ceiling)") {
                Text(
                    "Max working dimension when decoding huge Manga/Manhua strips:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1280, 1920, 2560).forEach { dim ->
                        FilterChip(
                            selected = settings.maxWorkingDimension == dim,
                            onClick = { onUpdateMaxDimension(dim) },
                            label = { Text("${dim}px") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AxionViolet.copy(alpha = 0.35f),
                                selectedLabelColor = TextPrimary
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Unload Model After Inference", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(
                            "Frees ONNX/LiteRT native buffers immediately after each edit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = settings.autoUnloadModelAfterInference,
                        onCheckedChange = onUpdateAutoUnload
                    )
                }
            }
        }

        item {
            SettingsCard(title = "CPU Inference Threads") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 4, 6).forEach { t ->
                        FilterChip(
                            selected = settings.inferenceThreads == t,
                            onClick = { onUpdateThreads(t) },
                            label = { Text("$t Threads") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AxionCyan,
                                selectedLabelColor = StudioObsidian
                            )
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(title = "Offline & Privacy Architecture") {
                Text(
                    "• Zero cloud API or Gemini API key required.\n• Photo Picker & SAF Export require zero broad storage permissions.\n• Downloaded/imported .onnx and .tflite models execute 100% offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusSuccess
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
        border = BorderStroke(1.dp, StudioBorder),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = AxionCyan)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}
