package com.cactus

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual object CactusModelManager {
    private fun getModelsDir(): String? {
        val documentsDirectory = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String
        return documentsDirectory?.let { "$it/models" }
    }

    actual fun getDownloadedModels(): List<String> {
        val modelsDir = getModelsDir() ?: return emptyList()
        val fm = NSFileManager.defaultManager

        if (!fm.fileExistsAtPath(modelsDir)) {
            return emptyList()
        }

        val contents = fm.contentsOfDirectoryAtPath(modelsDir, null) as? List<*>
            ?: return emptyList()

        return contents
            .filterIsInstance<String>()
            .filter { fileName ->
                // Filter for directories only (model folders)
                val fullPath = "$modelsDir/$fileName"
                fm.fileExistsAtPath(fullPath) && !fileName.startsWith(".")
            }
            .sorted()
    }

    actual fun isModelDownloaded(modelSlug: String): Boolean {
        val modelsDir = getModelsDir() ?: return false
        val modelPath = "$modelsDir/$modelSlug"
        val fm = NSFileManager.defaultManager
        return fm.fileExistsAtPath(modelPath)
    }

    actual fun deleteModel(modelSlug: String): Boolean {
        val modelsDir = getModelsDir() ?: return false
        val modelPath = "$modelsDir/$modelSlug"
        val fm = NSFileManager.defaultManager

        if (!fm.fileExistsAtPath(modelPath)) {
            return false
        }

        return try {
            fm.removeItemAtPath(modelPath, null)
        } catch (e: Exception) {
            false
        }
    }

    actual fun getModelsDirectory(): String {
        return getModelsDir() ?: ""
    }
}
