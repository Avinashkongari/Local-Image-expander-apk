package com.example.domain.model

import android.graphics.RectF
import kotlinx.serialization.Serializable

@Serializable
data class DetectedColorInfo(
    val hex: String,
    val percentage: Float,
    val label: String
)

@Serializable
data class DetectedRegion(
    val id: String,
    val label: String,
    val kind: RegionKind,
    val leftNorm: Float,
    val topNorm: Float,
    val rightNorm: Float,
    val bottomNorm: Float,
    val extractedText: String = "",
    val confidence: Float = 1f
) {
    fun toRectF(width: Int, height: Int): RectF = RectF(
        leftNorm * width,
        topNorm * height,
        rightNorm * width,
        bottomNorm * height
    )
}

@Serializable
enum class RegionKind {
    TEXT_OCR,
    SPEECH_BUBBLE,
    MAIN_SUBJECT
}

@Serializable
data class ImageAnalysisResult(
    val width: Int,
    val height: Int,
    val aspectRatioLabel: String,
    val subjectSummary: String,
    val subjectBoundsNorm: DetectedRegion?,
    val estimatedCharacterOrObjectRegions: Int,
    val textRegions: List<DetectedRegion>,
    val speechBubbleRegions: List<DetectedRegion>,
    val backgroundSummary: String,
    val dominantColors: List<DetectedColorInfo>,
    val colorTemperature: String,
    val lightingSummary: String,
    val lightDirection: String,
    val averageLuminance: Float,
    val contrastScore: Float,
    val perspectiveSummary: String,
    val compositionSummary: String,
    val suggestedOutpaintPrompt: String,
    val engineNotes: String
)
