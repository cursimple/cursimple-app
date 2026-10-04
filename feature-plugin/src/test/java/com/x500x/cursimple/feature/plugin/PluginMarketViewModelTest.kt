package com.x500x.cursimple.feature.plugin

import com.x500x.cursimple.core.data.UserPreferences
import com.x500x.cursimple.core.data.UserPreferencesRepository
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.PluginInstallPreview
import com.x500x.cursimple.core.plugin.install.PluginInstallResult
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import com.x500x.cursimple.core.plugin.manifest.PluginManifest
import com.x500x.cursimple.core.plugin.market.github.DefaultMarketSources
import com.x500x.cursimple.core.plugin.market.github.GitHubReleaseAsset
import com.x500x.cursimple.core.plugin.market.github.GitHubRepoSummary
import com.x500x.cursimple.core.plugin.market.github.MarketSourceCheck
import com.x500x.cursimple.core.plugin.market.github.MarketSourceKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy

class PluginMarketViewModelTest {
    @Test
    fun `merges ordered sources and keeps components out of school catalog`() {
        Harness(plugins = listOf("https://github.com/One/Registry", "one/REGISTRY", "two/registry"), components = listOf("parts/registry")).use { h ->
            h.ops.source = { source, _ -> when (source.source) {
                "one/registry" -> listOf(repo("school/shared", description = "first"), repo("school/one"))
                "two/registry" -> listOf(repo("SCHOOL/SHARED", description = "second"), repo("school/two"))
                else -> listOf(repo("school/shared"), repo("parts/widget", kind = "schedule"))
            } }
            h.vm.loadRegistry()

            assertEquals(listOf("school/shared", "school/one", "school/two"), h.state.marketRepos.map { it.fullName })
            assertEquals(listOf("parts/widget"), h.state.componentRepos.map { it.fullName })
            assertEquals(4, h.state.allMarketRepos.size)
            assertEquals("first", h.state.marketRepos.first().description)
            assertEquals("one/registry", h.state.marketRepos.first().registrySource)
            assertTrue(h.state.componentRepos.single().isExtension)
            assertEquals(3, h.ops.sourceCalls.size)
            assertEquals(MarketSourceKind.Component, h.ops.sourceCalls.last().first.kind)
            assertTrue(h.ops.sourceCalls.all { it.second })
        }
    }

    @Test
    fun `default sources use public preference while custom sources prefer account`() {
        Harness(plugins = listOf(DefaultMarketSources.PLUGIN_REGISTRY, "me/registry"), components = listOf(DefaultMarketSources.COMPONENT_REGISTRY)).use { h ->
            h.vm.loadRegistry()
            assertEquals(listOf(false, true, false), h.ops.sourceCalls.map { it.second })
        }
    }

    @Test
    fun `failed sources have independent diagnostics and successful lists survive refresh`() {
        Harness(plugins = listOf("good/registry", "bad/registry"), components = listOf("parts/registry")).use { h ->
            val offline = IOException("offline")
            h.ops.source = { source, _ -> if (source.source == "bad/registry") throw offline else listOf(repo("${source.source.substringBefore('/')}/item")) }
            h.ops.check = { _, _ -> MarketSourceCheck.NotFoundOrPrivate }
            h.vm.loadRegistry()

            assertEquals(listOf("good/item"), h.state.marketRepos.map { it.fullName })
            assertEquals(listOf("parts/item"), h.state.componentRepos.map { it.fullName })
            assertEquals("bad/registry", h.state.sourceErrors.single().source)
            assertSame(offline, h.state.sourceErrors.single().error)
            assertEquals(MarketSourceCheck.NotFoundOrPrivate, h.state.sourceErrors.single().check)
            assertEquals(0L, h.state.lastLoadedAtMillis)
            assertTrue(h.state.status is PluginMarketStatus.MarketLoaded)

            h.ops.source = { _, _ -> throw offline }
            h.vm.loadRegistry()
            assertEquals(2, h.state.allMarketRepos.size)
            assertEquals(3, h.state.sourceErrors.size)

            h.ops.source = { _, _ -> emptyList() }
            h.vm.loadRegistry()
            assertTrue(h.state.allMarketRepos.isEmpty())
            assertTrue(h.state.sourceErrors.isEmpty())
        }
    }

