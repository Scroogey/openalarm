package de.laurik.openalarm.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility class for persistent file logging.
 */
class FileLogger(context: Context) {
    companion object {
        private const val LOG_FILE_NAME = "best_alarm_logs.txt"
        private const val MAX_LOG_SIZE_BYTES = 5 * 1024 * 1024 // 5MB
        private const val TAG = "FileLogger"
    }

    private val logFile: File

    init {
        logFile = File(context.filesDir, LOG_FILE_NAME)
        if (!logFile.parentFile.exists()) {
            logFile.parentFile.mkdirs()
        }
    }

    /**
     * Log a message to file with specified log level.
     */
    fun log(level: Int, tag: String, message: String, throwable: Throwable? = null) {
        try {
            if (!logFile.parentFile.exists()) {
                logFile.parentFile.mkdirs()
            }
            // Rotate log file if it's getting too large
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE_BYTES) {
                rotateLogs()
            }

            FileWriter(logFile, true).use { writer ->
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                val levelStr = when (level) {
                    Log.VERBOSE -> "V"
                    Log.DEBUG -> "D"
                    Log.INFO -> "I"
                    Log.WARN -> "W"
                    Log.ERROR -> "E"
                    else -> "U"
                }

                writer.appendLine("$timestamp - $levelStr/$tag - $message")
                throwable?.let { writer.appendLine(it.stackTraceToString()) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }

    /**
     * Rotate log files by renaming the current log and creating a fresh one.
     */
    private fun rotateLogs() {
        try {
            val backupFile = File(logFile.parent, "${LOG_FILE_NAME}_old")
            if (backupFile.exists()) {
                backupFile.delete()
            }
            logFile.renameTo(backupFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate log files", e)
        }
    }

    /**
     * Get the log file content as a string.
     */
    fun getLogContent(): String {
        return try {
            if (logFile.exists()) {
                logFile.readText()
            } else {
                "No logs available"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read log file", e)
            "Failed to read logs: ${e.message}"
        }
    }

    /**
     * Delete the log file.
     */
    fun clearLogs() {
        try {
            if (logFile.exists()) {
                logFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete log file", e)
        }
    }
}
