package com.x500x.cursimple.app.download

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

private class FakeMirror(
    private val body: ByteArray,
    private val supportsRange: Boolean = true,
    private val bytesPerSecond: Long = 0L,
    private val firstByteDelayMillis: Long = 0L,
    private val dropAfterBytes: Long = 0L,
    private val corrupt: Boolean = false,
    private val stallForever: Boolean = false,
) {
    val requests = AtomicInteger()
    val rangeRequests = AtomicInteger()
    val bytesServed = AtomicLong()
    private val pool = Executors.newCachedThreadPool { Thread(it).apply { isDaemon = true } }
    private val server = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = server.localPort
    val url: String get() = "http://127.0.0.1:$port/file.bin"

    init {
        thread(isDaemon = true, name = "fake-mirror-$port") {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                pool.execute { serve(socket) }
            }
        }
    }

    fun candidate(name: String) = DownloadCandidate(name, url)
    fun stop() { runCatching { server.close() }; pool.shutdownNow() }

    private fun serve(socket: Socket) {
        try {
            socket.use { s ->
                s.soTimeout = 10_000
                val input = s.getInputStream().bufferedReader(Charsets.US_ASCII)
                val requestLine = input.readLine() ?: return
                val headOnly = requestLine.startsWith("HEAD ")
                var range: String? = null
                while (true) {
                    val header = input.readLine() ?: return
                    if (header.isEmpty()) break
                    if (header.startsWith("Range:", ignoreCase = true)) range = header.substringAfter(':').trim()
                }
                requests.incrementAndGet()
                if (stallForever) { Thread.sleep(60_000); return }
                if (firstByteDelayMillis > 0) Thread.sleep(firstByteDelayMillis)
                var start = 0L
                var end = body.size - 1L
                var status = "200 OK"
                var extra = ""
                if (range != null && supportsRange) {
                    val spec = range!!.removePrefix("bytes=").split('-')
                    start = spec[0].toLong()
                    end = minOf(spec[1].toLong(), body.size - 1L)
                    status = "206 Partial Content"
                    extra = "Content-Range: bytes $start-$end/${body.size}\r\n"
                    rangeRequests.incrementAndGet()
                }
                val length = end - start + 1
                val out = s.getOutputStream()
                out.write("HTTP/1.1 $status\r\nContent-Length: $length\r\n${extra}Connection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                out.flush()
                if (headOnly) return
                var sent = 0L
                val piece = 16 * 1024
                while (sent < length) {
                    val n = minOf(piece.toLong(), length - sent).toInt()
                    val chunk = body.copyOfRange((start + sent).toInt(), (start + sent).toInt() + n)
                    if (corrupt) for (i in chunk.indices) chunk[i] = (chunk[i].toInt() xor 0x5A).toByte()
                    out.write(chunk)
                    out.flush()
                    sent += n
                    bytesServed.addAndGet(n.toLong())
                    if (dropAfterBytes in 1..sent) return
                    if (bytesPerSecond > 0) Thread.sleep(n * 1000L / bytesPerSecond)
                }
            }
        } catch (_: Exception) {
            // Client cancellation and disconnect are expected fixture behavior.
        }
    }
}

class FastTransferTest {
    @get:Rule val folder = TemporaryFolder()
    private val mirrors = mutableListOf<FakeMirror>()

    @After fun stopMirrors() = mirrors.forEach { it.stop() }

    private fun mirror(body: ByteArray, vararg options: (FakeMirrorOptions) -> Unit): FakeMirror {
        val o = FakeMirrorOptions().also { options.forEach { f -> f(it) } }
        return FakeMirror(body, o.supportsRange, o.bytesPerSecond, o.firstByteDelayMillis, o.dropAfterBytes, o.corrupt, o.stallForever)
            .also { mirrors += it }
    }

    private class FakeMirrorOptions {
        var supportsRange = true
        var bytesPerSecond = 0L
        var firstByteDelayMillis = 0L
        var dropAfterBytes = 0L
        var corrupt = false
        var stallForever = false
    }

    private fun body(size: Int) = ByteArray(size) { ((it * 31 + it / 7) % 251).toByte() }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun sha(file: File) = sha(file.readBytes())

    private fun labels() = MirrorDownloaderLabels("local", "verify", "read", "local verify", "local read", "probe", "download", "no source")

    private fun downloader(vararg candidates: DownloadCandidate, store: MirrorPreferenceStore? = null) = MirrorDownloader(
        labels = labels(),
        mirrorPool = object : DownloadMirrorPool() {
            override fun candidates(request: DownloadRequest) = candidates.toList()
        },
        preferenceStore = store,
    )

    private val request = DownloadRequest(DownloadPurpose.GithubRelease, "https://github.com/example/app/releases/download/v1/app.apk")

