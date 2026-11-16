package com.example.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.db.AxionDao
import com.example.data.db.LocalModelEntity
import com.example.domain.model.ModelCapabilitySlot
import com.example.domain.model.ModelDownloadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class ModelDownloadManager(
    private val context: Context,
    private val dao: AxionDao
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun modelsDir(): File = File(context.filesDir, "models").apply {
        if (!exists()) mkdirs()
    }

    /**
     * Imports a user-selected local .onnx or .tflite file from device storage (offline).
     * Validates the model graph using ONNX Runtime or LiteRT before marking it installed.
     */
    suspend fun importLocalModelFile(
        slot: ModelCapabilitySlot,
        uri: Uri,
        threads: Int
    ): Result<LocalModelEntity> = withContext(Dispatchers.IO) {
        try {
            val fileName = queryFileName(uri) ?: "${slot.slotKey}.onnx"
            val format = detectFormat(fileName)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Unsupported model file '$fileName'. Please select a .onnx or .tflite model.")
                )

            val ext = if (format == "ONNX") "onnx" else "tflite"
            val targetFile = File(modelsDir(), "${slot.slotKey}.$ext")
            val tempFile = File(modelsDir(), "${slot.slotKey}.$ext.tmp")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return@withContext Result.failure(IllegalStateException("Unable to read selected file."))

            validateAndCommit(
                slot = slot,
                format = format,
                displayName = fileName,
                tempFile = tempFile,
                targetFile = targetFile,
                threads = threads,
                sourceInfo = "Imported locally from device storage"
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads a model file ONLY from a user-provided direct URL (never hardcoded fake URLs),
     * streaming to disk with real progress, cancellation, and post-download runtime verification.
     */
    suspend fun downloadModelFromUrl(
        slot: ModelCapabilitySlot,
        url: String,
        threads: Int,
        onProgress: (ModelDownloadProgress) -> Unit
    ): Result<LocalModelEntity> = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid http:// or https:// direct model file URL."))
        }

        val urlFileName = trimmed.substringAfterLast('/').substringBefore('?').ifBlank { "${slot.slotKey}.onnx" }
        val format = detectFormat(urlFileName) ?: "ONNX"
        val ext = if (format == "ONNX") "onnx" else "tflite"
        val targetFile = File(modelsDir(), "${slot.slotKey}.$ext")
        val tempFile = File(modelsDir(), "${slot.slotKey}.$ext.tmp")

        try {
            onProgress(ModelDownloadProgress(slot.slotKey, 0L, 0L, "Connecting..."))
            val request = Request.Builder().url(trimmed).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("HTTP ${response.code}: ${response.message}")
                    )
                }
                val body = response.body ?: return@withContext Result.failure(
                    IllegalStateException("Empty HTTP response body.")
                )
                val totalBytes = body.contentLength()
                var downloaded = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(
                                ModelDownloadProgress(
                                    slotKey = slot.slotKey,
                                    bytesDownloaded = downloaded,
                                    totalBytes = totalBytes,
                                    statusText = "Downloading ${formatBytes(downloaded)}${if (totalBytes > 0) " / ${formatBytes(totalBytes)}" else ""}"
                                )
                            )
                        }
                    }
                }
            }

            onProgress(ModelDownloadProgress(slot.slotKey, tempFile.length(), tempFile.length(), "Verifying model tensors..."))
            validateAndCommit(
                slot = slot,
                format = format,
                displayName = urlFileName,
                tempFile = tempFile,
                targetFile = targetFile,
                threads = threads,
                sourceInfo = "Downloaded from user URL"
            )
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(e)
        }
    }

    private suspend fun validateAndCommit(
        slot: ModelCapabilitySlot,
        format: String,
        displayName: String,
        tempFile: File,
        targetFile: File,
        threads: Int,
        sourceInfo: String
    ): Result<LocalModelEntity> {
        val inputSig: String
        val outputSig: String

        if (format == "ONNX") {
            val inspection = OnnxModelRunner.inspectModel(tempFile.absolutePath, threads)
            if (!inspection.isValid) {
                tempFile.delete()
                return Result.failure(
                    IllegalArgumentException("ONNX validation failed: ${inspection.errorMessage}")
                )
            }
            inputSig = inspection.inputSignature
            outputSig = inspection.outputSignature
        } else {
            val inspection = LiteRtModelRunner.inspectModel(tempFile.absolutePath, threads)
            if (!inspection.isValid) {
                tempFile.delete()
                return Result.failure(
                    IllegalArgumentException("LiteRT validation failed: ${inspection.errorMessage}")
                )
            }
            inputSig = inspection.inputSignature
            outputSig = inspection.outputSignature
        }

        if (targetFile.exists()) targetFile.delete()
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }

        val sha = computeSha256(targetFile)
        val entity = LocalModelEntity(
            slotKey = slot.slotKey,
            displayName = displayName,
            modelFormat = format,
            filePath = targetFile.absolutePath,
            fileSizeBytes = targetFile.length(),
            sha256 = sha,
            inputSignature = inputSig,
            outputSignature = outputSig,
            isInstalled = true,
            installedAt = System.currentTimeMillis(),
            sourceInfo = sourceInfo
        )
        dao.upsertLocalModel(entity)
        return Result.success(entity)
    }

    suspend fun deleteModel(slotKey: String) = withContext(Dispatchers.IO) {
        val existing = dao.getModelBySlot(slotKey)
        existing?.let {
            runCatching { File(it.filePath).delete() }
        }
        // Clean up any stray files for slotKey
        modelsDir().listFiles()?.forEach { f ->
            if (f.name.startsWith(slotKey)) f.delete()
        }
        dao.deleteLocalModel(slotKey)
    }

    private fun detectFormat(name: String): String? {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".onnx") -> "ONNX"
            lower.endsWith(".tflite") || lower.endsWith(".lite") -> "LITERT"
            else -> null
        }
    }

    private fun queryFileName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = input.read(buf)
                if (r <= 0) break
                digest.update(buf, 0, r)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(16)
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format("%.2f GB", gb)
                mb >= 1.0 -> String.format("%.1f MB", mb)
                kb >= 1.0 -> String.format("%.0f KB", kb)
                else -> "$bytes B"
            }
        }
    }
}
