package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.example.domain.model.DeviceMemorySnapshot
import com.example.domain.model.ExportFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object MemoryManager {

    fun getDeviceMemorySnapshot(context: Context): DeviceMemorySnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val mb = 1024L * 1024L
        return DeviceMemorySnapshot(
            totalRamMb = info.totalMem / mb,
            availableRamMb = info.availMem / mb,
            maxAppHeapMb = Runtime.getRuntime().maxMemory() / mb,
            isLowMemory = info.lowMemory
        )
    }

    suspend fun decodeBitmapFromUri(
        context: Context,
        uri: Uri,
        maxDimension: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                    val w = info.size.width
                    val h = info.size.height
                    val longest = max(w, h)
                    if (longest > maxDimension && maxDimension > 0) {
                        val scale = maxDimension.toFloat() / longest.toFloat()
                        decoder.setTargetSize(
                            (w * scale).roundToInt().coerceAtLeast(1),
                            (h * scale).roundToInt().coerceAtLeast(1)
                        )
                    }
                }
                ensureSoftwareArgb8888(decoded)
            } else {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, boundsOpts)
                }
                val longest = max(boundsOpts.outWidth, boundsOpts.outHeight)
                var sampleSize = 1
                while (longest / sampleSize > maxDimension * 2 && maxDimension > 0) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = true
                }
                val raw = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                } ?: return@withContext null
                val scaled = scaleDownIfNeeded(raw, maxDimension)
                ensureSoftwareArgb8888(scaled)
            }
        } catch (_: OutOfMemoryError) {
            System.gc()
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun decodeBitmapFromFile(
        filePath: String,
        maxDimension: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext null
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, bounds)
            val longest = max(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longest / sample > maxDimension * 2 && maxDimension > 0) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
            }
            val raw = BitmapFactory.decodeFile(filePath, opts) ?: return@withContext null
            ensureSoftwareArgb8888(scaleDownIfNeeded(raw, maxDimension))
        } catch (_: OutOfMemoryError) {
            System.gc()
            null
        } catch (_: Exception) {
            null
        }
    }

    fun ensureSoftwareArgb8888(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.ARGB_8888 && bitmap.isMutable) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }
    }

    fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (maxDimension <= 0 || longest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / longest.toFloat()
        val targetW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    fun createEmptyMask(width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    }

    suspend fun saveBitmapToPrivatePng(
        context: Context,
        bitmap: Bitmap,
        subDir: String,
        fileName: String
    ): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, subDir).apply { if (!exists()) mkdirs() }
        val target = File(dir, fileName)
        FileOutputStream(target).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        target.absolutePath
    }

    suspend fun exportBitmapToUri(
        context: Context,
        bitmap: Bitmap,
        destinationUri: Uri,
        format: ExportFormat,
        jpgQuality: Int = 95
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                val compressFormat = when (format) {
                    ExportFormat.PNG -> Bitmap.CompressFormat.PNG
                    ExportFormat.JPG -> Bitmap.CompressFormat.JPEG
                }
                val quality = if (format == ExportFormat.PNG) 100 else jpgQuality.coerceIn(70, 100)
                bitmap.compress(compressFormat, quality, out)
            } ?: false
        } catch (_: Exception) {
            false
        }
    }
}
