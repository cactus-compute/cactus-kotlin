@file:OptIn(ExperimentalForeignApi::class)
package com.cactus

import com.cactus.native.*
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.CoreCrypto.CC_SHA1
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH
import platform.Foundation.NSBundle

actual object CactusContext {
    private val json = Json { ignoreUnknownKeys = true }

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

    actual suspend fun initContext(modelPath: String, contextSize: UInt): Long? = withContext(Dispatchers.Default) {
        return@withContext memScoped {
            val handle = cactus_init(modelPath, contextSize.toULong())
            handle?.rawValue?.toLong()
        }
    }

    actual fun freeContext(handle: Long) {
        cactus_destroy(handle.toCPointer())
    }

    actual suspend fun completion(
        handle: Long,
        messages: List<ChatMessage>,
        params: CactusCompletionParams
    ): CactusCompletionResult = withContext(Dispatchers.Default) {

        val messagesJson = buildString {
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

        val optionsJson = buildString {
            append("{")
            append("\"temperature\":${params.temperature},")
            append("\"top_k\":${params.topK},")
            append("\"top_p\":${params.topP},")
            append("\"max_tokens\":${params.maxTokens}")
            if (params.stopSequences.isNotEmpty()) {
                append(",\"stop\":[${params.stopSequences.joinToString(",") { "\"${escapeJsonString(it)}\"" }}]")
            }
            append("}")
        }

        return@withContext memScoped {
            val responseBuffer = allocArray<ByteVar>(params.bufferSize)

            val result = cactus_complete(
                handle.toCPointer(),
                messagesJson,
                responseBuffer,
                params.bufferSize.convert(),
                optionsJson
            )

            if (result > 0) {
                val responseText = responseBuffer.toKString()

                try {
                    val jsonResponse = json.parseToJsonElement(responseText).jsonObject
                    val success = jsonResponse["success"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
                    val response = jsonResponse["response"]?.jsonPrimitive?.content ?: responseText
                    val timeToFirstTokenMs = jsonResponse["time_to_first_token_ms"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val totalTimeMs = jsonResponse["total_time_ms"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val tokensPerSecond = jsonResponse["tokens_per_second"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val prefillTokens = jsonResponse["prefill_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val decodeTokens = jsonResponse["decode_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val totalTokens = jsonResponse["total_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

                    CactusCompletionResult(
                        success = success,
                        response = response,
                        timeToFirstTokenMs = timeToFirstTokenMs,
                        totalTimeMs = totalTimeMs,
                        tokensPerSecond = tokensPerSecond,
                        prefillTokens = prefillTokens,
                        decodeTokens = decodeTokens,
                        totalTokens = totalTokens
                    )
                } catch (e: Exception) {
                    CactusCompletionResult(
                        success = false,
                        response = "Error: Unable to parse the response",
                        timeToFirstTokenMs = 0.0,
                        totalTimeMs = 0.0,
                        tokensPerSecond = 0.0,
                        prefillTokens = 0,
                        decodeTokens = 0,
                        totalTokens = 0
                    )
                }
            } else {
                CactusCompletionResult(
                    success = false,
                    response = "Error: completion failed with code $result",
                    timeToFirstTokenMs = 0.0,
                    totalTimeMs = 0.0,
                    tokensPerSecond = 0.0,
                    prefillTokens = 0,
                    decodeTokens = 0,
                    totalTokens = 0
                )
            }
        }
    }

    actual fun getBundleId(): String {
        return NSBundle.mainBundle.bundleIdentifier ?: "unknown"
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun sha1(input: ByteArray): ByteArray {
        val out = ByteArray(CC_SHA1_DIGEST_LENGTH)
        input.usePinned { inPinned ->
            out.usePinned { outPinned ->
                CC_SHA1(
                    inPinned.addressOf(0).reinterpret<UByteVar>(),
                    input.size.convert(),
                    outPinned.addressOf(0).reinterpret()
                )
            }
        }
        return out
    }
}
