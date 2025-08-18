package com.cactus

import android.util.Log
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.collections.forEachIndexed
import kotlin.collections.isNotEmpty
import kotlin.collections.joinToString
import kotlin.jvm.java
import kotlin.text.replace
import kotlin.text.toBooleanStrictOrNull
import kotlin.text.toDoubleOrNull
import kotlin.text.toIntOrNull
import kotlin.text.trim

internal interface CactusLibrary : Library {
    fun cactus_init(modelPath: String): Pointer?
    fun cactus_complete(
        model: Pointer, 
        messagesJson: String, 
        responseBuffer: ByteArray, 
        bufferSize: Int, 
        optionsJson: String?
    ): Int
    fun cactus_destroy(model: Pointer)
    
    companion object {
        val INSTANCE: CactusLibrary = Native.load("cactus", CactusLibrary::class.java)
    }
}


actual object CactusContext {
    private val lib = CactusLibrary.INSTANCE
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

    actual suspend fun initContext(modelPath: String): Long? = withContext(Dispatchers.Default) {
        try {
            Log.d("Cactus", "Initializing context with model: $modelPath")
            val handle = lib.cactus_init(modelPath)
            if (handle != null && handle != Pointer.NULL) {
                Log.d("Cactus", "Context initialized successfully")
                Pointer.nativeValue(handle)
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
            lib.cactus_destroy(Pointer(handle))
            Log.d("Cactus", "Context destroyed")
        } catch (e: Exception) {
            Log.e("Cactus", "Error destroying context: ${e.message}")
        }
    }
    
    actual suspend fun completion(
        handle: Long, 
        messages: List<ChatMessage>,
        params: CactusCompletionParams
    ): CactusCompletionResult = withContext(Dispatchers.Default) {

        // Convert messages to JSON format expected by C++ parser
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

        val result = lib.cactus_complete(
            Pointer(handle),
            messagesJson,
            responseBuffer,
            params.bufferSize,
            optionsJson
        )

        Log.i("Cactus", "Received completion result code: $result")

        if (result > 0) {
            val responseText = String(responseBuffer).trim('\u0000')

            try {
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
}
