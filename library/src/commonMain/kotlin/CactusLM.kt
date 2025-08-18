package com.cactus

class CactusLM {
    private var handle: Long? = null
    private var lastDownloadedFilename: String? = null

    suspend fun download(
        url: String = "https://huggingface.co/Cactus-Compute/Qwen3-600m-Instruct-GGUF/resolve/main/Qwen3-0.6B-Q8_0.gguf",
        filename: String? = null
    ): Boolean {
        val actualFilename = filename ?: url.substringAfterLast("/")
        val success = downloadModel(url, actualFilename)
        if (success) {
            lastDownloadedFilename = actualFilename
        }
        return success
    }
    
    suspend fun init(filename: String = lastDownloadedFilename ?: "Qwen3-0.6B-Q8_0.gguf"): Boolean {
        handle = initializeModel(filename)
        return handle != null
    }
    
    suspend fun completion(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        topP: Float = 0.9f
    ): CactusCompletionResult? {
        val currMessage = listOf<ChatMessage>(ChatMessage(prompt, "user"))
        val response: CactusCompletionResult? = handle?.let { h ->
            generateCompletion(h, currMessage,
                CactusCompletionParams(
                    temperature = temperature.toDouble(),
                    topP = topP.toDouble(),
                    maxTokens = maxTokens
                )
            )
        }
        return response
    }
    
    fun unload() {
        handle?.let { h ->
            uninitializeModel(h)
            handle = null
        }
    }
    
    fun isLoaded(): Boolean = handle != null
}

expect suspend fun downloadModel(url: String, filename: String): Boolean
expect suspend fun initializeModel(path: String): Long?
expect suspend fun generateCompletion(handle: Long, messages: List<ChatMessage>, options: CactusCompletionParams): CactusCompletionResult?
expect fun uninitializeModel(handle: Long)
