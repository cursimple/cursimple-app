package com.x500x.cursimple.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class LogExporterTest {
    @Test
    fun `logcat command exports full current process log without line limit`() {
        val command = LogExporter.logcatCommand("1234")

        assertEquals(listOf("logcat", "-d", "--pid=1234", "-v", "time"), command)
        assertFalse(command.contains("-t"))
        assertFalse(command.contains("2000"))
    }

    @Test
    fun `appendAppDiagnostics includes app log contents`() {
        val dir = Files.createTempDirectory("app-log-export-test").toFile()
        val appLog = AppDiagnosticsFileSink.appDiagnosticsFile(dir)
        appLog.parentFile?.mkdirs()
        appLog.writeText("2026-05-05 I/AppDiagnostics: app.lifecycle.on_create\n")
        val writer = StringWriter()

        LogExporter.appendAppDiagnostics(writer, appLog)

        val exported = writer.toString()
        assertTrue(exported.contains("# App diagnostics"))
        assertTrue(exported.contains("app.lifecycle.on_create"))
    }

    @Test
    fun `appendPluginDiagnostics includes plugin log contents`() {
        val dir = Files.createTempDirectory("plugin-log-export-test").toFile()
        val pluginLog = PluginFileLogSink.pluginDiagnosticsFile(dir)
        pluginLog.parentFile?.mkdirs()
        pluginLog.writeText("2026-05-01 I/PluginDiagnostics: plugin.sync.start\n")
        val writer = StringWriter()

        LogExporter.appendPluginDiagnostics(writer, pluginLog)

        val exported = writer.toString()
        assertTrue(exported.contains("# Plugin diagnostics"))
        assertTrue(exported.contains("plugin.sync.start"))
    }

    @Test
    fun `appendPluginDiagnostics handles missing plugin log`() {
        val dir = Files.createTempDirectory("plugin-log-missing-test").toFile()
        val writer = StringWriter()

        LogExporter.appendPluginDiagnostics(writer, PluginFileLogSink.pluginDiagnosticsFile(dir))

        val exported = writer.toString()
        assertTrue(exported.contains("# Plugin diagnostics"))
        assertTrue(exported.contains("No app-owned plugin diagnostics log"))
    }

    @Test
    fun `clearLogFiles removes exported and plugin logs`() {
        val cacheDir = Files.createTempDirectory("plugin-log-clear-test").toFile()
        val exportedLog = cacheDir.resolve("logs/app-log.txt")
        val appLog = AppDiagnosticsFileSink.appDiagnosticsFile(cacheDir)
        val pluginLog = PluginFileLogSink.pluginDiagnosticsFile(cacheDir)
        exportedLog.parentFile?.mkdirs()
        appLog.parentFile?.mkdirs()
        pluginLog.parentFile?.mkdirs()
        exportedLog.writeText("logcat")
        appLog.writeText("app")
        pluginLog.writeText("plugin")

        assertTrue(LogExporter.clearLogFiles(cacheDir))

        assertFalse(exportedLog.exists())
        assertFalse(appLog.exists())
        assertFalse(pluginLog.exists())
    }

    @Test
    fun `cleanupExpiredLogFiles removes logs older than three days`() {
        val cacheDir = Files.createTempDirectory("log-expiry-test").toFile()
        val oldLog = cacheDir.resolve("logs/old.txt")
        val recentLog = cacheDir.resolve("logs/recent.txt")
        val oldPluginLog = PluginFileLogSink.pluginDiagnosticsFile(cacheDir)
        val now = 1_000_000_000L
        oldLog.parentFile?.mkdirs()
        oldPluginLog.parentFile?.mkdirs()
        oldLog.writeText("old")
        recentLog.writeText("recent")
        oldPluginLog.writeText("plugin")
        oldLog.setLastModified(now - TimeUnit.DAYS.toMillis(3))
        oldPluginLog.setLastModified(now - TimeUnit.DAYS.toMillis(4))
        recentLog.setLastModified(now - TimeUnit.DAYS.toMillis(2))

        assertTrue(LogExporter.cleanupExpiredLogFiles(cacheDir, nowMillis = now))

        assertFalse(oldLog.exists())
        assertFalse(oldPluginLog.exists())
        assertTrue(recentLog.exists())
    }
}
