package com.cactus

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

typealias CactusTokenCallback = (String) -> Boolean
typealias CactusProgressCallback = (Double?, String, Boolean) -> Unit
typealias CactusStreamingCallback = (token: String, tokenId: UInt) -> Unit

data class CactusCompletionParams(
    val temperature: Double = 0.8,
    val topK: Int = 40,
    val topP: Double = 0.95,
    val maxTokens: Int = 1024,
    val stopSequences: List<String> = emptyList(),
    val bufferSize: Int = 1024
)

data class CactusCompletionResult(
    val success: Boolean,
    val response: String? = null,
    val timeToFirstTokenMs: Double? = null,
    val totalTimeMs: Double? = null,
    val tokensPerSecond: Double? = null,
    val prefillTokens: Int? = null,
    val decodeTokens: Int? = null,
    val totalTokens: Int? = null
)

data class CactusEmbeddingResult(
    val success: Boolean,
    val embeddings: List<Double> = listOf(),
    val dimension: Int? = null,
    val errorMessage: String? = null
)

data class ChatMessage(
    val content: String,
    val role: String,
    val timestamp: Long? = null
)

data class CactusInitParams(
    val model: String? = null,
    val contextSize: Int? = null
)

data class CactusModel(
    val createdAt: Instant,
    val slug: String,
    val sizeMb: Int,
    val supportsToolCalling: Boolean,
    val supportsVision: Boolean,
    val name: String
)

@Serializable
data class VoiceModel(
    val created_at: String? = null,
    val slug: String,
    val language: String? = null,
    val url: String? = null,
    val size_mb: Int? = null,
    val file_name: String,
    var isDownloaded: Boolean = false
)

data class SpeechRecognitionParams(
    val maxSilenceDuration: Long = 1000L,
    val maxDuration: Long = 30000L,
    val sampleRate: Int = 16000,
)

data class SpeechRecognitionResult(
    val success: Boolean,
    val text: String? = null,
    val processingTime: Double? = null
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