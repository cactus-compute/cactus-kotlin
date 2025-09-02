package com.cactus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.*

private var currentVLMHandle: Long? = null

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual suspend fun downloadVLMModel(url: String, filename: String): Boolean {
    return withContext(Dispatchers.Default) {
        try {
            val documentsDir = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory, NSUserDomainMask, true
            ).firstOrNull() as? String ?: return@withContext false
            
            val modelsDir = "$documentsDir/models"
            NSFileManager.defaultManager.createDirectoryAtPath(
                modelsDir, true, null, null
            )
            
            val modelPath = "$modelsDir/$filename"
            if (NSFileManager.defaultManager.fileExistsAtPath(modelPath)) {
                return@withContext true
            }
            
            val nsUrl = NSURL(string = url) ?: return@withContext false
            val data = NSData.dataWithContentsOfURL(nsUrl) ?: return@withContext false
            
            data.writeToFile(modelPath, true)
        } catch (e: Exception) {
            false
        }
    }
}

actual suspend fun loadVLMModel(modelPath: String, mmprojPath: String, threads: Int, contextSize: Int, batchSize: Int, gpuLayers: Int): Long? {
    return withContext(Dispatchers.Default) {
        try {
            val documentsDir = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory, NSUserDomainMask, true
            ).firstOrNull() as? String ?: return@withContext null
            
            val fullModelPath = "$documentsDir/models/$modelPath"
            val fullMmprojPath = "$documentsDir/models/$mmprojPath"
            
            if (!NSFileManager.defaultManager.fileExistsAtPath(fullModelPath) ||
                !NSFileManager.defaultManager.fileExistsAtPath(fullMmprojPath)) {
                return@withContext null
            }
            
            val useGpu = when (gpuLayers) {
                0 -> false
                99 -> true
                else -> false
            }
            
            val actualGpuLayers = when (gpuLayers) {
                0 -> 0
                99 -> 99
                else -> 0
            }
            
            val params = CactusInitParams(
                modelPath = fullModelPath,
                nCtx = contextSize,
                nThreads = threads,
                nBatch = batchSize,
                nGpuLayers = actualGpuLayers
            )
            
            val handle = CactusContext.initContext(params)
            if (handle != null) {
                val mmResult = CactusContext.initMultimodal(handle, fullMmprojPath, useGpu)
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