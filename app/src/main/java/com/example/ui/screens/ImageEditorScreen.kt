package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.domain.model.BackgroundReplacementMode
import com.example.domain.model.BannerActionType
import com.example.domain.model.BrushToolMode
import com.example.domain.model.EditorUiState
import com.example.domain.model.EditorWorkflowMode
import com.example.domain.model.ExportFormat
import com.example.domain.model.OutpaintPreset
import com.example.domain.model.StrokePoint
import com.example.domain.model.SubjectAnchor
import com.example.ui.components.StatusBannerCard
import com.example.ui.theme.AxionCyan
import com.example.ui.theme.AxionMagenta
import com.example.ui.theme.AxionViolet
import com.example.ui.theme.CheckerDark
import com.example.ui.theme.CheckerLight
import com.example.ui.theme.MaskOverlayCyan
import com.example.ui.theme.MaskOverlayRed
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ImageEditorScreen(
    state: EditorUiState,
    onImportImage: () -> Unit,
    onSetWorkflowMode: (EditorWorkflowMode) -> Unit,
    onSetToolMode: (BrushToolMode) -> Unit,
    onSetBrushRadius: (Float) -> Unit,
    onApplyStroke: (List<StrokePoint>, Float, Boolean) -> Unit,
    onClearMask: () -> Unit,
    onToggleMaskOverlay: () -> Unit,
    onSetShowOriginal: (Boolean) -> Unit,
    onToggleCompareSplit: () -> Unit,
    onSetCompareSplitFraction: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    onAutoMaskText: (Boolean) -> Unit,
    onRunAiTextRemoval: () -> Unit,
    onRunBackgroundRemoval: () -> Unit,
    onApplyManualForegroundMask: () -> Unit,
    onSetBgReplacementMode: (BackgroundReplacementMode) -> Unit,
    onSetOutpaintPreset: (OutpaintPreset) -> Unit,
    onSetSubjectAnchor: (SubjectAnchor) -> Unit,
    onSetOutpaintPrompt: (String) -> Unit,
    onRunOutpainting: () -> Unit,
    onSaveHistory: () -> Unit,
    onExportImage: (ExportFormat) -> Unit,
    onBannerAction: (BannerActionType) -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeBitmap = if (state.showOriginalPreview) state.originalBitmap else state.editedBitmap
    var showExportMenu by remember { mutableStateOf(false) }

    if (activeBitmap == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(StudioObsidian)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                border = BorderStroke(1.dp, StudioBorder)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = null,
                        tint = AxionCyan,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Editor Canvas Empty", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Import any Manga, Manhua, Anime, or video frame to brush masks, remove text/backgrounds, and expand to 16:9.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onImportImage,
                        colors = ButtonDefaults.buttonColors(containerColor = AxionCyan, contentColor = StudioObsidian),
                        modifier = Modifier.testTag("editor_empty_import")
                    ) {
                        Text("Import Image")
                    }
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StudioObsidian)
    ) {
        // Top Action Bar
        Surface(
            color = StudioSurface,
            border = BorderStroke(1.dp, StudioBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onImportImage,
                    modifier = Modifier.testTag("editor_import_btn")
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = "Import Image", tint = AxionCyan)
                }
                IconButton(
                    onClick = onUndo,
                    enabled = state.canUndo,
                    modifier = Modifier.testTag("editor_undo_btn")
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = "Undo",
                        tint = if (state.canUndo) TextPrimary else TextSecondary.copy(alpha = 0.35f)
                    )
                }
                IconButton(
                    onClick = onRedo,
                    enabled = state.canRedo,
                    modifier = Modifier.testTag("editor_redo_btn")
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Redo,
                        contentDescription = "Redo",
                        tint = if (state.canRedo) TextPrimary else TextSecondary.copy(alpha = 0.35f)
                    )
                }
                IconButton(
                    onClick = onToggleCompareSplit,
                    modifier = Modifier.testTag("editor_compare_btn")
                ) {
                    Icon(
                        Icons.Rounded.Compare,
                        contentDescription = "Compare Before/After",
                        tint = if (state.compareSplitMode) AxionCyan else TextPrimary
                    )
                }
                FilterChip(
                    selected = state.showOriginalPreview,
                    onClick = { onSetShowOriginal(!state.showOriginalPreview) },
                    label = { Text(if (state.showOriginalPreview) "Original" else "Edited") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AxionViolet.copy(alpha = 0.3f),
                        selectedLabelColor = TextPrimary
                    ),
                    modifier = Modifier.testTag("editor_original_toggle")
                )
                IconButton(
                    onClick = onToggleMaskOverlay,
                    modifier = Modifier.testTag("editor_mask_visibility_btn")
                ) {
                    Icon(
                        if (state.showMaskOverlay) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = "Toggle Mask Preview",
                        tint = if (state.showMaskOverlay) AxionMagenta else TextSecondary
                    )
                }
                IconButton(onClick = onReset, modifier = Modifier.testTag("editor_reset_btn")) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset to Original", tint = TextSecondary)
                }
                IconButton(onClick = onSaveHistory, modifier = Modifier.testTag("editor_save_history_btn")) {
                    Icon(Icons.Rounded.BookmarkAdd, contentDescription = "Save to History", tint = StatusSuccess)
                }
                Box {
                    Button(
                        onClick = { showExportMenu = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AxionCyan, contentColor = StudioObsidian),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.testTag("editor_export_btn")
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export", style = MaterialTheme.typography.labelLarge)
                    }
                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false }
                    ) {
                        ExportFormat.entries.forEach { fmt ->
                            DropdownMenuItem(
                                text = { Text("Export ${fmt.label}") },
                                onClick = {
                                    showExportMenu = false
                                    onExportImage(fmt)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Status Banner (Honest Model/Processing/Error Feedback)
        state.statusBanner?.let { banner ->
            StatusBannerCard(
                banner = banner,
                onAction = onBannerAction,
                onDismiss = onDismissBanner,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Interactive Canvas Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF06090F))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (state.workflowMode == EditorWorkflowMode.OUTPAINT_16_9 &&
                (activeBitmap.width.toFloat() / activeBitmap.height.coerceAtLeast(1)) < 1.68f
            ) {
                Outpaint16By9LivePreviewCanvas(
                    bitmap = activeBitmap,
                    preset = state.outpaintPreset,
                    anchor = state.subjectAnchor
                )
            } else {
                InteractiveMaskEditorCanvas(
                    originalBitmap = state.originalBitmap ?: activeBitmap,
                    editedBitmap = state.editedBitmap ?: activeBitmap,
                    maskBitmap = state.maskBitmap,
                    showOriginalOnly = state.showOriginalPreview,
                    showMaskOverlay = state.showMaskOverlay,
                    compareSplitMode = state.compareSplitMode,
                    splitFraction = state.compareSplitFraction,
                    toolMode = state.toolMode,
                    brushRadiusPx = state.brushRadiusPx,
                    onSplitFractionChange = onSetCompareSplitFraction,
                    onStrokeComplete = onApplyStroke
                )
            }

            // Dimensions Pill Overlay
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
                color = StudioObsidian.copy(alpha = 0.8f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, StudioBorder)
            ) {
                Text(
                    text = "${activeBitmap.width}×${activeBitmap.height} px",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Workflow Mode Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EditorWorkflowMode.entries.forEach { mode ->
                val selected = state.workflowMode == mode
                FilterChip(
                    selected = selected,
                    onClick = { onSetWorkflowMode(mode) },
                    label = { Text(mode.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AxionCyan,
                        selectedLabelColor = StudioObsidian
                    ),
                    modifier = Modifier.testTag("workflow_tab_${mode.name}")
                )
            }
        }

        // Context-Sensitive Tool Controls Dock
        Surface(
            color = StudioSurfaceElevated,
            border = BorderStroke(1.dp, StudioBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (state.workflowMode) {
                    EditorWorkflowMode.TEXT_REMOVAL -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ToolPillButton(
                                selected = state.toolMode == BrushToolMode.PAINT_MASK,
                                icon = Icons.Rounded.Brush,
                                label = "Brush",
                                onClick = { onSetToolMode(BrushToolMode.PAINT_MASK) },
                                modifier = Modifier.weight(1f).testTag("tool_brush_btn")
                            )
                            ToolPillButton(
                                selected = state.toolMode == BrushToolMode.ERASE_MASK,
                                icon = Icons.Rounded.CleaningServices,
                                label = "Eraser",
                                onClick = { onSetToolMode(BrushToolMode.ERASE_MASK) },
                                modifier = Modifier.weight(1f).testTag("tool_eraser_btn")
                            )
                            ToolPillButton(
                                selected = state.toolMode == BrushToolMode.VIEW_PAN,
                                icon = Icons.Rounded.PanTool,
                                label = "Inspect",
                                onClick = { onSetToolMode(BrushToolMode.VIEW_PAN) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Brush: ${state.brushRadiusPx.roundToInt()}px",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                modifier = Modifier.width(90.dp)
                            )
                            Slider(
                                value = state.brushRadiusPx,
                                onValueChange = onSetBrushRadius,
                                valueRange = 8f..88f,
                                colors = SliderDefaults.colors(thumbColor = AxionCyan, activeTrackColor = AxionCyan),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = onClearMask, contentPadding = PaddingValues(horizontal = 10.dp)) {
                                Text("Clear", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onAutoMaskText(true) },
                                border = BorderStroke(1.dp, AxionViolet),
                                modifier = Modifier.weight(1f).testTag("auto_mask_text_btn")
                            ) {
                                Icon(Icons.Rounded.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Auto-Mask Bubbles")
                            }
                            Button(
                                onClick = onRunAiTextRemoval,
                                colors = ButtonDefaults.buttonColors(containerColor = AxionMagenta, contentColor = StudioObsidian),
                                modifier = Modifier.weight(1f).testTag("run_ai_remove_btn")
                            ) {
                                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("AI Remove")
                            }
                        }
                    }

                    EditorWorkflowMode.BACKGROUND_REMOVAL -> {
                        Text(
                            "Background Replacement & Transparent PNG Export",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BackgroundReplacementMode.entries
                                .filter { it != BackgroundReplacementMode.CUSTOM_COLOR }
                                .forEach { bgMode ->
                                    FilterChip(
                                        selected = state.bgReplacementMode == bgMode,
                                        onClick = { onSetBgReplacementMode(bgMode) },
                                        label = { Text(bgMode.label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = StatusSuccess.copy(alpha = 0.28f),
                                            selectedLabelColor = TextPrimary
                                        )
                                    )
                                }
                        }
                        Button(
                            onClick = onRunBackgroundRemoval,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess, contentColor = StudioObsidian),
                            modifier = Modifier.fillMaxWidth().testTag("run_bg_remove_btn")
                        ) {
                            Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Remove Background (Local Segmentation)")
                        }
                    }

                    EditorWorkflowMode.OUTPAINT_16_9 -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutpaintPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = state.outpaintPreset == preset,
                                    onClick = { onSetOutpaintPreset(preset) },
                                    label = { Text(if (preset == OutpaintPreset.HD_720P) "1280×720" else "1920×1080") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AxionCyan,
                                        selectedLabelColor = StudioObsidian
                                    ),
                                    modifier = Modifier.testTag("preset_${preset.name}")
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            SubjectAnchor.entries.forEach { anchor ->
                                FilterChip(
                                    selected = state.subjectAnchor == anchor,
                                    onClick = { onSetSubjectAnchor(anchor) },
                                    label = { Text(anchor.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AxionViolet.copy(alpha = 0.35f),
                                        selectedLabelColor = TextPrimary
                                    )
                                )
                            }
                        }

                        OutlinedTextField(
                            value = state.outpaintPrompt,
                            onValueChange = onSetOutpaintPrompt,
                            label = { Text("16:9 Environment Outpainting Prompt") },
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AxionCyan,
                                unfocusedBorderColor = StudioBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("outpaint_prompt_input")
                        )

                        Button(
                            onClick = onRunOutpainting,
                            colors = ButtonDefaults.buttonColors(containerColor = AxionCyan, contentColor = StudioObsidian),
                            modifier = Modifier.fillMaxWidth().testTag("run_expand_16_9_btn")
                        ) {
                            Icon(Icons.Rounded.AspectRatio, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Expand Image to ${state.outpaintPreset.width}×${state.outpaintPreset.height} (16:9)")
                        }
                    }

                    EditorWorkflowMode.INSPECT -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onSetWorkflowMode(EditorWorkflowMode.TEXT_REMOVAL) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Remove Text")
                            }
                            OutlinedButton(
                                onClick = { onSetWorkflowMode(EditorWorkflowMode.BACKGROUND_REMOVAL) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Remove BG")
                            }
                            Button(
                                onClick = { onSetWorkflowMode(EditorWorkflowMode.OUTPAINT_16_9) },
                                colors = ButtonDefaults.buttonColors(containerColor = AxionCyan, contentColor = StudioObsidian),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("16:9 Expand")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolPillButton(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = if (selected) AxionCyan.copy(alpha = 0.22f) else StudioObsidian,
        border = BorderStroke(1.dp, if (selected) AxionCyan else StudioBorder),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(icon, contentDescription = label, tint = if (selected) AxionCyan else TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) TextPrimary else TextSecondary)
        }
    }
}

@Composable
private fun Outpaint16By9LivePreviewCanvas(
    bitmap: Bitmap,
    preset: OutpaintPreset,
    anchor: SubjectAnchor
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .border(1.5.dp, AxionCyan.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                .background(StudioSurface)
        ) {
            // Subtle outpaint grid guide
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cell = 20.dp.toPx()
                val cols = (size.width / cell).toInt() + 1
                val rows = (size.height / cell).toPx().toInt() + 1
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        drawRect(
                            color = if ((r + c) % 2 == 0) CheckerDark else CheckerLight,
                            topLeft = Offset(c * cell, r * cell),
                            size = Size(cell, cell)
                        )
                    }
                }
            }

            val alignment = when (anchor) {
                SubjectAnchor.LEFT -> Alignment.CenterStart
                SubjectAnchor.CENTER -> Alignment.Center
                SubjectAnchor.RIGHT -> Alignment.CenterEnd
            }

            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Original panel anchored in 16:9 canvas",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxHeight()
                    .align(alignment)
                    .border(1.5.dp, AxionViolet)
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp),
                color = StudioObsidian.copy(alpha = 0.85f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "16:9 Target: ${preset.width}×${preset.height} • Original Unstretched (${anchor.label})",
                    style = MaterialTheme.typography.labelSmall,
                    color = AxionCyan,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun InteractiveMaskEditorCanvas(
    originalBitmap: Bitmap,
    editedBitmap: Bitmap,
    maskBitmap: Bitmap?,
    showOriginalOnly: Boolean,
    showMaskOverlay: Boolean,
    compareSplitMode: Boolean,
    splitFraction: Float,
    toolMode: BrushToolMode,
    brushRadiusPx: Float,
    onSplitFractionChange: (Float) -> Unit,
    onStrokeComplete: (List<StrokePoint>, Float, Boolean) -> Unit
) {
    val activeDragPoints = remember { mutableStateListOf<StrokePoint>() }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val boxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val boxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val imgW = editedBitmap.width.toFloat().coerceAtLeast(1f)
        val imgH = editedBitmap.height.toFloat().coerceAtLeast(1f)
        val scale = min(boxW / imgW, boxH / imgH)
        val drawW = imgW * scale
        val drawH = imgH * scale
        val offsetX = (boxW - drawW) / 2f
        val offsetY = (boxH - drawH) / 2f

        val displayedImg = remember(editedBitmap, originalBitmap, showOriginalOnly) {
            (if (showOriginalOnly) originalBitmap else editedBitmap).asImageBitmap()
        }
        val origImg = remember(originalBitmap) { originalBitmap.asImageBitmap() }
        val editImg = remember(editedBitmap) { editedBitmap.asImageBitmap() }
        val maskImg = remember(maskBitmap) { maskBitmap?.asImageBitmap() }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(compareSplitMode, toolMode, brushRadiusPx, drawW, drawH, offsetX, offsetY) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            if (compareSplitMode) {
                                val frac = ((pos.x - offsetX) / drawW).coerceIn(0.05f, 0.95f)
                                onSplitFractionChange(frac)
                            } else if (toolMode != BrushToolMode.VIEW_PAN) {
                                activeDragPoints.clear()
                                activeDragPoints.add(
                                    StrokePoint(
                                        xNorm = ((pos.x - offsetX) / drawW).coerceIn(0f, 1f),
                                        yNorm = ((pos.y - offsetY) / drawH).coerceIn(0f, 1f)
                                    )
                                )
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val pos = change.position
                            if (compareSplitMode) {
                                val frac = ((pos.x - offsetX) / drawW).coerceIn(0.05f, 0.95f)
                                onSplitFractionChange(frac)
                            } else if (toolMode != BrushToolMode.VIEW_PAN) {
                                activeDragPoints.add(
                                    StrokePoint(
                                        xNorm = ((pos.x - offsetX) / drawW).coerceIn(0f, 1f),
                                        yNorm = ((pos.y - offsetY) / drawH).coerceIn(0f, 1f)
                                    )
                                )
                            }
                        },
                        onDragEnd = {
                            if (!compareSplitMode && toolMode != BrushToolMode.VIEW_PAN && activeDragPoints.isNotEmpty()) {
                                val normRadius = brushRadiusPx / maxOf(drawW, drawH).coerceAtLeast(1f)
                                onStrokeComplete(
                                    activeDragPoints.toList(),
                                    normRadius,
                                    toolMode == BrushToolMode.ERASE_MASK
                                )
                                activeDragPoints.clear()
                            }
                        },
                        onDragCancel = { activeDragPoints.clear() }
                    )
                }
        ) {
            val dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt())
            val dstSize = IntSize(drawW.roundToInt().coerceAtLeast(1), drawH.roundToInt().coerceAtLeast(1))

            // Checkerboard behind image for PNG alpha transparency preview
            val checkerStep = 14.dp.toPx()
            val cCols = (drawW / checkerStep).toInt() + 1
            val cRows = (drawH / checkerStep).toInt() + 1
            clipRect(offsetX, offsetY, offsetX + drawW, offsetY + drawH) {
                for (r in 0 until cRows) {
                    for (c in 0 until cCols) {
                        drawRect(
                            color = if ((r + c) % 2 == 0) CheckerDark else CheckerLight,
                            topLeft = Offset(offsetX + c * checkerStep, offsetY + r * checkerStep),
                            size = Size(checkerStep, checkerStep)
                        )
                    }
                }
            }

            if (compareSplitMode) {
                val splitX = offsetX + drawW * splitFraction
                // Left side: Original
                clipRect(offsetX, offsetY, splitX, offsetY + drawH) {
                    drawImage(image = origImg, dstOffset = dstOffset, dstSize = dstSize)
                }
                // Right side: Edited
                clipRect(splitX, offsetY, offsetX + drawW, offsetY + drawH) {
                    drawImage(image = editImg, dstOffset = dstOffset, dstSize = dstSize)
                }
                // Split Handle Line
                drawLine(
                    color = AxionCyan,
                    start = Offset(splitX, offsetY),
                    end = Offset(splitX, offsetY + drawH),
                    strokeWidth = 2.5.dp.toPx()
                )
                drawCircle(
                    color = AxionCyan,
                    radius = 12.dp.toPx(),
                    center = Offset(splitX, offsetY + drawH / 2f)
                )
            } else {
                drawImage(image = displayedImg, dstOffset = dstOffset, dstSize = dstSize)

                if (showMaskOverlay && maskImg != null) {
                    drawImage(image = maskImg, dstOffset = dstOffset, dstSize = dstSize, alpha = 0.65f)
                }

                // Live interactive stroke preview
                if (activeDragPoints.isNotEmpty()) {
                    val path = Path()
                    val first = activeDragPoints.first()
                    path.moveTo(offsetX + first.xNorm * drawW, offsetY + first.yNorm * drawH)
                    for (i in 1 until activeDragPoints.size) {
                        val pt = activeDragPoints[i]
                        path.lineTo(offsetX + pt.xNorm * drawW, offsetY + pt.yNorm * drawH)
                    }
                    drawPath(
                        path = path,
                        color = if (toolMode == BrushToolMode.ERASE_MASK) MaskOverlayCyan else MaskOverlayRed,
                        style = Stroke(
                            width = brushRadiusPx * 2f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
    }
}

private fun Float.toPx(): Float = this
