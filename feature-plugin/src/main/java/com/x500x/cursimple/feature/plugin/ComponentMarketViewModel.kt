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
    val status: ComponentMarketStatus? = null,
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

    fun setStatus(status: ComponentMarketStatus?) {
        _uiState.update { it.copy(status = status) }
    }

    fun loadRemoteMarket(indexUrl: String) {
        val url = indexUrl.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(status = ComponentMarketStatus.IndexUrlNotConfigured) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, status = ComponentMarketStatus.LoadingRemote) }
            runCatching { fetchComponentIndex(url) }
                .onSuccess { entries ->
                    val marketEntries = distinctMarketEntries(
                        entries.map(ComponentMarketIndexEntry::toMarketEntry),
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            knownComponents = marketEntries,
                            status = ComponentMarketStatus.RemoteLoaded(marketEntries.size),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            status = ComponentMarketStatus.RemoteLoadFailed(error),
                        )
                    }
                }
        }
    }

    fun installLocalPackage(bytes: ByteArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, status = ComponentMarketStatus.InstallingLocal) }
            handleInstallResult(installer.installLocalPackage(bytes))
        }
    }

    fun installRemoteEntry(entry: ComponentMarketEntry) {
        val url = entry.downloadUrl
        if (url.isNullOrBlank()) {
            _uiState.update { it.copy(status = ComponentMarketStatus.DownloadUrlMissing) }
            return
        }
        installRemotePackage(url)
    }

    private fun installRemotePackage(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, status = ComponentMarketStatus.DownloadingPackage) }
            runCatching { downloadPackage(url) }
                .onSuccess { bytes ->
                    _uiState.update { it.copy(status = ComponentMarketStatus.InstallingRemote) }
                    handleInstallResult(installer.installRemotePackage(bytes))
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            status = ComponentMarketStatus.DownloadFailed(error),
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
                        status = ComponentMarketStatus.Installed(result.record.id),
                    )
                }
            }

            is PluginComponentInstallResult.Failure -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        status = ComponentMarketStatus.InstallFailed(result.reason.error),
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

/** 列表键取自 id/版本/ABI/下载地址，重复条目会导致 LazyColumn 抛重复键异常。 */
internal fun distinctMarketEntries(entries: List<ComponentMarketEntry>): List<ComponentMarketEntry> =
    entries.distinctBy(::componentMarketEntryKey)
