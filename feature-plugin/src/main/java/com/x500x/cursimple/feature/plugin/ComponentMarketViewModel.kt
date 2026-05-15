package com.x500x.cursimple.feature.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.x500x.cursimple.core.plugin.component.InstalledPluginComponentRecord
import com.x500x.cursimple.core.plugin.component.PluginComponentInstallResult
import com.x500x.cursimple.core.plugin.component.PluginComponentInstaller
import com.x500x.cursimple.core.plugin.component.PluginComponentRepository
import com.x500x.cursimple.core.plugin.component.PluginComponentType
import com.x500x.cursimple.core.plugin.market.ComponentMarketIndexEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComponentMarketEntry(
    val id: String,
    val name: String,
    val type: PluginComponentType,
    val version: String,
    val abi: String? = null,
    val downloadUrl: String? = null,
    val description: String = "",
)

data class ComponentMarketUiState(
    val installedComponents: List<InstalledPluginComponentRecord> = emptyList(),
    val knownComponents: List<ComponentMarketEntry> = emptyList(),
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
)

class ComponentMarketViewModel(
    private val repository: PluginComponentRepository,
    private val installer: PluginComponentInstaller,
    private val downloadPackage: suspend (String) -> ByteArray,
    private val fetchComponentIndex: suspend (String) -> List<ComponentMarketIndexEntry>,
    knownComponents: List<ComponentMarketEntry> = emptyList(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(ComponentMarketUiState(knownComponents = knownComponents))
    val uiState: StateFlow<ComponentMarketUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.installedComponentsFlow.collect { installed ->
                _uiState.update {
                    it.copy(installedComponents = installed.sortedBy { record -> record.id })
                }
            }
        }
    }

    fun setStatusMessage(message: String?) {
        _uiState.update { it.copy(statusMessage = message) }
    }

    fun loadRemoteMarket(indexUrl: String) {
        val url = indexUrl.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(statusMessage = "请先在设置-插件中配置组件市场索引 URL") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "正在加载远程组件...") }
            runCatching { fetchComponentIndex(url) }
                .onSuccess { entries ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            knownComponents = entries.map(ComponentMarketIndexEntry::toMarketEntry),
                            statusMessage = "已加载 ${entries.size} 个组件",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = error.message ?: "加载远程组件失败",
                        )
                    }
                }
        }
    }

    fun installLocalPackage(bytes: ByteArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "正在安装本地组件...") }
            handleInstallResult(installer.installLocalPackage(bytes))
        }
    }

    fun installRemoteEntry(entry: ComponentMarketEntry) {
        val url = entry.downloadUrl
        if (url.isNullOrBlank()) {
            _uiState.update { it.copy(statusMessage = "该组件没有可下载地址") }
            return
        }
        installRemotePackage(url)
    }

    private fun installRemotePackage(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "正在下载组件包...") }
            runCatching { downloadPackage(url) }
                .onSuccess { bytes ->
                    _uiState.update { it.copy(statusMessage = "正在安装远程组件...") }
                    handleInstallResult(installer.installRemotePackage(bytes))
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = error.message ?: "下载组件包失败",
                        )
                    }
                }
        }
    }

    private fun handleInstallResult(result: PluginComponentInstallResult) {
        when (result) {
            is PluginComponentInstallResult.Success -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "已安装组件：${result.record.id}",
                    )
                }
            }

            is PluginComponentInstallResult.Failure -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = result.reason.message,
                    )
                }
            }
        }
    }
}

class ComponentMarketViewModelFactory(
    private val repository: PluginComponentRepository,
    private val installer: PluginComponentInstaller,
    private val downloadPackage: suspend (String) -> ByteArray,
    private val fetchComponentIndex: suspend (String) -> List<ComponentMarketIndexEntry>,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ComponentMarketViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ComponentMarketViewModel(
                repository = repository,
                installer = installer,
                downloadPackage = downloadPackage,
                fetchComponentIndex = fetchComponentIndex,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

private fun ComponentMarketIndexEntry.toMarketEntry(): ComponentMarketEntry {
    return ComponentMarketEntry(
        id = id,
        name = name,
        type = type,
        version = version,
        abi = abi,
        downloadUrl = downloadUrl,
        description = description,
    )
}
