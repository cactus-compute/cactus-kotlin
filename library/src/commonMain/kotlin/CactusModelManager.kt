package com.cactus

/**
 * Singleton for managing downloaded AI models without requiring a CactusLM/CactusSTT instance.
 * Provides utilities to list, check, and delete models from local storage.
 */
expect object CactusModelManager {
    /**
     * Returns a list of model slugs that are currently downloaded on disk.
     * @return List of model slugs (e.g., ["qwen3-0.6", "llama-3.2-1b"])
     */
    fun getDownloadedModels(): List<String>

    /**
     * Checks if a specific model exists in local storage.
     * @param modelSlug The model identifier (e.g., "qwen3-0.6")
     * @return true if model is downloaded, false otherwise
     */
    fun isModelDownloaded(modelSlug: String): Boolean

    /**
     * Deletes a model from local storage.
     * @param modelSlug The model identifier to delete
     * @return true if model was deleted, false if it didn't exist
     */
    fun deleteModel(modelSlug: String): Boolean

    /**
     * Gets the absolute path to the models directory.
     * Useful for debugging or advanced use cases.
     * @return Absolute path to models storage directory
     */
    fun getModelsDirectory(): String
}
