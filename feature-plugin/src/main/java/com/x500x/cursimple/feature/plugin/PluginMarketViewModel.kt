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
import com.x500x.cursimple.core.plugin.market.github.GitHubRepoSummary
import kotlinx.coroutines.Job
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
)

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
            runCatching { gitHubRegistryRepository.fetchAll(slug) }
                .onSuccess { repos ->
                    val now = nowMillis()
                    val status = if (repos.isEmpty()) {
                        PluginMarketStatus.MarketEmpty
                    } else {
                        PluginMarketStatus.MarketLoaded(repos.size)
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            marketRepos = repos,
                            lastLoadedRegistry = slug,
                            lastLoadedAtMillis = now,
                            status = status,
                        )
                    }
                    val encoded = runCatching { json.encodeToString(repos) }.getOrNull().orEmpty()
                    userPreferencesRepository.setPluginMarketCache(encoded, now, slug)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            status = PluginMarketStatus.MarketLoadFailed(error),
                        )
                    }
                }
        }
    }

    fun previewLocalPackage(bytes: ByteArray) {
        previewPackage(bytes, PluginInstallSource.Local, origin = null)
    }

    fun installFromGitHub(repo: GitHubRepoSummary) {
        val asset = repo.latestRelease
        if (asset == null) {
            _uiState.update {
                it.copy(status = PluginMarketStatus.ReleaseAssetMissing(repo.fullName))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    status = PluginMarketStatus.DownloadingAsset(asset.assetName, asset.tagName),
                )
            }
            val bytes = runCatching { pluginManager.downloadRemotePackage(asset.downloadUrl) }
                .getOrElse { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
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

    fun confirmInstall() {
        val bytes = pendingBytes ?: return
        val source = pendingSource ?: PluginInstallSource.Local
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, status = PluginMarketStatus.Installing) }
            when (val result = pluginManager.installPackage(bytes, source)) {
                is PluginInstallResult.Success -> {
                    pendingBytes = null
                    pendingSource = null
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            installPreview = null,
                            installPreviewOrigin = null,
                            status = PluginMarketStatus.Installed(result.record.name),
                        )
                    }
                }

                is PluginInstallResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
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
        _uiState.update { it.copy(installPreview = null, installPreviewOrigin = null) }
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
                    _uiState.update {
                        it.copy(
                            isLoading = false,
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
