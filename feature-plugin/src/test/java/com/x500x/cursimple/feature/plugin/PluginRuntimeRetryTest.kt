package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.core.plugin.manifest.PluginPermission
import com.x500x.cursimple.core.plugin.web.WebSessionRequest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class PluginRuntimeRetryTest {
    @Test fun generatedRuntimeRetriesWaitingPagesWithoutDuplicatingAnActiveImport() {
        val folder = File("build/runtime-retry-test").apply { mkdirs() }
        val request = WebSessionRequest(token = "test", pluginId = "test-importer", sessionId = "test-session", title = "Import",
            startUrl = "https://courses.example/eams", allowedHosts = listOf("courses.example"),
            permissions = listOf(PluginPermission.ScheduleWrite), retryIncompleteAfterMs = 2_000,
            entryScript = """export async function run(ctx) {
                window.calls = (window.calls || 0) + 1;
                if (window.hold) await new Promise(resolve => window.release = resolve);
                if (!window.ready) return {status:'waiting'};
                if (window.fail) throw new Error('test failure');
                ctx.schedule.addCourse({id:'course',title:'Test',dayOfWeek:1,startNode:1,endNode:1,weeks:[1]});
                ctx.schedule.commit({termId:'test-term'});
            }""")
        val normal = File(folder, "normal.js").apply { writeText(buildPluginRuntimeScript(request, request.startUrl)) }
        val forced = File(folder, "forced.js").apply { writeText(buildPluginRuntimeScript(request, request.startUrl, force = true)) }
        val legacy = File(folder, "legacy.js").apply { writeText(buildPluginRuntimeScript(request.copy(retryIncompleteAfterMs = null), request.startUrl)) }
        val runner = File("src/test/resources/plugin-runtime-retry.mjs")
        val process = ProcessBuilder("node", runner.absolutePath, normal.absolutePath, forced.absolutePath, legacy.absolutePath)
            .redirectErrorStream(true).start()
        val completed = process.waitFor(20, TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly()
        assertTrue("Runtime retry test timed out", completed)
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.exitValue())
    }

    @Test fun pluginHoldStopsAutomaticRetriesUntilTheUserUploads() {
        val folder = File("build/runtime-hold-test").apply { mkdirs() }
        val request = WebSessionRequest(token = "test", pluginId = "test-importer", sessionId = "test-session", title = "Import",
            startUrl = "https://courses.example/eams", allowedHosts = listOf("courses.example"),
            permissions = listOf(PluginPermission.ScheduleWrite), retryIncompleteAfterMs = 2_000,
            entryScript = """export async function run(ctx) {
                window.calls = (window.calls || 0) + 1;
                if (ctx.manual) window.manualSeen = true;
                if (!ctx.manual) return {status:'manual-required', hold:true};
                ctx.schedule.addCourse({id:'course',title:'Test',dayOfWeek:1,startNode:1,endNode:1,weeks:[1]});
                ctx.schedule.commit({termId:'test-term'});
            }""")
        val hold = File(folder, "hold.js").apply { writeText(buildPluginRuntimeScript(request, request.startUrl)) }
        val forced = File(folder, "forced.js").apply { writeText(buildPluginRuntimeScript(request, request.startUrl, force = true)) }
        val runner = File("src/test/resources/plugin-runtime-hold.mjs")
        val process = ProcessBuilder("node", runner.absolutePath, hold.absolutePath, forced.absolutePath)
            .redirectErrorStream(true).start()
        val completed = process.waitFor(20, TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly()
        assertTrue("Runtime hold test timed out", completed)
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.exitValue())
    }
}
