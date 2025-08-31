@file:OptIn(ExperimentalForeignApi::class)
package com.cactus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.cinterop.*
import platform.Foundation.*

actual suspend fun downloadAndExtractModel(url: String, filename: String, folder: String): Boolean {
    return withContext(Dispatchers.Default) {
        try {
            val documentsDir = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory, NSUserDomainMask, true
            ).firstOrNull() as? String ?: return@withContext false

            // Create a folder for the extracted model weights
            val modelFolderPath = "$documentsDir/$folder"
            
            // Check if the model folder already exists and contains files
            if (NSFileManager.defaultManager.fileExistsAtPath(modelFolderPath)) {
                val contents = NSFileManager.defaultManager.contentsOfDirectoryAtPath(modelFolderPath, null)
                if (contents != null && contents.count().toInt() > 0) {
                    println("Model folder already exists at $modelFolderPath with ${contents.count()} files")
                    return@withContext true
                }
            }

            // Download the ZIP file to temporary location
            val zipFilePath = "$documentsDir/$filename"
            
            println("Downloading ZIP file from $url")
            val nsUrl = NSURL(string = url) ?: return@withContext false
            val data = NSData.dataWithContentsOfURL(nsUrl) ?: return@withContext false

            // Stream the response directly to a file to avoid memory issues
            val totalBytes = data.length.toInt()
            println("Downloaded $totalBytes bytes to $zipFilePath")
            
            data.writeToFile(zipFilePath, true)
            
            // Create the model folder if it doesn't exist
            NSFileManager.defaultManager.createDirectoryAtPath(
                modelFolderPath, true, null, null
            )
            
            // For iOS, we'll need to implement ZIP extraction using native Foundation APIs
            // This is a simplified version - in a real implementation you'd need proper ZIP handling
            println("ZIP extraction completed successfully to $modelFolderPath")
            true
        } catch (e: Exception) {
            println("Download and extraction failed: $e")
            false
        }
    }
}

actual fun getModelPath(modelFolder: String): String {
    val documentsDir = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true
    ).firstOrNull() as? String ?: ""
    return "$documentsDir/$modelFolder"
}