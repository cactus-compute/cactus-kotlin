package com.cactus.services

import com.cactus.CactusModel

object CactusTelemetry {
    fun init(projectId: String, deviceId: String): Telemetry {
        return Telemetry.init(projectId, deviceId)
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
