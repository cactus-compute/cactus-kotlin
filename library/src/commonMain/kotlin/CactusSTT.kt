package com.cactus

import com.cactus.services.Supabase
import com.cactus.services.Telemetry
import utils.CactusLogger
import kotlin.time.TimeSource

class CactusSTT() {
    private var _handle: Long? = null
    private var _lastInitializedModel: String = "whisper-tiny"
    private val timeSource = TimeSource.Monotonic
    private val wisprFlow = WisprFlow()

    private var voiceModels = listOf<VoiceModel>()

    suspend fun downloadModel(
        model: String = _lastInitializedModel
    ) {
        if (modelExists(model)) {
            return
        }

        val currentModel  = getModel(model) ?: run {
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

    suspend fun transcribe(
        filePath: String,
        prompt: String = "<|startoftranscript|><|en|><|transcribe|><|notimestamps|>",
        params: CactusTranscriptionParams = CactusTranscriptionParams(),
        onToken: CactusStreamingCallback? = null,
        mode: TranscriptionMode = TranscriptionMode.LOCAL,
        apiKey: String? = null
    ): CactusTranscriptionResult? {
        val startTime = timeSource.markNow()
        var result: CactusTranscriptionResult?

        val localTranscribe = suspend local@{
            val model = params.model ?: _lastInitializedModel
            val currentHandle = getValidatedHandle(model)
            val quantization = Supabase.getModel(model)?.quantization ?: 8

            if (currentHandle == null) {
                if (Telemetry.isInitialized) {
                    Telemetry.instance?.logTranscription(
                        CactusTranscriptionResult(success = false),
                        model,
                        message = "Context not initialized",
                        mode = mode
                    )
                }
                return@local null
            }

            try {
                CactusContext.transcribe(
                    currentHandle,
                    filePath,
                    prompt,
                    params,
                    onToken,
                    quantization
                )
            } catch (e: Exception) {
                if (Telemetry.isInitialized) {
                    Telemetry.instance?.logCompletion(CactusCompletionResult(success = false), _lastInitializedModel, message = e.message)
                }
                throw e
            }
        }

        val remoteTranscribe = suspend {
            if (apiKey != null) {
                val wisprResult = wisprFlow.transcribe(filePath, apiKey)
                wisprResult?.let {
                    CactusTranscriptionResult(
                        it.success,
                        it.text,
                        totalTimeMs = it.processingTime
                    )
                }
            } else {
                CactusTranscriptionResult(
                    success = false,
                    text = "Remote transcription requires filePath and apiKey."
                )
            }
        }

        when (mode) {
            TranscriptionMode.LOCAL -> {
                result = localTranscribe()
            }
            TranscriptionMode.REMOTE -> {
                result = remoteTranscribe()
            }
            TranscriptionMode.LOCAL_FIRST -> {
                result = localTranscribe()
                if (result?.success != true) {
                    val localError = result?.text
                    result = remoteTranscribe()
                    if (result?.success != true && localError != null) {
                        result = CactusTranscriptionResult(
                            success = false,
                            text = "Local transcription failed: $localError. Remote transcription also failed: ${result?.text}"
                        )
                    }
                }
            }
            TranscriptionMode.REMOTE_FIRST -> {
                result = remoteTranscribe()
                if (result?.success != true) {
                    val remoteError = result?.text
                    result = localTranscribe()
                    if (result?.success != true && remoteError != null) {
                        result = CactusTranscriptionResult(
                            success = false,
                            text = "Remote transcription failed: $remoteError. Local transcription also failed: ${result?.text}"
                        )
                    }
                }
            }
        }

        val message: String? = if (result == null) {
            "Transcription failed"
        } else {
            if (result.success) null else result.text
        }

        if (Telemetry.isInitialized) {
            Telemetry.instance?.logTranscription(
                CactusTranscriptionResult(
                    success = result?.success == true,
                    totalTimeMs = result?.totalTimeMs
                ),
                _lastInitializedModel,
                message = message,
                responseTime = startTime.elapsedNow().inWholeMilliseconds.toDouble(),
                mode = mode
            )
        }

        return result
    }

    suspend fun warmUpWispr(apiKey: String) {
        wisprFlow.warmUp(apiKey)
    }

    fun isReady(): Boolean = _handle != null

    suspend fun getVoiceModels(): List<VoiceModel> {
        return voiceModels.ifEmpty {
            val newModels = Supabase.fetchVoiceModels()
            newModels.onEach { model ->
                model.isDownloaded = modelExists(model.slug)
            }
            voiceModels = newModels
            newModels
        }
    }

    private suspend fun getValidatedHandle(model: String): Long? {
        if (_handle != null && (model == _lastInitializedModel)) {
            return _handle
        }

        initializeModel(CactusInitParams(model = model))
        return _handle
    }

    suspend fun isModelDownloaded(
        modelName: String = _lastInitializedModel
    ): Boolean {
        val currentModel = getModel(modelName) ?: return false
        return modelExists(currentModel.slug)
    }

    private suspend fun getModel(slug: String): VoiceModel? {
        val modelsForProvider = getVoiceModels()
        return modelsForProvider.firstOrNull { it.slug == slug }
    }
}
