package com.kebiao.viewer.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kebiao.viewer.core.data.ScheduleRepository
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.kernel.plugin.PluginContext
import com.kebiao.viewer.core.kernel.plugin.PluginSyncRequest
import com.kebiao.viewer.core.kernel.plugin.SyncCredentials
import com.kebiao.viewer.core.kernel.plugin.SyncFailure
import com.kebiao.viewer.core.kernel.plugin.SyncResult
import com.kebiao.viewer.core.kernel.service.ScheduleSyncService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScheduleUiState(
    val username: String = "",
    val password: String = "",
    val pluginId: String = "demo-campus",
    val termId: String = "2026-spring",
    val baseUrl: String = "https://demo-campus.example",
    val isSyncing: Boolean = false,
    val statusMessage: String? = null,
    val schedule: TermSchedule? = null,
)

class ScheduleViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val scheduleSyncService: ScheduleSyncService,
    private val onSyncCompleted: suspend () -> Unit = {},
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                scheduleRepository.scheduleFlow,
                scheduleRepository.lastPluginIdFlow,
                scheduleRepository.lastUsernameFlow,
                scheduleRepository.lastTermIdFlow,
            ) { schedule, pluginId, username, termId ->
                KernelSnapshot(
                    schedule = schedule,
                    pluginId = pluginId,
                    username = username,
                    termId = termId,
                )
            }.collect { snapshot ->
                _uiState.update {
                    it.copy(
                        schedule = snapshot.schedule,
                        pluginId = snapshot.pluginId,
                        username = if (it.username.isBlank()) snapshot.username else it.username,
                        termId = snapshot.termId,
                    )
                }
            }
        }
    }

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun onPluginIdChange(value: String) {
        _uiState.update { it.copy(pluginId = value) }
    }

    fun onTermIdChange(value: String) {
        _uiState.update { it.copy(termId = value) }
    }

    fun onBaseUrlChange(value: String) {
        _uiState.update { it.copy(baseUrl = value) }
    }

    fun syncSchedule() {
        val snapshot = _uiState.value
        if (snapshot.username.isBlank() || snapshot.password.isBlank()) {
            _uiState.update { it.copy(statusMessage = "请输入账号和密码") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, statusMessage = "正在同步课表...") }
            val syncResult = withContext(ioDispatcher) {
                scheduleRepository.saveLastInput(
                    pluginId = snapshot.pluginId,
                    username = snapshot.username,
                    termId = snapshot.termId,
                )
                scheduleSyncService.sync(
                    request = PluginSyncRequest(
                        pluginId = snapshot.pluginId,
                        context = PluginContext(
                            termId = snapshot.termId,
                            baseUrl = snapshot.baseUrl,
                        ),
                        credentials = SyncCredentials(
                            username = snapshot.username,
                            password = snapshot.password,
                        ),
                    ),
                )
            }

            when (syncResult) {
                is SyncResult.Success -> {
                    withContext(ioDispatcher) {
                        scheduleRepository.saveSchedule(syncResult.schedule)
                    }
                    runCatching { onSyncCompleted() }
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            statusMessage = "同步完成：${syncResult.schedule.termId}",
                        )
                    }
                }

                is SyncResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            statusMessage = "同步失败：${syncResult.reason.toReadableMessage()}",
                        )
                    }
                }
            }
        }
    }

    private fun SyncFailure.toReadableMessage(): String {
        return when (this) {
            SyncFailure.PluginNotFound -> "未找到插件"
            is SyncFailure.PluginLoadFailed -> message
            is SyncFailure.ExecutionFailed -> message
        }
    }

    private data class KernelSnapshot(
        val schedule: TermSchedule?,
        val pluginId: String,
        val username: String,
        val termId: String,
    )
}

class ScheduleViewModelFactory(
    private val scheduleRepository: ScheduleRepository,
    private val scheduleSyncService: ScheduleSyncService,
    private val onSyncCompleted: suspend () -> Unit = {},
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScheduleViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ScheduleViewModel(
                scheduleRepository = scheduleRepository,
                scheduleSyncService = scheduleSyncService,
                onSyncCompleted = onSyncCompleted,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
