package com.cactus

import com.cactus.services.Supabase
import com.cactus.services.Telemetry
import kotlin.time.TimeSource

class CactusSTT(
    private val provider: TranscriptionProvider = TranscriptionProvider.VOSK
) {
    private var isInitialized = false
    private var lastDownloadedModelName: String = "vosk-en-us"
    private val timeSource = TimeSource.Monotonic
    private val wisprFlow = WisprFlow()
    private lateinit var speechProvider: SpeechRecognitionProvider


    // spk model is universal, no need to change it for different languages
    private val spkModelFolder: String = "vosk-model-spk-0.4"
    private val spkModelUrl: String = "https://alphacephei.com/vosk/models/vosk-model-spk-0.4.zip"

    private var voiceModels = mutableMapOf<TranscriptionProvider, List<VoiceModel>>()

    suspend fun download(
        model: String = lastDownloadedModelName
    ): Boolean {
        if (modelExists(model) && modelExists(spkModelFolder)) {
            return true
        }
        val currentModel  = getModel(model) ?: run {
            println("No data found for model: $model")
            return false
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
            } else {
                // VOSK models are .zip files, need extraction
                tasks.add(DownloadTask(
                    url = currentModel.url,
                    filename = "${currentModel.slug}.zip",
                    folder = currentModel.slug,
                    requiresExtraction = true
                ))
            }
        }
        if(!isWhisper && !modelExists(spkModelFolder)) {
            tasks.add(DownloadTask(
                url = spkModelUrl,
                filename = "$spkModelFolder.zip",
                folder = spkModelFolder,
                requiresExtraction = true
            ))
        }
        val success = downloadAndExtractModels(tasks)
        if (success) {
            lastDownloadedModelName = model
        }
        return success
    }

    suspend fun init(model: String = lastDownloadedModelName): Boolean {
        isInitialized = false
        try {
            // Initialize the speech provider based on the selected provider
            speechProvider = getSpeechRecognitionProvider(provider)
            isInitialized = speechProvider.initialize(model, spkModelFolder)
            
            if (Telemetry.isInitialized) {
                val message = if (isInitialized) null else "Failed to initialize model: $model"
                Telemetry.instance?.logInit(isInitialized, model, message)
            }
        } catch (e: Exception) {
            println("Error initializing STT: ${e.message}")
            e.printStackTrace()
            if (Telemetry.isInitialized) {
                Telemetry.instance?.logInit(isInitialized, model, "Error in initializing STT: ${e.message}")
            }
        }
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

        val localTranscribe = suspend {
            if (isInitialized) {
                speechProvider.performRecognition(params, filePath)
            } else {
                println("Local STT not initialized.")
                null
            }
        }

        val remoteTranscribe = suspend {
            if (filePath != null && apiKey != null) {
                wisprFlow.transcribe(filePath, apiKey)
            } else {
                println("Remote transcription requires filePath and apiKey.")
                null
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
                    println("Local transcription failed or unavailable, trying remote.")
                    result = remoteTranscribe()
                }
            }
            TranscriptionMode.REMOTE_FIRST -> {
                result = remoteTranscribe()
                if (result?.success != true) {
                    println("Remote transcription failed or unavailable, trying local.")
                    result = localTranscribe()
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
                lastDownloadedModelName,
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
                TranscriptionProvider.VOSK -> "vosk"
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
        modelName: String = lastDownloadedModelName
    ): Boolean {
        val currentModel = getModel(modelName) ?: run {
            println("No data found for model: $lastDownloadedModelName")
            return false
        }
        if (!modelExists(currentModel.slug)) {
            return false
        }
        if (currentModel.provider == "vosk") {
            return modelExists(spkModelFolder)
        }
        return true
    }

    private suspend fun getModel(slug: String): VoiceModel? {
        val modelsForProvider = getVoiceModels(provider)
        return modelsForProvider.firstOrNull { it.slug == slug }
    }
}
