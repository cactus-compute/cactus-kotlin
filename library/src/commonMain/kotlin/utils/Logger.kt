package utils

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.platformLogWriter

/**
 * Centralized logging utility for the Cactus library.
 * Wraps Kermit logger to provide consistent logging across all platforms.
 */
object CactusLogger {
    private var logger = makeLogger(listOf(platformLogWriter()))
    private fun makeLogger(logWriters: List<LogWriter>) =
        Logger(
            config = StaticConfig(
                minSeverity = Severity.Verbose,
                logWriterList = logWriters
            ),
            tag = "Cactus"
        )

    /**
     * Not thread safe, call only during app initialization.
     * Overrides the default logger with the provided [logWriter].
     */
    fun setLogWriter(logWriter: LogWriter) {
        logger = makeLogger(listOf(logWriter))
    }

    fun v(message: String, tag: String = "Cactus", throwable: Throwable? = null) {
        logger.v(throwable = throwable, tag = tag) { message }
    }

    fun d(message: String, tag: String = "Cactus", throwable: Throwable? = null) {
        logger.d(throwable = throwable, tag = tag) { message }
    }

    fun i(message: String, tag: String = "Cactus", throwable: Throwable? = null) {
        logger.i(throwable = throwable, tag = tag) { message }
    }

    fun w(message: String, tag: String = "Cactus", throwable: Throwable? = null) {
        logger.w(throwable = throwable, tag = tag) { message }
    }

    fun e(message: String, tag: String = "Cactus", throwable: Throwable? = null) {
        logger.e(throwable = throwable, tag = tag) { message }
    }
}