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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.db.LocalModelEntity
import com.example.domain.model.DeviceMemorySnapshot
import com.example.domain.model.ModelCapabilitySlot
import com.example.domain.model.ModelDownloadProgress
import com.example.engine.ModelDownloadManager
import com.example.ui.components.RamGaugeBar
import com.example.ui.theme.AxionCyan
import com.example.ui.theme.AxionViolet
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ModelManagerScreen(
    memorySnapshot: DeviceMemorySnapshot,
    installedModels: Map<String, LocalModelEntity>,
    downloadProgress: ModelDownloadProgress?,
    statusMessage: String?,
    onRefreshMemory: () -> Unit,
    onImportLocalModel: (ModelCapabilitySlot) -> Unit,
    onDownloadModelUrl: (ModelCapabilitySlot, String) -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteModel: (String) -> Unit,
    onDismissStatusMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var urlDialogSlot by remember { mutableStateOf<ModelCapabilitySlot?>(null) }
    var urlInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(StudioObsidian)
            .testTag("model_manager_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Local AI Model Manager", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Text(
                        "ONNX Runtime & LiteRT (.tflite) • Offline execution after install",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = onRefreshMemory) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh RAM Stats", tint = AxionCyan)
                }
            }
        }

        // Device RAM & Hardware Compatibility Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                border = BorderStroke(1.dp, StudioBorder),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RamGaugeBar(memory = memorySnapshot)
                    Text(
                        text = "Target Profile: Snapdragon 6 Gen 1 (6GB RAM) • Models load sequentially and unload immediately after inference.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        if (statusMessage != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AxionViolet.copy(alpha = 0.16f)),
                    border = BorderStroke(1.dp, AxionViolet),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissStatusMessage) {
                            Text("OK", color = AxionCyan)
                        }
                    }
                }
            }
        }

        // Active Download Progress Card
        if (downloadProgress != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                    border = BorderStroke(1.dp, AxionCyan),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Installing Model (${downloadProgress.slotKey})", style = MaterialTheme.typography.titleSmall, color = AxionCyan)
                        Text(downloadProgress.statusText, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        if (downloadProgress.totalBytes > 0L) {
                            LinearProgressIndicator(
                                progress = { downloadProgress.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = AxionCyan
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = AxionCyan
                            )
                        }
                        OutlinedButton(onClick = onCancelDownload) {
                            Text("Cancel Installation", color = StatusError)
                        }
                    }
                }
            }
        }

        // Honest Model Slot Cards
        items(ModelCapabilitySlot.entries) { slot ->
            val installed = installedModels[slot.slotKey]
            val isInstalled = installed != null && installed.isInstalled
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("model_slot_${slot.slotKey}"),
                colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                border = BorderStroke(1.dp, if (isInstalled) StatusSuccess.copy(alpha = 0.65f) else StudioBorder),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(slot.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(slot.modelTypeLabel, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Surface(
                            color = if (isInstalled) StatusSuccess.copy(alpha = 0.16f) else StatusWarning.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isInstalled) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = if (isInstalled) StatusSuccess else StatusWarning,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isInstalled) "Installed" else "Not Installed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isInstalled) StatusSuccess else StatusWarning
                                )
                            }
                        }
                    }

                    Text(
                        text = "Formats: ${slot.supportedFormats.joinToString(" / ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AxionCyan
                    )
                    Text(
                        text = "Recommended: ${slot.recommendedArtifactHint}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (slot.isCompatibleWith6GbDevice) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = if (slot.isCompatibleWith6GbDevice) StatusSuccess else StatusWarning,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = "6GB RAM Compatibility: ${slot.snapdragon6Gen1Compatibility}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (slot.isCompatibleWith6GbDevice) StatusSuccess else StatusWarning
                            )
                            Text(
                                text = slot.ramRequirementNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    if (installed != null && installed.isInstalled) {
                        Surface(
                            color = StudioObsidian,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = "File: ${installed.displayName} (${installed.modelFormat})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Actual Size: ${ModelDownloadManager.formatBytes(installed.fileSizeBytes)} • SHA256: ${installed.sha256}...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AxionCyan
                                )
                                Text(
                                    text = "Inputs: ${installed.inputSignature}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onImportLocalModel(slot) },
                            colors = ButtonDefaults.buttonColors(containerColor = AxionCyan, contentColor = StudioObsidian),
                            modifier = Modifier.weight(1f).testTag("import_local_${slot.slotKey}")
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Import File")
                        }
                        OutlinedButton(
                            onClick = {
                                urlInput = ""
                                urlDialogSlot = slot
                            },
                            border = BorderStroke(1.dp, AxionViolet),
                            modifier = Modifier.weight(1f).testTag("download_url_${slot.slotKey}")
                        ) {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("From URL")
                        }
                        if (isInstalled) {
                            IconButton(
                                onClick = { onDeleteModel(slot.slotKey) },
                                modifier = Modifier.testTag("delete_model_${slot.slotKey}")
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete Model", tint = StatusError)
                            }
                        }
                    }
                }
            }
        }
    }

    urlDialogSlot?.let { slot ->
        AlertDialog(
            onDismissRequest = { urlDialogSlot = null },
            containerColor = StudioSurfaceElevated,
            title = { Text("Download ${slot.title}", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste a direct HTTPS URL to a compatible .onnx or .tflite model file. The file is verified locally and stored for offline use.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Direct .onnx / .tflite URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val submittedUrl = urlInput.trim()
                        urlDialogSlot = null
                        if (submittedUrl.isNotEmpty()) {
                            onDownloadModelUrl(slot, submittedUrl)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AxionCyan, contentColor = StudioObsidian)
                ) {
                    Text("Download & Verify")
                }
            },
            dismissButton = {
                TextButton(onClick = { urlDialogSlot = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
