package com.kebiao.viewer.core.reminder.logging

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

interface ReminderLogSink {
    fun write(priority: Int, tag: String, message: String, throwableText: String?)
}

object ReminderLogger {
    private const val TAG = "ReminderDiagnostics"
    private const val MAX_VALUE_LENGTH = 240

    @Volatile
    private var sink: ReminderLogSink? = null

    fun setSink(sink: ReminderLogSink?) {
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
