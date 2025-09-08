package com.cactus

import com.cactus.services.Telemetry
import kotlin.time.TimeSource

class CactusSTT {
    private var isInitialized = false
    private var lastDownloadedModelName: String = "vosk-model-small-en-us-0.15"
    private var lastDownloadedSpkName: String = "vosk-model-spk-0.4"
    private val timeSource = TimeSource.Monotonic


    // spk model is universal, no need to change it for different languages
    private val spkModel: String = "https://alphacephei.com/vosk/models/vosk-model-spk-0.4.zip"

    suspend fun download(
        model: String = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    ): Boolean {
        val modelFileName = model.substringAfterLast("/")
        val spkModelFileName = spkModel.substringAfterLast("/")
        val success = downloadSTTModel(model, modelFileName, spkModel, spkModelFileName)
        if (success) {
            lastDownloadedModelName = modelFileName.removeSuffix(".zip")
            lastDownloadedSpkName = spkModelFileName.removeSuffix(".zip")
        }
        return success
    }

    suspend fun init(model: String? = lastDownloadedModelName, spkModel: String? = lastDownloadedSpkName): Boolean {
        isInitialized = false
        if(model != null && spkModel != null) {
            isInitialized = initializeSTT(model, spkModel)
        }
        if (Telemetry.isInitialized) {
            Telemetry.instance?.logInit(isInitialized, CactusInitParams(
                model = model
            ))
        }
        return isInitialized
    }

    suspend fun transcribe(params: SpeechRecognitionParams): SpeechRecognitionResult? {
        if (isInitialized) {
            val startTime = timeSource.markNow()
            val result = performSTT(params)
            if (Telemetry.isInitialized) {
                Telemetry.instance?.logTranscription(
                    CactusCompletionResult(
                        success = result?.success == true,
                        response = result?.text,
                        totalTimeMs = result?.processingTime
                    ),
                    CactusInitParams(model = lastDownloadedModelName),
                    message = if (result?.success == false) result.text else null,
                    responseTime = startTime.elapsedNow().inWholeMilliseconds.toDouble()
                )
            }
            return result
        }
        return null
    }

    fun stop() {
        stopSTT()
    }

    fun isReady(): Boolean = isInitialized

    suspend fun isModelDownloaded(
        modelName: String = lastDownloadedModelName
    ): Boolean {
        return checkModelsDownloaded(modelName, lastDownloadedSpkName)
    }
}

expect suspend fun downloadSTTModel(model: String, modelName: String, spkModel: String, spkModelName: String): Boolean
expect suspend fun initializeSTT(modelFolder: String, spkModelFolder: String): Boolean
expect suspend fun performSTT(params: SpeechRecognitionParams): SpeechRecognitionResult?
expect fun stopSTT()
expect suspend fun checkModelsDownloaded(modelName: String, spkModelName: String): Boolean
