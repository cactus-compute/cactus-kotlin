package com.cactus

internal object CactusLibrary {
    init {
        System.loadLibrary("cactus")
    }
    
    @JvmName("cactus_init")
    external fun cactus_init(modelPath: String, contextSize: UInt): Long
    external fun cactus_complete(
        model: Long,
        messagesJson: String,
        responseBuffer: ByteArray,
        bufferSize: Int,
        optionsJson: String?,
        callback: ((String, Int) -> Unit)?,
        userData: Long
    ): Int
    external fun cactus_destroy(model: Long)
}
