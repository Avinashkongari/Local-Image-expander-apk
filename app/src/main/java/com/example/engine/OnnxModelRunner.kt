package com.example.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import kotlin.coroutines.coroutineContext
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class OnnxInspectionResult(
    val isValid: Boolean,
    val inputSignature: String,
    val outputSignature: String,
    val errorMessage: String? = null
)

object OnnxModelRunner {

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    suspend fun inspectModel(modelPath: String, threads: Int = 4): OnnxInspectionResult =
        withContext(Dispatchers.IO) {
            val file = File(modelPath)
            if (!file.exists() || file.length() < 64L) {
                return@withContext OnnxInspectionResult(
                    isValid = false,
                    inputSignature = "",
                    outputSignature = "",
                    errorMessage = "Model file missing or empty."
                )
            }
            var session: OrtSession? = null
            try {
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(threads.coerceIn(1, 4))
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                }
                session = env.createSession(modelPath, opts)
                val inputs = session.inputInfo.entries.joinToString("; ") { (name, node) ->
                    val ti = node.info as? TensorInfo
                    val shape = ti?.shape?.joinToString("x") ?: "?"
                    val type = ti?.type?.name ?: "?"
                    "$name[$shape:$type]"
                }
                val outputs = session.outputInfo.entries.joinToString("; ") { (name, node) ->
                    val ti = node.info as? TensorInfo
                    val shape = ti?.shape?.joinToString("x") ?: "?"
                    val type = ti?.type?.name ?: "?"
                    "$name[$shape:$type]"
                }
                OnnxInspectionResult(
                    isValid = true,
                    inputSignature = inputs,
                    outputSignature = outputs
                )
            } catch (e: Throwable) {
                OnnxInspectionResult(
                    isValid = false,
                    inputSignature = "",
                    outputSignature = "",
                    errorMessage = e.message ?: "Failed to load ONNX graph."
                )
            } finally {
                runCatching { session?.close() }
            }
        }

    /**
     * Runs real ONNX inpainting (e.g., LaMa ONNX) using memory-aware 512x512 tiling.
     * Preserves all unmasked pixels 100% untouched.
     */
    suspend fun runTiledInpainting(
        modelPath: String,
        source: Bitmap,
        mask: Bitmap,
        threads: Int,
        autoUnload: Boolean,
        onProgress: (Float, String) -> Unit
    ): Bitmap = withContext(Dispatchers.Default) {
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(threads.coerceIn(1, 4))
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        onProgress(0.05f, "Loading ONNX inpainting session...")
        val session = env.createSession(modelPath, opts)
        try {
            val inputEntries = session.inputInfo.entries.toList()
            val imageInputEntry = inputEntries.firstOrNull { (name, node) ->
                val shape = (node.info as? TensorInfo)?.shape
                name.contains("img", true) || name.contains("image", true) ||
                    (shape != null && shape.size == 4 && (shape[1] == 3L || shape[3] == 3L))
            } ?: inputEntries.firstOrNull()
                ?: throw IllegalStateException("ONNX model has no inputs.")

            val maskInputEntry = inputEntries.firstOrNull { (name, node) ->
                val shape = (node.info as? TensorInfo)?.shape
                name != imageInputEntry.key && (
                    name.contains("mask", true) ||
                        (shape != null && shape.size == 4 && (shape[1] == 1L || shape[3] == 1L))
                    )
            }

            val imgInfo = imageInputEntry.value.info as? TensorInfo
            val imgShape = imgInfo?.shape ?: longArrayOf(1L, 3L, 512L, 512L)
            val isNchw = imgShape.size == 4 && (imgShape[1] == 3L || imgShape[1] == -1L)
            val modelH = if (imgShape.size == 4) {
                val hDim = if (isNchw) imgShape[2] else imgShape[1]
                if (hDim > 0L) hDim.toInt().coerceIn(128, 512) else 512
            } else 512
            val modelW = if (imgShape.size == 4) {
                val wDim = if (isNchw) imgShape[3] else imgShape[2]
                if (wDim > 0L) wDim.toInt().coerceIn(128, 512) else 512
            } else 512

            val fullW = source.width
            val fullH = source.height
            val outBitmap = source.copy(Bitmap.Config.ARGB_8888, true)

            // Find bounding box of masked pixels to skip empty tiles
            val fullMask = IntArray(fullW * fullH)
            mask.getPixels(fullMask, 0, fullW, 0, 0, fullW, fullH)
            var minX = fullW
            var maxX = -1
            var minY = fullH
            var maxY = -1
            for (y in 0 until fullH) {
                for (x in 0 until fullW) {
                    if (Color.alpha(fullMask[y * fullW + x]) > 25) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }
            if (maxX < minX || maxY < minY) return@withContext outBitmap

            val stepW = max(128, modelW - 64)
            val stepH = max(128, modelH - 64)
            val tiles = mutableListOf<Pair<Int, Int>>()

            var ty = max(0, minY - 32)
            while (ty <= maxY) {
                val clampedY = ty.coerceAtMost(max(0, fullH - modelH))
                var tx = max(0, minX - 32)
                while (tx <= maxX) {
                    val clampedX = tx.coerceAtMost(max(0, fullW - modelW))
                    tiles.add(clampedX to clampedY)
                    if (clampedX + modelW >= fullW || clampedX + modelW > maxX + 32) break
                    tx += stepW
                }
                if (clampedY + modelH >= fullH || clampedY + modelH > maxY + 32) break
                ty += stepH
            }

            val distinctTiles = tiles.distinct()
            distinctTiles.forEachIndexed { tileIdx, (tileX, tileY) ->
                coroutineContext.ensureActive()
                onProgress(
                    0.12f + 0.8f * (tileIdx.toFloat() / distinctTiles.size.coerceAtLeast(1)),
                    "Running ONNX tile ${tileIdx + 1}/${distinctTiles.size} (${modelW}×${modelH})..."
                )

                val cropW = min(modelW, fullW - tileX)
                val cropH = min(modelH, fullH - tileY)
                val patchSrc = Bitmap.createBitmap(outBitmap, tileX, tileY, cropW, cropH)
                val patchMask = Bitmap.createBitmap(mask, tileX, tileY, cropW, cropH)

                val paddedSrc = if (cropW != modelW || cropH != modelH) {
                    Bitmap.createScaledBitmap(patchSrc, modelW, modelH, true)
                } else patchSrc
                val paddedMask = if (cropW != modelW || cropH != modelH) {
                    Bitmap.createScaledBitmap(patchMask, modelW, modelH, false)
                } else patchMask

                val inferredPatch = inferInpaintPatch(
                    session = session,
                    imageName = imageInputEntry.key,
                    maskName = maskInputEntry?.key,
                    isNchw = isNchw,
                    patch = paddedSrc,
                    patchMask = paddedMask,
                    w = modelW,
                    h = modelH
                )

                val restoredPatch = if (cropW != modelW || cropH != modelH) {
                    Bitmap.createScaledBitmap(inferredPatch, cropW, cropH, true)
                } else inferredPatch

                // Composite strictly where mask alpha > 0, preserving unmasked subject pixels
                val dstPixels = IntArray(cropW * cropH)
                val genPixels = IntArray(cropW * cropH)
                val mskPixels = IntArray(cropW * cropH)
                outBitmap.getPixels(dstPixels, 0, cropW, tileX, tileY, cropW, cropH)
                restoredPatch.getPixels(genPixels, 0, cropW, 0, 0, cropW, cropH)
                patchMask.getPixels(mskPixels, 0, cropW, 0, 0, cropW, cropH)

                for (i in dstPixels.indices) {
                    val alpha = Color.alpha(mskPixels[i]) / 255f
                    if (alpha > 0.05f) {
                        val weight = alpha.coerceIn(0f, 1f)
                        val d = dstPixels[i]
                        val g = genPixels[i]
                        val r = (Color.red(d) * (1f - weight) + Color.red(g) * weight).roundToInt().coerceIn(0, 255)
                        val gr = (Color.green(d) * (1f - weight) + Color.green(g) * weight).roundToInt().coerceIn(0, 255)
                        val b = (Color.blue(d) * (1f - weight) + Color.blue(g) * weight).roundToInt().coerceIn(0, 255)
                        dstPixels[i] = Color.argb(Color.alpha(d), r, gr, b)
                    }
                }
                outBitmap.setPixels(dstPixels, 0, cropW, tileX, tileY, cropW, cropH)

                if (paddedSrc != patchSrc) paddedSrc.recycle()
                if (paddedMask != patchMask) paddedMask.recycle()
                if (restoredPatch != inferredPatch) restoredPatch.recycle()
                inferredPatch.recycle()
                patchSrc.recycle()
                patchMask.recycle()
            }

            onProgress(0.96f, if (autoUnload) "Unloading ONNX session to free RAM..." else "Finalizing...")
            outBitmap
        } finally {
            if (autoUnload) {
                runCatching { session.close() }
            }
        }
    }

    private fun inferInpaintPatch(
        session: OrtSession,
        imageName: String,
        maskName: String?,
        isNchw: Boolean,
        patch: Bitmap,
        patchMask: Bitmap,
        w: Int,
        h: Int
    ): Bitmap {
        val pixels = IntArray(w * h)
        val maskPx = IntArray(w * h)
        patch.getPixels(pixels, 0, w, 0, 0, w, h)
        patchMask.getPixels(maskPx, 0, w, 0, 0, w, h)

        val imgFloats = FloatArray(3 * w * h)
        val mskFloats = FloatArray(w * h)
        val planeSize = w * h

        for (i in 0 until planeSize) {
            val c = pixels[i]
            val r = Color.red(c) / 255f
            val g = Color.green(c) / 255f
            val b = Color.blue(c) / 255f
            if (isNchw) {
                imgFloats[i] = r
                imgFloats[planeSize + i] = g
                imgFloats[2 * planeSize + i] = b
            } else {
                imgFloats[i * 3] = r
                imgFloats[i * 3 + 1] = g
                imgFloats[i * 3 + 2] = b
            }
            mskFloats[i] = if (Color.alpha(maskPx[i]) > 25) 1f else 0f
        }

        val imgShape = if (isNchw) longArrayOf(1L, 3L, h.toLong(), w.toLong()) else longArrayOf(1L, h.toLong(), w.toLong(), 3L)
        val mskShape = if (isNchw) longArrayOf(1L, 1L, h.toLong(), w.toLong()) else longArrayOf(1L, h.toLong(), w.toLong(), 1L)

        var imgTensor: OnnxTensor? = null
        var mskTensor: OnnxTensor? = null
        var result: OrtSession.Result? = null
        try {
            imgTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(imgFloats), imgShape)
            val inputs = LinkedHashMap<String, OnnxTensor>()
            inputs[imageName] = imgTensor
            if (maskName != null) {
                mskTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(mskFloats), mskShape)
                inputs[maskName] = mskTensor
            }

            result = session.run(inputs)
            val outTensor = result[0] as OnnxTensor
            val outBuf = outTensor.floatBuffer
            val outArr = FloatArray(outBuf.remaining())
            outBuf.get(outArr)

            // Detect whether model outputs [0..1] or [0..255]
            var maxVal = 0f
            for (v in outArr) {
                if (v > maxVal) maxVal = v
            }
            val scale255 = if (maxVal <= 1.5f) 255f else 1f

            val outPixels = IntArray(planeSize)
            val outShape = (outTensor.info as? TensorInfo)?.shape
            val outIsNchw = outShape == null || outShape.size != 4 || outShape[1] == 3L
            for (i in 0 until planeSize) {
                val r: Int
                val g: Int
                val b: Int
                if (outIsNchw) {
                    r = (outArr[i] * scale255).roundToInt().coerceIn(0, 255)
                    g = (outArr[planeSize + i] * scale255).roundToInt().coerceIn(0, 255)
                    b = (outArr[2 * planeSize + i] * scale255).roundToInt().coerceIn(0, 255)
                } else {
                    r = (outArr[i * 3] * scale255).roundToInt().coerceIn(0, 255)
                    g = (outArr[i * 3 + 1] * scale255).roundToInt().coerceIn(0, 255)
                    b = (outArr[i * 3 + 2] * scale255).roundToInt().coerceIn(0, 255)
                }
                outPixels[i] = Color.argb(255, r, g, b)
            }
            return Bitmap.createBitmap(outPixels, w, h, Bitmap.Config.ARGB_8888)
        } finally {
            runCatching { result?.close() }
            runCatching { imgTensor?.close() }
            runCatching { mskTensor?.close() }
        }
    }

    /**
     * Runs real ONNX foreground segmentation (e.g., U2Net-P, ISNet-Anime, MODNet, RMBG).
     * Returns an ARGB_8888 alpha matte bitmap matching source dimensions.
     */
    suspend fun runSegmentation(
        modelPath: String,
        source: Bitmap,
        threads: Int,
        autoUnload: Boolean,
        onProgress: (Float, String) -> Unit
    ): Bitmap = withContext(Dispatchers.Default) {
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(threads.coerceIn(1, 4))
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        onProgress(0.1f, "Loading ONNX segmentation model...")
        val session = env.createSession(modelPath, opts)
        var imgTensor: OnnxTensor? = null
        var result: OrtSession.Result? = null
        try {
            val inputEntry = session.inputInfo.entries.firstOrNull()
                ?: throw IllegalStateException("ONNX segmentation model has no inputs.")
            val shape = (inputEntry.value.info as? TensorInfo)?.shape ?: longArrayOf(1L, 3L, 320L, 320L)
            val isNchw = shape.size != 4 || shape[1] == 3L || shape[1] == -1L
            val modelH = if (shape.size == 4) {
                val d = if (isNchw) shape[2] else shape[1]
                if (d > 0L) d.toInt().coerceIn(128, 768) else 320
            } else 320
            val modelW = if (shape.size == 4) {
                val d = if (isNchw) shape[3] else shape[2]
                if (d > 0L) d.toInt().coerceIn(128, 768) else 320
            } else 320

            onProgress(0.35f, "Preparing ${modelW}×${modelH} normalized tensor...")
            val scaled = Bitmap.createScaledBitmap(source, modelW, modelH, true)
            val planeSize = modelW * modelH
            val px = IntArray(planeSize)
            scaled.getPixels(px, 0, modelW, 0, 0, modelW, modelH)
            if (scaled != source) scaled.recycle()

            // Standard ImageNet normalization
            val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
            val std = floatArrayOf(0.229f, 0.224f, 0.225f)
            val floats = FloatArray(3 * planeSize)
            for (i in 0 until planeSize) {
                val c = px[i]
                val r = (Color.red(c) / 255f - mean[0]) / std[0]
                val g = (Color.green(c) / 255f - mean[1]) / std[1]
                val b = (Color.blue(c) / 255f - mean[2]) / std[2]
                if (isNchw) {
                    floats[i] = r
                    floats[planeSize + i] = g
                    floats[2 * planeSize + i] = b
                } else {
                    floats[i * 3] = r
                    floats[i * 3 + 1] = g
                    floats[i * 3 + 2] = b
                }
            }

            val tensorShape = if (isNchw) longArrayOf(1L, 3L, modelH.toLong(), modelW.toLong())
            else longArrayOf(1L, modelH.toLong(), modelW.toLong(), 3L)

            onProgress(0.6f, "Executing ONNX foreground matte inference...")
            imgTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floats), tensorShape)
            result = session.run(mapOf(inputEntry.key to imgTensor))

            val outTensor = result[0] as OnnxTensor
            val outBuf = outTensor.floatBuffer
            val outArr = FloatArray(outBuf.remaining())
            outBuf.get(outArr)

            // Detect logits vs probabilities
            var minV = Float.MAX_VALUE
            var maxV = -Float.MAX_VALUE
            val limit = min(planeSize, outArr.size)
            for (i in 0 until limit) {
                val v = outArr[i]
                if (v < minV) minV = v
                if (v > maxV) maxV = v
            }
            val needsSigmoid = minV < -0.05f || maxV > 1.05f

            val maskSmallPx = IntArray(planeSize)
            for (i in 0 until limit) {
                val raw = outArr[i]
                val prob = if (needsSigmoid) 1f / (1f + exp(-raw)) else raw.coerceIn(0f, 1f)
                val alpha = (prob * 255f).roundToInt().coerceIn(0, 255)
                maskSmallPx[i] = Color.argb(alpha, 255, 255, 255)
            }
            val maskSmall = Bitmap.createBitmap(maskSmallPx, modelW, modelH, Bitmap.Config.ARGB_8888)
            val fullMask = Bitmap.createScaledBitmap(maskSmall, source.width, source.height, true)
            maskSmall.recycle()
            onProgress(0.92f, if (autoUnload) "Unloading ONNX session..." else "Done")
            fullMask
        } finally {
            runCatching { result?.close() }
            runCatching { imgTensor?.close() }
            if (autoUnload) {
                runCatching { session.close() }
            }
        }
    }
}
