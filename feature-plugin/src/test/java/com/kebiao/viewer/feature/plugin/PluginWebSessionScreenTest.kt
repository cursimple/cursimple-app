package com.kebiao.viewer.feature.plugin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginWebSessionScreenTest {
    @Test
    fun `allowed host accepts exact host and subdomain`() {
        val allowed = listOf("example.edu.cn")

        assertTrue(isAllowedHost("https://example.edu.cn/login", allowed))
        assertTrue(isAllowedHost("https://cas.example.edu.cn/login", allowed))
        assertFalse(isAllowedHost("https://evil-example.edu.cn/login", allowed))
    }

    @Test
    fun `javascript payload decoding tolerates blank or malformed values`() {
        decodeJavascriptPayload(null)
        decodeJavascriptPayload("not-json")
    }
}
