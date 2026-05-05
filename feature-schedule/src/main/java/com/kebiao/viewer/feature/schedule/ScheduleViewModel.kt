package com.kebiao.viewer.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kebiao.viewer.core.data.ManualCourseRepository
import com.kebiao.viewer.core.data.ScheduleRepository
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.DailySchedule
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.plugin.PluginManager
import com.kebiao.viewer.core.plugin.install.InstalledPluginRecord
import com.kebiao.viewer.core.plugin.logging.PluginLogger
import com.kebiao.viewer.core.plugin.runtime.AlarmRecommendation
import com.kebiao.viewer.core.plugin.runtime.PluginSyncInput
import com.kebiao.viewer.core.plugin.runtime.WorkflowExecutionResult
import com.kebiao.viewer.core.plugin.ui.PluginUiSchema
import com.kebiao.viewer.core.plugin.web.WebSessionPacket
import com.kebiao.viewer.core.plugin.web.WebSessionRequest
import com.kebiao.viewer.core.reminder.ReminderCoordinator
import com.kebiao.viewer.core.reminder.ReminderSyncWindows
import com.kebiao.viewer.core.reminder.model.ReminderDayPeriod
import com.kebiao.viewer.core.reminder.model.ReminderRule
import com.kebiao.viewer.core.reminder.model.ReminderSyncReason
import com.kebiao.viewer.core.reminder.model.ReminderScopeType
import com.kebiao.viewer.core.reminder.model.SystemAlarmSyncSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime

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
    val initialized: Boolean = false,
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
    private val normalizeTimingProfile: suspend (TermTimingProfile?) -> TermTimingProfile? = { it },
    private val onSyncCompleted: suspend (TermTimingProfile?) -> Unit = {},
    private val resolveTimingProfile: suspend () -> TermTimingProfile? = { null },
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
                _uiState.update { it.copy(initialized = true) }
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

    fun syncSchedule(targetPluginId: String? = null) {
        val snapshot = _uiState.value
        val pluginId = targetPluginId?.takeIf { it.isNotBlank() } ?: snapshot.pluginId
        if (pluginId.isBlank()) {
            PluginLogger.warn("plugin.schedule.sync.rejected", mapOf("reason" to "missing_plugin"))
            _uiState.update { it.copy(statusMessage = "请先安装并启用至少一个插件") }
            return
        }
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            PluginLogger.info(
                "plugin.schedule.sync.request",
                mapOf(
                    "pluginId" to pluginId,
                    "usernamePresent" to snapshot.username.isNotBlank(),
                    "termIdPresent" to snapshot.termId.isNotBlank(),
                    "baseUrl" to PluginLogger.sanitizeUrl(snapshot.baseUrl),
                ),
            )
            _uiState.update {
                it.copy(
                    pluginId = pluginId,
                    isSyncing = true,
                    statusMessage = "正在打开登录取数流程...",
                )
            }
            val result = runCatching {
                withContext(ioDispatcher) {
                    scheduleRepository.saveLastInput(
                        pluginId = pluginId,
                        username = snapshot.username,
                        termId = snapshot.termId,
                    )
                    pluginManager.startSync(
                        PluginSyncInput(
                            pluginId = pluginId,
                            username = snapshot.username,
                            password = snapshot.password,
                            termId = snapshot.termId,
                            baseUrl = snapshot.baseUrl,
                        ),
                    )
                }
            }.getOrElse { error ->
                PluginLogger.error(
                    "plugin.schedule.sync.request.failure",
                    mapOf("pluginId" to pluginId, "elapsedMs" to (System.currentTimeMillis() - startedAt)),
                    error,
                )
                WorkflowExecutionResult.Failure(error.message ?: "启动插件同步流程失败")
            }
            handleExecutionResult(result, startedAt)
        }
    }

    fun completeWebSession(packet: WebSessionPacket) {
        val request = _uiState.value.pendingWebSession ?: return
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            PluginLogger.info(
                "plugin.schedule.web_session.complete",
                mapOf(
                    "pluginId" to request.pluginId,
                    "sessionId" to request.sessionId,
                    "finalUrl" to PluginLogger.sanitizeUrl(packet.finalUrl),
                    "cookieCount" to packet.cookies.size,
                    "localStorageCount" to packet.localStorageSnapshot.size,
                    "sessionStorageCount" to packet.sessionStorageSnapshot.size,
                    "capturedFieldCount" to packet.capturedFields.size,
                    "capturedPacketCount" to packet.capturedPackets.size,
                    "htmlDigest" to packet.htmlDigest,
                ),
            )
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
                PluginLogger.error(
                    "plugin.schedule.web_session.resume.failure",
                    mapOf(
                        "pluginId" to request.pluginId,
                        "sessionId" to request.sessionId,
                        "elapsedMs" to (System.currentTimeMillis() - startedAt),
                    ),
                    error,
                )
                WorkflowExecutionResult.Failure(error.message ?: "恢复插件同步流程失败")
            }
            handleExecutionResult(result, startedAt)
        }
    }

    fun cancelWebSession() {
        _uiState.value.pendingWebSession?.let { request ->
            PluginLogger.warn(
                "plugin.schedule.web_session.cancel",
                mapOf(
                    "pluginId" to request.pluginId,
                    "sessionId" to request.sessionId,
                    "startUrl" to PluginLogger.sanitizeUrl(request.startUrl),
                ),
            )
        }
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
        val schedule = reminderSchedule(state) ?: return
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
            val dispatchSummary = syncTodaySystemClockAlarms(
                pluginId = state.pluginId,
                schedule = schedule,
                timingProfile = state.timingProfile,
                reason = ReminderSyncReason.RuleCreatedToday,
            )
            _uiState.update {
                it.copy(
                    selectionState = null,
                    statusMessage = systemAlarmSyncMessage(
                        successMessage = "已创建提醒规则：${rule.ruleId.take(8)}",
                        summary = dispatchSummary,
                    ),
                )
            }
        }
    }

    fun createReminderForCourse(courseId: String, advanceMinutes: Int, ringtoneUri: String?) {
        val state = _uiState.value
        val schedule = reminderSchedule(state)
        if (state.pluginId.isBlank()) {
            _uiState.update { it.copy(statusMessage = "请先选择插件后再创建提醒") }
            return
        }
        if (schedule == null) {
            _uiState.update { it.copy(statusMessage = "暂无课表可设置提醒") }
            return
        }
        viewModelScope.launch {
            val rule = reminderCoordinator.createRule(
                pluginId = state.pluginId,
                courseId = courseId,
                dayOfWeek = null,
                startNode = null,
                endNode = null,
                scopeType = ReminderScopeType.SingleCourse,
                advanceMinutes = advanceMinutes.coerceIn(0, 720),
                ringtoneUri = ringtoneUri,
            )
            val dispatchSummary = syncTodaySystemClockAlarms(
                pluginId = state.pluginId,
                schedule = schedule,
                timingProfile = state.timingProfile,
                reason = ReminderSyncReason.RuleCreatedToday,
            )
            val courseTitle = schedule.dailySchedules
                .flatMap { it.courses }
                .firstOrNull { it.id == courseId }
                ?.title
            _uiState.update {
                it.copy(
                    selectionState = null,
                    statusMessage = systemAlarmSyncMessage(
                        successMessage = "已创建提醒：${courseTitle ?: rule.ruleId.take(8)}",
                        summary = dispatchSummary,
                    ),
                )
            }
        }
    }

    fun addManualCourse(course: CourseItem) {
        viewModelScope.launch {
            manualCourseRepository.addCourse(course)
            val dispatchSummary = reconcileTodaySystemClockAlarms(ReminderSyncReason.ScheduleChanged)
            _uiState.update {
                it.copy(
                    statusMessage = systemAlarmSyncMessage(
                        successMessage = "已添加课程：${course.title}",
                        summary = dispatchSummary,
                    ),
                )
            }
        }
    }

    fun removeManualCourse(courseId: String) {
        viewModelScope.launch {
            manualCourseRepository.removeCourse(courseId)
            val dispatchSummary = reconcileTodaySystemClockAlarms(ReminderSyncReason.ScheduleChanged)
            _uiState.update {
                it.copy(
                    statusMessage = systemAlarmSyncMessage(
                        successMessage = "已删除手动课程",
                        summary = dispatchSummary,
                    ),
                )
            }
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
            val schedule = reminderSchedule(state)
            if (schedule != null) {
                val dispatchSummary = syncTodaySystemClockAlarms(
                    pluginId = state.pluginId,
                    schedule = schedule,
                    timingProfile = state.timingProfile,
                    reason = ReminderSyncReason.RuleCreatedToday,
                )
                _uiState.update {
                    it.copy(
                        statusMessage = systemAlarmSyncMessage(
                            successMessage = "已为 ${courseIds.size} 门课程创建提醒",
                            summary = dispatchSummary,
                        ),
                    )
                }
            } else {
                _uiState.update { it.copy(statusMessage = "已为 ${courseIds.size} 门课程创建提醒；暂无课表可设置提醒") }
            }
        }
    }

    fun loadSampleCourses() {
        viewModelScope.launch {
            manualCourseRepository.replaceAll(sampleManualCourses())
            val dispatchSummary = reconcileTodaySystemClockAlarms(ReminderSyncReason.ScheduleChanged)
            _uiState.update {
                it.copy(
                    statusMessage = systemAlarmSyncMessage(
                        successMessage = "已加载示例课表",
                        summary = dispatchSummary,
                    ),
                )
            }
        }
    }

    fun clearManualCourses() {
        viewModelScope.launch {
            manualCourseRepository.replaceAll(emptyList())
            val dispatchSummary = reconcileTodaySystemClockAlarms(ReminderSyncReason.ScheduleChanged)
            _uiState.update {
                it.copy(
                    statusMessage = systemAlarmSyncMessage(
                        successMessage = "已清空手动课表",
                        summary = dispatchSummary,
                    ),
                )
            }
        }
    }

    fun clearImportedSchedule() {
        viewModelScope.launch {
            scheduleRepository.clearSchedule()
            val dispatchSummary = reconcileTodaySystemClockAlarms(ReminderSyncReason.ScheduleChanged)
            _uiState.update {
                it.copy(
                    schedule = null,
                    statusMessage = systemAlarmSyncMessage(
                        successMessage = "已清空导入的课表",
                        summary = dispatchSummary,
                    ),
                )
            }
        }
    }

    fun clearAllSchedules() {
        viewModelScope.launch {
            val ruleIds = _uiState.value.reminderRules.map { it.ruleId }
            ruleIds.forEach { reminderCoordinator.deleteRule(it) }
            reminderCoordinator.clearSystemAlarmRecords()
            manualCourseRepository.replaceAll(emptyList())
            scheduleRepository.clearSchedule()
            _uiState.update {
                it.copy(
                    schedule = null,
                    statusMessage = "已清空全部课表",
                    selectionState = null,
                )
            }
        }
    }

    fun removeReminderRule(ruleId: String) {
        viewModelScope.launch {
            reminderCoordinator.deleteRule(ruleId)
            _uiState.update { it.copy(statusMessage = "已删除提醒规则") }
        }
    }

    fun saveFirstCourseReminder(
        period: ReminderDayPeriod,
        enabled: Boolean,
        advanceMinutes: Int,
        ringtoneUri: String?,
    ) {
        val state = _uiState.value
        val pluginId = state.pluginId
        if (pluginId.isBlank()) {
            _uiState.update { it.copy(statusMessage = "请先选择插件后再设置首次课提醒") }
            return
        }
        viewModelScope.launch {
            reminderCoordinator.upsertFirstCourseReminder(
                pluginId = pluginId,
                period = period,
                enabled = enabled,
                advanceMinutes = advanceMinutes.coerceIn(0, 720),
                ringtoneUri = ringtoneUri,
            )
            val schedule = currentReminderSchedule()
            if (schedule != null) {
                val dispatchSummary = syncTodaySystemClockAlarms(
                    pluginId = pluginId,
                    schedule = schedule,
                    timingProfile = _uiState.value.timingProfile,
                    reason = ReminderSyncReason.RuleCreatedToday,
                )
                val label = when (period) {
                    ReminderDayPeriod.Morning -> "上午首次课提醒"
                    ReminderDayPeriod.Afternoon -> "下午首次课提醒"
                }
                _uiState.update {
                    it.copy(
                        statusMessage = systemAlarmSyncMessage(
                            successMessage = if (enabled) "已开启$label" else "已关闭$label",
                            summary = dispatchSummary,
                        ),
                    )
                }
                return@launch
            }
            val label = when (period) {
                ReminderDayPeriod.Morning -> "上午首次课提醒"
                ReminderDayPeriod.Afternoon -> "下午首次课提醒"
            }
            _uiState.update {
                it.copy(statusMessage = if (enabled) "已开启$label" else "已关闭$label")
            }
        }
    }

    private suspend fun syncTodaySystemClockAlarms(
        pluginId: String,
        schedule: TermSchedule,
        timingProfile: TermTimingProfile?,
        reason: ReminderSyncReason,
    ): SystemAlarmSyncSummary {
        val profile = timingProfile ?: resolveTimingProfile() ?: return emptySystemAlarmSyncSummary()
        return reminderCoordinator.syncSystemClockAlarmsForWindow(
            pluginId = pluginId,
            schedule = schedule,
            timingProfile = profile,
            window = ReminderSyncWindows.todayFromNow(profile),
            reason = reason,
        )
    }

    private suspend fun reconcileTodaySystemClockAlarms(reason: ReminderSyncReason): SystemAlarmSyncSummary {
        val pluginId = _uiState.value.pluginId
        val schedule = currentReminderSchedule()
        if (schedule == null) {
            reminderCoordinator.clearSystemAlarmRecords()
            return emptySystemAlarmSyncSummary()
        }
        return syncTodaySystemClockAlarms(
            pluginId = pluginId,
            schedule = schedule,
            timingProfile = _uiState.value.timingProfile,
            reason = reason,
        )
    }

    private fun systemAlarmSyncMessage(
        successMessage: String,
        summary: SystemAlarmSyncSummary,
    ): String {
        val details = buildList {
            if (summary.dismissedCount > 0) add("已删除 ${summary.dismissedCount} 个过期系统闹钟")
            if (summary.dismissFailedCount > 0) add("${summary.dismissFailedCount} 个过期系统闹钟删除失败")
            if (summary.createdCount > 0) add("成功添加 ${summary.createdCount} 个系统闹钟")
            if (summary.skippedExistingCount > 0) add("跳过 ${summary.skippedExistingCount} 个已添加闹钟")
            if (summary.skippedUnrepresentableCount > 0) add("跳过 ${summary.skippedUnrepresentableCount} 个无法安全表达日期的闹钟")
            if (summary.failedCount > 0) add("${summary.failedCount} 个设置失败：${summary.results.first { !it.succeeded }.message}")
        }
        return if (details.isEmpty()) "$successMessage；暂无可立即添加的系统闹钟" else "$successMessage；${details.joinToString("，")}"
    }

    private suspend fun currentReminderSchedule(): TermSchedule? {
        val schedule = scheduleRepository.scheduleFlow.first()
        val manualCourses = manualCourseRepository.manualCoursesFlow.first()
        return mergeManualCoursesForReminders(schedule, manualCourses)
    }

    private suspend fun loadPluginPresentation(pluginId: String) {
        runCatching {
            val schema = pluginManager.loadUiSchema(pluginId)
            val timingProfile = normalizeTimingProfile(pluginManager.loadTimingProfile(pluginId))
            _uiState.update {
                it.copy(
                    uiSchema = schema,
                    timingProfile = timingProfile,
                )
            }
            // Mirror the timing profile into widget storage so home-screen widgets can
            // resolve real clock times even before the user has run a fresh sync.
            if (timingProfile != null) {
                onSyncCompleted(timingProfile)
            }
        }.onFailure { error ->
            PluginLogger.error(
                "plugin.presentation.load.failure",
                mapOf("pluginId" to pluginId),
                error,
            )
        }
    }

    private suspend fun handleExecutionResult(result: WorkflowExecutionResult, startedAt: Long = System.currentTimeMillis()) {
        when (result) {
            is WorkflowExecutionResult.Success -> {
                val schedule = try {
                    validatePluginSchedule(result.schedule)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    PluginLogger.error(
                        "plugin.schedule.result.validation_failure",
                        mapOf(
                            "pluginId" to _uiState.value.pluginId,
                            "dailyScheduleCount" to result.schedule.dailySchedules.size,
                            "courseCount" to result.schedule.dailySchedules.sumOf { it.courses.size },
                            "elapsedMs" to (System.currentTimeMillis() - startedAt),
                        ),
                        error,
                    )
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            pendingWebSession = null,
                            statusMessage = "同步失败：${error.message ?: "插件返回的课表数据无效"}",
                        )
                    }
                    return
                }
                var syncedTimingProfile: TermTimingProfile? = null
                try {
                    syncedTimingProfile = normalizeTimingProfile(result.timingProfile)
                    withContext(ioDispatcher) {
                        scheduleRepository.saveSchedule(schedule)
                    }
                    syncTodaySystemClockAlarms(
                        pluginId = _uiState.value.pluginId,
                        schedule = mergeManualCoursesForReminders(schedule, _uiState.value.manualCourses) ?: schedule,
                        timingProfile = syncedTimingProfile,
                        reason = ReminderSyncReason.ScheduleChanged,
                    )
                    onSyncCompleted(syncedTimingProfile)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    PluginLogger.error(
                        "plugin.schedule.result.persistence_failure",
                        mapOf(
                            "pluginId" to _uiState.value.pluginId,
                            "dailyScheduleCount" to schedule.dailySchedules.size,
                            "courseCount" to schedule.dailySchedules.sumOf { it.courses.size },
                            "elapsedMs" to (System.currentTimeMillis() - startedAt),
                        ),
                        error,
                    )
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            pendingWebSession = null,
                            statusMessage = "同步失败：${error.message ?: "插件结果后处理失败"}",
                        )
                    }
                    return
                }
                PluginLogger.info(
                    "plugin.schedule.result.success",
                    mapOf(
                        "pluginId" to _uiState.value.pluginId,
                        "dailyScheduleCount" to schedule.dailySchedules.size,
                        "courseCount" to schedule.dailySchedules.sumOf { it.courses.size },
                        "messageCount" to result.messages.size,
                        "recommendationCount" to result.recommendations.size,
                        "hasTimingProfile" to (syncedTimingProfile != null),
                        "elapsedMs" to (System.currentTimeMillis() - startedAt),
                    ),
                )
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        pendingWebSession = null,
                        schedule = schedule,
                        uiSchema = result.uiSchema,
                        timingProfile = syncedTimingProfile,
                        alarmRecommendations = result.recommendations,
                        messages = result.messages,
                        statusMessage = "同步完成，已更新课表",
                    )
                }
            }

            is WorkflowExecutionResult.AwaitingWebSession -> {
                PluginLogger.info(
                    "plugin.schedule.result.awaiting_web_session",
                    mapOf(
                        "pluginId" to result.request.pluginId,
                        "sessionId" to result.request.sessionId,
                        "startUrl" to PluginLogger.sanitizeUrl(result.request.startUrl),
                        "allowedHostCount" to result.request.allowedHosts.size,
                        "messageCount" to result.messages.size,
                        "elapsedMs" to (System.currentTimeMillis() - startedAt),
                    ),
                )
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
                PluginLogger.warn(
                    "plugin.schedule.result.failure",
                    mapOf(
                        "pluginId" to _uiState.value.pluginId,
                        "failureMessage" to result.message,
                        "elapsedMs" to (System.currentTimeMillis() - startedAt),
                    ),
                )
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

internal fun validatePluginSchedule(schedule: TermSchedule): TermSchedule {
    val courses = schedule.dailySchedules.flatMap { daily ->
        require(daily.dayOfWeek in 1..7) { "插件返回了无效星期: ${daily.dayOfWeek}" }
        daily.courses.onEach { course ->
            require(course.id.isNotBlank()) { "插件返回了空课程 ID" }
            require(course.title.isNotBlank()) { "插件返回了空课程名称" }
            require(course.time.dayOfWeek in 1..7) { "插件返回了无效课程星期: ${course.time.dayOfWeek}" }
            require(course.time.dayOfWeek == daily.dayOfWeek) { "插件课程星期与日程分组不一致" }
            require(course.time.startNode in 1..32) { "插件返回了无效开始节次: ${course.time.startNode}" }
            require(course.time.endNode in course.time.startNode..32) { "插件返回了无效结束节次: ${course.time.endNode}" }
            require(course.weeks.all { it in 1..60 }) { "插件返回了无效教学周" }
        }
    }
    require(courses.size <= 1000) { "插件返回的课程数量过多" }
    return schedule
}

private fun reminderSchedule(state: ScheduleUiState): TermSchedule? =
    mergeManualCoursesForReminders(state.schedule, state.manualCourses)

private fun mergeManualCoursesForReminders(
    schedule: TermSchedule?,
    manualCourses: List<CourseItem>,
): TermSchedule? {
    if (schedule == null && manualCourses.isEmpty()) return null
    val allCourses = schedule?.dailySchedules.orEmpty().flatMap { it.courses } + manualCourses
    val dailySchedules = allCourses
        .groupBy { it.time.dayOfWeek }
        .toSortedMap()
        .map { (day, courses) ->
            DailySchedule(
                dayOfWeek = day,
                courses = courses.sortedWith(
                    compareBy<CourseItem> { it.time.startNode }
                        .thenBy { it.time.endNode }
                        .thenBy { it.title },
                ),
            )
        }
    return TermSchedule(
        termId = schedule?.termId ?: "manual",
        updatedAt = schedule?.updatedAt ?: OffsetDateTime.now().toString(),
        dailySchedules = dailySchedules,
    )
}

private fun emptySystemAlarmSyncSummary(): SystemAlarmSyncSummary = SystemAlarmSyncSummary(
    submittedCount = 0,
    createdCount = 0,
    skippedExistingCount = 0,
    skippedUnrepresentableCount = 0,
    results = emptyList(),
)

class ScheduleViewModelFactory(
    private val scheduleRepository: ScheduleRepository,
    private val pluginManager: PluginManager,
    private val reminderCoordinator: ReminderCoordinator,
    private val manualCourseRepository: ManualCourseRepository,
    private val normalizeTimingProfile: suspend (TermTimingProfile?) -> TermTimingProfile? = { it },
    private val onSyncCompleted: suspend (TermTimingProfile?) -> Unit = {},
    private val resolveTimingProfile: suspend () -> TermTimingProfile? = { null },
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScheduleViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ScheduleViewModel(
                scheduleRepository = scheduleRepository,
                pluginManager = pluginManager,
                reminderCoordinator = reminderCoordinator,
                manualCourseRepository = manualCourseRepository,
                normalizeTimingProfile = normalizeTimingProfile,
                onSyncCompleted = onSyncCompleted,
                resolveTimingProfile = resolveTimingProfile,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
