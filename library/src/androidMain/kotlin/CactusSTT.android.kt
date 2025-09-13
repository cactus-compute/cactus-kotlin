package com.cactus

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

private val applicationContext: Context by lazy {
    CactusContextInitializer.getApplicationContext()
}

actual suspend fun downloadSTTModel(modelUrl: String, modelName: String, slug: String, spkModelUrl: String, spkModelName: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val modelsDir = File(applicationContext.filesDir, "models/vosk")
            if (!modelsDir.exists()) modelsDir.mkdirs()

            // Download model
            val modelFile = File(modelsDir, modelName)
            if (modelFile.exists()) return@withContext true

            val modelConnection = URL(modelUrl).openConnection()
            modelConnection.getInputStream().use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (modelName.endsWith(".zip")) {
                val slugDir = File(modelsDir, slug)
                extractZip(modelFile, slugDir)
                modelFile.delete()
            }

            // Download speaker model
            val spkModelConnection = URL(spkModelUrl).openConnection()
            val spkModelFile = File(modelsDir, spkModelName)
            if (spkModelFile.exists()) return@withContext true
            spkModelConnection.getInputStream().use { input ->
                spkModelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (spkModelName.endsWith(".zip")) {
                val spkModelDir = File(modelsDir, spkModelName.replace(".zip", ""))
                extractZip(spkModelFile, spkModelDir)
                spkModelFile.delete()
            }

            true
        } catch (e: Exception) {
            println("Error downloading STT model: ${e.message}")
            e.printStackTrace()
            false
        }
    }
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

actual suspend fun modelExists(modelName: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val modelsDir = File(applicationContext.filesDir, "models/vosk")
            if (!modelsDir.exists()) return@withContext false

            val modelDir = File(modelsDir, modelName)
            val modelExists = modelDir.exists() && modelDir.isDirectory

            println("Checking models - Main model ($modelName): $modelExists")

            modelExists
        } catch (e: Exception) {
            println("Error checking downloaded models: ${e.message}")
            false
        }
    }
}

private fun extractZip(zipFile: File, targetDir: File) {
    try {
        if (!targetDir.exists()) targetDir.mkdirs()
        
        java.util.zip.ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    // Strip the first directory level if it exists (removes the top-level folder from zip)
                    val relativePath = if (entry.name.contains('/')) {
                        entry.name.substringAfter('/')
                    } else {
                        entry.name
                    }
                    
                    val entryFile = File(targetDir, relativePath)
                    entryFile.parentFile?.mkdirs()
                    entryFile.outputStream().use { output ->
                        zip.copyTo(output)
                    }
                }
                entry = zip.nextEntry
            }
        }
    } catch (e: Exception) {
        println("Error extracting zip file: ${e.message}")
        e.printStackTrace()
    }
}