package com.x500x.cursimple.core.plugin.market

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketIndexRepositoryTest {
    @Test
    fun `progress downloader receives callback and legacy single url injection still works`() = runBlocking {
        val expected = byteArrayOf(1, 2, 3)
        val progress = mutableListOf<Pair<Long, Long>>()
        val repository = MarketIndexRepository(
            downloadBytes = { error("progress injection must take priority") },
            downloadBytesWithProgress = { url, onProgress ->
                assertEquals("https://example.test/plugin.zip", url)
                onProgress(0L, -1L)
                onProgress(2L, -1L)
                onProgress(3L, -1L)
                expected
            },
        )
        assertArrayEquals(expected, repository.downloadPackage("https://example.test/plugin.zip") { downloaded, total ->
            progress += downloaded to total
        })
        assertEquals(listOf(0L to -1L, 2L to -1L, 3L to -1L), progress)
        val legacy = MarketIndexRepository(downloadBytes = { expected })
        assertArrayEquals(expected, legacy.downloadPackage("https://example.test/plugin.zip"))
    }

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

    @Test
    fun `component market manifest decodes components`() = runBlocking {
        val repository = MarketIndexRepository(
            fetchText = {
                """
                {
                  "indexId": "components",
                  "generatedAt": "2026-05-15T00:00:00Z",
                  "components": [
                    {
                      "id": "engine.chromium.android",
                      "name": "Chromium",
                      "type": "engine_chromium",
                      "version": "1.0.0",
                      "downloadUrl": "https://example.test/chromium.zip"
                    }
                  ]
                }
                """.trimIndent()
            },
            downloadBytes = { ByteArray(0) },
        )

        val payload = repository.fetchComponentIndex("https://example.test/components.json")

        assertEquals("components", payload.indexId)
        assertEquals(1, payload.components.size)
        assertEquals("engine.chromium.android", payload.components.single().id)
    }
}
