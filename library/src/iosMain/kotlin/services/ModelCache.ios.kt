package com.cactus.services

import com.cactus.CactusModel
import com.cactus.VoiceModel
import kotlinx.serialization.json.Json
import platform.Foundation.*

internal actual object ModelCache {
    private const val MODELS_KEY = "cactus_models"
    private const val VOICE_MODELS_KEY = "cactus_voice_models"

    actual suspend fun saveModel(model: CactusModel) {
        try {
            val userDefaults = NSUserDefaults.standardUserDefaults
            val jsonString = Json.encodeToString(model)
            userDefaults.setObject(jsonString, forKey = "${MODELS_KEY}_${model.slug}")
            userDefaults.synchronize()
        } catch (e: Exception) {
            println("Error saving models to cache: $e")
        }
    }

    actual suspend fun loadModel(slug: String): CactusModel? {
        return try {
            val userDefaults = NSUserDefaults.standardUserDefaults
            val jsonString = userDefaults.stringForKey("${MODELS_KEY}_${slug}")
            if (jsonString.isNullOrEmpty()) return null
            Json.decodeFromString<CactusModel>(jsonString)
        } catch (e: Exception) {
            println("Error loading models from cache: $e")
            null
        }
    }

    actual suspend fun saveVoiceModels(models: List<VoiceModel>) {
        try {
            val userDefaults = NSUserDefaults.standardUserDefaults
            val jsonString = Json.encodeToString(models)
            userDefaults.setObject(jsonString, forKey = VOICE_MODELS_KEY)
            userDefaults.synchronize()
        } catch (e: Exception) {
            println("Error saving voice models to cache: $e")
        }
    }

    actual suspend fun loadVoiceModels(): List<VoiceModel> {
        return try {
            val userDefaults = NSUserDefaults.standardUserDefaults
            val jsonString = userDefaults.stringForKey(VOICE_MODELS_KEY)
            if (jsonString.isNullOrEmpty()) return emptyList()
            Json.decodeFromString<List<VoiceModel>>(jsonString)
        } catch (e: Exception) {
            println("Error loading voice models from cache: $e")
            emptyList()
        }
    }
}
