package com.puretube.adfree.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Production-grade, thread-safe persistent file logger.
 * Appends all logs across app sessions to a persistent file without overwriting on launch.
 */
object AppLogger {

    private const val TAG = "AppLogger"
    private const val LOG_DIR_NAME = "logs"
    private const val LOG_FILE_NAME = "puretube.log"
    private const val MAX_LOG_SIZE_BYTES = 5 * 1024 * 1024L // Strict 5 MB limit before auto-rotation

    private val logScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logChannel = Channel<String>(capacity = 500)

    @Volatile
    private var logFile: File? = null

    @Volatile
    private var isInitialized = false

    private val dateTimeFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        }
    }

    private val timeOnlyFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        }
    }

    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return

            try {
                val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, LOG_DIR_NAME)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val file = File(dir, LOG_FILE_NAME)
                if (!file.exists()) {
                    file.createNewFile()
                }
                logFile = file
                isInitialized = true

                // Start background worker to process log messages without blocking callers
                logScope.launch {
                    for (logEntry in logChannel) {
                        writeToFileSync(logEntry)
                    }
                }

                // Write session launch header (appends to existing log history)
                val timeStr = formatCurrentDateTime()
                val header = buildString {
                    append("\n")
                    append("================================================================================\n")
                    append("=== PureTube App Session Started: $timeStr\n")
                    append("=== Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})\n")
                    append("=== App: com.puretube.adfree\n")
                    append("================================================================================\n")
                }
                writeRaw(header)
                Log.i(TAG, "AppLogger initialized. Log path: ${file.absolutePath}")

            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize AppLogger: ${e.message}", e)
            }
        }
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        enqueueLog("DEBUG", tag, message, null)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        enqueueLog("INFO", tag, message, null)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
        enqueueLog("WARN", tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
        enqueueLog("ERROR", tag, message, throwable)
    }

    private fun enqueueLog(level: String, tag: String, message: String, throwable: Throwable?) {
        try {
            val timestamp = formatCurrentDateTime()
            val threadName = Thread.currentThread().name
            val logMessage = buildString {
                append("[$timestamp] [$level/$tag] [Thread: $threadName] $message\n")
                if (throwable != null) {
                    val sw = StringWriter()
                    val pw = PrintWriter(sw)
                    throwable.printStackTrace(pw)
                    append(sw.toString())
                    append("\n")
                }
            }

            val channelResult = logChannel.trySend(logMessage)
            if (!channelResult.isSuccess) {
                // Channel full or uninitialized, write synchronously as fallback
                writeToFileSync(logMessage)
            }
        } catch (e: Throwable) {
            // Defensive: logging must never crash the app
        }
    }

    private fun writeRaw(text: String) {
        try {
            val channelResult = logChannel.trySend(text)
            if (!channelResult.isSuccess) {
                writeToFileSync(text)
            }
        } catch (e: Throwable) {}
    }

    @Synchronized
    private fun writeToFileSync(text: String) {
        try {
            val file = logFile ?: return
            
            // Log rotation if file gets too massive (> 10MB)
            if (file.exists() && file.length() > MAX_LOG_SIZE_BYTES) {
                val oldFile = File(file.parentFile, "puretube_old.log")
                if (oldFile.exists()) oldFile.delete()
                file.renameTo(oldFile)
                file.createNewFile()
            }

            FileWriter(file, true).use { writer ->
                writer.write(text)
                writer.flush()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error writing log to file: ${e.message}")
        }
    }

    /**
     * Synchronously flushes any pending logs directly to disk (e.g. before fatal crash exit)
     */
    fun flush() {
        try {
            while (true) {
                val msg = logChannel.tryReceive().getOrNull() ?: break
                writeToFileSync(msg)
            }
        } catch (e: Throwable) {}
    }

    fun getLogFile(): File? = logFile

    fun getLogFilePath(): String {
        return logFile?.absolutePath ?: "Log file not initialized"
    }

    fun readRecentLogs(maxLines: Int = 500): String {
        return try {
            val file = logFile ?: return "No log file found."
            if (!file.exists()) return "Log file is empty."

            val lines = file.readLines()
            val recent = lines.takeLast(maxLines)
            recent.joinToString("\n")
        } catch (e: Throwable) {
            "Error reading log file: ${e.message}"
        }
    }

    fun clearLogs(): Boolean {
        return try {
            val file = logFile ?: return false
            if (file.exists()) {
                FileWriter(file, false).use { it.write("") }
                i(TAG, "Log file cleared by user request.")
                true
            } else false
        } catch (e: Throwable) {
            e(TAG, "Failed to clear log file: ${e.message}", e)
            false
        }
    }

    fun shareLogFile(context: Context) {
        flush()
        try {
            val file = logFile
            if (file == null || !file.exists() || file.length() == 0L) {
                Toast.makeText(context, "Log file is empty or not created yet.", Toast.LENGTH_SHORT).show()
                return
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "PureTube App Logs")
                putExtra(Intent.EXTRA_TEXT, "Attached PureTube diagnostic log file.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Share PureTube Log File"))
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to share log file: ${e.message}", e)
            Toast.makeText(context, "Could not share log file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun formatCurrentDateTime(): String {
        return try {
            dateTimeFormatter.get()?.format(Date()) ?: SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        } catch (e: Throwable) {
            Date().toString()
        }
    }

    fun formatTimeOnly(timeMillis: Long = System.currentTimeMillis()): String {
        return try {
            timeOnlyFormatter.get()?.format(Date(timeMillis)) ?: SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timeMillis))
        } catch (e: Throwable) {
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        }
    }
}
