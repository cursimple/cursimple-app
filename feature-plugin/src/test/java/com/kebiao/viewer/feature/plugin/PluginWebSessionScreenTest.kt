package com.kebiao.viewer.feature.plugin

import com.kebiao.viewer.core.plugin.web.WebCapturedPacket
import com.kebiao.viewer.core.plugin.web.WebSessionCaptureSpec
import com.kebiao.viewer.core.plugin.web.WebSessionPacket
import com.kebiao.viewer.core.plugin.web.WebSessionRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginWebSessionScreenTest {
    @Test
    fun `allowed host accepts exact host and subdomain`() {
        val allowed = listOf("example.edu.cn")

        assertTrue(isAllowedHost("https://example.edu.cn/login", allowed))
        assertTrue(isAllowedHost("https://cas.example.edu.cn/login", allowed))
        assertTrue(isAllowedHost("https://cas.example.edu.cn:8443/login", allowed))
        assertFalse(isAllowedHost("https://evil-example.edu.cn/login", allowed))
    }

    @Test
    fun `allowed host accepts atrust proxied subdomains`() {
        val allowed = listOf("atrust.yangtzeu.edu.cn")

        assertTrue(isAllowedHost("https://cas-yangtzeu-edu-cn.atrust.yangtzeu.edu.cn/authserver/login", allowed))
        assertTrue(isAllowedHost("https://ehall-yangtzeu-edu-cn-s.atrust.yangtzeu.edu.cn/portal", allowed))
        assertFalse(isAllowedHost("https://fake-atrust-yangtzeu.edu.cn/login", allowed))
    }

    @Test
    fun `javascript payload decoding tolerates blank or malformed values`() {
        assertEquals("{}", normalizeJavascriptPayload(null))
        assertEquals("{}", normalizeJavascriptPayload("not-json"))
    }

    @Test
    fun `javascript payload decoding unwraps webview json strings`() {
        val payload = normalizeJavascriptPayload("\"{\\\"html\\\":\\\"<main>ok</main>\\\"}\"")

        assertEquals("{\"html\":\"<main>ok</main>\"}", payload)
    }

    @Test
    fun `console error filter ignores known eams beangle noise`() {
        assertFalse(shouldSurfaceConsoleError("Uncaught ReferenceError: beangle is not defined"))
        assertFalse(shouldSurfaceConsoleError("Uncaught ReferenceError: jQuery is not defined"))
        assertTrue(shouldSurfaceConsoleError("Uncaught TypeError: cannot read properties of null"))
    }

    @Test
    fun `capture spec does not treat atrust query appUrl as target host`() {
        val request = webRequest(
            capturePackets = listOf(
                WebSessionCaptureSpec(
                    id = "eams-course-home",
                    urlHost = "jwc3-yangtzeu-edu-cn-s.atrust.yangtzeu.edu.cn",
                    urlPathContains = "/eams/courseTableForStd.action",
                    captureSelectors = listOf("title"),
                    minCookieCount = 1,
                ),
            ),
        )
        val packet = webPacket(
            finalUrl = "https://atrust.yangtzeu.edu.cn:4443/portal/shortcut.html?appUrl=https%253A%252F%252Fjwc3-yangtzeu-edu-cn-s.atrust.yangtzeu.edu.cn%253A443%252Feams%252FcourseTableForStd.action",
            cookies = mapOf("JSESSIONID" to "cookie"),
            capturedFields = mapOf("title" to "登录"),
        )

        assertTrue(readyCaptureSpecs(request, packet).isEmpty())
    }

    @Test
    fun `auto navigation targets configured allowed follow up once`() {
        val target = "https://jwc3-yangtzeu-edu-cn-s.atrust.yangtzeu.edu.cn/eams/courseTableForStd.action"
        val request = webRequest(
            autoNavigateOnUrlContains = "/eams/home.action",
            autoNavigateToUrl = target,
        )

        assertEquals(
            target,
            autoNavigateTargetForRequest(
                request,
                "https://jwc3-yangtzeu-edu-cn-s.atrust.yangtzeu.edu.cn/eams/home.action",
                emptySet(),
            ),
        )
        assertEquals(
            null,
            autoNavigateTargetForRequest(
                request,
                "https://jwc3-yangtzeu-edu-cn-s.atrust.yangtzeu.edu.cn/eams/home.action",
                setOf(target),
            ),
        )
        assertEquals(
            null,
            autoNavigateTargetForRequest(
                request,
                "https://atrust.yangtzeu.edu.cn/portal/shortcut.html?appUrl=https%3A%2F%2Fjwc3-yangtzeu-edu-cn-s.atrust.yangtzeu.edu.cn%2Feams%2Fhome.action",
                emptySet(),
            ),
        )
        assertEquals(
            null,
            autoNavigateTargetForRequest(
                request,
                "https://jwc3-yangtzeu-edu-cn-s.atrust.yangtzeu.edu.cn/eams/courseTableForStd.action",
                emptySet(),
            ),
        )
    }

    @Test
    fun `capture selectors drop oversize values before script generation`() {
        val overlongSelector = "a".repeat(3000)
        val request = webRequest(
            capturePackets = listOf(
                WebSessionCaptureSpec(
                    id = "login",
                    captureSelectors = listOf("title", overlongSelector),
                ),
            ),
        )

        assertEquals(listOf("title"), captureSelectorsForRequest(request))
    }

    @Test
    fun `capture selector script ignores oversize values before escaping`() {
        val overlongSelector = "\\".repeat(3000)

        val script = listOf("title", overlongSelector).toCaptureSelectorArrayScript()

        assertEquals("[\"title\"]", script)
    }

    @Test
    fun `capture selectors cap runaway selector lists`() {
        val selectors = (0 until 80).map { "meta[name='field$it']" }
        val request = webRequest(
            capturePackets = listOf(
                WebSessionCaptureSpec(
                    id = "login",
                    captureSelectors = selectors,
                ),
            ),
        )

        assertEquals(64, captureSelectorsForRequest(request).size)
    }

    @Test
    fun `required capture packets must all be present before web session completes`() {
        val request = webRequest(
            capturePackets = listOf(
                WebSessionCaptureSpec(id = "login", urlHost = "atrust.yangtzeu.edu.cn"),
                WebSessionCaptureSpec(id = "eams", urlHost = "jwc3-yangtzeu-edu-cn-s.atrust.yangtzeu.edu.cn"),
            ),
        )
        val loginPacket = WebCapturedPacket(
            id = "login",
            finalUrl = "https://atrust.yangtzeu.edu.cn/portal",
            timestamp = "2026-05-02T12:00:00+08:00",
        )
        val eamsPacket = WebCapturedPacket(
            id = "eams",
            finalUrl = "https://jwc3-yangtzeu-edu-cn-s.atrust.yangtzeu.edu.cn/eams/courseTableForStd.action",
            timestamp = "2026-05-02T12:00:01+08:00",
        )

        assertFalse(hasAllRequiredCapturePackets(request, mapOf("login" to loginPacket)))
        assertTrue(hasAllRequiredCapturePackets(request, mapOf("login" to loginPacket, "eams" to eamsPacket)))
    }

    @Test
    fun `oversized required selector does not satisfy capture readiness`() {
        val overlongSelector = "a".repeat(3000)
        val spec = WebSessionCaptureSpec(
            id = "login",
            captureSelectors = listOf(overlongSelector),
        )
        val packet = webPacket(
            finalUrl = "https://example.edu.cn/login",
            capturedFields = mapOf(overlongSelector to "ok"),
        )

        assertFalse(isCaptureSpecReady(spec, packet))
    }

    @Test
    fun `aggregate web session packet preserves multiple captured packets`() {
        val request = webRequest(
            capturePackets = listOf(
                WebSessionCaptureSpec(id = "login"),
                WebSessionCaptureSpec(id = "eams"),
            ),
        )
        val latest = webPacket(
            finalUrl = "https://jwc3-yangtzeu-edu-cn-s.atrust.yangtzeu.edu.cn/eams/courseTableForStd.action",
            cookies = mapOf("latest" to "1"),
        )
        val login = webPacket(
            finalUrl = "https://atrust.yangtzeu.edu.cn/portal",
            cookies = mapOf("atrust" to "1"),
            capturedFields = mapOf("title" to "认证"),
        ).toCapturedPacket("login")
        val eams = webPacket(
            finalUrl = "https://jwc3-yangtzeu-edu-cn-s.atrust.yangtzeu.edu.cn/eams/courseTableForStd.action",
            cookies = mapOf("JSESSIONID" to "ok"),
            capturedFields = mapOf("title" to "课表"),
        ).toCapturedPacket("eams")

        val aggregate = aggregateWebSessionPacket(request, latest, mapOf("login" to login, "eams" to eams))

        assertEquals(setOf("login", "eams"), aggregate.capturedPackets.keys)
        assertEquals("ok", aggregate.cookies["JSESSIONID"])
        assertEquals("课表", aggregate.capturedFields["eams.title"])
    }

    private fun webRequest(
        capturePackets: List<WebSessionCaptureSpec> = emptyList(),
        autoNavigateOnUrlContains: String? = null,
        autoNavigateToUrl: String? = null,
    ): WebSessionRequest {
        return WebSessionRequest(
            token = "token",
            pluginId = "yangtzeu-eams-v2",
            sessionId = "login",
            title = "登录",
            startUrl = "https://atrust.yangtzeu.edu.cn/portal",
            allowedHosts = listOf("atrust.yangtzeu.edu.cn", "jwc3-yangtzeu-edu-cn-s.atrust.yangtzeu.edu.cn"),
            capturePackets = capturePackets,
            autoNavigateOnUrlContains = autoNavigateOnUrlContains,
            autoNavigateToUrl = autoNavigateToUrl,
        )
    }

    private fun webPacket(
        finalUrl: String,
        cookies: Map<String, String> = emptyMap(),
        capturedFields: Map<String, String> = emptyMap(),
    ): WebSessionPacket {
        return WebSessionPacket(
            finalUrl = finalUrl,
            cookies = cookies,
            capturedFields = capturedFields,
            timestamp = "2026-05-02T12:00:00+08:00",
        )
    }
}
