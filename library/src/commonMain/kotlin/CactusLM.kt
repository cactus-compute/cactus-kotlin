package com.cactus

import com.cactus.models.CactusTool
import com.cactus.models.toToolsJson
import com.cactus.services.Supabase
import com.cactus.services.Telemetry
import com.cactus.services.ToolFilterConfig
import com.cactus.services.ToolFilterService
import utils.CactusLogger
import kotlin.time.TimeSource

class CactusLM(
    var enableToolFiltering: Boolean = true,
    var toolFilterConfig: ToolFilterConfig? = null
) {
    private var _handle: Long? = null
    private var _lastInitializedModel: String = "qwen3-0.6"
    private val openRouterModule = OpenRouterModule()
    private val timeSource = TimeSource.Monotonic

    private val _models = mutableListOf<CactusModel>()
    private var _toolFilterService: ToolFilterService? = null

    suspend fun downloadModel(
        model: String = _lastInitializedModel
    ) {
        if (modelExists(model)) {
            return
        }

        val currentModel  = Supabase.getModel(model) ?: run {
            throw Exception("Failed to get model $model")
        }

        val actualFilename = currentModel.download_url.split('?').first().split('/').last()
        val task = DownloadTask(currentModel.download_url, actualFilename, currentModel.slug)

        val success = downloadAndExtractModels(listOf(task))
        if (!success) {
            throw Exception("Failed to download and extract model $model from ${currentModel.download_url}")
        }
    }

    suspend fun initializeModel(params: CactusInitParams) {
        val modelFolder = params.model ?: _lastInitializedModel
        val modelPath = getModelPath(modelFolder)

        _handle = CactusContext.initContext(modelPath, (params.contextSize ?: 2048).toUInt())
        _lastInitializedModel = modelFolder
        
        // If initialization failed and model is not downloaded, try to download first
        if (_handle == null && !modelExists(modelFolder)) {
            CactusLogger.i("Failed to initialize model context with model at $modelPath, trying to download the model first.", tag = "CactusLM")
            downloadModel(model = modelFolder)
            return initializeModel(params)
        }
        
        if (Telemetry.isInitialized) {
            val message = if (_handle != null) null else "Failed to initialize model at path: $modelPath"
            Telemetry.instance?.logInit(_handle != null, modelFolder, message)
        }
        
        if (_handle == null) {
            throw Exception("Failed to initialize model context with model at $modelPath")
        }
        _lastInitializedModel = modelFolder
    }

    suspend fun generateCompletion(
        messages: List<ChatMessage>,
        params: CactusCompletionParams = CactusCompletionParams(),
        onToken: CactusStreamingCallback? = null
    ): CactusCompletionResult? {
        val startTime = timeSource.markNow()
        var result: CactusCompletionResult?

        // Filter tools if enabled
        val filteredParams = if (enableToolFiltering && params.tools.isNotEmpty()) {
            val filteredTools = filterTools(messages, params.tools)
            params.copy(tools = filteredTools)
        } else {
            params
        }

        val localCompletion = suspend local@{
            val model = filteredParams.model ?: _lastInitializedModel
            val currentHandle = getValidatedHandle(model)
            val quantization = Supabase.getModel(model)?.quantization ?: 8

            if (currentHandle == null) {
                if (Telemetry.isInitialized) {
                    Telemetry.instance?.logCompletion(
                        CactusCompletionResult(success = false),
                        model,
                        message = "Context not initialized",
                    )
                }
                return@local null
            }

            try {
                val toolsJson = filteredParams.tools.toToolsJson()
                if(filteredParams.tools.isNotEmpty()) {
                    unload()
                    getValidatedHandle(model)?.let {
                        CactusContext.completion(it, messages, filteredParams, toolsJson, onToken, quantization)
                    }
                } else {
                    CactusContext.completion(currentHandle, messages, filteredParams, toolsJson, onToken, quantization)
                }
            } catch (e: Exception) {
                if (Telemetry.isInitialized) {
                    Telemetry.instance?.logCompletion(CactusCompletionResult(success = false), _lastInitializedModel, message = e.message)
                }
                throw e
            }
        }

        val remoteCompletion = suspend {
            if (filteredParams.cactusToken != null) {
                openRouterModule.generateCompletion(messages, filteredParams, filteredParams.cactusToken, onToken)
            } else {
                CactusLogger.w("Remote inference requires an apiKey.", tag = "CactusLM")
                null
            }
        }

        result = when (filteredParams.mode) {
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
            val message = if (result?.success == true) null else result?.response
            Telemetry.instance?.logCompletion(
                result ?: CactusCompletionResult(success = false),
                _lastInitializedModel,
                message = message,
                responseTime = startTime.elapsedNow().inWholeMilliseconds.toDouble(),
                mode = filteredParams.mode
            )
        }

        return result
    }

    private suspend fun filterTools(messages: List<ChatMessage>, tools: List<CactusTool>): List<CactusTool> {
        if (_toolFilterService == null) {
            _toolFilterService = ToolFilterService(
                config = toolFilterConfig ?: ToolFilterConfig.simple(),
                lm = this
            )
        }
        
        val userQuery = messages.lastOrNull { it.role == "user" }?.content 
            ?: messages.lastOrNull()?.content 
            ?: ""
        
        val filteredTools = _toolFilterService!!.filterTools(userQuery, tools)

        if (filteredTools.size != tools.size) {
            CactusLogger.d("Tool filtering: ${tools.size} -> ${filteredTools.size} tools", tag = "CactusLM")
            CactusLogger.d("Filtered tools: ${filteredTools.joinToString(", ") { it.function.name }}", tag = "CactusLM")
        }

        return filteredTools
    }

    suspend fun generateEmbedding(
        text: String,
        modelName: String? = null
    ): CactusEmbeddingResult? {
        val model = modelName ?: _lastInitializedModel
        val currentHandle = getValidatedHandle(model)
        val quantization = Supabase.getModel(model)?.quantization ?: 8

        if (currentHandle == null) {
            CactusLogger.w("Context not initialized", tag = "CactusLM")
            return null
        }

        try {
            CactusLogger.d("Generating embedding for text: ${if (text.length > 50) text.substring(0, 50) + "..." else text}", tag = "CactusLM")

            val result = CactusContext.generateEmbedding(currentHandle, text, quantization)

            CactusLogger.i("Embedding generation ${if (result.success) "completed successfully" else "failed"}: " +
                    "dimension=${result.dimension}, " +
                    "embeddings_length=${result.embeddings.size}", tag = "CactusLM")

            if (Telemetry.isInitialized) {
                Telemetry.instance?.logEmbedding(result, _lastInitializedModel)
            }

            return result
        } catch (e: Exception) {
            CactusLogger.e("Exception during embedding generation: $e", tag = "CactusLM", throwable = e)
            if (Telemetry.isInitialized) {
                Telemetry.instance?.logEmbedding(CactusEmbeddingResult(success = false), _lastInitializedModel, message = e.message)
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

    suspend fun getModels(): List<CactusModel> {
        if (_models.isEmpty()) {
            _models.addAll(Supabase.fetchModels())
        }
        for (model in _models) {
            model.isDownloaded = modelExists(model.slug)
        }
        return _models
    }

    private suspend fun getValidatedHandle(model: String): Long? {
        if (_handle != null && (model == _lastInitializedModel)) {
            return _handle
        }

        initializeModel(CactusInitParams(model = model))
        return _handle
    }
}

expect fun getModelPath(modelFolder: String): String
