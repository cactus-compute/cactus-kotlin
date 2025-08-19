package com.cactus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*

private var currentHandle: Long? = null

@OptIn(ExperimentalForeignApi::class)
actual suspend fun downloadModel(url: String, filename: String): Boolean {
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
            println("CactusLM: Failed to download model: ${e.message}")
            false
        }
    }
}

actual suspend fun initializeModel(path: String): Long? {
    return withContext(Dispatchers.Default) {
        try {
            println("CactusLM: Loading model: $path")
            val documentsDir = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory, NSUserDomainMask, true
            ).firstOrNull() as? String ?: return@withContext null

            val modelsDir = "$documentsDir/models"
            val modelPath = "$modelsDir/$path"

            if (!NSFileManager.defaultManager.fileExistsAtPath(modelPath)) {
                println("CactusLM: Model file not found: $modelPath")
                return@withContext null
            }

            println("CactusLM: Model file found: $modelPath")

            println("CactusLM: Initializing  context...")
            val handle = CactusContext.initContext(modelPath)
            if (handle != null) {
                currentHandle = handle
                println("CactusLM: Model loaded successfully with handle: $handle")
                handle
            } else {
                println("CactusLM: Failed to initialize context")
                null
            }
        } catch (e: Exception) {
            println("CactusLM: Exception loading model: ${e.message}")
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
            println("CactusLM: Generating  completion")
            val result = CactusContext.completion(handle, messages, options)
            if (result.success) {
                println("CactusLM:  completion successful")
                result
            } else {
                println("CactusLM:  completion failed: ${result.response}")
                null
            }
        } catch (e: Exception) {
            println("CactusLM: Exception in  completion: ${e.message}")
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
        println("CactusLM: Model unloaded")
    } catch (e: Exception) {
        println("CactusLM: Error unloading model: ${e.message}")
    }
}