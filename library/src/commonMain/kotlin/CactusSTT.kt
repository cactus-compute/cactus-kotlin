package com.cactus

class CactusSTT(
    private val language: String = "en-US",
    private val sampleRate: Int = 16000,
    private val maxDuration: Int = 30
) {
    private var isInitialized = false
    private var lastDownloadedModelName: String = "vosk-model-small-en-us-0.15"
    private var lastDownloadedSpkName: String = "vosk-model-spk-0.4"

    suspend fun download(
        model: String = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
        spkModel: String = "https://alphacephei.com/vosk/models/vosk-model-spk-0.4.zip",
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
        return isInitialized
    }

    suspend fun transcribe(): SpeechRecognitionResult? {
        return if (isInitialized) {
            performSTT(language, maxDuration, sampleRate)
        } else null
    }

    fun stop() {
        stopSTT()
    }

    fun isReady(): Boolean = isInitialized
}

expect suspend fun downloadSTTModel(model: String, modelName: String, spkModel: String, spkModelName: String): Boolean
expect suspend fun initializeSTT(modelFolder: String, spkModelFolder: String): Boolean
expect suspend fun performSTT(language: String, maxDuration: Int, sampleRate: Int): SpeechRecognitionResult?
expect fun stopSTT()
