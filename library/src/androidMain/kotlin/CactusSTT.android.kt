package com.cactus

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

private val applicationContext: Context by lazy {
    CactusContextInitializer.getApplicationContext()
}

actual suspend fun downloadSTTModel(url: String, filename: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val modelsDir = File(applicationContext.cacheDir, "models/vosk")
            if (!modelsDir.exists()) modelsDir.mkdirs()
            
            val modelFile = File(modelsDir, filename)
            if (modelFile.exists()) return@withContext true
            
            val urlConnection = URL(url).openConnection()
            urlConnection.getInputStream().use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            if (filename.endsWith(".zip")) {
                extractZip(modelFile, modelsDir)
                modelFile.delete()
            }
            
            true
        } catch (e: Exception) {
            false
        }
    }
}

actual suspend fun initializeSTT(): Boolean {
    val result = initializeSpeechRecognition()
    println("CactusSTT.initializeSTT() result: $result")
    return result
}

actual suspend fun performSTT(language: String, maxDuration: Int): STTResult? {
    println("CactusSTT.performSTT() called with language: $language, maxDuration: $maxDuration")
    return try {
        val params = SpeechRecognitionParams(
            language = language,
            enablePartialResults = false,
            maxDuration = maxDuration
        )
        val speechResult = performSpeechRecognition(params)
        println("CactusSTT.performSTT() got result: ${speechResult?.text}")
        speechResult?.let {
            STTResult(
                text = it.text,
                confidence = it.confidence,
                isPartial = it.isPartial
            )
        }
    } catch (e: Exception) {
        println("CactusSTT.performSTT() error: ${e.message}")
        e.printStackTrace()
        null
    }
}

actual suspend fun performFileSTT(audioPath: String, language: String): STTResult? {
    return try {
        val params = SpeechRecognitionParams(
            language = language,
            enablePartialResults = false,
            maxDuration = 60
        )
        val speechResult = recognizeSpeechFromFile(audioPath, params)
        speechResult?.let {
            STTResult(
                text = it.text,
                confidence = it.confidence,
                isPartial = it.isPartial
            )
        }
    } catch (e: Exception) {
        null
    }
}

actual fun stopSTT() {
    stopSpeechRecognition()
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
    }
} 