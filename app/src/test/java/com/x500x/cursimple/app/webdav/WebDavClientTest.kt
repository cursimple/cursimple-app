package com.x500x.cursimple.app.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavClientTest {
    @Test
    fun `normalizes webdav directory url to https when scheme is omitted`() {
        assertEquals(
            "https://dav.example.com/backups/",
            normalizeSecureWebDavDirectoryUrl("dav.example.com/backups"),
        )
    }

    @Test
    fun `keeps https webdav directory url`() {
        assertEquals(
            "https://dav.example.com/backups/",
            normalizeSecureWebDavDirectoryUrl("https://dav.example.com/backups/"),
        )
    }

    @Test
    fun `rejects cleartext webdav url`() {
        val error = runCatching {
            normalizeSecureWebDavDirectoryUrl("http://dav.example.com/backups/")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("HTTPS"))
    }

    @Test
    fun `keeps backup url that stays on the configured webdav host`() {
        assertEquals(
            "https://dav.example.com/backups/cursimple/backups/a.json",
            requireSameWebDavOrigin(
                baseUrl = "https://dav.example.com/backups/",
                url = "https://dav.example.com/backups/cursimple/backups/a.json",
            ),
        )
    }

    @Test
    fun `rejects backup url pointing at another host`() {
        val error = runCatching {
            requireSameWebDavOrigin(
                baseUrl = "https://dav.example.com/backups/",
                url = "https://attacker.example.net/steal.json",
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("与配置的服务器不一致"))
    }

    @Test
    fun `rejects backup url pointing at another port on the same host`() {
        val error = runCatching {
            requireSameWebDavOrigin(
                baseUrl = "https://dav.example.com/backups/",
                url = "https://dav.example.com:8443/backups/a.json",
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("与配置的服务器不一致"))
    }
}
