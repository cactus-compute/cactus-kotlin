package com.cactus.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.cactus.CactusContextInitializer

// External JNI functions that will be loaded from the cactus_util library
external fun nativeRegisterApp(encryptedPayload: String): String?
external fun nativeGetDeviceId(): String?
external fun nativeSetAndroidDataDirectory(dataDirectory: String)

private object CactusUtilLoader {
    init {
        try {
            // The cactus_util library contains the utility functions
            System.loadLibrary("cactus_util")
        } catch (e: UnsatisfiedLinkError) {
            throw RuntimeException("Could not load cactus_util native library", e)
        }
    }
}

actual suspend fun getDeviceMetadata(): Map<String, Any> {
    val context = CactusContextInitializer.getApplicationContext()
    
    return try {
        mapOf(
            "model" to Build.MODEL,
            "os" to "Android",
            "os_version" to Build.VERSION.RELEASE,
            "device_id" to (Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"),
            "brand" to Build.BRAND
        )
    } catch (e: Exception) {
        mapOf(
            "model" to "Unknown",
            "os" to "Android",
            "os_version" to Build.VERSION.RELEASE,
            "device_id" to "unknown",
            "error" to e.toString()
        )
    }
}

actual suspend fun getDeviceId(): String? {
    return try {
        // Ensure the native library is loaded
        CactusUtilLoader
        
        // Initialize data directory if not already done
        val context = CactusContextInitializer.getApplicationContext()
        nativeSetAndroidDataDirectory(context.filesDir.absolutePath)
        
        // Get device ID from your native library
        nativeGetDeviceId()
    } catch (e: Exception) {
        println("Error getting device ID from native library: $e")
        null
    }
}

actual suspend fun registerApp(encString: String): String? {
    return try {
        // Ensure the native library is loaded
        CactusUtilLoader
        
        // Initialize data directory if not already done
        val context = CactusContextInitializer.getApplicationContext()
        nativeSetAndroidDataDirectory(context.filesDir.absolutePath)
        
        // Register app and get device ID from your native library
        nativeRegisterApp(encString)
    } catch (e: Exception) {
        println("Error registering app with native library: $e")
        null
    }
}
