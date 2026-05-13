package com.x500x.cursimple.app.util

import android.content.Context
import android.util.Log
import com.x500x.cursimple.core.reminder.logging.ReminderLogSink
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface AppDiagnosticsSink {
    fun write(priority: Int, tag: String, message: String, throwableText: String?)
}

object AppDiagnosticsLogger {
    private const val TAG = "AppDiagnostics"
    private const val MAX_VALUE_LENGTH = 240

    @Volatile
    private var sink: AppDiagnosticsSink? = null

    fun setSink(sink: AppDiagnosticsSink?) {
        this.sink = sink
    }

    fun info(event: String, fields: Map<String, Any?> = emptyMap()) {
        log(Log.INFO, event, fields, null)
    }

    fun warn(event: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null) {
        log(Log.WARN, event, fields, error)
    }

    fun error(event: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null) {
        log(Log.ERROR, event, fields, error)
    }

    private fun log(priority: Int, event: String, fields: Map<String, Any?>, error: Throwable?) {
        val renderedFields = renderFields(fields + errorFields(error))
        val message = buildString {
            append(event)
            if (renderedFields.isNotBlank()) {
                append(' ')
                append(renderedFields)
            }
        }
        val throwableText = error?.toStackTraceText()
        runCatching {
            Log.println(priority, TAG, message)
            if (!throwableText.isNullOrBlank()) {
                Log.println(priority, TAG, throwableText)
            }
        }
        sink?.let { currentSink ->
            runCatching {
                currentSink.write(priority, TAG, message, throwableText)
            }
        }
    }

    private fun renderFields(fields: Map<String, Any?>): String {
        return fields
            .filterValues { it != null }
            .toSortedMap()
            .map { (key, value) -> "${key.sanitizeKey()}=${value.toSafeValue()}" }
            .joinToString(" ")
    }

    private fun errorFields(error: Throwable?): Map<String, Any?> {
        return if (error == null) {
            emptyMap()
        } else {
            mapOf(
                "errorType" to error::class.java.simpleName,
                "errorMessage" to error.message.orEmpty(),
            )
        }
    }

    private fun String.sanitizeKey(): String {
        return replace(Regex("[^A-Za-z0-9_.-]"), "_")
    }

    private fun Any?.toSafeValue(): String {
        val value = when (this) {
            null -> ""
            is Boolean, is Number -> toString()
            is Enum<*> -> name
            else -> toString()
                .replace(Regex("[\\r\\n\\t]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
        return if (value.length <= MAX_VALUE_LENGTH) {
            value
        } else {
            value.take(MAX_VALUE_LENGTH) + "...(truncated)"
        }
    }

    private fun Throwable.toStackTraceText(): String {
        val writer = StringWriter()
        printStackTrace(PrintWriter(writer))
        return writer.toString()
    }
}

class AppDiagnosticsFileSink(
    private val file: File,
) : AppDiagnosticsSink, ReminderLogSink {

    constructor(context: Context) : this(appDiagnosticsFile(context))

    @Synchronized
    override fun write(priority: Int, tag: String, message: String, throwableText: String?) {
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText(
                buildString {
                    append(timestamp())
                    append(' ')
                    append(priorityLabel(priority))
                    append('/')
                    append(tag)
                    append(": ")
                    appendLine(message)
                    if (!throwableText.isNullOrBlank()) {
                        appendLine(throwableText.trimEnd())
                    }
                },
            )
            trimIfNeeded()
        }
    }

    private fun trimIfNeeded() {
        if (file.length() <= MAX_LOG_BYTES) {
            return
        }
        val content = file.readText()
        val trimmed = content.takeLast(TRIMMED_LOG_CHARS)
        file.writeText("# App diagnostics log trimmed to recent entries\n$trimmed")
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
    }

    private fun priorityLabel(priority: Int): String {
        return when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> priority.toString()
        }
    }

    companion object {
        private const val MAX_LOG_BYTES = 512 * 1024L
        private const val TRIMMED_LOG_CHARS = 384 * 1024
        const val LOG_DIR_NAME = "app-logs"
        const val LOG_FILE_NAME = "app-diagnostics.log"

        fun appDiagnosticsFile(context: Context): File {
            return appDiagnosticsFile(context.cacheDir)
        }

        fun appDiagnosticsFile(cacheDir: File): File {
            return File(File(cacheDir, LOG_DIR_NAME), LOG_FILE_NAME)
        }
    }
}
