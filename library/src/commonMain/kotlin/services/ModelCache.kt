package com.cactus.services

import com.cactus.CactusModel
import com.cactus.VoiceModel

internal expect object ModelCache {
    suspend fun saveModels(models: List<CactusModel>)
    suspend fun loadModels(): List<CactusModel>
    suspend fun saveVoiceModels(models: List<VoiceModel>)
    suspend fun loadVoiceModels(): List<VoiceModel>
}
