@file:OptIn(ExperimentalForeignApi::class)
package com.cactus

import com.cactus.internal.CactusJsonParser
import com.cactus.internal.CactusPayloadBuilder
import com.cactus.native.*
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreCrypto.CC_SHA1
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH
import platform.Foundation.NSBundle
import utils.CactusLogger
import kotlin.math.max

// Global variables for iOS callback handling
private var currentStreamingCallback: CactusStreamingCallback? = null
private var currentStreamingResponse: StringBuilder? = null

// Static callback function for iOS FFI
private val nativeTokenCallback = staticCFunction { token: CPointer<ByteVar>?, tokenId: UInt, userData: COpaquePointer? ->
    token?.let { tokenPtr ->
        val tokenString = tokenPtr.toKString()
        currentStreamingResponse?.append(tokenString)
        currentStreamingCallback?.invoke(tokenString, tokenId)
    }
    Unit
}

actual object CactusContext {
    actual suspend fun initContext(modelPath: String, contextSize: UInt): Long? = withContext(Dispatchers.Default) {
        return@withContext memScoped {
            // We are not using corpusDir for now, passing null pointer
            val handle = cactus_init(modelPath, contextSize.toULong(), null)
            handle?.rawValue?.toLong()
        }
    }

    actual fun freeContext(handle: Long) {
        cactus_destroy(handle.toCPointer())
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

        return@withContext memScoped {
            val bufferSize = max(params.maxTokens * quantization, 2048)
            val responseBuffer = allocArray<ByteVar>(bufferSize)

            // Set up streaming if callback is provided
            val fullResponse = if (onToken != null) {
                currentStreamingCallback = onToken
                currentStreamingResponse = StringBuilder()
                currentStreamingResponse
            } else {
                currentStreamingCallback = null
                currentStreamingResponse = null
                null
            }

            val callback = if (onToken != null) nativeTokenCallback else null

            val result = cactus_complete(
                handle.toCPointer(),
                messagesJson,
                responseBuffer,
                bufferSize.convert(),
                optionsJson,
                tools,
                callback,
                null
            )
            
            // Clean up global state
            currentStreamingCallback = null
            currentStreamingResponse = null

            if (result > 0) {
                val responseText = responseBuffer.toKString()

                try {
                    CactusJsonParser.parseCompletionResult(responseText)
                } catch (e: Exception) {
                    CactusCompletionResult(
                        success = false,
                        response = "Error: Unable to parse the response. Exception: ${e.message}"
                    )
                }
            } else {
                CactusCompletionResult(
                    success = false,
                    response = "Error: completion failed with code $result"
                )
            }
        }
    }

    actual suspend fun generateEmbedding(
        handle: Long,
        text: String,
        quantization: Int
    ): CactusEmbeddingResult = withContext(Dispatchers.Default) {
        return@withContext memScoped {
            val bufferSize = max(text.length * quantization, 1024)
            CactusLogger.d("CactusContext", "Generating embedding for text: ${if (text.length > 50) text.substring(0, 50) + "..." else text}")
            CactusLogger.d("CactusContext", "Buffer allocated for $bufferSize float elements")

            val embeddingDimPtr = alloc<ULongVar>()
            val embeddingsBuffer = allocArray<FloatVar>(bufferSize)

            // Calculate buffer size in bytes (bufferSize * sizeof(float))
            val bufferSizeInBytes = bufferSize * 4

            val result = cactus_embed(
                handle.toCPointer(),
                text,
                embeddingsBuffer,
                bufferSizeInBytes.convert(),
                embeddingDimPtr.ptr
            )

            CactusLogger.d("CactusContext", "Received embedding result code: $result")

            if (result > 0) {
                val actualEmbeddingDim = embeddingDimPtr.value.toInt()
                CactusLogger.d("CactusContext", "Actual embedding dimension: $actualEmbeddingDim")

                if (actualEmbeddingDim > bufferSize) {
                    return@memScoped CactusEmbeddingResult(
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

                CactusLogger.d("CactusContext", "Successfully extracted ${embeddings.size} embedding values")

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
    }

    actual suspend fun transcribe(
        handle: Long,
        audioFilePath: String,
        prompt: String,
        params: CactusTranscriptionParams,
        onToken: CactusStreamingCallback?,
        quantization: Int
    ): CactusTranscriptionResult = withContext(Dispatchers.Default) {
        val optionsJson = CactusPayloadBuilder.buildParamsJson(params)

        return@withContext memScoped {
            val bufferSize = max(params.maxTokens * quantization, 2048)
            val responseBuffer = allocArray<ByteVar>(bufferSize)

            // Set up streaming if callback is provided
            val fullResponse = if (onToken != null) {
                currentStreamingCallback = onToken
                currentStreamingResponse = StringBuilder()
                currentStreamingResponse
            } else {
                currentStreamingCallback = null
                currentStreamingResponse = null
                null
            }

            val callback = if (onToken != null) nativeTokenCallback else null

            val result = cactus_transcribe(
                handle.toCPointer(),
                audioFilePath,
                prompt,
                responseBuffer,
                bufferSize.convert(),
                optionsJson,
                callback,
                null
            )

            // Clean up global state
            currentStreamingCallback = null
            currentStreamingResponse = null

            if (result > 0) {
                val responseText = responseBuffer.toKString()

                try {
                    CactusJsonParser.parseTranscriptionResult(responseText)
                } catch (e: Exception) {
                    CactusTranscriptionResult(
                        success = false,
                        errorMessage = "Error: Unable to parse the response. Exception: ${e.message}"
                    )
                }
            } else {
                CactusTranscriptionResult(
                    success = false,
                    errorMessage = "Error: completion failed with code $result"
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