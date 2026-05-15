package com.x500x.cursimple.core.plugin.market

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketIndexRepositoryTest {
    @Test
    fun `unknown market manifest becomes empty unsupported payload`() = runBlocking {
        val repository = MarketIndexRepository(
            fetchText = { """{"schema":"future"}""" },
            downloadBytes = { ByteArray(0) },
        )

        val payload = repository.fetch("https://example.test/manifest.json")

        assertEquals("unsupported", payload.indexId)
        assertEquals("", payload.generatedAt)
        assertTrue(payload.plugins.isEmpty())
    }

    @Test
    fun `package download uses injected downloader`() = runBlocking {
        val expected = byteArrayOf(1, 2, 3)
        val repository = MarketIndexRepository(
            fetchText = { """{"schema":"future"}""" },
            downloadBytes = { expected },
        )

        assertArrayEquals(expected, repository.downloadPackage("https://example.test/plugin.zip"))
    }
}
