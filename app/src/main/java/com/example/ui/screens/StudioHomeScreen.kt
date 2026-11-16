package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.db.LocalModelEntity
import com.example.domain.model.DeviceMemorySnapshot
import com.example.domain.model.EditorUiState
import com.example.domain.model.EditorWorkflowMode
import com.example.ui.components.MemoryBadgeChip
import com.example.ui.theme.AxionCyan
import com.example.ui.theme.AxionMagenta
import com.example.ui.theme.AxionViolet
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private data class StudioActionItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val badge: String,
    val testTag: String,
    val onClick: () -> Unit
)

@Composable
fun StudioHomeScreen(
    editorState: EditorUiState,
    memorySnapshot: DeviceMemorySnapshot,
    installedModels: Map<String, LocalModelEntity>,
    historyCount: Int,
    onImportImage: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenEditorMode: (EditorWorkflowMode) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenModelManager: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasActiveImage = editorState.editedBitmap != null

    val actions = listOf(
        StudioActionItem(
            title = "Import Image",
            subtitle = "Manga, Manhua, Anime & Screenshots",
            icon = Icons.Rounded.AddPhotoAlternate,
            accent = AxionCyan,
            badge = "Gallery",
            testTag = "home_import_image",
            onClick = onImportImage
        ),
        StudioActionItem(
            title = "AI Image Analysis",
            subtitle = "OCR, Speech Bubbles, Colors & Lighting",
            icon = Icons.Rounded.Insights,
            accent = AxionViolet,
            badge = if (editorState.analysisResult != null) "Ready" else "Offline CV",
            testTag = "home_ai_analysis",
            onClick = {
                if (hasActiveImage) onOpenAnalysis() else onImportImage()
            }
        ),
        StudioActionItem(
            title = "Remove Text",
            subtitle = "Speech Bubbles, SFX & Brush Mask",
            icon = Icons.Rounded.TextFields,
            accent = AxionMagenta,
            badge = if (installedModels.containsKey("lama_inpainting")) "LaMa Ready" else "Model Needed",
            testTag = "home_remove_text",
            onClick = {
                if (hasActiveImage) onOpenEditorMode(EditorWorkflowMode.TEXT_REMOVAL) else onImportImage()
            }
        ),
        StudioActionItem(
            title = "Remove Background",
            subtitle = "Subject Matte & Transparent PNG",
            icon = Icons.Rounded.ContentCut,
            accent = StatusSuccess,
            badge = if (installedModels.containsKey("foreground_segmentation")) "Local Model" else "ML Kit",
            testTag = "home_remove_bg",
            onClick = {
                if (hasActiveImage) onOpenEditorMode(EditorWorkflowMode.BACKGROUND_REMOVAL) else onImportImage()
            }
        ),
        StudioActionItem(
            title = "AI Outpainting",
            subtitle = "Smart 16:9 Canvas (720p / 1080p)",
            icon = Icons.Rounded.AspectRatio,
            accent = AxionCyan,
            badge = "16:9 Expand",
            testTag = "home_ai_outpainting",
            onClick = {
                if (hasActiveImage) onOpenEditorMode(EditorWorkflowMode.OUTPAINT_16_9) else onImportImage()
            }
        ),
        StudioActionItem(
            title = "Image History",
            subtitle = "Reopen & Export Saved Panels",
            icon = Icons.Rounded.History,
            accent = AxionViolet,
            badge = "$historyCount Saved",
            testTag = "home_image_history",
            onClick = onOpenHistory
        ),
        StudioActionItem(
            title = "Model Manager",
            subtitle = "ONNX & LiteRT Local Weights",
            icon = Icons.Rounded.Hub,
            accent = StatusWarning,
            badge = "${installedModels.size} Installed",
            testTag = "home_model_manager",
            onClick = onOpenModelManager
        ),
        StudioActionItem(
            title = "Settings",
            subtitle = "6GB RAM Tiling, Export & Threads",
            icon = Icons.Rounded.Settings,
            accent = TextSecondary,
            badge = "Studio",
            testTag = "home_settings",
            onClick = onOpenSettings
        )
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(StudioObsidian),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AXION",
                        style = MaterialTheme.typography.headlineMedium,
                        color = AxionCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "IMAGE EXPANDER STUDIO",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
                MemoryBadgeChip(memory = memorySnapshot)
            }
        }

        // Studio Hero Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, StudioBorder),
                colors = CardDefaults.cardColors(containerColor = StudioSurface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_studio_1794820681616),
                        contentDescription = "Axion 16:9 Studio Backdrop",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        StudioObsidian.copy(alpha = 0.25f),
                                        StudioObsidian.copy(alpha = 0.94f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = StudioObsidian.copy(alpha = 0.78f),
                            border = BorderStroke(1.dp, AxionCyan.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoFixHigh,
                                    contentDescription = null,
                                    tint = AxionCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Creator 16:9 Workspace • Offline Engine",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (hasActiveImage) "Active: ${editorState.projectTitle}" else "Expand Panels to 16:9",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (hasActiveImage) {
                                        val bmp = editorState.editedBitmap!!
                                        "${bmp.width}×${bmp.height} • Ready for text removal, cutout & outpainting"
                                    } else {
                                        "Zero subject stretch • Speech bubble cleanup • Local ONNX & LiteRT"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = if (hasActiveImage) {
                                    { onOpenEditorMode(EditorWorkflowMode.OUTPAINT_16_9) }
                                } else {
                                    onImportImage
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AxionCyan,
                                    contentColor = StudioObsidian
                                ),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.testTag("hero_primary_cta")
                            ) {
                                Text(
                                    text = if (hasActiveImage) "Open Editor" else "Import",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active Workspace Thumbnail Strip (if an image is loaded)
        val activeBmp = editorState.editedBitmap
        if (activeBmp != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenEditorMode(editorState.workflowMode) },
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                    border = BorderStroke(1.dp, AxionViolet.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            bitmap = activeBmp.asImageBitmap(),
                            contentDescription = "Active canvas preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Resume Current Canvas",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "${activeBmp.width}×${activeBmp.height} px • ${editorState.workflowMode.label}",
                                style = MaterialTheme.typography.labelMedium,
                                color = AxionCyan
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "CREATOR WORKFLOW TOOLS",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }

        // Responsive 2-col or 4-col grid of the 8 core tools
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 680.dp) 4 else 2
                val rows = actions.chunked(columns)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { item ->
                                StudioToolTile(
                                    item = item,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(columns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StudioToolTile(
    item: StudioActionItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(136.dp)
            .testTag(item.testTag)
            .clickable(onClick = item.onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(item.accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = item.accent,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = item.accent.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = item.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = item.accent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Column {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2
                )
            }
        }
    }
}
