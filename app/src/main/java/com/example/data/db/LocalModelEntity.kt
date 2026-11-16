package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_models")
data class LocalModelEntity(
    @PrimaryKey val slotKey: String,
    val displayName: String,
    val modelFormat: String, // "ONNX" or "LITERT"
    val filePath: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val inputSignature: String,
    val outputSignature: String,
    val isInstalled: Boolean,
    val installedAt: Long,
    val sourceInfo: String
)
