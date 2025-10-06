package com.cactus

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import utils.IOSFileUtils

@OptIn(ExperimentalForeignApi::class)
actual suspend fun downloadAndExtractModels(tasks: List<DownloadTask>): Boolean {
    return withContext(Dispatchers.Default) {
        try {
            val modelsDir = IOSFileUtils.getModelsDirectory() ?: return@withContext false
            for (task in tasks) {
                val modelFolderPath = "$modelsDir/${task.folder}"
                if (IOSFileUtils.fileExists(modelFolderPath)) {
                    println("Model folder already exists at $modelFolderPath")
                    continue
                }

                IOSFileUtils.createDirectoryIfNeeded(modelsDir)

                val success = IOSFileUtils.ensureFilePresentOrDownloadedAndUnzipped(
                    urlString = task.url,
                    fileName = task.filename,
                    baseDir = modelsDir,
                    extractedDirName = task.folder
                )

                if (!success) {
                    println("Download and extraction failed for ${task.filename}")
                    return@withContext false
                }
                println("Download and extraction completed for ${task.filename}")
            }
            true
        } catch (e: Exception) {
            println("Download and extraction failed: $e")
            false
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun modelExists(modelName: String): Boolean = withContext(Dispatchers.Default) {
    try {
        val modelsDir = IOSFileUtils.getModelsDirectory() ?: return@withContext false
        val modelPath = "$modelsDir/$modelName"
        val modelExists = IOSFileUtils.fileExists(modelPath)

        modelExists
    } catch (e: Exception) {
        println("Error checking downloaded models: $e")
        false
    }
}
