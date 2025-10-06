package com.cactus

data class DownloadTask(
    val url: String, 
    val filename: String, 
    val folder: String, 
    val requiresExtraction: Boolean = true
)

object ModelDownloader {
    suspend fun <T> updateDownloadStatus(
        models: List<T>,
        nameSelector: (T) -> String,
        statusSetter: (T, Boolean) -> Unit
    ) {
        for (model in models) {
            statusSetter(model, modelExists(nameSelector(model)))
        }
    }
}

expect suspend fun modelExists(modelName: String): Boolean
expect suspend fun downloadAndExtractModels(tasks: List<DownloadTask>): Boolean
