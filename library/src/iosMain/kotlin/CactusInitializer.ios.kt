package com.cactus

import com.cactus.services.CactusConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import utils.CactusLogger

internal object CactusInitializer {
    private var isInitialized = false
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        CactusLogger.d("CactusInitializer", "Initializing Cactus library for iOS")
        initialize()
    }

    private fun initialize() {
        if (isInitialized) {
            CactusLogger.d("CactusInitializer", "Cactus already initialized")
            return
        }
        isInitialized = true

        scope.launch {
            try {
                CactusConfig.init()
                CactusLogger.i("CactusInitializer", "Cactus initialization complete")
            } catch (e: Exception) {
                CactusLogger.e("CactusInitializer", "Error during Cactus initialization: ${e.message}", throwable = e)
            }
        }
    }

    fun ensureInitialized() {
        // Just accessing this object ensures init block runs
        CactusLogger.v("CactusInitializer", "ensureInitialized called")
    }
}
