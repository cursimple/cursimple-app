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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 待安装插件包的来源，本地文件安装时为 null。 */
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
    /** 兼容导课页：这里只放导课插件，组件由 [componentRepos] 提供。 */
    val marketRepos: List<GitHubRepoSummary> = emptyList(),
    val componentRepos: List<GitHubRepoSummary> = emptyList(),
    /** 按插件来源、组件来源的配置顺序合并并去重。 */
    val allMarketRepos: List<GitHubRepoSummary> = emptyList(),
    val sources: List<PluginMarketSource> = emptyList(),
    val sourceErrors: List<PluginMarketSourceError> = emptyList(),
    /** 缓存写入失败不影响已经读到的来源列表。 */
    val cacheError: Throwable? = null,
    val installedPlugins: List<InstalledPluginRecord> = emptyList(),
    val installPreview: PluginInstallPreview? = null,
    val installPreviewOrigin: PluginInstallOrigin? = null,
    /** ZIP 实际字节数，本地包和远程包均使用 bytes.size。 */
    val packageSizeBytes: Long? = null,
    val downloadProgress: PluginDownloadProgress? = null,
    val isLoading: Boolean = false,
    val status: PluginMarketStatus? = null,
    val lastLoadedRegistry: String? = null,
    val lastLoadedAtMillis: Long = 0L,
    /** 正在查哪个插件（installKey）有没有新版；查的时候按钮转圈，别让人以为没反应。 */
    val checkingUpdateKey: String? = null,
    /** 正在为导课升级哪个插件（installKey）：下载、解析、安装这一路按钮都转圈。 */
    val upgradingKey: String? = null,
    /** 列表已经出来了、还在后台补各插件的版本信息。 */
    val isRefreshingReleases: Boolean = false,
    /** 查到了新版、要先升级才能导课的插件。 */
    val pendingUpgrade: PendingPluginUpgrade? = null,
    /** 升级装好了、可以接着导课的插件（installKey），界面接到后发起同步再清掉。 */
    val readyToSyncKey: String? = null,
    /**
     * 已装插件现查到的最新版，键是来源仓库（小写）。
     *
     * 市场列表来自注册表的汇总数据，发版后要等它重新汇总、再加上本地一天的缓存才会变；
     * 进页面时对已装的几个插件单独现查一次，新版才能立刻显示出来。
     */
    val latestReleases: Map<String, GitHubReleaseAsset> = emptyMap(),
)

