package com.example.domain.model

enum class ModelCapabilitySlot(
    val slotKey: String,
    val title: String,
    val modelTypeLabel: String,
    val supportedFormats: List<String>,
    val recommendedArtifactHint: String,
    val ramRequirementNote: String,
    val snapdragon6Gen1Compatibility: String,
    val isCompatibleWith6GbDevice: Boolean
) {
    LAMA_INPAINTING(
        slotKey = "lama_inpainting",
        title = "LaMa Inpainting (Text & Object Removal)",
        modelTypeLabel = "Inpainting / Reconstruction",
        supportedFormats = listOf("ONNX (.onnx)", "LiteRT (.tflite)"),
        recommendedArtifactHint = "LaMa Fourier Convolutions ONNX (512x512 tiled, FP16 or INT8 quantized)",
        ramRequirementNote = "~200 MB storage, ~550–750 MB peak RAM with 512x512 tiling & auto-unload",
        snapdragon6Gen1Compatibility = "Compatible (CPU XNNPACK / ORT tiled execution)",
        isCompatibleWith6GbDevice = true
    ),
    FOREGROUND_SEGMENTATION(
        slotKey = "foreground_segmentation",
        title = "Anime & Subject Segmentation",
        modelTypeLabel = "Foreground Matte / Background Removal",
        supportedFormats = listOf("ONNX (.onnx)", "LiteRT (.tflite)"),
        recommendedArtifactHint = "U2Net-P, ISNet-Anime, MODNet, or RMBG quantized ONNX/TFLite",
        ramRequirementNote = "~4–45 MB storage, ~180–350 MB peak RAM (plus ML Kit Subject Segmentation fallback)",
        snapdragon6Gen1Compatibility = "Highly Compatible (CPU / LiteRT GPU)",
        isCompatibleWith6GbDevice = true
    ),
    OUTPAINT_GENERATION(
        slotKey = "outpaint_generation",
        title = "Local Outpainting / Diffusion Generation",
        modelTypeLabel = "16:9 Canvas Extension / Image Generation",
        supportedFormats = listOf("ONNX (.onnx)", "LiteRT (.tflite)"),
        recommendedArtifactHint = "Lightweight quantized ONNX Inpainting/Outpainting (e.g. LaMa ONNX or mobile-compiled UNet subgraph)",
        ramRequirementNote = "Full FP32/FP16 Stable Diffusion 1.5 Inpainting requires ~2.5–4.5 GB model weights + >4 GB inference RAM (exceeds 6GB Android app heap/RAM limits without NPU-compiled INT4/W4A16 graphs)",
        snapdragon6Gen1Compatibility = "Partial: Tiled ONNX Inpainting supported; full unquantized SD 1.5 exceeds 6GB RAM budget",
        isCompatibleWith6GbDevice = false
    )
}

data class DeviceMemorySnapshot(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val maxAppHeapMb: Long,
    val isLowMemory: Boolean
)

data class ModelDownloadProgress(
    val slotKey: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val statusText: String
) {
    val progressFraction: Float
        get() = if (totalBytes > 0L) (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}