    @Test
    fun `a failed probe does not end the wave before a healthy mirror replies`() = runBlocking {
        val data = body(1024)
        val dead = DownloadCandidate("dead", "http://127.0.0.1:1/file.bin")
        val good = mirror(data, { it.firstByteDelayMillis = 250L })
        val probes = FastTransfer(SharedHttp.transfer, "test", null)
            .probe(listOf(dead, good.candidate("good")))

        assertEquals(listOf("good"), probes.map { it.candidate.sourceName })
        assertEquals(data.size.toLong(), probes.single().total)
        assertTrue(probes.single().acceptsRange)
    }

    @Test
    fun `several mirrors cooperate and the assembled file is byte for byte identical`() = runBlocking {
        val data = body(6 * 1024 * 1024)
        val a = mirror(data); val b = mirror(data); val c = mirror(data)
        val target = folder.newFile("app.apk")
        val result = downloader(a.candidate("a"), b.candidate("b"), c.candidate("c"))
            .downloadFile(request, target, validate = { assertEquals(sha(data), sha(it)) })

        assertTrue(result is MirrorDownloadResult.Success)
        assertEquals(sha(data), sha(target))
        val contributors = mirrors.count { it.rangeRequests.get() > 0 }
        assertTrue("至少两个镜像参与分块，实际 $contributors", contributors >= 2)
        val served = mirrors.sumOf { it.bytesServed.get() }
        assertTrue("总流量 $served 不应远超文件大小", served < data.size * 1.4)
    }

    @Test
    fun `a slow starting mirror does not hold the download hostage`() = runBlocking {
        val data = body(6 * 1024 * 1024)
        val slow = mirror(data, { it.bytesPerSecond = 150_000L }, { it.firstByteDelayMillis = 300L })
        val fast1 = mirror(data); val fast2 = mirror(data)
        val target = folder.newFile("app.apk")
        val started = System.nanoTime()
        downloader(slow.candidate("slow"), fast1.candidate("f1"), fast2.candidate("f2"))
            .downloadFile(request, target)
        val seconds = (System.nanoTime() - started) / 1e9

        assertEquals(sha(data), sha(target))
        assertTrue("耗时 $seconds 秒，仍被慢镜像拖住", seconds < 8.0)
        assertTrue(fast1.bytesServed.get() + fast2.bytesServed.get() > slow.bytesServed.get() * 3)
    }

    @Test
    fun `a dead mirror and one that drops mid chunk are routed around`() = runBlocking {
        val data = body(5 * 1024 * 1024)
        val dead = DownloadCandidate("dead", "http://127.0.0.1:1/file.bin")
        val flaky = mirror(data, { it.dropAfterBytes = 100 * 1024L })
        val good = mirror(data)
        val target = folder.newFile("app.apk")
        val result = downloader(dead, flaky.candidate("flaky"), good.candidate("good")).downloadFile(request, target)

        assertTrue(result is MirrorDownloadResult.Success)
        assertEquals(sha(data), sha(target))
    }

    @Test
    fun `a mirror serving wrong bytes is caught by validation and the sequential fallback still delivers`() = runBlocking {
        val data = body(4 * 1024 * 1024)
        val evil = mirror(data, { it.corrupt = true })
        val good = mirror(data)
        val target = folder.newFile("app.apk")
        val result = downloader(evil.candidate("evil"), good.candidate("good"))
            .downloadFile(request, target, validate = { file ->
                if (sha(file) != sha(data)) throw IOException("checksum mismatch")
            })

        assertTrue(result is MirrorDownloadResult.Success)
        assertEquals("最终落盘的必须是校验通过的正确文件", sha(data), sha(target))
    }

    @Test
    fun `large file with no ranged mirror is not downloaded several times in parallel`() = runBlocking {
        val data = body(3 * 1024 * 1024)
        // Throttle probes so byte accounting includes only data actually read before cancellation.
        val a = mirror(data, { it.supportsRange = false }, { it.bytesPerSecond = 3_000_000L })
        val b = mirror(data, { it.supportsRange = false }, { it.bytesPerSecond = 3_000_000L })
        val target = folder.newFile("app.apk")
        val result = downloader(a.candidate("a"), b.candidate("b")).downloadFile(request, target)

        assertTrue(result is MirrorDownloadResult.Success)
        assertEquals(sha(data), sha(target))
        val fullBodiesServed = (a.bytesServed.get() + b.bytesServed.get()).toDouble() / data.size
        // Fallback performs one full transfer rather than duplicating large non-Range downloads.
        assertTrue("完整下载了 $fullBodiesServed 遍", fullBodiesServed < 1.6)
    }

