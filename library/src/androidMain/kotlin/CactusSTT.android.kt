package com.cactus

import android.content.Context

private val applicationContext: Context by lazy {
    CactusContextInitializer.getApplicationContext()
}

actual suspend fun initializeSTT(modelFolder: String, spkModelFolder: String): Boolean {
    return try {
        val initialized = initializeSpeechRecognition(modelFolder, spkModelFolder)
        if (initialized) {
            println("Android Speech recognition initialized successfully")
        } else {
            println("Failed to initialize Android speech recognition")
        }
        initialized
    } catch (e: Exception) {
        println("Error initializing Android speech recognition: $e")
        false
    }
}

actual suspend fun performSTT(params: SpeechRecognitionParams, filePath: String?): SpeechRecognitionResult? {
    println("CactusSTT.performSTT() called with $params")
    return try {
        if (!isSpeechRecognitionAvailable()) {
            println("Vosk model not loaded on this device")
            return SpeechRecognitionResult(
                success = false
            )
        }

        val speechResult = performSpeechRecognition(params, filePath)
        println("CactusSTT.performSTT() got result: ${speechResult?.text}")
        speechResult
    } catch (e: Exception) {
        println("CactusSTT.performSTT() error: ${e.message}")
        e.printStackTrace()
        SpeechRecognitionResult(
            success = false,
            text = "Error during speech recognition: ${e.message}"
        )
    }
}

actual fun stopSTT() {
    stopSpeechRecognition()
}