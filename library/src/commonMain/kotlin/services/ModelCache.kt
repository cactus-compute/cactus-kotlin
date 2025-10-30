package com.cactus.services

import com.cactus.CactusModel
import com.cactus.VoiceModel

internal expect object ModelCache {
    suspend fun saveModel(model: CactusModel)
    suspend fun loadModel(slug: String): CactusModel?
    suspend fun saveVoiceModels(models: List<VoiceModel>)
    suspend fun loadVoiceModels(): List<VoiceModel>
}
