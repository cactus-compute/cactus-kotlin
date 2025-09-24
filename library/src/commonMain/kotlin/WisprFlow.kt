
package com.cactus

import io.ktor.client.* 
import io.ktor.client.call.* 
import io.ktor.client.plugins.contentnegotiation.* 
import io.ktor.client.request.* 
import io.ktor.http.* 
import io.ktor.serialization.kotlinx.json.* 
import kotlinx.serialization.Serializable 
import kotlinx.serialization.json.Json 
import okio.ByteString.Companion.toByteString 
import okio.Path.Companion.toPath

@Serializable
data class WisprFlowRequest(
    val audio: String,
    val language: String = "en"
)

@Serializable
data class WisprFlowResponse(
    val text: String
)

class WisprFlow {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
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
                println("WisprFlow API warmed up.")
                warmedUp = true
            } else {
                val errorBody = response.body<String>()
                println("Error from WisprFlow API during warm-up: ${response.status} - $errorBody")
            }
        } catch (e: Exception) {
            println("Error during WisprFlow warm-up: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun transcribe(filePath: String, apiKey: String): SpeechRecognitionResult? {
        if (!warmedUp) {
            warmUp(apiKey)
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
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(WisprFlowRequest(audio = audioBase64))
            }

            if (response.status == HttpStatusCode.OK) {
                val wisprResponse: WisprFlowResponse = response.body()
                result = SpeechRecognitionResult(
                    text = wisprResponse.text,
                    success = true,
                    eventSuccess = true
                )
            } else {
                val errorBody = response.body<String>()
                println("Error from WisprFlow API: ${response.status} - $errorBody")
            }
        } catch (e: Exception) {
            println("Error during WisprFlow transcription: ${e.message}")
            e.printStackTrace()
        }
        return result
    }
}

expect fun getOkioFileSystem(): okio.FileSystem
