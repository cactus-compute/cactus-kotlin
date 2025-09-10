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
        onToken: CactusStreamingCallback? = null
    ): CactusCompletionResult
}