package com.cactus

import utils.IOSFileUtils
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.*

@OptIn(ExperimentalForeignApi::class)
actual suspend fun downloadSTTModel(
    modelUrl: String,
    modelName: String,
    slug: String,
    spkModelUrl: String,
    spkModelName: String
): Boolean = withContext(Dispatchers.Default) {
    try {
        val cachesDir = IOSFileUtils.getCachesDirectory() ?: return@withContext false
        val modelsDir = "$cachesDir/models/vosk"
        IOSFileUtils.createDirectoryIfNeeded(modelsDir)

        val modelOk = IOSFileUtils.ensureFilePresentOrDownloadedAndUnzipped(
            urlString = modelUrl,
            fileName = modelName,
            baseDir = modelsDir,
            extractedDirName = slug
        )
        if (!modelOk) return@withContext false

        val spkOk = IOSFileUtils.ensureFilePresentOrDownloadedAndUnzipped(
            urlString = spkModelUrl,
            fileName = spkModelName,
            baseDir = modelsDir
        )
        if (!spkOk) return@withContext false
        true
    } catch (e: Exception) {
        println("Error downloading STT model: ${e.message}")
        e.printStackTrace()
        false
    }
}

actual suspend fun initializeSTT(modelFolder: String, spkModelFolder: String): Boolean {
    return try {
        val initialized = initializeSpeechRecognition(modelFolder, spkModelFolder)
        if (initialized) {
            println("iOS Speech recognition initialized successfully")
        } else {
            println("Failed to initialize iOS speech recognition")
        }
        initialized
    } catch (e: Exception) {
        println("Error initializing iOS speech recognition: $e")
        false
    }
}

actual suspend fun performSTT(params: SpeechRecognitionParams, filePath: String?): SpeechRecognitionResult? {
    println("CactusSTT.performSTT() called with $params")
    return try {
        // Request microphone permissions if needed
        if(filePath == null && !isSpeechRecognitionAuthorized()) {
            val permissionGranted = requestSpeechPermissions()
            if (!permissionGranted) {
                println("Speech recognition permission not granted")
                return null
            }
            println("Speech recognition permission granted")
        }

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

@OptIn(ExperimentalForeignApi::class)
actual suspend fun modelExists(modelName: String): Boolean = withContext(Dispatchers.Default) {
    try {
        // Use the same directory as download function
        val cachesDir = IOSFileUtils.getCachesDirectory() ?: return@withContext false

        val modelsDir = "$cachesDir/models/vosk"
        val modelPath = "$modelsDir/$modelName"
        val modelExists = IOSFileUtils.fileExists(modelPath)

        println("Checking models - Main model ($modelName): $modelExists")
        
        modelExists
    } catch (e: Exception) {
        println("Error checking downloaded models: $e")
        false
    }
}