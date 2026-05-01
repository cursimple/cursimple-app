package com.kebiao.viewer.core.plugin.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginLoggerTest {
    @Test
    fun `sanitizeUrl redacts sensitive query values`() {
        val sanitized = PluginLogger.sanitizeUrl(
            "https://example.edu/login?ticket=abc&username=20260001&token=secret&password=pwd",
        )

        assertTrue(sanitized.contains("ticket=***"))
        assertTrue(sanitized.contains("username=20260001"))
        assertTrue(sanitized.contains("token=***"))
        assertTrue(sanitized.contains("password=***"))
        assertFalse(sanitized.contains("secret"))
        assertFalse(sanitized.contains("pwd"))
    }

    @Test
    fun `sanitizeUrl handles invalid url without throwing`() {
        val sanitized = PluginLogger.sanitizeUrl("not a url with token=secret")

        assertTrue(sanitized.contains("not a url"))
        assertFalse(sanitized.contains("secret"))
    }

    @Test
    fun `sha256 is stable`() {
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            PluginLogger.sha256("hello"),
        )
    }

    @Test
    fun `fields render in stable order and omit nulls`() {
        val rendered = PluginLogger.renderFieldsForTest(
            mapOf(
                "zeta" to "last",
                "empty" to null,
                "alpha" to "first value",
            ),
        )

        assertEquals("alpha=first value zeta=last", rendered)
    }

    @Test
    fun `fields redact sensitive keys and inline values`() {
        val rendered = PluginLogger.renderFieldsForTest(
            mapOf(
                "token" to "secret-token",
                "failureMessage" to "failed with password: hunter2",
            ),
        )

        assertTrue(rendered.contains("token=***"))
        assertTrue(rendered.contains("password: ***"))
        assertFalse(rendered.contains("secret-token"))
        assertFalse(rendered.contains("hunter2"))
    }
}
