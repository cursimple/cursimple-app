package com.kebiao.viewer.app.util

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

object LogExporter {

    /**
     * 抓取当前进程的 logcat 写入 cache/logs/ 下，返回该文件的 content:// URI 的分享 Intent。
     * 失败返回 null。
     */
    suspend fun exportRecentLogs(context: Context, maxLines: Int = 2000): Intent? = withContext(Dispatchers.IO) {
        val file = collectLogs(context, maxLines) ?: return@withContext null
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "ClassScheduleViewer 日志 ${file.name}")
            // Make the content URI visible to Android's grant machinery, including the sharesheet.
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun clearLogs(context: Context): Boolean = withContext(Dispatchers.IO) {
        clearLogFiles(context.cacheDir)
    }

    private fun collectLogs(context: Context, maxLines: Int): File? = runCatching {
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val target = File(dir, "app-log-$timestamp.txt")

        val pid = Process.myPid().toString()
        val process = ProcessBuilder(
            "logcat",
            "-d",
            "-t", maxLines.toString(),
            "--pid=$pid",
            "-v", "time",
        ).redirectErrorStream(true).start()

        target.bufferedWriter().use { writer ->
            writer.appendLine("# ClassScheduleViewer log dump")
            writer.appendLine("# pid=$pid time=$timestamp")
            writer.appendLine("# device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            writer.appendLine("# android=${android.os.Build.VERSION.RELEASE} (sdk=${android.os.Build.VERSION.SDK_INT})")
            writer.appendLine("# includes plugin diagnostics with sensitive values redacted")
            writer.appendLine("# ----")
            writer.appendLine("# Logcat")
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { writer.appendLine(it) }
            }
            appendPluginDiagnostics(writer, PluginFileLogSink.pluginDiagnosticsFile(context))
        }
        process.waitFor()
        target
    }.getOrNull()

    internal fun appendPluginDiagnostics(writer: Writer, pluginLogFile: File) {
        writer.appendLine()
        writer.appendLine("# ----")
        writer.appendLine("# Plugin diagnostics")
        if (!pluginLogFile.isFile || pluginLogFile.length() == 0L) {
            writer.appendLine("# No app-owned plugin diagnostics log is available.")
            return
        }
        pluginLogFile.bufferedReader().useLines { lines ->
            lines.forEach { writer.appendLine(it) }
        }
    }

    internal fun clearLogFiles(cacheDir: File): Boolean = runCatching {
        val exportedLogsDir = File(cacheDir, "logs")
        val pluginLogsDir = File(cacheDir, PluginFileLogSink.LOG_DIR_NAME)
        val exportedLogsCleared = !exportedLogsDir.exists() || exportedLogsDir.deleteRecursively()
        val pluginLogsCleared = !pluginLogsDir.exists() || pluginLogsDir.deleteRecursively()
        exportedLogsCleared && pluginLogsCleared
    }.getOrDefault(false)
}
