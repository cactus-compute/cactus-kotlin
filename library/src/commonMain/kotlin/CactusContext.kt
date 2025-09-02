package com.cactus

typealias CactusContextHandle = Long

expect object CactusContext {
    suspend fun initContext(params: CactusInitParams): CactusContextHandle?
    fun freeContext(handle: CactusContextHandle)
    suspend fun completion(handle: CactusContextHandle, params: CactusCompletionParams): CactusCompletionResult
    suspend fun multimodalCompletion(handle: CactusContextHandle, params: CactusCompletionParams, mediaPaths: List<String>): CactusCompletionResult
    fun stopCompletion(handle: CactusContextHandle)
    suspend fun tokenize(handle: CactusContextHandle, text: String): CactusTokenArray
    suspend fun detokenize(handle: CactusContextHandle, tokens: IntArray): String
    suspend fun tokenizeWithMedia(handle: CactusContextHandle, text: String, mediaPaths: List<String>): CactusTokenizeResult
    suspend fun embedding(handle: CactusContextHandle, text: String): CactusFloatArray
    fun setGuideTokens(handle: CactusContextHandle, tokens: IntArray)
    suspend fun initMultimodal(handle: CactusContextHandle, mmprojPath: String, useGpu: Boolean): Int
    fun isMultimodalEnabled(handle: CactusContextHandle): Boolean
    fun supportsVision(handle: CactusContextHandle): Boolean
    fun supportsAudio(handle: CactusContextHandle): Boolean
    fun releaseMultimodal(handle: CactusContextHandle)
    suspend fun initVocoder(handle: CactusContextHandle, vocoderModelPath: String): Int
    fun isVocoderEnabled(handle: CactusContextHandle): Boolean
    
    suspend fun getFormattedAudioCompletion(handle: CactusContextHandle, speakerJson: String?, textToSpeak: String): String
    suspend fun getAudioGuideTokens(handle: CactusContextHandle, textToSpeak: String): CactusTokenArray
    suspend fun decodeAudioTokens(handle: CactusContextHandle, tokens: IntArray): CactusFloatArray
    fun releaseVocoder(handle: CactusContextHandle)
    suspend fun bench(handle: CactusContextHandle, pp: Int, tg: Int, pl: Int, nr: Int): CactusBenchResult
    suspend fun applyLoraAdapters(handle: CactusContextHandle, adapters: List<CactusLoraAdapter>): Int
    fun removeLoraAdapters(handle: CactusContextHandle)
    fun getLoadedLoraAdapters(handle: CactusContextHandle): List<CactusLoraAdapter>
    fun validateChatTemplate(handle: CactusContextHandle, useJinja: Boolean, name: String?): Boolean
    suspend fun getFormattedChat(handle: CactusContextHandle, messages: String, chatTemplate: String?): String
    suspend fun getFormattedChatWithJinja(handle: CactusContextHandle, messages: String, chatTemplate: String?, jsonSchema: String?, tools: String?, parallelToolCalls: Boolean, toolChoice: String?): CactusChatResult
    fun rewind(handle: CactusContextHandle)
    fun initSampling(handle: CactusContextHandle): Boolean
    fun beginCompletion(handle: CactusContextHandle)
    fun endCompletion(handle: CactusContextHandle)
    fun loadPrompt(handle: CactusContextHandle)
    fun loadPromptWithMedia(handle: CactusContextHandle, mediaPaths: List<String>)
    suspend fun doCompletionStep(handle: CactusContextHandle): Pair<Int, String>
    fun findStoppingStrings(handle: CactusContextHandle, text: String, lastTokenSize: Int, stopType: Int): Long
    fun getNCtx(handle: CactusContextHandle): Int
    fun getNEmbd(handle: CactusContextHandle): Int
    fun getModelDesc(handle: CactusContextHandle): String
    fun getModelSize(handle: CactusContextHandle): Long
    fun getModelParams(handle: CactusContextHandle): Long
} 