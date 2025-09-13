package com.cactus

import com.cactus.services.Supabase
import com.cactus.services.Telemetry
import kotlin.time.TimeSource

class CactusSTT {
    private var isInitialized = false
    private var lastDownloadedModelName: String = "vosk-en-us"
    private val timeSource = TimeSource.Monotonic


    // spk model is universal, no need to change it for different languages
    private val spkModelFolder: String = "vosk-model-spk-0.4"
    private val spkModelUrl: String = "https://alphacephei.com/vosk/models/vosk-model-spk-0.4.zip"

    private var voiceModels = listOf<VoiceModel>()

    suspend fun download(
        model: String = lastDownloadedModelName
    ): Boolean {
        val currentModel  = getModel(model) ?: run {
            println("No data found for model: $model")
            return false
        }
        val success = downloadSTTModel(currentModel.url, currentModel.file_name + ".zip", model, spkModelUrl, spkModelFolder + ".zip")
        if (success) {
            lastDownloadedModelName = model
        }
        return success
    }

    suspend fun init(model: String = lastDownloadedModelName): Boolean {
        isInitialized = false
        try {
            isInitialized = initializeSTT(model, spkModelFolder)
            if (Telemetry.isInitialized) {
                val message = if (isInitialized) null else "Failed to initialize model: $model"
                Telemetry.instance?.logInit(isInitialized, CactusInitParams(
                    model = model
                ), message)
            }
        } catch (e: Exception) {
            println("Error initializing STT: ${e.message}")
            e.printStackTrace()
            if (Telemetry.isInitialized) {
                Telemetry.instance?.logInit(isInitialized, CactusInitParams(
                    model = model
                ), "Error in initializing STT: ${e.message}")
            }
        }
        return isInitialized
    }

    suspend fun transcribe(params: SpeechRecognitionParams, filePath: String? = null): SpeechRecognitionResult? {
        if (isInitialized) {
            val startTime = timeSource.markNow()
            val result = performSTT(params, filePath)
            if (Telemetry.isInitialized) {
                Telemetry.instance?.logTranscription(
                    CactusCompletionResult(
                        success = result?.success == true,
                        totalTimeMs = result?.processingTime
                    ),
                    CactusInitParams(model = lastDownloadedModelName),
                    message = if (result?.success == false) result.text else null,
                    responseTime = startTime.elapsedNow().inWholeMilliseconds.toDouble()
                )
            }
            return result
        }
        if (Telemetry.isInitialized) {
            Telemetry.instance?.logTranscription(
                CactusCompletionResult(
                    success = false,
                ),
                CactusInitParams(model = lastDownloadedModelName),
                message = "STT not initialized"
            )
        }
        return null
    }

    fun stop() {
        stopSTT()
    }

    fun isReady(): Boolean = isInitialized

    suspend fun getVoiceModels(): List<VoiceModel> {
        if (voiceModels.isEmpty()) {
            voiceModels = Supabase.fetchVoiceModels()
            for (model in voiceModels) {
                model.isDownloaded = modelExists(model.file_name)
            }
        }
        return voiceModels
    }

    suspend fun isModelDownloaded(
        modelName: String = lastDownloadedModelName
    ): Boolean {
        val currentModel = getModel(modelName) ?: run {
            println("No data found for model: $lastDownloadedModelName")
            return false
        }
        return modelExists(currentModel.file_name) && modelExists(spkModelFolder)
    }

    private suspend fun getModel(slug: String): VoiceModel? {
        if (voiceModels.isEmpty()) {
            voiceModels = getVoiceModels()
        }
        return voiceModels.firstOrNull { it.slug == slug }
    }
}

expect suspend fun downloadSTTModel(modelUrl: String, modelName: String, slug:String, spkModelUrl: String, spkModelName: String): Boolean
expect suspend fun initializeSTT(modelFolder: String, spkModelFolder: String): Boolean
expect suspend fun performSTT(params: SpeechRecognitionParams, filePath: String?): SpeechRecognitionResult?
expect fun stopSTT()
expect suspend fun modelExists(modelName: String): Boolean
