package com.cactus.utils

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.UIKit.*
import com.cactus.util.native.*
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
actual suspend fun getDeviceMetadata(): Map<String, Any> {
    return try {
        val device = UIDevice.currentDevice
        val systemVersion = device.systemVersion
        val model = device.model
        val name = device.name
        val identifierForVendor = device.identifierForVendor?.UUIDString ?: "unknown"
        
        mapOf(
            "model" to name,
            "os" to "iOS",
            "os_version" to systemVersion,
            "device_id" to identifierForVendor,
            "brand" to "Apple",
            "device_model" to model
        )
    } catch (e: Exception) {
        mapOf(
            "model" to "Unknown",
            "os" to "iOS",
            "os_version" to "Unknown",
            "device_id" to "unknown",
            "error" to e.toString()
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun getDeviceId(): String? {
    return try {
        ""
    } catch (e: Exception) {
        println("Error getting device ID from native library: $e")
        null
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun registerApp(encString: String): String? {
    return try {
        ""
    } catch (e: Exception) {
        println("Error registering app with native library: $e")
        null
    }
}
