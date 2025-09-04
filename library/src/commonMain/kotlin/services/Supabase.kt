package com.cactus.services

import com.cactus.CactusModel
import com.cactus.models.LogRecord
import com.cactus.utils.getDeviceMetadata
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Internal model representation with download URL
 */
@Serializable
internal data class InternalModel(
    val created_at: String,
    val slug: String,
    val download_url: String,
    val size_mb: Int,
    val supports_tool_calling: Boolean,
    val supports_vision: Boolean,
    val name: String
) {
    fun toPublicModel(): CactusModel {
        return CactusModel(
            createdAt = Instant.parse(created_at),
            slug = slug,
            sizeMb = size_mb,
            supportsToolCalling = supports_tool_calling,
            supportsVision = supports_vision,
            name = name
        )
    }
}

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
    
    // Private map to store slug to downloadUrl mappings
    private val modelDownloadUrls = mutableMapOf<String, String>()

    suspend fun sendLogRecord(record: LogRecord): Boolean {
        return try {
            // First, try to send just the current record
            val success = sendLogRecordsBatch(listOf(record))
            
            if (success) {
                println("Successfully sent current log record")
                
                // Only if current record was successful, try to send buffered records
                val failedRecords = LogBuffer.loadFailedLogRecords()
                if (failedRecords.isNotEmpty()) {
                    println("Attempting to send ${failedRecords.size} buffered log records...")
                    
                    val bufferedSuccess = sendLogRecordsBatch(
                        failedRecords.map { it.record }
                    )
                    
                    if (bufferedSuccess) {
                        LogBuffer.clearFailedLogRecords()
                        println("Successfully sent ${failedRecords.size} buffered log records")
                    } else {
                        failedRecords.forEach { buffered ->
                            LogBuffer.handleRetryFailedLogRecord(buffered.record)
                        }
                        println("Failed to send buffered records, keeping them for next successful attempt")
                    }
                }
            } else {
                // Current record failed, add it to buffer
                LogBuffer.handleFailedLogRecord(record)
                println("Current log record failed, added to buffer")
            }
            success
        } catch (e: Exception) {
            println("Error sending log record: $e")
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
            
            println("Response from Supabase: ${response.status}")
            
            if (response.status != HttpStatusCode.Created && response.status != HttpStatusCode.OK) {
                val responseBody = response.body<String>()
                println("Error response body: $responseBody")
                return false
            }
            
            true
        } catch (e: Exception) {
            println("Error in sendLogRecordsBatch: $e")
            false
        }
    }

    suspend fun registerDevice(deviceData: Map<String, String>): String? {
        return try {
            val response = client.post("$SUPABASE_URL/functions/v1/device-registration") {
                header("Content-Type", "application/json")
                setBody(DeviceRegistrationRequest(device_data = deviceData))
            }
            
            if (response.status == HttpStatusCode.OK) {
                println("Device registered successfully")
                com.cactus.utils.registerApp(response.body<String>())
            } else {
                null
            }
        } catch (e: Exception) {
            println("Error registering device: $e")
            null
        }
    }

    suspend fun fetchModels(): List<CactusModel> {
        return try {
            val response = client.get("$SUPABASE_URL/rest/v1/models") {
                header("apikey", SUPABASE_KEY)
                header("Authorization", "Bearer $SUPABASE_KEY")
                header("Accept-Profile", "cactus")
                parameter("select", "*")
            }
            
            if (response.status == HttpStatusCode.OK) {
                val internalModels = response.body<List<InternalModel>>()
                
                modelDownloadUrls.clear()
                
                internalModels.map { internalModel ->
                    modelDownloadUrls[internalModel.slug] = internalModel.download_url
                    internalModel.toPublicModel()
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            println("Error fetching models: $e")
            emptyList()
        }
    }

    suspend fun getModelDownloadUrl(slug: String): String? {
        if (modelDownloadUrls.isEmpty()) {
            fetchModels()
        }
        return modelDownloadUrls[slug]
    }
}
