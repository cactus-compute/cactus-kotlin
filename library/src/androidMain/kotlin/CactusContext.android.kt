package com.cactus

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest


actual object CactusContext {
    private val lib = CactusLibrary
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
        try {
            Log.d("Cactus", "Initializing context with model: $modelPath")
            val handle = lib.cactus_init(modelPath, contextSize)
            if (handle != 0L) {
                Log.d("Cactus", "Context initialized successfully")
                handle
            } else {
                Log.e("Cactus", "Failed to initialize context")
                null
            }
        } catch (e: Exception) {
            Log.e("Cactus", "Exception during context initialization: ${e.message}", e)
            null
        }
    }
    
    actual fun freeContext(handle: Long) {
        try {
            lib.cactus_destroy(handle)
            Log.d("Cactus", "Context destroyed")
        } catch (e: Exception) {
            Log.e("Cactus", "Error destroying context: ${e.message}")
        }
    }
    
    actual suspend fun completion(
        handle: Long, 
        messages: List<ChatMessage>,
        params: CactusCompletionParams,
        tools: String?,
        onToken: CactusStreamingCallback?
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
                append(
                    ",\"stop\":[${
                        params.stopSequences.joinToString(",") {
                            "\"${
                                escapeJsonString(
                                    it
                                )
                            }\""
                        }
                    }]"
                )
            }
            append("}")
        }

        val responseBuffer = ByteArray(params.bufferSize)
        val fullResponse = StringBuilder()

        // Create callback wrapper if onToken is provided
        val callback: ((String, Int) -> Unit)? = if (onToken != null) {
            { token, tokenId ->
                onToken(token, tokenId.toUInt())
                fullResponse.append(token)
            }
        } else null

        val result = lib.cactus_complete(
            handle,
            messagesJson,
            responseBuffer,
            params.bufferSize,
            optionsJson,
            tools,
            callback,
            0L // userData - not used in our implementation
        )

        Log.i("Cactus", "Received completion result code: $result")

        if (result > 0) {
            val responseText = String(responseBuffer).trim('\u0000')

            try {
                val jsonResponse = json.parseToJsonElement(responseText).jsonObject
                val success =
                    jsonResponse["success"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
                
                // Use streaming response if we have it, otherwise use the response from buffer
                val response = if (onToken != null && fullResponse.isNotEmpty()) {
                    fullResponse.toString()
                } else {
                    jsonResponse["response"]?.jsonPrimitive?.content ?: responseText
                }
                
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
                Log.e("Cactus", "Unable to parse the response json", e)
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

    actual suspend fun generateEmbedding(
        handle: Long,
        text: String,
        bufferSize: Int
    ): CactusEmbeddingResult = withContext(Dispatchers.Default) {
        Log.d("Cactus", "Generating embedding for text: ${if (text.length > 50) text.substring(0, 50) + "..." else text}")
        Log.d("Cactus", "Buffer allocated for $bufferSize float elements")

        val embeddingsBuffer = FloatArray(bufferSize)
        val embeddingDimPtr = IntArray(1) // To receive the actual dimension

        val result = lib.cactus_embed(
            handle,
            text,
            embeddingsBuffer,
            bufferSize * 4, // Buffer size in bytes (bufferSize * sizeof(float))
            embeddingDimPtr
        )

        Log.d("Cactus", "Received embedding result code: $result")

        if (result > 0) {
            val actualEmbeddingDim = embeddingDimPtr[0]
            Log.d("Cactus", "Actual embedding dimension: $actualEmbeddingDim")

            if (actualEmbeddingDim > bufferSize) {
                return@withContext CactusEmbeddingResult(
                    success = false,
                    embeddings = emptyList(),
                    dimension = 0,
                    errorMessage = "Embedding dimension ($actualEmbeddingDim) exceeds allocated buffer size ($bufferSize)"
                )
            }

            val embeddings = mutableListOf<Double>()
            for (i in 0 until actualEmbeddingDim) {
                embeddings.add(embeddingsBuffer[i].toDouble())
            }

            Log.d("Cactus", "Successfully extracted ${embeddings.size} embedding values")

            CactusEmbeddingResult(
                success = true,
                embeddings = embeddings,
                dimension = actualEmbeddingDim
            )
        } else {
            CactusEmbeddingResult(
                success = false,
                embeddings = emptyList(),
                dimension = 0,
                errorMessage = "Embedding generation failed with code $result"
            )
        }
    }

    actual fun getBundleId(): String {
        return CactusContextInitializer.getApplicationContext().packageName
    }

    actual fun sha1(input: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-1").digest(input)
}
