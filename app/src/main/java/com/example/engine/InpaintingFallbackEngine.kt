package com.example.engine

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic, explicitly non-AI structural patch/diffusion fill.
 * Only executed when the user explicitly opts into the non-AI fallback after being
 * informed that no neural inpainting model (LaMa ONNX / LiteRT) is installed.
 */
object InpaintingFallbackEngine {

    suspend fun runStructuralFill(
        source: Bitmap,
        mask: Bitmap,
        onProgress: (Float, String) -> Unit
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = source.width
        val h = source.height
        val srcPixels = IntArray(w * h)
        val maskPixels = IntArray(w * h)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)
        mask.getPixels(maskPixels, 0, w, 0, 0, w, h)

        val isMasked = BooleanArray(w * h)
        var maskedCount = 0
        for (i in maskPixels.indices) {
            if (Color.alpha(maskPixels[i]) > 25) {
                isMasked[i] = true
                maskedCount++
            }
        }

        if (maskedCount == 0) return@withContext source.copy(Bitmap.Config.ARGB_8888, true)

        val workR = FloatArray(w * h)
        val workG = FloatArray(w * h)
        val workB = FloatArray(w * h)
        val known = BooleanArray(w * h)

        for (i in srcPixels.indices) {
            val c = srcPixels[i]
            workR[i] = Color.red(c).toFloat()
            workG[i] = Color.green(c).toFloat()
            workB[i] = Color.blue(c).toFloat()
            known[i] = !isMasked[i]
        }

        // Phase 1: Onion-peel inward concentric boundary propagation
        var remaining = maskedCount
        var ring = 0
        val frontier = IntArray(w * h)
        while (remaining > 0 && ring < 400) {
            coroutineContext.ensureActive()
            var frontierCount = 0
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val idx = y * w + x
                    if (known[idx]) continue
                    var sumR = 0f
                    var sumG = 0f
                    var sumB = 0f
                    var wSum = 0f

                    for (dy in -1..1) {
                        val ny = y + dy
                        if (ny !in 0 until h) continue
                        for (dx in -1..1) {
                            val nx = x + dx
                            if (nx !in 0 until w) continue
                            val nIdx = ny * w + nx
                            if (known[nIdx]) {
                                val weight = if (dx == 0 || dy == 0) 1.0f else 0.707f
                                sumR += workR[nIdx] * weight
                                sumG += workG[nIdx] * weight
                                sumB += workB[nIdx] * weight
                                wSum += weight
                            }
                        }
                    }

                    if (wSum > 0f) {
                        workR[idx] = sumR / wSum
                        workG[idx] = sumG / wSum
                        workB[idx] = sumB / wSum
                        frontier[frontierCount++] = idx
                    }
                }
            }
            if (frontierCount == 0) break
            for (k in 0 until frontierCount) {
                known[frontier[k]] = true
            }
            remaining -= frontierCount
            ring++
            if (ring % 4 == 0) {
                val frac = ((maskedCount - remaining).toFloat() / maskedCount.toFloat()).coerceIn(0f, 0.8f)
                onProgress(frac, "Inward structural fill pass $ring...")
            }
        }

        // Phase 2: Harmonic smoothing inside masked region only (preserves unmasked pixels 100%)
        val nextR = workR.copyOf()
        val nextG = workG.copyOf()
        val nextB = workB.copyOf()
        val iterations = 10
        for (iter in 0 until iterations) {
            coroutineContext.ensureActive()
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val idx = y * w + x
                    if (!isMasked[idx]) continue
                    nextR[idx] = (workR[idx - 1] + workR[idx + 1] + workR[idx - w] + workR[idx + w]) * 0.25f
                    nextG[idx] = (workG[idx - 1] + workG[idx + 1] + workG[idx - w] + workG[idx + w]) * 0.25f
                    nextB[idx] = (workB[idx - 1] + workB[idx + 1] + workB[idx - w] + workB[idx + w]) * 0.25f
                }
            }
            System.arraycopy(nextR, 0, workR, 0, workR.size)
            System.arraycopy(nextG, 0, workG, 0, workG.size)
            System.arraycopy(nextB, 0, workB, 0, workB.size)
            onProgress(0.8f + (iter + 1) * 0.02f, "Blending boundary seams...")
        }

        val outPixels = srcPixels.copyOf()
        for (i in outPixels.indices) {
            if (isMasked[i]) {
                val r = workR[i].toInt().coerceIn(0, 255)
                val g = workG[i].toInt().coerceIn(0, 255)
                val b = workB[i].toInt().coerceIn(0, 255)
                outPixels[i] = Color.argb(Color.alpha(srcPixels[i]), r, g, b)
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        result
    }
}
