package com.kebiao.viewer.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.nio.file.Files

class LogExporterTest {
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
        val pluginLog = PluginFileLogSink.pluginDiagnosticsFile(cacheDir)
        exportedLog.parentFile?.mkdirs()
        pluginLog.parentFile?.mkdirs()
        exportedLog.writeText("logcat")
        pluginLog.writeText("plugin")

        assertTrue(LogExporter.clearLogFiles(cacheDir))

        assertFalse(exportedLog.exists())
        assertFalse(pluginLog.exists())
    }
}
