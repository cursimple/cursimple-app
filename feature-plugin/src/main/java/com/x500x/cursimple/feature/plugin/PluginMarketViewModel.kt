package com.x500x.cursimple.feature.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.x500x.cursimple.core.data.UserPreferencesRepository
import com.x500x.cursimple.core.plugin.PluginManager
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.PluginInstallPreview
import com.x500x.cursimple.core.plugin.install.PluginInstallResult
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import com.x500x.cursimple.core.plugin.market.github.GitHubRegistryRepository
import com.x500x.cursimple.core.plugin.market.github.GitHubReleaseAsset
import com.x500x.cursimple.core.plugin.market.github.GitHubRepoSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 待安装插件包的来源，本地文件安装时为 null。 */
data class PluginInstallOrigin(
    val repoSlug: String,
    val downloadUrl: String,
)

data class PluginMarketUiState(
    val marketRepos: List<GitHubRepoSummary> = emptyList(),
    val installedPlugins: List<InstalledPluginRecord> = emptyList(),
    val installPreview: PluginInstallPreview? = null,
    val installPreviewOrigin: PluginInstallOrigin? = null,
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

class PluginMarketViewModel(
    private val pluginManager: PluginManager,
    private val gitHubRegistryRepository: GitHubRegistryRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PluginMarketUiState())
    val uiState: StateFlow<PluginMarketUiState> = _uiState

    private var pendingBytes: ByteArray? = null
    private var pendingSource: PluginInstallSource? = null
    /** 为导课而升级时记下 installKey，装好后自动接着导课。 */
    private var syncAfterInstallKey: String? = null

    private val hydrationJob: Job = viewModelScope.launch { hydrateFromCache() }

    init {
        viewModelScope.launch {
            pluginManager.installedPluginsFlow.collect { installed ->
                _uiState.update {
                    it.copy(installedPlugins = installed.sortedBy { record -> record.name })
                }
            }
        }
    }

    private suspend fun hydrateFromCache() {
        val prefs = userPreferencesRepository.preferencesFlow.first()
        val cached = decodeCache(prefs.pluginMarketCacheJson)
        if (cached.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    marketRepos = cached,
                    lastLoadedAtMillis = prefs.pluginMarketCachedAtMillis,
                    lastLoadedRegistry = prefs.pluginMarketCachedRegistry.takeIf(String::isNotBlank),
                )
            }
        }
    }

    fun setStatus(status: PluginMarketStatus?) {
        _uiState.update { it.copy(status = status) }
    }

    /**
     * 仅当缓存超过 [maxAgeMillis] 或注册表仓库发生变化时才发起网络请求。
     * 进入页面时调用，实现每天首次进入刷新一次。
     */
    fun refreshIfStale(registryRepo: String, maxAgeMillis: Long) {
        viewModelScope.launch {
            hydrationJob.join()
            val state = _uiState.value
            if (state.isLoading) return@launch
            val now = nowMillis()
            val sameRegistry = state.lastLoadedRegistry == registryRepo.trim()
            val fresh = state.lastLoadedAtMillis > 0L &&
                (now - state.lastLoadedAtMillis) < maxAgeMillis &&
                sameRegistry &&
                state.marketRepos.isNotEmpty()
            if (fresh) return@launch
            loadRegistry(registryRepo)
        }
    }

    fun loadRegistry(registryRepo: String) {
        val slug = registryRepo.trim()
        if (slug.isBlank()) {
            _uiState.update { it.copy(status = PluginMarketStatus.RegistryNotConfigured) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, status = PluginMarketStatus.LoadingMarket) }
            // 第一步只拉注册表（几百字节，走 CDN 很快），拿到就先把列表摆出来。
            // 以前要等每个插件的最新版清单都查完才显示，那一步走 GitHub 代理，国内常要十几秒，
            // 第一次进导课页就对着转圈干等
            val summaries = runCatching { gitHubRegistryRepository.fetchRegistry(slug) }
                .getOrElse { error ->
                    _uiState.update {
                        it.copy(isLoading = false, status = PluginMarketStatus.MarketLoadFailed(error))
                    }
                    return@launch
                }
            val known = _uiState.value.marketRepos.associateBy { it.fullName.lowercase() }
            val listed = summaries.map { summary ->
                summary.latestRelease?.let { return@map summary }
                summary.copy(latestRelease = known[summary.fullName.lowercase()]?.latestRelease)
            }
            val now = nowMillis()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshingReleases = listed.isNotEmpty(),
                    marketRepos = listed,
                    lastLoadedRegistry = slug,
                    lastLoadedAtMillis = now,
                    status = if (listed.isEmpty()) {
                        PluginMarketStatus.MarketEmpty
                    } else {
                        PluginMarketStatus.MarketLoaded(listed.size)
                    },
                )
            }
            persistMarketCache(listed, now, slug)
            if (listed.isEmpty()) return@launch
            // 第二步在后台并发补各插件的最新版；装插件、判断有没有新版要用，列表本身不等它
            val filled = coroutineScope {
                listed.map { repo ->
                    async {
                        val fetched = gitHubRegistryRepository.fetchLatestReleaseAsset(repo.fullName)
                        repo.copy(latestRelease = fetched ?: repo.latestRelease)
                    }
                }.awaitAll()
            }
            _uiState.update { state ->
                // 这期间列表可能又被刷新过，只在还是同一份时写回
                if (state.lastLoadedAtMillis != now) state else state.copy(marketRepos = filled, isRefreshingReleases = false)
            }
            persistMarketCache(filled, now, slug)
        }
    }

    private suspend fun persistMarketCache(repos: List<GitHubRepoSummary>, now: Long, slug: String) {
        val encoded = runCatching { json.encodeToString(repos) }.getOrNull().orEmpty()
        userPreferencesRepository.setPluginMarketCache(encoded, now, slug)
    }

    fun previewLocalPackage(bytes: ByteArray) {
        previewPackage(bytes, PluginInstallSource.Local, origin = null)
    }

    fun installFromGitHub(repo: GitHubRepoSummary) {
        viewModelScope.launch {
            // 列表先出来、版本信息后补，点安装时可能还没补到这一条，就地现查
            val asset = repo.latestRelease ?: run {
                _uiState.update { it.copy(isLoading = true, status = PluginMarketStatus.LoadingMarket) }
                gitHubRegistryRepository.fetchLatestReleaseAsset(repo.fullName)
            }
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
            _uiState.update {
                it.copy(
                    isLoading = true,
                    status = PluginMarketStatus.DownloadingAsset(asset.assetName, asset.tagName),
                )
            }
            val bytes = runCatching { pluginManager.downloadRemotePackage(asset.downloadUrl) }
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
            previewPackage(
                bytes = bytes,
                source = PluginInstallSource.Remote,
                origin = PluginInstallOrigin(
                    repoSlug = repo.fullName,
                    downloadUrl = asset.downloadUrl,
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
        viewModelScope.launch {
            _uiState.update {
                it.copy(checkingUpdateKey = record.installKey, status = PluginMarketStatus.CheckingUpdate(record.name))
            }
            val known = _uiState.value.marketRepos.firstOrNull { it.fullName.equals(slug, ignoreCase = true) }
            // 查新版最多等几秒：网慢时宁可先用缓存里的版本导课，也别让人对着转圈等半分钟
            val fetched = withTimeoutOrNull(UPDATE_CHECK_TIMEOUT_MILLIS) {
                gitHubRegistryRepository.fetchLatestReleaseAsset(slug, fresh = true)
            }
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
                    val repo = (known ?: minimalRepo(slug)).copy(latestRelease = latest)
                    cleared.copy(pendingUpgrade = PendingPluginUpgrade(record, repo))
                } else {
                    cleared.copy(readyToSyncKey = record.installKey)
                }
            }
        }
    }

    private var lastInstalledVersionCheckAt = 0L

    /** 进导课页或插件页时现查已装插件的最新版；十分钟内查过就不再查。 */
    fun refreshInstalledPluginVersions() {
        val now = nowMillis()
        if (now - lastInstalledVersionCheckAt < INSTALLED_VERSION_CHECK_INTERVAL_MILLIS) return
        viewModelScope.launch {
            hydrationJob.join()
            // 已装列表是另一路流读出来的，进程刚起来时可能还是空的
            val installed = _uiState.value.installedPlugins.ifEmpty { pluginManager.installedPluginsFlow.first() }
            val slugs = installed
                .mapNotNull { it.sourceRepo?.trim()?.takeIf(String::isNotEmpty) }
                .distinctBy { it.lowercase() }
            if (slugs.isEmpty()) return@launch
            lastInstalledVersionCheckAt = now
            // 各插件并发查，不用一个等一个
            val found = coroutineScope {
                slugs.map { slug ->
                    async {
                        gitHubRegistryRepository.fetchLatestReleaseAsset(slug, fresh = true)?.let { slug.lowercase() to it }
                    }
                }.awaitAll()
            }.filterNotNull().toMap()
            if (found.isNotEmpty()) {
                _uiState.update { it.copy(latestReleases = it.latestReleases + found) }
            }
        }
    }

    /** 直接升级到市场上已知的新版，装好后接着导课。 */
    fun upgradeThenSync(record: InstalledPluginRecord, repo: GitHubRepoSummary) {
        syncAfterInstallKey = record.installKey
        _uiState.update { it.copy(upgradingKey = record.installKey) }
        installFromGitHub(repo)
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, status = PluginMarketStatus.Installing) }
            val sourceRepo = _uiState.value.installPreviewOrigin?.repoSlug
            when (val result = pluginManager.installPackage(bytes, source, sourceRepo)) {
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
                            status = PluginMarketStatus.Installed(result.record.name),
                            readyToSyncKey = continueSync ?: it.readyToSyncKey,
                        )
                    }
                }

                is PluginInstallResult.Failure -> {
                    syncAfterInstallKey = null
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            upgradingKey = null,
                            status = PluginMarketStatus.InstallFailed(result.error),
                        )
                    }
                }
            }
        }
    }

    fun dismissInstallPreview() {
        pendingBytes = null
        pendingSource = null
        syncAfterInstallKey = null
        _uiState.update { it.copy(installPreview = null, installPreviewOrigin = null, upgradingKey = null) }
    }

    fun removePlugin(pluginKey: String) {
        viewModelScope.launch {
            runCatching { pluginManager.removePlugin(pluginKey) }
                .onSuccess {
                    _uiState.update { it.copy(status = PluginMarketStatus.Removed(pluginKey)) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(status = PluginMarketStatus.RemoveFailed(error.message)) }
                }
        }
    }

    private fun previewPackage(
        bytes: ByteArray,
        source: PluginInstallSource,
        origin: PluginInstallOrigin?,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, status = PluginMarketStatus.ParsingPackage) }
            runCatching { pluginManager.previewPackage(bytes, source) }
                .onSuccess { preview ->
                    pendingBytes = bytes
                    pendingSource = source
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            installPreview = preview,
                            installPreviewOrigin = origin,
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
                            status = PluginMarketStatus.ParsePackageFailed(error),
                        )
                    }
                }
        }
    }

    private fun decodeCache(raw: String): List<GitHubRepoSummary> {
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<GitHubRepoSummary>>(raw) }
            .getOrDefault(emptyList())
    }
}

class PluginMarketViewModelFactory(
    private val pluginManager: PluginManager,
    private val gitHubRegistryRepository: GitHubRegistryRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PluginMarketViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PluginMarketViewModel(
                pluginManager = pluginManager,
                gitHubRegistryRepository = gitHubRegistryRepository,
                userPreferencesRepository = userPreferencesRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

private const val INSTALLED_VERSION_CHECK_INTERVAL_MILLIS = 10 * 60 * 1000L

/** 导课前查新版最多等这么久，超时就按缓存里的版本走。 */
private const val UPDATE_CHECK_TIMEOUT_MILLIS = 4_000L
