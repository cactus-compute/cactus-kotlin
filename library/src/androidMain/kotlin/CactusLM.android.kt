package com.cactus

import android.content.Context
import android.util.Log
import com.sun.jna.Pointer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import kotlin.io.copyTo
import kotlin.io.outputStream
import kotlin.io.use

private var currentHandle: Long? = null

private val applicationContext: Context by lazy {
    CactusContextInitializer.getApplicationContext()
}

actual suspend fun downloadModel(url: String, filename: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val modelsDir = File(applicationContext.cacheDir, "models")
            if (!modelsDir.exists()) modelsDir.mkdirs()

            val modelFile = File(modelsDir, filename)
            if (modelFile.exists()) return@withContext true

            val urlConnection = URL(url).openConnection()
            urlConnection.getInputStream().use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            modelFile.exists()
        } catch (e: Exception) {
            Log.e("CactusLM", "Failed to download model: ${e.message}")
            false
        }
    }
}

actual suspend fun initializeModel(path: String): Long? {
    return withContext(Dispatchers.Default) {
        try {
            Log.d("CactusLM", "Loading model: $path")
            val modelsDir = File(applicationContext.cacheDir, "models")
            val modelFile = File(modelsDir, path)

            if (!modelFile.exists()) {
                Log.e("CactusLM", "Model file not found: ${modelFile.absolutePath}")
                return@withContext null
            }

            Log.d("CactusLM", "Model file found: ${modelFile.absolutePath}")

            Log.d("CactusLM", "Initializing  context...")
            val handle = CactusContext.initContext(modelFile.absolutePath)
            if (handle != null) {
                currentHandle = handle
                Log.d("CactusLM", " Model loaded successfully with handle: $handle")
                handle
            } else {
                Log.e("CactusLM", "Failed to initialize  context")
                null
            }
        } catch (e: Exception) {
            Log.e("CactusLM", "Exception loading  model: ${e.message}", e)
            null
        }
    }
}

actual suspend fun generateCompletion(
    handle: Long,
    messages: List<ChatMessage>,
    options: CactusCompletionParams
): CactusCompletionResult? {
    return withContext(Dispatchers.Default) {
        try {
            Log.d("CactusLM", "Generating  completion")
            val result = CactusContext.completion(handle, messages, options)
            if (result.success) {
                Log.d("CactusLM", " completion successful")
                result
            } else {
                Log.e("CactusLM", " completion failed: ${result.response}")
                null
            }
        } catch (e: Exception) {
            Log.e("CactusLM", "Exception in  completion: ${e.message}", e)
            null
        }
    }
}

actual fun uninitializeModel(handle: Long) {
    try {
        CactusContext.freeContext(handle)
        if (currentHandle == handle) {
            currentHandle = null
        }
        Log.d("CactusLM", " Model unloaded")
    } catch (e: Exception) {
        Log.e("CactusLM", "Error unloading  model: ${e.message}")
    }
}
