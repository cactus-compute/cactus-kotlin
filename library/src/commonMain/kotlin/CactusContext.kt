package com.cactus

expect object CactusContext {
    suspend fun initContext(modelPath: String, contextSize: UInt): Long?
    fun freeContext(handle: Long)
    suspend fun completion(
        handle: Long,
        messages: List<ChatMessage>,
        params: CactusCompletionParams
    ): CactusCompletionResult
}