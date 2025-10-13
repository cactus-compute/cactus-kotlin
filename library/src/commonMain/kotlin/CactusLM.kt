package com.cactus

import com.cactus.models.toToolsJson
import com.cactus.services.Supabase
import com.cactus.services.Telemetry
import kotlin.time.TimeSource

class CactusLM {
    private var _handle: Long? = null
    private var _lastDownloadedModel: String = "qwen3-0.6"
    private var models = listOf<CactusModel>()
    private val openRouterModule = OpenRouterModule()
    private val timeSource = TimeSource.Monotonic

    suspend fun downloadModel(
        model: String = _lastDownloadedModel
    ): Boolean {
        if (modelExists(model)) {
            return true
        }
        val currentModel  = getModel(model) ?: run {
            println("No data found for model: $model")
            return false
        }
        val actualFilename = currentModel.download_url.split('?').first().split('/').last()
        val task = DownloadTask(currentModel.download_url, actualFilename, currentModel.slug)
        val success = downloadAndExtractModels(listOf(task))
        if (success) {
            _lastDownloadedModel = currentModel.slug
        }
        return success
    }

    suspend fun initializeModel(params: CactusInitParams): Boolean {
        val modelFolder = params.model ?: _lastDownloadedModel
        val modelPath = getModelPath(modelFolder)

        _handle = CactusContext.initContext(modelPath, (params.contextSize ?: 2048).toUInt())
        _lastDownloadedModel = modelFolder
        
        // If initialization failed and model is not downloaded, try to download first
        if (_handle == null && !modelExists(modelFolder)) {
            println("Failed to initialize model context with model at $modelPath, trying to download the model first.")
            val downloadSuccess = downloadModel(model = modelFolder)
            if (downloadSuccess) {
                return initializeModel(params)
            }
        }
        
        if (Telemetry.isInitialized) {
            val updatedParams = params.copy(model = modelFolder)
            val message = if (_handle != null) null else "Failed to initialize model at path: $modelPath"
            Telemetry.instance?.logInit(_handle != null, updatedParams, message)
        }
        
        if (_handle == null) {
            throw Exception("Failed to initialize model context with model at $modelPath")
        }
        
        return _handle != null
    }

    suspend fun generateCompletion(
        messages: List<ChatMessage>,
        params: CactusCompletionParams = CactusCompletionParams(),
        onToken: CactusStreamingCallback? = null
    ): CactusCompletionResult? {
        val startTime = timeSource.markNow()
        var result: CactusCompletionResult?

        val localCompletion = suspend local@{
            val model = params.model ?: _lastDownloadedModel
            val currentHandle = getValidatedHandle(model)
            
            if (currentHandle == null) {
                if (Telemetry.isInitialized) {
                    Telemetry.instance?.logCompletion(
                        CactusCompletionResult(success = false),
                        CactusInitParams(model = model),
                        message = "Context not initialized",
                    )
                }
                return@local null
            }

            try {
                val toolsJson = params.tools.toToolsJson()
                println("Tools JSON: $toolsJson")
                CactusContext.completion(currentHandle, messages, params, toolsJson, onToken)
            } catch (e: Exception) {
                if (Telemetry.isInitialized) {
                    val initParams = CactusInitParams(model = _lastDownloadedModel)
                    Telemetry.instance?.logCompletion(CactusCompletionResult(success = false), initParams, message = e.message)
                }
                throw e
            }
        }

        val remoteCompletion = suspend {
            if (params.cactusToken != null) {
                openRouterModule.generateCompletion(messages, params, params.cactusToken, onToken)
            } else {
                println("Remote inference requires an apiKey.")
                null
            }
        }

        result = when (params.mode) {
            InferenceMode.LOCAL -> localCompletion()
            InferenceMode.REMOTE -> remoteCompletion()
            InferenceMode.LOCAL_FIRST -> {
                val localResult = localCompletion()
                if (localResult?.success == true) localResult else remoteCompletion()
            }
            InferenceMode.REMOTE_FIRST -> {
                val remoteResult = remoteCompletion()
                if (remoteResult?.success == true) remoteResult else localCompletion()
            }
        }

        if (Telemetry.isInitialized) {
            val initParams = CactusInitParams(
                model = _lastDownloadedModel,
            )
            val message = if (result?.success == true) null else result?.response
            Telemetry.instance?.logCompletion(
                result ?: CactusCompletionResult(success = false),
                initParams,
                message = message,
                responseTime = startTime.elapsedNow().inWholeMilliseconds.toDouble(),
                mode = params.mode
            )
        }

        return result
    }

    suspend fun generateEmbedding(
        text: String,
        bufferSize: Int = 2048
    ): CactusEmbeddingResult? {
        val currentHandle = getValidatedHandle()
        if (currentHandle == null) {
            println("CactusLM: Context not initialized")
            return null
        }

        try {
            println("CactusLM: Generating embedding for text: ${if (text.length > 50) text.substring(0, 50) + "..." else text}")
            
            val result = CactusContext.generateEmbedding(currentHandle, text, bufferSize)
            
            println("CactusLM: Embedding generation ${if (result.success) "completed successfully" else "failed"}: " +
                    "dimension=${result.dimension}, " +
                    "embeddings_length=${result.embeddings.size}")

            if (Telemetry.isInitialized) {
                val initParams = CactusInitParams(
                    model = _lastDownloadedModel,
                )
                Telemetry.instance?.logEmbedding(result, initParams)
            }
            
            return result
        } catch (e: Exception) {
            println("CactusLM: Exception during embedding generation: $e")
            if (Telemetry.isInitialized) {
                val initParams = CactusInitParams(
                    model = _lastDownloadedModel,
                )
                Telemetry.instance?.logEmbedding(CactusEmbeddingResult(success = false), initParams, message = e.message)
            }
            return CactusEmbeddingResult(
                success = false,
                embeddings = emptyList(),
                dimension = 0,
                errorMessage = e.message
            )
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

    private suspend fun getValidatedHandle(model: String? = null): Long? {
        if (_handle != null && (model == null || model == _lastDownloadedModel)) {
            return _handle
        }
        
        val targetModel = model ?: _lastDownloadedModel
        
        val initSuccess = initializeModel(CactusInitParams(model = targetModel))
        return if (initSuccess) _handle else null
    }

    suspend fun getModels(): List<CactusModel> {
        if (models.isEmpty()) {
            models = Supabase.fetchModels()
            for (model in models) {
                model.isDownloaded = modelExists(model.slug)
            }
        }
        return models
    }

    private suspend fun getModel(slug: String): CactusModel? {
        if (models.isEmpty()) {
            models = getModels()
        }
        return models.firstOrNull { it.slug == slug }
    }
}

expect fun getModelPath(modelFolder: String): String
