package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.BannerActionType
import com.example.domain.model.DeviceMemorySnapshot
import com.example.domain.model.ProcessingStatus
import com.example.domain.model.StatusBanner
import com.example.ui.theme.AxionCyan
import com.example.ui.theme.AxionViolet
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorContainer
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun MemoryBadgeChip(
    memory: DeviceMemorySnapshot,
    modifier: Modifier = Modifier
) {
    val tint = if (memory.isLowMemory || memory.availableRamMb < 700L) StatusWarning else AxionCyan
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = StudioSurfaceElevated,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Memory,
                contentDescription = "Available RAM",
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "${memory.availableRamMb}MB Free",
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun StatusBannerCard(
    banner: StatusBanner,
    onAction: (BannerActionType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = when {
        banner.isError -> StatusErrorContainer
        banner.isWarning -> StatusWarningContainer
        else -> StudioSurfaceElevated
    }
    val borderColor = when {
        banner.isError -> StatusError
        banner.isWarning -> StatusWarning
        else -> AxionCyan.copy(alpha = 0.6f)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (banner.isWarning || banner.isError) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = borderColor,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(StatusSuccess)
                        )
                    }
                    Text(
                        text = banner.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss notification",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = banner.message,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            val actionLabel = banner.actionLabel
            val actionType = banner.actionType
            if (actionLabel != null && actionType != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onAction(actionType) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = borderColor,
                            contentColor = StudioObsidian
                        ),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.testTag("banner_primary_action")
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    if (actionType == BannerActionType.OPEN_MODEL_MANAGER && banner.title.contains("LaMa", ignoreCase = true)) {
                        OutlinedButton(
                            onClick = { onAction(BannerActionType.RUN_STRUCTURAL_INPAINT_FALLBACK) },
                            border = BorderStroke(1.dp, StudioBorder),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.testTag("banner_fallback_inpaint")
                        ) {
                            Text("Non-AI Structural Fill", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (actionType == BannerActionType.OPEN_MODEL_MANAGER && banner.title.contains("Outpainting", ignoreCase = true)) {
                        OutlinedButton(
                            onClick = { onAction(BannerActionType.RUN_EDGE_EXTEND_PREVIEW) },
                            border = BorderStroke(1.dp, StudioBorder),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.testTag("banner_fallback_outpaint")
                        ) {
                            Text("Non-AI Edge Preview", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProcessingOverlay(
    status: ProcessingStatus,
    onCancel: () -> Unit
) {
    AnimatedVisibility(visible = status.isRunning) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                border = BorderStroke(1.dp, AxionCyan.copy(alpha = 0.5f)),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = status.stageTitle.ifBlank { "Processing..." },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = status.detailMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { status.progress.coerceIn(0.05f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = AxionCyan,
                        trackColor = StudioObsidian
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(status.progress * 100).toInt().coerceIn(0, 100)}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = AxionCyan
                    )
                    if (status.canCancel) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = onCancel,
                            border = BorderStroke(1.dp, StatusError.copy(alpha = 0.6f)),
                            modifier = Modifier.testTag("cancel_processing_button")
                        ) {
                            Text("Cancel Task", color = StatusError)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RamGaugeBar(
    memory: DeviceMemorySnapshot,
    modifier: Modifier = Modifier
) {
    val usedMb = (memory.totalRamMb - memory.availableRamMb).coerceAtLeast(0L)
    val usedFrac = if (memory.totalRamMb > 0L) (usedMb.toFloat() / memory.totalRamMb.toFloat()).coerceIn(0f, 1f) else 0f
    val barColor = when {
        usedFrac > 0.85f -> StatusError
        usedFrac > 0.72f -> StatusWarning
        else -> AxionViolet
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "System RAM Usage",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Text(
                text = "${memory.availableRamMb} MB free / ${memory.totalRamMb} MB total",
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { usedFrac },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(6.dp)),
            color = barColor,
            trackColor = StudioObsidian
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "App JVM Heap Ceiling: ${memory.maxAppHeapMb} MB • Auto-unload & 512px tiling recommended for 6GB devices",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}
