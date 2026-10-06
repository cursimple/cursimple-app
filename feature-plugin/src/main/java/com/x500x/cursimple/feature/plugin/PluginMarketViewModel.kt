package com.x500x.cursimple.feature.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.x500x.cursimple.core.data.UserPreferences
import com.x500x.cursimple.core.data.UserPreferencesRepository
import com.x500x.cursimple.core.plugin.PluginManager
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.PluginInstallPreview
import com.x500x.cursimple.core.plugin.install.PluginInstallResult
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import com.x500x.cursimple.core.plugin.market.github.DefaultMarketSources
import com.x500x.cursimple.core.plugin.market.github.GitHubApiClient
import com.x500x.cursimple.core.plugin.market.github.GitHubRegistryRepository
import com.x500x.cursimple.core.plugin.market.github.GitHubReleaseAsset
import com.x500x.cursimple.core.plugin.market.github.GitHubRepoAddress
import com.x500x.cursimple.core.plugin.market.github.GitHubRepoSummary
import com.x500x.cursimple.core.plugin.market.github.MarketSourceCheck
import com.x500x.cursimple.core.plugin.market.github.MarketSourceKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class PluginInstallOrigin(
    val repoSlug: String,
    val downloadUrl: String,
    val viaAccount: Boolean = false,
    val registrySource: String? = null,
    val displayName: String? = null,
    val description: String? = null,
    val sizeBytes: Long? = null,
)

data class PluginMarketSource(val source: String, val kind: MarketSourceKind)

data class PluginMarketUiState(
    /** Schedule-only list for import; extensions use [componentRepos]. */
    val marketRepos: List<GitHubRepoSummary> = emptyList(),
    val componentRepos: List<GitHubRepoSummary> = emptyList(),
    /** Merge and deduplicate in configured source order. */
    val allMarketRepos: List<GitHubRepoSummary> = emptyList(),
    val sources: List<PluginMarketSource> = emptyList(),
    val sourceErrors: List<PluginMarketSourceError> = emptyList(),
    /** Cache write failure does not discard loaded sources. */
    val cacheError: Throwable? = null,
    val installedPlugins: List<InstalledPluginRecord> = emptyList(),
    val installPreview: PluginInstallPreview? = null,
    val installPreviewOrigin: PluginInstallOrigin? = null,
    val packageSizeBytes: Long? = null,
    val downloadProgress: PluginDownloadProgress? = null,
    val isLoading: Boolean = false,
    val installingRepo: String? = null,
    val status: PluginMarketStatus? = null,
    val lastLoadedRegistry: String? = null,
    val lastLoadedAtMillis: Long = 0L,
    val checkingUpdateKey: String? = null,
    val upgradingKey: String? = null,
    val isRefreshingReleases: Boolean = false,
    /**
     * Hide cached versions and disable market installation until the current check finishes.
     */
    val versionsChecking: Boolean = false,
    val pendingUpgrade: PendingPluginUpgrade? = null,
    val readyToSyncKey: String? = null,
    /**
     * Fresh installed-item releases keyed by lowercase repository, independent of registry
     * aggregation delay.
     */
    val latestReleases: Map<String, GitHubReleaseAsset> = emptyMap(),
    val autoCheckUpdates: Boolean = true,
    val showUpdateBadge: Boolean = true,
    val updateIntervalHours: Int = 6,
    val checkingInstalledUpdates: Boolean = false,
    val updateCheckCompleted: Int = 0,
    val updateCheckTotal: Int = 0,
    val updateCheckUnconfirmed: Int = 0,
    val lastUpdateCheckAtMillis: Long = 0L,
)

/** Installed [record] and latest [repo] release for pre-import upgrading. */
data class PendingPluginUpgrade(
    val record: InstalledPluginRecord,
    val repo: GitHubRepoSummary,
) {
    val latestVersion: String get() = repo.latestRelease?.tagName.orEmpty()
}

