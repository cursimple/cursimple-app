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
}
