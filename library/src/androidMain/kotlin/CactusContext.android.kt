package com.cactus

import android.util.Log
import com.cactus.internal.CactusJsonParser
import com.cactus.internal.CactusPayloadBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.math.max


actual object CactusContext {
    private val lib = CactusLibrary

    actual suspend fun initContext(modelPath: String, contextSize: UInt): Long? = withContext(Dispatchers.Default) {
        try {
            Log.d("Cactus", "Initializing context with model: $modelPath")
            // We are not using corpusDir for now, passing null pointer
            val handle = lib.cactus_init(modelPath, contextSize, null)
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
        onToken: CactusStreamingCallback?,
        quantization: Int
    ): CactusCompletionResult = withContext(Dispatchers.Default) {
        val messagesJson = CactusPayloadBuilder.buildMessagesJson(messages)
        val optionsJson = CactusPayloadBuilder.buildOptionsJson(params)
        val bufferSize = max(params.maxTokens * quantization, 2048)

        val responseBuffer = ByteArray(bufferSize)

        // Create callback wrapper if onToken is provided
        val callback: ((String, Int) -> Unit)? = if (onToken != null) {
            { token, tokenId ->
                onToken(token, tokenId.toUInt())
            }
        } else null

        val result = lib.cactus_complete(
            handle,
            messagesJson,
            responseBuffer,
            bufferSize,
            optionsJson,
            tools,
            callback,
            0L // userData - not used in our implementation
        )

        Log.i("Cactus", "Received completion result code: $result")

        if (result > 0) {
            val responseText = String(responseBuffer).trim('\u0000')

            return@withContext try {
                CactusJsonParser.parseCompletionResult(responseText)
            } catch (e: Exception) {
                CactusCompletionResult(
                    success = false,
                    response = "Error: Unable to parse the response. Exception: ${e.message}"
                )
            }
        } else {
            return@withContext CactusCompletionResult(
                success = false,
                response = "Error: completion failed with code $result"
            )
        }
    }

    actual suspend fun generateEmbedding(
        handle: Long,
        text: String,
        quantization: Int
    ): CactusEmbeddingResult = withContext(Dispatchers.Default) {
        val bufferSize = max(text.length * quantization, 1024)
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

    actual suspend fun transcribe(
        handle: Long,
        audioFilePath: String,
        prompt: String,
        params: CactusTranscriptionParams,
        onToken: CactusStreamingCallback?,
        quantization: Int
    ): CactusTranscriptionResult = withContext(Dispatchers.Default) {
        val optionsJson = CactusPayloadBuilder.buildParamsJson(params)
        val bufferSize = max(params.maxTokens * quantization, 2048)

        val responseBuffer = ByteArray(bufferSize)

        // Create callback wrapper if onToken is provided
        val callback: ((String, Int) -> Unit)? = if (onToken != null) {
            { token, tokenId ->
                onToken(token, tokenId.toUInt())
            }
        } else null

        val result = lib.cactus_transcribe(
            handle,
            audioFilePath,
            prompt,
            responseBuffer,
            bufferSize,
            optionsJson,
            callback,
            0L // userData - not used in our implementation
        )

        Log.i("Cactus", "Received completion result code: $result")

        if (result > 0) {
            val responseText = String(responseBuffer).trim('\u0000')

            return@withContext try {
                CactusJsonParser.parseTranscriptionResult(responseText)
            } catch (e: Exception) {
                CactusTranscriptionResult(
                    success = false,
                    errorMessage = "Error: Unable to parse the response. Exception: ${e.message}"
                )
            }
        } else {
            return@withContext CactusTranscriptionResult(
                success = false,
                errorMessage = "Error: completion failed with code $result"
            )
        }
    }
}