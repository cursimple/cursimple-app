package com.kebiao.viewer.core.plugin.logging

import android.util.Log
import java.net.URI
import java.security.MessageDigest

object PluginLogger {
    private const val TAG = "PluginDiagnostics"
    private const val MAX_VALUE_LENGTH = 240
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

    fun info(event: String, fields: Map<String, Any?> = emptyMap()) {
        log(Log.INFO, event, fields, null)
    }

    fun warn(event: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null) {
        log(Log.WARN, event, fields, error)
    }

    fun error(event: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null) {
        log(Log.ERROR, event, fields, error)
    }

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

    internal fun renderFieldsForTest(fields: Map<String, Any?>): String = renderFields(fields)

    private fun log(priority: Int, event: String, fields: Map<String, Any?>, error: Throwable?) {
        runCatching {
            val renderedFields = renderFields(fields + errorFields(error))
            val message = buildString {
                append(event)
                if (renderedFields.isNotBlank()) {
                    append(' ')
                    append(renderedFields)
                }
            }
            if (error != null) {
                Log.println(priority, TAG, message)
                Log.println(priority, TAG, Log.getStackTraceString(error))
            } else {
                Log.println(priority, TAG, message)
            }
        }
    }

    private fun renderFields(fields: Map<String, Any?>): String {
        return fields
            .filterValues { it != null }
            .toSortedMap()
            .map { (key, value) ->
                val renderedValue = if (isSensitiveKey(key)) "***" else value.toSafeValue()
                "${key.sanitizeKey()}=$renderedValue"
            }
            .joinToString(" ")
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
        return sensitiveKeys.any { sensitive -> normalized == sensitive || normalized.contains(sensitive) }
    }
}
