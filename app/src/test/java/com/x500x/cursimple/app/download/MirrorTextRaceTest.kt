package com.x500x.cursimple.app.download

import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.Collections
import kotlin.concurrent.thread

class MirrorTextRaceTest {
    @Test fun `validated source wins without waiting for stalled mirrors and cancels their sockets`() = runBlocking {
        val calls = Collections.synchronizedList(mutableListOf<FixtureCall>())
        val request = DownloadRequest(DownloadPurpose.GithubRelease,
            "https://github.com/example/component/releases/latest/download/manifest.json")
        // The first pool candidate returns a fast error body; do not hardcode its hostname.
        val fastBad = MirrorPreferenceStore.hostOf(
            fastTextCandidates(DownloadMirrorPool().candidates(request)).first { it.sourceName != DownloadSourceIds.GITHUB_ORIGIN }.url,
        )
        val factory = object : Call.Factory {
            override fun newCall(request: Request): Call = FixtureCall(request, fastBad).also { calls += it }
        }
        val downloader = MirrorDownloader(labels(), textTransport = factory)
        val at = System.nanoTime()
        val result = downloader.downloadText(request, validate = {
                require(it.contains("\"version\"")) { "not a release manifest" }
            })
        val elapsed = (System.nanoTime() - at) / 1_000_000
        assertTrue(result is MirrorDownloadResult.Success)
        assertEquals(DownloadSourceIds.GITHUB_ORIGIN, (result as MirrorDownloadResult.Success).candidate.sourceName)
        assertTrue("Winner was delayed by a stalled mirror: $elapsed ms", elapsed < 1500)
        assertTrue(calls.filter { it.original.url.host != "github.com" && it.original.url.host != fastBad }.all { it.isCanceled() })
        assertTrue(result.failures.any { MirrorPreferenceStore.hostOf("https://${it.sourceName}/") == fastBad || it.sourceName == fastBad })
    }

    private class FixtureCall(val original: Request, val fastBad: String) : Call {
        @Volatile private var cancelled = false
        override fun request(): Request = original
        override fun enqueue(callback: Callback) {
            // The fastest mirror returns a JSON error page; it must not beat a valid manifest.
            if (original.url.host == fastBad || original.url.host == "github.com") thread(isDaemon = true) {
                Thread.sleep(if (original.url.host == fastBad) 10 else 100)
                if (!cancelled) callback.onResponse(this, Response.Builder().request(original).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body((if (original.url.host == "github.com")
                        "{\"version\":\"2.0.0\",\"filename\":\"component.zip\"}" else "{\"error\":\"denied\"}")
                        .toResponseBody("application/json".toMediaType())).build())
            }
        }
        override fun execute(): Response = throw IOException("Async fixture only")
        override fun cancel() { cancelled = true }
        override fun isExecuted(): Boolean = true
        override fun isCanceled(): Boolean = cancelled
        override fun timeout(): Timeout = Timeout()
        override fun clone(): Call = FixtureCall(original, fastBad)
        override fun <T : Any> tag(type: kotlin.reflect.KClass<T>): T? = null
        override fun <T> tag(type: Class<out T>): T? = null
        override fun <T : Any> tag(type: kotlin.reflect.KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
    }
    private fun labels() = MirrorDownloaderLabels("local", "verify", "read", "local verify", "local read", "probe", "download", "no source")
}
