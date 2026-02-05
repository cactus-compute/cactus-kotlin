@file:OptIn(ExperimentalSerializationApi::class)

package com.cactus

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okio.ByteString.Companion.toByteString
import okio.Path.Companion.toPath
import utils.CactusLogger

@Serializable
private data class WisprFlowRequest(
    val audio: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val language: List<String>? = null,
    val context: WisprContext? = null
)

@Serializable
private data class WisprContext(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val app: AppInfo? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val dictionaryContext: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val userFirstName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val userLastName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val contentText: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val conversation: WisprConversationContext? = null
)

@Serializable
private data class AppInfo(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val name: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val type: String? = null,
)

@Serializable
private data class WisprFlowResponse(
    val text: String,
    val total_time: Double
)

@Serializable
data class WisprConversationContext(
    val id: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val participants: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val messages: List<WisprConversationMessage> = emptyList()
)

@Serializable
data class WisprConversationMessage(
    /**
     * One of: user, human, assistant
     */
    val role: String,
    val content: String
)
/**
 * Configuration for WisprFlow transcription requests.
 * - [apiKey]: Your WisprFlow API key (required).
 * - [languages]: Optional list of language codes to assist transcription (e.g. ["en", "es"]). If not provided, WisprFlow will auto-detect the language.
 * - [appName]: Optional name of the app sending audio.
 * - [appType]: Optional type/category of the app ("email", "ai", "other").
 * - [dictionaryContext]: Optional list of custom words/phrases to help with transcription accuracy.
 * - [userFirstName]: Optional first name of the user (improves spelling).
 * - [userLastName]: Optional last name of the user (improves spelling).
 * - [contentsContext]: Optional additional text context related to the contents of the app the user is dictating for.
 */
data class WisprFlowConfig(
    val apiKey: String,
    val languages: List<String>? = null,
    val appName: String? = null,
    val appType: String? = null,
    val dictionaryContext: List<String> = emptyList(),
    val userFirstName: String? = null,
    val userLastName: String? = null,
    val contentsContext: String? = null,
    val conversationContext: WisprConversationContext? = null
)

class WisprFlow {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                namingStrategy = JsonNamingStrategy.SnakeCase
            })
        }
    }

    private var warmedUp = false

    suspend fun warmUp(apiKey: String) {
        try {
            val response = client.get("https://api.wisprflow.ai/api/v1/dash/warmup_dash") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $apiKey")
            }
            if (response.status == HttpStatusCode.OK) {
                CactusLogger.i("WisprFlow API warmed up.", tag = "WisprFlow")
                warmedUp = true
            } else {
                val errorBody = response.body<String>()
                CactusLogger.e("Error from WisprFlow API during warm-up: ${response.status} - $errorBody", tag = "WisprFlow")
            }
        } catch (e: Exception) {
            CactusLogger.e("Error during WisprFlow warm-up: ${e.message}", tag = "WisprFlow", throwable = e)
        }
    }

    suspend fun transcribe(filePath: String, config: WisprFlowConfig): SpeechRecognitionResult? {
        if (!warmedUp) {
            warmUp(config.apiKey)
        }
        var result: SpeechRecognitionResult? = null
        try {
            val fileSystem = getOkioFileSystem()
            val audioBytes = fileSystem.read(filePath.toPath()) {
                readByteArray()
            }
            val audioBase64 = audioBytes.toByteString().base64()

            val response = client.post("https://api.wisprflow.ai/api/v1/dash/api") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                setBody(
                    WisprFlowRequest(
                        audio = audioBase64,
                        language = config.languages,
                        context = WisprContext(
                            app = if (config.appName != null || config.appType != null) {
                                AppInfo(
                                    name = config.appName,
                                    type = config.appType
                                )
                            } else null,
                            dictionaryContext = config.dictionaryContext.ifEmpty { null },
                            userFirstName = config.userFirstName,
                            userLastName = config.userLastName,
                            contentText = config.contentsContext,
                            conversation = config.conversationContext
                        )
                    )
                )
            }

            if (response.status == HttpStatusCode.OK) {
                val wisprResponse: WisprFlowResponse = response.body()
                result = SpeechRecognitionResult(
                    text = wisprResponse.text,
                    processingTime = wisprResponse.total_time * 1000, // convert to ms
                    success = true,
                    eventSuccess = true
                )
            } else {
                val errorBody = response.body<String>()
                CactusLogger.e("Error from WisprFlow API: ${response.status} - $errorBody", tag = "WisprFlow")
            }
        } catch (e: Exception) {
            CactusLogger.e("Error during WisprFlow transcription: ${e.message}", tag = "WisprFlow", throwable = e)
        }
        return result
    }
}

expect fun getOkioFileSystem(): okio.FileSystem
