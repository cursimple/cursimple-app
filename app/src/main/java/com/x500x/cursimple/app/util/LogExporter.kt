package com.x500x.cursimple.app.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object LogExporter {

    /**
     * 抓取当前进程的完整 logcat 写入 cache/logs/ 下，返回该文件的 content:// URI 的分享 Intent。
     * 失败返回 null。
     */
    suspend fun exportRecentLogs(context: Context): Intent? = withContext(Dispatchers.IO) {
        val file = collectLogs(context) ?: return@withContext null
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "CurSimple 日志 ${file.name}")
            // Make the content URI visible to Android's grant machinery, including the sharesheet.
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun clearLogs(context: Context): Boolean = withContext(Dispatchers.IO) {
        AppDiagnosticsLogger.info("log.clear.manual.start")
        clearLogFiles(context.cacheDir)
    }

    suspend fun clearExpiredLogs(context: Context): Boolean = withContext(Dispatchers.IO) {
        AppDiagnosticsLogger.info("log.cleanup.periodic.start")
        val cleared = cleanupExpiredLogFiles(context.cacheDir)
        AppDiagnosticsLogger.info("log.cleanup.periodic.finish", mapOf("succeeded" to cleared))
        cleared
    }

    private fun collectLogs(context: Context): File? = runCatching {
        AppDiagnosticsLogger.info("log.export.start")
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val target = File(dir, "app-log-$timestamp.txt")

        val pid = Process.myPid().toString()
        val process = ProcessBuilder(logcatCommand(pid)).redirectErrorStream(true).start()

        target.bufferedWriter().use { writer ->
            writer.appendLine("# CurSimple log dump")
            writer.appendLine("# pid=$pid time=$timestamp")
            writer.appendLine("# device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            writer.appendLine("# android=${android.os.Build.VERSION.RELEASE} (sdk=${android.os.Build.VERSION.SDK_INT})")
            writer.appendLine("# includes app and plugin diagnostics")
            appendAppDiagnostics(writer, AppDiagnosticsFileSink.appDiagnosticsFile(context))
            appendPluginDiagnostics(writer, PluginFileLogSink.pluginDiagnosticsFile(context))
            writer.appendLine()
            writer.appendLine("# ----")
            writer.appendLine("# Logcat")
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { writer.appendLine(it) }
            }
        }
        val exitCode = process.waitFor()
        AppDiagnosticsLogger.info("log.export.finish", mapOf("file" to target.name, "logcatExitCode" to exitCode))
        target
    }.onFailure {
        AppDiagnosticsLogger.error("log.export.failure", error = it)
    }.getOrNull()

    internal fun logcatCommand(pid: String): List<String> {
        return listOf(
            "logcat",
            "-d",
            "--pid=$pid",
            "-v",
            "time",
        )
    }

    internal fun appendAppDiagnostics(writer: Writer, appLogFile: File) {
        appendDiagnosticsSection(
            writer = writer,
            title = "App diagnostics",
            missingMessage = "No app-owned diagnostics log is available.",
            logFile = appLogFile,
        )
    }

    internal fun appendPluginDiagnostics(writer: Writer, pluginLogFile: File) {
        appendDiagnosticsSection(
            writer = writer,
            title = "Plugin diagnostics",
            missingMessage = "No app-owned plugin diagnostics log is available.",
            logFile = pluginLogFile,
        )
    }

    private fun appendDiagnosticsSection(
        writer: Writer,
        title: String,
        missingMessage: String,
        logFile: File,
    ) {
        writer.appendLine()
        writer.appendLine("# ----")
        writer.appendLine("# $title")
        if (!logFile.isFile || logFile.length() == 0L) {
            writer.appendLine("# $missingMessage")
            return
        }
        logFile.bufferedReader().useLines { lines ->
            lines.forEach { writer.appendLine(it) }
        }
    }

    internal fun clearLogFiles(cacheDir: File): Boolean = runCatching {
        val exportedLogsDir = File(cacheDir, "logs")
        val appLogsDir = File(cacheDir, AppDiagnosticsFileSink.LOG_DIR_NAME)
        val pluginLogsDir = File(cacheDir, PluginFileLogSink.LOG_DIR_NAME)
        val exportedLogsCleared = !exportedLogsDir.exists() || exportedLogsDir.deleteRecursively()
        val appLogsCleared = !appLogsDir.exists() || appLogsDir.deleteRecursively()
        val pluginLogsCleared = !pluginLogsDir.exists() || pluginLogsDir.deleteRecursively()
        exportedLogsCleared && appLogsCleared && pluginLogsCleared
    }.getOrDefault(false)

    internal fun cleanupExpiredLogFiles(
        cacheDir: File,
        nowMillis: Long = System.currentTimeMillis(),
        retentionMillis: Long = LOG_RETENTION_MILLIS,
    ): Boolean = runCatching {
        val targets = listOf(
            File(cacheDir, "logs"),
            File(cacheDir, AppDiagnosticsFileSink.LOG_DIR_NAME),
            File(cacheDir, PluginFileLogSink.LOG_DIR_NAME),
        )
        targets.forEach { target ->
            if (target.isDirectory) {
                target.walkBottomUp()
                    .filter { it.isFile && isExpired(it, nowMillis, retentionMillis) }
                    .forEach { it.delete() }
                target.walkBottomUp()
                    .filter { it.isDirectory && it.listFiles()?.isEmpty() == true }
                    .forEach { it.delete() }
            } else if (target.isFile && isExpired(target, nowMillis, retentionMillis)) {
                target.delete()
            }
        }
        true
    }.getOrDefault(false)

    private fun isExpired(file: File, nowMillis: Long, retentionMillis: Long): Boolean {
        return nowMillis - file.lastModified() >= retentionMillis
    }

    private val LOG_RETENTION_MILLIS = TimeUnit.DAYS.toMillis(3)
}
