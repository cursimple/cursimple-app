package com.x500x.cursimple.core.plugin.logging

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URI
import java.security.MessageDigest

interface PluginLogSink {
    fun write(priority: Int, tag: String, message: String, throwableText: String?)
}

object PluginLogger {
    private const val TAG = "PluginDiagnostics"
    private const val MAX_VALUE_LENGTH = 240

    @Volatile
    private var sink: PluginLogSink? = null
    private var buffer: PluginLogBuffer = PluginLogBuffer.instance

    private val sensitiveKeys = setOf(
        "authorization",
        "auth",
        "cookie",
        "credential",
        "credentials",
        "jsessionid",
        "key",
        "passwd",
        "password",
        "pwd",
        "session",
        "sessionid",
        "sid",
        "ticket",
        "token",
    )

    private val reservedKeys = setOf("traceId", "pluginId", "sessionId")

    fun setSink(sink: PluginLogSink?) {
        this.sink = sink
    }

    /** Test-only: replace the singleton ring buffer. */
    internal fun setBufferForTest(buffer: PluginLogBuffer) {
        this.buffer = buffer
    }

    fun info(event: String, fields: Map<String, Any?> = emptyMap()) {
        emit(PluginLogLevel.INFO, PluginLogSource.Host, event, fields, null)
    }

    fun warn(event: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null) {
        emit(PluginLogLevel.WARN, PluginLogSource.Host, event, fields, error)
    }

    fun error(event: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null) {
        emit(PluginLogLevel.ERROR, PluginLogSource.Host, event, fields, error)
    }

    /** Public entry point for emitting structured events with explicit level and source. */
    fun event(
        level: PluginLogLevel,
        source: PluginLogSource,
        event: String,
        fields: Map<String, Any?> = emptyMap(),
        error: Throwable? = null,
    ) {
        emit(level, source, event, fields, error)
    }

    /**
     * Returns a scope that auto-injects traceId/pluginId/sessionId into every event so call sites
     * don't have to repeat them. Use [PluginLogger.scope] at flow boundaries (e.g., when a sync
     * starts and a traceId is minted).
     */
    fun scope(
        traceId: String? = null,
        pluginId: String? = null,
        sessionId: String? = null,
    ): PluginLogScope = PluginLogScope(this, traceId, pluginId, sessionId)

    fun sanitizeUrl(url: String?): String {
        val raw = url.orEmpty().trim()
        if (raw.isBlank()) {
            return ""
        }
        return runCatching {
            val uri = URI(raw)
            val query = uri.rawQuery.orEmpty()
            if (query.isBlank()) {
                return@runCatching limit(raw)
            }
            val redactedQuery = query.split("&")
                .filter(String::isNotBlank)
                .joinToString("&") { part ->
                    val segments = part.split("=", limit = 2)
                    val key = segments[0]
                    val value = segments.getOrNull(1).orEmpty()
                    "$key=${if (isSensitiveKey(key)) "***" else value}"
                }
            val sanitized = URI(
                uri.scheme,
                uri.rawAuthority,
                uri.rawPath,
                redactedQuery,
                uri.rawFragment,
            ).toString()
            limit(sanitized)
        }.getOrElse {
            limit(redactInlineSensitiveValues(raw))
        }
    }

    fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    internal fun renderFieldsForTest(fields: Map<String, Any?>): String =
        renderFlatFields(buildFieldStrings(fields))

    internal fun emit(
        level: PluginLogLevel,
        source: PluginLogSource,
        event: String,
        fields: Map<String, Any?>,
        error: Throwable?,
    ) {
        val timestamp = System.currentTimeMillis()
        val mergedFields = fields + errorFields(error)
        val renderedFields = runCatching { buildFieldStrings(mergedFields) }
            .getOrDefault(emptyMap())
        val correlation = extractCorrelation(renderedFields)
        val payloadFields = renderedFields - reservedKeys
        val stack = error?.toStackTraceText()
        val entry = PluginLogEntry(
            sequence = buffer.nextSequence(),
            timestampMs = timestamp,
            level = level,
            event = event,
            source = source,
            traceId = correlation.traceId,
            pluginId = correlation.pluginId,
            sessionId = correlation.sessionId,
            fields = payloadFields,
            errorStack = stack,
        )
        buffer.add(entry)

        val message = renderJsonLine(entry)
        runCatching {
            Log.println(level.priority, TAG, message)
            if (stack != null) {
                Log.println(level.priority, TAG, stack)
            }
        }
        sink?.let { currentSink ->
            runCatching { currentSink.write(level.priority, TAG, message, stack) }
        }
    }

    private fun buildFieldStrings(fields: Map<String, Any?>): Map<String, String> {
        val rendered = LinkedHashMap<String, String>(fields.size)
        fields.forEach { (rawKey, rawValue) ->
            if (rawValue == null) return@forEach
            val key = rawKey.sanitizeKey()
            val value = if (isSensitiveKey(rawKey)) "***" else rawValue.toSafeValue()
            rendered[key] = value
        }
        return rendered
    }

    private fun extractCorrelation(fields: Map<String, String>): Correlation {
        return Correlation(
            traceId = fields["traceId"]?.takeIf(String::isNotBlank),
            pluginId = fields["pluginId"]?.takeIf(String::isNotBlank),
            sessionId = fields["sessionId"]?.takeIf(String::isNotBlank),
        )
    }

