package com.cactus.services

import com.cactus.CactusModel
import com.cactus.VoiceModel
import com.cactus.models.LogRecord
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.cactus.BuildConfig
import utils.CactusLogger

/**
 * Device registration request
 */
@Serializable
internal data class DeviceRegistrationRequest(
    val device_data: Map<String, String>
)
/**
 * Supabase service for API communication
 */
object Supabase {
    private const val SUPABASE_URL = "https://vlqqczxwyaodtcdmdmlw.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZscXFjenh3eWFvZHRjZG1kbWx3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTE1MTg2MzIsImV4cCI6MjA2NzA5NDYzMn0.nBzqGuK9j6RZ6mOPWU2boAC_5H9XDs-fPpo5P3WZYbI"
    
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }
    }

    suspend fun sendLogRecord(record: LogRecord): Boolean {
        if (!CactusTelemetry.isTelemetryEnabled) {
            CactusLogger.d("Telemetry is disabled, skipping log record", tag = "Supabase")
            return true
        }

        return try {
            val success = sendLogRecordsBatch(listOf(record))

            if (success) {
                CactusLogger.d("Successfully sent current log record", tag = "Supabase")

                val failedRecords = LogBuffer.loadFailedLogRecords()
                if (failedRecords.isNotEmpty()) {
                    CactusLogger.i("Attempting to send ${failedRecords.size} buffered log records...", tag = "Supabase")

                    val bufferedSuccess = sendLogRecordsBatch(
                        failedRecords.map { it.record }
                    )

                    if (bufferedSuccess) {
                        LogBuffer.clearFailedLogRecords()
                        CactusLogger.i("Successfully sent ${failedRecords.size} buffered log records", tag = "Supabase")
                    } else {
                        failedRecords.forEach { buffered ->
                            LogBuffer.handleRetryFailedLogRecord(buffered.record)
                        }
                        CactusLogger.w("Failed to send buffered records, keeping them for next successful attempt", tag = "Supabase")
                    }
                }
            } else {
                LogBuffer.handleFailedLogRecord(record)
                CactusLogger.w("Current log record failed, added to buffer", tag = "Supabase")
            }
            success
        } catch (e: Exception) {
            CactusLogger.e("Error sending log record: $e", tag = "Supabase", throwable = e)
            LogBuffer.handleFailedLogRecord(record)
            false
        }
    }
    
    private suspend fun sendLogRecordsBatch(records: List<LogRecord>): Boolean {
        return try {
            val response = client.post("$SUPABASE_URL/rest/v1/logs") {
                header("apikey", SUPABASE_KEY)
                header("Authorization", "Bearer $SUPABASE_KEY")
                header("Content-Type", "application/json")
                header("Prefer", "return=minimal")
                header("Content-Profile", "cactus")
                
                setBody(records)
            }

            CactusLogger.d("Response from Supabase: ${response.status}", tag = "Supabase")

            if (response.status != HttpStatusCode.Created && response.status != HttpStatusCode.OK) {
                val responseBody = response.body<String>()
                CactusLogger.e("Error response body: $responseBody", tag = "Supabase")
                return false
            }

            true
        } catch (e: Exception) {
            CactusLogger.e("Error in sendLogRecordsBatch: $e", tag = "Supabase", throwable = e)
            false
        }
    }

    suspend fun registerDevice(deviceData: Map<String, String>): String? {
        if (!CactusTelemetry.isTelemetryEnabled) {
            CactusLogger.d("Telemetry is disabled, returning telemetry-disabled device ID", tag = "Supabase")
            return com.cactus.utils.registerApp("telemetry-disabled")
        }

        return try {
            val response = client.post("$SUPABASE_URL/functions/v1/device-registration") {
                header("Content-Type", "application/json")
                setBody(DeviceRegistrationRequest(device_data = deviceData))
            }

            if (response.status == HttpStatusCode.OK) {
                CactusLogger.i("Device registered successfully", tag = "Supabase")
                com.cactus.utils.registerApp(response.body<String>())
            } else {
                null
            }
        } catch (e: Exception) {
            CactusLogger.e("Error registering device: $e", tag = "Supabase", throwable = e)
            null
        }
    }

    suspend fun getModel(slug: String): CactusModel? {
        ModelCache.loadModel(slug)?.let { return it }

        return try {
            val response = client.get("$SUPABASE_URL/functions/v1/get-models?slug=$slug&sdk_name=kotlin&sdk_version=${BuildConfig.FRAMEWORK_VERSION}") {
                header("Authorization", "Bearer $SUPABASE_KEY")
            }

            if (response.status == HttpStatusCode.OK) {
                val model = response.body<CactusModel>()
                ModelCache.saveModel(model)
                model
            } else {
                null
            }
        } catch (e: Exception) {
            CactusLogger.e("Error fetching model: $e", tag = "Supabase", throwable = e)
            null
        }
    }

    suspend fun fetchModels(): List<CactusModel> {
        return try {
            val response = client.get("$SUPABASE_URL/functions/v1/get-models?sdk_name=kotlin&sdk_version=${BuildConfig.FRAMEWORK_VERSION}") {
                header("Authorization", "Bearer $SUPABASE_KEY")
            }

            if (response.status == HttpStatusCode.OK) {
                response.body<List<CactusModel>>()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            CactusLogger.e("Error fetching models: $e", tag = "Supabase", throwable = e)
            emptyList()
        }
    }

    suspend fun fetchVoiceModels(): List<VoiceModel> {
        return try {
            val response = client.get("$SUPABASE_URL/rest/v1/whisper") {
                header("apikey", SUPABASE_KEY)
                header("Authorization", "Bearer $SUPABASE_KEY")
                header("Accept-Profile", "cactus")
                parameter("select", "*")
            }

            if (response.status == HttpStatusCode.OK) {
                val voiceModels = response.body<List<VoiceModel>>()
                ModelCache.saveVoiceModels(voiceModels)
                voiceModels
            } else {
                ModelCache.loadVoiceModels()
            }
        } catch (e: Exception) {
            CactusLogger.e("Error fetching voice models: $e", tag = "Supabase", throwable = e)
            ModelCache.loadVoiceModels()
        }
    }
}
