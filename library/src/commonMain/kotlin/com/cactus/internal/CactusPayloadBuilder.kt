package com.cactus.internal

import com.cactus.CactusCompletionParams
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
                append("}")
            }
            append("]")
        }
    }

    fun buildOptionsJson(params: CactusCompletionParams): String {
        return buildString {
            append("{")
            append("\"temperature\":${params.temperature},")
            append("\"top_k\":${params.topK},")
            append("\"top_p\":${params.topP},")
            append("\"max_tokens\":${params.maxTokens}")
            if (params.stopSequences.isNotEmpty()) {
                append(
                    ",\"stop\":[${params.stopSequences.joinToString(",") {
                            "\"${escapeJsonString(it)}\""
                        }}}]"
                )
            }
            append("}")
        }
    }
}
