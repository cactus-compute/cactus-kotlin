package com.cactus.services

import com.cactus.models.BufferedLogRecord
import com.cactus.models.LogRecord

expect object LogBuffer {
    suspend fun loadFailedLogRecords(): List<BufferedLogRecord>
    suspend fun clearFailedLogRecords()
    suspend fun handleFailedLogRecord(record: LogRecord)
    suspend fun handleRetryFailedLogRecord(record: LogRecord)
}
