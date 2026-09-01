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
import com.x500x.cursimple.core.plugin.security.PluginSignatureStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 预检结束后展示在市场页顶部的状态文案。 */
internal fun installPreviewStatusMessage(preview: PluginInstallPreview): String = when {
    !preview.checksumVerified -> "插件包摘要校验未通过，不能安装"
    preview.signatureStatus == PluginSignatureStatus.Invalid -> "插件包签名校验未通过，不能安装"
    else -> "插件包已通过完整性预检，请确认下方权限与站点"
}

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
    val statusMessage: String? = null,
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

    fun setStatusMessage(message: String?) {
        _uiState.update { it.copy(statusMessage = message) }
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
            _uiState.update { it.copy(statusMessage = "请先在设置-插件中配置注册表仓库") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "正在加载插件市场...") }
            runCatching { gitHubRegistryRepository.fetchAll(slug) }
                .onSuccess { repos ->
                    val now = nowMillis()
                    val message = if (repos.isEmpty()) "插件市场为空" else "已加载 ${repos.size} 个插件"
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            marketRepos = repos,
                            lastLoadedRegistry = slug,
                            lastLoadedAtMillis = now,
                            statusMessage = message,
                        )
                    }
                    val encoded = runCatching { json.encodeToString(repos) }.getOrNull().orEmpty()
                    userPreferencesRepository.setPluginMarketCache(encoded, now, slug)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = error.message ?: "加载插件市场失败",
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
                it.copy(statusMessage = "${repo.fullName} 还没有带 ZIP 资产的 Release，无法安装。")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, statusMessage = "正在下载 ${asset.assetName}（${asset.tagName}）...")
            }
            val bytes = runCatching { pluginManager.downloadRemotePackage(asset.downloadUrl) }
                .getOrElse { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = error.message ?: "下载插件包失败",
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
            _uiState.update { it.copy(isLoading = true, statusMessage = "正在安装插件...") }
            when (val result = pluginManager.installPackage(bytes, source)) {
                is PluginInstallResult.Success -> {
                    pendingBytes = null
                    pendingSource = null
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            installPreview = null,
                            installPreviewOrigin = null,
                            statusMessage = "已安装插件：${result.record.name}",
                        )
                    }
                }

                is PluginInstallResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = result.message,
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
                    _uiState.update { it.copy(statusMessage = "已移除插件：$pluginKey") }
                }
                .onFailure { error ->
                    val errorMessage = error.message ?: "移除插件失败"
                    _uiState.update { it.copy(statusMessage = errorMessage) }
                }
        }
    }

    private fun previewPackage(
        bytes: ByteArray,
        source: PluginInstallSource,
        origin: PluginInstallOrigin?,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "正在解析插件包...") }
            runCatching { pluginManager.previewPackage(bytes, source) }
                .onSuccess { preview ->
                    pendingBytes = bytes
                    pendingSource = source
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            installPreview = preview,
                            installPreviewOrigin = origin,
                            statusMessage = installPreviewStatusMessage(preview),
                        )
                    }
                }
                .onFailure {
                    val errorMessage = it.message ?: "解析插件包失败"
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            installPreviewOrigin = null,
                            statusMessage = errorMessage,
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
