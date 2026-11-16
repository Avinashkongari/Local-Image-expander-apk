package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.ExportFormat
import com.example.domain.model.OutpaintPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.axionDataStore: DataStore<Preferences> by preferencesDataStore(name = "axion_settings")

data class StudioSettings(
    val defaultOutpaintPreset: OutpaintPreset = OutpaintPreset.HD_720P,
    val defaultExportFormat: ExportFormat = ExportFormat.PNG,
    val jpgQuality: Int = 95,
    val maxWorkingDimension: Int = 1920,
    val autoUnloadModelAfterInference: Boolean = true,
    val inferenceThreads: Int = 4,
    val enableTilingForLargeImages: Boolean = true
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val DEFAULT_PRESET = stringPreferencesKey("default_outpaint_preset")
        val DEFAULT_EXPORT_FORMAT = stringPreferencesKey("default_export_format")
        val MAX_WORKING_DIMENSION = intPreferencesKey("max_working_dimension")
        val AUTO_UNLOAD_MODEL = booleanPreferencesKey("auto_unload_model")
        val INFERENCE_THREADS = intPreferencesKey("inference_threads")
        val ENABLE_TILING = booleanPreferencesKey("enable_tiling")
    }

    val settingsFlow: Flow<StudioSettings> = context.axionDataStore.data.map { prefs ->
        val presetName = prefs[Keys.DEFAULT_PRESET] ?: OutpaintPreset.HD_720P.name
        val exportName = prefs[Keys.DEFAULT_EXPORT_FORMAT] ?: ExportFormat.PNG.name
        StudioSettings(
            defaultOutpaintPreset = runCatching { OutpaintPreset.valueOf(presetName) }
                .getOrDefault(OutpaintPreset.HD_720P),
            defaultExportFormat = runCatching { ExportFormat.valueOf(exportName) }
                .getOrDefault(ExportFormat.PNG),
            maxWorkingDimension = prefs[Keys.MAX_WORKING_DIMENSION] ?: 1920,
            autoUnloadModelAfterInference = prefs[Keys.AUTO_UNLOAD_MODEL] ?: true,
            inferenceThreads = (prefs[Keys.INFERENCE_THREADS] ?: 4).coerceIn(1, 8),
            enableTilingForLargeImages = prefs[Keys.ENABLE_TILING] ?: true
        )
    }

    suspend fun setDefaultOutpaintPreset(preset: OutpaintPreset) {
        context.axionDataStore.edit { it[Keys.DEFAULT_PRESET] = preset.name }
    }

    suspend fun setDefaultExportFormat(format: ExportFormat) {
        context.axionDataStore.edit { it[Keys.DEFAULT_EXPORT_FORMAT] = format.name }
    }

    suspend fun setMaxWorkingDimension(maxDim: Int) {
        context.axionDataStore.edit { it[Keys.MAX_WORKING_DIMENSION] = maxDim }
    }

    suspend fun setAutoUnloadModel(enabled: Boolean) {
        context.axionDataStore.edit { it[Keys.AUTO_UNLOAD_MODEL] = enabled }
    }

    suspend fun setInferenceThreads(threads: Int) {
        context.axionDataStore.edit { it[Keys.INFERENCE_THREADS] = threads.coerceIn(1, 8) }
    }

    suspend fun setEnableTiling(enabled: Boolean) {
        context.axionDataStore.edit { it[Keys.ENABLE_TILING] = enabled }
    }
}
