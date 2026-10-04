package com.x500x.cursimple.core.plugin.market.github

import com.x500x.cursimple.core.plugin.market.github.GitHubTestTransport.Reply
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class GitHubSourceRepositoryTest {
    private val slug = "owner/private"
    private val registry = """{"repositories":[{"name":"owner/plugin","release":{"tag":"v1","filename":"demo.zip"},"schools":["学校"]}]}"""

    private fun repository(
        transport: GitHubTestTransport,
        token: () -> String? = { "secret" },
        publicText: suspend (String) -> String = { throw AssertionError("private source sent to public transport: $it") },
        publicBytes: suspend (String) -> ByteArray = { throw AssertionError("private download sent to public transport: $it") },
    ) = GitHubRegistryRepository(apiClient = transport.api(), tokenProvider = token, fetchText = publicText, fetchBytes = publicBytes)

    @Test
    fun `private plugin and component registries never use public transport even without preferAccount`() = runBlocking {
        for (kind in MarketSourceKind.entries) for (preferAccount in listOf(false, true)) {
            val transport = GitHubTestTransport { request ->
                when (request.url.encodedPath) {
                    "/repos/$slug" -> Reply(repoFixture(slug, private = true))
                    "/repos/$slug/contents/${kind.dataFile}" -> Reply(registry)
                    else -> Reply(code = 599)
                }
            }
            val repo = repository(transport)
            val entries = repo.fetchSource(slug, kind, preferAccount)
            val entry = entries.single()
            assertEquals(slug, entry.registrySource)
            assertTrue(entry.viaAccount)
            assertNull("embedded browser URL must be resolved through API", entry.latestRelease)
            assertEquals(listOf("学校"), entry.schoolAliases)
            assertEquals(if (kind == MarketSourceKind.Component) "extension" else "", entry.kind)
            assertEquals(kind.dataBranch, transport.requests.last().url.queryParameter("ref"))
            assertTrue(transport.requests.all { it.header("Authorization") == "Bearer secret" })
        }
    }

    @Test
    fun `preferred account failure never falls back to mirrors or public direct releases`() = runBlocking {
        for (private in listOf(false, true)) for (code in listOf(401, 403, 429, 500)) {
            val transport = GitHubTestTransport { request ->
                if (request.url.encodedPath == "/repos/$slug") Reply(repoFixture(slug, private)) else Reply(code = code)
            }
            val check = repository(transport).checkSource(slug, MarketSourceKind.Plugin, preferAccount = true)
            assertEquals(if (code == 401) MarketSourceCheck.AccountExpired else MarketSourceCheck.Unreachable("GitHub $code"), check)
            assertEquals(2, transport.requests.size)
        }
    }

    @Test
    fun `unknown anonymous sources are checked on API before any mirror sees their names`() = runBlocking {
        val transport = GitHubTestTransport { Reply(code = 404) }
        val repo = repository(transport, token = { null })
        assertEquals(MarketSourceCheck.NotFoundOrPrivate, repo.checkSource(slug, MarketSourceKind.Plugin, preferAccount = false))
        assertEquals(1, transport.requests.size)
        assertNull(transport.requests.single().header("Authorization"))
    }

    @Test
    fun `checkSource distinguishes missing repositories expired tokens rate limits and network failures`() = runBlocking {
        val scenarios = listOf(
            Triple(null, Reply(code = 404), MarketSourceCheck.NotFoundOrPrivate),
            Triple("secret", Reply(code = 404), MarketSourceCheck.NotFound),
            Triple("secret", Reply(code = 401), MarketSourceCheck.AccountExpired),
            Triple("secret", Reply(code = 403), MarketSourceCheck.Unreachable("GitHub 403")),
            Triple(null, Reply(code = 429), MarketSourceCheck.Unreachable("GitHub 429")),
            Triple(null, Reply(failure = IOException("offline")), MarketSourceCheck.Unreachable("offline")),
        )
        for ((token, reply, expected) in scenarios) {
            val transport = GitHubTestTransport { reply }
            assertEquals(expected, repository(transport, token = { token }).checkSource(slug, MarketSourceKind.Plugin))
        }
    }

    @Test
    fun `checkSource reports nothing published only after existing repo has no registry or usable release`() = runBlocking {
        for (release in listOf(Reply(code = 404), Reply("""{"tag_name":"v1","assets":[]}"""))) {
            val transport = GitHubTestTransport { request ->
                when (request.url.encodedPath) {
                    "/repos/$slug" -> Reply(repoFixture(slug, private = true))
                    "/repos/$slug/releases/latest" -> release
                    else -> Reply(code = 404)
                }
            }
            assertEquals(MarketSourceCheck.NothingPublished, repository(transport).checkSource(slug, MarketSourceKind.Plugin))
        }
    }

    @Test
    fun `successful metadata does not disguise malformed registry or failed manifest downloads as nothing published`() = runBlocking {
        for (registryReply in listOf(Reply("{}"), Reply("not json"), Reply(code = 503))) {
            val transport = GitHubTestTransport { request ->
                if (request.url.encodedPath == "/repos/$slug") Reply(repoFixture(slug, private = true)) else registryReply
            }
            assertTrue(repository(transport).checkSource(slug, MarketSourceKind.Plugin) is MarketSourceCheck.Unreachable)
        }
        for (manifestReply in listOf(Reply("invalid"), Reply(code = 404), Reply(code = 403))) {
            val transport = GitHubTestTransport { request ->
                when (request.url.encodedPath) {
                    "/repos/$slug" -> Reply(repoFixture(slug, private = true))
                    "/repos/$slug/releases/latest" -> Reply(releaseFixture(slug))
                    "/repos/$slug/releases/assets/1" -> manifestReply
                    else -> Reply(code = 404)
                }
            }
            assertTrue(repository(transport).checkSource(slug, MarketSourceKind.Plugin) is MarketSourceCheck.Unreachable)
        }
    }

    @Test
    fun `empty private registry is available and still marks the source for account access`() = runBlocking {
        val transport = GitHubTestTransport { request ->
            if (request.url.encodedPath == "/repos/$slug") Reply(repoFixture(slug)) else Reply("""{"repositories":[]}""")
        }
        val repo = repository(transport)
        assertEquals(MarketSourceCheck.Available(0, true), repo.checkSource(slug, MarketSourceKind.Plugin))
        assertTrue(repo.fetchSource(slug, MarketSourceKind.Plugin, preferAccount = false).isEmpty())
        assertEquals(4, transport.requests.size)
    }

    @Test
    fun `direct private plugin and component releases download manifest and package through API`() = runBlocking {
        for (kind in MarketSourceKind.entries) {
            val transport = GitHubTestTransport { request ->
                when (request.url.encodedPath) {
                    "/repos/$slug" -> Reply(repoFixture(slug, private = true))
                    "/repos/$slug/releases/latest" -> Reply(releaseFixture(slug, "demo plugin.zip"))
                    "/repos/$slug/releases/assets/1" -> Reply("""{"name":"demo plugin.zip","version":"2.3"}""")
                    "/repos/$slug/releases/assets/2" -> Reply("zip bytes")
                    else -> Reply(code = 404)
                }
            }
            val repo = repository(transport)
            val entry = repo.fetchSource(slug, kind).single()
            val asset = entry.latestRelease!!
            assertEquals("2.3", asset.tagName)
            assertEquals("demo plugin.zip", asset.assetName)
            assertEquals(1234L, asset.sizeBytes)
            assertEquals("https://api.github.com/repos/$slug/releases/assets/2", asset.downloadUrl)
            assertTrue(entry.viaAccount)
            assertEquals(if (kind == MarketSourceKind.Component) "extension" else "", entry.kind)
            assertEquals("zip bytes", repo.downloadReleaseAsset(asset).decodeToString())
            assertEquals(listOf("application/octet-stream", "application/octet-stream"),
                transport.requests.filter { "/assets/" in it.url.encodedPath }.map { it.header("Accept") })
            assertTrue(transport.requests.all { it.header("Authorization") == "Bearer secret" })
        }
    }

    @Test
    fun `API manifest version can fall back to release tag`() = runBlocking {
        val transport = GitHubTestTransport { request ->
            when (request.url.encodedPath) {
                "/repos/$slug" -> Reply(repoFixture(slug, private = true))
                "/repos/$slug/releases/latest" -> Reply(releaseFixture(slug))
                "/repos/$slug/releases/assets/1" -> Reply("""{"filename":"demo.zip"}""")
                else -> Reply(code = 404)
            }
        }
        assertEquals("v2", repository(transport).fetchSource(slug, MarketSourceKind.Plugin).single().latestRelease?.tagName)
    }

    @Test
    fun `API rejects foreign and mismatched repository asset URLs before downloading`() = runBlocking {
        for (url in listOf("https://mirror.test/manifest.json", "https://api.github.com/repos/other/repo/releases/assets/1")) {
            val transport = GitHubTestTransport { request ->
                when (request.url.encodedPath) {
                    "/repos/$slug" -> Reply(repoFixture(slug, private = true))
                    "/repos/$slug/releases/latest" -> Reply("""{"assets":[{"name":"manifest.json","url":"$url"}]}""")
                    else -> Reply(code = 404)
                }
            }
            assertTrue(repository(transport).checkSource(slug, MarketSourceKind.Plugin) is MarketSourceCheck.Unreachable)
            assertFalse(transport.requests.any { "/assets/" in it.url.encodedPath })
        }
    }

    @Test
    fun `verified public direct repositories can use mirrors without disclosing token`() = runBlocking {
        val publicUrls = mutableListOf<String>()
        val transport = GitHubTestTransport { Reply(repoFixture(slug)) }
        val repo = repository(transport, publicText = { url ->
            publicUrls += url
            if ("raw.githubusercontent.com" in url) throw IOException("no registry")
            """{"filename":"demo plugin.zip","version":"v1"}"""
        }, publicBytes = { url -> publicUrls += url; "public bytes".encodeToByteArray() })
        val entry = repo.fetchSource(slug, MarketSourceKind.Component).single()
        assertFalse(entry.viaAccount)
        assertEquals("extension", entry.kind)
        assertEquals("public bytes", repo.downloadReleaseAsset(entry.latestRelease!!).decodeToString())
        assertTrue(publicUrls.all { "secret" !in it })
        assertTrue(publicUrls.last().endsWith("demo%20plugin.zip"))
    }

    @Test
    fun `private registry children stay on API and account failure cannot use cached public releases`() = runBlocking {
        var failing = false
        val transport = GitHubTestTransport { request ->
            when (request.url.encodedPath) {
                "/repos/$slug" -> Reply(repoFixture(slug, private = true))
                "/repos/$slug/contents/plugins-stars.json" -> Reply(registry)
                "/repos/owner/plugin/releases/latest" -> if (failing) Reply(code = 401) else Reply(releaseFixture("owner/plugin"))
                "/repos/owner/plugin/releases/assets/1" -> Reply("""{"filename":"demo.zip","version":"v2"}""")
                else -> Reply(code = 599)
            }
        }
        val repo = repository(transport)
        repo.fetchSource(slug, MarketSourceKind.Plugin)
        assertNotNull(repo.fetchLatestReleaseAsset("owner/plugin"))
        repo.clearAccountCache()
        repo.fetchSource(slug, MarketSourceKind.Plugin)
        failing = true
        assertNull(repo.fetchLatestReleaseAsset("owner/plugin", fresh = true))
        assertFalse(transport.requests.any { it.url.encodedPath == "/repos/owner/plugin" })
    }

    @Test
    fun `logout and token replacement invalidate account release caches automatically`() = runBlocking {
        val token = AtomicReference<String?>("first")
        val transport = GitHubTestTransport { request ->
            if (request.header("Authorization") != "Bearer first") Reply(code = 404) else when (request.url.encodedPath) {
                "/repos/$slug" -> Reply(repoFixture(slug, private = true))
                "/repos/$slug/releases/latest" -> Reply(releaseFixture(slug))
                "/repos/$slug/releases/assets/1" -> Reply("""{"filename":"demo.zip","version":"v2"}""")
                else -> Reply(code = 404)
            }
        }
        val repo = repository(transport, token = token::get)
        assertNotNull(repo.fetchLatestReleaseAsset(slug))
        val count = transport.requests.size
        assertNotNull(repo.fetchLatestReleaseAsset(slug, fresh = true))
        assertEquals(count, transport.requests.size)
        token.set(null)
        assertNull(repo.fetchLatestReleaseAsset(slug))
        assertNull(transport.requests.last().header("Authorization"))
        token.set("second")
        assertNull(repo.fetchLatestReleaseAsset(slug))
        assertEquals("Bearer second", transport.requests.last().header("Authorization"))
    }

    @Test
    fun `first installed refresh discovers private repo before any public transport request`() = runBlocking {
        val transport = GitHubTestTransport { request ->
            when (request.url.encodedPath) {
                "/repos/$slug" -> Reply(repoFixture(slug, private = true))
                "/repos/$slug/releases/latest" -> Reply(releaseFixture(slug))
                "/repos/$slug/releases/assets/1" -> Reply("""{"filename":"demo.zip","version":"v2"}""")
                else -> Reply(code = 404)
            }
        }
        val asset = repository(transport).fetchLatestReleaseAsset(slug, fresh = true)
        assertEquals("https://api.github.com/repos/$slug/releases/assets/2", asset?.downloadUrl)
        assertEquals("/repos/$slug", transport.requests.first().url.encodedPath)
    }

    @Test
    fun `explicit viaAccount uses API only and invalidates an existing public release cache`() = runBlocking {
        val transport = GitHubTestTransport { request ->
            when (request.url.encodedPath) {
                "/repos/$slug" -> Reply(repoFixture(slug))
                "/repos/$slug/releases/latest" -> Reply(releaseFixture(slug))
                "/repos/$slug/releases/assets/1" -> Reply("""{"filename":"demo.zip","version":"v2"}""")
                else -> Reply(code = 401)
            }
        }
        var publicCalls = 0
        val repo = repository(transport, publicText = {
            publicCalls++
            """{"filename":"demo.zip","version":"old-public"}"""
        })
        assertEquals("old-public", repo.fetchLatestReleaseAsset(slug)?.tagName)
        assertEquals("v2", repo.fetchLatestReleaseAsset(slug, fresh = true, viaAccount = true)?.tagName)
        assertEquals(1, publicCalls)
        val first = transport.requests.size
        assertEquals("v2", repo.fetchLatestReleaseAsset(slug, viaAccount = true)?.tagName)
        assertEquals(first, transport.requests.size)
    }

    @Test
    fun `explicit viaAccount failure and logged out hint never fall back to mirrors`() = runBlocking {
        for (token in listOf(null, "secret")) {
            val transport = GitHubTestTransport { Reply(code = if (token == null) 404 else 401) }
            assertNull(repository(transport, token = { token }).fetchLatestReleaseAsset(slug, viaAccount = true))
            assertEquals("/repos/$slug/releases/latest", transport.requests.single().url.encodedPath)
        }
    }

    @Test
    fun `explicit logout clear stops old in-flight responses from repopulating caches`() = runBlocking {
        supervisorScope {
            val started = CompletableDeferred<Unit>()
            val release = CountDownLatch(1)
            var first = true
            val transport = GitHubTestTransport { request ->
                when (request.url.encodedPath) {
                    "/repos/$slug" -> Reply(repoFixture(slug, private = true))
                    "/repos/$slug/releases/latest" -> Reply(releaseFixture(slug))
                    "/repos/$slug/releases/assets/1" -> {
                        if (first) {
                            first = false
                            started.complete(Unit)
                            if (!release.await(5, TimeUnit.SECONDS)) throw IOException("fixture timeout")
                        }
                        Reply("""{"filename":"demo.zip","version":"v2"}""")
                    }
                    else -> Reply(code = 404)
                }
            }
            val repo = repository(transport)
            val old = async { repo.fetchLatestReleaseAsset(slug, viaAccount = true) }
            try {
                withTimeout(5000) { started.await() }
                val waiter = async(start = CoroutineStart.UNDISPATCHED) { repo.fetchLatestReleaseAsset(slug) }
                repo.clearAccountCache()
                try { waiter.await(); fail("waiter should be cancelled") } catch (_: CancellationException) { }
                release.countDown()
                try { old.await(); fail("old account response must be invalidated") } catch (_: CancellationException) { }
                val count = transport.requests.size
                assertNotNull(repo.fetchLatestReleaseAsset(slug))
                assertTrue(transport.requests.size > count)
            } finally {
                release.countDown()
                old.cancel()
            }
        }
    }

    @Test
    fun `public fetch cancellation propagates through source check release lookup and shared waiters`() = runBlocking {
        val repo = GitHubRegistryRepository(fetchText = { throw CancellationException("cancelled") })
        for (operation in listOf<suspend () -> Any?>(
            { repo.fetchSource(DefaultMarketSources.PLUGIN_REGISTRY, MarketSourceKind.Plugin) },
            { repo.checkSource(DefaultMarketSources.PLUGIN_REGISTRY, MarketSourceKind.Plugin, preferAccount = false) },
            { repo.fetchLatestReleaseAsset(DefaultMarketSources.PLUGIN_REGISTRY) },
        )) {
            try { operation(); fail("cancellation swallowed") } catch (error: CancellationException) {
                assertEquals("cancelled", error.message)
            }
        }
        supervisorScope {
            val started = CompletableDeferred<Unit>()
            val unblock = CompletableDeferred<Unit>()
            val shared = GitHubRegistryRepository(fetchText = {
                started.complete(Unit)
                unblock.await()
                throw CancellationException("shared cancelled")
            })
            val owner = async { shared.fetchLatestReleaseAsset(DefaultMarketSources.PLUGIN_REGISTRY) }
            started.await()
            val waiter = async(start = CoroutineStart.UNDISPATCHED) { shared.fetchLatestReleaseAsset(DefaultMarketSources.PLUGIN_REGISTRY) }
            unblock.complete(Unit)
            for (task in listOf(owner, waiter)) {
                try { task.await(); fail("shared cancellation swallowed") } catch (_: CancellationException) { }
            }
        }
    }

    @Test
    fun `invalid source performs no transport requests`() = runBlocking {
        val transport = GitHubTestTransport { Reply(code = 599) }
        assertEquals(MarketSourceCheck.NotFound, repository(transport).checkSource("https://evil.test/o/r", MarketSourceKind.Plugin))
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `missing private metadata is not proof that a custom source is public`() = runBlocking {
        val transport = GitHubTestTransport { Reply("""{"full_name":"$slug"}""") }
        val repo = repository(transport)
        assertTrue(repo.checkSource(slug, MarketSourceKind.Plugin, preferAccount = false) is MarketSourceCheck.Unreachable)
        assertNull(repo.fetchLatestReleaseAsset(slug))
        assertEquals(2, transport.requests.size)
        assertTrue(transport.requests.all { it.url.encodedPath == "/repos/$slug" })
    }

    @Test
    fun `browser asset URLs of private repos are rejected before public download`() = runBlocking {
        val transport = GitHubTestTransport { Reply(repoFixture(slug, private = true)) }
        try {
            repository(transport).downloadReleaseAsset("https://github.com/$slug/releases/latest/download/demo.zip")
            fail("private browser URL")
        } catch (_: IllegalArgumentException) {
            assertEquals(1, transport.requests.size)
        }
    }

    @Test
    fun `public download cancellation also propagates`() = runBlocking {
        val repo = repository(GitHubTestTransport { Reply(repoFixture(slug)) }, publicBytes = { throw CancellationException("download cancelled") })
        try {
            repo.downloadReleaseAsset("https://github.com/$slug/releases/latest/download/demo.zip")
            fail("download cancellation swallowed")
        } catch (error: CancellationException) {
            assertEquals("download cancelled", error.message)
        }
    }

    @Test
    fun `default public registry children still query mirrors when GitHub API is offline`() = runBlocking {
        val transport = GitHubTestTransport { Reply(failure = IOException("API offline")) }
        val repo = repository(transport, publicText = { url ->
            if ("raw.githubusercontent.com" in url) registry
            else """{"filename":"demo.zip","version":"new-public"}"""
        }, publicBytes = { "public bytes".encodeToByteArray() })
        val entry = repo.fetchSource(DefaultMarketSources.PLUGIN_REGISTRY, MarketSourceKind.Plugin).single()
        assertFalse(entry.viaAccount)
        val latest = repo.fetchLatestReleaseAsset(entry.fullName, fresh = true)!!
        assertEquals("new-public", latest.tagName)
        assertEquals("public bytes", repo.downloadReleaseAsset(latest).decodeToString())
        assertTrue("default child must not need API reachability", transport.requests.isEmpty())
    }

    @Test
    fun `account hint overrides public registry knowledge and clears its public cache`() = runBlocking {
        val transport = GitHubTestTransport { Reply(code = 401) }
        var manifestCalls = 0
        val repo = repository(transport, publicText = { url ->
            if ("raw.githubusercontent.com" in url) registry else {
                manifestCalls++
                """{"filename":"demo.zip","version":"public"}"""
            }
        })
        repo.fetchSource(DefaultMarketSources.PLUGIN_REGISTRY, MarketSourceKind.Plugin)
        assertNotNull(repo.fetchLatestReleaseAsset("owner/plugin"))
        assertNull(repo.fetchLatestReleaseAsset("owner/plugin", viaAccount = true))
        assertEquals(1, manifestCalls)
        assertEquals("/repos/owner/plugin/releases/latest", transport.requests.single().url.encodedPath)
        // 重新读公开清单也不能盖掉账号路径。
        repo.fetchSource(DefaultMarketSources.PLUGIN_REGISTRY, MarketSourceKind.Plugin)
        assertNull(repo.fetchLatestReleaseAsset("owner/plugin"))
        assertEquals(1, manifestCalls)
    }

    @Test
    fun `API only query does not join an in-flight public query or reuse its result`() = runBlocking {
        supervisorScope {
            val started = CompletableDeferred<Unit>()
            val unblock = CompletableDeferred<Unit>()
            val transport = GitHubTestTransport { request ->
                when (request.url.encodedPath) {
                    "/repos/owner/plugin/releases/latest" -> Reply(releaseFixture("owner/plugin"))
                    "/repos/owner/plugin/releases/assets/1" -> Reply("""{"filename":"demo.zip","version":"account"}""")
                    else -> Reply(code = 599)
                }
            }
            val repo = repository(transport, publicText = { url ->
                if ("raw.githubusercontent.com" in url) registry else {
                    started.complete(Unit)
                    unblock.await()
                    """{"filename":"demo.zip","version":"public"}"""
                }
            })
            repo.fetchSource(DefaultMarketSources.PLUGIN_REGISTRY, MarketSourceKind.Plugin)
            val publicQuery = async { repo.fetchLatestReleaseAsset("owner/plugin") }
            try {
                started.await()
                val account = withTimeout(5000) { repo.fetchLatestReleaseAsset("owner/plugin", viaAccount = true) }
                assertEquals("account", account?.tagName)
                unblock.complete(Unit)
                try { publicQuery.await(); fail("old public result must be invalidated") } catch (_: CancellationException) { }
                assertEquals("account", repo.fetchLatestReleaseAsset("owner/plugin")?.tagName)
            } finally {
                unblock.complete(Unit)
                publicQuery.cancel()
            }
        }
    }
}
