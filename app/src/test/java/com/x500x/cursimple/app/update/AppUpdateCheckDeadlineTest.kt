package com.x500x.cursimple.app.update

import com.x500x.cursimple.app.download.DownloadCandidate
import com.x500x.cursimple.app.download.DownloadMirrorPool
import com.x500x.cursimple.app.download.DownloadRequest
import com.x500x.cursimple.app.download.MirrorDownloaderLabels
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/** Answers every request with headers, then one byte every second so no read timeout fires. */
private class TricklingServer {
    private val pool = Executors.newCachedThreadPool { Thread(it).apply { isDaemon = true } }
    private val server = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
    val url get() = "http://127.0.0.1:${server.localPort}/slow"

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                pool.execute {
                    runCatching {
                        socket.use { s ->
                            val input = s.getInputStream().bufferedReader()
                            while (input.readLine()?.isNotEmpty() == true) Unit
                            val out = s.getOutputStream()
                            out.write("HTTP/1.1 200 OK\r\nContent-Length: 100000\r\n\r\n".toByteArray())
                            repeat(100_000) { out.write('['.code); out.flush(); Thread.sleep(1_000) }
                        }
                    }
                }
            }
        }
    }

    fun stop() { runCatching { server.close() }; pool.shutdownNow() }
}

class AppUpdateCheckDeadlineTest {
    private val server = TricklingServer()

    @After fun stop() = server.stop()

    @Test
    fun `a mirror that trickles forever cannot keep the check spinning past its deadline`() = runBlocking {
        val checker = AppUpdateChecker(
            downloaderLabels = MirrorDownloaderLabels("local", "verify", "read", "local verify", "local read", "probe", "download", "no source"),
            mirrorPool = object : DownloadMirrorPool() {
                override fun candidates(request: DownloadRequest) = listOf(DownloadCandidate("slow", server.url))
            },
            checkDeadlineMillis = 2_000L,
        )
        val started = System.nanoTime()
        val result = checker.check(includePrerelease = true)
        val seconds = (System.nanoTime() - started) / 1e9

        assertTrue("检查转了 $seconds 秒才结束", seconds < 4.0)
        assertEquals(AppUpdateCheckResult.Failure(UpdateStatusReason.CheckError(UpdateErrorReason.Timeout)), result)
    }
}