    @Test
    fun `progress starts at zero never goes backwards and ends at the total`() = runBlocking {
        val data = body(4 * 1024 * 1024)
        val a = mirror(data); val b = mirror(data)
        val reports = Collections.synchronizedList(mutableListOf<Pair<Long, Long>>())
        downloader(a.candidate("a"), b.candidate("b"))
            .downloadFile(request, folder.newFile("app.apk"), onProgress = { done, total -> reports += done to total })

        val snapshot = reports.toList()
        assertEquals(0L to data.size.toLong(), snapshot.first())
        assertEquals(data.size.toLong() to data.size.toLong(), snapshot.last())
        assertTrue("进度回退了：$snapshot", snapshot.zipWithNext().all { (x, y) -> y.first >= x.first })
        assertTrue(snapshot.all { it.first <= it.second })
    }

    @Test
    fun `measured speeds are remembered per host so the next run starts with the fast mirror`() = runBlocking {
        val data = body(5 * 1024 * 1024)
        val a = mirror(data, { it.bytesPerSecond = 4_000_000L }); val b = mirror(data, { it.bytesPerSecond = 4_000_000L })
        val store = RecordingStore()
        downloader(a.candidate("a"), b.candidate("b"), store = store).downloadFile(request, folder.newFile("app.apk"))

        assertTrue("应当记下实测速度", store.speeds.isNotEmpty())
        assertTrue(store.speeds.values.all { it > 0L })
    }

    @Test
    fun `cancelling stops a running download promptly`() = runBlocking {
        val data = body(8 * 1024 * 1024)
        val a = mirror(data, { it.bytesPerSecond = 400_000L }); val b = mirror(data, { it.bytesPerSecond = 400_000L })
        val job = async(Dispatchers.IO) {
            downloader(a.candidate("a"), b.candidate("b")).downloadFile(request, folder.newFile("app.apk"))
        }
        delay(1500)
        val before = a.bytesServed.get() + b.bytesServed.get()
        withTimeout(3000) { job.cancelAndJoin() }
        delay(500)
        val after = a.bytesServed.get() + b.bytesServed.get()
        assertTrue("取消后还在继续下载：$before → $after", after - before < 600 * 1024)
    }

    @Test
    fun `small payloads race staggered and a stalled first mirror costs only the stagger`() = runBlocking {
        val data = body(64 * 1024)
        val stalled = mirror(data, { it.stallForever = true })
        val good = mirror(data)
        val started = System.nanoTime()
        val result = downloader(stalled.candidate("stalled"), good.candidate("good")).downloadBytes(request)
        val millis = (System.nanoTime() - started) / 1_000_000

        assertTrue(result is MirrorDownloadResult.Success)
        assertEquals(sha(data), sha((result as MirrorDownloadResult.Success).value))
        assertEquals("good", result.candidate.sourceName)
        assertTrue("被卡住的镜像拖了 $millis ms", millis < 2500)
    }

    @Test
    fun `small payload failing validation moves on to the next mirror immediately`() = runBlocking {
        val data = body(64 * 1024)
        val wrong = mirror(data, { it.corrupt = true })
        val good = mirror(data)
        val result = downloader(wrong.candidate("wrong"), good.candidate("good"))
            .downloadBytes(request, validate = { if (sha(it) != sha(data)) throw IOException("not the expected file") })

        assertTrue(result is MirrorDownloadResult.Success)
        assertEquals("good", (result as MirrorDownloadResult.Success).candidate.sourceName)
        assertTrue(result.failures.any { it.sourceName == "wrong" })
    }

    @Test
    fun `hedged gives up only after every candidate failed and reports each failure`() = runBlocking {
        val transfer = FastTransfer(SharedHttp.transfer, "test", null)
        val outcome = transfer.hedged(listOf(DownloadCandidate("x", "u1"), DownloadCandidate("y", "u2")), staggerMillis = 10) {
            throw IOException("boom ${it.sourceName}")
        }
        assertTrue(outcome is HedgeOutcome.Lost)
        assertEquals(listOf("x", "y"), (outcome as HedgeOutcome.Lost).failures.map { it.sourceName }.sorted())
        assertNotNull(outcome.firstError)
    }

    @Test
    fun `hedged cancels the losers once a candidate wins`() = runBlocking {
        val transfer = FastTransfer(SharedHttp.transfer, "test", null)
        val loserCancelled = CompletableDeferred<Boolean>()
        val outcome = transfer.hedged(listOf(DownloadCandidate("slow", "u1"), DownloadCandidate("fast", "u2")), staggerMillis = 20) {
            if (it.sourceName == "slow") {
                try { delay(30_000); "late" } catch (e: kotlinx.coroutines.CancellationException) { loserCancelled.complete(true); throw e }
            } else "fast-result"
        }
        assertTrue(outcome is HedgeOutcome.Won)
        assertEquals("fast", (outcome as HedgeOutcome.Won).winner.sourceName)
        assertTrue(withTimeout(2000) { loserCancelled.await() })
    }

