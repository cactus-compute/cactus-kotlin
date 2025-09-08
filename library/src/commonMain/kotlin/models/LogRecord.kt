package com.cactus.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import com.cactus.BuildConfig

@Serializable
data class LogRecord(
    @SerialName("event_type") val eventType: String,
    @SerialName("project_id") val projectId: String?,
    @SerialName("device_id") val deviceId: String?,
    val ttft: Double? = null,
    val tps: Double? = null,
    @SerialName("response_time") val responseTime: Double? = null,
    val model: String? = null,
    val tokens: Double? = null,
    @SerialName("framework") val framework: String = "kotlin",
    @SerialName("framework_version") val frameworkVersion: String = BuildConfig.FRAMEWORK_VERSION,
    val success: Boolean? = null,
    val message: String? = null,
    @SerialName("telemetry_token") val telemetryToken: String?,
    @SerialName("audio_duration") val audioDuration: Long? = null,
)

@Serializable
data class BufferedLogRecord(
    val record: LogRecord,
    var retryCount: Int = 0,
    val firstAttempt: Long
)
