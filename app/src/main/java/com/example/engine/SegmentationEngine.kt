package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.data.db.LocalModelEntity
import com.example.domain.model.BackgroundReplacementMode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

data class SegmentationOutcome(
    val foregroundMask: Bitmap,
    val compositedBitmap: Bitmap,
    val engineUsed: String
)

object SegmentationEngine {

    /**
     * Runs real local subject segmentation.
     * Priority 1: User-installed ONNX or LiteRT segmentation model.
     * Priority 2: ML Kit On-Device Subject Segmentation.
     */
    suspend fun segmentForeground(
        source: Bitmap,
        installedModel: LocalModelEntity?,
        threads: Int,
        autoUnload: Boolean,
        replacementMode: BackgroundReplacementMode,
        customBgColorArgb: Int,
        onProgress: (Float, String) -> Unit
    ): SegmentationOutcome {
        val alphaMask: Bitmap
        val engineUsed: String

        if (installedModel != null && installedModel.isInstalled) {
            if (installedModel.modelFormat == "ONNX") {
                alphaMask = OnnxModelRunner.runSegmentation(
                    modelPath = installedModel.filePath,
                    source = source,
                    threads = threads,
                    autoUnload = autoUnload,
                    onProgress = onProgress
                )
                engineUsed = "Local ONNX (${installedModel.displayName})"
            } else {
                alphaMask = LiteRtModelRunner.runSegmentation(
                    modelPath = installedModel.filePath,
                    source = source,
                    threads = threads,
                    autoUnload = autoUnload,
                    onProgress = onProgress
                )
                engineUsed = "Local LiteRT (${installedModel.displayName})"
            }
        } else {
            onProgress(0.25f, "Running ML Kit on-device Subject Segmentation...")
            alphaMask = runMlKitSubjectSegmentation(source)
            engineUsed = "ML Kit Subject Segmentation"
        }

        onProgress(0.88f, "Compositing background transparency...")
        val composited = compositeWithBackground(
            source = source,
            foregroundMask = alphaMask,
            replacementMode = replacementMode,
            customBgColorArgb = customBgColorArgb
        )
        return SegmentationOutcome(
            foregroundMask = alphaMask,
            compositedBitmap = composited,
            engineUsed = engineUsed
        )
    }

    private suspend fun runMlKitSubjectSegmentation(source: Bitmap): Bitmap =
        suspendCancellableCoroutine { cont ->
            val options = SubjectSegmenterOptions.Builder()
                .enableForegroundConfidenceMask()
                .build()
            val client = SubjectSegmentation.getClient(options)
            val input = InputImage.fromBitmap(source, 0)
            client.process(input)
                .addOnSuccessListener { result ->
                    val maskBuf = result.foregroundConfidenceMask
                    val w = source.width
                    val h = source.height
                    val outPx = IntArray(w * h)
                    if (maskBuf != null) {
                        maskBuf.rewind()
                        for (i in 0 until w * h) {
                            val conf = if (maskBuf.hasRemaining()) maskBuf.get().coerceIn(0f, 1f) else 0f
                            val alpha = (conf * 255f).roundToInt().coerceIn(0, 255)
                            outPx[i] = Color.argb(alpha, 255, 255, 255)
                        }
                    }
                    client.close()
                    val maskBmp = Bitmap.createBitmap(outPx, w, h, Bitmap.Config.ARGB_8888)
                    if (cont.isActive) cont.resume(maskBmp)
                }
                .addOnFailureListener { exc ->
                    client.close()
                    if (cont.isActive) cont.resumeWithException(exc)
                }
        }

    suspend fun compositeWithBackground(
        source: Bitmap,
        foregroundMask: Bitmap,
        replacementMode: BackgroundReplacementMode,
        customBgColorArgb: Int
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = source.width
        val h = source.height
        val scaledMask = if (foregroundMask.width != w || foregroundMask.height != h) {
            Bitmap.createScaledBitmap(foregroundMask, w, h, true)
        } else foregroundMask

        val srcPx = IntArray(w * h)
        val mskPx = IntArray(w * h)
        source.getPixels(srcPx, 0, w, 0, 0, w, h)
        scaledMask.getPixels(mskPx, 0, w, 0, 0, w, h)
        if (scaledMask != foregroundMask) scaledMask.recycle()

        val fgPixels = IntArray(w * h)
        for (i in srcPx.indices) {
            val c = srcPx[i]
            val maskAlpha = Color.alpha(mskPx[i])
            val finalAlpha = (Color.alpha(c) * maskAlpha) / 255
            fgPixels[i] = Color.argb(finalAlpha, Color.red(c), Color.green(c), Color.blue(c))
        }
        val fgBitmap = Bitmap.createBitmap(fgPixels, w, h, Bitmap.Config.ARGB_8888)

        if (replacementMode == BackgroundReplacementMode.TRANSPARENT) {
            return@withContext fgBitmap
        }

        val bgColor = when (replacementMode) {
            BackgroundReplacementMode.SOLID_DARK -> 0xFF090D16.toInt()
            BackgroundReplacementMode.SOLID_WHITE -> Color.WHITE
            BackgroundReplacementMode.CHROMA_GREEN -> 0xFF00B140.toInt()
            BackgroundReplacementMode.CUSTOM_COLOR -> customBgColorArgb
            BackgroundReplacementMode.TRANSPARENT -> Color.TRANSPARENT
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(bgColor)
        canvas.drawBitmap(fgBitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
        fgBitmap.recycle()
        result
    }
}
