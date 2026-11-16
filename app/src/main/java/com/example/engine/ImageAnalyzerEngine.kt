package com.example.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.example.domain.model.DetectedColorInfo
import com.example.domain.model.DetectedRegion
import com.example.domain.model.ImageAnalysisResult
import com.example.domain.model.RegionKind
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ImageAnalyzerEngine {

    suspend fun analyzeImage(bitmap: Bitmap): ImageAnalysisResult = withContext(Dispatchers.Default) {
        val safeBitmap = MemoryManager.ensureSoftwareArgb8888(bitmap)
        val width = safeBitmap.width
        val height = safeBitmap.height

        // 1. Run real bundled ML Kit OCR on-device
        val ocrRegions = detectTextWithMlKit(safeBitmap, width, height)

        // 2. Downsample to max 256px for fast pixel-level computer vision analysis
        val sampleMax = 256
        val scale = sampleMax.toFloat() / max(width, height).toFloat()
        val sw = (width * scale).roundToInt().coerceAtLeast(16)
        val sh = (height * scale).roundToInt().coerceAtLeast(16)
        val small = Bitmap.createScaledBitmap(safeBitmap, sw, sh, true)
        val pixels = IntArray(sw * sh)
        small.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        if (small != safeBitmap) small.recycle()

        // 3. Dominant color quantization & warm/cool color temperature
        val (dominantColors, colorTemp) = extractDominantColors(pixels)

        // 4. Luminance, contrast & directional lighting analysis
        val lighting = analyzeLighting(pixels, sw, sh)

        // 5. Sobel edge orientation, perspective & saliency subject bounds
        val composition = analyzePerspectiveAndSubject(pixels, sw, sh)

        // 6. Speech bubble detection (combining bright enclosed regions + OCR clusters)
        val speechBubbles = detectSpeechBubbles(pixels, sw, sh, ocrRegions)

        // 7. Aspect ratio & background estimation
        val ratioLabel = computeAspectRatioLabel(width, height)
        val bgSummary = buildBackgroundSummary(pixels, sw, sh, lighting, colorTemp)
        val subjectSummary = buildSubjectSummary(composition, ocrRegions.size, speechBubbles.size)

        val suggestedPrompt = buildOutpaintPrompt(
            colorTemp = colorTemp,
            dominantColors = dominantColors,
            lightingSummary = lighting.summary,
            lightDirection = lighting.direction,
            perspective = composition.perspectiveSummary
        )

        ImageAnalysisResult(
            width = width,
            height = height,
            aspectRatioLabel = ratioLabel,
            subjectSummary = subjectSummary,
            subjectBoundsNorm = composition.subjectBounds,
            estimatedCharacterOrObjectRegions = composition.objectClusters,
            textRegions = ocrRegions,
            speechBubbleRegions = speechBubbles,
            backgroundSummary = bgSummary,
            dominantColors = dominantColors,
            colorTemperature = colorTemp,
            lightingSummary = lighting.summary,
            lightDirection = lighting.direction,
            averageLuminance = lighting.meanLuma,
            contrastScore = lighting.contrastScore,
            perspectiveSummary = composition.perspectiveSummary,
            compositionSummary = composition.compositionSummary,
            suggestedOutpaintPrompt = suggestedPrompt,
            engineNotes = "Analyzed offline via ML Kit On-Device OCR + Luminance/Sobel Vision Kernels"
        )
    }

    private suspend fun detectTextWithMlKit(
        bitmap: Bitmap,
        width: Int,
        height: Int
    ): List<DetectedRegion> = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val list = visionText.textBlocks.mapIndexedNotNull { idx, block ->
                    val box = block.boundingBox ?: return@mapIndexedNotNull null
                    val l = (box.left.toFloat() / width).coerceIn(0f, 1f)
                    val t = (box.top.toFloat() / height).coerceIn(0f, 1f)
                    val r = (box.right.toFloat() / width).coerceIn(0f, 1f)
                    val b = (box.bottom.toFloat() / height).coerceIn(0f, 1f)
                    if (r <= l || b <= t) return@mapIndexedNotNull null
                    DetectedRegion(
                        id = "ocr_$idx",
                        label = "Text Block ${idx + 1}",
                        kind = RegionKind.TEXT_OCR,
                        leftNorm = l,
                        topNorm = t,
                        rightNorm = r,
                        bottomNorm = b,
                        extractedText = block.text.replace("\n", " ").take(80),
                        confidence = 0.92f
                    )
                }
                recognizer.close()
                if (cont.isActive) cont.resume(list)
            }
            .addOnFailureListener {
                recognizer.close()
                if (cont.isActive) cont.resume(emptyList())
            }
    }

    private fun detectSpeechBubbles(
        pixels: IntArray,
        w: Int,
        h: Int,
        ocrRegions: List<DetectedRegion>
    ): List<DetectedRegion> {
        // Connected component search on bright low-saturation pixels (typical manga/manhua speech balloons)
        val isBubblePixel = BooleanArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            val sat = if (maxC == 0) 0f else (maxC - minC).toFloat() / maxC.toFloat()
            if (luma > 225f && sat < 0.15f) {
                isBubblePixel[i] = true
            }
        }

        val visited = BooleanArray(w * h)
        val bubbles = mutableListOf<DetectedRegion>()
        val queue = IntArray(w * h)
        val minArea = (w * h * 0.004f).toInt().coerceAtLeast(18)
        val maxArea = (w * h * 0.25f).toInt()

        for (start in pixels.indices) {
            if (!isBubblePixel[start] || visited[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true

            var minX = start % w
            var maxX = minX
            var minY = start / w
            var maxY = minY
            var count = 0

            while (head < tail) {
                val idx = queue[head++]
                count++
                val x = idx % w
                val y = idx / w
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y

                val neighbors = intArrayOf(
                    if (x > 0) idx - 1 else -1,
                    if (x < w - 1) idx + 1 else -1,
                    if (y > 0) idx - w else -1,
                    if (y < h - 1) idx + w else -1
                )
                for (n in neighbors) {
                    if (n >= 0 && !visited[n] && isBubblePixel[n]) {
                        visited[n] = true
                        queue[tail++] = n
                    }
                }
            }

            if (count in minArea..maxArea) {
                val boxW = (maxX - minX + 1).coerceAtLeast(1)
                val boxH = (maxY - minY + 1).coerceAtLeast(1)
                val fillRatio = count.toFloat() / (boxW * boxH).toFloat()
                // Speech bubbles are compact ovals/rounded rects (avoid thin border strips)
                if (fillRatio > 0.42f && boxW > 6 && boxH > 6) {
                    val l = (minX.toFloat() / w - 0.008f).coerceIn(0f, 1f)
                    val t = (minY.toFloat() / h - 0.008f).coerceIn(0f, 1f)
                    val r = (maxX.toFloat() / w + 0.008f).coerceIn(0f, 1f)
                    val b = (maxY.toFloat() / h + 0.008f).coerceIn(0f, 1f)
                    val overlapsOcr = ocrRegions.any { ocr ->
                        ocr.leftNorm < r && ocr.rightNorm > l && ocr.topNorm < b && ocr.bottomNorm > t
                    }
                    // Include if it overlaps OCR OR is compact interior balloon not touching image edge excessively
                    val touchesOuterBorder = minX == 0 && maxX == w - 1
                    if (!touchesOuterBorder && (overlapsOcr || fillRatio > 0.56f)) {
                        bubbles.add(
                            DetectedRegion(
                                id = "bubble_${bubbles.size}",
                                label = if (overlapsOcr) "Dialogue Bubble ${bubbles.size + 1}" else "Balloon Candidate ${bubbles.size + 1}",
                                kind = RegionKind.SPEECH_BUBBLE,
                                leftNorm = l,
                                topNorm = t,
                                rightNorm = r,
                                bottomNorm = b,
                                confidence = if (overlapsOcr) 0.94f else 0.72f
                            )
                        )
                    }
                }
            }
        }
        return bubbles.take(16)
    }

    private fun extractDominantColors(pixels: IntArray): Pair<List<DetectedColorInfo>, String> {
        // 4-bit per channel quantization (4096 buckets)
        val counts = HashMap<Int, Int>()
        val sumR = HashMap<Int, Long>()
        val sumG = HashMap<Int, Long>()
        val sumB = HashMap<Int, Long>()

        var totalWarmth = 0.0
        for (c in pixels) {
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            totalWarmth += (r - b)
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            counts[key] = (counts[key] ?: 0) + 1
            sumR[key] = (sumR[key] ?: 0L) + r
            sumG[key] = (sumG[key] ?: 0L) + g
            sumB[key] = (sumB[key] ?: 0L) + b
        }

        val sorted = counts.entries.sortedByDescending { it.value }
        val selected = mutableListOf<DetectedColorInfo>()
        val selectedRgb = mutableListOf<Triple<Int, Int, Int>>()
        val total = pixels.size.coerceAtLeast(1).toFloat()

        for (entry in sorted) {
            val key = entry.key
            val cnt = entry.value
            val avgR = (sumR[key]!! / cnt).toInt()
            val avgG = (sumG[key]!! / cnt).toInt()
            val avgB = (sumB[key]!! / cnt).toInt()

            val tooClose = selectedRgb.any { (sr, sg, sb) ->
                abs(sr - avgR) + abs(sg - avgG) + abs(sb - avgB) < 75
            }
            if (!tooClose) {
                selectedRgb.add(Triple(avgR, avgG, avgB))
                val hex = String.format("#%02X%02X%02X", avgR, avgG, avgB)
                val pct = (cnt / total) * 100f
                selected.add(
                    DetectedColorInfo(
                        hex = hex,
                        percentage = pct,
                        label = describeColor(avgR, avgG, avgB)
                    )
                )
            }
            if (selected.size >= 5) break
        }

        val avgWarmth = totalWarmth / total
        val temp = when {
            avgWarmth > 14 -> "Warm / Golden"
            avgWarmth < -14 -> "Cool / Cyan-Blue"
            else -> "Neutral Balanced"
        }
        return selected to temp
    }

    private fun describeColor(r: Int, g: Int, b: Int): String {
        val luma = 0.299 * r + 0.587 * g + 0.114 * b
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        if (maxC - minC < 22) {
            return when {
                luma < 45 -> "Deep Shadow"
                luma < 110 -> "Charcoal"
                luma < 195 -> "Mid Gray"
                else -> "Paper White"
            }
        }
        return when {
            r > g + 35 && r > b + 35 -> if (g > 120) "Amber / Orange" else "Crimson / Red"
            g > r + 25 && g > b + 25 -> "Emerald / Green"
            b > r + 30 && b > g + 20 -> if (g > 140) "Cyan / Sky" else "Cobalt / Indigo"
            r > 160 && b > 150 && g < 130 -> "Violet / Magenta"
            r > 170 && g > 160 && b < 120 -> "Gold / Yellow"
            else -> "Muted Tone"
        }
    }

    private data class LightingStats(
        val summary: String,
        val direction: String,
        val meanLuma: Float,
        val contrastScore: Float
    )

    private fun analyzeLighting(pixels: IntArray, w: Int, h: Int): LightingStats {
        var sum = 0.0
        var sumSq = 0.0
        var topLeft = 0.0
        var topRight = 0.0
        var bottom = 0.0
        val halfW = w / 2
        val halfH = h / 2

        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = pixels[y * w + x]
                val luma = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
                sum += luma
                sumSq += luma * luma
                if (y < halfH) {
                    if (x < halfW) topLeft += luma else topRight += luma
                } else {
                    bottom += luma
                }
            }
        }
        val n = pixels.size.coerceAtLeast(1)
        val mean = (sum / n).toFloat()
        val std = sqrt((sumSq / n - mean * mean).coerceAtLeast(0.0)).toFloat()

        val tlAvg = (topLeft / (halfW * halfH).coerceAtLeast(1)).toFloat()
        val trAvg = (topRight / ((w - halfW) * halfH).coerceAtLeast(1)).toFloat()
        val botAvg = (bottom / (w * (h - halfH)).coerceAtLeast(1)).toFloat()

        val direction = when {
            tlAvg > trAvg + 0.07f && tlAvg > botAvg + 0.05f -> "Top-Left Key Light"
            trAvg > tlAvg + 0.07f && trAvg > botAvg + 0.05f -> "Top-Right Key Light"
            (tlAvg + trAvg) / 2f > botAvg + 0.08f -> "Overhead / Sky Illumination"
            botAvg > (tlAvg + trAvg) / 2f + 0.07f -> "Low-Angle / Rim Uplight"
            else -> "Ambient / Diffused Lighting"
        }

        val keyDesc = when {
            mean < 0.30f -> "Dark / Low-Key"
            mean > 0.72f -> "Bright / High-Key"
            else -> "Balanced Mid-Key"
        }
        val contrastDesc = when {
            std > 0.27f -> "High Contrast Dramatic"
            std > 0.17f -> "Crisp Studio Contrast"
            else -> "Soft / Flat Shading"
        }
        return LightingStats(
            summary = "$keyDesc • $contrastDesc",
            direction = direction,
            meanLuma = mean,
            contrastScore = (std * 2.5f).coerceIn(0f, 1f)
        )
    }

    private data class CompositionStats(
        val subjectBounds: DetectedRegion?,
        val objectClusters: Int,
        val perspectiveSummary: String,
        val compositionSummary: String
    )

    private fun analyzePerspectiveAndSubject(pixels: IntArray, w: Int, h: Int): CompositionStats {
        val luma = FloatArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            luma[i] = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
        }

        var horizEnergy = 0.0
        var vertEnergy = 0.0
        var diagEnergy = 0.0
        var weightedX = 0.0
        var weightedY = 0.0
        var weightSum = 0.0

        val saliency = FloatArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val gx = -luma[(y - 1) * w + (x - 1)] + luma[(y - 1) * w + (x + 1)] -
                    2f * luma[y * w + (x - 1)] + 2f * luma[y * w + (x + 1)] -
                    luma[(y + 1) * w + (x - 1)] + luma[(y + 1) * w + (x + 1)]
                val gy = -luma[(y - 1) * w + (x - 1)] - 2f * luma[(y - 1) * w + x] - luma[(y - 1) * w + (x + 1)] +
                    luma[(y + 1) * w + (x - 1)] + 2f * luma[(y + 1) * w + x] + luma[(y + 1) * w + (x + 1)]
                val mag = hypot(gx, gy)
                saliency[y * w + x] = mag

                if (mag > 0.18f) {
                    val angle = abs(Math.toDegrees(atan2(gy.toDouble(), gx.toDouble())))
                    when {
                        angle < 20.0 || angle > 160.0 -> vertEnergy += mag
                        angle in 70.0..110.0 -> horizEnergy += mag
                        else -> diagEnergy += mag
                    }
                    // Center-weighted saliency bias for main subject bounding
                    val centerBoost = 1.2f - 0.5f * hypot((x.toFloat() / w - 0.5f), (y.toFloat() / h - 0.5f))
                    val swVal = mag * centerBoost.coerceAtLeast(0.3f)
                    weightedX += x * swVal
                    weightedY += y * swVal
                    weightSum += swVal
                }
            }
        }

        val cx = if (weightSum > 0) (weightedX / weightSum / w).toFloat().coerceIn(0.15f, 0.85f) else 0.5f
        val cy = if (weightSum > 0) (weightedY / weightSum / h).toFloat().coerceIn(0.15f, 0.85f) else 0.5f

        // Compute tight bounding box around top-salient pixels
        var minX = w
        var maxX = 0
        var minY = h
        var maxY = 0
        var salientCount = 0
        for (y in 2 until h - 2) {
            for (x in 2 until w - 2) {
                if (saliency[y * w + x] > 0.25f) {
                    salientCount++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        val bounds = if (salientCount > 20 && maxX > minX && maxY > minY) {
            DetectedRegion(
                id = "main_subject",
                label = "Primary Subject Zone",
                kind = RegionKind.MAIN_SUBJECT,
                leftNorm = (minX.toFloat() / w).coerceIn(0.03f, 0.9f),
                topNorm = (minY.toFloat() / h).coerceIn(0.03f, 0.9f),
                rightNorm = (maxX.toFloat() / w).coerceIn(0.1f, 0.97f),
                bottomNorm = (maxY.toFloat() / h).coerceIn(0.1f, 0.97f),
                confidence = 0.85f
            )
        } else {
            DetectedRegion(
                id = "main_subject",
                label = "Primary Subject Zone",
                kind = RegionKind.MAIN_SUBJECT,
                leftNorm = 0.2f,
                topNorm = 0.15f,
                rightNorm = 0.8f,
                bottomNorm = 0.88f,
                confidence = 0.6f
            )
        }

        val totalE = (horizEnergy + vertEnergy + diagEnergy).coerceAtLeast(1.0)
        val diagRatio = diagEnergy / totalE
        val vertRatio = vertEnergy / totalE
        val perspective = when {
            diagRatio > 0.48 -> "Dynamic Diagonal / Action Perspective"
            vertRatio > 0.42 -> "Strong Vertical Framing (Portrait / Architectural)"
            else -> "Stable Horizon / Eye-Level Framing"
        }

        val horizPlacement = when {
            cx < 0.38f -> "Left-Third Anchored"
            cx > 0.62f -> "Right-Third Anchored"
            else -> "Center-Balanced Subject"
        }
        val vertPlacement = when {
            cy < 0.38f -> "Upper Focal Plane"
            cy > 0.62f -> "Lower Grounded Plane"
            else -> "Mid-Frame Focus"
        }

        val clusterEstimate = (salientCount / ((w * h) / 14).coerceAtLeast(1)).coerceIn(1, 6)

        return CompositionStats(
            subjectBounds = bounds,
            objectClusters = clusterEstimate,
            perspectiveSummary = perspective,
            compositionSummary = "$horizPlacement • $vertPlacement"
        )
    }

    private fun buildBackgroundSummary(
        pixels: IntArray,
        w: Int,
        h: Int,
        lighting: LightingStats,
        colorTemp: String
    ): String {
        // Sample border pixels (top/left/right edges) to describe background environment
        var borderLuma = 0.0
        var borderCount = 0
        for (x in 0 until w) {
            borderLuma += lumaOf(pixels[x])
            borderLuma += lumaOf(pixels[(h - 1) * w + x])
            borderCount += 2
        }
        for (y in 0 until h) {
            borderLuma += lumaOf(pixels[y * w])
            borderLuma += lumaOf(pixels[y * w + (w - 1)])
            borderCount += 2
        }
        val avgBorder = (borderLuma / borderCount.coerceAtLeast(1)).toFloat()
        val tone = when {
            avgBorder > 0.85f -> "High-brightness / clean light backdrop"
            avgBorder < 0.25f -> "Deep shadow / night or dark atmospheric backdrop"
            else -> "Mid-tone textured environment"
        }
        return "$tone • $colorTemp palette (${(lighting.meanLuma * 100).roundToInt()}% avg luma)"
    }

    private fun buildSubjectSummary(
        comp: CompositionStats,
        ocrCount: Int,
        bubbleCount: Int
    ): String {
        val textNote = when {
            bubbleCount > 0 && ocrCount > 0 -> "$bubbleCount speech bubble(s) and $ocrCount text block(s) detected"
            ocrCount > 0 -> "$ocrCount text region(s) detected"
            bubbleCount > 0 -> "$bubbleCount speech balloon region(s) detected"
            else -> "Clean artwork without overlaid text blocks"
        }
        return "${comp.compositionSummary}; $textNote."
    }

    private fun buildOutpaintPrompt(
        colorTemp: String,
        dominantColors: List<DetectedColorInfo>,
        lightingSummary: String,
        lightDirection: String,
        perspective: String
    ): String {
        val paletteStr = dominantColors.take(3).joinToString(", ") { "${it.label} (${it.hex})" }
        return "Expand this image to 16:9. Continue the existing environment, preserve the original character and crisp linework, match the $lightingSummary with $lightDirection, $colorTemp tones ($paletteStr), and maintain $perspective with seamless edges."
    }

    private fun computeAspectRatioLabel(w: Int, h: Int): String {
        val ratio = w.toFloat() / h.coerceAtLeast(1).toFloat()
        return when {
            abs(ratio - 16f / 9f) < 0.08f -> "16:9 Widescreen"
            abs(ratio - 9f / 16f) < 0.08f -> "9:16 Vertical Panel"
            abs(ratio - 4f / 3f) < 0.08f -> "4:3 Standard"
            abs(ratio - 3f / 4f) < 0.08f -> "3:4 Portrait"
            abs(ratio - 1f) < 0.08f -> "1:1 Square"
            ratio < 0.55f -> "Tall Webtoon / Manhua Strip"
            else -> String.format("%.2f:1 Custom", ratio)
        }
    }

    private fun lumaOf(c: Int): Float {
        return (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
    }
}
