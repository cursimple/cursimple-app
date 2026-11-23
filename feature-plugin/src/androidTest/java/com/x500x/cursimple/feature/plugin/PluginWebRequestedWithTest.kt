package com.x500x.cursimple.feature.plugin

import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Test

/**
 * Exercise the actual document proxy, rather than an obsolete allow-list API that does nothing.
 */
class PluginWebRequestedWithTest {
    @Test fun academicDocumentProxyRemovesPackageHeaderAndReturnsACompleteDocument() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var connection: FixtureConnection? = null
        val compat = LegacySiteCompat(context, listOf("portal.fixture.invalid"), { "Fixture/1" }) { url ->
            FixtureConnection(url).also { connection = it }
        }
        val request = object : WebResourceRequest {
            override fun getUrl() = Uri.parse("https://portal.fixture.invalid/home")
            override fun isForMainFrame() = true
            override fun isRedirect() = false
            override fun hasGesture() = true
            override fun getMethod() = "GET"
            override fun getRequestHeaders() = mapOf(
                "Accept" to "text/html,application/xhtml+xml",
                "X-Requested-With" to context.packageName,
            )
        }
        val response = compat.interceptDocument(request)
        assertNotNull(response)
        assertFalse(connection!!.sent.keys.any { it.equals("X-Requested-With", true) })
        assertEquals(200, response!!.statusCode)
        assertTrue(response.data.bufferedReader().use { it.readText() }.contains("<head>"))
        assertTrue(connection!!.disconnected)
    }

    private class FixtureConnection(url: URL) : HttpURLConnection(url) {
        val sent = mutableMapOf<String, String>()
        var disconnected = false
        override fun setRequestProperty(key: String, value: String) { sent[key] = value }
        override fun getResponseCode() = HTTP_OK
        override fun getHeaderFields(): Map<String, List<String>> = emptyMap()
        override fun getHeaderField(name: String?): String? = null
        override fun getContentType() = "text/html; charset=utf-8"
        override fun getContentLengthLong() = -1L
        override fun getResponseMessage() = "OK"
        override fun getInputStream() = ByteArrayInputStream(
            (if (sent.keys.any { it.equals("X-Requested-With", true) }) "<div>fragment</div>"
            else "<html><head><title>Academic system</title></head><body>Complete document</body></html>").toByteArray(),
        )
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun connect() {}
    }
}
