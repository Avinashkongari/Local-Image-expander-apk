package com.example.engine

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.exp
import kotlin.math.roundToInt

data class LiteRtInspectionResult(
    val isValid: Boolean,
    val inputSignature: String,
    val outputSignature: String,
    val errorMessage: String? = null
)

object LiteRtModelRunner {

    private val liteRtDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "AxionLiteRTThread")
    }.asCoroutineDispatcher()

    suspend fun inspectModel(modelPath: String, threads: Int = 4): LiteRtInspectionResult =
        withContext(liteRtDispatcher) {
            val file = File(modelPath)
            if (!file.exists() || file.length() < 64L) {
                return@withContext LiteRtInspectionResult(
                    isValid = false,
                    inputSignature = "",
                    outputSignature = "",
                    errorMessage = "TFLite/LiteRT file missing or empty."
                )
            }
            var interpreter: Interpreter? = null
            try {
                val options = Interpreter.Options().apply {
                    setNumThreads(threads.coerceIn(1, 4))
                    setUseXNNPACK(true)
                }
                interpreter = Interpreter(file, options)
                val inSig = (0 until interpreter.inputTensorCount).joinToString("; ") { idx ->
                    val t = interpreter.getInputTensor(idx)
                    "${t.name()}[${t.shape().joinToString("x")}:${t.dataType()}]"
                }
                val outSig = (0 until interpreter.outputTensorCount).joinToString("; ") { idx ->
                    val t = interpreter.getOutputTensor(idx)
                    "${t.name()}[${t.shape().joinToString("x")}:${t.dataType()}]"
                }
                LiteRtInspectionResult(
                    isValid = true,
                    inputSignature = inSig,
                    outputSignature = outSig
                )
            } catch (e: Throwable) {
                LiteRtInspectionResult(
                    isValid = false,
                    inputSignature = "",
                    outputSignature = "",
                    errorMessage = e.message ?: "Invalid LiteRT/TFLite model."
                )
            } finally {
                runCatching { interpreter?.close() }
            }
        }

    suspend fun runSegmentation(
        modelPath: String,
        source: Bitmap,
        threads: Int,
        autoUnload: Boolean,
        onProgress: (Float, String) -> Unit
    ): Bitmap = withContext(liteRtDispatcher) {
        onProgress(0.15f, "Loading LiteRT Interpreter...")
        val options = Interpreter.Options().apply {
            setNumThreads(threads.coerceIn(1, 4))
            setUseXNNPACK(true)
        }
        val interpreter = Interpreter(File(modelPath), options)
        try {
            val inTensor = interpreter.getInputTensor(0)
            val inShape = inTensor.shape() // Typically [1, H, W, 3] or [1, 3, H, W]
            val isNhwc = inShape.size != 4 || inShape[3] == 3
            val modelH = if (inShape.size == 4) {
                (if (isNhwc) inShape[1] else inShape[2]).coerceIn(64, 768)
            } else 256
            val modelW = if (inShape.size == 4) {
                (if (isNhwc) inShape[2] else inShape[3]).coerceIn(64, 768)
            } else 256

            onProgress(0.45f, "Preprocessing ${modelW}×${modelH} LiteRT tensor...")
            val scaled = Bitmap.createScaledBitmap(source, modelW, modelH, true)
            val planeSize = modelW * modelH
            val px = IntArray(planeSize)
            scaled.getPixels(px, 0, modelW, 0, 0, modelW, modelH)
            if (scaled != source) scaled.recycle()

            val inputBuf = ByteBuffer.allocateDirect(4 * 3 * planeSize).order(ByteOrder.nativeOrder())
            if (isNhwc) {
                for (i in 0 until planeSize) {
                    val c = px[i]
                    inputBuf.putFloat(Color.red(c) / 255f)
                    inputBuf.putFloat(Color.green(c) / 255f)
                    inputBuf.putFloat(Color.blue(c) / 255f)
                }
            } else {
                for (i in 0 until planeSize) inputBuf.putFloat(Color.red(px[i]) / 255f)
                for (i in 0 until planeSize) inputBuf.putFloat(Color.green(px[i]) / 255f)
                for (i in 0 until planeSize) inputBuf.putFloat(Color.blue(px[i]) / 255f)
            }
            inputBuf.rewind()

            val outTensor = interpreter.getOutputTensor(0)
            val outElems = outTensor.numElements().coerceAtLeast(planeSize)
            val outBuf = ByteBuffer.allocateDirect(4 * outElems).order(ByteOrder.nativeOrder())

            onProgress(0.72f, "Executing LiteRT segmentation...")
            interpreter.run(inputBuf, outBuf)
            outBuf.rewind()

            val outArr = FloatArray(planeSize)
            val availableFloats = (outBuf.remaining() / 4).coerceAtMost(planeSize)
            for (i in 0 until availableFloats) {
                outArr[i] = outBuf.getFloat()
            }

            var minV = Float.MAX_VALUE
            var maxV = -Float.MAX_VALUE
            for (i in 0 until availableFloats) {
                if (outArr[i] < minV) minV = outArr[i]
                if (outArr[i] > maxV) maxV = outArr[i]
            }
            val needsSigmoid = minV < -0.05f || maxV > 1.05f

            val maskPx = IntArray(planeSize)
            for (i in 0 until availableFloats) {
                val p = if (needsSigmoid) 1f / (1f + exp(-outArr[i])) else outArr[i].coerceIn(0f, 1f)
                val alpha = (p * 255f).roundToInt().coerceIn(0, 255)
                maskPx[i] = Color.argb(alpha, 255, 255, 255)
            }

            val smallMask = Bitmap.createBitmap(maskPx, modelW, modelH, Bitmap.Config.ARGB_8888)
            val fullMask = Bitmap.createScaledBitmap(smallMask, source.width, source.height, true)
            smallMask.recycle()
            onProgress(0.95f, if (autoUnload) "Unloading LiteRT Interpreter..." else "Done")
            fullMask
        } finally {
            if (autoUnload) {
                runCatching { interpreter.close() }
            }
        }
    }
}
