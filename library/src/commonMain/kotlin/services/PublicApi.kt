package com.cactus.services

import com.cactus.CactusModel

object CactusTelemetry {
    fun init(deviceId: String?, cactusTelemetryToken: String?): Telemetry {
        val projectId = CactusId.getProjectId()
        return Telemetry.init(projectId, deviceId, cactusTelemetryToken)
    }

    suspend fun fetchDeviceId(): String? {
        return Telemetry.fetchDeviceId()
    }

    val isInitialized: Boolean
        get() = Telemetry.isInitialized
}

object CactusUtils {
    suspend fun listModels(): List<CactusModel> {
        return Supabase.fetchModels()
    }
}