    @Test
    fun `public cache and empty successful sources obey freshness`() {
        Harness(plugins = listOf("one/registry"), components = listOf("parts/registry")).use { first ->
            first.ops.source = { source, _ -> if (source.kind == MarketSourceKind.Plugin) listOf(repo("school/one")) else emptyList() }
            first.vm.loadRegistry()
            val prefs = first.prefs.value
            Harness(initialPrefs = prefs, accounts = MutableStateFlow(null)).use { restored ->
                assertEquals(listOf("school/one"), restored.state.marketRepos.map { it.fullName })
                restored.vm.refreshIfStale(100_000)
                assertTrue(restored.ops.sourceCalls.isEmpty())
                restored.now += 100_001
                restored.vm.refreshIfStale(100_000)
                assertEquals(2, restored.ops.sourceCalls.size)
            }
        }
    }

    @Test
    fun `manual refresh bypasses old release cache while automatic loads retain TTL`() {
        Harness().use { h ->
            val old = asset("1.0.0")
            val latest = asset("1.2.0")
            h.ops.source = { _, _ -> listOf(repo("school/one", release = old)) }
            h.ops.release = { _, fresh, _ -> if (fresh) latest else old }

            h.vm.refreshIfStale(100_000)
            assertEquals(old, h.state.marketRepos.single().latestRelease)
            h.vm.refreshIfStale(100_000)
            assertEquals(1, h.ops.releaseCalls.size)
            h.now += 100_001
            h.vm.refreshIfStale(100_000)
            assertEquals(listOf(false, false), h.ops.releaseCalls.map { it.second })

            h.vm.loadRegistry()
            assertEquals(latest, h.state.marketRepos.single().latestRelease)
            h.vm.loadRegistry("ignored/legacy")
            assertEquals(listOf(false, false, true, true), h.ops.releaseCalls.map { it.second })
            assertEquals(latest, h.state.marketRepos.single().latestRelease)
        }
    }

    @Test
    fun `older registry and release manifest cannot replace a known update`() {
        val gate = CompletableDeferred<Unit>()
        Harness().use { h ->
            val old = asset("1.0.0")
            val latest = asset("v1.2.0")
            h.ops.source = { _, _ -> listOf(repo("school/one", release = old)) }
            h.ops.release = { _, _, _ -> latest }
            h.vm.loadRegistry()

            h.ops.release = { _, _, _ -> gate.await(); old }
            h.vm.loadRegistry()
            assertTrue(h.state.isRefreshingReleases)
            assertEquals(latest, h.state.marketRepos.single().latestRelease)
            gate.complete(Unit)
            assertEquals(latest, h.state.marketRepos.single().latestRelease)
            assertTrue(h.prefs.value.pluginMarketCacheJson.contains("\"tagName\":\"v1.2.0\""))

            // 同一语义版本允许采用刚查到的附件地址，v 前缀不应阻止更新。
            val sameVersion = asset("1.2.0").copy(assetName = "latest.zip", downloadUrl = "https://github.com/school/one/releases/latest/download/latest.zip")
            h.ops.release = { _, _, _ -> sameVersion }
            h.vm.loadRegistry()
            assertEquals(sameVersion, h.state.marketRepos.single().latestRelease)
        }
    }

    @Test
    fun `install from restored old snapshot fetches latest and persists new source asset`() {
        Harness().use { first ->
            first.ops.source = { _, _ -> listOf(repo("school/one", release = asset("1.0.0"))) }
            first.vm.loadRegistry()
            Harness(initialPrefs = first.prefs.value).use { h ->
                val cached = h.state.marketRepos.single()
                val latest = asset("1.2.0").copy(assetName = "latest.zip", sizeBytes = 12)
                h.ops.release = { _, fresh, _ -> if (fresh) latest else asset("1.0.0") }
                h.vm.installFromGitHub(cached)

                assertEquals(Triple("school/one", true, false), h.ops.releaseCalls.single())
                assertEquals(latest, h.ops.downloaded?.second)
                assertEquals(latest.downloadUrl, h.state.installPreviewOrigin?.downloadUrl)
                assertEquals(latest, h.state.marketRepos.single().latestRelease)
                assertTrue(h.ops.sourceCalls.isEmpty())
                Harness(initialPrefs = h.prefs.value).use { restored ->
                    assertEquals(latest, restored.state.marketRepos.single().latestRelease)
                }
            }
        }
    }

