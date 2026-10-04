package com.x500x.cursimple.core.plugin.market.github

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class GitHubApiClientTest {
    private val assetUrl = "https://api.github.com/repos/owner/private/releases/assets/1"

    @Test
    fun `account download streams byte progress after redirect with known or unknown length`() = runBlocking {
        val payload = "x".repeat(192 * 1024)
        for (unknownLength in listOf(false, true)) {
            val transport = GitHubTestTransport { request ->
                if (request.url.host == "api.github.com") {
                    GitHubTestTransport.Reply(code = 302, location = "https://release-assets.githubusercontent.com/opaque")
                } else GitHubTestTransport.Reply(payload, unknownLength = unknownLength)
            }
            val progress = mutableListOf<Pair<Long, Long>>()
            val repository = GitHubRegistryRepository(apiClient = transport.api(), tokenProvider = { "test-only-token" })
            val bytes = repository.downloadAccountAsset(assetUrl) { downloaded, total -> progress += downloaded to total }
            val total = if (unknownLength) -1L else payload.length.toLong()
            assertEquals(payload, bytes.decodeToString())
            assertEquals(0L to total, progress.first())
            assertEquals(bytes.size.toLong() to total, progress.last())
            assertTrue("must report actual bytes before EOF", progress.any { it.first in 1L until bytes.size.toLong() })
            assertTrue(progress.zipWithNext().all { (a, b) -> a.first <= b.first })
            assertTrue(progress.all { it.second == total })
            assertEquals("Bearer test-only-token", transport.requests.first().header("Authorization"))
            assertNull(transport.requests.last().header("Authorization"))
        }
    }

    @Test
    fun `cancelling from first byte progress stops the stream before completion`() = runBlocking {
        val payload = "x".repeat(192 * 1024)
        val transport = GitHubTestTransport { GitHubTestTransport.Reply(payload) }
        val progress = mutableListOf<Long>()
        var completed = false
        val download = launch {
            val downloadJob = currentCoroutineContext().job
            transport.api().downloadAsset(assetUrl, "test-only-token") { downloaded, _ ->
                progress += downloaded
                if (downloaded > 0L) downloadJob.cancel()
            }
            completed = true
        }
        withTimeout(5000) { download.join() }
        assertTrue(download.isCancelled)
        assertFalse(completed)
        assertEquals(0L, progress.first())
        assertTrue(progress.last() in 1L until payload.length.toLong())
    }

    @Test
    fun `private contents use API raw media type and encode ref and path`() = runBlocking {
        val transport = GitHubTestTransport { GitHubTestTransport.Reply("{\"repositories\":[]}") }
        val raw = transport.api().fileText("owner/private", "branch/a & b", "dir/a b.json", "private-token")
        assertEquals("{\"repositories\":[]}", raw)
        val request = transport.requests.single()
        assertEquals("api.github.com", request.url.host)
        assertEquals("/repos/owner/private/contents/dir/a%20b.json", request.url.encodedPath)
        assertEquals("branch/a & b", request.url.queryParameter("ref"))
        assertEquals("application/vnd.github.raw+json", request.header("Accept"))
        assertEquals("Bearer private-token", request.header("Authorization"))
    }

    @Test
    fun `asset redirects strip token outside API and never restore it`() = runBlocking {
        val urls = listOf(assetUrl, "https://release-assets.githubusercontent.com/opaque", "https://api.github.com/returned")
        val transport = GitHubTestTransport { request ->
            val index = urls.indexOf(request.url.toString())
            if (index < 2) GitHubTestTransport.Reply(code = 302, location = urls[index + 1])
            else GitHubTestTransport.Reply("zip bytes")
        }
        assertEquals("zip bytes", transport.api().downloadAsset(assetUrl, "secret").decodeToString())
        assertEquals(urls, transport.requests.map { it.url.toString() })
        assertEquals(listOf("Bearer secret", null, null), transport.requests.map { it.header("Authorization") })
        assertTrue(transport.requests.all { it.header("Accept") == "application/octet-stream" })
    }

    @Test
    fun `same API origin relative redirect retains credentials`() = runBlocking {
        val transport = GitHubTestTransport { request ->
            if (request.url.pathSegments.last() == "1") GitHubTestTransport.Reply(code = 307, location = "2")
            else GitHubTestTransport.Reply("package")
        }
        assertEquals("package", transport.api().downloadAsset(assetUrl, "secret").decodeToString())
        assertEquals(2, transport.requests.size)
        assertTrue(transport.requests.all { it.header("Authorization") == "Bearer secret" })
    }

    @Test
    fun `alternate HTTPS port is tokenless`() = runBlocking {
        val transport = GitHubTestTransport { request ->
            if (request.url.port == 443) GitHubTestTransport.Reply(code = 302, location = "https://api.github.com:444/blob")
            else GitHubTestTransport.Reply("bytes")
        }
        transport.api().downloadAsset(assetUrl, "secret")
        assertNull(transport.requests.last().header("Authorization"))
    }

    @Test
    fun `HTTP downgrade and userinfo redirects stop before credentials leave API`() = runBlocking {
        for (location in listOf("http://api.github.com/blob", "http://mirror.test/blob", "https://user:secret@mirror.test/blob")) {
            val transport = GitHubTestTransport { GitHubTestTransport.Reply(code = 302, location = location) }
            try {
                transport.api().downloadAsset(assetUrl, "secret")
                fail(location)
            } catch (_: IOException) {
                assertEquals(location, 1, transport.requests.size)
            }
        }
    }

    @Test
    fun `redirect loop is bounded`() = runBlocking {
        val transport = GitHubTestTransport { GitHubTestTransport.Reply(code = 302, location = assetUrl) }
        try {
            transport.api().downloadAsset(assetUrl, "secret")
            fail("redirect loop")
        } catch (_: IOException) {
            assertEquals(11, transport.requests.size)
        }
    }

    @Test
    fun `asset URL validation rejects foreign origins and non asset endpoints`() = runBlocking {
        val transport = GitHubTestTransport { GitHubTestTransport.Reply("unexpected") }
        val api = transport.api()
        assertTrue(GitHubApiClient.isAssetApiUrl(assetUrl))
        for (url in listOf(
            "https://api.github.com.evil.test/repos/o/r/releases/assets/1", "http://api.github.com/repos/o/r/releases/assets/1",
            "https://user@api.github.com/repos/o/r/releases/assets/1", "https://api.github.com:444/repos/o/r/releases/assets/1",
            "https://api.github.com/repos/o/r/releases/assets/no-id", "https://api.github.com/repos/o/r/releases/assets/1/extra",
            "https://api.github.com/repos/o/r/releases/assets/1?token=other", "https://api.github.com/repos/o/r/releases/assets/1#x",
            "https://api.github.com/repos/o/r/contents/releases/assets/1", "https://github.com/o/r/releases/assets/1",
        )) {
            assertFalse(url, GitHubApiClient.isAssetApiUrl(url))
            try { api.downloadAsset(url, "secret"); fail(url) } catch (_: IllegalArgumentException) { }
        }
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `HTTP status survives for source error classification`() = runBlocking {
        val transport = GitHubTestTransport { GitHubTestTransport.Reply(code = 401) }
        try { transport.api().repo("owner/repo", "secret"); fail("401") } catch (error: GitHubApiClient.HttpError) {
            assertEquals(401, error.code)
        }
    }

    @Test
    fun `cancelling API coroutine cancels active transport call`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        val transport = Call.Factory { request ->
            // Delegate optional tag APIs to a real, unexecuted call so the fake stays compatible with OkHttp.
            object : Call by OkHttpClient().newCall(request) {
                override fun request(): Request = request
                override fun enqueue(responseCallback: Callback) { started.complete(Unit) }
                override fun execute(): Response = error("must use cancellable enqueue")
                override fun cancel() { cancelled = true }
                override fun isExecuted() = started.isCompleted
                override fun isCanceled() = cancelled
                override fun timeout() = Timeout.NONE
                override fun clone(): Call = error("unused")
            }
        }
        val job = launch { GitHubApiClient(transport = transport).viewer("secret") }
        started.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(cancelled)
    }
}