/** 导课前查到的新版：[record] 是本机装的那份，[repo] 带着最新的 release。 */
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
    private val _uiState = MutableStateFlow(PluginMarketUiState())
    val uiState: StateFlow<PluginMarketUiState> = _uiState

    private var pendingBytes: ByteArray? = null
    private var pendingSource: PluginInstallSource? = null
    /** 为导课而升级时记下 installKey，装好后自动接着导课。 */
    private var syncAfterInstallKey: String? = null

    private val hydrated = CompletableDeferred<Unit>()
    private var configuredSources = emptyList<PluginMarketSource>()
    private var requestedSources: List<PluginMarketSource>? = null
    private val sourceResults = linkedMapOf<PluginMarketSource, MarketSourceSnapshot>()
    private val sourceFailures = mutableMapOf<PluginMarketSource, PluginMarketSourceError>()
    private val cacheMutex = Mutex()
    private var contextRevision = 0L
    private var loadRevision = 0L
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
        if (cache?.version == MARKET_CACHE_VERSION) {
            cache.sources.forEach { cached ->
                val source = configuredSources.firstOrNull {
                    it.source == cached.source && it.kind.name == cached.kind
                } ?: return@forEach
                val publicRepos = cached.repos.filterNot { it.requiresAccount }
                sourceResults[source] = MarketSourceSnapshot(
                    publicRepos,
                    cached.atMillis.takeIf { cached.complete && publicRepos.size == cached.repos.size } ?: 0L,
                )
            }
        } else {
            // 旧缓存只可能属于原来那一个插件来源，不能套到新的来源列表上。
            val source = configuredSources.firstOrNull {
                it.kind == MarketSourceKind.Plugin &&
                    it.source.equals(GitHubRepoAddress.parse(prefs.pluginMarketCachedRegistry), ignoreCase = true)
            }
            if (source != null) {
                val legacy = marketAttempt { json.decodeFromString<List<GitHubRepoSummary>>(raw) }
                    .getOrDefault(emptyList())
                sourceResults[source] = MarketSourceSnapshot(
                    legacy.filterNot { it.requiresAccount }.map { it.copy(registrySource = source.source) },
                    0L, // 迁移后重新检查完整来源，而不是把旧单来源缓存当作合并缓存。
                )
            }
        }
        publishSources()
        // 即使从前存过私有条目，启动时也立即改写成只含公有内容的缓存。
        persistMarketCache()
    }

    fun setStatus(status: PluginMarketStatus?) {
        _uiState.update { it.copy(status = status) }
    }

    /** 仅更新 ViewModel 配置；设置页仍负责持久化偏好。空列表表示关闭对应市场。 */
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

    /** 账号标识只用于失效判断，不传令牌；同一账号换令牌也可强制刷新。 */
    fun refreshAccount(accountKey: String?) = changeAccount(accountKey, force = true)

    fun onAccountChanged(accountKey: String?) = changeAccount(accountKey, force = false)

    private fun changeAccount(key: String?, force: Boolean) {
        if (!force && accountObserved && accountKey == key) return
        val firstObservation = !accountObserved
        accountObserved = true
        accountKey = key
        invalidateRequests()
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
            refreshInstalledPluginVersions()
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
        if (pendingSource == PluginInstallSource.Remote) dismissInstallPreview()
        syncAfterInstallKey = null
        _uiState.update {
            it.copy(
                latestReleases = emptyMap(), pendingUpgrade = null, readyToSyncKey = null,
                checkingUpdateKey = null, upgradingKey = null,
                isLoading = false, isRefreshingReleases = false, status = null,
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
            } && sourceFailures.isEmpty()
            if (!fresh) startMarketLoad()
        }
    }

    /** 兼容 SchoolImportScreen；来源以 pluginSources/componentSources 为准，不能恢复被删除的默认源。 */
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
        val request = ++loadRevision
        val context = contextRevision
        val sources = configuredSources.toList()
        sourceFailures.clear()
        if (sources.isEmpty()) {
            publishSources()
            _uiState.update { it.copy(isLoading = false, status = PluginMarketStatus.RegistryNotConfigured) }
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
                                    val known = sourceResults[source]?.repos.orEmpty().associateBy { it.fullName.lowercase() }
                                    val listed = repos.filter { accountKey != null || !accountObserved || !it.requiresAccount }
                                        .map { repo ->
                                            repo.copy(
                                                registrySource = source.source,
                                                kind = if (source.kind == MarketSourceKind.Component) "extension" else repo.kind,
                                                latestRelease = preferLatestRelease(repo.latestRelease, known[repo.fullName.lowercase()]?.latestRelease),
                                            )
                                        }
                                    sourceResults[source] = MarketSourceSnapshot(listed, nowMillis())
                                },
                                onFailure = { error ->
                                    sourceFailures[source] = PluginMarketSourceError(source.source, source.kind, error)
                                },
                            )
                            // 成功的源立刻显示，不等失败来源的额外检测。
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
                    listed.map { repo ->
                        async {
                            val fetched = fetchRelease(repo.fullName, fresh = freshReleases, viaAccount = repo.requiresAccount)
                            currentCoroutineContext().ensureActive()
                            if (request != loadRevision || context != contextRevision || fetched == null) return@async
                            updateSourceRelease(repo, fetched)
                        }
                    }.awaitAll()
                }
                persistMarketCache()
            } finally {
                if (request == loadRevision && context == contextRevision) {
                    _uiState.update { it.copy(isLoading = false, isRefreshingReleases = false) }
                }
            }
        }
    }

    private fun updateSourceRelease(repo: GitHubRepoSummary, fetched: GitHubReleaseAsset) {
        sourceResults.replaceAll { _, snapshot ->
            snapshot.copy(repos = snapshot.repos.map {
                if (it.fullName.equals(repo.fullName, ignoreCase = true) && it.registrySource == repo.registrySource) {
                    val latest = preferLatestRelease(fetched, it.latestRelease)!!
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
                })
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
            it.copy(installPreview = null, installPreviewOrigin = null, packageSizeBytes = null, downloadProgress = null)
        }
        remoteInstallJob = marketScope.launch {
            val listed = _uiState.value.allMarketRepos.firstOrNull { it.fullName.equals(repo.fullName, ignoreCase = true) }
            val viaAccount = repo.requiresAccount || listed?.requiresAccount == true
            if (viaAccount && accountObserved && accountKey == null) {
                syncAfterInstallKey = null
                _uiState.update {
                    it.copy(isLoading = false, upgradingKey = null, status = PluginMarketStatus.ReleaseAssetMissing(repo.fullName))
                }
                return@launch
            }
            // 列表可能来自历史缓存，安装前现查；失败仍允许用已知附件走公有镜像。
            _uiState.update { it.copy(isLoading = true, status = PluginMarketStatus.LoadingMarket) }
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
                        // 后端每 100ms 节流；回调可能来自 IO，统一回到 ViewModel 的作用域检查请求。
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
     * 导课（同步课表）前先查插件有没有新版。
     *
     * 教务系统一改版旧插件就可能对不上，带着旧版导课多半白忙。所以先现查一次最新版：
     * 比本机的新，就先让人升级（[PluginMarketUiState.pendingUpgrade]），升级装好后自动接着导课；
     * 不比本机新，直接放行（[PluginMarketUiState.readyToSyncKey]）。现查失败（比如没网）时
     * 退回市场列表里缓存的版本；两样都没有就直接导课，不因为查不到更新把人卡住。
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
            // 查新版最多等几秒：网慢时宁可先用缓存里的版本导课，也别让人对着转圈等半分钟
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

    /** 进导课页或插件页时现查已装插件的最新版；十分钟内查过就不再查。 */
    fun refreshInstalledPluginVersions() {
        val now = nowMillis()
        if (versionsJob?.isActive == true) return
        if (lastInstalledVersionCheckAt > 0L && now - lastInstalledVersionCheckAt in 0 until INSTALLED_VERSION_CHECK_INTERVAL_MILLIS) return
        val context = contextRevision
        versionsJob = marketScope.launch {
            hydrated.await()
            // 已装列表是另一路流读出来的，进程刚起来时可能还是空的
            val installed = _uiState.value.installedPlugins.ifEmpty { operations.installedPluginsFlow.first() }
            val records = installed.filter { !it.sourceRepo.isNullOrBlank() }
                .groupBy { it.sourceRepo!!.trim().lowercase() }
            if (records.isEmpty()) return@launch
            lastInstalledVersionCheckAt = now
            // 各插件并发查，不用一个等一个
            val found = coroutineScope {
                records.map { (slug, recordsForRepo) ->
                    async {
                        val known = _uiState.value.allMarketRepos.firstOrNull { it.fullName.equals(slug, ignoreCase = true) }
                        val viaAccount = recordsForRepo.any { installedUsesAccount(it, known) }
                        fetchRelease(slug, fresh = true, viaAccount = viaAccount)?.let { slug to it }
                    }
                }.awaitAll()
            }.filterNotNull().toMap()
            currentCoroutineContext().ensureActive()
            if (found.isNotEmpty() && context == contextRevision) {
                _uiState.update { it.copy(latestReleases = it.latestReleases + found) }
            }
        }
    }

    /** 直接升级到市场上已知的新版，装好后接着导课。 */
    fun upgradeThenSync(record: InstalledPluginRecord, repo: GitHubRepoSummary) {
        syncAfterInstallKey = record.installKey
        _uiState.update { it.copy(upgradingKey = record.installKey) }
        installFromGitHub(repo.copy(registrySource = record.registrySource?.takeIf(String::isNotBlank) ?: repo.registrySource))
    }

    /** 升级提示里点了「升级」。 */
    fun startPendingUpgrade() {
        val pending = _uiState.value.pendingUpgrade ?: return
        _uiState.update { it.copy(pendingUpgrade = null) }
        upgradeThenSync(pending.record, pending.repo)
    }

    fun dismissPendingUpgrade() {
        _uiState.update { it.copy(pendingUpgrade = null) }
    }

    /** 界面已经发起了同步。 */
    fun consumeReadyToSync() {
        _uiState.update { it.copy(readyToSyncKey = null) }
    }

    fun confirmInstall() {
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
                    // 刚装好的插件默认就打开：装它就是为了用它，
                    // 不打开的话课表那边既选不到也同步不了，还得再回来摸一次开关。
                    userPreferencesRepository.setPluginEnabled(result.record.installKey, true)
                    // 为导课而升级的，装好就接着导课，不用再回去点一次
                    val continueSync = syncAfterInstallKey?.takeIf { it == result.record.installKey }
                    syncAfterInstallKey = null
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            upgradingKey = null,
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
                            upgradingKey = null,
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
                installPreview = null, installPreviewOrigin = null, upgradingKey = null,
                packageSizeBytes = null, downloadProgress = null, isLoading = false,
                status = it.status.takeUnless { status ->
                    status is PluginMarketStatus.DownloadingAsset || status == PluginMarketStatus.ParsingPackage ||
                        status == PluginMarketStatus.PreviewReady || status == PluginMarketStatus.PreviewChecksumRejected ||
                        status == PluginMarketStatus.PreviewSignatureRejected ||
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
                        upgradingKey = null,
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

private const val INSTALLED_VERSION_CHECK_INTERVAL_MILLIS = 10 * 60 * 1000L

/** 导课前查新版最多等这么久，超时就按缓存里的版本走。 */
private const val UPDATE_CHECK_TIMEOUT_MILLIS = 4_000L

private const val MARKET_CACHE_VERSION = 2

private data class MarketSourceSnapshot(val repos: List<GitHubRepoSummary>, val atMillis: Long)

@Serializable
private data class MarketCache(val version: Int = MARKET_CACHE_VERSION, val sources: List<CachedMarketSource>)

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

/** runCatching 不能吞掉协程取消，包括本地包解析、缓存和来源检测。 */
internal inline fun <T> marketAttempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}

/** 注入业务操作即可测试完整 ViewModel，不需要 Android Context 或修改测试依赖。 */
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
