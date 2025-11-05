package com.cactus

import com.cactus.services.Supabase
import com.cactus.services.Telemetry
import kotlin.time.TimeSource

class CactusSTT(
    private val provider: TranscriptionProvider = TranscriptionProvider.WHISPER
) {
    private var isInitialized = false
    private var _lastInitializedModel: String = "whisper-tiny"
    private val timeSource = TimeSource.Monotonic
    private val wisprFlow = WisprFlow()
    private lateinit var speechProvider: SpeechRecognitionProvider

    private var voiceModels = mutableMapOf<TranscriptionProvider, List<VoiceModel>>()

    suspend fun download(
        model: String
    ): Boolean {
        val currentModel  = getModel(model) ?: return false
        if (isModelDownloaded(model)) {
            return true
        }
        val tasks = mutableListOf<DownloadTask>()

        // Check if model is whisper (provider field)
        val isWhisper = currentModel.provider == "whisper"

        if(!modelExists(currentModel.slug)) {
            if (isWhisper) {
                // Whisper models are .bin files, no extraction needed
                tasks.add(DownloadTask(
                    url = currentModel.url,
                    filename = "${currentModel.slug}.bin",
                    folder = currentModel.slug,
                    requiresExtraction = false
                ))
            }
        }
        return downloadAndExtractModels(tasks)
    }

    suspend fun init(model: String): Boolean {
        isInitialized = false
        if (!isModelDownloaded(model)) {
            download(model)
        }
        try {
            // Initialize the speech provider based on the selected provider
            speechProvider = getSpeechRecognitionProvider(provider)
            isInitialized = speechProvider.initialize(model)
            
            if (Telemetry.isInitialized) {
                val message = if (isInitialized) null else "Failed to initialize model: $model"
                Telemetry.instance?.logInit(isInitialized, model, message)
            }
        } catch (e: Exception) {
            if (Telemetry.isInitialized) {
                Telemetry.instance?.logInit(isInitialized, model, "Error in initializing STT: ${e.message}")
            }
        }
        _lastInitializedModel = model
        return isInitialized
    }

    suspend fun transcribe(
        params: SpeechRecognitionParams = SpeechRecognitionParams(),
        filePath: String? = null,
        mode: TranscriptionMode = TranscriptionMode.LOCAL,
        apiKey: String? = null
    ): SpeechRecognitionResult? {
        val startTime = timeSource.markNow()
        var result: SpeechRecognitionResult?
        val model = params.model ?: _lastInitializedModel

        val localTranscribe = suspend {
            if (!isInitialized || model!= _lastInitializedModel) {
                init(model)
            }
            if (isInitialized) {
                speechProvider.performRecognition(params, filePath)
            } else {
                SpeechRecognitionResult(
                    success = false,
                    text = "Local STT not initialized."
                )
            }
        }

        val remoteTranscribe = suspend {
            if (filePath != null && apiKey != null) {
                wisprFlow.transcribe(filePath, apiKey)
            } else {
                SpeechRecognitionResult(
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
                        result = SpeechRecognitionResult(
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
                        result = SpeechRecognitionResult(
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
                CactusCompletionResult(
                    success = result?.eventSuccess == true,
                    totalTimeMs = result?.processingTime
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

    fun stop() {
        if (isInitialized) {
            speechProvider.stop()
        }
    }

    fun isReady(): Boolean = isInitialized

    suspend fun getVoiceModels(provider: TranscriptionProvider = this.provider): List<VoiceModel> {
        return voiceModels[provider] ?: run {
            val providerName = when (provider) {
                TranscriptionProvider.WHISPER -> "whisper"
            }
            val newModels = Supabase.fetchVoiceModels(providerName)
            newModels.onEach { model ->
                model.isDownloaded = modelExists(model.slug)
            }
            voiceModels[provider] = newModels
            newModels
        }
    }

    suspend fun isModelDownloaded(
        modelName: String = _lastInitializedModel
    ): Boolean {
        val currentModel = getModel(modelName) ?: return false
        return modelExists(currentModel.slug)
    }

    private suspend fun getModel(slug: String): VoiceModel? {
        val modelsForProvider = getVoiceModels(provider)
        return modelsForProvider.firstOrNull { it.slug == slug }
    }
}
