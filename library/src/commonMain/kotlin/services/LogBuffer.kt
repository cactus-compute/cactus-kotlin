package com.cactus.services

import com.cactus.models.BufferedLogRecord
import com.cactus.models.LogRecord

expect object LogBuffer {
    internal suspend fun loadFailedLogRecords(): List<BufferedLogRecord>
    internal suspend fun clearFailedLogRecords()
    internal suspend fun handleFailedLogRecord(record: LogRecord)
    internal suspend fun handleRetryFailedLogRecord(record: LogRecord)
}