class PluginMarketViewModel internal constructor(
    private val operations: PluginMarketOperations,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    private val nowMillis: () -> Long = System::currentTimeMillis,
    scope: CoroutineScope? = null,
    accountKeyFlow: Flow<String?>? = null,
) : ViewModel() {
    constructor(
        pluginManager: PluginManager,
        gitHubRegistryRepository: GitHubRegistryRepository,
        userPreferencesRepository: UserPreferencesRepository,
        json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
        nowMillis: () -> Long = System::currentTimeMillis,
        accountKeyFlow: Flow<String?>? = null,
    ) : this(
        DefaultPluginMarketOperations(pluginManager, gitHubRegistryRepository),
        userPreferencesRepository, json, nowMillis, accountKeyFlow = accountKeyFlow,
    )

    private val marketScope = scope ?: viewModelScope
    private val _uiState = MutableStateFlow(PluginMarketUiState(versionsChecking = true))
    val uiState: StateFlow<PluginMarketUiState> = _uiState

    private var pendingBytes: ByteArray? = null
    private var pendingSource: PluginInstallSource? = null
    private var syncAfterInstallKey: String? = null

    private val hydrated = CompletableDeferred<Unit>()
    private var configuredSources = emptyList<PluginMarketSource>()
    private var requestedSources: List<PluginMarketSource>? = null
    private val sourceResults = linkedMapOf<PluginMarketSource, MarketSourceSnapshot>()
    private val sourceFailures = mutableMapOf<PluginMarketSource, PluginMarketSourceError>()
    private val cacheMutex = Mutex()
    private var contextRevision = 0L
    private var loadRevision = 0L
    private var releasesVerifiedAt = 0L
    /**
     * Highest version seen, including cache, for comparison only; never use it as fresh display
     * data.
     */
    private val seenReleases = mutableMapOf<String, GitHubReleaseAsset>()
    private var activeLoadFresh = false
    private var loadJob: Job? = null
    private var versionsJob: Job? = null
    private var updateCheckJob: Job? = null
    private var remoteInstallJob: Job? = null
    private var remoteInstallRevision = 0L
    private var activeDownloadRequest: Long? = null
    private var activated = false
    private var accountObserved = false
    private var accountKey: String? = null
    private var lastInstalledVersionCheckAt = 0L
    private var installedCheckFingerprint = ""
    private var lastInstalledVersionFailureAt = 0L

    init {
        marketScope.launch {
            operations.installedPluginsFlow.collect { installed ->
                _uiState.update {
                    it.copy(installedPlugins = installed.sortedBy { record -> record.name })
                }
            }
        }
        marketScope.launch {
            var previousSources: List<PluginMarketSource>? = null
            userPreferencesRepository.preferencesFlow.collect { prefs ->
                if (!prefs.loaded) return@collect
                val wasAuto = _uiState.value.autoCheckUpdates
                _uiState.update { it.copy(autoCheckUpdates = prefs.pluginAutoUpdateCheckEnabled,
                    showUpdateBadge = prefs.pluginUpdateBadgeEnabled, updateIntervalHours = prefs.pluginUpdateCheckIntervalHours) }
                if (!wasAuto && prefs.pluginAutoUpdateCheckEnabled && hydrated.isCompleted) refreshInstalledPluginVersions(automatic = true, force = true)
                val sources = marketSources(prefs.pluginSources, prefs.componentSources)
                if (!hydrated.isCompleted) {
                    configuredSources = requestedSources ?: sources
                    hydrateFromCache(prefs)
                    hydrated.complete(Unit)
                } else if (previousSources != sources) {
                    changeSources(sources)
                }
                previousSources = sources
            }
        }
        accountKeyFlow?.let { flow ->
            marketScope.launch { flow.collect { onAccountChanged(it) } }
        }
    }

    private fun hydrateFromCache(prefs: UserPreferences) {
        val raw = prefs.pluginMarketCacheJson
        val cache = marketAttempt { json.decodeFromString<MarketCache>(raw) }.getOrNull()
        if (cache != null && cache.version in 2..MARKET_CACHE_VERSION) {
            _uiState.update { it.copy(latestReleases = cache.installedReleases.filterValues { release -> !GitHubApiClient.isAssetApiUrl(release.downloadUrl) }, lastUpdateCheckAtMillis = cache.updateCheckedAtMillis) }
            lastInstalledVersionCheckAt = cache.updateCheckedAtMillis
            installedCheckFingerprint = cache.installedFingerprint
            lastInstalledVersionFailureAt = cache.updateFailedAtMillis
            cache.sources.forEach { cached ->
                val source = configuredSources.firstOrNull {
                    it.source == cached.source && it.kind.name == cached.kind
                } ?: return@forEach
                // Restore catalog identity without displaying cached release versions.
                val publicRepos = cached.repos.filterNot { it.requiresAccount }.onEach { rememberRelease(it.fullName, it.latestRelease) }.map { it.copy(latestRelease = null) }
                sourceResults[source] = MarketSourceSnapshot(
                    publicRepos,
                    cached.atMillis.takeIf { cached.complete && publicRepos.size == cached.repos.size } ?: 0L,
                )
            }
        } else {
            // Legacy cache belongs only to its original registry source.
            val source = configuredSources.firstOrNull {
                it.kind == MarketSourceKind.Plugin &&
                    it.source.equals(GitHubRepoAddress.parse(prefs.pluginMarketCachedRegistry), ignoreCase = true)
            }
            if (source != null) {
                val legacy = marketAttempt { json.decodeFromString<List<GitHubRepoSummary>>(raw) }
                    .getOrDefault(emptyList())
                sourceResults[source] = MarketSourceSnapshot(
                    legacy.filterNot { it.requiresAccount }.map { it.copy(registrySource = source.source, latestRelease = null) },
                    0L,
                )
            }
        }
        publishSources()
        // Rewrite restored caches to public-only content immediately.
        persistMarketCache()
    }

    fun setStatus(status: PluginMarketStatus?) {
        _uiState.update { it.copy(status = status) }
    }

    /**
     * Update ViewModel configuration only; callers persist preferences. Empty source lists
     * disable the corresponding market.
     */
    fun setSources(pluginSources: List<String>, componentSources: List<String>) {
        val sources = marketSources(pluginSources, componentSources)
        requestedSources = sources
        marketScope.launch {
            hydrated.await()
            changeSources(sources)
        }
    }

    private fun changeSources(sources: List<PluginMarketSource>) {
        if (sources == configuredSources) return
        invalidateRequests()
        configuredSources = sources
        sourceResults.keys.retainAll(sources.toSet())
        sourceFailures.clear()
        publishSources()
        persistMarketCache()
        if (activated) startMarketLoad()
    }

    /**
     * Identity invalidates requests without exposing tokens; token replacement can force
     * refresh.
     */
    fun refreshAccount(accountKey: String?) = changeAccount(accountKey, force = true)

    fun onAccountChanged(accountKey: String?) = changeAccount(accountKey, force = false)

    private fun changeAccount(key: String?, force: Boolean) {
        if (!force && accountObserved && accountKey == key) return
        val firstObservation = !accountObserved
        val initialPublicReleases = if (firstObservation && !force) _uiState.value.latestReleases.filterValues { !GitHubApiClient.isAssetApiUrl(it.downloadUrl) } else emptyMap()
        val initialCheckAt = lastInstalledVersionCheckAt
        accountObserved = true
        accountKey = key
        invalidateRequests()
        if (firstObservation && !force) {
            lastInstalledVersionCheckAt = initialCheckAt
            _uiState.update { it.copy(latestReleases = initialPublicReleases) }
        }
        operations.clearAccountCache()
        sourceResults.replaceAll { _, snapshot ->
            snapshot.copy(
                repos = snapshot.repos.filterNot { it.requiresAccount },
                atMillis = if (firstObservation && !force && snapshot.repos.none { it.requiresAccount }) snapshot.atMillis else 0L,
            )
        }
        sourceFailures.clear()
        publishSources()
        persistMarketCache()
        if (activated && hydrated.isCompleted) {
            startMarketLoad()
            refreshInstalledPluginVersions(automatic = true)
        }
    }

    private fun invalidateRequests() {
        contextRevision++
        loadRevision++
        loadJob?.cancel()
        versionsJob?.cancel()
        updateCheckJob?.cancel()
        remoteInstallJob?.cancel()
        activeDownloadRequest = null
        lastInstalledVersionCheckAt = 0L
        releasesVerifiedAt = 0L
        seenReleases.clear()
        if (pendingSource == PluginInstallSource.Remote) dismissInstallPreview()
        syncAfterInstallKey = null
        _uiState.update {
            it.copy(
                latestReleases = emptyMap(), pendingUpgrade = null, readyToSyncKey = null,
                checkingUpdateKey = null, upgradingKey = null, installingRepo = null, checkingInstalledUpdates = false,
                isLoading = false, isRefreshingReleases = false, versionsChecking = true, status = null,
                downloadProgress = null,
                packageSizeBytes = if (pendingSource == PluginInstallSource.Local) it.packageSizeBytes else null,
            )
        }
    }

    fun refreshIfStale(maxAgeMillis: Long) {
        marketScope.launch {
            hydrated.await()
            activated = true
            if (loadJob?.isActive == true) return@launch
            val now = nowMillis()
            val fresh = configuredSources.all { source ->
                sourceResults[source]?.let { it.atMillis > 0 && now - it.atMillis in 0 until maxAgeMillis } == true
            } && sourceFailures.isEmpty() &&
                releasesVerifiedAt > 0 && now - releasesVerifiedAt in 0 until maxAgeMillis
            if (!fresh) startMarketLoad()
        }
    }

    /**
     * Refresh startup and page-entry versions, clearing stale display data first. Deduplicate
     * active checks and successful checks within [ENTER_REFRESH_DEBOUNCE_MILLIS].
     */
    fun refreshOnEnter() {
        val now = nowMillis()
        val recentlyVerified = releasesVerifiedAt > 0 && now - releasesVerifiedAt in 0 until ENTER_REFRESH_DEBOUNCE_MILLIS
        val running = loadJob?.isActive == true && activeLoadFresh
        if (!recentlyVerified && !running && hydrated.isCompleted) _uiState.update { it.copy(versionsChecking = true) }
        marketScope.launch {
            hydrated.await()
            activated = true
            val at = nowMillis()
            val verified = releasesVerifiedAt > 0 && at - releasesVerifiedAt in 0 until ENTER_REFRESH_DEBOUNCE_MILLIS
            val inFlight = loadJob?.isActive == true && activeLoadFresh
            if (!verified && !inFlight) startMarketLoad(freshReleases = true)
        }
        refreshInstalledPluginVersions(automatic = true, force = true)
    }

    /** Legacy import compatibility must not restore user-removed default sources. */
    @Suppress("UNUSED_PARAMETER")
    fun refreshIfStale(registryRepo: String, maxAgeMillis: Long) = refreshIfStale(maxAgeMillis)

    @Suppress("UNUSED_PARAMETER")
    fun loadRegistry(registryRepo: String) = loadRegistry()

    fun loadRegistry() {
        marketScope.launch {
            hydrated.await()
            activated = true
            startMarketLoad(freshReleases = true)
        }
    }

    private fun startMarketLoad(freshReleases: Boolean = false) {
        loadJob?.cancel()
        activeLoadFresh = freshReleases
        if (freshReleases) {
            sourceResults.replaceAll { _, snapshot -> snapshot.copy(repos = snapshot.repos.map { it.copy(latestRelease = null) }) }
            publishSources()
        }
        val request = ++loadRevision
        val context = contextRevision
        val sources = configuredSources.toList()
        sourceFailures.clear()
        if (sources.isEmpty()) {
            publishSources()
            _uiState.update { it.copy(isLoading = false, versionsChecking = false, status = PluginMarketStatus.RegistryNotConfigured) }
            return
        }
        loadJob = marketScope.launch {
            _uiState.update { it.copy(isLoading = true, isRefreshingReleases = false, sourceErrors = emptyList(), status = PluginMarketStatus.LoadingMarket) }
            try {
                coroutineScope {
                    sources.map { source ->
                        async {
                            val preferAccount = !DefaultMarketSources.isDefault(source.source)
                            val result = marketAttempt { operations.fetchSource(source, preferAccount) }
                            currentCoroutineContext().ensureActive()
                            if (request != loadRevision || context != contextRevision) return@async
                            result.fold(
                                onSuccess = { repos ->
                                    val listed = repos.filter { accountKey != null || !accountObserved || !it.requiresAccount }
                                        .map { repo ->
                                            repo.copy(
                                                registrySource = source.source,
                                                kind = if (source.kind == MarketSourceKind.Component) "extension" else repo.kind,
                                                // Display only current fetch data; reject catalog versions below the known high-water mark and fetch them separately.
                                                latestRelease = repo.latestRelease
                                                    ?.takeUnless { seenReleases[repo.fullName.lowercase()]?.let { seen -> isNewerVersion(seen.tagName, it.tagName) } == true }
                                                    .also { rememberRelease(repo.fullName, repo.latestRelease) },
                                            )
                                        }
                                    sourceResults[source] = MarketSourceSnapshot(listed, nowMillis())
                                },
                                onFailure = { error ->
                                    sourceFailures[source] = PluginMarketSourceError(source.source, source.kind, error)
                                },
                            )
                            // Publish each successful source before diagnosing failed sources.
                            publishSources()
                            if (result.isFailure) {
                                val check = marketAttempt { operations.checkSource(source, preferAccount) }.getOrNull()
                                currentCoroutineContext().ensureActive()
                                if (request == loadRevision && context == contextRevision) {
                                    sourceFailures[source] = sourceFailures.getValue(source).copy(check = check)
                                    publishSources()
                                }
                            }
                        }
                    }.awaitAll()
                }
                currentCoroutineContext().ensureActive()
                if (request != loadRevision || context != contextRevision) return@launch
                val listed = _uiState.value.allMarketRepos
                _uiState.update { it.copy(isLoading = false, isRefreshingReleases = listed.isNotEmpty()) }
                persistMarketCache()
                coroutineScope {
                    val slots = Semaphore(4)
                    listed.filter { freshReleases || it.latestRelease == null }.map { repo ->
                        async { slots.withPermit {
                            val fetched = withTimeoutOrNull(UPDATE_CHECK_TIMEOUT_MILLIS) { fetchRelease(repo.fullName, fresh = freshReleases, viaAccount = repo.requiresAccount) }
                            currentCoroutineContext().ensureActive()
                            if (request != loadRevision || context != contextRevision || fetched == null) return@async
                            updateSourceRelease(repo, fetched)
                        } }
                    }.awaitAll()
                }
                if (request == loadRevision && context == contextRevision && sourceFailures.isEmpty()) releasesVerifiedAt = nowMillis()
                persistMarketCache()
            } finally {
                if (request == loadRevision && context == contextRevision) {
                    _uiState.update { it.copy(isLoading = false, isRefreshingReleases = false, versionsChecking = false) }
                }
            }
        }
    }

    private fun rememberRelease(fullName: String, asset: GitHubReleaseAsset?) {
        if (asset == null) return
        val key = fullName.lowercase()
        seenReleases[key] = preferLatestRelease(asset, seenReleases[key])!!
    }

    private fun updateSourceRelease(repo: GitHubRepoSummary, fetched: GitHubReleaseAsset) {
        sourceResults.replaceAll { _, snapshot ->
            snapshot.copy(repos = snapshot.repos.map {
                if (it.fullName.equals(repo.fullName, ignoreCase = true) && it.registrySource == repo.registrySource) {
                    val latest = preferLatestRelease(fetched, seenReleases[it.fullName.lowercase()] ?: it.latestRelease)!!
                    rememberRelease(it.fullName, latest)
                    it.copy(
                        latestRelease = latest,
                        viaAccount = it.viaAccount || GitHubApiClient.isAssetApiUrl(latest.downloadUrl),
                    )
                } else it
            })
        }
        publishSources()
    }

    private fun publishSources() {
        val merged = configuredSources.flatMap { sourceResults[it]?.repos.orEmpty() }
            .distinctBy { it.fullName.lowercase() }
        val errors = configuredSources.mapNotNull { sourceFailures[it] }
        val loadedAt = configuredSources.map { sourceResults[it]?.atMillis ?: 0L }.minOrNull() ?: 0L
        _uiState.update {
            it.copy(
                marketRepos = merged.filterNot { repo -> repo.isExtension },
                componentRepos = merged.filter { repo -> repo.isExtension },
                allMarketRepos = merged,
                sources = configuredSources,
                sourceErrors = errors,
                lastLoadedRegistry = configuredSources.singleOrNull { source -> source.kind == MarketSourceKind.Plugin }?.source,
                lastLoadedAtMillis = loadedAt.takeIf { errors.isEmpty() } ?: 0L,
                status = if (it.status != null && !it.status.isMarketStatus) it.status else when {
                    merged.isNotEmpty() -> PluginMarketStatus.MarketLoaded(merged.size)
                    errors.isNotEmpty() -> PluginMarketStatus.MarketLoadFailed(errors.first().error)
                    configuredSources.isEmpty() -> PluginMarketStatus.RegistryNotConfigured
                    else -> PluginMarketStatus.MarketEmpty
                },
            )
        }
    }

    private fun persistMarketCache() {
        val revision = contextRevision
        marketScope.launch {
            cacheMutex.withLock {
                if (revision != contextRevision) return@withLock
                val cache = MarketCache(sources = configuredSources.mapNotNull { source ->
                    sourceResults[source]?.let { snapshot ->
                        CachedMarketSource(
                            source.source, source.kind.name, snapshot.atMillis,
                            snapshot.repos.filterNot { it.requiresAccount },
                            complete = snapshot.repos.none { it.requiresAccount } && source !in sourceFailures,
                        )
                    }
                }, installedReleases = _uiState.value.latestReleases.filterValues { !GitHubApiClient.isAssetApiUrl(it.downloadUrl) },
                    updateCheckedAtMillis = lastInstalledVersionCheckAt, installedFingerprint = installedCheckFingerprint,
                    updateFailedAtMillis = lastInstalledVersionFailureAt)
                val result = marketAttempt {
                    userPreferencesRepository.setPluginMarketCache(
                        json.encodeToString(cache), _uiState.value.lastLoadedAtMillis,
                        configuredSources.joinToString("|") { "${it.kind.name}:${it.source}" },
                    )
                }
                currentCoroutineContext().ensureActive()
                if (revision == contextRevision) _uiState.update { it.copy(cacheError = result.exceptionOrNull()) }
            }
        }
    }

    fun previewLocalPackage(bytes: ByteArray) {
        dismissInstallPreview()
        marketScope.launch { previewPackage(bytes, PluginInstallSource.Local, origin = null) }
    }

    fun installFromGitHub(repo: GitHubRepoSummary) {
        remoteInstallJob?.cancel()
        activeDownloadRequest = null
        val request = ++remoteInstallRevision
        val context = contextRevision
        pendingBytes = null
        pendingSource = null
        _uiState.update {
            it.copy(installPreview = null, installPreviewOrigin = null, packageSizeBytes = null, downloadProgress = null,
                installingRepo = repo.fullName.trim(), isLoading = true, status = PluginMarketStatus.CheckingInstallRelease(repo.displayTitle))
        }
        remoteInstallJob = marketScope.launch {
            val listed = _uiState.value.allMarketRepos.firstOrNull { it.fullName.equals(repo.fullName, ignoreCase = true) }
            val viaAccount = repo.requiresAccount || listed?.requiresAccount == true
            if (viaAccount && accountObserved && accountKey == null) {
                syncAfterInstallKey = null
                _uiState.update {
                    it.copy(isLoading = false, upgradingKey = null, installingRepo = null, status = PluginMarketStatus.ReleaseAssetMissing(repo.fullName))
                }
                return@launch
            }
            // Recheck before installation; a failed refresh can retain the known public asset route.
            _uiState.update { it.copy(isLoading = true, status = PluginMarketStatus.CheckingInstallRelease(repo.displayTitle)) }
            val fetched = fetchRelease(repo.fullName, fresh = true, viaAccount = viaAccount)
            currentCoroutineContext().ensureActive()
            if (context != contextRevision || request != remoteInstallRevision) return@launch
            val known = listOfNotNull(
                repo.latestRelease,
                _uiState.value.allMarketRepos.firstOrNull { it.fullName.equals(repo.fullName, ignoreCase = true) }?.latestRelease,
                _uiState.value.latestReleases[repo.fullName.lowercase()],
            ).filter { asset ->
                (!viaAccount || GitHubApiClient.isAssetApiUrl(asset.downloadUrl)) &&
                    (!GitHubApiClient.isAssetApiUrl(asset.downloadUrl) || !accountObserved || accountKey != null)
            }.fold(null as GitHubReleaseAsset?) { latest, asset -> preferLatestRelease(asset, latest) }
            val asset = preferLatestRelease(fetched, known)
            if (asset == null) {
                syncAfterInstallKey = null
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        upgradingKey = null,
                        installingRepo = null,
                        status = PluginMarketStatus.ReleaseAssetMissing(repo.fullName),
                    )
                }
                return@launch
            }
            if (fetched != null) {
                updateSourceRelease(repo, asset)
                persistMarketCache()
            }
            _uiState.update {
                it.copy(
                    isLoading = true,
                    status = PluginMarketStatus.DownloadingAsset(asset.assetName, asset.tagName),
                    downloadProgress = null,
                )
            }
            activeDownloadRequest = request
            val downloaded = try {
                marketAttempt {
                    operations.downloadPackage(repo, asset) { downloadedBytes, totalBytes ->
                        // Marshal throttled IO progress into the ViewModel scope and validate request identity.
                        marketScope.launch {
                            if (downloadedBytes >= 0 && request == activeDownloadRequest &&
                                request == remoteInstallRevision && context == contextRevision
                            ) {
                                _uiState.update {
                                    it.copy(downloadProgress = PluginDownloadProgress(downloadedBytes, totalBytes.takeIf { total -> total >= 0 }))
                                }
                            }
                        }
                    }
                }
            } finally {
                if (activeDownloadRequest == request) {
                    activeDownloadRequest = null
                    _uiState.update { it.copy(downloadProgress = null) }
                }
            }
            currentCoroutineContext().ensureActive()
            if (context != contextRevision || request != remoteInstallRevision) return@launch
            val bytes = downloaded
                .getOrElse { error ->
                    syncAfterInstallKey = null
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            upgradingKey = null,
                            installingRepo = null,
                            status = PluginMarketStatus.DownloadFailed(error),
                        )
                    }
                    return@launch
                }
            currentCoroutineContext().ensureActive()
            if (context != contextRevision || request != remoteInstallRevision) return@launch
            previewPackage(
                bytes = bytes,
                source = PluginInstallSource.Remote,
                origin = PluginInstallOrigin(
                    repoSlug = repo.fullName,
                    downloadUrl = asset.downloadUrl,
                    viaAccount = repo.viaAccount || GitHubApiClient.isAssetApiUrl(asset.downloadUrl),
                    registrySource = repo.registrySource.takeIf(String::isNotBlank),
                    displayName = repo.displayTitle,
                    description = repo.description.takeIf(String::isNotBlank),
                    sizeBytes = bytes.size.toLong(),
                ),
            )
        }
    }

    /**
     * Offer newer plugins before sync, then continue automatically. Failed checks fall back to
     * known market metadata or the installed plugin.
     */
    fun syncWithUpdateCheck(record: InstalledPluginRecord) {
        val slug = record.sourceRepo?.trim()?.takeIf { it.isNotEmpty() }
        if (slug == null) {
            _uiState.update { it.copy(readyToSyncKey = record.installKey) }
            return
        }
        if (_uiState.value.checkingUpdateKey != null) return
        val context = contextRevision
        updateCheckJob = marketScope.launch {
            _uiState.update {
                it.copy(checkingUpdateKey = record.installKey, status = PluginMarketStatus.CheckingUpdate(record.name))
            }
            val known = _uiState.value.allMarketRepos.firstOrNull { it.fullName.equals(slug, ignoreCase = true) }
            val fetched = withTimeoutOrNull(UPDATE_CHECK_TIMEOUT_MILLIS) {
                fetchRelease(slug, fresh = true, viaAccount = installedUsesAccount(record, known))
            }
            currentCoroutineContext().ensureActive()
            if (context != contextRevision) return@launch
            val latest = fetched
                ?: _uiState.value.latestReleases[slug.lowercase()]
                ?: known?.latestRelease
            _uiState.update { state ->
                val releases = if (fetched != null) {
                    state.latestReleases + (slug.lowercase() to fetched)
                } else {
                    state.latestReleases
                }
                val cleared = state.copy(
                    checkingUpdateKey = null,
                    latestReleases = releases,
                    status = state.status.takeUnless { it is PluginMarketStatus.CheckingUpdate },
                )
                if (latest != null && isNewerVersion(latest.tagName, record.version)) {
                    val repo = (known ?: minimalRepo(slug)).copy(
                        latestRelease = latest,
                        viaAccount = known?.viaAccount == true || GitHubApiClient.isAssetApiUrl(latest.downloadUrl),
                    )
                    cleared.copy(pendingUpgrade = PendingPluginUpgrade(record, repo))
                } else {
                    cleared.copy(readyToSyncKey = record.installKey)
                }
            }
        }
    }

    /** Check installed repositories independently and publish each result immediately. */
    fun refreshInstalledPluginVersions(automatic: Boolean = false, force: Boolean = false) {
        if (versionsJob?.isActive == true || (automatic && !_uiState.value.autoCheckUpdates)) return
        val context = contextRevision
        versionsJob = marketScope.launch {
            hydrated.await()
            if (automatic && !_uiState.value.autoCheckUpdates) return@launch
            val installed = _uiState.value.installedPlugins.ifEmpty { operations.installedPluginsFlow.first() }
            val records = installed.filter { !it.sourceRepo.isNullOrBlank() }.groupBy { it.sourceRepo!!.trim().lowercase() }
            if (records.isEmpty()) return@launch
            val fingerprint = updateFingerprint(installed)
            val now = nowMillis()
            val interval = _uiState.value.updateIntervalHours * 3_600_000L
            if (!force && fingerprint == installedCheckFingerprint &&
                ((lastInstalledVersionCheckAt > 0L && now - lastInstalledVersionCheckAt in 0 until interval) ||
                    (lastInstalledVersionFailureAt > 0L && now - lastInstalledVersionFailureAt in 0 until 5 * 60_000L))) return@launch
            if (context != contextRevision) return@launch
            if (fingerprint != installedCheckFingerprint) { lastInstalledVersionCheckAt = 0; lastInstalledVersionFailureAt = 0 }
            _uiState.update { it.copy(checkingInstalledUpdates = true, updateCheckTotal = records.size, updateCheckCompleted = 0, updateCheckUnconfirmed = 0) }
            val slots = Semaphore(6)
            try {
                coroutineScope {
                    records.map { (slug, versions) -> async { slots.withPermit {
                        val known = _uiState.value.allMarketRepos.firstOrNull { it.fullName.equals(slug, ignoreCase = true) }
                        val fetched = withTimeoutOrNull(UPDATE_CHECK_TIMEOUT_MILLIS) {
                            fetchRelease(slug, fresh = true, viaAccount = versions.any { installedUsesAccount(it, known) })
                        }
                        currentCoroutineContext().ensureActive()
                        if (context == contextRevision) _uiState.update { state ->
                            state.copy(latestReleases = if (fetched == null) state.latestReleases else state.latestReleases +
                                (slug to preferLatestRelease(fetched, state.latestReleases[slug])!!),
                                updateCheckCompleted = state.updateCheckCompleted + 1,
                                updateCheckUnconfirmed = state.updateCheckUnconfirmed + if (fetched == null) 1 else 0)
                        }
                    } } }.awaitAll()
                }
                if (context == contextRevision) {
                    installedCheckFingerprint = fingerprint
                    if (_uiState.value.updateCheckUnconfirmed == 0) { lastInstalledVersionCheckAt = nowMillis(); lastInstalledVersionFailureAt = 0 }
                    else lastInstalledVersionFailureAt = nowMillis()
                    _uiState.update { it.copy(lastUpdateCheckAtMillis = lastInstalledVersionCheckAt) }
                    persistMarketCache()
                }
            } finally {
                if (context == contextRevision) _uiState.update { it.copy(checkingInstalledUpdates = false) }
            }
        }
    }

    fun setUpdateOptions(autoCheck: Boolean = _uiState.value.autoCheckUpdates,
        badge: Boolean = _uiState.value.showUpdateBadge, intervalHours: Int = _uiState.value.updateIntervalHours) {
        marketScope.launch { userPreferencesRepository.setPluginUpdateOptions(autoCheck, badge, intervalHours) }
    }

    fun upgradeThenSync(record: InstalledPluginRecord, repo: GitHubRepoSummary) {
        syncAfterInstallKey = record.installKey
        _uiState.update { it.copy(upgradingKey = record.installKey) }
        installFromGitHub(repo.copy(registrySource = record.registrySource?.takeIf(String::isNotBlank) ?: repo.registrySource))
    }

    fun startPendingUpgrade() {
        val pending = _uiState.value.pendingUpgrade ?: return
        _uiState.update { it.copy(pendingUpgrade = null) }
        upgradeThenSync(pending.record, pending.repo)
    }

    fun dismissPendingUpgrade() {
        _uiState.update { it.copy(pendingUpgrade = null) }
    }

    fun consumeReadyToSync() {
        _uiState.update { it.copy(readyToSyncKey = null) }
    }

    fun confirmInstall() {
        val preview = _uiState.value.installPreview ?: return
        if (!canConfirmPluginInstall(preview)) {
            _uiState.update { it.copy(status = installPreviewStatus(preview)) }
            return
        }
        val bytes = pendingBytes ?: return
        val source = pendingSource ?: PluginInstallSource.Local
        val origin = _uiState.value.installPreviewOrigin
        marketScope.launch {
            _uiState.update { it.copy(isLoading = true, status = PluginMarketStatus.Installing, downloadProgress = null) }
            val result = marketAttempt { operations.installPackage(bytes, source, origin?.repoSlug, origin?.registrySource) }
                .getOrElse { PluginInstallResult.Failure(it) }
            currentCoroutineContext().ensureActive()
            when (result) {
                is PluginInstallResult.Success -> {
                    pendingBytes = null
                    pendingSource = null
                    // Enable newly installed plugins so they are immediately selectable for sync.
                    userPreferencesRepository.setPluginEnabled(result.record.installKey, true)
                    val continueSync = syncAfterInstallKey?.takeIf { it == result.record.installKey }
                    syncAfterInstallKey = null
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            upgradingKey = null, installingRepo = null,
                            installPreview = null,
                            installPreviewOrigin = null,
                            packageSizeBytes = null,
                            downloadProgress = null,
                            status = PluginMarketStatus.Installed(result.record.name),
                            readyToSyncKey = continueSync ?: it.readyToSyncKey,
                        )
                    }
                }

                is PluginInstallResult.Failure -> {
                    if (result.error is CancellationException) throw result.error
                    syncAfterInstallKey = null
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            upgradingKey = null, installingRepo = null,
                            downloadProgress = null,
                            status = PluginMarketStatus.InstallFailed(result.error),
                        )
                    }
                }
            }
        }
    }

    fun dismissInstallPreview() {
        val cancellingRemote = remoteInstallJob?.isActive == true
        remoteInstallJob?.cancel()
        activeDownloadRequest = null
        remoteInstallRevision++
        pendingBytes = null
        pendingSource = null
        syncAfterInstallKey = null
        _uiState.update {
            it.copy(
                installPreview = null, installPreviewOrigin = null, upgradingKey = null, installingRepo = null,
                packageSizeBytes = null, downloadProgress = null, isLoading = false,
                status = it.status.takeUnless { status ->
                    status is PluginMarketStatus.DownloadingAsset || status is PluginMarketStatus.CheckingInstallRelease || status == PluginMarketStatus.ParsingPackage ||
                        status == PluginMarketStatus.PreviewReady || status == PluginMarketStatus.PreviewChecksumRejected ||
                        status == PluginMarketStatus.PreviewSignatureRejected ||
                        status is PluginMarketStatus.PreviewIncompatible ||
                        (cancellingRemote && status == PluginMarketStatus.LoadingMarket)
                },
            )
        }
    }

    fun removePlugin(pluginKey: String) {
        marketScope.launch {
            val result = marketAttempt { operations.removePlugin(pluginKey) }
            currentCoroutineContext().ensureActive()
            result
                .onSuccess {
                    _uiState.update { it.copy(status = PluginMarketStatus.Removed(pluginKey)) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(status = PluginMarketStatus.RemoveFailed(error.message)) }
                }
        }
    }

    private suspend fun previewPackage(
        bytes: ByteArray,
        source: PluginInstallSource,
        origin: PluginInstallOrigin?,
    ) {
        val context = contextRevision
        _uiState.update {
            it.copy(isLoading = true, status = PluginMarketStatus.ParsingPackage, packageSizeBytes = bytes.size.toLong(), downloadProgress = null)
        }
        val result = marketAttempt { operations.previewPackage(bytes, source) }
        currentCoroutineContext().ensureActive()
        if (source == PluginInstallSource.Remote && context != contextRevision) return
        result
            .onSuccess { preview ->
                pendingBytes = bytes
                pendingSource = source
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        installPreview = preview,
                        installPreviewOrigin = origin,
                        packageSizeBytes = bytes.size.toLong(),
                        downloadProgress = null,
                        status = installPreviewStatus(preview),
                    )
                }
            }
            .onFailure { error ->
                syncAfterInstallKey = null
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        upgradingKey = null, installingRepo = null,
                        installPreviewOrigin = null,
                        packageSizeBytes = null,
                        downloadProgress = null,
                        status = PluginMarketStatus.ParsePackageFailed(error),
                    )
                }
            }
    }

    private suspend fun fetchRelease(slug: String, fresh: Boolean = false, viaAccount: Boolean = false): GitHubReleaseAsset? {
        if (viaAccount && accountObserved && accountKey == null) return null
        val asset = marketAttempt { operations.fetchLatestReleaseAsset(slug, fresh, viaAccount) }.getOrNull()
        currentCoroutineContext().ensureActive()
        if (asset != null && GitHubApiClient.isAssetApiUrl(asset.downloadUrl) && accountObserved && accountKey == null) return null
        if (viaAccount && asset != null && !GitHubApiClient.isAssetApiUrl(asset.downloadUrl)) return null
        return asset
    }

    private fun installedUsesAccount(record: InstalledPluginRecord, known: GitHubRepoSummary?): Boolean =
        known?.viaAccount == true ||
            ((!accountObserved || accountKey != null) && record.registrySource?.takeIf(String::isNotBlank)
                ?.let { !DefaultMarketSources.isDefault(it) } == true)
}

