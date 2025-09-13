package com.cactus

import android.content.Context
import android.util.Log
import com.cactus.services.CactusTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object CactusContextInitializer {
    private var applicationContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    init {
        try {
            Log.d("CactusInit", "Starting Cactus library initialization...")
            Log.d("CactusInit", "Loading native library 'cactus'...")
            System.loadLibrary("cactus")
            Log.d("CactusInit", "Native library 'cactus' loaded successfully!")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("CactusInit", "Failed to load native library 'cactus': ${e.message}", e)
            e.printStackTrace()
        }
    }
    
    fun initialize(context: Context) {
        Log.d("CactusInit", "Initializing Cactus context...")
        if (applicationContext == null) {
            applicationContext = context.applicationContext
            Log.d("CactusInit", "Application context set")
            Log.d("CactusInit", "Cactus initialization complete")
        } else {
            Log.d("CactusInit", "Cactus already initialized")
        }
        scope.launch {
            CactusTelemetry.init()
        }
    }
    
    fun getApplicationContext(): Context {
        return applicationContext ?: throw kotlin.IllegalStateException(
            "CactusContextInitializer not initialized. Call CactusContextInitializer.initialize(context) in your Application or Activity."
        )
    }
} 