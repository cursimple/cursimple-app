package com.x500x.cursimple.feature.plugin

import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginStreamLimitsTest {
    @Test
    fun `bounded read returns content within the limit`() {
        val payload = ByteArray(4096) { (it % 251).toByte() }

        assertArrayEquals(payload, ByteArrayInputStream(payload).readAtMostBytes(4096L))
    }

    @Test
    fun `bounded read stops before buffering an oversized stream`() {
        val source = CountingInputStream(ByteArrayInputStream(ByteArray(2 * 1024 * 1024)))

        assertNull(source.readAtMostBytes(1024L))
        assertTrue(
            "已读取 ${source.readCount} 字节",
            source.readCount <= 1024L + STREAM_READ_BUFFER_BYTES,
        )
    }

    @Test
    fun `local package read rejects oversized input`() {
        val error = runCatching {
            ByteArrayInputStream(ByteArray(2048)).readLocalPackageBytes(1024L)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("超过大小限制"))
    }

    @Test
    fun `local package read accepts input within the limit`() {
        val payload = ByteArray(512) { 7 }

        assertArrayEquals(payload, ByteArrayInputStream(payload).readLocalPackageBytes(1024L))
    }

    @Test
    fun `network capture connection gets finite timeouts`() {
        val connection = FakeHttpConnection()

        assertEquals(0, connection.connectTimeout)
        assertEquals(0, connection.readTimeout)

        connection.applyNetworkCaptureTimeouts()

        assertTrue(connection.connectTimeout > 0)
        assertTrue(connection.readTimeout > 0)
        assertEquals(NETWORK_CAPTURE_CONNECT_TIMEOUT_MS, connection.connectTimeout)
        assertEquals(NETWORK_CAPTURE_READ_TIMEOUT_MS, connection.readTimeout)
    }

    @Test
    fun `intercepted body limit stays above the capture body limit`() {
        assertTrue(MAX_INTERCEPTED_BODY_BYTES > 0)
        assertTrue(MAX_INTERCEPTED_BODY_BYTES.toLong() < MAX_LOCAL_PACKAGE_BYTES)
    }

    private class FakeHttpConnection : HttpURLConnection(URL("https://example.com/resource")) {
        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false
    }

    private class CountingInputStream(source: InputStream) : FilterInputStream(source) {
        var readCount: Long = 0
            private set

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) {
                readCount += 1
            }
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val value = super.read(b, off, len)
            if (value > 0) {
                readCount += value.toLong()
            }
            return value
        }
    }
}
