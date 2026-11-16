package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.model.BannerActionType
import com.example.domain.model.EditorWorkflowMode
import com.example.domain.model.ExportFormat
import com.example.domain.model.ModelCapabilitySlot
import com.example.ui.components.ProcessingOverlay
import com.example.ui.navigation.AxionRoute
import com.example.ui.screens.AiAnalysisScreen
import com.example.ui.screens.ImageEditorScreen
import com.example.ui.screens.ImageHistoryScreen
import com.example.ui.screens.ModelManagerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudioHomeScreen
import com.example.ui.theme.AxionCyan
import com.example.ui.theme.AxionTheme
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AxionViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AxionTheme {
                AxionAppRoot()
            }
        }
    }
}

private data class TopNavItem(
    val label: String,
    val icon: ImageVector,
    val route: AxionRoute
)

@Composable
fun AxionAppRoot(
    viewModel: AxionViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val memorySnapshot by viewModel.memorySnapshot.collectAsStateWithLifecycle()
    val installedModels by viewModel.installedModels.collectAsStateWithLifecycle()
    val historyItems by viewModel.historyItems.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val modelManagerMessage by viewModel.modelManagerMessage.collectAsStateWithLifecycle()
    val toastMessage by viewModel.uiToastMessage.collectAsStateWithLifecycle()

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToastMessage()
        }
    }

    var pendingImportMode by remember { mutableStateOf(EditorWorkflowMode.INSPECT) }
    var pendingNavigateToAnalysis by remember { mutableStateOf(false) }

    // Zero-permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            viewModel.importImageFromUri(it, targetMode = pendingImportMode) {
                if (pendingNavigateToAnalysis) {
                    pendingNavigateToAnalysis = false
                    navController.navigate(AxionRoute.AiAnalysis) { launchSingleTop = true }
                } else {
                    navController.navigate(AxionRoute.ImageEditor) { launchSingleTop = true }
                }
            }
        }
    }

    // SAF OpenDocument for importing local .onnx / .tflite weights
    var pendingModelSlot by remember { mutableStateOf<ModelCapabilitySlot?>(null) }
    val modelFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val slot = pendingModelSlot
        if (uri != null && slot != null) {
            viewModel.importLocalModel(slot, uri)
        }
        pendingModelSlot = null
    }

    // SAF CreateDocument for lossless PNG / JPG export
    var pendingExportFormat by remember { mutableStateOf(ExportFormat.PNG) }
    val exportPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        uri?.let { viewModel.exportEditedImageToUri(it, ExportFormat.PNG) }
    }
    val exportJpgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri ->
        uri?.let { viewModel.exportEditedImageToUri(it, ExportFormat.JPG) }
    }

    fun launchPhotoPicker(mode: EditorWorkflowMode, goAnalysis: Boolean = false) {
        pendingImportMode = mode
        pendingNavigateToAnalysis = goAnalysis
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun launchExport(format: ExportFormat) {
        pendingExportFormat = format
        val fileName = "${editorState.projectTitle.replace(" ", "_")}_axion.${format.extension}"
        if (format == ExportFormat.PNG) {
            exportPngLauncher.launch(fileName)
        } else {
            exportJpgLauncher.launch(fileName)
        }
    }

    val navItems = listOf(
        TopNavItem("Studio", Icons.Rounded.Home, AxionRoute.StudioHome),
        TopNavItem("Editor", Icons.Rounded.AutoFixHigh, AxionRoute.ImageEditor),
        TopNavItem("History", Icons.Rounded.History, AxionRoute.ImageHistory),
        TopNavItem("Models", Icons.Rounded.Hub, AxionRoute.ModelManager),
        TopNavItem("Settings", Icons.Rounded.Settings, AxionRoute.Settings)
    )

    val currentDest = backStackEntry?.destination

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 680.dp

        Scaffold(
            containerColor = StudioObsidian,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(containerColor = StudioSurface) {
                        navItems.forEach { item ->
                            val selected = currentDest?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AxionCyan,
                                    selectedTextColor = AxionCyan,
                                    indicatorColor = AxionCyan.copy(alpha = 0.16f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(containerColor = StudioSurface) {
                        navItems.forEach { item ->
                            val selected = currentDest?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = AxionCyan,
                                    selectedTextColor = AxionCyan,
                                    indicatorColor = AxionCyan.copy(alpha = 0.16f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = AxionRoute.StudioHome
                    ) {
                        composable<AxionRoute.StudioHome> {
                            StudioHomeScreen(
                                editorState = editorState,
                                memorySnapshot = memorySnapshot,
                                installedModels = installedModels,
                                historyCount = historyItems.size,
                                onImportImage = { launchPhotoPicker(EditorWorkflowMode.INSPECT) },
                                onOpenAnalysis = {
                                    if (editorState.editedBitmap == null) {
                                        launchPhotoPicker(EditorWorkflowMode.INSPECT, goAnalysis = true)
                                    } else {
                                        navController.navigate(AxionRoute.AiAnalysis) { launchSingleTop = true }
                                    }
                                },
                                onOpenEditorMode = { mode ->
                                    viewModel.setWorkflowMode(mode)
                                    navController.navigate(AxionRoute.ImageEditor) { launchSingleTop = true }
                                },
                                onOpenHistory = {
                                    navController.navigate(AxionRoute.ImageHistory) { launchSingleTop = true }
                                },
                                onOpenModelManager = {
                                    navController.navigate(AxionRoute.ModelManager) { launchSingleTop = true }
                                },
                                onOpenSettings = {
                                    navController.navigate(AxionRoute.Settings) { launchSingleTop = true }
                                }
                            )
                        }

                        composable<AxionRoute.ImageEditor> {
                            ImageEditorScreen(
                                state = editorState,
                                onImportImage = { launchPhotoPicker(editorState.workflowMode) },
                                onSetWorkflowMode = viewModel::setWorkflowMode,
                                onSetToolMode = viewModel::setToolMode,
                                onSetBrushRadius = viewModel::setBrushRadius,
                                onApplyStroke = viewModel::applyMaskStroke,
                                onClearMask = viewModel::clearMask,
                                onToggleMaskOverlay = viewModel::toggleMaskOverlay,
                                onSetShowOriginal = viewModel::setShowOriginalPreview,
                                onToggleCompareSplit = viewModel::toggleCompareSplitMode,
                                onSetCompareSplitFraction = viewModel::setCompareSplitFraction,
                                onUndo = viewModel::undo,
                                onRedo = viewModel::redo,
                                onReset = viewModel::resetToOriginal,
                                onAutoMaskText = viewModel::autoMaskTextAndBubbles,
                                onRunAiTextRemoval = viewModel::runAiTextRemoval,
                                onRunBackgroundRemoval = viewModel::runBackgroundRemoval,
                                onApplyManualForegroundMask = viewModel::applyManualForegroundMask,
                                onSetBgReplacementMode = viewModel::setBgReplacementMode,
                                onSetOutpaintPreset = viewModel::setOutpaintPreset,
                                onSetSubjectAnchor = viewModel::setSubjectAnchor,
                                onSetOutpaintPrompt = viewModel::setOutpaintPrompt,
                                onRunOutpainting = viewModel::runSmart16By9Outpainting,
                                onSaveHistory = viewModel::saveCurrentToHistory,
                                onExportImage = ::launchExport,
                                onBannerAction = { action ->
                                    when (action) {
                                        BannerActionType.OPEN_MODEL_MANAGER -> {
                                            viewModel.dismissBanner()
                                            navController.navigate(AxionRoute.ModelManager) { launchSingleTop = true }
                                        }
                                        BannerActionType.RUN_STRUCTURAL_INPAINT_FALLBACK -> {
                                            viewModel.dismissBanner()
                                            viewModel.runExplicitNonAiStructuralFill()
                                        }
                                        BannerActionType.RUN_EDGE_EXTEND_PREVIEW -> {
                                            viewModel.dismissBanner()
                                            viewModel.runExplicitNonAiEdgeExtendPreview()
                                        }
                                    }
                                },
                                onDismissBanner = viewModel::dismissBanner
                            )
                        }

                        composable<AxionRoute.AiAnalysis> {
                            AiAnalysisScreen(
                                editorState = editorState,
                                onImportImage = { launchPhotoPicker(EditorWorkflowMode.INSPECT, goAnalysis = true) },
                                onReanalyze = viewModel::runAiAnalysis,
                                onAutoMaskAndOpenTextRemoval = {
                                    viewModel.autoMaskTextAndBubbles(includeBubbles = true)
                                    navController.navigate(AxionRoute.ImageEditor) { launchSingleTop = true }
                                },
                                onSendToOutpaint = {
                                    viewModel.setWorkflowMode(EditorWorkflowMode.OUTPAINT_16_9)
                                    navController.navigate(AxionRoute.ImageEditor) { launchSingleTop = true }
                                }
                            )
                        }

                        composable<AxionRoute.ImageHistory> {
                            ImageHistoryScreen(
                                items = historyItems,
                                onReopenItem = { item ->
                                    viewModel.reopenHistoryItem(item) {
                                        navController.navigate(AxionRoute.ImageEditor) { launchSingleTop = true }
                                    }
                                },
                                onDeleteItem = viewModel::deleteHistoryItem
                            )
                        }

                        composable<AxionRoute.ModelManager> {
                            ModelManagerScreen(
                                memorySnapshot = memorySnapshot,
                                installedModels = installedModels,
                                downloadProgress = downloadProgress,
                                statusMessage = modelManagerMessage,
                                onRefreshMemory = viewModel::refreshMemoryStats,
                                onImportLocalModel = { slot ->
                                    pendingModelSlot = slot
                                    modelFilePickerLauncher.launch(arrayOf("*/*"))
                                },
                                onDownloadModelUrl = viewModel::downloadModelFromUrl,
                                onCancelDownload = viewModel::cancelModelDownload,
                                onDeleteModel = viewModel::deleteInstalledModel,
                                onDismissStatusMessage = viewModel::clearModelManagerMessage
                            )
                        }

                        composable<AxionRoute.Settings> {
                            SettingsScreen(
                                settings = settings,
                                onUpdatePreset = viewModel::updateDefaultPreset,
                                onUpdateExportFormat = viewModel::updateDefaultExportFormat,
                                onUpdateMaxDimension = viewModel::updateMaxWorkingDimension,
                                onUpdateAutoUnload = viewModel::updateAutoUnload,
                                onUpdateThreads = viewModel::updateInferenceThreads
                            )
                        }
                    }

                    ProcessingOverlay(
                        status = editorState.processing,
                        onCancel = viewModel::cancelActiveProcessing
                    )
                }
            }
        }
    }
}
