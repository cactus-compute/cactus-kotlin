package com.cactus

expect object CactusContext {
    fun getBundleId(): String
    fun sha1(input: ByteArray): ByteArray
    suspend fun initContext(modelPath: String, contextSize: UInt): Long?
    fun freeContext(handle: Long)
    suspend fun completion(
        handle: Long,
        messages: List<ChatMessage>,
        params: CactusCompletionParams,
        tools: String? = null,
        onToken: CactusStreamingCallback? = null,
        quantization: Int
    ): CactusCompletionResult
    suspend fun generateEmbedding(
        handle: Long,
        text: String,
        quantization: Int = 8
    ): CactusEmbeddingResult

    suspend fun transcribe(
        handle: Long,
        audioFilePath: String,
        prompt: String,
        params: CactusTranscriptionParams,
        onToken: CactusStreamingCallback? = null,
        quantization: Int
    ): CactusTranscriptionResult
}