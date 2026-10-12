package com.x500x.cursimple.feature.plugin

import org.junit.Assert.*
import org.junit.Test

class PluginWebSessionResetTest {
    @Test fun `reset covers root path cookies and custom portal ports`() {
        val scopes = mergeCookieScopes(emptySet(), listOf("https://login.example.edu:4443/passport/login", "https://courses.example.edu/eams/courseTable.action"))
        val urls = cookieScopeUrls(listOf("login.example.edu", "courses.example.edu"), scopes)
        assertTrue("https://login.example.edu:4443/" in urls)
        assertTrue("https://login.example.edu:4443/passport/" in urls)
        assertTrue("https://courses.example.edu/eams/" in urls)
        assertFalse(urls.any { "other.example.edu" in it })
    }

    @Test fun `expiration reaches path with and without trailing slash and parent domain`() {
        val headers = expiredCookieHeaders("SESSION", "courses.example.edu", "/eams/")
        assertTrue(headers.any { it == "SESSION=; Max-Age=0; Path=/eams" })
        assertTrue(headers.any { it == "SESSION=; Max-Age=0; Path=/eams/; Domain=.example.edu" })
        assertFalse(headers.any { it.endsWith("Domain=.edu") })
        val kept = expiredCookieHeaders("SESSION", "courses.example.edu", "/", setOf("login.example.edu"))
        assertFalse(kept.any { it.endsWith("Domain=.example.edu") })
    }

    @Test fun `foreign logout origins and malformed scopes are rejected`() {
        assertEquals(listOf("https://courses.example.edu/logout"), sanitizeLogoutUrls(
            listOf("https://courses.example.edu/logout", "https://evil.example/logout", "javascript:alert(1)"), listOf("courses.example.edu")))
        assertNull(cookieScopeOf("file:///tmp/page"))
        assertFalse(cookieScopeUrls(listOf("courses.example.edu"), listOf("https://evil.example/path/"))
            .any { "evil.example" in it })
    }

    @Test fun `account markers detect changes without keeping account names`() {
        assertEquals(accountMarker(" user "), accountMarker("user"))
        assertNotEquals(accountMarker("user"), accountMarker("other"))
        assertNotEquals(accountMarker("user"), accountMarker("USER"))
        assertEquals(64, accountMarker("user").length)
    }
}
