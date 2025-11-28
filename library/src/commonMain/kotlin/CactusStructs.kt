package com.cactus

import kotlinx.serialization.Serializable
import com.cactus.models.CactusTool

typealias CactusTokenCallback = (String) -> Boolean
typealias CactusProgressCallback = (Double?, String, Boolean) -> Unit
typealias CactusStreamingCallback = (token: String, tokenId: UInt) -> Unit

data class CactusCompletionParams(
    val model: String? = null,
    val temperature: Double? = null,
    val topK: Int? = null,
    val topP: Double? = null,
    val maxTokens: Int = 512,
    val stopSequences: List<String> = listOf("<|im_end|>", "<end_of_turn>"),
    val tools: List<CactusTool> = emptyList(),
    val mode: InferenceMode = InferenceMode.LOCAL,
    val cactusToken: String? = null
)

data class CactusTranscriptionParams(
    val model: String? = null,
    val maxTokens: Int = 512,
    val stopSequences: List<String> = listOf("<|im_end|>", "<end_of_turn>"),
)

data class CactusTranscriptionResult(
    val success: Boolean,
    val text: String? = null,
    val timeToFirstTokenMs: Double? = null,
    val totalTimeMs: Double? = null,
    val tokensPerSecond: Double? = null,
    val errorMessage: String? = null
)

data class CactusCompletionResult(
    val success: Boolean,
    val response: String? = null,
    val timeToFirstTokenMs: Double? = null,
    val totalTimeMs: Double? = null,
    val tokensPerSecond: Double? = null,
    val prefillTokens: Int? = null,
    val decodeTokens: Int? = null,
    val totalTokens: Int? = null,
    val toolCalls: List<ToolCall>? = emptyList(),
)

data class CactusEmbeddingResult(
    val success: Boolean,
    val embeddings: List<Double> = listOf(),
    val dimension: Int? = null,
    val errorMessage: String? = null
)

@Serializable
data class ChatMessage(
    val content: String,
    val role: String,
    val images: List<String> = emptyList(),
    val timestamp: Long? = null
)

data class CactusInitParams(
    val model: String? = null,
    val contextSize: Int? = null
)

@Serializable
data class CactusModel(
    val created_at: String,
    val slug: String,
    val download_url: String,
    val size_mb: Int,
    val supports_tool_calling: Boolean,
    val supports_vision: Boolean,
    val name: String,
    var isDownloaded: Boolean = false,
    val quantization: Int = 8
)

@Serializable
data class VoiceModel(
    val created_at: String,
    val slug: String,
    val download_url: String,
    val size_mb: Int,
    val file_name: String,
    var isDownloaded: Boolean = false
)

data class SpeechRecognitionParams(
    val maxSilenceDuration: Long = 1000L,
    val maxDuration: Long = 30000L,
    val sampleRate: Int = 16000,
    val model: String? = null
)

data class SpeechRecognitionResult(
    val success: Boolean,
    val text: String? = null,
    val eventSuccess: Boolean = true,
    val processingTime: Double? = null
)


@Serializable
data class ToolCall(
    val name: String,
    val arguments: Map<String, String>
)

sealed class SpeechError : Exception() {
    object PermissionDenied : SpeechError()
    object NotAvailable : SpeechError()
    object NetworkError : SpeechError()
    object AudioUnavailable : SpeechError()
    object NoSpeechDetected : SpeechError()
    object RecognitionUnavailable : SpeechError()
    data class UnknownError(override val message: String) : SpeechError()
}

sealed class SpeechState {
    object Idle : SpeechState()
    object Listening : SpeechState()
    object Processing : SpeechState()
    data class Result(val result: SpeechRecognitionResult) : SpeechState()
    data class Error(val error: SpeechError) : SpeechState()
}

enum class TranscriptionMode {
    LOCAL, REMOTE, LOCAL_FIRST, REMOTE_FIRST
}

enum class InferenceMode {
    LOCAL, REMOTE, LOCAL_FIRST, REMOTE_FIRST
}