    private class RecordingStore : MirrorPreferenceStore {
        val speeds = java.util.concurrent.ConcurrentHashMap<String, Long>()
        override fun preferred(cacheKey: String): String? = null
        override fun recordSuccess(cacheKey: String, sourceName: String) = Unit
        override fun recordFailure(cacheKey: String, sourceName: String) = Unit
        override fun probeLatency(mirrorHost: String): Long? = null
        override fun recordProbe(mirrorHost: String, latencyMillis: Long?) = Unit
        override fun invalidate(mirrorHost: String) = Unit
        override fun recordSpeed(mirrorHost: String, kBps: Long) { speeds[mirrorHost] = kBps }
    }
}

class MirrorRankingTest {
    private fun c(name: String) = DownloadCandidate(name, "https://$name/x")
    private val all = listOf("a", "b", "c", "d", "e").map(::c)

    @Test
    fun `preferred first then fast then unknown then slow then cooling`() {
        val speeds = mapOf("b" to 3000L, "c" to 200L, "d" to 1500L)
        val ranked = rankCandidates(all, preferredName = "e", speedKBps = { speeds[it] }, coolingUntil = { 0L }, nowMillis = 1L)
        assertEquals(listOf("e", "b", "d", "a", "c"), ranked.map { it.sourceName })
    }

    @Test
    fun `mirrors still cooling after a failure go last and a cooling preferred one loses its spot`() {
        val ranked = rankCandidates(all, preferredName = "a", speedKBps = { null }, coolingUntil = { if (it == "a" || it == "c") 100L else 0L }, nowMillis = 50L)
        assertEquals(listOf("b", "d", "e", "a", "c"), ranked.map { it.sourceName })
        val recovered = rankCandidates(all, preferredName = "a", speedKBps = { null }, coolingUntil = { if (it == "a") 100L else 0L }, nowMillis = 200L)
        assertEquals("a", recovered.first().sourceName)
    }

    @Test
    fun `unmeasured pool keeps its original order and nothing is lost`() {
        val ranked = rankCandidates(all, preferredName = null, speedKBps = { null }, coolingUntil = { 0L }, nowMillis = 0L)
        assertEquals(all, ranked)
    }

    @Test
    fun `speed smoothing damps a single outlier and keeps the first sample`() {
        assertEquals(1000L, MirrorPreferenceStore.smoothSpeed(null, 1000L))
        assertEquals(2000L, MirrorPreferenceStore.smoothSpeed(3000L, 1000L))
        assertTrue(MirrorPreferenceStore.smoothSpeed(3000L, 1L) > 1000L)
    }
}

class SegmentStateTest {
    private var now = 0L

    @Test
    fun `chunks are handed out once and a failed one returns to the front`() {
        val state = SegmentState(3) { now }
        assertEquals(0, state.take()); assertEquals(1, state.take())
        assertEquals(1, state.fail(1))
        assertEquals("失败的块优先重领", 1, state.take())
        assertEquals(2, state.take())
        assertTrue(state.complete(0)); assertTrue(state.complete(1)); assertTrue(state.complete(2))
        assertTrue(state.allDone())
    }

    @Test
    fun `an idle worker hedges the oldest straggler only after it has been slow long enough`() {
        val state = SegmentState(1) { now }
        assertEquals(0, state.take())
        now += 500
        assertEquals("刚开始跑的块不对冲", null, state.take())
        now += 1_000
        assertEquals("跑太久的块对冲", 0, state.take())
        assertEquals("同一块只对冲一次", null, state.take())
        assertTrue("先完成的算数", state.complete(0))
        assertFalse("对冲的另一份不重复计入", state.complete(0))
        assertTrue(state.allDone())
    }

    @Test
    fun `released chunks go back without counting as failures`() {
        val state = SegmentState(2) { now }
        assertEquals(0, state.take())
        state.release(0)
        assertEquals(0, state.take())
        assertEquals(1, state.fail(0))
    }

    @Test
    fun `hedge threshold shrinks once quick chunks have finished`() {
        val state = SegmentState(4) { now }
        now = 0
        repeat(3) { assertEquals(it, state.take()) }
        now = 100
        assertTrue(state.complete(0)); assertTrue(state.complete(1))   // Two 100ms samples remain bounded by the 500ms hedge minimum.
        assertEquals(3, state.take())
        now = 400
        assertEquals("还没到自适应阈值", null, state.take())
        now = 700
        assertEquals("2 号块已跑 700 ms，超过 500 ms，对冲", 2, state.take())
    }
}
