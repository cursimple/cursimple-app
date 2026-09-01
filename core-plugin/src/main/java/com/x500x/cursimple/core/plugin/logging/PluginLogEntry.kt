package com.x500x.cursimple.core.plugin.logging

import android.util.Log

enum class PluginLogLevel(val priority: Int, val short: String) {
    DEBUG(Log.DEBUG, "DEBUG"),
    INFO(Log.INFO, "INFO"),
    WARN(Log.WARN, "WARN"),
    ERROR(Log.ERROR, "ERROR"),
}

enum class PluginLogSource(val token: String) {
    Host("host"),
    PluginJs("plugin-js"),
}

data class PluginLogEntry(
    val sequence: Long,
    val timestampMs: Long,
    val level: PluginLogLevel,
    val event: String,
    val source: PluginLogSource,
    val traceId: String?,
    val pluginId: String?,
    val sessionId: String?,
    val fields: Map<String, String>,
    val errorStack: String?,
)