    @Test
    fun `failed or empty fresh lookup falls back to known public mirror asset`() {
        listOf(false, true).forEach { throws ->
            Harness().use { h ->
                val old = asset("1.0.0")
                h.ops.source = { _, _ -> listOf(repo("school/one", release = old)) }
                h.vm.loadRegistry()
                h.ops.release = { _, _, _ -> if (throws) throw IOException("offline") else null }
                h.vm.installFromGitHub(h.state.marketRepos.single())

                assertTrue(h.ops.releaseCalls.last().second)
                assertEquals(old, h.ops.downloaded?.second)
                assertEquals(old, h.state.marketRepos.single().latestRelease)
                assertNotNull(h.state.installPreview)
                assertEquals(old.downloadUrl, h.state.installPreviewOrigin?.downloadUrl)
                assertFalse(h.state.installPreviewOrigin?.viaAccount == true)
            }
        }
    }

    @Test
    fun `install keeps newer snapshot when clicked item or fresh manifest is older`() {
        Harness().use { h ->
            val old = asset("1.0.0")
            val latest = asset("v1.2.0")
            h.ops.source = { _, _ -> listOf(repo("school/one", release = old)) }
            h.ops.release = { _, _, _ -> latest }
            h.vm.loadRegistry()
            val clicked = h.state.marketRepos.single().copy(latestRelease = old)

            h.ops.release = { _, _, _ -> old }
            h.vm.installFromGitHub(clicked)
            assertEquals(latest, h.ops.downloaded?.second)
            assertEquals(latest, h.state.marketRepos.single().latestRelease)

            h.ops.release = { _, _, _ -> throw IOException("offline") }
            h.vm.installFromGitHub(clicked)
            assertEquals(latest, h.ops.downloaded?.second)
        }
    }

    @Test
    fun `private install never falls back to public asset when fresh API lookup fails`() {
        listOf(false, true).forEach { throws ->
            Harness(plugins = emptyList()).use { h ->
                h.vm.refreshAccount("alice:1")
                h.ops.release = { _, _, _ -> if (throws) throw IOException("offline") else asset("1.2.0") }
                h.vm.installFromGitHub(repo("school/one", viaAccount = true, release = asset("1.0.0")))

                assertEquals(Triple("school/one", true, true), h.ops.releaseCalls.single())
                assertNull(h.ops.downloaded)
                assertNull(h.state.installPreview)
                assertTrue(h.state.status is PluginMarketStatus.ReleaseAssetMissing)
            }
        }
    }

    @Test
    fun `private install refreshes even a known API asset and never persists credentials path`() {
        Harness(accounts = MutableStateFlow("alice:1")).use { h ->
            val old = asset("1.0.0", api = true)
            val latest = asset("1.2.0", api = true).copy(downloadUrl = "https://api.github.com/repos/school/one/releases/assets/2")
            h.ops.source = { _, _ -> listOf(repo("school/one", viaAccount = true, release = old)) }
            h.vm.loadRegistry()
            h.ops.release = { _, _, _ -> latest }
            h.vm.installFromGitHub(h.state.marketRepos.single())

            assertEquals(Triple("school/one", true, true), h.ops.releaseCalls.last())
            assertEquals(latest, h.ops.downloaded?.second)
            assertEquals(latest, h.state.marketRepos.single().latestRelease)
            assertTrue(h.state.installPreviewOrigin?.viaAccount == true)
            assertFalse(h.prefs.value.pluginMarketCacheJson.contains("api.github.com"))
            assertFalse(h.prefs.value.pluginMarketCacheJson.contains("school/one"))
        }
    }

    @Test
    fun `dismiss and logout reject late fresh install lookup without fallback or snapshot write`() {
        listOf(false, true).forEach { logout ->
            val gate = CompletableDeferred<Unit>()
            Harness(accounts = MutableStateFlow("alice:1")).use { h ->
                val old = asset("1.0.0", api = logout)
                h.ops.source = { _, _ -> listOf(repo("school/one", viaAccount = logout, release = old)) }
                h.vm.loadRegistry()
                val clicked = h.state.marketRepos.single()
                h.ops.release = { _, _, _ -> withContext(NonCancellable) { gate.await(); asset("1.2.0", api = logout) } }
                h.vm.installFromGitHub(clicked)
                assertTrue(h.state.isLoading)
                if (logout) h.vm.refreshAccount(null) else h.vm.dismissInstallPreview()
                gate.complete(Unit)

                assertNull(h.ops.downloaded)
                assertNull(h.state.installPreview)
                assertNull(h.state.installPreviewOrigin)
                assertFalse(h.state.isLoading)
                assertTrue(h.state.marketRepos.none { it.latestRelease?.tagName == "1.2.0" })
                assertFalse(h.prefs.value.pluginMarketCacheJson.contains("1.2.0"))
            }
        }
    }

