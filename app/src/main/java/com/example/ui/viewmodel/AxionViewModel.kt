package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AxionDatabase
import com.example.data.db.HistoryEntity
import com.example.data.db.LocalModelEntity
import com.example.data.prefs.SettingsRepository
import com.example.data.prefs.StudioSettings
import com.example.domain.model.BackgroundReplacementMode
import com.example.domain.model.BannerActionType
import com.example.domain.model.BrushToolMode
import com.example.domain.model.DeviceMemorySnapshot
import com.example.domain.model.EditorUiState
import com.example.domain.model.EditorWorkflowMode
import com.example.domain.model.ExportFormat
import com.example.domain.model.ImageAnalysisResult
import com.example.domain.model.ModelCapabilitySlot
import com.example.domain.model.ModelDownloadProgress
import com.example.domain.model.OutpaintPreset
import com.example.domain.model.ProcessingStatus
import com.example.domain.model.StatusBanner
import com.example.domain.model.StrokePoint
import com.example.domain.model.SubjectAnchor
import com.example.engine.ImageAnalyzerEngine
import com.example.engine.InpaintingFallbackEngine
import com.example.engine.MemoryManager
import com.example.engine.ModelDownloadManager
import com.example.engine.OnnxModelRunner
import com.example.engine.OutpaintCanvasEngine
import com.example.engine.SegmentationEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class EditorUndoSnapshot(
    val editedBitmap: Bitmap,
    val maskBitmap: Bitmap
)

class AxionViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val dao = AxionDatabase.getInstance(context).axionDao()
    private val settingsRepo = SettingsRepository(context)
    private val modelManager = ModelDownloadManager(context, dao)

    val settings: StateFlow<StudioSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StudioSettings())

    val historyItems: StateFlow<List<HistoryEntity>> = dao.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installedModels: StateFlow<Map<String, LocalModelEntity>> = dao.observeLocalModels()
        .map { list ->
            list.filter { it.isInstalled && File(it.filePath).exists() }
                .associateBy { it.slotKey }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _editorState = MutableStateFlow(EditorUiState())
    val editorState: StateFlow<EditorUiState> = _editorState.asStateFlow()

    private val _memorySnapshot = MutableStateFlow(MemoryManager.getDeviceMemorySnapshot(context))
    val memorySnapshot: StateFlow<DeviceMemorySnapshot> = _memorySnapshot.asStateFlow()

    private val _downloadProgress = MutableStateFlow<ModelDownloadProgress?>(null)
    val downloadProgress: StateFlow<ModelDownloadProgress?> = _downloadProgress.asStateFlow()

    private val _modelManagerMessage = MutableStateFlow<String?>(null)
    val modelManagerMessage: StateFlow<String?> = _modelManagerMessage.asStateFlow()

    private val _uiToastMessage = MutableStateFlow<String?>(null)
    val uiToastMessage: StateFlow<String?> = _uiToastMessage.asStateFlow()

    private val undoStack = ArrayDeque<EditorUndoSnapshot>()
    private val redoStack = ArrayDeque<EditorUndoSnapshot>()
    private val maxUndoDepth = 6 // Memory-safe bound for 6GB RAM

    private var activeProcessingJob: Job? = null
    private var activeDownloadJob: Job? = null

    fun refreshMemoryStats() {
        _memorySnapshot.value = MemoryManager.getDeviceMemorySnapshot(context)
    }

    fun clearToastMessage() {
        _uiToastMessage.value = null
    }

    fun clearModelManagerMessage() {
        _modelManagerMessage.value = null
    }

    fun dismissBanner() {
        _editorState.update { it.copy(statusBanner = null) }
    }

    fun importImageFromUri(
        uri: Uri,
        targetMode: EditorWorkflowMode = EditorWorkflowMode.INSPECT,
        onLoaded: () -> Unit = {}
    ) {
        activeProcessingJob?.cancel()
        activeProcessingJob = viewModelScope.launch {
            _editorState.update {
                it.copy(
                    processing = ProcessingStatus(
                        isRunning = true,
                        stageTitle = "Importing Image",
                        detailMessage = "Decoding ARGB_8888 bitmap safely within 6GB RAM budget...",
                        progress = 0.25f,
                        canCancel = true
                    ),
                    statusBanner = null
                )
            }
            val maxDim = settings.value.maxWorkingDimension
            val decoded = MemoryManager.decodeBitmapFromUri(context, uri, maxDim)
            if (decoded == null) {
                _editorState.update {
                    it.copy(
                        processing = ProcessingStatus(),
                        statusBanner = StatusBanner(
                            title = "Import Failed",
                            message = "Could not decode image. Ensure it is a valid PNG, JPG, or WebP file.",
                            isError = true
                        )
                    )
                }
                return@launch
            }

            undoStack.clear()
            redoStack.clear()
            val mask = MemoryManager.createEmptyMask(decoded.width, decoded.height)
            val workingCopy = decoded.copy(Bitmap.Config.ARGB_8888, true)

            _editorState.update {
                it.copy(
                    projectId = null,
                    projectTitle = "Panel_${System.currentTimeMillis() % 100000}",
                    workflowMode = targetMode,
                    toolMode = if (targetMode == EditorWorkflowMode.TEXT_REMOVAL) BrushToolMode.PAINT_MASK else BrushToolMode.VIEW_PAN,
                    originalBitmap = decoded,
                    editedBitmap = workingCopy,
                    maskBitmap = mask,
                    outpaintPreset = settings.value.defaultOutpaintPreset,
                    analysisResult = null,
                    canUndo = false,
                    canRedo = false,
                    hasMaskContent = false,
                    showMaskOverlay = true,
                    showOriginalPreview = false,
                    compareSplitMode = false,
                    processing = ProcessingStatus(
                        isRunning = true,
                        stageTitle = "Running AI Image Analysis",
                        detailMessage = "Scanning text, speech bubbles, palette, lighting & composition...",
                        progress = 0.65f,
                        canCancel = true
                    )
                )
            }

            // Automatically run offline vision + OCR analysis on import
            val analysis = runCatching { ImageAnalyzerEngine.analyzeImage(decoded) }.getOrNull()
            _editorState.update {
                it.copy(
                    analysisResult = analysis,
                    outpaintPrompt = analysis?.suggestedOutpaintPrompt ?: it.outpaintPrompt,
                    processing = ProcessingStatus()
                )
            }
            refreshMemoryStats()
            saveToHistoryInternal(operationTag = "Imported")
            onLoaded()
        }
    }

    fun reopenHistoryItem(item: HistoryEntity, onLoaded: () -> Unit) {
        viewModelScope.launch {
            _editorState.update {
                it.copy(
                    processing = ProcessingStatus(
                        isRunning = true,
                        stageTitle = "Loading Project",
                        detailMessage = "Restoring ${item.title} from local storage...",
                        progress = 0.4f,
                        canCancel = false
                    )
                )
            }
            val maxDim = settings.value.maxWorkingDimension
            val orig = MemoryManager.decodeBitmapFromFile(item.originalPath, maxDim)
            val edited = MemoryManager.decodeBitmapFromFile(item.editedPath, maxDim) ?: orig?.copy(Bitmap.Config.ARGB_8888, true)
            if (orig == null || edited == null) {
                _editorState.update {
                    it.copy(
                        processing = ProcessingStatus(),
                        statusBanner = StatusBanner(
                            title = "Project Files Missing",
                            message = "Saved image files for ${item.title} are no longer on disk.",
                            isError = true
                        )
                    )
                }
                return@launch
            }
            undoStack.clear()
            redoStack.clear()
            val mask = MemoryManager.createEmptyMask(edited.width, edited.height)
            val analysis = runCatching { ImageAnalyzerEngine.analyzeImage(orig) }.getOrNull()
            _editorState.update {
                it.copy(
                    projectId = item.id,
                    projectTitle = item.title,
                    originalBitmap = orig,
                    editedBitmap = edited,
                    maskBitmap = mask,
                    analysisResult = analysis,
                    outpaintPrompt = item.promptUsed.ifBlank { analysis?.suggestedOutpaintPrompt ?: it.outpaintPrompt },
                    canUndo = false,
                    canRedo = false,
                    hasMaskContent = false,
                    processing = ProcessingStatus(),
                    statusBanner = null
                )
            }
            onLoaded()
        }
    }

    fun runAiAnalysis() {
        val target = _editorState.value.editedBitmap ?: _editorState.value.originalBitmap ?: return
        activeProcessingJob?.cancel()
        activeProcessingJob = viewModelScope.launch {
            _editorState.update {
                it.copy(
                    processing = ProcessingStatus(
                        isRunning = true,
                        stageTitle = "AI Image Analysis",
                        detailMessage = "Running ML Kit OCR, speech-bubble contours, palette, lighting & Sobel perspective...",
                        progress = 0.45f,
                        canCancel = true
                    )
                )
            }
            val result = ImageAnalyzerEngine.analyzeImage(target)
            _editorState.update {
                it.copy(
                    analysisResult = result,
                    outpaintPrompt = result.suggestedOutpaintPrompt,
                    processing = ProcessingStatus(),
                    statusBanner = StatusBanner(
                        title = "Analysis Complete",
                        message = "Found ${result.textRegions.size} text block(s), ${result.speechBubbleRegions.size} bubble candidate(s), ${result.dominantColors.size} dominant colors."
                    )
                )
            }
            refreshMemoryStats()
        }
    }

    fun setWorkflowMode(mode: EditorWorkflowMode) {
        _editorState.update {
            val nextTool = when (mode) {
                EditorWorkflowMode.TEXT_REMOVAL -> BrushToolMode.PAINT_MASK
                EditorWorkflowMode.BACKGROUND_REMOVAL -> BrushToolMode.VIEW_PAN
                EditorWorkflowMode.OUTPAINT_16_9 -> BrushToolMode.VIEW_PAN
                EditorWorkflowMode.INSPECT -> BrushToolMode.VIEW_PAN
            }
            it.copy(
                workflowMode = mode,
                toolMode = nextTool,
                statusBanner = null
            )
        }
    }

    fun setToolMode(tool: BrushToolMode) {
        _editorState.update { it.copy(toolMode = tool) }
    }

    fun setBrushRadius(radiusPx: Float) {
        _editorState.update { it.copy(brushRadiusPx = radiusPx.coerceIn(8f, 96f)) }
    }

    fun toggleMaskOverlay() {
        _editorState.update { it.copy(showMaskOverlay = !it.showMaskOverlay) }
    }

    fun setShowOriginalPreview(showOriginal: Boolean) {
        _editorState.update { it.copy(showOriginalPreview = showOriginal) }
    }

    fun toggleCompareSplitMode() {
        _editorState.update { it.copy(compareSplitMode = !it.compareSplitMode) }
    }

    fun setCompareSplitFraction(fraction: Float) {
        _editorState.update { it.copy(compareSplitFraction = fraction.coerceIn(0.05f, 0.95f)) }
    }

    fun setOutpaintPreset(preset: OutpaintPreset) {
        _editorState.update { it.copy(outpaintPreset = preset) }
    }

    fun setSubjectAnchor(anchor: SubjectAnchor) {
        _editorState.update { it.copy(subjectAnchor = anchor) }
    }

    fun setOutpaintPrompt(prompt: String) {
        _editorState.update { it.copy(outpaintPrompt = prompt) }
    }

    fun setBgReplacementMode(mode: BackgroundReplacementMode) {
        _editorState.update { it.copy(bgReplacementMode = mode) }
    }

    fun setCustomBgColor(argb: Int) {
        _editorState.update { it.copy(customBgColorArgb = argb) }
    }

    fun applyMaskStroke(points: List<StrokePoint>, brushRadiusNorm: Float, isEraser: Boolean) {
        val current = _editorState.value
        val mask = current.maskBitmap ?: return
        if (points.isEmpty()) return

        pushUndoSnapshot()
        val updatedMask = mask.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(updatedMask)
        val w = updatedMask.width.toFloat()
        val h = updatedMask.height.toFloat()
        val strokeWidthPx = (brushRadiusNorm * maxOf(w, h) * 2f).coerceAtLeast(6f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = strokeWidthPx
            if (isEraser) {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            } else {
                color = Color.argb(210, 239, 68, 68)
            }
        }

        if (points.size == 1) {
            val fillPaint = Paint(paint).apply { style = Paint.Style.FILL }
            canvas.drawCircle(points[0].xNorm * w, points[0].yNorm * h, strokeWidthPx / 2f, fillPaint)
        } else {
            val path = android.graphics.Path()
            path.moveTo(points[0].xNorm * w, points[0].yNorm * h)
            for (i in 1 until points.size) {
                path.lineTo(points[i].xNorm * w, points[i].yNorm * h)
            }
            canvas.drawPath(path, paint)
        }

        _editorState.update {
            it.copy(
                maskBitmap = updatedMask,
                hasMaskContent = !isEraser || checkMaskHasPixels(updatedMask),
                showMaskOverlay = true
            )
        }
    }

    fun clearMask() {
        val current = _editorState.value
        val edited = current.editedBitmap ?: return
        pushUndoSnapshot()
        _editorState.update {
            it.copy(
                maskBitmap = MemoryManager.createEmptyMask(edited.width, edited.height),
                hasMaskContent = false
            )
        }
    }

    /**
     * Populates maskBitmap automatically from real ML Kit OCR text blocks & speech bubble contours.
     */
    fun autoMaskTextAndBubbles(includeBubbles: Boolean) {
        val current = _editorState.value
        val edited = current.editedBitmap ?: return
        activeProcessingJob?.cancel()
        activeProcessingJob = viewModelScope.launch {
            _editorState.update {
                it.copy(
                    processing = ProcessingStatus(
                        isRunning = true,
                        stageTitle = "Detecting Text & Speech Bubbles",
                        detailMessage = "Scanning panel regions offline...",
                        progress = 0.4f,
                        canCancel = true
                    )
                )
            }
            val analysis = current.analysisResult ?: ImageAnalyzerEngine.analyzeImage(edited)
            val regions = if (includeBubbles) {
                analysis.textRegions + analysis.speechBubbleRegions
            } else {
                analysis.textRegions
            }

            if (regions.isEmpty()) {
                _editorState.update {
                    it.copy(
                        analysisResult = analysis,
                        processing = ProcessingStatus(),
                        statusBanner = StatusBanner(
                            title = "No Text Regions Detected",
                            message = "OCR found no text blocks automatically. Paint over stylized SFX/text manually with the Brush tool.",
                            isWarning = true
                        )
                    )
                }
                return@launch
            }

            pushUndoSnapshot()
            val mask = (current.maskBitmap ?: MemoryManager.createEmptyMask(edited.width, edited.height))
                .copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mask)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(210, 239, 68, 68)
                style = Paint.Style.FILL
            }
            val padX = edited.width * 0.012f
            val padY = edited.height * 0.012f
            regions.forEach { reg ->
                val rect = reg.toRectF(edited.width, edited.height)
                val expanded = RectF(
                    (rect.left - padX).coerceAtLeast(0f),
                    (rect.top - padY).coerceAtLeast(0f),
                    (rect.right + padX).coerceAtMost(edited.width.toFloat()),
                    (rect.bottom + padY).coerceAtMost(edited.height.toFloat())
                )
                canvas.drawRoundRect(expanded, 12f, 12f, paint)
            }

            _editorState.update {
                it.copy(
                    analysisResult = analysis,
                    maskBitmap = mask,
                    hasMaskContent = true,
                    showMaskOverlay = true,
                    workflowMode = EditorWorkflowMode.TEXT_REMOVAL,
                    processing = ProcessingStatus(),
                    statusBanner = StatusBanner(
                        title = "Mask Ready (${regions.size} regions)",
                        message = "Refine with Brush/Eraser if needed, then run AI Text Removal."
                    )
                )
            }
        }
    }

    /**
     * Runs real local ONNX Inpainting (e.g. LaMa ONNX) if installed.
     * NEVER fakes AI removal: if no local model is installed, reports exact requirement
     * and offers explicit Model Manager import OR explicit non-AI structural fill fallback.
     */
    fun runAiTextRemoval() {
        val current = _editorState.value
        val edited = current.editedBitmap ?: return
        val mask = current.maskBitmap ?: return

        if (!current.hasMaskContent && !checkMaskHasPixels(mask)) {
            _editorState.update {
                it.copy(
                    statusBanner = StatusBanner(
                        title = "Select Mask First",
                        message = "Paint over text/speech bubbles with the Brush tool or tap Auto-Mask Text.",
                        isWarning = true
                    )
                )
            }
            return
        }

        val lamaModel = installedModels.value[ModelCapabilitySlot.LAMA_INPAINTING.slotKey]
        if (lamaModel == null) {
            _editorState.update {
                it.copy(
                    statusBanner = StatusBanner(
                        title = "LaMa Inpainting Model Not Installed",
                        message = "Real AI text removal requires a local ONNX inpainting model (e.g. LaMa ONNX) in Model Manager. We do not fake AI results.",
                        isWarning = true,
                        actionLabel = "Open Model Manager",
                        actionType = BannerActionType.OPEN_MODEL_MANAGER
                    )
                )
            }
            return
        }

        if (lamaModel.modelFormat != "ONNX") {
            _editorState.update {
                it.copy(
                    statusBanner = StatusBanner(
                        title = "Unsupported Inpainting Format",
                        message = "Installed model is ${lamaModel.modelFormat}. Tiled inpainting requires an ONNX (.onnx) model such as LaMa ONNX.",
                        isWarning = true,
                        actionLabel = "Open Model Manager",
                        actionType = BannerActionType.OPEN_MODEL_MANAGER
                    )
                )
            }
            return
        }

        activeProcessingJob?.cancel()
        activeProcessingJob = viewModelScope.launch {
            try {
                pushUndoSnapshot()
                val curSettings = settings.value
                val result = OnnxModelRunner.runTiledInpainting(
                    modelPath = lamaModel.filePath,
                    source = edited,
                    mask = mask,
                    threads = curSettings.inferenceThreads,
                    autoUnload = curSettings.autoUnloadModelAfterInference
                ) { prog, msg ->
                    _editorState.update {
                        it.copy(
                            processing = ProcessingStatus(
                                isRunning = true,
                                stageTitle = "LaMa ONNX Inpainting",
                                detailMessage = msg,
                                progress = prog,
                                canCancel = true
                            )
                        )
                    }
                }
                _editorState.update {
                    it.copy(
                        editedBitmap = result,
                        maskBitmap = MemoryManager.createEmptyMask(result.width, result.height),
                        hasMaskContent = false,
                        processing = ProcessingStatus(),
                        statusBanner = StatusBanner(
                            title = "ONNX Inpainting Complete",
                            message = "Processed via ${lamaModel.displayName}. Model unloaded from RAM."
                        )
                    )
                }
                saveToHistoryInternal("AI Text Removal (ONNX)")
                refreshMemoryStats()
            } catch (e: CancellationException) {
                undo()
                _editorState.update { it.copy(processing = ProcessingStatus()) }
            } catch (e: OutOfMemoryError) {
                System.gc()
                undo()
                _editorState.update {
                    it.copy(
                        processing = ProcessingStatus(),
                        statusBanner = StatusBanner(
                            title = "Memory Budget Exceeded (OOM)",
                            message = "Lower Max Working Dimension in Settings for 6GB RAM compatibility.",
                            isError = true
                        )
                    )
                }
            } catch (e: Throwable) {
                undo()
                _editorState.update {
                    it.copy(
                        processing = ProcessingStatus(),
                        statusBanner = StatusBanner(
                            title = "ONNX Inference Error",
                            message = e.message ?: "Model tensor signature did not match LaMa [1,3,H,W] + [1,1,H,W] contract.",
                            isError = true,
                            actionLabel = "Run Non-AI Structural Fill",
                            actionType = BannerActionType.RUN_STRUCTURAL_INPAINT_FALLBACK
                        )
                    )
                }
            }
        }
    }

    fun runExplicitNonAiStructuralFill() {
        val current = _editorState.value
        val edited = current.editedBitmap ?: return
        val mask = current.maskBitmap ?: return
        activeProcessingJob?.cancel()
        activeProcessingJob = viewModelScope.launch {
            try {
                pushUndoSnapshot()
                val filled = InpaintingFallbackEngine.runStructuralFill(edited, mask) { prog, msg ->
                    _editorState.update {
                        it.copy(
                            processing = ProcessingStatus(
                                isRunning = true,
                                stageTitle = "Non-AI Structural Fill",
                                detailMessage = msg,
                                progress = prog,
                                canCancel = true
                            )
                        )
                    }
                }
                _editorState.update {
                    it.copy(
                        editedBitmap = filled,
                        maskBitmap = MemoryManager.createEmptyMask(filled.width, filled.height),
                        hasMaskContent = false,
                        processing = ProcessingStatus(),
                        statusBanner = StatusBanner(
                            title = "Structural Fill Applied (Non-AI)",
                            message = "Completed classic boundary-diffusion fill. Install LaMa ONNX in Model Manager for neural texture reconstruction."
                        )
                    )
                }
                saveToHistoryInternal("Structural Text Fill (Non-AI)")
                refreshMemoryStats()
            } catch (e: CancellationException) {
                undo()
                _editorState.update { it.copy(processing = ProcessingStatus()) }
            }
        }
    }

    /**
     * Runs real foreground segmentation using installed local ONNX/LiteRT segmentation model
     * OR ML Kit Subject Segmentation on-device.
     */
    fun runBackgroundRemoval() {
        val current = _editorState.value
        val edited = current.editedBitmap ?: return
        val segModel = installedModels.value[ModelCapabilitySlot.FOREGROUND_SEGMENTATION.slotKey]
        val curSettings = settings.value

        activeProcessingJob?.cancel()
        activeProcessingJob = viewModelScope.launch {
            try {
                pushUndoSnapshot()
                val outcome = SegmentationEngine.segmentForeground(
                    source = edited,
                    installedModel = segModel,
                    threads = curSettings.inferenceThreads,
                    autoUnload = curSettings.autoUnloadModelAfterInference,
                    replacementMode = current.bgReplacementMode,
                    customBgColorArgb = current.customBgColorArgb
                ) { prog, msg ->
                    _editorState.update {
                        it.copy(
                            processing = ProcessingStatus(
                                isRunning = true,
                                stageTitle = "Background Removal",
                                detailMessage = msg,
                                progress = prog,
                                canCancel = true
                            )
                        )
                    }
                }

                _editorState.update {
                    it.copy(
                        editedBitmap = outcome.compositedBitmap,
                        workflowMode = EditorWorkflowMode.BACKGROUND_REMOVAL,
                        processing = ProcessingStatus(),
                        statusBanner = StatusBanner(
                            title = "Background Removed",
                            message = "Engine: ${outcome.engineUsed}. Export as PNG to preserve alpha transparency."
                        )
                    )
                }
                saveToHistoryInternal("Background Removed")
                refreshMemoryStats()
            } catch (e: CancellationException) {
                undo()
                _editorState.update { it.copy(processing = ProcessingStatus()) }
            } catch (e: Throwable) {
                undo()
                _editorState.update {
                    it.copy(
                        processing = ProcessingStatus(),
                        statusBanner = StatusBanner(
                            title = "Segmentation Unavailable",
                            message = "ML Kit Subject Segmentation requires Google Play Services model download, or install a local ONNX/LiteRT model (e.g. U2Net-P / ISNet-Anime) in Model Manager. You can also paint the subject with Manual Keep Brush below. (${e.localizedMessage ?: "Error"})",
                            isWarning = true,
                            actionLabel = "Open Model Manager",
                            actionType = BannerActionType.OPEN_MODEL_MANAGER
                        )
                    )
                }
            }
        }
    }

    /**
     * Applies user's manually brushed mask as the foreground keep region for background removal.
     */
    fun applyManualForegroundMask() {
        val current = _editorState.value
        val edited = current.editedBitmap ?: return
        val mask = current.maskBitmap ?: return
        if (!current.hasMaskContent && !checkMaskHasPixels(mask)) {
            _editorState.update {
                it.copy(
                    statusBanner = StatusBanner(
                        title = "Paint Subject Mask First",
                        message = "Use the Keep Brush to paint over the character/subject you want to keep, then tap Apply Manual Cutout.",
                        isWarning = true
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            pushUndoSnapshot()
            val composited = SegmentationEngine.compositeWithBackground(
                source = edited,
                foregroundMask = mask,
                replacementMode = current.bgReplacementMode,
                customBgColorArgb = current.customBgColorArgb
            )
            _editorState.update {
                it.copy(
                    editedBitmap = composited,
                    maskBitmap = MemoryManager.createEmptyMask(composited.width, composited.height),
                    hasMaskContent = false,
                    statusBanner = StatusBanner(
                        title = "Manual Background Cutout Applied",
                        message = "Foreground preserved using your manual mask. Export as PNG for alpha transparency."
                    )
                )
            }
            saveToHistoryInternal("Manual BG Cutout")
        }
    }

    /**
     * Smart 16:9 Outpainting (1280x720 or 1920x1080).
     * Never stretches original image; keeps original character/panel pixels intact.
     * Uses installed local ONNX outpainting/inpainting model when present, or clearly reports
     * hardware/model requirement without faking AI generation.
     */
    fun runSmart16By9Outpainting() {
        val current = _editorState.value
        val source = current.editedBitmap ?: current.originalBitmap ?: return
        val outpaintModel = installedModels.value[ModelCapabilitySlot.OUTPAINT_GENERATION.slotKey]
            ?: installedModels.value[ModelCapabilitySlot.LAMA_INPAINTING.slotKey]

        if (outpaintModel == null || outpaintModel.modelFormat != "ONNX") {
            _editorState.update {
                it.copy(
                    statusBanner = StatusBanner(
                        title = "Local Outpainting Model Not Installed",
                        message = "True neural 16:9 outpainting requires a local ONNX inpainting/generation model. Full Stable Diffusion 1.5 exceeds 6GB RAM without mobile quantization; install a compatible tiled ONNX model (e.g. LaMa ONNX) in Model Manager, or run a Non-AI Edge-Aware Canvas Preview.",
                        isWarning = true,
                        actionLabel = "Open Model Manager",
                        actionType = BannerActionType.OPEN_MODEL_MANAGER
                    )
                )
            }
            return
        }

        val curSettings = settings.value
        activeProcessingJob?.cancel()
        activeProcessingJob = viewModelScope.launch {
            try {
                pushUndoSnapshot()
                val expanded = OutpaintCanvasEngine.runLocalOutpainting(
                    source = source,
                    preset = current.outpaintPreset,
                    anchor = current.subjectAnchor,
                    installedOutpaintOrInpaintModel = outpaintModel,
                    threads = curSettings.inferenceThreads,
                    autoUnload = curSettings.autoUnloadModelAfterInference
                ) { prog, msg ->
                    _editorState.update {
                        it.copy(
                            processing = ProcessingStatus(
                                isRunning = true,
                                stageTitle = "16:9 Local Outpainting (${current.outpaintPreset.width}×${current.outpaintPreset.height})",
                                detailMessage = msg,
                                progress = prog,
                                canCancel = true
                            )
                        )
                    }
                }
                val newMask = MemoryManager.createEmptyMask(expanded.width, expanded.height)
                _editorState.update {
                    it.copy(
                        editedBitmap = expanded,
                        maskBitmap = newMask,
                        hasMaskContent = false,
                        processing = ProcessingStatus(),
                        statusBanner = StatusBanner(
                            title = "16:9 Canvas Expanded (${expanded.width}×${expanded.height})",
                            message = "Original image region preserved with zero stretching via ${outpaintModel.displayName}."
                        )
                    )
                }
                saveToHistoryInternal("16:9 Outpaint (${current.outpaintPreset.width}x${current.outpaintPreset.height})")
                refreshMemoryStats()
            } catch (e: CancellationException) {
                undo()
                _editorState.update { it.copy(processing = ProcessingStatus()) }
            } catch (e: OutOfMemoryError) {
                System.gc()
                undo()
                _editorState.update {
                    it.copy(
                        processing = ProcessingStatus(),
                        statusBanner = StatusBanner(
                            title = "Out of Memory",
                            message = "Try 1280×720 preset or lower Max Working Dimension in Settings for 6GB RAM.",
                            isError = true
                        )
                    )
                }
            } catch (e: Throwable) {
                undo()
                _editorState.update {
                    it.copy(
                        processing = ProcessingStatus(),
                        statusBanner = StatusBanner(
                            title = "Outpainting Failed",
                            message = e.message ?: "Model execution error.",
                            isError = true
                        )
                    )
                }
            }
        }
    }

    fun runExplicitNonAiEdgeExtendPreview() {
        val current = _editorState.value
        val source = current.editedBitmap ?: current.originalBitmap ?: return
        activeProcessingJob?.cancel()
        activeProcessingJob = viewModelScope.launch {
            try {
                pushUndoSnapshot()
                val expanded = OutpaintCanvasEngine.runNonAiEdgeExtensionPreview(
                    source = source,
                    preset = current.outpaintPreset,
                    anchor = current.subjectAnchor
                ) { prog, msg ->
                    _editorState.update {
                        it.copy(
                            processing = ProcessingStatus(
                                isRunning = true,
                                stageTitle = "Non-AI 16:9 Canvas Extension",
                                detailMessage = msg,
                                progress = prog,
                                canCancel = true
                            )
                        )
                    }
                }
                val newMask = MemoryManager.createEmptyMask(expanded.width, expanded.height)
                _editorState.update {
                    it.copy(
                        editedBitmap = expanded,
                        maskBitmap = newMask,
                        hasMaskContent = false,
                        processing = ProcessingStatus(),
                        statusBanner = StatusBanner(
                            title = "16:9 Edge Extension Preview (Non-AI)",
                            message = "Original image preserved unstretched at ${expanded.width}×${expanded.height}. Install an ONNX model for AI texture generation."
                        )
                    )
                }
                saveToHistoryInternal("16:9 Canvas Layout (Non-AI)")
                refreshMemoryStats()
            } catch (e: CancellationException) {
                undo()
                _editorState.update { it.copy(processing = ProcessingStatus()) }
            }
        }
    }

    fun cancelActiveProcessing() {
        activeProcessingJob?.cancel()
        _editorState.update { it.copy(processing = ProcessingStatus()) }
    }

    fun undo() {
        val snapshot = undoStack.removeLastOrNull() ?: return
        val current = _editorState.value
        val curEdited = current.editedBitmap
        val curMask = current.maskBitmap
        if (curEdited != null && curMask != null) {
            redoStack.addLast(EditorUndoSnapshot(curEdited, curMask))
        }
        _editorState.update {
            it.copy(
                editedBitmap = snapshot.editedBitmap,
                maskBitmap = snapshot.maskBitmap,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                hasMaskContent = checkMaskHasPixels(snapshot.maskBitmap)
            )
        }
    }

    fun redo() {
        val snapshot = redoStack.removeLastOrNull() ?: return
        val current = _editorState.value
        val curEdited = current.editedBitmap
        val curMask = current.maskBitmap
        if (curEdited != null && curMask != null) {
            undoStack.addLast(EditorUndoSnapshot(curEdited, curMask))
        }
        _editorState.update {
            it.copy(
                editedBitmap = snapshot.editedBitmap,
                maskBitmap = snapshot.maskBitmap,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                hasMaskContent = checkMaskHasPixels(snapshot.maskBitmap)
            )
        }
    }

    fun resetToOriginal() {
        val orig = _editorState.value.originalBitmap ?: return
        pushUndoSnapshot()
        val copy = orig.copy(Bitmap.Config.ARGB_8888, true)
        val mask = MemoryManager.createEmptyMask(copy.width, copy.height)
        _editorState.update {
            it.copy(
                editedBitmap = copy,
                maskBitmap = mask,
                hasMaskContent = false,
                statusBanner = StatusBanner("Reset", "Restored original untouched image.")
            )
        }
    }

    fun saveCurrentToHistory() {
        viewModelScope.launch {
            saveToHistoryInternal("Manual Save")
            _uiToastMessage.value = "Saved snapshot to Image History."
        }
    }

    fun exportEditedImageToUri(uri: Uri, format: ExportFormat) {
        val target = _editorState.value.editedBitmap ?: _editorState.value.originalBitmap ?: return
        viewModelScope.launch {
            val ok = MemoryManager.exportBitmapToUri(
                context = context,
                bitmap = target,
                destinationUri = uri,
                format = format,
                jpgQuality = settings.value.jpgQuality
            )
            _uiToastMessage.value = if (ok) {
                "Exported ${target.width}×${target.height} ${format.extension.uppercase()} successfully."
            } else {
                "Failed to export image to selected location."
            }
        }
    }

    fun deleteHistoryItem(item: HistoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { File(item.editedPath).delete() }
            runCatching { File(item.originalPath).delete() }
            dao.deleteHistoryById(item.id)
        }
    }

    // Model Manager Actions
    fun importLocalModel(slot: ModelCapabilitySlot, uri: Uri) {
        activeDownloadJob?.cancel()
        activeDownloadJob = viewModelScope.launch {
            _downloadProgress.value = ModelDownloadProgress(slot.slotKey, 0L, 0L, "Inspecting & validating model...")
            val res = modelManager.importLocalModelFile(slot, uri, settings.value.inferenceThreads)
            _downloadProgress.value = null
            _modelManagerMessage.value = res.fold(
                onSuccess = { "Installed ${it.displayName} (${ModelDownloadManager.formatBytes(it.fileSizeBytes)}). Ready offline." },
                onFailure = { "Import failed: ${it.message}" }
            )
            refreshMemoryStats()
        }
    }

    fun downloadModelFromUrl(slot: ModelCapabilitySlot, url: String) {
        activeDownloadJob?.cancel()
        activeDownloadJob = viewModelScope.launch {
            val res = modelManager.downloadModelFromUrl(slot, url, settings.value.inferenceThreads) { prog ->
                _downloadProgress.value = prog
            }
            _downloadProgress.value = null
            _modelManagerMessage.value = res.fold(
                onSuccess = { "Downloaded & verified ${it.displayName} (${ModelDownloadManager.formatBytes(it.fileSizeBytes)}). Ready offline." },
                onFailure = { "Download failed: ${it.message}" }
            )
            refreshMemoryStats()
        }
    }

    fun cancelModelDownload() {
        activeDownloadJob?.cancel()
        _downloadProgress.value = null
        _modelManagerMessage.value = "Model installation cancelled."
    }

    fun deleteInstalledModel(slotKey: String) {
        viewModelScope.launch {
            modelManager.deleteModel(slotKey)
            _modelManagerMessage.value = "Model removed from local storage."
            refreshMemoryStats()
        }
    }

    // Settings mutations
    fun updateDefaultPreset(preset: OutpaintPreset) = viewModelScope.launch {
        settingsRepo.setDefaultOutpaintPreset(preset)
    }

    fun updateDefaultExportFormat(format: ExportFormat) = viewModelScope.launch {
        settingsRepo.setDefaultExportFormat(format)
    }

    fun updateMaxWorkingDimension(maxDim: Int) = viewModelScope.launch {
        settingsRepo.setMaxWorkingDimension(maxDim)
    }

    fun updateAutoUnload(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setAutoUnloadModel(enabled)
    }

    fun updateInferenceThreads(threads: Int) = viewModelScope.launch {
        settingsRepo.setInferenceThreads(threads)
    }

    private suspend fun saveToHistoryInternal(operationTag: String) {
        val current = _editorState.value
        val orig = current.originalBitmap ?: return
        val edited = current.editedBitmap ?: return
        val existingId = current.projectId
        val stamp = existingId ?: System.currentTimeMillis()
        val origPath = MemoryManager.saveBitmapToPrivatePng(context, orig, "history", "orig_$stamp.png")
        val editPath = MemoryManager.saveBitmapToPrivatePng(context, edited, "history", "edit_$stamp.png")
        val row = HistoryEntity(
            id = existingId ?: 0L,
            title = current.projectTitle,
            originalPath = origPath,
            editedPath = editPath,
            operationTag = operationTag,
            width = edited.width,
            height = edited.height,
            promptUsed = current.outpaintPrompt,
            createdAt = if (existingId != null) stamp else System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val savedId = withContext(Dispatchers.IO) { dao.insertOrUpdateHistory(row) }
        _editorState.update { it.copy(projectId = savedId) }
    }

    private fun pushUndoSnapshot() {
        val current = _editorState.value
        val edited = current.editedBitmap ?: return
        val mask = current.maskBitmap ?: return
        if (undoStack.size >= maxUndoDepth) {
            undoStack.removeFirst()
        }
        undoStack.addLast(
            EditorUndoSnapshot(
                editedBitmap = edited.copy(Bitmap.Config.ARGB_8888, true),
                maskBitmap = mask.copy(Bitmap.Config.ARGB_8888, true)
            )
        )
        redoStack.clear()
        _editorState.update {
            it.copy(canUndo = true, canRedo = false)
        }
    }

    private fun checkMaskHasPixels(mask: Bitmap): Boolean {
        val step = 6
        for (y in 0 until mask.height step step) {
            for (x in 0 until mask.width step step) {
                if (Color.alpha(mask.getPixel(x, y)) > 25) return true
            }
        }
        return false
    }
}
