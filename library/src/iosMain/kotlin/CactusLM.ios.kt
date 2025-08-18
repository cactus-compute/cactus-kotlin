package com.cactus

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile
import kotlin.collections.firstOrNull

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
            false
        }
    }
}

actual suspend fun initializeModel(path: String): Long? {
    return withContext(Dispatchers.Default) {
        null
    }
}

actual suspend fun generateCompletion(handle: Long, messages: List<ChatMessage>, options: CactusCompletionParams): CactusCompletionResult? {
    return withContext(Dispatchers.Default) {
        null
    }
}

actual fun uninitializeModel(handle: Long) {
    try {
        CactusContext.freeContext(handle)
        if (currentHandle == handle) {
            currentHandle = null
        }
    } catch (e: Exception) {
    }
}