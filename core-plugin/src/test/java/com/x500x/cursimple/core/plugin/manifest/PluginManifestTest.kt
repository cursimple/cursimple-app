package com.x500x.cursimple.core.plugin.manifest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlinx.serialization.json.Json

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
              "permissions": ["web.navigate", "web.read_dom", "schedule.write"]
            }
            """.trimIndent(),
        )

        assertEquals("edu.demo", manifest.id)
        assertEquals("main.js", manifest.entry)
        assertEquals(PluginWebEngineRequirement.ENGINE_SYSTEM_WEBVIEW, manifest.webEngine.preferred)
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
}
