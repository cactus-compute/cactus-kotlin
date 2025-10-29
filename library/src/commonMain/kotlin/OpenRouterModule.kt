package com.cactus

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource
import io.ktor.utils.io.readUTF8Line
import kotlin.math.round

@Serializable
data class OpenRouterRequest(
    val model: String = "qwen/qwen-2.5-7b-instruct",
    val messages: List<ChatMessage>,
    val temperature: Float? = 0.1f,
    @SerialName("max_tokens")
    val maxTokens: Int = 200,
    @SerialName("top_p")
    val topP: Float? = 0.95f,
    val stop: List<String> = emptyList(),
    val stream: Boolean = false
)

@Serializable
data class OpenRouterResponse(
    val choices: List<OpenRouterChoice> = emptyList(),
    val usage: OpenRouterUsage? = null
)

@Serializable
data class OpenRouterChoice(
    val message: ChatMessage? = null,
    val delta: ChatMessage? = null
)

@Serializable
data class OpenRouterUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerialName("completion_tokens")
    val completionTokens: Int = 0,
    @SerialName("total_tokens")
    val totalTokens: Int = 0
)

class OpenRouterModule {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    private val baseUrl = "https://openrouter.ai/api/v1"
    private val timeSource = TimeSource.Monotonic

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun generateCompletion(
        messages: List<ChatMessage>,
        params: CactusCompletionParams,
        cactusToken: String,
        onToken: CactusStreamingCallback? = null
    ): CactusCompletionResult {
        if (onToken != null) {
            return generateCompletionStream(messages, params, cactusToken, onToken)
        }

        val requestBody = OpenRouterRequest(
            messages = messages,
            temperature = params.temperature?.toFloat(),
            maxTokens = params.maxTokens,
            topP = params.topP?.toFloat(),
            stop = params.stopSequences
        )

        lateinit var result: CactusCompletionResult
        val startTime = timeSource.markNow()
        try {
            val response = client.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $cactusToken")
                header("HTTP-Referer", "https://cactuscompute.com")
                header("X-Title", "Cactus Kotlin SDK")
                setBody(json.encodeToString(requestBody))
            }

            if (response.status == HttpStatusCode.OK) {
                val openRouterResponse: OpenRouterResponse = response.body()
                val content = openRouterResponse.choices.firstOrNull()?.message?.content ?: ""
                val usage = openRouterResponse.usage ?: OpenRouterUsage()
                result = CactusCompletionResult(
                    success = true,
                    response = content,
                    totalTimeMs = 0.0,
                    tokensPerSecond = 0.0,
                    prefillTokens = usage.promptTokens,
                    decodeTokens = usage.completionTokens,
                    totalTokens = usage.totalTokens
                )
            } else {
                val errorBody = response.body<String>()
                result = CactusCompletionResult(success = false, response = "OpenRouter API error: ${response.status} - $errorBody")
            }
        } catch (e: Exception) {
            result = CactusCompletionResult(success = false, response = "OpenRouter API error: ${e.message}")
        }
        val totalTimeMs = startTime.elapsedNow().inWholeMilliseconds.toDouble()
        val totalTokens = result.totalTokens ?: 0
        result = result.copy(
            totalTimeMs = totalTimeMs,
            tokensPerSecond = round((if (totalTimeMs > 0 && totalTokens > 0) (totalTokens * 1000.0) / totalTimeMs else 0.0) * 100) / 100.0
        )

        return result
    }

    private suspend fun generateCompletionStream(
        messages: List<ChatMessage>,
        params: CactusCompletionParams,
        cactusToken: String,
        onToken: CactusStreamingCallback
    ): CactusCompletionResult {
        val requestBody = OpenRouterRequest(
            messages = messages,
            temperature = params.temperature?.toFloat(),
            maxTokens = params.maxTokens,
            topP = params.topP?.toFloat(),
            stop = params.stopSequences,
            stream = true
        )

        var fullResponse = ""
        var promptTokens = 0
        var completionTokens = 0
        var totalTokens = 0
        var timeToFirstTokenMs = 0.0
        val startTime = TimeSource.Monotonic.markNow()

        try {
            client.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $cactusToken")
                header("HTTP-Referer", "https://cactuscompute.com")
                header("X-Title", "Cactus Kotlin SDK")
                setBody(json.encodeToString(requestBody))
            }.bodyAsChannel().let { channel ->
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line()
                    if (line?.startsWith("data: ") == true) {
                        val dataString = line.substring(6)
                        if (dataString == "[DONE]") {
                            break
                        }
                        try {
                            val data = json.decodeFromString<OpenRouterResponse>(dataString)
                            data.choices.firstOrNull()?.delta?.content?.let { content ->
                                if (timeToFirstTokenMs == 0.0) {
                                    timeToFirstTokenMs = startTime.elapsedNow().inWholeMilliseconds.toDouble()
                                }
                                onToken(content, fullResponse.length.toUInt())
                                fullResponse += content
                            }
                            data.usage?.let {
                                promptTokens = it.promptTokens
                                completionTokens = it.completionTokens
                                totalTokens = it.totalTokens
                            }
                        } catch (e: Exception) {
                            return CactusCompletionResult(success = false, response = "Unable to parse API response json: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return CactusCompletionResult(success = false, response = "OpenRouter API error: ${e.message}")
        }

        val totalTimeMs = startTime.elapsedNow().inWholeMilliseconds.toDouble()
        return CactusCompletionResult(
            success = true,
            response = fullResponse,
            timeToFirstTokenMs = timeToFirstTokenMs,
            totalTimeMs = totalTimeMs,
            tokensPerSecond = round((if (totalTimeMs > 0 && totalTokens > 0) (totalTokens * 1000.0) / totalTimeMs else 0.0) * 100) / 100.0,
            prefillTokens = promptTokens,
            decodeTokens = completionTokens,
            totalTokens = totalTokens
        )
    }
}