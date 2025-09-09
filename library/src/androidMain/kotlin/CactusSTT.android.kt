package com.cactus

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

private val applicationContext: Context by lazy {
    CactusContextInitializer.getApplicationContext()
}

actual suspend fun downloadSTTModel(modelUrl: String, modelName: String, spkModelUrl: String, spkModelName: String): Boolean {
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
                extractZip(modelFile, modelsDir)
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
                extractZip(spkModelFile, modelsDir)
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
    val result = initializeSpeechRecognition(modelFolder, spkModelFolder)
    println("CactusSTT.initializeSTT() result: $result")
    return result
}

actual suspend fun performSTT(params: SpeechRecognitionParams, filePath: String?): SpeechRecognitionResult? {
    println("CactusSTT.performSTT() called with $params")
    return try {
        val speechResult = performSpeechRecognition(params, filePath)
        println("CactusSTT.performSTT() got result: ${speechResult?.text}")
        speechResult
    } catch (e: Exception) {
        println("CactusSTT.performSTT() error: ${e.message}")
        e.printStackTrace()
        null
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
        java.util.zip.ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val entryFile = File(targetDir, entry.name)
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