    @Test
    fun `cancelled fresh install lookup cannot download a known fallback`() {
        Harness().use { h ->
            h.ops.release = { _, _, _ -> throw CancellationException("cancelled") }
            h.vm.installFromGitHub(repo("school/one", release = asset("1.0.0")))
            assertNull(h.ops.downloaded)
            assertNull(h.state.installPreview)
            assertFalse(h.state.status is PluginMarketStatus.DownloadFailed)
        }
    }

    @Test
    fun `private cache is never persisted or restored and login logout reloads`() {
        val accounts = MutableStateFlow<String?>("alice:1")
        Harness(plugins = listOf("me/registry"), components = emptyList(), accounts = accounts).use { h ->
            h.ops.source = { _, _ -> listOf(repo("school/public"), repo("school/secret", viaAccount = true)) }
            h.ops.release = { slug, _, _ -> if (slug.endsWith("secret")) asset(api = true) else null }
            h.vm.loadRegistry()
            assertEquals(2, h.state.marketRepos.size)
            assertFalse(h.prefs.value.pluginMarketCacheJson.contains("secret"))
            assertFalse(h.prefs.value.pluginMarketCacheJson.contains("api.github.com"))
            Harness(initialPrefs = h.prefs.value).use { restored ->
                assertEquals(listOf("school/public"), restored.state.marketRepos.map { it.fullName })
                restored.vm.refreshIfStale(100_000)
                assertEquals(1, restored.ops.sourceCalls.size)
            }

            accounts.value = null
            assertEquals(listOf("school/public"), h.state.marketRepos.map { it.fullName })
            assertTrue(h.state.latestReleases.isEmpty())
            assertNull(h.state.pendingUpgrade)
            assertFalse(h.prefs.value.pluginMarketCacheJson.contains("secret"))
            accounts.value = "bob:2"
            assertEquals(2, h.state.marketRepos.size)
            assertEquals(3, h.ops.clearCount)
            assertEquals(3, h.ops.sourceCalls.size)
        }
    }

    @Test
    fun `old disk cache containing account data is sanitized immediately`() {
        val cached = Json.encodeToString(listOf(repo("school/secret", viaAccount = true), repo("school/api", release = asset(api = true)), repo("school/public")))
        Harness(initialPrefs = prefs(listOf("one/registry"), emptyList()).copy(pluginMarketCacheJson = cached, pluginMarketCachedRegistry = "one/registry")).use { h ->
            assertEquals(listOf("school/public"), h.state.marketRepos.map { it.fullName })
            assertFalse(h.prefs.value.pluginMarketCacheJson.contains("secret"))
            assertFalse(h.prefs.value.pluginMarketCacheJson.contains("school/api"))
            h.vm.refreshIfStale(100_000)
            assertEquals(1, h.ops.sourceCalls.size)
        }
    }

    @Test
    fun `source switches reject late work and cache writes even at identical clock times`() {
        val gate = CompletableDeferred<Unit>()
        Harness(plugins = listOf("old/registry"), components = emptyList()).use { h ->
            h.ops.source = { source, _ ->
                if (source.source == "old/registry") withContext(NonCancellable) { gate.await(); listOf(repo("old/secret")) }
                else listOf(repo("new/item"))
            }
            h.vm.loadRegistry()
            h.vm.setSources(listOf("new/registry"), emptyList())
            gate.complete(Unit)

            assertEquals(listOf("new/item"), h.state.marketRepos.map { it.fullName })
            assertFalse(h.prefs.value.pluginMarketCacheJson.contains("old/"))
            assertFalse(h.state.isLoading)
            assertFalse(h.state.isRefreshingReleases)
        }
    }

