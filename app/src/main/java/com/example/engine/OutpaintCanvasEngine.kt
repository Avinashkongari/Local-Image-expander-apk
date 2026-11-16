package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.example.data.db.LocalModelEntity
import com.example.domain.model.OutpaintPreset
import com.example.domain.model.SubjectAnchor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.math.roundToInt

data class OutpaintCanvasLayout(
    val canvasBitmap: Bitmap,
    val outpaintMask: Bitmap,
    val placementRect: Rect
)

object OutpaintCanvasEngine {

    /**
     * Places the original image inside a 16:9 canvas (1280x720 or 1920x1080) without
     * EVER stretching or distorting the original aspect ratio.
     * Generates a mask covering the new surrounding canvas regions with a 10px seam feather.
     */
    suspend fun prepare16By9Canvas(
        source: Bitmap,
        preset: OutpaintPreset,
        anchor: SubjectAnchor
    ): OutpaintCanvasLayout = withContext(Dispatchers.Default) {
        val targetW = preset.width
        val targetH = preset.height

        // Fit original inside 16:9 canvas without stretching (preserve exact aspect ratio)
        val scale = min(
            targetW.toFloat() / source.width.toFloat(),
            targetH.toFloat() / source.height.toFloat()
        )
        val drawnW = (source.width * scale).roundToInt().coerceIn(1, targetW)
        val drawnH = (source.height * scale).roundToInt().coerceIn(1, targetH)

        val offsetX = when (anchor) {
            SubjectAnchor.LEFT -> 0
            SubjectAnchor.CENTER -> (targetW - drawnW) / 2
            SubjectAnchor.RIGHT -> targetW - drawnW
        }
        val offsetY = (targetH - drawnH) / 2

        val placementRect = Rect(offsetX, offsetY, offsetX + drawnW, offsetY + drawnH)

        val canvasBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)

        // Pre-seed canvas edges cleanly with nearest edge pixels so tiled inference has smooth color context
        seedEdgeContext(canvasBitmap, source, placementRect)

        // Draw untouched original image cleanly scaled proportionally (no stretch)
        val dstPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(source, null, placementRect, dstPaint)

        // Build outpaint mask: 255 alpha outside placementRect, 0 inside core subject region, soft feather at seam
        val maskBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val maskPx = IntArray(targetW * targetH)
        val featherPx = 10
        for (y in 0 until targetH) {
            for (x in 0 until targetW) {
                val dx = when {
                    x < placementRect.left -> placementRect.left - x
                    x >= placementRect.right -> x - placementRect.right + 1
                    else -> 0
                }
                val dy = when {
                    y < placementRect.top -> placementRect.top - y
                    y >= placementRect.bottom -> y - placementRect.bottom + 1
                    else -> 0
                }
                val distOutside = maxOf(dx, dy)
                val alpha = when {
                    distOutside == 0 -> 0
                    distOutside <= featherPx -> ((distOutside.toFloat() / featherPx.toFloat()) * 255f).roundToInt().coerceIn(0, 255)
                    else -> 255
                }
                maskPx[y * targetW + x] = Color.argb(alpha, 255, 64, 129)
            }
        }
        maskBitmap.setPixels(maskPx, 0, targetW, 0, 0, targetW, targetH)

