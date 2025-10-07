package com.cactus

interface SpeechRecognitionProvider {
    suspend fun initialize(modelFolder: String, spkModelFolder: String): Boolean
    suspend fun requestPermissions(): Boolean
    suspend fun performRecognition(params: SpeechRecognitionParams, filePath: String? = null): SpeechRecognitionResult?
    fun stop()
    fun isAvailable(): Boolean
    fun isAuthorized(): Boolean
}

expect fun getSpeechRecognitionProvider(provider: TranscriptionProvider): SpeechRecognitionProvider