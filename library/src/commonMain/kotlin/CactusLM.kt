package com.cactus

import com.cactus.services.Telemetry
import kotlin.collections.lastIndex

class CactusLM {
    private var handle: Long? = null
    private var lastDownloadedFilename: String? = null

    suspend fun download(
        url: String = "https://ytmrvwsckmqyfpnwfcme.supabase.co/storage/v1/object/sign/cactus-models/qwen3-600m.zip?token=eyJraWQiOiJzdG9yYWdlLXVybC1zaWduaW5nLWtleV9kMjQzNjhmOS02MmEzLTQ2NDQtYjI0Ni01NjdjZWEyYjk2MTIiLCJhbGciOiJIUzI1NiJ9.eyJ1cmwiOiJjYWN0dXMtbW9kZWxzL3F3ZW4zLTYwMG0uemlwIiwiaWF0IjoxNzU2MjcwNzI5LCJleHAiOjE3ODc4MDY3Mjl9.UJoA6ORgZ67FKXneN_ekyU3lTe1fJ4siryFM6uR3pMU",
        filename: String? = null
    ): Boolean {
        val actualFilename = filename ?: url.split('?').first().split('/').last()
        val success = downloadModel(url, actualFilename)
        if (success) {
            lastDownloadedFilename = actualFilename.replace(".zip", "")
        }
        return success
    }
    
    suspend fun init(filename: String = lastDownloadedFilename ?: "Qwen3-0.6B-Q8_0.gguf", contextSize: UInt = 2048u): Boolean {
        val initParams = CactusInitParams(model = filename, contextSize = contextSize.toInt())
        
        try {
            handle = initializeModel(filename, contextSize)
            val success = handle != null
            
            // Log initialization result
            Telemetry.instance?.logInit(success, initParams)
            
            return success
        } catch (e: Exception) {
            // Log initialization failure
            Telemetry.instance?.logInit(false, initParams)
            throw CactusException("Failed to initialize model: ${e.message}", e)
        }
    }
    
    suspend fun completion(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        topP: Float = 0.9f
    ): CactusCompletionResult? {
        val currMessage = listOf<ChatMessage>(ChatMessage(prompt, "user"))
        val params = CactusCompletionParams(
            temperature = temperature.toDouble(),
            topP = topP.toDouble(),
            maxTokens = maxTokens
        )
        val initParams = CactusInitParams(model = lastDownloadedFilename)
        
        try {
            val response: CactusCompletionResult? = handle?.let { h ->
                generateCompletion(h, currMessage, params)
            }
            
            // Log completion result
            Telemetry.instance?.logCompletion(
                result = response,
                options = initParams,
                success = response?.success
            )
            
            return response
        } catch (e: Exception) {
            // Log completion failure
            Telemetry.instance?.logCompletion(
                result = null,
                options = initParams,
                message = e.message,
                success = false
            )
            throw CactusException("Completion failed: ${e.message}", e)
        }
    }
    
    fun unload() {
        handle?.let { h ->
            uninitializeModel(h)
            handle = null
        }
    }
    
    fun isLoaded(): Boolean = handle != null
}

expect suspend fun downloadModel(url: String, filename: String): Boolean
expect suspend fun initializeModel(modelFolderName: String, contextSize: UInt): Long?
expect suspend fun generateCompletion(handle: Long, messages: List<ChatMessage>, options: CactusCompletionParams): CactusCompletionResult?
expect fun uninitializeModel(handle: Long)