    private data class Correlation(val traceId: String?, val pluginId: String?, val sessionId: String?)

    private fun renderJsonLine(entry: PluginLogEntry): String {
        val builder = StringBuilder(128 + entry.fields.size * 24)
        builder.append('{')
        builder.appendJsonField("ts", entry.timestampMs)
        builder.append(',')
        builder.appendJsonField("lv", entry.level.short)
        builder.append(',')
        builder.appendJsonField("src", entry.source.token)
        builder.append(',')
        builder.appendJsonField("ev", entry.event)
        entry.traceId?.let {
            builder.append(',')
            builder.appendJsonField("tid", it)
        }
        entry.pluginId?.let {
            builder.append(',')
            builder.appendJsonField("plg", it)
        }
        entry.sessionId?.let {
            builder.append(',')
            builder.appendJsonField("sid", it)
        }
        if (entry.fields.isNotEmpty()) {
            builder.append(',')
            builder.append("\"f\":{")
            entry.fields.entries.forEachIndexed { index, (key, value) ->
                if (index > 0) builder.append(',')
                builder.appendJsonField(key, value)
            }
            builder.append('}')
        }
        builder.append('}')
        return builder.toString()
    }

    /** Plain key=value rendering retained for backward compatibility in unit tests. */
    private fun renderFlatFields(fields: Map<String, String>): String {
        return fields.toSortedMap()
            .entries
            .joinToString(" ") { "${it.key}=${it.value}" }
    }

    private fun errorFields(error: Throwable?): Map<String, Any?> {
        return if (error == null) {
            emptyMap()
        } else {
            mapOf(
                "errorType" to error::class.java.simpleName,
                "errorMessage" to redactInlineSensitiveValues(error.message.orEmpty()),
            )
        }
    }

    private fun String.sanitizeKey(): String {
        return replace(Regex("[^A-Za-z0-9_.-]"), "_")
    }

    private fun Any?.toSafeValue(): String {
        return when (this) {
            null -> ""
            is Boolean, is Number -> toString()
            is Enum<*> -> name
            else -> limit(
                redactInlineSensitiveValues(
                    toString()
                        .replace(Regex("\\s+"), " ")
                        .replace(Regex("[\\r\\n\\t]"), " ")
                        .trim(),
                ),
            )
        }
    }

    private fun limit(value: String): String {
        return if (value.length <= MAX_VALUE_LENGTH) {
            value
        } else {
            value.take(MAX_VALUE_LENGTH) + "...(truncated)"
        }
    }

    private fun redactInlineSensitiveValues(value: String): String {
        var redacted = value
        sensitiveKeys.forEach { key ->
            redacted = redacted.replace(
                Regex("(?i)($key\\s*[=:]\\s*)[^&\\s]+"),
                "$1***",
            )
        }
        return redacted
    }

    private fun isSensitiveKey(key: String): Boolean {
        val normalized = runCatching {
            java.net.URLDecoder.decode(key, Charsets.UTF_8.name())
        }.getOrDefault(key).lowercase()
        if (reservedKeys.any { it.equals(key, ignoreCase = true) }) {
            return false
        }
        return sensitiveKeys.any { sensitive -> normalized == sensitive || normalized.contains(sensitive) }
    }

    private fun Throwable.toStackTraceText(): String {
        val writer = StringWriter()
        printStackTrace(PrintWriter(writer))
        return redactInlineSensitiveValues(writer.toString())
    }
}

private fun StringBuilder.appendJsonField(key: String, value: Any) {
    append('"').appendJsonEscaped(key).append('"').append(':')
    when (value) {
        is Number, is Boolean -> append(value.toString())
        else -> append('"').appendJsonEscaped(value.toString()).append('"')
    }
}

private fun StringBuilder.appendJsonEscaped(value: String): StringBuilder {
    for (ch in value) {
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
        }
    }
    return this
}

class PluginLogScope internal constructor(
    private val logger: PluginLogger,
    val traceId: String?,
    val pluginId: String?,
    val sessionId: String?,
) {
    fun info(event: String, fields: Map<String, Any?> = emptyMap()) {
        logger.emit(PluginLogLevel.INFO, PluginLogSource.Host, event, withCorrelation(fields), null)
    }

    fun warn(event: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null) {
        logger.emit(PluginLogLevel.WARN, PluginLogSource.Host, event, withCorrelation(fields), error)
    }

    fun error(event: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null) {
        logger.emit(PluginLogLevel.ERROR, PluginLogSource.Host, event, withCorrelation(fields), error)
    }

    fun with(
        traceId: String? = this.traceId,
        pluginId: String? = this.pluginId,
        sessionId: String? = this.sessionId,
    ): PluginLogScope = PluginLogScope(logger, traceId, pluginId, sessionId)

    private fun withCorrelation(fields: Map<String, Any?>): Map<String, Any?> {
        if (traceId == null && pluginId == null && sessionId == null) return fields
        val merged = LinkedHashMap<String, Any?>(fields.size + 3)
        traceId?.let { merged["traceId"] = it }
        pluginId?.let { merged["pluginId"] = it }
        sessionId?.let { merged["sessionId"] = it }
        merged.putAll(fields)
        return merged
    }
}
