@file:OptIn(ExperimentalForeignApi::class)
package com.cactus

import utils.IOSFileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.cinterop.*
import platform.Foundation.*

actual suspend fun downloadAndExtractModel(url: String, filename: String, folder: String): Boolean {
    return withContext(Dispatchers.Default) {
        try {
            val documentsDir = IOSFileUtils.getDocumentsDirectory() ?: return@withContext false
            val modelFolderPath = "$documentsDir/$folder"
            if (IOSFileUtils.fileExists(modelFolderPath)) {
                println("Model folder already exists at $modelFolderPath")
                return@withContext true
            }

            IOSFileUtils.createDirectoryIfNeeded(modelFolderPath)
            
            val success = IOSFileUtils.ensureFilePresentOrDownloadedAndUnzipped(
                urlString = url,
                fileName = filename,
                baseDir = documentsDir
            )
            
            if (success) {
                println("ZIP extraction completed successfully to $modelFolderPath")
            } else {
                println("Download and extraction failed")
            }
            success
        } catch (e: Exception) {
            println("Download and extraction failed: $e")
            false
        }
    }
}

actual fun getModelPath(modelFolder: String): String {
    val documentsDir = IOSFileUtils.getDocumentsDirectory() ?: ""
    return "$documentsDir/$modelFolder"
}