        OutpaintCanvasLayout(
            canvasBitmap = canvasBitmap,
            outpaintMask = maskBitmap,
            placementRect = placementRect
        )
    }

    private fun seedEdgeContext(
        target: Bitmap,
        source: Bitmap,
        rect: Rect
    ) {
        val tw = target.width
        val th = target.height
        val scaledSrc = Bitmap.createScaledBitmap(source, rect.width().coerceAtLeast(1), rect.height().coerceAtLeast(1), true)
        val srcW = scaledSrc.width
        val srcH = scaledSrc.height
        val srcPx = IntArray(srcW * srcH)
        scaledSrc.getPixels(srcPx, 0, srcW, 0, 0, srcW, srcH)
        scaledSrc.recycle()

        val outPx = IntArray(tw * th)
        for (y in 0 until th) {
            val sy = (y - rect.top).coerceIn(0, srcH - 1)
            for (x in 0 until tw) {
                val sx = (x - rect.left).coerceIn(0, srcW - 1)
                outPx[y * tw + x] = srcPx[sy * srcW + sx]
            }
        }
        target.setPixels(outPx, 0, tw, 0, 0, tw, th)
    }

    /**
     * Executes neural outpainting when a compatible local ONNX model is installed,
     * preserving the original region intact.
     */
    suspend fun runLocalOutpainting(
        source: Bitmap,
        preset: OutpaintPreset,
        anchor: SubjectAnchor,
        installedOutpaintOrInpaintModel: LocalModelEntity,
        threads: Int,
        autoUnload: Boolean,
        onProgress: (Float, String) -> Unit
    ): Bitmap {
        onProgress(0.08f, "Preparing ${preset.width}×${preset.height} 16:9 canvas...")
        val layout = prepare16By9Canvas(source, preset, anchor)
        return try {
            val inferred = OnnxModelRunner.runTiledInpainting(
                modelPath = installedOutpaintOrInpaintModel.filePath,
                source = layout.canvasBitmap,
                mask = layout.outpaintMask,
                threads = threads,
                autoUnload = autoUnload,
                onProgress = onProgress
            )
            // Guarantee original character/panel pixels remain crisp and untouched
            val canvas = Canvas(inferred)
            canvas.drawBitmap(
                source,
                null,
                layout.placementRect,
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            inferred
        } finally {
            layout.canvasBitmap.recycle()
            layout.outpaintMask.recycle()
        }
    }

    /**
     * Explicit Non-AI Edge-Aware Canvas Extension Preview:
     * Mirrors & softly diffuses border environment tones into the 16:9 canvas margins
     * while preserving the central original image region untouched.
     * Clearly labeled as Non-AI Preview in the UI.
     */
    suspend fun runNonAiEdgeExtensionPreview(
        source: Bitmap,
        preset: OutpaintPreset,
        anchor: SubjectAnchor,
        onProgress: (Float, String) -> Unit
    ): Bitmap = withContext(Dispatchers.Default) {
        onProgress(0.15f, "Building ${preset.width}×${preset.height} 16:9 canvas...")
        val layout = prepare16By9Canvas(source, preset, anchor)
        val w = preset.width
        val h = preset.height
        val px = IntArray(w * h)
        layout.canvasBitmap.getPixels(px, 0, w, 0, 0, w, h)

        val rect = layout.placementRect
        val blurPx = px.copyOf()

        // Smooth multi-pass horizontal & vertical box diffusion strictly outside placementRect
        onProgress(0.45f, "Softly extending edge environment...")
        val passes = 12
        val radius = 14
        val temp = IntArray(w * h)

        for (pass in 0 until passes) {
            coroutineContext.ensureActive()
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (x in rect.left until rect.right && y in rect.top until rect.bottom) {
                        temp[y * w + x] = px[y * w + x]
                        continue
                    }
                    var sr = 0
                    var sg = 0
                    var sb = 0
                    var cnt = 0
                    val x0 = (x - radius).coerceAtLeast(0)
                    val x1 = (x + radius).coerceAtMost(w - 1)
                    var sx = x0
                    while (sx <= x1) {
                        val c = blurPx[y * w + sx]
                        sr += Color.red(c)
                        sg += Color.green(c)
                        sb += Color.blue(c)
                        cnt++
                        sx += 3
                    }
                    temp[y * w + x] = Color.argb(255, sr / cnt, sg / cnt, sb / cnt)
                }
            }
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (x in rect.left until rect.right && y in rect.top until rect.bottom) {
                        blurPx[y * w + x] = px[y * w + x]
                        continue
                    }
                    var sr = 0
                    var sg = 0
                    var sb = 0
                    var cnt = 0
                    val y0 = (y - radius).coerceAtLeast(0)
                    val y1 = (y + radius).coerceAtMost(h - 1)
                    var sy = y0
                    while (sy <= y1) {
                        val c = temp[sy * w + x]
                        sr += Color.red(c)
                        sg += Color.green(c)
                        sb += Color.blue(c)
                        cnt++
                        sy += 3
                    }
                    blurPx[y * w + x] = Color.argb(255, sr / cnt, sg / cnt, sb / cnt)
                }
            }
            onProgress(0.45f + (pass + 1) * 0.04f, "Blending 16:9 canvas margins...")
        }

        val out = Bitmap.createBitmap(blurPx, w, h, Bitmap.Config.ARGB_8888)
        // Re-stamp untouched original image with crisp anti-aliased boundary
        Canvas(out).drawBitmap(source, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
        layout.canvasBitmap.recycle()
        layout.outpaintMask.recycle()
        out
    }
}