class PluginMarketViewModelFactory(
    private val pluginManager: PluginManager,
    private val gitHubRegistryRepository: GitHubRegistryRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val accountKeyFlow: Flow<String?>? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PluginMarketViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PluginMarketViewModel(
                pluginManager = pluginManager,
                gitHubRegistryRepository = gitHubRegistryRepository,
                userPreferencesRepository = userPreferencesRepository,
                accountKeyFlow = accountKeyFlow,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

private const val UPDATE_CHECK_TIMEOUT_MILLIS = 4_000L

private const val MARKET_CACHE_VERSION = 3

/** Successful page-entry refresh debounce interval. */
private const val ENTER_REFRESH_DEBOUNCE_MILLIS = 20_000L

private data class MarketSourceSnapshot(val repos: List<GitHubRepoSummary>, val atMillis: Long)

@Serializable
private data class MarketCache(val version: Int = MARKET_CACHE_VERSION, val sources: List<CachedMarketSource>,
    val installedReleases: Map<String, GitHubReleaseAsset> = emptyMap(), val updateCheckedAtMillis: Long = 0L,
    val installedFingerprint: String = "", val updateFailedAtMillis: Long = 0L)

/** Persist irreversible fingerprints rather than private source names. */
private fun updateFingerprint(records: List<InstalledPluginRecord>): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(records.filter { !it.sourceRepo.isNullOrBlank() }.map { "${it.installKey}:${it.version}:${it.sourceRepo?.lowercase()}" }.sorted().joinToString("|").toByteArray())
    .joinToString("") { "%02x".format(it) }

fun PluginMarketUiState.availableUpdateKeys(): Set<String> = installedPlugins.filter { record ->
    val slug = record.sourceRepo?.trim() ?: return@filter false
    val latest = preferLatestRelease(latestReleases[slug.lowercase()],
        allMarketRepos.firstOrNull { it.fullName.equals(slug, ignoreCase = true) }?.latestRelease)
    latest != null && isNewerVersion(latest.tagName, record.version)
}.map { it.installKey }.toSet()

@Serializable
private data class CachedMarketSource(
    val source: String,
    val kind: String,
    val atMillis: Long,
    val repos: List<GitHubRepoSummary>,
    val complete: Boolean,
)

private fun preferLatestRelease(candidate: GitHubReleaseAsset?, known: GitHubReleaseAsset?): GitHubReleaseAsset? =
    if (candidate == null || (known != null && isNewerVersion(known.tagName, candidate.tagName))) known else candidate

private val GitHubRepoSummary.requiresAccount: Boolean
    get() = viaAccount || latestRelease?.downloadUrl?.let(GitHubApiClient::isAssetApiUrl) == true

private val PluginMarketStatus.isMarketStatus: Boolean
    get() = this is PluginMarketStatus.MarketLoaded || this is PluginMarketStatus.MarketLoadFailed ||
        this == PluginMarketStatus.MarketEmpty || this == PluginMarketStatus.LoadingMarket ||
        this == PluginMarketStatus.RegistryNotConfigured

private fun marketSources(plugins: List<String>, components: List<String>): List<PluginMarketSource> =
    listOf(MarketSourceKind.Plugin to plugins, MarketSourceKind.Component to components).flatMap { (kind, sources) ->
        sources.mapNotNull { source ->
            val slug = (GitHubRepoAddress.parse(source) ?: source.trim()).lowercase()
            slug.takeIf(String::isNotBlank)?.let { PluginMarketSource(it, kind) }
        }.distinct()
    }

/** Propagate coroutine cancellation through parsing, cache and source checks. */
internal inline fun <T> marketAttempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}

/** Inject operations for ViewModel tests without Android Context. */
internal interface PluginMarketOperations {
    val installedPluginsFlow: Flow<List<InstalledPluginRecord>>
    suspend fun fetchSource(source: PluginMarketSource, preferAccount: Boolean): List<GitHubRepoSummary>
    suspend fun checkSource(source: PluginMarketSource, preferAccount: Boolean): MarketSourceCheck
    suspend fun fetchLatestReleaseAsset(slug: String, fresh: Boolean, viaAccount: Boolean): GitHubReleaseAsset?
    fun clearAccountCache()
    suspend fun downloadPackage(repo: GitHubRepoSummary, asset: GitHubReleaseAsset, onProgress: (Long, Long) -> Unit): ByteArray
    suspend fun previewPackage(bytes: ByteArray, source: PluginInstallSource): PluginInstallPreview
    suspend fun installPackage(bytes: ByteArray, source: PluginInstallSource, sourceRepo: String?, registrySource: String?): PluginInstallResult
    suspend fun removePlugin(pluginKey: String)
}

private class DefaultPluginMarketOperations(
    private val manager: PluginManager,
    private val registry: GitHubRegistryRepository,
) : PluginMarketOperations {
    override val installedPluginsFlow get() = manager.installedPluginsFlow
    override suspend fun fetchSource(source: PluginMarketSource, preferAccount: Boolean) =
        registry.fetchSource(source.source, source.kind, preferAccount)
    override suspend fun checkSource(source: PluginMarketSource, preferAccount: Boolean) =
        registry.checkSource(source.source, source.kind, preferAccount)
    override suspend fun fetchLatestReleaseAsset(slug: String, fresh: Boolean, viaAccount: Boolean) =
        registry.fetchLatestReleaseAsset(slug, fresh = fresh, viaAccount = viaAccount)
    override fun clearAccountCache() = registry.clearAccountCache()
    override suspend fun downloadPackage(repo: GitHubRepoSummary, asset: GitHubReleaseAsset, onProgress: (Long, Long) -> Unit): ByteArray =
        if (repo.viaAccount || GitHubApiClient.isAssetApiUrl(asset.downloadUrl)) {
            registry.downloadAccountAsset(asset.downloadUrl, onProgress = onProgress)
        } else {
            manager.downloadRemotePackage(asset.downloadUrl, onProgress = onProgress)
        }
    override suspend fun previewPackage(bytes: ByteArray, source: PluginInstallSource) = manager.previewPackage(bytes, source)
    override suspend fun installPackage(bytes: ByteArray, source: PluginInstallSource, sourceRepo: String?, registrySource: String?) =
        manager.installPackage(bytes, source, sourceRepo, registrySource)
    override suspend fun removePlugin(pluginKey: String) = manager.removePlugin(pluginKey)
}
