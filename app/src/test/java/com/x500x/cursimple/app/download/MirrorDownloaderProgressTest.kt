package com.x500x.cursimple.app.download

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class MirrorDownloaderProgressTest {
    @Test
    fun `byte downloads stream known and chunked lengths and preserve trailing validation lambda`() = runBlocking {
        val expected = ByteArray(256 * 1024) { (it % 251).toByte() }
        val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val serverFailure = AtomicReference<Throwable?>()
        val serverThread = thread(isDaemon = true, name = "mirror-progress-fixture") {
            try {
                while (!server.isClosed) {
                    server.accept().use { socket ->
                        socket.soTimeout = 3000
                        val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                        val requestLine = requireNotNull(reader.readLine())
                        while (true) {
                            val header = requireNotNull(reader.readLine())
                            if (header.isEmpty()) break
                        }
                        val parts = requestLine.split(' ', limit = 3)
                        val chunked = parts[1] == "/chunked"
                        socket.getOutputStream().buffered().use { output ->
                            val lengthHeader = if (chunked) "Transfer-Encoding: chunked" else "Content-Length: ${expected.size}"
                            output.write("HTTP/1.1 200 OK\r\nConnection: close\r\n$lengthHeader\r\n\r\n".toByteArray(Charsets.US_ASCII))
                            if (parts[0] != "HEAD") {
                                if (chunked) {
                                    var offset = 0
                                    while (offset < expected.size) {
                                        val size = minOf(32 * 1024, expected.size - offset)
                                        output.write("${size.toString(16)}\r\n".toByteArray(Charsets.US_ASCII))
                                        output.write(expected, offset, size)
                                        output.write("\r\n".toByteArray(Charsets.US_ASCII))
                                        offset += size
                                    }
                                    output.write("0\r\n\r\n".toByteArray(Charsets.US_ASCII))
                                } else output.write(expected)
                            }
                            output.flush()
                        }
                    }
                }
            } catch (error: Exception) {
                if (!server.isClosed || error !is SocketException) serverFailure.set(error)
            }
        }
        val downloader = MirrorDownloader(
            labels = MirrorDownloaderLabels(
                localFileSource = "local", verifyFailed = "verify", readFailed = "read",
                localFileVerifyFailed = "local verify", localFileReadFailed = "local read",
                probeFailed = "probe", downloadFailed = "download", noSource = "no source",
            ),
        )
        try {
            for (unknown in listOf(false, true)) {
                val request = DownloadRequest(
                    DownloadPurpose.DirectUrl,
                    "http://127.0.0.1:${server.localPort}/${if (unknown) "chunked" else "known"}",
                )
                val progress = mutableListOf<Pair<Long, Long>>()
                val result = downloader.downloadBytes(request, onProgress = { downloaded, total ->
                    progress += downloaded to total
                })
                assertTrue(result is MirrorDownloadResult.Success)
                assertArrayEquals(expected, (result as MirrorDownloadResult.Success).value)
                val total = if (unknown) -1L else expected.size.toLong()
                assertEquals(0L to total, progress.first())
                assertEquals(expected.size.toLong() to total, progress.last())
                assertTrue(progress.any { it.first in 1L until expected.size.toLong() })
                assertTrue(progress.zipWithNext().all { (a, b) -> a.first <= b.first })
                assertTrue(progress.all { it.second == total })
                var validated = false
                val legacy = downloader.downloadBytes(request) { bytes ->
                    assertArrayEquals(expected, bytes)
                    validated = true
                }
                assertTrue(legacy is MirrorDownloadResult.Success)
                assertTrue(validated)
            }
        } finally {
            server.close()
            serverThread.join(5000)
            assertFalse("local HTTP fixture must stop", serverThread.isAlive)
            serverFailure.get()?.let { throw AssertionError("local HTTP fixture failed", it) }
        }
    }
}
