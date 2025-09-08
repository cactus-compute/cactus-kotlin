package com.cactus.services

import com.cactus.CactusCompletionResult
import com.cactus.CactusInitParams
import com.cactus.models.LogRecord
import com.cactus.utils.getDeviceId
import com.cactus.utils.getDeviceMetadata

/**
 * Telemetry service for logging and analytics
 */
class Telemetry private constructor(
    private val projectId: String?,
    private val deviceId: String?,
    private val cactusTelemetryToken: String?
) {
    companion object {
        private var _instance: Telemetry? = null
        
        val isInitialized: Boolean
            get() = _instance != null
            
        val instance: Telemetry?
            get() = _instance
        
        fun init(projectId: String?, deviceId: String?, cactusTelemetryToken: String?): Telemetry {
            val telemetry = Telemetry(projectId, deviceId, cactusTelemetryToken)
            _instance = telemetry
            return telemetry
        }
        
        suspend fun fetchDeviceId(): String? {
            val deviceId = getDeviceId()
            if (deviceId == null) {
                println("Failed to get device ID, registering device...")
                return try {
                    val deviceData = getDeviceMetadata()
                    // Convert Any values to String for registration
                    val stringDeviceData = deviceData.mapValues { it.value.toString() }
                    Supabase.registerDevice(stringDeviceData)
                } catch (e: Exception) {
                    println("Error during device registration: $e")
                    null
                }
            }
            return deviceId
        }
    }

    suspend fun logInit(success: Boolean, options: CactusInitParams) {
        val record = LogRecord(
            eventType = "init",
            projectId = projectId,
            deviceId = deviceId,
            model = options.model,
            success = success,
            telemetryToken = cactusTelemetryToken
        )

        Supabase.sendLogRecord(record)
    }

    suspend fun logCompletion(
        result: CactusCompletionResult?,
        options: CactusInitParams,
        message: String? = null,
    ) {
        val record = LogRecord(
            eventType = "completion",
            projectId = projectId,
            deviceId = deviceId,
            ttft = result?.timeToFirstTokenMs,
            tps = result?.tokensPerSecond,
            responseTime = result?.totalTimeMs,
            model = options.model,
            tokens = result?.totalTokens?.toDouble(),
            success = result?.success,
            message = message,
            telemetryToken = cactusTelemetryToken
        )

        Supabase.sendLogRecord(record)
    }

    suspend fun logTranscription(
        result: CactusCompletionResult?,
        options: CactusInitParams,
        message: String? = null,
        responseTime: Double? = null
    ) {
        val record = LogRecord(
            eventType = "transcription",
            projectId = projectId,
            deviceId = deviceId,
            responseTime = responseTime,
            model = options.model,
            success = result?.success,
            telemetryToken = cactusTelemetryToken,
            message = message,
            audioDuration = result?.totalTimeMs?.toLong()
        )

        Supabase.sendLogRecord(record)
    }
}
