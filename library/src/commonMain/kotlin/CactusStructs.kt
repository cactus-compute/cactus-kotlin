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