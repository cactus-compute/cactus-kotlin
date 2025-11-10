package com.cactus.services

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.cactus.CactusContextInitializer
import com.cactus.CactusModel
import com.cactus.VoiceModel
import utils.CactusLogger
import kotlinx.serialization.json.Json

internal actual object ModelCache {
    private const val PREFS_NAME = "cactus_model_cache"
    private const val MODELS_KEY = "cactus_models"
    private const val VOICE_MODELS_KEY = "cactus_voice_models"

    private fun getSharedPreferences(): SharedPreferences {
        val context = CactusContextInitializer.getApplicationContext()
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    actual suspend fun saveModel(model: CactusModel) {
        try {
            val prefs = getSharedPreferences()
            val jsonString = Json.encodeToString(model)
            prefs.edit { putString("${MODELS_KEY}_${model.slug}", jsonString) }
        } catch (e: Exception) {
            CactusLogger.e("Error saving models to cache: ${e.message}", tag = "ModelCache", throwable = e)
        }
    }

    actual suspend fun loadModel(slug: String): CactusModel? {
        return try {
            val prefs = getSharedPreferences()
            val jsonString = prefs.getString("${MODELS_KEY}_${slug}", null)
            if (jsonString.isNullOrEmpty()) return null
            Json.decodeFromString<CactusModel>(jsonString)
        } catch (e: Exception) {
            CactusLogger.e("Error loading models from cache: ${e.message}", tag = "ModelCache", throwable = e)
            null
        }
    }

    actual suspend fun saveVoiceModels(models: List<VoiceModel>) {
        try {
            val prefs = getSharedPreferences()
            val jsonString = Json.encodeToString(models)
            prefs.edit { putString(VOICE_MODELS_KEY, jsonString) }
        } catch (e: Exception) {
            CactusLogger.e("Error saving voice models to cache: ${e.message}", tag = "ModelCache", throwable = e)
        }
    }

    actual suspend fun loadVoiceModels(): List<VoiceModel> {
        return try {
            val prefs = getSharedPreferences()
            val jsonString = prefs.getString(VOICE_MODELS_KEY, null)
            if (jsonString.isNullOrEmpty()) return emptyList()
            Json.decodeFromString<List<VoiceModel>>(jsonString)
        } catch (e: Exception) {
            CactusLogger.e("Error loading voice models from cache: ${e.message}", tag = "ModelCache", throwable = e)
            emptyList()
        }
    }
}