    @Test
    fun `late release from previous refresh cannot overwrite newer result`() {
        val gate = CompletableDeferred<Unit>()
        Harness(plugins = listOf("one/registry"), components = emptyList()).use { h ->
            h.ops.source = { _, _ -> listOf(repo("school/one")) }
            var calls = 0
            h.ops.release = { _, _, _ -> if (++calls == 1) withContext(NonCancellable) { gate.await(); asset("1") } else asset("2") }
            h.vm.loadRegistry()
            h.vm.loadRegistry()
            gate.complete(Unit)
            assertEquals("2", h.state.marketRepos.single().latestRelease?.tagName)
            assertTrue(h.prefs.value.pluginMarketCacheJson.contains("\"tagName\":\"2\""))
        }
    }

    @Test
    fun `preferences changes reload and string compatibility never resurrects deleted sources`() {
        Harness(plugins = listOf("one/registry"), components = emptyList()).use { h ->
            h.ops.source = { source, _ -> listOf(repo("${source.source.substringBefore('/')}/item")) }
            h.vm.loadRegistry("ignored/legacy")
            assertEquals("one/item", h.state.marketRepos.single().fullName)
            h.prefs.value = h.prefs.value.copy(pluginSources = emptyList(), componentSources = listOf("parts/registry"))
            assertTrue(h.state.marketRepos.isEmpty())
            assertEquals("parts/item", h.state.componentRepos.single().fullName)
            h.vm.loadRegistry("one/registry")
            assertTrue(h.state.marketRepos.isEmpty())
            h.prefs.value = h.prefs.value.copy(componentSources = emptyList())
            h.vm.refreshIfStale("one/registry", 100_000)
            assertTrue(h.state.allMarketRepos.isEmpty())
            assertEquals(PluginMarketStatus.RegistryNotConfigured, h.state.status)
        }
    }

    @Test
    fun `hydration waits for loaded preferences without fetching default sources`() {
        Harness(initialPrefs = prefs(listOf("one/registry"), emptyList()).copy(loaded = false)).use { h ->
            h.vm.setSources(listOf("chosen/registry"), emptyList())
            h.vm.refreshIfStale(100_000)
            assertTrue(h.ops.sourceCalls.isEmpty())
            h.prefs.value = h.prefs.value.copy(loaded = true, pluginSources = listOf("chosen/registry"))
            assertEquals(listOf("chosen/registry"), h.ops.sourceCalls.map { it.first.source })
        }
    }

    @Test
    fun `private install resolves account asset and upgrade preserves installed registry source`() {
        Harness(plugins = emptyList(), components = emptyList()).use { h ->
            h.vm.refreshAccount("alice:1")
            h.ops.release = { _, _, _ -> asset(api = true) }
            val installed = record(registrySource = "original/registry")
            h.vm.upgradeThenSync(installed, repo("school/one", viaAccount = true, release = asset(), registrySource = "new/registry"))
            assertTrue(h.ops.releaseCalls.single().third)
            assertEquals(asset(api = true), h.ops.downloaded?.second)
            assertTrue(h.state.installPreviewOrigin?.viaAccount == true)
            assertEquals("original/registry", h.state.installPreviewOrigin?.registrySource)
            h.vm.confirmInstall()
            assertEquals("school/one" to "original/registry", h.ops.installedOrigin)
            assertEquals(installed.installKey, h.state.readyToSyncKey)
        }
    }

    @Test
    fun `installed custom source uses account API before registry is fetched after restart`() {
        Harness(plugins = listOf("private/registry"), components = emptyList(), accounts = MutableStateFlow("alice:1")).use { h ->
            val installed = record(registrySource = "private/registry")
            h.ops.installedPluginsFlow.value = listOf(installed)
            h.ops.release = { _, _, _ -> asset(api = true) }
            h.vm.refreshInstalledPluginVersions()
            assertTrue(h.ops.sourceCalls.isEmpty())
            assertTrue(h.ops.releaseCalls.single().third)
            h.vm.syncWithUpdateCheck(installed)
            assertTrue(h.ops.releaseCalls.all { it.third })
            assertTrue(h.state.pendingUpgrade?.repo?.viaAccount == true)
            h.vm.startPendingUpgrade()
            assertEquals("private/registry", h.state.installPreviewOrigin?.registrySource)
        }
    }

