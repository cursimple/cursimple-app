package com.kebiao.viewer.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kebiao.viewer.core.data.ManualCourseRepository
import com.kebiao.viewer.core.data.ScheduleRepository
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.plugin.PluginManager
import com.kebiao.viewer.core.plugin.install.InstalledPluginRecord
import com.kebiao.viewer.core.plugin.runtime.AlarmRecommendation
import com.kebiao.viewer.core.plugin.runtime.PluginSyncInput
import com.kebiao.viewer.core.plugin.runtime.WorkflowExecutionResult
import com.kebiao.viewer.core.plugin.ui.PluginUiSchema
import com.kebiao.viewer.core.plugin.web.WebSessionPacket
import com.kebiao.viewer.core.plugin.web.WebSessionRequest
import com.kebiao.viewer.core.reminder.ReminderCoordinator
import com.kebiao.viewer.core.reminder.model.ReminderRule
import com.kebiao.viewer.core.reminder.model.ReminderScopeType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScheduleSelectionState {
    data class SingleCourse(val courseId: String) : ScheduleSelectionState
    data class TimeSlot(val startNode: Int, val endNode: Int) : ScheduleSelectionState
}

data class ScheduleUiState(
    val username: String = "",
    val password: String = "",
    val pluginId: String = "",
    val termId: String = "",
    val baseUrl: String = "",
    val isSyncing: Boolean = false,
    val statusMessage: String? = null,
    val schedule: TermSchedule? = null,
    val installedPlugins: List<InstalledPluginRecord> = emptyList(),
    val uiSchema: PluginUiSchema = PluginUiSchema(),
    val pendingWebSession: WebSessionRequest? = null,
    val alarmRecommendations: List<AlarmRecommendation> = emptyList(),
    val reminderRules: List<ReminderRule> = emptyList(),
    val selectionState: ScheduleSelectionState? = null,
    val timingProfile: TermTimingProfile? = null,
    val messages: List<String> = emptyList(),
    val manualCourses: List<CourseItem> = emptyList(),
)

class ScheduleViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val pluginManager: PluginManager,
    private val reminderCoordinator: ReminderCoordinator,
    private val manualCourseRepository: ManualCourseRepository,
    private val onSyncCompleted: suspend () -> Unit = {},
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState

    init {
        viewModelScope.launch {
            manualCourseRepository.manualCoursesFlow.collect { courses ->
                _uiState.update { it.copy(manualCourses = courses) }
            }
        }
        viewModelScope.launch {
            combine(
                scheduleRepository.scheduleFlow,
                scheduleRepository.lastPluginIdFlow,
                scheduleRepository.lastUsernameFlow,
                scheduleRepository.lastTermIdFlow,
            ) { schedule, pluginId, username, termId ->
                BaseSnapshot(
                    schedule = schedule,
                    pluginId = pluginId,
                    username = username,
                    termId = termId,
                )
            }.combine(pluginManager.installedPluginsFlow) { base, installedPlugins ->
                base to installedPlugins
            }.combine(reminderCoordinator.reminderRulesFlow) { pair, reminderRules ->
                KernelSnapshot(
                    schedule = pair.first.schedule,
                    pluginId = pair.first.pluginId,
                    username = pair.first.username,
                    termId = pair.first.termId,
                    installedPlugins = pair.second,
                    reminderRules = reminderRules,
                )
            }.collect { snapshot ->
                val selectedPluginId = when {
                    snapshot.installedPlugins.any { it.pluginId == snapshot.pluginId } -> snapshot.pluginId
                    snapshot.installedPlugins.isNotEmpty() -> snapshot.installedPlugins.first().pluginId
                    else -> ""
                }
                _uiState.update {
                    it.copy(
                        schedule = snapshot.schedule,
                        pluginId = selectedPluginId,
                        username = if (it.username.isBlank()) snapshot.username else it.username,
                        termId = snapshot.termId,
                        installedPlugins = snapshot.installedPlugins,
                        reminderRules = snapshot.reminderRules,
                    )
                }
                if (selectedPluginId.isNotBlank()) {
                    loadPluginPresentation(selectedPluginId)
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
        if (value.isNotBlank()) {
            viewModelScope.launch { loadPluginPresentation(value) }
        }
    }

    fun onTermIdChange(value: String) {
        _uiState.update { it.copy(termId = value) }
    }

    fun onBaseUrlChange(value: String) {
        _uiState.update { it.copy(baseUrl = value) }
    }

    fun syncSchedule() {
        val snapshot = _uiState.value
        if (snapshot.pluginId.isBlank()) {
            _uiState.update { it.copy(statusMessage = "请先安装并选择插件") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, statusMessage = "正在打开登录取数流程...") }
            val result = runCatching {
                withContext(ioDispatcher) {
                    scheduleRepository.saveLastInput(
                        pluginId = snapshot.pluginId,
                        username = snapshot.username,
                        termId = snapshot.termId,
                    )
                    pluginManager.startSync(
                        PluginSyncInput(
                            pluginId = snapshot.pluginId,
                            username = snapshot.username,
                            password = snapshot.password,
                            termId = snapshot.termId,
                            baseUrl = snapshot.baseUrl,
                        ),
                    )
                }
            }.getOrElse { error ->
                WorkflowExecutionResult.Failure(error.message ?: "启动插件同步流程失败")
            }
            handleExecutionResult(result)
        }
    }

    fun completeWebSession(packet: WebSessionPacket) {
        val request = _uiState.value.pendingWebSession ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, statusMessage = "正在继续执行插件工作流...") }
            val result = runCatching {
                withContext(ioDispatcher) {
                    pluginManager.resumeSync(
                        pluginId = request.pluginId,
                        token = request.token,
                        packet = packet,
                    )
                }
            }.getOrElse { error ->
                WorkflowExecutionResult.Failure(error.message ?: "恢复插件同步流程失败")
            }
            handleExecutionResult(result)
        }
    }

    fun cancelWebSession() {
        _uiState.update {
            it.copy(
                pendingWebSession = null,
                isSyncing = false,
                statusMessage = "已取消网页登录流程",
            )
        }
    }

    fun selectCourse(courseId: String) {
        _uiState.update { it.copy(selectionState = ScheduleSelectionState.SingleCourse(courseId)) }
    }

    fun selectTimeSlot(startNode: Int, endNode: Int) {
        _uiState.update { it.copy(selectionState = ScheduleSelectionState.TimeSlot(startNode, endNode)) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectionState = null) }
    }

    fun createReminderForSelection(advanceMinutes: Int, ringtoneUri: String?) {
        val state = _uiState.value
        val selection = state.selectionState ?: return
        val schedule = state.schedule ?: return
        viewModelScope.launch {
            val rule = when (selection) {
                is ScheduleSelectionState.SingleCourse -> reminderCoordinator.createRule(
                    pluginId = state.pluginId,
                    courseId = selection.courseId,
                    dayOfWeek = null,
                    startNode = null,
                    endNode = null,
                    scopeType = ReminderScopeType.SingleCourse,
                    advanceMinutes = advanceMinutes,
                    ringtoneUri = ringtoneUri,
                )

                is ScheduleSelectionState.TimeSlot -> reminderCoordinator.createRule(
                    pluginId = state.pluginId,
                    courseId = null,
                    dayOfWeek = null,
                    startNode = selection.startNode,
                    endNode = selection.endNode,
                    scopeType = ReminderScopeType.TimeSlot,
                    advanceMinutes = advanceMinutes,
                    ringtoneUri = ringtoneUri,
                )
            }
            reminderCoordinator.syncRulesForSchedule(
                pluginId = state.pluginId,
                schedule = schedule,
                timingProfile = state.timingProfile,
                preferSystemClock = true,
            )
            _uiState.update {
                it.copy(
                    selectionState = null,
                    statusMessage = "已创建提醒规则：${rule.ruleId.take(8)}",
                )
            }
        }
    }

    fun addManualCourse(course: CourseItem) {
        viewModelScope.launch {
            manualCourseRepository.addCourse(course)
            _uiState.update { it.copy(statusMessage = "已添加课程：${course.title}") }
        }
    }

    fun removeManualCourse(courseId: String) {
        viewModelScope.launch {
            manualCourseRepository.removeCourse(courseId)
        }
    }

    fun createReminderForCourses(
        courseIds: Set<String>,
        advanceMinutes: Int,
        ringtoneUri: String?,
    ) {
        if (courseIds.isEmpty()) return
        val state = _uiState.value
        viewModelScope.launch {
            courseIds.forEach { id ->
                reminderCoordinator.createRule(
                    pluginId = state.pluginId,
                    courseId = id,
                    dayOfWeek = null,
                    startNode = null,
                    endNode = null,
                    scopeType = ReminderScopeType.SingleCourse,
                    advanceMinutes = advanceMinutes,
                    ringtoneUri = ringtoneUri,
                )
            }
            val schedule = state.schedule
            if (schedule != null) {
                reminderCoordinator.syncRulesForSchedule(
                    pluginId = state.pluginId,
                    schedule = schedule,
                    timingProfile = state.timingProfile,
                    preferSystemClock = true,
                )
            }
            _uiState.update { it.copy(statusMessage = "已为 ${courseIds.size} 门课程创建提醒") }
        }
    }

    fun loadSampleCourses() {
        viewModelScope.launch {
            manualCourseRepository.replaceAll(sampleManualCourses())
            _uiState.update { it.copy(statusMessage = "已加载示例课表") }
        }
    }

    fun clearManualCourses() {
        viewModelScope.launch {
            manualCourseRepository.replaceAll(emptyList())
            _uiState.update { it.copy(statusMessage = "已清空手动课表") }
        }
    }

    fun removeReminderRule(ruleId: String) {
        viewModelScope.launch {
            reminderCoordinator.deleteRule(ruleId)
            _uiState.update { it.copy(statusMessage = "已删除提醒规则") }
        }
    }

    private suspend fun loadPluginPresentation(pluginId: String) {
        runCatching {
            val schema = pluginManager.loadUiSchema(pluginId)
            val timingProfile = pluginManager.loadTimingProfile(pluginId)
            _uiState.update {
                it.copy(
                    uiSchema = schema,
                    timingProfile = timingProfile,
                )
            }
        }
    }

    private suspend fun handleExecutionResult(result: WorkflowExecutionResult) {
        when (result) {
            is WorkflowExecutionResult.Success -> {
                withContext(ioDispatcher) {
                    scheduleRepository.saveSchedule(result.schedule)
                }
                reminderCoordinator.syncRulesForSchedule(
                    pluginId = _uiState.value.pluginId,
                    schedule = result.schedule,
                    timingProfile = result.timingProfile,
                    preferSystemClock = false,
                )
                runCatching { onSyncCompleted() }
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        pendingWebSession = null,
                        schedule = result.schedule,
                        uiSchema = result.uiSchema,
                        timingProfile = result.timingProfile,
                        alarmRecommendations = result.recommendations,
                        messages = result.messages,
                        statusMessage = "同步完成，已更新课表",
                    )
                }
            }

            is WorkflowExecutionResult.AwaitingWebSession -> {
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        pendingWebSession = result.request,
                        uiSchema = result.uiSchema,
                        messages = result.messages,
                        statusMessage = "请在当前插件页完成登录",
                    )
                }
            }

            is WorkflowExecutionResult.Failure -> {
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        pendingWebSession = null,
                        statusMessage = "同步失败：${result.message}",
                    )
                }
            }
        }
    }

    private data class KernelSnapshot(
        val schedule: TermSchedule?,
        val pluginId: String,
        val username: String,
        val termId: String,
        val installedPlugins: List<InstalledPluginRecord>,
        val reminderRules: List<ReminderRule>,
    )

    private data class BaseSnapshot(
        val schedule: TermSchedule?,
        val pluginId: String,
        val username: String,
        val termId: String,
    )
}

class ScheduleViewModelFactory(
    private val scheduleRepository: ScheduleRepository,
    private val pluginManager: PluginManager,
    private val reminderCoordinator: ReminderCoordinator,
    private val manualCourseRepository: ManualCourseRepository,
    private val onSyncCompleted: suspend () -> Unit = {},
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScheduleViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ScheduleViewModel(
                scheduleRepository = scheduleRepository,
                pluginManager = pluginManager,
                reminderCoordinator = reminderCoordinator,
                manualCourseRepository = manualCourseRepository,
                onSyncCompleted = onSyncCompleted,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
