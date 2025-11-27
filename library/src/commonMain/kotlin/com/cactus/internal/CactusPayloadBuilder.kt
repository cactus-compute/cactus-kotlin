package com.cactus.internal

import com.cactus.CactusCompletionParams
import com.cactus.CactusTranscriptionParams
import com.cactus.ChatMessage

internal object CactusPayloadBuilder {
    /**
     * Escape a string for JSON, matching the C++ parser expectations
     */
    private fun escapeJsonString(input: String): String {
        return input
            .replace("\\", "\\\\")  // Escape backslashes first
            .replace("\"", "\\\"")  // Escape quotes
            .replace("\n", "\\n")   // Escape newlines
            .replace("\r", "\\r")   // Escape carriage returns
            .replace("\t", "\\t")   // Escape tabs
    }

    fun buildMessagesJson(messages: List<ChatMessage>): String {
        return buildString {
            append("[")
            messages.forEachIndexed { index, message ->
                if (index > 0) append(",")
                append("{")
                append("\"role\":\"${message.role}\",")
                append("\"content\":\"${escapeJsonString(message.content)}\"")
                if (message.images.isNotEmpty()) {
                    append(",\"images\":[")
                    message.images.forEachIndexed { imgIndex, image ->
                        if (imgIndex > 0) append(",")
                        append("\"${escapeJsonString(image)}\"")
                    }
                    append("]")
                }
                append("}")
            }
            append("]")
        }
    }

    fun buildOptionsJson(params: CactusCompletionParams): String {
        return buildString {
            append("{")
            params.temperature?.let {
                append("\"temperature\":${params.temperature},")
            }
            params.topK?.let {
                append("\"top_k\":${params.topK},")
            }
            params.topP?.let {
                append("\"top_p\":${params.topP},")
            }
            append("\"max_tokens\":${params.maxTokens}")
            if (params.stopSequences.isNotEmpty()) {
                append(
                    ",\"stop_sequences\":[${params.stopSequences.joinToString(",") {
                            "\"${escapeJsonString(it)}\""
                        }}}]"
                )
            }
            append("}")
        }
    }

    fun buildParamsJson(params: CactusTranscriptionParams): String {
        return buildString {
            append("{")
            append("\"max_tokens\":${params.maxTokens}")
            if (params.stopSequences.isNotEmpty()) {
                append(
                    ",\"stop_sequences\":[${params.stopSequences.joinToString(",") {
                        "\"${escapeJsonString(it)}\""
                    }}}]"
                )
            }
            append("}")
        }
    }
}
