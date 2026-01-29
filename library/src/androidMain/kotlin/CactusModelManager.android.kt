package com.cactus

import android.content.Context
import java.io.File

private val applicationContext: Context by lazy {
    CactusContextInitializer.getApplicationContext()
}

actual object CactusModelManager {
    private fun getModelsDir(): File {
        return File(applicationContext.filesDir, "models")
    }

    actual fun getDownloadedModels(): List<String> {
        val modelsDir = getModelsDir()
        if (!modelsDir.exists() || !modelsDir.isDirectory) {
            return emptyList()
        }

        return modelsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    actual fun isModelDownloaded(modelSlug: String): Boolean {
        val modelDir = File(getModelsDir(), modelSlug)
        return modelDir.exists() && modelDir.isDirectory
    }

    actual fun deleteModel(modelSlug: String): Boolean {
        val modelDir = File(getModelsDir(), modelSlug)
        if (!modelDir.exists()) {
            return false
        }

        return try {
            modelDir.deleteRecursively()
        } catch (e: Exception) {
            false
        }
    }

    actual fun getModelsDirectory(): String {
        return getModelsDir().absolutePath
    }
}
