package com.cactus

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import utils.IOSFileUtils
import utils.CactusLogger

@OptIn(ExperimentalForeignApi::class)
actual suspend fun downloadAndExtractModels(tasks: List<DownloadTask>): Boolean {
    return withContext(Dispatchers.Default) {
        try {
            val modelsDir = IOSFileUtils.getModelsDirectory() ?: return@withContext false
            for (task in tasks) {
                val modelFolderPath = "$modelsDir/${task.folder}"
                if (IOSFileUtils.fileExists(modelFolderPath)) {
                    CactusLogger.i("ModelDownloader", "Model folder already exists at $modelFolderPath")
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
                    CactusLogger.e("ModelDownloader", "Download and extraction failed for ${task.filename}")
                    return@withContext false
                }
                CactusLogger.i("ModelDownloader", "Download and extraction completed for ${task.filename}")
            }
            true
        } catch (e: Exception) {
            CactusLogger.e("ModelDownloader", "Download and extraction failed", throwable = e)
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
        CactusLogger.e("ModelDownloader", "Error checking downloaded models", throwable = e)
        false
    }
}
