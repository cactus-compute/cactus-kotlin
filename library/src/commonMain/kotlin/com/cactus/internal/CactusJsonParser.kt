package com.cactus.internal

import com.cactus.CactusCompletionResult
import com.cactus.CactusTranscriptionResult
import com.cactus.ToolCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object CactusJsonParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseCompletionResult(
        responseText: String
    ): CactusCompletionResult {
        val jsonResponse = json.parseToJsonElement(responseText).jsonObject
        val success =
            jsonResponse["success"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true

        val response = jsonResponse["response"]?.jsonPrimitive?.content ?: responseText

        val timeToFirstTokenMs =
            jsonResponse["time_to_first_token_ms"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: 0.0
        val totalTimeMs =
            jsonResponse["total_time_ms"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        val tokensPerSecond =
            jsonResponse["tokens_per_second"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: 0.0
        val prefillTokens =
            jsonResponse["prefill_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val decodeTokens =
            jsonResponse["decode_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val totalTokens =
            jsonResponse["total_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val toolCalls = jsonResponse["function_calls"]?.let { element ->
            json.decodeFromJsonElement<List<ToolCall>>(element)
        } ?: emptyList()

        return CactusCompletionResult(
            success = success,
            response = response,
            timeToFirstTokenMs = timeToFirstTokenMs,
            totalTimeMs = totalTimeMs,
            tokensPerSecond = tokensPerSecond,
            prefillTokens = prefillTokens,
            decodeTokens = decodeTokens,
            totalTokens = totalTokens,
            toolCalls = toolCalls
        )
    }

    fun parseTranscriptionResult(
        responseText: String
    ): CactusTranscriptionResult {
        val jsonResponse = json.parseToJsonElement(responseText).jsonObject
        val success =
            jsonResponse["success"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true

        // Check for both "text" and "response" fields to maintain compatibility
        val rawText = jsonResponse["text"]?.jsonPrimitive?.content
            ?: jsonResponse["response"]?.jsonPrimitive?.content
            ?: responseText

        // Clean up special tokens from the transcription
        val text = rawText
            .replace(Regex("<\\|startoftranscript\\|[^>]*>"), "")
            .replace(Regex("<\\|[^>]+\\|>"), "")
            .trim()

        val timeToFirstTokenMs =
            jsonResponse["time_to_first_token_ms"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: 0.0
        val totalTimeMs =
            jsonResponse["total_time_ms"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        val tokensPerSecond =
            jsonResponse["tokens_per_second"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: 0.0

        return CactusTranscriptionResult(
            success = success,
            text = text,
            timeToFirstTokenMs = timeToFirstTokenMs,
            totalTimeMs = totalTimeMs,
            tokensPerSecond = tokensPerSecond
        )
    }
}
