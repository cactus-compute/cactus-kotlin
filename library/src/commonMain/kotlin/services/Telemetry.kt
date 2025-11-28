package com.cactus.services

import com.cactus.CactusCompletionResult
import com.cactus.CactusEmbeddingResult
import com.cactus.CactusInitParams
import com.cactus.CactusTranscriptionResult
import com.cactus.InferenceMode
import com.cactus.TranscriptionMode
import com.cactus.models.LogRecord
import com.cactus.utils.getDeviceId
import com.cactus.utils.getDeviceMetadata
import utils.CactusLogger

/**
 * Telemetry service for logging and analytics
 */
class Telemetry private constructor(
    private val projectId: String?,
    private val deviceId: String?
) {
    private var cactusTelemetryToken: String? = null
    companion object {
        private var _instance: Telemetry? = null
        
        val isInitialized: Boolean
            get() = _instance != null
            
        val instance: Telemetry?
            get() = _instance
        
        fun init(projectId: String?, deviceId: String?) {
            _instance = Telemetry(projectId, deviceId)
            CactusLogger.i("Telemetry initialized with projectId: $projectId, deviceId: $deviceId", tag = "Telemetry")
        }

        suspend fun fetchDeviceId(): String? {
            val deviceId = getDeviceId()
            if (deviceId == null) {
                CactusLogger.w("Failed to get device ID, registering device...", tag = "Telemetry")
                return try {
                    val deviceData = getDeviceMetadata()
                    // Convert Any values to String for registration
                    val stringDeviceData = deviceData.mapValues { it.value.toString() }
                    Supabase.registerDevice(stringDeviceData)
                } catch (e: Exception) {
                    CactusLogger.e("Error during device registration: $e", tag = "Telemetry", throwable = e)
                    null
                }
            }
            return deviceId
        }
    }

    fun setCactusToken(token: String) {
        cactusTelemetryToken = token
    }

    suspend fun logInit(success: Boolean, model: String, message: String? = null) {
        val record = LogRecord(
            eventType = "init",
            projectId = projectId,
            deviceId = deviceId,
            model = model,
            success = success,
            telemetryToken = cactusTelemetryToken,
            message = message
        )

        Supabase.sendLogRecord(record)
    }

    suspend fun logCompletion(
        result: CactusCompletionResult?,
        model: String,
        message: String? = null,
        responseTime: Double? = null,
        mode: InferenceMode? = null
    ) {
        val record = LogRecord(
            eventType = "completion",
            projectId = projectId,
            deviceId = deviceId,
            ttft = result?.timeToFirstTokenMs,
            tps = result?.tokensPerSecond,
            responseTime = responseTime,
            model = model,
            tokens = result?.totalTokens,
            success = result?.success,
            message = message,
            telemetryToken = cactusTelemetryToken,
            mode = mode?.toString()
        )

        Supabase.sendLogRecord(record)
    }

    suspend fun logTranscription(
        result: CactusTranscriptionResult?,
        model: String,
        message: String? = null,
        responseTime: Double? = null,
        mode: TranscriptionMode
    ) {
        val record = LogRecord(
            eventType = "transcription",
            projectId = projectId,
            deviceId = deviceId,
            responseTime = responseTime,
            model = model,
            success = result?.success,
            telemetryToken = cactusTelemetryToken,
            message = message,
            audioDuration = result?.totalTimeMs?.toLong(),
            mode = mode.toString()
        )

        Supabase.sendLogRecord(record)
    }

    suspend fun logEmbedding(
        result: CactusEmbeddingResult?,
        model: String,
        message: String? = null,
    ) {
        val record = LogRecord(
            eventType = "embedding",
            projectId = projectId,
            deviceId = deviceId,
            model = model,
            success = result?.success,
            message = message,
            telemetryToken = cactusTelemetryToken
        )

        Supabase.sendLogRecord(record)
    }
}
