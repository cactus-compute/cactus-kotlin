package com.cactus

import kotlinx.datetime.Instant

typealias CactusTokenCallback = (String) -> Boolean
typealias CactusProgressCallback = (Double?, String, Boolean) -> Unit

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
    val response: String,
    val timeToFirstTokenMs: Double,
    val totalTimeMs: Double,
    val tokensPerSecond: Double,
    val prefillTokens: Int,
    val decodeTokens: Int,
    val totalTokens: Int
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

data class SpeechRecognitionParams(
    val language: String = "en-US",
    val continuous: Boolean = false,
    val maxDuration: Int = 30,
    val enablePartialResults: Boolean = true,
    val enableProfanityFilter: Boolean = true,
    val enablePunctuation: Boolean = true
)

data class SpeechRecognitionResult(
    val text: String,
    val confidence: Float,
    val isPartial: Boolean = false,
    val alternatives: List<String> = emptyList()
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