    @Test
    fun `success is visible while another source diagnosis is still waiting`() {
        val gate = CompletableDeferred<Unit>()
        Harness(plugins = listOf("good/registry", "bad/registry"), components = emptyList()).use { h ->
            h.ops.source = { source, _ -> if (source.source == "bad/registry") throw IOException("offline") else listOf(repo("good/item")) }
            h.ops.check = { _, _ -> gate.await(); MarketSourceCheck.NotFound }
            h.vm.loadRegistry()
            assertEquals("good/item", h.state.marketRepos.single().fullName)
            assertNull(h.state.sourceErrors.single().check)
            gate.complete(Unit)
            assertEquals(MarketSourceCheck.NotFound, h.state.sourceErrors.single().check)
            assertFalse(h.state.isLoading)
        }
    }

    @Test
    fun `logout cancels private downloads and prevents late preview or install`() {
        val gate = CompletableDeferred<Unit>()
        Harness(plugins = emptyList(), components = emptyList()).use { h ->
            h.vm.refreshAccount("alice:1")
            h.ops.download = { _, _, _ -> withContext(NonCancellable) { gate.await(); byteArrayOf(1) } }
            h.vm.installFromGitHub(repo("school/one", viaAccount = true, release = asset(api = true)))
            h.vm.refreshAccount(null)
            gate.complete(Unit)
            assertNull(h.state.installPreview)
            assertNull(h.state.installPreviewOrigin)
            h.vm.confirmInstall()
            assertNull(h.ops.installedOrigin)
            assertFalse(h.state.isLoading)
        }
    }

    @Test
    fun `download progress is real and dismiss logout and completion reject old callbacks`() {
        Harness(plugins = emptyList(), components = emptyList()).use { h ->
            val callbacks = mutableListOf<(Long, Long) -> Unit>()
            val downloads = mutableListOf<CompletableDeferred<ByteArray>>()
            h.ops.download = { _, _, progress ->
                callbacks += progress
                CompletableDeferred<ByteArray>().also { downloads += it }.await()
            }
            val selected = repo("school/one", description = "summary", release = asset())
            h.vm.installFromGitHub(selected)
            assertNull(h.state.downloadProgress)
            callbacks[0](2, -1)
            assertEquals(PluginDownloadProgress(2, null), h.state.downloadProgress)
            callbacks[0](4, 10)
            assertEquals(PluginDownloadProgress(4, 10), h.state.downloadProgress)

            h.vm.dismissInstallPreview()
            assertFalse(h.state.isLoading)
            assertNull(h.state.status)
            assertNull(h.state.downloadProgress)
            callbacks[0](8, 10)
            assertNull(h.state.downloadProgress)

            h.vm.installFromGitHub(selected)
            callbacks[1](1, -1)
            callbacks[0](9, 10)
            assertEquals(PluginDownloadProgress(1, null), h.state.downloadProgress)
            h.vm.refreshAccount(null)
            callbacks[1](2, 10)
            assertNull(h.state.downloadProgress)
            assertFalse(h.state.isLoading)

            h.vm.installFromGitHub(selected)
            callbacks[2](3, 8)
            downloads[2].complete(byteArrayOf(1, 2, 3))
            assertNull(h.state.downloadProgress)
            assertEquals(3L, h.state.packageSizeBytes)
            assertEquals(3L, h.state.installPreviewOrigin?.sizeBytes)
            assertEquals("one", h.state.installPreviewOrigin?.displayName)
            assertEquals("summary", h.state.installPreviewOrigin?.description)
            callbacks[2](99, 100)
            assertNull(h.state.downloadProgress)
            h.vm.confirmInstall()
            assertNull(h.state.packageSizeBytes)
            assertNull(h.state.downloadProgress)

            h.ops.download = { _, _, progress -> progress(1, -1); throw IOException("offline") }
            h.vm.installFromGitHub(selected)
            assertNull(h.state.downloadProgress)
            assertFalse(h.state.isLoading)
            assertTrue(h.state.status is PluginMarketStatus.DownloadFailed)
        }
    }

    @Test
    fun `local preview reports actual zip byte size and clears it when dismissed`() {
        Harness(plugins = emptyList(), components = emptyList()).use { h ->
            h.vm.previewLocalPackage(byteArrayOf(1, 2, 3, 4))
            assertNotNull(h.state.installPreview)
            assertNull(h.state.installPreviewOrigin)
            assertEquals(4L, h.state.packageSizeBytes)
            assertNull(h.state.downloadProgress)
            h.vm.dismissInstallPreview()
            assertNull(h.state.packageSizeBytes)
            assertNull(h.state.installPreview)
        }
    }

