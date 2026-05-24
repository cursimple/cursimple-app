package com.x500x.cursimple.feature.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.x500x.cursimple.core.plugin.PluginManager
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.install.PluginInstallPreview
import com.x500x.cursimple.core.plugin.install.PluginInstallResult
import com.x500x.cursimple.core.plugin.install.PluginInstallSource
import com.x500x.cursimple.core.plugin.market.github.GitHubRegistryRepository
import com.x500x.cursimple.core.plugin.market.github.GitHubRepoSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PluginMarketUiState(
    val marketRepos: List<GitHubRepoSummary> = emptyList(),
    val installedPlugins: List<InstalledPluginRecord> = emptyList(),
    val installPreview: PluginInstallPreview? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val lastLoadedRegistry: String? = null,
)

class PluginMarketViewModel(
    private val pluginManager: PluginManager,
    private val gitHubRegistryRepository: GitHubRegistryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PluginMarketUiState())
    val uiState: StateFlow<PluginMarketUiState> = _uiState

    private var pendingBytes: ByteArray? = null
    private var pendingSource: PluginInstallSource? = null

    init {
        viewModelScope.launch {
            pluginManager.installedPluginsFlow.collect { installed ->
                _uiState.update {
                    it.copy(installedPlugins = installed.sortedBy { record -> record.name })
                }
            }
        }
    }

    fun setStatusMessage(message: String?) {
        _uiState.update { it.copy(statusMessage = message) }
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
                    val message = if (repos.isEmpty()) "插件市场为空" else "已加载 ${repos.size} 个插件"
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            marketRepos = repos,
                            lastLoadedRegistry = slug,
                            statusMessage = message,
                        )
                    }
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
        previewPackage(bytes, PluginInstallSource.Local)
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
        _uiState.update { it.copy(installPreview = null) }
    }

    fun removePlugin(pluginId: String) {
        viewModelScope.launch {
            runCatching { pluginManager.removePlugin(pluginId) }
                .onSuccess {
                    _uiState.update { it.copy(statusMessage = "已移除插件：$pluginId") }
                }
                .onFailure { error ->
                    val errorMessage = error.message ?: "移除插件失败"
                    _uiState.update { it.copy(statusMessage = errorMessage) }
                }
        }
    }

    private fun previewPackage(bytes: ByteArray, source: PluginInstallSource) {
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
                            statusMessage = "插件包已通过预检",
                        )
                    }
                }
                .onFailure {
                    val errorMessage = it.message ?: "解析插件包失败"
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = errorMessage,
                        )
                    }
                }
        }
    }
}

class PluginMarketViewModelFactory(
    private val pluginManager: PluginManager,
    private val gitHubRegistryRepository: GitHubRegistryRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PluginMarketViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PluginMarketViewModel(pluginManager, gitHubRegistryRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
