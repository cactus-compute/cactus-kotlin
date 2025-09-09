package com.cactus

import com.cactus.services.Supabase
import com.cactus.services.Telemetry

class CactusLM {
    private var _handle: Long? = null
    private var _lastDownloadedModel: String? = null

    suspend fun downloadModel(
        model: String = "qwen3-0.6"
    ): Boolean {
        val url = Supabase.getModelDownloadUrl(model)
        if (url == null) {
            println("No download URL found for model: $model")
            return false
        }
        val actualFilename = url.split('?').first().split('/').last()
        val success = _downloadAndExtractModel(url, actualFilename, model)
        if (success) {
            _lastDownloadedModel = model
        }
        return success
    }

    suspend fun initializeModel(params: CactusInitParams): Boolean {
        val modelFolder = params.model ?: _lastDownloadedModel ?: "qwen3-0.6"
        val modelPath = getModelPath(modelFolder)

        _handle = CactusContext.initContext(modelPath, (params.contextSize ?: 2048).toUInt())
        _lastDownloadedModel = modelFolder
        if (Telemetry.isInitialized) {
            val updatedParams = params.copy(model = modelFolder)
            val message = if (_handle != null) null else "Failed to initialize model at path: $modelPath"
            Telemetry.instance?.logInit(_handle != null, updatedParams, message)
        }
        return _handle != null
    }

    suspend fun generateCompletion(
        messages: List<ChatMessage>,
        params: CactusCompletionParams,
        onToken: CactusStreamingCallback? = null
    ): CactusCompletionResult? {
        val currentHandle = _handle
        if (currentHandle == null) {
            if (Telemetry.isInitialized) {
                Telemetry.instance?.logCompletion(
                    CactusCompletionResult(success = false),
                    CactusInitParams(), 
                    message = "Context not initialized",
                )
            }
            return null
        }

        try {
            val result = CactusContext.completion(currentHandle, messages, params, onToken)
            
            // Track telemetry for successful completions (if telemetry is initialized)
            if (result.success && Telemetry.isInitialized) {
                val initParams = CactusInitParams(
                    model = _lastDownloadedModel,
                )
                Telemetry.instance?.logCompletion(result, initParams)
            }
            
            return result
        } catch (e: Exception) {
            // Track telemetry for errors (if telemetry is initialized)
            if (Telemetry.isInitialized) {
                val initParams = CactusInitParams(
                    model = _lastDownloadedModel,
                )
                Telemetry.instance?.logCompletion(CactusCompletionResult(success = false), initParams, message = e.message)
            }
            throw e
        }
    }

    fun unload() {
        val currentHandle = _handle
        if (currentHandle != null) {
            CactusContext.freeContext(currentHandle)
            _handle = null
        }
    }

    fun isLoaded(): Boolean = _handle != null

    private suspend fun _downloadAndExtractModel(url: String, filename: String, folder: String): Boolean {
        return downloadAndExtractModel(url, filename, folder)
    }
}

expect suspend fun downloadAndExtractModel(url: String, filename: String, folder: String): Boolean
expect fun getModelPath(modelFolder: String): String
