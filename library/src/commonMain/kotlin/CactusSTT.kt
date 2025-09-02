package com.cactus

data class STTResult(
    val text: String,
    val confidence: Float,
    val isPartial: Boolean = false
)

class CactusSTT(
    private val language: String = "en-US",
    private val sampleRate: Int = 16000,
    private val maxDuration: Int = 30
) {
    private var isInitialized = false
    private var lastDownloadedFilename: String? = null
    
    suspend fun download(
        url: String = "https://huggingface.co/Cactus-Compute/vosk-small-en-0.15/resolve/main/vosk-model-small-en-us-0.15.zip",
        filename: String? = null
    ): Boolean {
        val actualFilename = filename ?: url.substringAfterLast("/")
        val success = downloadSTTModel(url, actualFilename)
        if (success) {
            lastDownloadedFilename = actualFilename
        }
        return success
    }
    
    suspend fun init(filename: String? = lastDownloadedFilename): Boolean {
        isInitialized = initializeSTT()
        return isInitialized
    }
    
    suspend fun transcribe(): STTResult? {
        return if (isInitialized) {
            performSTT(language, maxDuration)
        } else null
    }
    
    suspend fun transcribeFile(audioPath: String): STTResult? {
        return if (isInitialized) {
            performFileSTT(audioPath, language)
        } else null
    }
    
    fun stop() {
        stopSTT()
    }
    
    fun isReady(): Boolean = isInitialized
}

expect suspend fun downloadSTTModel(url: String, filename: String): Boolean
expect suspend fun initializeSTT(): Boolean
expect suspend fun performSTT(language: String, maxDuration: Int): STTResult?
expect suspend fun performFileSTT(audioPath: String, language: String): STTResult?
expect fun stopSTT() 
