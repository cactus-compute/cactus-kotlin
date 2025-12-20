package com.cactus.utils

/**
 * Expect/actual declarations for device info utilities
 */
expect suspend fun getDeviceMetadata(): Map<String, Any>
expect suspend fun getDeviceId(cactusToken: String?): String?
expect suspend fun registerApp(encString: String): String?
