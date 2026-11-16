package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AxionDatabase
import com.example.data.db.HistoryEntity
import com.example.data.db.LocalModelEntity
import com.example.domain.model.BackgroundReplacementMode
import com.example.domain.model.DeviceMemorySnapshot
import com.example.domain.model.EditorUiState
import com.example.domain.model.OutpaintPreset
import com.example.domain.model.SubjectAnchor
import com.example.engine.InpaintingFallbackEngine
import com.example.engine.OutpaintCanvasEngine
import com.example.engine.SegmentationEngine
import com.example.ui.screens.ModelManagerScreen
import com.example.ui.screens.StudioHomeScreen
import com.example.ui.theme.AxionTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AxionEngineRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun prepare16By9Canvas_preservesAspectRatioWithoutStretching() = runTest {
        // Vertical 400x800 manga panel -> should fit cleanly in 1280x720 with width = 360, height = 720
        val src = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(40, 120, 220))
        }
        val layout = OutpaintCanvasEngine.prepare16By9Canvas(
            source = src,
            preset = OutpaintPreset.HD_720P,
            anchor = SubjectAnchor.CENTER
        )
        assertEquals(1280, layout.canvasBitmap.width)
        assertEquals(720, layout.canvasBitmap.height)
        assertEquals(360, layout.placementRect.width())
        assertEquals(720, layout.placementRect.height())
        assertEquals(460, layout.placementRect.left)
        // Core original pixel preserved
        assertEquals(Color.rgb(40, 120, 220), layout.canvasBitmap.getPixel(640, 360))
        // Core inside placementRect has mask alpha == 0
        assertEquals(0, Color.alpha(layout.outpaintMask.getPixel(640, 360)))
        // Exterior margin has mask alpha == 255
        assertEquals(255, Color.alpha(layout.outpaintMask.getPixel(10, 360)))
    }

    @Test
    fun structuralInpaintingFallback_preservesUnmaskedPixelsAndFillsMask() = runTest {
        val src = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(20, 80, 160))
        }
        val mask = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        for (y in 15..24) {
            for (x in 15..24) {
                src.setPixel(x, y, Color.WHITE)
                mask.setPixel(x, y, Color.argb(255, 255, 0, 0))
            }
        }
        val filled = InpaintingFallbackEngine.runStructuralFill(src, mask) { _, _ -> }
        // Unmasked border pixel must remain 100% identical
        assertEquals(Color.rgb(20, 80, 160), filled.getPixel(2, 2))
        // Masked center pixel should have been reconstructed close to surrounding blue (not white)
        val center = filled.getPixel(20, 20)
        assertNotEquals(Color.WHITE, center)
        assertTrue(Color.blue(center) > 120)
    }

    @Test
    fun segmentationComposite_supportsTransparentPngAlpha() = runTest {
        val src = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(255, 100, 50))
        }
        val mask = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        for (y in 5 until 15) {
            for (x in 5 until 15) {
                mask.setPixel(x, y, Color.argb(255, 255, 255, 255))
            }
        }
        val result = SegmentationEngine.compositeWithBackground(
            source = src,
            foregroundMask = mask,
            replacementMode = BackgroundReplacementMode.TRANSPARENT,
            customBgColorArgb = Color.BLACK
        )
        assertEquals(0, Color.alpha(result.getPixel(0, 0)))
        assertEquals(255, Color.alpha(result.getPixel(10, 10)))
        assertEquals(255, Color.red(result.getPixel(10, 10)))
    }

    @Test
    fun roomDatabase_persistsHistoryAndModelMetadata() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AxionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.axionDao()
        dao.insertOrUpdateHistory(
            HistoryEntity(
                title = "Chapter42_Panel3",
                originalPath = "/tmp/orig.png",
                editedPath = "/tmp/edit.png",
                operationTag = "16:9 Outpaint",
                width = 1280,
                height = 720
            )
        )
        dao.upsertLocalModel(
            LocalModelEntity(
                slotKey = "lama_inpainting",
                displayName = "lama_fp16.onnx",
                modelFormat = "ONNX",
                filePath = "/tmp/lama.onnx",
                fileSizeBytes = 1024L,
                sha256 = "abcd1234",
                inputSignature = "image[1x3x512x512]",
                outputSignature = "output[1x3x512x512]",
                isInstalled = true,
                installedAt = 123L,
                sourceInfo = "Local"
            )
        )
        val history = dao.observeHistory().first()
        val models = dao.observeLocalModels().first()
        assertEquals(1, history.size)
        assertEquals("Chapter42_Panel3", history[0].title)
        assertEquals(1, models.size)
        assertEquals("lama_fp16.onnx", models[0].displayName)
        db.close()
    }

    @Test
    fun studioHomeScreen_rendersAllEightCreatorWorkflowTiles() {
        composeRule.setContent {
            AxionTheme {
                StudioHomeScreen(
                    editorState = EditorUiState(),
                    memorySnapshot = DeviceMemorySnapshot(6000, 2400, 512, false),
                    installedModels = emptyMap(),
                    historyCount = 0,
                    onImportImage = {},
                    onOpenAnalysis = {},
                    onOpenEditorMode = {},
                    onOpenHistory = {},
                    onOpenModelManager = {},
                    onOpenSettings = {}
                )
            }
        }
        composeRule.waitForIdle()
        listOf(
            "home_import_image",
            "home_ai_analysis",
            "home_remove_text",
            "home_remove_bg",
            "home_ai_outpainting",
            "home_image_history",
            "home_model_manager",
            "home_settings"
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertExists()
        }
    }

    @Test
    fun modelManagerScreen_honestlyDisplaysSlotsAndRamTelemetry() {
        composeRule.setContent {
            AxionTheme {
                ModelManagerScreen(
                    memorySnapshot = DeviceMemorySnapshot(6144, 2800, 512, false),
                    installedModels = emptyMap(),
                    downloadProgress = null,
                    statusMessage = null,
                    onRefreshMemory = {},
                    onImportLocalModel = {},
                    onDownloadModelUrl = { _, _ -> },
                    onCancelDownload = {},
                    onDeleteModel = {},
                    onDismissStatusMessage = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("model_manager_list")
            .performScrollToNode(hasTestTag("model_slot_lama_inpainting"))
        composeRule.onNodeWithTag("model_slot_lama_inpainting").assertIsDisplayed()
        composeRule.onNodeWithTag("model_manager_list")
            .performScrollToNode(hasTestTag("model_slot_foreground_segmentation"))
        composeRule.onNodeWithTag("model_slot_foreground_segmentation").assertIsDisplayed()
        composeRule.onNodeWithTag("model_manager_list")
            .performScrollToNode(hasTestTag("model_slot_outpaint_generation"))
        composeRule.onNodeWithTag("model_slot_outpaint_generation").assertIsDisplayed()
    }
}
