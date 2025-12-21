package com.cactus.services

import com.cactus.CactusModel

object CactusConfig {
    var isTelemetryEnabled: Boolean = true
    var cactusProKey: String? = null

    fun setTelemetryToken(token: String) {
        Telemetry.instance?.setCactusToken(token)
    }

    fun setProKey(proKey: String) {
        cactusProKey = proKey
    }

    internal suspend fun init() {
        if(!Telemetry.isInitialized && isTelemetryEnabled) {
            val projectId = CactusId.getProjectId()
            val deviceId = Telemetry.fetchDeviceId()
            Telemetry.init(projectId, deviceId)
        }
    }
}

object CactusUtils {
    suspend fun listModels(): List<CactusModel> {
        return Supabase.fetchModels()
    }
}
