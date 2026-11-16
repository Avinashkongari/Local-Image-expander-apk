package com.example.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.EditorUiState
import com.example.ui.theme.AxionCyan
import com.example.ui.theme.AxionMagenta
import com.example.ui.theme.AxionViolet
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun AiAnalysisScreen(
    editorState: EditorUiState,
    onImportImage: () -> Unit,
    onReanalyze: () -> Unit,
    onAutoMaskAndOpenTextRemoval: () -> Unit,
    onSendToOutpaint: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bmp = editorState.editedBitmap ?: editorState.originalBitmap
    val analysis = editorState.analysisResult
    var showOverlays by remember { mutableStateOf(true) }

    if (bmp == null) {
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
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No Image Loaded", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Import a Manga, Manhua, Anime, or video frame to run offline subject, OCR, lighting, color, and composition analysis.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onImportImage,
                        colors = ButtonDefaults.buttonColors(containerColor = AxionCyan, contentColor = StudioObsidian),
                        modifier = Modifier.testTag("analysis_import_button")
                    ) {
                        Text("Import Image")
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(StudioObsidian),
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
                    Text("AI Image Analysis", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Text(
                        text = analysis?.engineNotes ?: "On-Device Vision & ML Kit OCR",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                OutlinedButton(
                    onClick = onReanalyze,
                    border = BorderStroke(1.dp, AxionCyan.copy(alpha = 0.6f)),
                    modifier = Modifier.testTag("analysis_reanalyze_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Re-Scan")
                }
            }
        }

        // Visual Bounding Overlay Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioBorder),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${bmp.width}×${bmp.height} • ${analysis?.aspectRatioLabel ?: ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = AxionCyan
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Overlays", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Spacer(modifier = Modifier.size(6.dp))
                            Switch(checked = showOverlays, onCheckedChange = { showOverlays = it })
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio((bmp.width.toFloat() / bmp.height.coerceAtLeast(1)).coerceIn(0.55f, 2.2f))
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Analyzed image preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (showOverlays && analysis != null) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                analysis.subjectBoundsNorm?.let { sb ->
                                    drawRect(
                                        color = AxionCyan,
                                        topLeft = Offset(sb.leftNorm * size.width, sb.topNorm * size.height),
                                        size = Size(
                                            (sb.rightNorm - sb.leftNorm) * size.width,
                                            (sb.bottomNorm - sb.topNorm) * size.height
                                        ),
                                        style = Stroke(width = 3.dp.toPx())
                                    )
                                }
                                analysis.speechBubbleRegions.forEach { bubble ->
                                    drawRect(
                                        color = AxionViolet,
                                        topLeft = Offset(bubble.leftNorm * size.width, bubble.topNorm * size.height),
                                        size = Size(
                                            (bubble.rightNorm - bubble.leftNorm) * size.width,
                                            (bubble.bottomNorm - bubble.topNorm) * size.height
                                        ),
                                        style = Stroke(width = 2.5.dp.toPx())
                                    )
                                }
                                analysis.textRegions.forEach { txt ->
                                    drawRect(
                                        color = StatusWarning,
                                        topLeft = Offset(txt.leftNorm * size.width, txt.topNorm * size.height),
                                        size = Size(
                                            (txt.rightNorm - txt.leftNorm) * size.width,
                                            (txt.bottomNorm - txt.topNorm) * size.height
                                        ),
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LegendDot(color = AxionCyan, label = "Main Subject")
                        LegendDot(color = AxionViolet, label = "Speech Bubbles")
                        LegendDot(color = StatusWarning, label = "OCR Text")
                    }
                }
            }
        }

        if (analysis != null) {
            // Quick workflow handoff buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onAutoMaskAndOpenTextRemoval,
                        colors = ButtonDefaults.buttonColors(containerColor = AxionMagenta, contentColor = StudioObsidian),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("analysis_auto_mask_text")
                    ) {
                        Icon(Icons.Rounded.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Mask & Remove Text")
                    }
                    Button(
                        onClick = onSendToOutpaint,
                        colors = ButtonDefaults.buttonColors(containerColor = AxionCyan, contentColor = StudioObsidian),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("analysis_send_outpaint")
                    ) {
                        Icon(Icons.Rounded.AspectRatio, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Expand 16:9")
                    }
                }
            }

            // Subject, Text & Bubbles Card
            item {
                AnalysisSectionCard(title = "Subject, Characters & Text") {
                    Text(analysis.subjectSummary, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Estimated salient character/object clusters: ${analysis.estimatedCharacterOrObjectRegions}",
                        style = MaterialTheme.typography.labelMedium,
                        color = AxionCyan
                    )
                    if (analysis.textRegions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Detected OCR Snippets:", style = MaterialTheme.typography.labelMedium, color = StatusWarning)
                        analysis.textRegions.take(5).forEach { reg ->
                            Text(
                                text = "• \"${reg.extractedText}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Dominant Colors Card
            item {
                AnalysisSectionCard(title = "Dominant Colors & Palette (${analysis.colorTemperature})") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        analysis.dominantColors.forEach { col ->
                            val parsed = runCatching { Color(AndroidColor.parseColor(col.hex)) }.getOrDefault(AxionCyan)
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(parsed)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(col.hex, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                                Text("${col.percentage.roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            // Lighting, Perspective & Background Card
            item {
                AnalysisSectionCard(title = "Lighting, Perspective & Composition") {
                    AnalysisMetricRow("Lighting", analysis.lightingSummary)
                    AnalysisMetricRow("Light Direction", analysis.lightDirection)
                    AnalysisMetricRow("Avg Luminance", "${(analysis.averageLuminance * 100).roundToInt()}%")
                    AnalysisMetricRow("Contrast Score", "${(analysis.contrastScore * 100).roundToInt()}/100")
                    AnalysisMetricRow("Perspective", analysis.perspectiveSummary)
                    AnalysisMetricRow("Composition", analysis.compositionSummary)
                    AnalysisMetricRow("Background", analysis.backgroundSummary)
                }
            }

            // Generated Outpaint Prompt
            item {
                AnalysisSectionCard(title = "Generated 16:9 Outpainting Prompt") {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = AxionCyan, modifier = Modifier.size(18.dp))
                        Text(
                            text = analysis.suggestedOutpaintPrompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun AnalysisSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
        border = BorderStroke(1.dp, StudioBorder),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = AxionCyan)
            content()
        }
    }
}

@Composable
private fun AnalysisMetricRow(label: String, value: String) {
    Surface(
        color = StudioObsidian.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(modifier = Modifier.size(12.dp))
            Text(value, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
        }
    }
}
