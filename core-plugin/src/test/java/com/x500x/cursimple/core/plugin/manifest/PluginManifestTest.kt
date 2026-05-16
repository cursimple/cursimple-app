package com.x500x.cursimple.core.plugin.manifest

import com.x500x.cursimple.core.plugin.buildWebSessionRequest
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PluginManifestTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `manifest decodes new defaults and string permissions`() {
        val manifest = json.decodeFromString<PluginManifest>(
            """
            {
              "id": "edu.demo",
              "name": "Demo",
              "version": "1.0.0",
              "versionCode": 1,
              "entry": "main.js",
              "userAgent": "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36",
              "permissions": ["web.navigate", "web.read_dom", "schedule.write"]
            }
            """.trimIndent(),
        )

        assertEquals("edu.demo", manifest.id)
        assertEquals("main.js", manifest.entry)
        assertEquals(PluginWebEngineRequirement.ENGINE_SYSTEM_WEBVIEW, manifest.webEngine.preferred)
        assertEquals(
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36",
            manifest.userAgent,
        )
        assertFalse(manifest.webEngine.allowChromium)
        assertEquals(60_000, manifest.limits.timeoutMs)
        assertEquals(1_000, manifest.limits.maxCourses)
        assertEquals(
            listOf(
                PluginPermission.WebNavigate,
                PluginPermission.WebReadDom,
                PluginPermission.ScheduleWrite,
            ),
            manifest.permissions,
        )
    }

    @Test
    fun `legacy getters map to new manifest fields`() {
        val manifest = PluginManifest(
            id = "edu.demo",
            name = "Demo",
            version = "1.0.0",
            versionCode = 1,
            entry = "main.js",
            permissions = listOf(PluginPermission.NetworkFetch),
        )

        assertEquals(manifest.id, manifest.pluginId)
        assertEquals(manifest.apiVersion, manifest.targetApiVersion)
        assertEquals(manifest.permissions, manifest.declaredPermissions)
    }

    @Test
    fun `manifest decodes scoped network capture specs`() {
        val manifest = json.decodeFromString<PluginManifest>(
            """
            {
              "id": "edu.demo",
              "name": "Demo",
              "version": "1.0.0",
              "versionCode": 1,
              "entry": "main.js",
              "permissions": ["web.capture_packet"],
              "networkCaptures": [
                {
                  "id": "course-json",
                  "required": true,
                  "method": "POST",
                  "urlHost": "jw.school.edu.cn",
                  "urlPathContains": "/api/course",
                  "requestHeaders": ["content-type"],
                  "responseHeaders": ["content-type"],
                  "captureResponseBody": true,
                  "responseBodyMimeTypes": ["application/json"],
                  "maxBodyBytes": 4096
                }
              ]
            }
            """.trimIndent(),
        )

        val capture = manifest.networkCaptures.single()

        assertEquals(PluginPermission.WebCapturePacket, manifest.permissions.single())
        assertEquals("course-json", capture.id)
        assertEquals(true, capture.required)
        assertEquals("POST", capture.method)
        assertEquals(true, capture.captureResponseBody)
        assertEquals(4096, capture.maxBodyBytes)
    }

    @Test
    fun `manifest user agent flows into web session request`() {
        val userAgent = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
        val manifest = PluginManifest(
            id = "yangtzeu-eams-js-v1",
            name = "长江大学教务插件 JS v1",
            version = "1.0.1",
            versionCode = 1001,
            entry = "main.js",
            permissions = listOf(
                PluginPermission.WebNavigate,
                PluginPermission.NetworkFetch,
                PluginPermission.ScheduleWrite,
            ),
            allowedHosts = listOf("atrust.yangtzeu.edu.cn"),
            userAgent = userAgent,
        )
        val record = InstalledPluginRecord(
            pluginId = manifest.id,
            name = manifest.name,
            version = manifest.version,
            versionCode = manifest.versionCode,
            apiVersion = manifest.apiVersion,
            entry = manifest.entry,
            storagePath = "/tmp/plugin",
            installedAt = "2026-05-16T00:00:00+08:00",
            source = PluginInstallSource.Local,
            permissions = manifest.permissions,
            allowedHosts = manifest.allowedHosts,
            webEngine = manifest.webEngine,
            components = manifest.components,
        )

        val request = buildWebSessionRequest(
            token = "token",
            record = record,
            sessionId = "session",
            startUrl = "https://atrust.yangtzeu.edu.cn/",
            termId = "2026-spring",
            entryScript = "export async function run(ctx) { return ctx.schedule.commit({ courses: [] }); }",
            manifest = manifest,
        )

        assertEquals(userAgent, request.userAgent)
        assertEquals(manifest.allowedHosts, request.allowedHosts)
        assertFalse(request.extractCookies)
    }
}
