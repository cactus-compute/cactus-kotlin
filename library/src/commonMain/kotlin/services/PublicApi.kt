package com.cactus.services

import com.cactus.CactusModel

object CactusTelemetry {
    fun setTelemetryToken(token: String) {
        Telemetry.instance?.setCactusToken(token)
    }
    internal suspend fun init() {
        val projectId = CactusId.getProjectId()
        val deviceId = Telemetry.fetchDeviceId()
        Telemetry.init(projectId, deviceId)
    }
}

object CactusUtils {
    suspend fun listModels(): List<CactusModel> {
        return Supabase.fetchModels()
    }
}
