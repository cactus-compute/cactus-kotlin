package com.cactus.services

import android.content.Context
import android.content.SharedPreferences
import com.cactus.CactusContextInitializer
import com.cactus.models.BufferedLogRecord
import com.cactus.models.LogRecord

actual object LogBuffer {
    private const val MAX_RETRIES = 3
    private const val PREFS_NAME = "cactus_log_buffer"
    private const val FAILED_LOG_RECORDS_KEY = "cactus_failed_log_records"
    
    private fun getSharedPreferences(): SharedPreferences {
        val context = CactusContextInitializer.getApplicationContext()
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    actual suspend fun loadFailedLogRecords(): List<BufferedLogRecord> {
        return try {
            val prefs = getSharedPreferences()
            val jsonString = prefs.getString(FAILED_LOG_RECORDS_KEY, null)
            
            if (jsonString == null) return emptyList()
            
            // For now, return empty list - we'll implement proper JSON parsing later
            // This is a simplified version to get the structure working
            emptyList()
        } catch (e: Exception) {
            println("Error loading failed log records: $e")
            emptyList()
        }
    }
    
    actual suspend fun clearFailedLogRecords() {
        try {
            val prefs = getSharedPreferences()
            prefs.edit().remove(FAILED_LOG_RECORDS_KEY).apply()
        } catch (e: Exception) {
            println("Error clearing failed log records: $e")
        }
    }

    actual suspend fun handleFailedLogRecord(record: LogRecord) {
        val failedRecords = loadFailedLogRecords().toMutableList()
        failedRecords.add(BufferedLogRecord(
            record = record,
            retryCount = 1,
            firstAttempt = System.currentTimeMillis()
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
                println("Retry ${bufferedRecord.retryCount}/${MAX_RETRIES} for buffered log record")
            }
            saveFailedLogRecords(failedRecords)
        }
    }

    private suspend fun saveFailedLogRecords(records: List<BufferedLogRecord>) {
        try {
            val prefs = getSharedPreferences()
            // For now, just store a simple string - we'll implement proper JSON serialization later
            val jsonString = "[]" // Empty array for now
            prefs.edit().putString(FAILED_LOG_RECORDS_KEY, jsonString).apply()
        } catch (e: Exception) {
            println("Error saving failed log records: $e")
        }
    }
}
