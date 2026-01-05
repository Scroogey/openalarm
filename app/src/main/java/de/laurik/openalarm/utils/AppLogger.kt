package de.laurik.openalarm.utils

import android.content.Context
import android.util.Log

/**
 * Wrapper for Android's Log class that adds file logging.
 *
 * Usage:
 * val logger = AppLogger(context)
 * logger.d("MyTag", "Debug message")
 */
class AppLogger(context: Context) {
    private val fileLogger = FileLogger(context)

    fun v(tag: String, message: String, throwable: Throwable? = null) {
        Log.v(tag, message, throwable)
        fileLogger.log(Log.VERBOSE, tag, message, throwable)
    }

    fun d(tag: String, message: String, throwable: Throwable? = null) {
        Log.d(tag, message, throwable)
        fileLogger.log(Log.DEBUG, tag, message, throwable)
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        Log.i(tag, message, throwable)
        fileLogger.log(Log.INFO, tag, message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        fileLogger.log(Log.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        fileLogger.log(Log.ERROR, tag, message, throwable)
    }

    fun getLogContent(): String {
        return fileLogger.getLogContent()
    }

    fun clearLogs() {
        fileLogger.clearLogs()
    }
}