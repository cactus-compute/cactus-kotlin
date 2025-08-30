package com.cactus

/**
 * Exception class for Cactus-related errors
 */
class CactusException(
    override val message: String,
    val underlyingError: Throwable? = null
) : Exception(message, underlyingError) {
    
    override fun toString(): String {
        return if (underlyingError != null) {
            "CactusException: $message (Caused by: $underlyingError)"
        } else {
            "CactusException: $message"
        }
    }
}
