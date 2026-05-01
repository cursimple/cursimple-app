package com.kebiao.viewer.feature.plugin

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
        assertTrue(shouldSurfaceConsoleError("Uncaught TypeError: cannot read properties of null"))
    }
}
