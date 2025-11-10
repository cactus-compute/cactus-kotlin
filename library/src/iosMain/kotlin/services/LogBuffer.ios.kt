package com.cactus.services

import com.cactus.models.BufferedLogRecord
import com.cactus.models.LogRecord
import kotlinx.cinterop.*
import kotlinx.serialization.json.Json
import platform.Foundation.*
import utils.CactusLogger

@OptIn(ExperimentalForeignApi::class)
actual object LogBuffer {
    private const val MAX_RETRIES = 3
    private const val FAILED_LOG_RECORDS_KEY = "cactus_failed_log_records"
    
    actual suspend fun loadFailedLogRecords(): List<BufferedLogRecord> {
        return try {
            val userDefaults = NSUserDefaults.standardUserDefaults
            val jsonString = userDefaults.stringForKey(FAILED_LOG_RECORDS_KEY)

            if (jsonString.isNullOrEmpty()) return emptyList()

            Json.decodeFromString<List<BufferedLogRecord>>(jsonString)
        } catch (e: Exception) {
            CactusLogger.e("LogBuffer", "Error loading failed log records", throwable = e)
            emptyList()
        }
    }

    actual suspend fun clearFailedLogRecords() {
        try {
            val userDefaults = NSUserDefaults.standardUserDefaults
            userDefaults.removeObjectForKey(FAILED_LOG_RECORDS_KEY)
            userDefaults.synchronize()
        } catch (e: Exception) {
            CactusLogger.e("LogBuffer", "Error clearing failed log records", throwable = e)
        }
    }

    actual suspend fun handleFailedLogRecord(record: LogRecord) {
        val failedRecords = loadFailedLogRecords().toMutableList()
        failedRecords.add(BufferedLogRecord(
            record = record,
            retryCount = 1,
            firstAttempt = NSDate().timeIntervalSince1970.toLong() * 1000 // Convert to milliseconds
        ))
        saveFailedLogRecords(failedRecords)
    }

    actual suspend fun handleRetryFailedLogRecord(record: LogRecord) {
        val failedRecords = loadFailedLogRecords().toMutableList()
        
        val existingIndex = failedRecords.indexOfFirst { buffered ->
            buffered.record.eventType == record.eventType && 
            buffered.record.deviceId == record.deviceId &&
            buffered.record.model == record.model
        }
        
        if (existingIndex >= 0) {
            val bufferedRecord = failedRecords[existingIndex]
            bufferedRecord.retryCount++

            if (bufferedRecord.retryCount > MAX_RETRIES) {
                failedRecords.removeAt(existingIndex)
            } else {
                CactusLogger.d("LogBuffer", "Retry ${bufferedRecord.retryCount}/${MAX_RETRIES} for buffered log record")
            }
            saveFailedLogRecords(failedRecords)
        }
    }

    private suspend fun saveFailedLogRecords(records: List<BufferedLogRecord>) {
        try {
            val userDefaults = NSUserDefaults.standardUserDefaults
            val jsonString = Json.encodeToString(records)
            userDefaults.setObject(jsonString, FAILED_LOG_RECORDS_KEY)
            userDefaults.synchronize()
        } catch (e: Exception) {
            CactusLogger.e("LogBuffer", "Error saving failed log records", throwable = e)
        }
    }
}
