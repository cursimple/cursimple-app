package com.kebiao.viewer.feature.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kebiao.viewer.core.plugin.PluginManager
import com.kebiao.viewer.core.plugin.install.InstalledPluginRecord
import com.kebiao.viewer.core.plugin.install.PluginInstallPreview
import com.kebiao.viewer.core.plugin.install.PluginInstallResult
import com.kebiao.viewer.core.plugin.install.PluginInstallSource
import com.kebiao.viewer.core.plugin.market.MarketPluginEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PluginMarketUiState(
    val remoteIndexUrl: String = "",
    val marketPlugins: List<MarketPluginEntry> = emptyList(),
    val installedPlugins: List<InstalledPluginRecord> = emptyList(),
    val installPreview: PluginInstallPreview? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
)

class PluginMarketViewModel(
    private val pluginManager: PluginManager,
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

    fun onRemoteIndexUrlChange(value: String) {
        _uiState.update { it.copy(remoteIndexUrl = value) }
    }

    fun loadRemoteMarket() {
        val url = _uiState.value.remoteIndexUrl.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(statusMessage = "请输入远程市场索引 URL") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "正在加载远程市场...") }
            runCatching { pluginManager.fetchMarketIndex(url) }
                .onSuccess { payload ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            marketPlugins = payload.plugins,
                            statusMessage = "已加载 ${payload.plugins.size} 个插件",
                        )
                    }
                }
                .onFailure {
                    val errorMessage = it.message ?: "加载远程市场失败"
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = errorMessage,
                        )
                    }
                }
        }
    }

    fun previewLocalPackage(bytes: ByteArray) {
        previewPackage(bytes, PluginInstallSource.Local)
    }

    fun previewRemotePackage(entry: MarketPluginEntry) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "正在下载插件包...") }
            runCatching { pluginManager.downloadRemotePackage(entry.downloadUrl) }
                .onSuccess { bytes ->
                    previewPackage(bytes, PluginInstallSource.Remote)
                }
                .onFailure {
                    val errorMessage = it.message ?: "下载插件包失败"
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = errorMessage,
                        )
                    }
                }
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
            pluginManager.removePlugin(pluginId)
            _uiState.update { it.copy(statusMessage = "已移除插件：$pluginId") }
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
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PluginMarketViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PluginMarketViewModel(pluginManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
