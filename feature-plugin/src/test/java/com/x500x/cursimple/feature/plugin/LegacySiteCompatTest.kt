package com.x500x.cursimple.feature.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 老站兼容层里不依赖 Android 的那部分规则。
 *
 * 背景：aTrust 这类 VPN 代理的自举壳页靠 document.domain 跨子域握手，新版 Chromium 在
 * HTTPS 下默认按源隔离把它拦掉；响应头补上 Origin-Agent-Cluster: ?0 才能放行。
 * 本机 Chrome 153 实测：不带头 → SecurityError，带 ?0 → 正常互访。
 */
class LegacySiteCompatTest {

    private val navigationAccept =
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"

    @Test
    fun `只代发页面导航，脚本请求原样放行`() {
        assertTrue("主框架 GET 一律算导航", isDocumentNavigation("GET", isMainFrame = true, accept = null))
        assertTrue("iframe 导航靠 Accept 认出来", isDocumentNavigation("GET", isMainFrame = false, accept = navigationAccept))
        assertFalse(
            "jQuery 要 HTML 的 XHR 不含 xhtml，不能当导航代发",
            isDocumentNavigation("GET", isMainFrame = false, accept = "text/html, */*; q=0.01"),
        )
        assertFalse("POST 拿不到请求体，绝不代发", isDocumentNavigation("POST", isMainFrame = true, accept = navigationAccept))
    }

    @Test
    fun `代发时补上 Cookie，去掉会引出 304 和压缩的头`() {
        val headers = forwardRequestHeaders(
            requestHeaders = mapOf(
                "Accept" to navigationAccept,
                "If-None-Match" to "\"abc\"",
                "If-Modified-Since" to "Tue, 22 Sep 2026 00:00:00 GMT",
                "Accept-Encoding" to "gzip, deflate, br",
                "X-Requested-With" to "com.x500x.cursimple.ci",
                "Referer" to "https://atrust.example.edu.cn/",
            ),
            cookie = "sdp_user_token=abc; sid=1",
            fallbackUserAgent = "UA/1.0",
        )

        assertEquals("sdp_user_token=abc; sid=1", headers["Cookie"])
        assertEquals("UA/1.0", headers["User-Agent"])
        assertEquals("https://atrust.example.edu.cn/", headers["Referer"])
        assertFalse("304 没有正文交不回 WebView", headers.containsKey("If-None-Match"))
        assertFalse(headers.containsKey("If-Modified-Since"))
        assertFalse("交给 HttpURLConnection 自己解压", headers.containsKey("Accept-Encoding"))
        assertFalse("WebView 塞的包名版 X-Requested-With 要去掉", headers.containsKey("X-Requested-With"))
    }

    @Test
    fun `页面自己带的 UA 优先于兜底 UA`() {
        val headers = forwardRequestHeaders(mapOf("User-Agent" to "Page/2.0"), cookie = null, fallbackUserAgent = "UA/1.0")
        assertEquals("Page/2.0", headers["User-Agent"])
        assertFalse(headers.containsKey("Cookie"))
    }

    @Test
    fun `交回的响应头补上 Origin-Agent-Cluster 并去掉已处理的项`() {
        val headers = legacyDocumentResponseHeaders(
            mapOf(
                null to listOf("HTTP/1.1 200 OK"),
                "Content-Type" to listOf("text/html; charset=GBK"),
                "Content-Encoding" to listOf("gzip"),
                "Content-Length" to listOf("6612"),
                "Set-Cookie" to listOf("a=1", "b=2"),
                "Origin-Agent-Cluster" to listOf("?1"),
                "X-Frame-Options" to listOf("SAMEORIGIN"),
            ),
        )

        assertEquals("?0", headers["Origin-Agent-Cluster"])
        assertEquals("text/html; charset=GBK", headers["Content-Type"])
        assertEquals("SAMEORIGIN", headers["X-Frame-Options"])
        assertFalse("正文已解压，编码头会误导解析", headers.containsKey("Content-Encoding"))
        assertFalse(headers.containsKey("Content-Length"))
        assertFalse("Cookie 已存进 CookieManager，不重复交回", headers.containsKey("Set-Cookie"))
        assertEquals("服务器原来的 ?1 要被覆盖掉，不能出现两份", 1, headers.keys.count { it.equals("Origin-Agent-Cluster", true) })
    }

    @Test
    fun `Content-Type 拆出类型与字符集，没写字符集交给页面 meta`() {
        assertEquals("text/html" to "GBK", parseContentType("text/html; charset=GBK"))
        assertEquals("text/html" to "utf-8", parseContentType("TEXT/HTML;charset=\"utf-8\""))
        assertEquals("text/html" to null, parseContentType("text/html"))
        assertEquals(null to null, parseContentType(null))
    }

    @Test
    fun `重定向替身页只跳一次，目标地址正确转义`() {
        val html = redirectStubHtml("https://cas.example.edu.cn/login?service=a\"b</script>")
        assertTrue(html.contains("location.replace("))
        assertEquals("地址里的 </script> 不能截断脚本，全文只能有收尾那一个", 1, Regex("</script>").findAll(html).count())
        assertTrue(html.contains("\\u003c/script>"))
    }

    @Test
    fun `起始脚本只在白名单域名上注入 jQuery`() {
        val script = legacyDocumentStartScript(
            allowedHosts = listOf("atrust.example.edu.cn", " "),
            jquerySource = "window.jQuery=window.\$=function(){};",
            migrateSource = "/*migrate*/",
        )
        assertTrue(script.contains("[\"atrust.example.edu.cn\"]"))
        assertTrue("已有 jQuery 就不再注入", script.contains("window.jQuery) { return; }"))
        assertTrue(script.contains("migrateMute = true"))
        assertNull("空白域名要被过滤掉", Regex("\"\\s+\"").find(script))
    }
}