    @Test
    fun `installed update errors preserve fallback and account change rejects stale private versions`() {
        val accounts = MutableStateFlow<String?>("alice:1")
        val gate = CompletableDeferred<Unit>()
        Harness(plugins = listOf("one/registry"), components = emptyList(), accounts = accounts).use { h ->
            val installed = record()
            h.ops.installedPluginsFlow.value = listOf(installed)
            h.ops.source = { _, _ -> listOf(repo("school/one", release = asset("2"))) }
            h.vm.loadRegistry()
            h.ops.release = { _, _, _ -> throw IOException("offline") }
            h.vm.syncWithUpdateCheck(installed)
            assertEquals("2", h.state.pendingUpgrade?.latestVersion)
            h.vm.dismissPendingUpgrade()

            h.ops.release = { _, _, _ -> withContext(NonCancellable) { gate.await(); asset("3", api = true) } }
            h.vm.refreshInstalledPluginVersions()
            accounts.value = null
            gate.complete(Unit)
            assertTrue(h.state.latestReleases.isEmpty())
            assertNull(h.state.pendingUpgrade)
            assertNull(h.state.checkingUpdateKey)
        }
    }

    @Test
    fun `source version download parsing and removal cancellation are never errors`() {
        val cancelled = CancellationException("cancelled")
        assertSame(cancelled, try { marketAttempt<Unit> { throw cancelled }; null } catch (e: CancellationException) { e })
        assertSame(cancelled, try { pluginPackageReadFailure(cancelled); null } catch (e: CancellationException) { e })
        Harness(plugins = listOf("one/registry"), components = emptyList()).use { h ->
            h.ops.source = { _, _ -> throw cancelled }
            h.vm.loadRegistry()
            assertTrue(h.state.sourceErrors.isEmpty())
            assertTrue(h.ops.checkCalls.isEmpty())
            assertFalse(h.state.status is PluginMarketStatus.MarketLoadFailed)
            h.ops.release = { _, _, _ -> throw cancelled }
            h.vm.syncWithUpdateCheck(record())
            assertNull(h.state.readyToSyncKey)
            assertNull(h.state.pendingUpgrade)
            h.ops.release = { _, _, _ -> null }
            h.ops.download = { _, _, _ -> throw cancelled }
            h.vm.installFromGitHub(repo("school/one", release = asset()))
            assertFalse(h.state.status is PluginMarketStatus.DownloadFailed)
            h.ops.preview = { _, _ -> throw cancelled }
            h.vm.previewLocalPackage(byteArrayOf(1))
            assertFalse(h.state.status is PluginMarketStatus.ParsePackageFailed)
            h.ops.remove = { throw cancelled }
            h.vm.removePlugin("one")
            assertFalse(h.state.status is PluginMarketStatus.RemoveFailed)
        }
    }

