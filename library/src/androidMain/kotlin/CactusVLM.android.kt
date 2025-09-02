package com.cactus

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import android.util.Log

private var currentVLMHandle: Long? = null

private val applicationContext: Context by lazy {
    CactusContextInitializer.getApplicationContext()
}

actual suspend fun downloadVLMModel(url: String, filename: String): Boolean {
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
            false
        }
    }
}

actual suspend fun loadVLMModel(modelPath: String, mmprojPath: String, threads: Int, contextSize: Int, batchSize: Int, gpuLayers: Int): Long? {
    return withContext(Dispatchers.Default) {
        try {
            val modelsDir = File(applicationContext.cacheDir, "models")
            val modelFile = File(modelsDir, modelPath)
            val mmprojFile = File(modelsDir, mmprojPath)
            
            if (!modelFile.exists() || !mmprojFile.exists()) return@withContext null
            
            val actualGpuLayers = 0
            if (gpuLayers == 99) {
                Log.w("CactusVLM", "GPU acceleration requested but not supported on Android. Using CPU.")
            }
            
            val params = CactusInitParams(
                modelPath = modelFile.absolutePath,
                nCtx = contextSize,
                nThreads = threads,
                nBatch = batchSize,
                nGpuLayers = actualGpuLayers
            )
            
            val handle = CactusContext.initContext(params)
            if (handle != null) {
                val mmResult = CactusContext.initMultimodal(handle, mmprojFile.absolutePath, false)
                if (mmResult == 0) {
                    currentVLMHandle = handle
                    handle
                } else {
                    CactusContext.freeContext(handle)
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

actual suspend fun generateVLMCompletion(handle: Long, prompt: String, imagePath: String, maxTokens: Int, temperature: Float, topP: Float): String? {
    return withContext(Dispatchers.Default) {
        try {
            val params = CactusCompletionParams(
                prompt = prompt,
                nPredict = maxTokens,
                temperature = temperature.toDouble(),
                topP = topP.toDouble()
            )
            
            val mediaPaths = listOf(imagePath)
            val result = CactusContext.multimodalCompletion(handle, params, mediaPaths)
            result.text
        } catch (e: Exception) {
            null
        }
    }
}

actual fun unloadVLMModel(handle: Long) {
    try {
        CactusContext.freeContext(handle)
        if (currentVLMHandle == handle) {
            currentVLMHandle = null
        }
    } catch (e: Exception) {
    }
} 