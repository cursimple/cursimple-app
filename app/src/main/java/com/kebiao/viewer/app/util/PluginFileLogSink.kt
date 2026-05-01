package com.kebiao.viewer.app.util

import android.content.Context
import android.util.Log
import com.kebiao.viewer.core.plugin.logging.PluginLogSink
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PluginFileLogSink(
    private val file: File,
) : PluginLogSink {

    constructor(context: Context) : this(pluginDiagnosticsFile(context))

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
        file.writeText("# Plugin diagnostics log trimmed to recent entries\n$trimmed")
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
        const val LOG_DIR_NAME = "plugin-logs"
        const val LOG_FILE_NAME = "plugin-diagnostics.log"

        fun pluginDiagnosticsFile(context: Context): File {
            return pluginDiagnosticsFile(context.cacheDir)
        }

        fun pluginDiagnosticsFile(cacheDir: File): File {
            return File(File(cacheDir, LOG_DIR_NAME), LOG_FILE_NAME)
        }
    }
}