    private class Harness(
        plugins: List<String> = listOf("one/registry"),
        components: List<String> = emptyList(),
        initialPrefs: UserPreferences = prefs(plugins, components),
        accounts: MutableStateFlow<String?>? = null,
    ) : AutoCloseable {
        val prefs = MutableStateFlow(initialPrefs)
        val ops = FakeOperations()
        var now = 5_000_000L
        private val uncaught = mutableListOf<Throwable>()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, error -> uncaught += error })
        private val preferences = Proxy.newProxyInstance(UserPreferencesRepository::class.java.classLoader, arrayOf(UserPreferencesRepository::class.java)) { _, method, args ->
            when (method.name) {
                "getPreferencesFlow" -> prefs
                "setPluginMarketCache" -> {
                    prefs.value = prefs.value.copy(pluginMarketCacheJson = args[0] as String, pluginMarketCachedAtMillis = args[1] as Long, pluginMarketCachedRegistry = args[2] as String)
                    Unit
                }
                "setPluginEnabled" -> Unit
                else -> error("Unexpected preference operation: ${method.name}")
            }
        } as UserPreferencesRepository
        val vm = PluginMarketViewModel(ops, preferences, nowMillis = { now }, scope = scope, accountKeyFlow = accounts)
        val state get() = vm.uiState.value
        override fun close() {
            scope.cancel()
            assertEquals("Uncaught coroutine errors: $uncaught", emptyList<Throwable>(), uncaught)
        }
    }

    private class FakeOperations : PluginMarketOperations {
        override val installedPluginsFlow = MutableStateFlow(emptyList<InstalledPluginRecord>())
        val sourceCalls = mutableListOf<Pair<PluginMarketSource, Boolean>>()
        val checkCalls = mutableListOf<PluginMarketSource>()
        val releaseCalls = mutableListOf<Triple<String, Boolean, Boolean>>()
        var clearCount = 0
        var downloaded: Pair<GitHubRepoSummary, GitHubReleaseAsset>? = null
        var installedOrigin: Pair<String?, String?>? = null
        var source: suspend (PluginMarketSource, Boolean) -> List<GitHubRepoSummary> = { _, _ -> emptyList() }
        var check: suspend (PluginMarketSource, Boolean) -> MarketSourceCheck = { _, _ -> MarketSourceCheck.Unreachable("offline") }
        var release: suspend (String, Boolean, Boolean) -> GitHubReleaseAsset? = { _, _, _ -> null }
        var download: suspend (GitHubRepoSummary, GitHubReleaseAsset, (Long, Long) -> Unit) -> ByteArray = { _, _, _ -> byteArrayOf(1) }
        var preview: suspend (ByteArray, PluginInstallSource) -> PluginInstallPreview = { _, source ->
            PluginInstallPreview(PluginManifest("one", "one", version = "2", versionCode = 2, entry = "main.js"), true, source)
        }
        var remove: suspend (String) -> Unit = {}
        override suspend fun fetchSource(source: PluginMarketSource, preferAccount: Boolean): List<GitHubRepoSummary> {
            sourceCalls += source to preferAccount
            return this.source(source, preferAccount)
        }
        override suspend fun checkSource(source: PluginMarketSource, preferAccount: Boolean): MarketSourceCheck {
            checkCalls += source
            return check(source, preferAccount)
        }
        override suspend fun fetchLatestReleaseAsset(slug: String, fresh: Boolean, viaAccount: Boolean): GitHubReleaseAsset? {
            releaseCalls += Triple(slug, fresh, viaAccount)
            return release(slug, fresh, viaAccount)
        }
        override fun clearAccountCache() { clearCount++ }
        override suspend fun downloadPackage(repo: GitHubRepoSummary, asset: GitHubReleaseAsset, onProgress: (Long, Long) -> Unit): ByteArray {
            downloaded = repo to asset
            return download(repo, asset, onProgress)
        }
        override suspend fun previewPackage(bytes: ByteArray, source: PluginInstallSource) = preview(bytes, source)
        override suspend fun installPackage(bytes: ByteArray, source: PluginInstallSource, sourceRepo: String?, registrySource: String?): PluginInstallResult {
            installedOrigin = sourceRepo to registrySource
            return PluginInstallResult.Success(record(registrySource))
        }
        override suspend fun removePlugin(pluginKey: String) = remove(pluginKey)
    }

    companion object {
        private fun prefs(plugins: List<String>, components: List<String>) = UserPreferences(pluginSources = plugins, componentSources = components, loaded = true)
        private fun asset(version: String = "2", api: Boolean = false) = GitHubReleaseAsset(version, "one.zip", if (api) "https://api.github.com/repos/school/one/releases/assets/1" else "https://github.com/school/one/releases/download/v$version/one.zip", 1)
        private fun repo(fullName: String, description: String = "", kind: String = "", viaAccount: Boolean = false, release: GitHubReleaseAsset? = null, registrySource: String = "") = GitHubRepoSummary(
            fullName, fullName.substringBefore('/'), fullName.substringAfter('/'), description, 0, "", "https://github.com/$fullName",
            ownerHtmlUrl = "https://github.com/${fullName.substringBefore('/')}", isFresh = false,
            latestRelease = release, kind = kind, viaAccount = viaAccount, registrySource = registrySource,
        )
        private fun record(registrySource: String? = null) = InstalledPluginRecord(
            pluginId = "one", name = "one", version = "1", versionCode = 1, storagePath = "/tmp/one",
            installedAt = "2026-10-04T00:00:00Z", source = PluginInstallSource.Remote,
            sourceRepo = "school/one", registrySource = registrySource,
        )
    }
}
