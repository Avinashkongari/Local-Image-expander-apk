package com.example.domain.model

import android.graphics.Bitmap

enum class EditorWorkflowMode(val label: String) {
    INSPECT("Inspect"),
    TEXT_REMOVAL("Remove Text"),
    BACKGROUND_REMOVAL("Remove BG"),
    OUTPAINT_16_9("16:9 Expand")
}

enum class BrushToolMode {
    VIEW_PAN,
    PAINT_MASK,
    ERASE_MASK
}

enum class OutpaintPreset(
    val label: String,
    val width: Int,
    val height: Int
) {
    HD_720P("1280 × 720 (HD 16:9)", 1280, 720),
    FHD_1080P("1920 × 1080 (Full HD 16:9)", 1920, 1080)
}

enum class SubjectAnchor(val label: String) {
    LEFT("Left"),
    CENTER("Center"),
    RIGHT("Right")
}

enum class ExportFormat(val label: String, val mimeType: String, val extension: String) {
    PNG("PNG (Lossless / Alpha)", "image/png", "png"),
    JPG("JPG (95% Quality)", "image/jpeg", "jpg")
}

enum class BackgroundReplacementMode(val label: String) {
    TRANSPARENT("Transparent"),
    SOLID_DARK("Studio Dark"),
    SOLID_WHITE("Pure White"),
    CHROMA_GREEN("Chroma Green"),
    CUSTOM_COLOR("Custom Color")
}

data class ProcessingStatus(
    val isRunning: Boolean = false,
    val stageTitle: String = "",
    val detailMessage: String = "",
    val progress: Float = 0f,
    val canCancel: Boolean = true
)

data class StrokePoint(
    val xNorm: Float,
    val yNorm: Float
)

data class MaskStroke(
    val points: List<StrokePoint>,
    val brushRadiusNorm: Float,
    val isEraser: Boolean
)

data class EditorUiState(
    val projectId: Long? = null,
    val projectTitle: String = "Untitled Panel",
    val workflowMode: EditorWorkflowMode = EditorWorkflowMode.INSPECT,
    val toolMode: BrushToolMode = BrushToolMode.PAINT_MASK,
    val brushRadiusPx: Float = 28f,
    val originalBitmap: Bitmap? = null,
    val editedBitmap: Bitmap? = null,
    val maskBitmap: Bitmap? = null,
    val showMaskOverlay: Boolean = true,
    val showOriginalPreview: Boolean = false,
    val compareSplitMode: Boolean = false,
    val compareSplitFraction: Float = 0.5f,
    val outpaintPreset: OutpaintPreset = OutpaintPreset.HD_720P,
    val subjectAnchor: SubjectAnchor = SubjectAnchor.CENTER,
    val outpaintPrompt: String = "Expand this image to 16:9. Continue the existing environment, preserve the original character, match the lighting and colors, and generate a natural background.",
    val bgReplacementMode: BackgroundReplacementMode = BackgroundReplacementMode.TRANSPARENT,
    val customBgColorArgb: Int = 0xFF0F172A.toInt(),
    val analysisResult: ImageAnalysisResult? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hasMaskContent: Boolean = false,
    val processing: ProcessingStatus = ProcessingStatus(),
    val statusBanner: StatusBanner? = null
)

data class StatusBanner(
    val title: String,
    val message: String,
    val isWarning: Boolean = false,
    val isError: Boolean = false,
    val actionLabel: String? = null,
    val actionType: BannerActionType? = null
)

enum class BannerActionType {
    OPEN_MODEL_MANAGER,
    RUN_STRUCTURAL_INPAINT_FALLBACK,
    RUN_EDGE_EXTEND_PREVIEW
}
