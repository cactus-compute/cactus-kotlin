import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual object CactusContext {
    actual suspend fun initContext(modelPath: String): Long? = withContext(Dispatchers.Default) {
        null
    }
    
    actual fun freeContext(handle: Long) {
        // No-op for iOS
    }
    
    actual suspend fun completion(
        handle: Long,
        messages: List<ChatMessage>,
        params: CactusCompletionParams
    ): CactusCompletionResult = withContext(Dispatchers.Default) {
        CactusCompletionResult(
            success = true,
            response = "Mock response",
            timeToFirstTokenMs = 100.0,
            totalTimeMs = 500.0,
            tokensPerSecond = 10.0,
            prefillTokens = 50,
            decodeTokens = 20,
            totalTokens = 70
        )
    }
}