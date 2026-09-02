package com.x500x.cursimple.feature.schedule

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.x500x.cursimple.core.data.ManualCourseRepository
import com.x500x.cursimple.core.data.ScheduleRepository
import com.x500x.cursimple.core.data.note.CourseNote
import com.x500x.cursimple.core.data.note.CourseNoteIndex
import com.x500x.cursimple.core.data.note.CourseNoteInput
import com.x500x.cursimple.core.data.note.CourseNoteRepository
import com.x500x.cursimple.core.data.note.resolveCourseNotes
import com.x500x.cursimple.core.data.note.validateCourseNote
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.findSlot
import com.x500x.cursimple.core.kernel.model.reminderSlotLabel
import com.x500x.cursimple.core.kernel.model.weekdayLabel
import com.x500x.cursimple.core.plugin.PluginManager
import com.x500x.cursimple.core.plugin.manifest.PluginComponentRequirement
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.logging.PluginLogger
import com.x500x.cursimple.core.plugin.runtime.AlarmRecommendation
import com.x500x.cursimple.core.plugin.runtime.PluginSyncInput
import com.x500x.cursimple.core.plugin.runtime.WorkflowExecutionResult
import com.x500x.cursimple.core.plugin.ui.PluginUiSchema
import com.x500x.cursimple.core.plugin.web.WebSessionPacket
import com.x500x.cursimple.core.plugin.web.WebSessionRequest
import com.x500x.cursimple.core.reminder.ReminderCoordinator
import com.x500x.cursimple.core.reminder.ReminderSyncWindows
import com.x500x.cursimple.core.reminder.R as ReminderR
import com.x500x.cursimple.core.reminder.model.AlarmDismissResult
import com.x500x.cursimple.core.reminder.model.AlarmDispatchResult
import com.x500x.cursimple.core.reminder.model.reminderMessageText
import com.x500x.cursimple.core.reminder.model.FirstCourseCandidateScope
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
import com.x500x.cursimple.core.reminder.model.ReminderCustomOccupancy
import com.x500x.cursimple.core.reminder.model.ReminderNodeRange
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderSyncReason
import com.x500x.cursimple.core.reminder.model.ReminderScopeType
import com.x500x.cursimple.core.reminder.model.ReminderTimeRange
import com.x500x.cursimple.core.reminder.model.SystemAlarmSyncSummary
import com.x500x.cursimple.core.reminder.model.SystemAlarmRecord
import com.x500x.cursimple.core.reminder.model.EditableAppAlarmSettings
import com.x500x.cursimple.core.reminder.model.ReminderLabelAction
import com.x500x.cursimple.core.reminder.model.ReminderLabelActionType
import com.x500x.cursimple.core.reminder.model.ReminderLabelCondition
import com.x500x.cursimple.core.reminder.model.ReminderLabelPresence
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
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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
    val customOccupancies: List<ReminderCustomOccupancy> = emptyList(),
    val systemAlarmRecords: List<SystemAlarmRecord> = emptyList(),
    val selectionState: ScheduleSelectionState? = null,
    val timingProfile: TermTimingProfile? = null,
    val messages: List<String> = emptyList(),
    val missingComponents: List<PluginComponentRequirement> = emptyList(),
    val manualCourses: List<CourseItem> = emptyList(),
    val courseNotes: CourseNoteIndex = CourseNoteIndex(),
    /** 每完成一次插件同步递增，界面据此跳转，不再比较提示文字。 */
    val syncCompletedCount: Int = 0,
)

/** 备注关联时参与匹配的课程集合：插件下发的课表加上手动添加的课。 */
internal fun ScheduleUiState.noteMatchCourses(): List<CourseItem> =
    schedule?.dailySchedules.orEmpty().flatMap { it.courses } + manualCourses

class ScheduleViewModel(
    appContext: Context,
    private val scheduleRepository: ScheduleRepository,
    private val pluginManager: PluginManager,
    private val reminderCoordinator: ReminderCoordinator,
    private val manualCourseRepository: ManualCourseRepository,
    private val courseNoteRepository: CourseNoteRepository? = null,
    private val normalizeTimingProfile: suspend (TermTimingProfile?) -> TermTimingProfile? = { it },
    private val onSyncCompleted: suspend (TermTimingProfile?) -> Unit = {},
    private val onAlarmSyncChecked: suspend () -> Unit = {},
    private val resolveTimingProfile: suspend () -> TermTimingProfile? = { null },
    private val timingProfileFlow: Flow<TermTimingProfile?> = flowOf(null),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    /** 状态提示要按当前语言渲染，这里只取应用级 Context，不持有 Activity。 */
    private val resources: Context = appContext.applicationContext

    private fun text(resId: Int): String = resources.getString(resId)

    private fun text(resId: Int, vararg formatArgs: Any): String =
        resources.getString(resId, *formatArgs)

    /** 闹钟下发结果的提示按当前语言渲染，逻辑层给的类型化结果优先于已渲染文本。 */
    private fun AlarmDispatchResult.displayText(): String =
        localizedMessage?.let { resources.reminderMessageText(it) } ?: message

    private fun AlarmDismissResult.displayText(): String =
        localizedMessage?.let { resources.reminderMessageText(it) } ?: message

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState

    private var storedCourseNotes: List<CourseNote> = emptyList()

    init {
        viewModelScope.launch {
            manualCourseRepository.manualCoursesFlow.collect { courses ->
                _uiState.update { it.copy(manualCourses = courses) }
                refreshCourseNoteIndex()
                reconcileCourseNotes()
            }
        }
        courseNoteRepository?.let { repository ->
            viewModelScope.launch {
                repository.courseNotesFlow.collect { notes ->
                    storedCourseNotes = notes
                    refreshCourseNoteIndex()
                }
            }
        }
        // 节次时间表以存储为准：插件同步与手动编辑都汇入同一份，手动录课的用户才能拿到上课时间
        viewModelScope.launch {
            timingProfileFlow.collect { profile ->
                _uiState.update { it.copy(timingProfile = profile) }
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
            }.combine(reminderCoordinator.systemAlarmRecordsFlow) { snapshot, systemAlarmRecords ->
                snapshot.copy(systemAlarmRecords = systemAlarmRecords)
            }.combine(reminderCoordinator.customOccupanciesFlow) { snapshot, customOccupancies ->
                snapshot.copy(customOccupancies = customOccupancies)
            }.collect { snapshot ->
                val selectedPluginKey = resolveSelectedPluginKey(snapshot.pluginId, snapshot.installedPlugins)
                _uiState.update {
                    it.copy(
                        schedule = snapshot.schedule,
                        pluginId = selectedPluginKey,
                        username = if (it.username.isBlank()) snapshot.username else it.username,
                        termId = snapshot.termId,
                        installedPlugins = snapshot.installedPlugins,
                        reminderRules = snapshot.reminderRules,
                        customOccupancies = snapshot.customOccupancies,
                        systemAlarmRecords = snapshot.systemAlarmRecords,
                    )
                }
                if (selectedPluginKey.isNotBlank()) {
                    loadPluginPresentation(selectedPluginKey)
                }
                migrateLegacyCourseReminderRules()
                refreshCourseNoteIndex()
                reconcileCourseNotes()
                _uiState.update { it.copy(initialized = true) }
            }
        }
    }

    fun syncSchedule(targetPluginId: String? = null) {
        val snapshot = _uiState.value
        val pluginId = targetPluginId?.takeIf { it.isNotBlank() } ?: snapshot.pluginId
        if (pluginId.isBlank()) {
            PluginLogger.warn("plugin.schedule.sync.rejected", mapOf("reason" to "missing_plugin"))
            _uiState.update { it.copy(statusMessage = text(R.string.schedule_status_no_plugin_installed)) }
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
                    statusMessage = text(R.string.schedule_status_sync_opening),
                    missingComponents = emptyList(),
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
                WorkflowExecutionResult.Failure(error.message ?: text(R.string.schedule_status_sync_start_failed))
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
            _uiState.update { it.copy(isSyncing = true, statusMessage = text(R.string.schedule_status_sync_resuming)) }
            val result = runCatching {
                withContext(ioDispatcher) {
                    pluginManager.resumeSync(
                        pluginKey = request.installKey.ifBlank { request.pluginId },
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
                WorkflowExecutionResult.Failure(error.message ?: text(R.string.schedule_status_sync_resume_failed))
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
                statusMessage = text(R.string.schedule_status_web_session_cancelled),
            )
        }
    }

    fun createReminderForCourse(courseId: String, advanceMinutes: Int, ringtoneUri: String?) {
        val state = _uiState.value
        val schedule = reminderSchedule(state)
        if (state.pluginId.isBlank()) {
            _uiState.update { it.copy(statusMessage = text(R.string.schedule_status_select_plugin_reminder)) }
            return
        }
        if (schedule == null) {
            _uiState.update { it.copy(statusMessage = text(R.string.schedule_status_no_schedule_reminder)) }
            return
        }
        viewModelScope.launch {
            val rule = createCourseReminderRule(
                state = _uiState.value,
                courseId = courseId,
                advanceMinutes = advanceMinutes.coerceIn(0, 720),
                ringtoneUri = ringtoneUri,
            ) ?: return@launch _uiState.update {
                it.copy(statusMessage = text(R.string.schedule_status_course_time_unresolved))
            }
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
                        successMessage = text(R.string.schedule_status_reminder_created, courseTitle ?: rule.ruleId.take(8)),
                        summary = dispatchSummary,
                    ),
                )
            }
        }
    }

    /** 写入或清空一门课的备注，[text] 去掉首尾空白后为空表示删除。 */
    fun setCourseNote(course: CourseItem, text: String) {
        val repository = courseNoteRepository ?: return
        val accepted = when (val result = validateCourseNote(text)) {
            is CourseNoteInput.Accepted -> result.text
            is CourseNoteInput.TooLong -> {
                _uiState.update {
                    it.copy(statusMessage = text(R.string.schedule_status_note_too_long, result.limit, result.length))
                }
                return
            }
        }
        val courses = _uiState.value.noteMatchCourses()
        viewModelScope.launch {
            runCatching { repository.setNote(courses, course, accepted) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(statusMessage = error.message ?: text(R.string.schedule_status_note_save_failed))
                    }
                }
        }
    }

    private fun refreshCourseNoteIndex() {
        if (courseNoteRepository == null) return
        _uiState.update { state ->
            state.copy(courseNotes = resolveCourseNotes(state.noteMatchCourses(), storedCourseNotes))
        }
    }

    /** 课表整体替换后把备注锚点刷新到新的课程 id 上。 */
    private fun reconcileCourseNotes() {
        val repository = courseNoteRepository ?: return
        if (storedCourseNotes.isEmpty()) return
        val courses = _uiState.value.noteMatchCourses()
        if (courses.isEmpty()) return
        viewModelScope.launch { runCatching { repository.reconcile(courses) } }
    }

    fun addManualCourse(course: CourseItem) {
        viewModelScope.launch {
            manualCourseRepository.addCourse(course)
            val dispatchSummary = reconcileTodaySystemClockAlarms(ReminderSyncReason.ScheduleChanged)
            _uiState.update {
                it.copy(
                    statusMessage = systemAlarmSyncMessage(
                        successMessage = text(R.string.schedule_status_course_added, course.title),
                        summary = dispatchSummary,
                    ),
                )
            }
        }
    }

    fun reportCourseMoveBlocked() {
        _uiState.update { it.copy(statusMessage = text(R.string.schedule_status_move_blocked)) }
    }

    fun moveManualCourse(courseId: String, time: CourseTimeSlot) {
        viewModelScope.launch {
            applyManualCourseTime(courseId, time) {
                text(R.string.schedule_status_course_moved, weekdayLabel(time.dayOfWeek), time.startNode)
            }
        }
    }

    fun resizeManualCourse(courseId: String, time: CourseTimeSlot) {
        viewModelScope.launch {
            applyManualCourseTime(courseId, time) {
                val changed = text(R.string.schedule_status_course_span_changed, time.startNode, time.endNode)
                // 改后的节次区间可能不再精确对应任何一条节次时间，提醒展开会取不到时间
                if (_uiState.value.timingProfile?.findSlot(time.startNode, time.endNode) == null) {
                    "$changed ${text(R.string.schedule_status_course_time_unresolved)}"
                } else {
                    changed
                }
            }
        }
    }

    /**
     * 改写手动课程的上课时间并落库。
     *
     * 单课与考试提醒规则把课程当时的节次范围写进了匹配条件，节次一变就匹配不上，
     * 提醒会静默失效，所以时间改动后要按新节次重建这两类规则。
     */
    private suspend fun applyManualCourseTime(
        courseId: String,
        time: CourseTimeSlot,
        successMessage: (CourseItem) -> String,
    ) {
        val courses = manualCourseRepository.manualCoursesFlow.first()
        val target = courses.firstOrNull { it.id == courseId }
        if (target == null) {
            _uiState.update { it.copy(statusMessage = text(R.string.schedule_status_move_manual_only)) }
            return
        }
        if (target.time == time) return
        val moved = target.copy(time = time)
        manualCourseRepository.replaceAll(
            courses.map { if (it.id == courseId) moved else it },
        )
        rebuildCourseScopedRules(moved)
        val dispatchSummary = reconcileTodaySystemClockAlarms(ReminderSyncReason.ScheduleChanged)
        _uiState.update {
            it.copy(
                statusMessage = systemAlarmSyncMessage(
                    successMessage = successMessage(moved),
                    summary = dispatchSummary,
                ),
            )
        }
    }

    /** 按课程当前的节次范围重建它名下的单课与考试提醒规则。 */
    private suspend fun rebuildCourseScopedRules(course: CourseItem) {
        val affected = _uiState.value.reminderRules.filter {
            it.courseId == course.id && (it.isCourseReminderRule() || it.isExamReminderRule())
        }
        affected.forEach { rule ->
            val candidate = if (rule.isExamReminderRule()) {
                examReminderCandidateScope(course)
            } else {
                courseReminderCandidateScope(course)
            }
            reminderCoordinator.saveRule(rule.copy(firstCourseCandidate = candidate))
        }
    }

    fun removeManualCourse(courseId: String) {
        viewModelScope.launch {
            manualCourseRepository.removeCourse(courseId)
            val dispatchSummary = reconcileTodaySystemClockAlarms(ReminderSyncReason.ScheduleChanged)
            _uiState.update {
                it.copy(
                    statusMessage = systemAlarmSyncMessage(
                        successMessage = text(R.string.schedule_status_manual_course_removed),
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
            var successCount = 0
            val failedTitles = mutableListOf<String>()
            courseIds.forEach { id ->
                val current = _uiState.value
                val rule = createCourseReminderRule(
                    state = current,
                    courseId = id,
                    advanceMinutes = advanceMinutes.coerceIn(0, 720),
                    ringtoneUri = ringtoneUri,
                )
                if (rule != null) {
                    successCount++
                } else {
                    failedTitles += courseTitleById(current, id) ?: id
                }
            }
            val successMessage = resources.bulkReminderStatusText(
                bulkReminderStatusMessage(
                    successCount = successCount,
                    failedTitles = failedTitles,
                    hasTimingProfile = _uiState.value.timingProfile != null,
                ),
            )
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
                            successMessage = successMessage,
                            summary = dispatchSummary,
                        ),
                    )
                }
            } else {
                _uiState.update { it.copy(statusMessage = text(R.string.schedule_status_bulk_reminder_no_schedule, successMessage)) }
            }
        }
    }

    fun loadSampleCourses() {
        viewModelScope.launch {
            manualCourseRepository.replaceAll(sampleManualCourses(resources))
            val dispatchSummary = reconcileTodaySystemClockAlarms(ReminderSyncReason.ScheduleChanged)
            _uiState.update {
                it.copy(
                    statusMessage = systemAlarmSyncMessage(
                        successMessage = text(R.string.schedule_status_sample_loaded),
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
                        successMessage = text(R.string.schedule_status_manual_cleared),
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
                        successMessage = text(R.string.schedule_status_imported_cleared),
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
                    statusMessage = text(R.string.schedule_status_all_cleared),
                    selectionState = null,
                )
            }
        }
    }

    fun applyImportedSchedule(
        schedule: TermSchedule?,
        manualCourses: List<CourseItem>,
        onComplete: (Result<Pair<Int, Int>>) -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                if (schedule != null) {
                    scheduleRepository.saveSchedule(schedule)
                }
                manualCourseRepository.replaceAll(manualCourses)
                reconcileTodaySystemClockAlarms(ReminderSyncReason.ScheduleChanged)
                val importedCourseCount = schedule?.dailySchedules?.sumOf { it.courses.size } ?: 0
                importedCourseCount to manualCourses.size
            }.let(onComplete)
        }
    }

    fun removeReminderRule(ruleId: String) {
        viewModelScope.launch {
            reminderCoordinator.deleteRule(ruleId)
            _uiState.update { it.copy(statusMessage = text(R.string.schedule_status_rule_deleted)) }
        }
    }

    fun saveExamReminder(
        enabled: Boolean,
        advanceMinutes: Int,
        ringtoneUri: String?,
    ) {
        val state = _uiState.value
        val pluginId = state.pluginId
        if (pluginId.isBlank()) {
            _uiState.update { it.copy(statusMessage = text(R.string.schedule_status_select_plugin_exam)) }
            return
        }
        viewModelScope.launch {
            val schedule = currentReminderSchedule()
            val timingProfile = _uiState.value.timingProfile ?: resolveTimingProfile()
            val pluginRules = reminderCoordinator.getRules().filter { it.pluginId == pluginId }
            // 早期版本把考试提醒写成按节次名匹配的规则，会连带命中同节次的普通课，这里一并清除
            pluginRules.filter { it.isLegacyExamLabelRule() }
                .forEach { reminderCoordinator.deleteRule(it.ruleId) }
            val exams = schedule?.dailySchedules.orEmpty()
                .flatMap { it.courses }
                .filter { it.category == CourseCategory.Exam }
            val examIds = exams.mapTo(mutableSetOf()) { it.id }
            val existingExamRules = pluginRules.filter { it.isExamReminderRule() }
            existingExamRules.filter { it.courseId.orEmpty() !in examIds }
                .forEach { reminderCoordinator.deleteRule(it.ruleId) }

            val coveredTitles = mutableListOf<String>()
            val unresolvedTitles = mutableListOf<String>()
            if (!enabled) {
                existingExamRules.filter { it.courseId.orEmpty() in examIds }
                    .forEach { reminderCoordinator.deleteRule(it.ruleId) }
            } else {
                val existingByCourse = existingExamRules.associateBy { it.courseId.orEmpty() }
                val now = OffsetDateTime.now().toString()
                exams.forEach { exam ->
                    if (timingProfile?.findSlot(exam.time.startNode, exam.time.endNode) == null) {
                        unresolvedTitles += exam.title
                        return@forEach
                    }
                    reminderCoordinator.saveRule(
                        buildExamReminderRule(
                            existing = existingByCourse[exam.id],
                            course = exam,
                            pluginId = pluginId,
                            advanceMinutes = advanceMinutes.coerceIn(0, 720),
                            ringtoneUri = ringtoneUri,
                            now = now,
                            newRuleId = UUID.randomUUID().toString(),
                        ),
                    )
                    coveredTitles += exam.title
                }
            }

            val successMessage = resources.examReminderStatusText(
                examReminderStatusMessage(
                    enabled = enabled,
                    coveredCount = coveredTitles.size,
                    unresolvedTitles = unresolvedTitles,
                ),
            )
            if (schedule != null) {
                val dispatchSummary = syncTodaySystemClockAlarms(
                    pluginId = pluginId,
                    schedule = schedule,
                    timingProfile = timingProfile,
                    reason = ReminderSyncReason.RuleCreatedToday,
                )
                _uiState.update {
                    it.copy(
                        statusMessage = systemAlarmSyncMessage(
                            successMessage = successMessage,
                            summary = dispatchSummary,
                        ),
                    )
                }
            } else {
                _uiState.update { it.copy(statusMessage = successMessage) }
            }
        }
    }

    fun muteExamReminder(courseId: String) {
        updateExamMute(courseId = courseId, muted = true)
    }

    fun restoreExamReminder(courseId: String) {
        updateExamMute(courseId = courseId, muted = false)
    }

    fun removeAlarmRecord(alarmKey: String, backend: ReminderAlarmBackend) {
        viewModelScope.launch {
            val result = reminderCoordinator.deleteAlarmRecord(alarmKey, backend)
            _uiState.update {
                it.copy(statusMessage = if (result.succeeded) result.displayText() else text(R.string.schedule_status_alarm_cancel_failed, result.displayText()))
            }
        }
    }

    fun setAppAlarmEnabled(alarmKey: String, enabled: Boolean) {
        viewModelScope.launch {
            val result = reminderCoordinator.setAppAlarmEnabled(alarmKey, enabled)
            _uiState.update {
                it.copy(statusMessage = if (result.succeeded) result.displayText() else text(R.string.schedule_status_alarm_toggle_failed, result.displayText()))
            }
        }
    }

    fun updateAppAlarmSettings(alarmKey: String, settings: EditableAppAlarmSettings) {
        viewModelScope.launch {
            val result = reminderCoordinator.updateAppAlarmSettings(alarmKey, settings)
            _uiState.update {
                it.copy(statusMessage = if (result.succeeded) text(R.string.schedule_status_alarm_settings_updated) else text(R.string.schedule_status_alarm_settings_failed, result.displayText()))
            }
        }
    }

    fun createManualAppAlarm(
        triggerAtMillis: Long,
        title: String,
        message: String,
        settings: EditableAppAlarmSettings,
    ) {
        val pluginId = _uiState.value.pluginId.ifBlank { "manual" }
        viewModelScope.launch {
            val result = reminderCoordinator.createManualAppAlarm(
                pluginId = pluginId,
                triggerAtMillis = triggerAtMillis,
                title = title.ifBlank { text(ReminderR.string.reminder_manual_alarm_default_title) },
                message = message.ifBlank { text(ReminderR.string.reminder_manual_alarm_default_message) },
                settings = settings,
            )
            _uiState.update {
                it.copy(statusMessage = if (result.succeeded) text(R.string.schedule_status_manual_alarm_created) else text(R.string.schedule_status_manual_alarm_failed, result.displayText()))
            }
        }
    }

    private fun updateExamMute(courseId: String, muted: Boolean) {
        val state = _uiState.value
        val pluginId = state.pluginId
        if (pluginId.isBlank()) {
            _uiState.update { it.copy(statusMessage = text(R.string.schedule_status_select_plugin_exam_mute)) }
            return
        }
        val rule = state.reminderRules.firstOrNull {
            it.pluginId == pluginId && it.isExamReminderRule() && it.courseId == courseId
        }
        if (rule == null) {
            _uiState.update { it.copy(statusMessage = text(R.string.schedule_status_enable_exam_first)) }
            return
        }
        val courseTitle = courseTitleById(state, courseId)
        viewModelScope.launch {
            val nextMutedCourseIds = if (muted) {
                (rule.mutedCourseIds + courseId).distinct()
            } else {
                rule.mutedCourseIds.filterNot { it == courseId }
            }
            reminderCoordinator.saveRule(
                rule.copy(
                    mutedCourseIds = nextMutedCourseIds,
                    updatedAt = OffsetDateTime.now().toString(),
                ),
            )
            reminderCoordinator.setRuleEnabled(rule.ruleId, !muted)
            val schedule = currentReminderSchedule()
            val summary = if (schedule != null) {
                syncTodaySystemClockAlarms(
                    pluginId = pluginId,
                    schedule = schedule,
                    timingProfile = _uiState.value.timingProfile,
                    reason = ReminderSyncReason.ScheduleChanged,
                )
            } else {
                emptySystemAlarmSyncSummary()
            }
            val label = courseTitle ?: text(R.string.schedule_status_exam_fallback_label)
            _uiState.update {
                it.copy(
                    statusMessage = systemAlarmSyncMessage(
                        successMessage = if (muted) {
                            text(R.string.schedule_status_exam_muted, label)
                        } else {
                            text(R.string.schedule_status_exam_restored, label)
                        },
                        summary = summary,
                    ),
                )
            }
        }
    }

    fun refreshReminderAlarmsNow() {
        viewModelScope.launch {
            val summary = reconcileTodaySystemClockAlarms(ReminderSyncReason.ScheduleChanged)
            _uiState.update {
                it.copy(
                    statusMessage = systemAlarmSyncMessage(
                        successMessage = text(R.string.schedule_status_alarms_refreshed),
                        summary = summary,
                    ),
                )
            }
        }
    }

    fun saveLabelReminderRule(
        ruleId: String?,
        displayName: String,
        enabled: Boolean,
        advanceMinutes: Int,
        ringtoneUri: String?,
        conditions: List<ReminderLabelCondition>,
        actions: List<ReminderLabelAction>,
    ) {
        val state = _uiState.value
        val pluginId = state.pluginId
        if (pluginId.isBlank()) {
            _uiState.update { it.copy(statusMessage = text(R.string.schedule_status_select_plugin_rule)) }
            return
        }
        val isNewRule = ruleId == null
        viewModelScope.launch {
            val rule = reminderCoordinator.upsertLabelRule(
                pluginId = pluginId,
                ruleId = ruleId,
                displayName = displayName,
                enabled = enabled,
                advanceMinutes = advanceMinutes.coerceIn(0, 720),
                ringtoneUri = ringtoneUri,
                labelConditions = conditions,
                labelActions = actions,
            )
            val schedule = currentReminderSchedule()
            val timingProfile = _uiState.value.timingProfile ?: resolveTimingProfile()
            val summary = if (schedule != null) {
                if (isNewRule) {
                    onAlarmSyncChecked()
                    reminderCoordinator.syncNearestAlarmForRule(
                        pluginId = pluginId,
                        ruleId = rule.ruleId,
                        schedule = schedule,
                        timingProfile = timingProfile,
                        reason = ReminderSyncReason.RuleCreatedToday,
                    )
                } else {
                    syncTodaySystemClockAlarms(
                        pluginId = pluginId,
                        schedule = schedule,
                        timingProfile = timingProfile,
                        reason = ReminderSyncReason.RuleCreatedToday,
                    )
                }
            } else {
                emptySystemAlarmSyncSummary()
            }
            _uiState.update {
                it.copy(
                    statusMessage = systemAlarmSyncMessage(
                        successMessage = text(R.string.schedule_status_rule_saved, rule.displayName ?: rule.ruleId.take(8)),
                        summary = summary,
                    ),
                )
            }
        }
    }

    fun setReminderRuleEnabled(ruleId: String, enabled: Boolean) {
        viewModelScope.launch {
            reminderCoordinator.setRuleEnabled(ruleId, enabled)
            val summary = reconcileTodaySystemClockAlarms(ReminderSyncReason.ScheduleChanged)
            _uiState.update {
                it.copy(
                    statusMessage = systemAlarmSyncMessage(
                        successMessage = if (enabled) text(R.string.schedule_status_rule_enabled) else text(R.string.schedule_status_rule_disabled),
                        summary = summary,
                    ),
                )
            }
        }
    }

    fun savePlaceholderCourse(
        courseId: String?,
        label: String,
        startTime: String,
        endTime: String,
        weeks: List<Int>,
        daysOfWeek: List<Int>,
        title: String?,
    ) {
        val normalizedLabel = label.trim()
        if (normalizedLabel.isBlank()) {
            _uiState.update { it.copy(statusMessage = text(R.string.schedule_status_placeholder_name_empty)) }
            return
        }
        val id = courseId ?: "placeholder-${UUID.randomUUID()}"
        val groupId = id.placeholderGroupId()
        val days = daysOfWeek.filter { it in 1..7 }.distinct().sorted().ifEmpty { (1..5).toList() }
        viewModelScope.launch {
            val existing = manualCourseRepository.manualCoursesFlow.first()
                .filterNot { it.id.placeholderGroupId() == groupId }
            val placeholders = days.map { day ->
                CourseItem(
                    id = if (days.size == 1) groupId else "$groupId-$day",
                    title = title?.takeIf { it.isNotBlank() } ?: normalizedLabel,
                    weeks = weeks.distinct().sorted(),
                    time = CourseTimeSlot(dayOfWeek = day, startNode = 1, endNode = 1),
                    reminderOnly = true,
                    slotLabelOverride = normalizedLabel,
                    reminderStartTime = startTime,
                    reminderEndTime = endTime,
                )
            }
            manualCourseRepository.replaceAll(existing + placeholders)
            val summary = reconcileTodaySystemClockAlarms(ReminderSyncReason.ScheduleChanged)
            _uiState.update {
                it.copy(
                    statusMessage = systemAlarmSyncMessage(
                        successMessage = text(R.string.schedule_status_placeholder_saved, normalizedLabel),
                        summary = summary,
                    ),
                )
            }
        }
    }

    fun deletePlaceholderCourse(courseId: String) {
        viewModelScope.launch {
            val groupId = courseId.placeholderGroupId()
            val next = manualCourseRepository.manualCoursesFlow.first()
                .filterNot { it.id.placeholderGroupId() == groupId }
            manualCourseRepository.replaceAll(next)
            val summary = reconcileTodaySystemClockAlarms(ReminderSyncReason.ScheduleChanged)
            _uiState.update {
                it.copy(
                    statusMessage = systemAlarmSyncMessage(
                        successMessage = text(R.string.schedule_status_placeholder_deleted),
                        summary = summary,
                    ),
                )
            }
        }
    }

    fun saveCustomOccupancy(
        occupancyId: String?,
        name: String,
        timeRange: ReminderTimeRange,
        daysOfWeek: List<Int>,
        weeks: List<Int>,
        includeDates: List<String>,
        excludeDates: List<String>,
        linkedNodeRange: ReminderNodeRange?,
    ) {
        val state = _uiState.value
        val pluginId = state.pluginId
        if (pluginId.isBlank()) {
            _uiState.update { it.copy(statusMessage = text(R.string.schedule_status_select_plugin_occupancy)) }
            return
        }
        viewModelScope.launch {
            val occupancy = reminderCoordinator.upsertCustomOccupancy(
                pluginId = pluginId,
                occupancyId = occupancyId,
                name = name,
                timeRange = timeRange,
                daysOfWeek = daysOfWeek,
                weeks = weeks,
                includeDates = includeDates,
                excludeDates = excludeDates,
                linkedNodeRange = linkedNodeRange,
            )
            val summary = reconcileTodaySystemClockAlarms(ReminderSyncReason.ScheduleChanged)
            _uiState.update {
                it.copy(
                    statusMessage = systemAlarmSyncMessage(
                        successMessage = text(R.string.schedule_status_occupancy_saved, occupancy.name),
                        summary = summary,
                    ),
                )
            }
        }
    }

    fun removeCustomOccupancy(occupancyId: String) {
        viewModelScope.launch {
            reminderCoordinator.removeCustomOccupancy(occupancyId)
            val summary = reconcileTodaySystemClockAlarms(ReminderSyncReason.ScheduleChanged)
            _uiState.update {
                it.copy(
                    statusMessage = systemAlarmSyncMessage(
                        successMessage = text(R.string.schedule_status_occupancy_deleted),
                        summary = summary,
                    ),
                )
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
        onAlarmSyncChecked()
        return reminderCoordinator.syncAlarmsForWindow(
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
            if (summary.expiredRecordClearedCount > 0) {
                add(text(R.string.schedule_status_alarm_expired_cleared, summary.expiredRecordClearedCount))
            }
            if (summary.dismissedCount > 0) {
                add(text(R.string.schedule_status_alarm_dismissed, summary.dismissedCount))
            }
            if (summary.dismissFailedCount > 0) {
                add(text(R.string.schedule_status_alarm_dismiss_failed, summary.dismissFailedCount))
            }
            if (summary.createdCount > 0) {
                add(text(R.string.schedule_status_alarm_created_count, summary.createdCount))
            }
            if (summary.skippedExistingCount > 0) {
                add(text(R.string.schedule_status_alarm_skipped_existing, summary.skippedExistingCount))
            }
            if (summary.skippedUnrepresentableCount > 0) {
                add(text(R.string.schedule_status_alarm_skipped_unrepresentable, summary.skippedUnrepresentableCount))
            }
            if (summary.failedCount > 0) {
                add(
                    text(
                        R.string.schedule_status_alarm_failed_count,
                        summary.failedCount,
                        summary.results.first { !it.succeeded }.displayText(),
                    ),
                )
            }
        }
        return if (details.isEmpty()) {
            text(R.string.schedule_status_alarm_sync_none, successMessage)
        } else {
            text(
                R.string.schedule_status_alarm_sync_details,
                successMessage,
                details.joinToString(text(R.string.schedule_status_alarm_detail_separator)),
            )
        }
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
                    // 插件没有节次时间表时保留已有的，用户手填的版本不能被覆盖成空
                    timingProfile = timingProfile ?: it.timingProfile,
                )
            }
            // 把节次时间配置镜像到小组件存储，用户尚未重新同步时桌面小组件也能解析出真实上课时间。
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
                    val detail = resources.scheduleValidationErrorText(error)
                        ?: error.message
                        ?: text(R.string.schedule_status_sync_invalid_schedule)
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            pendingWebSession = null,
                            statusMessage = text(R.string.schedule_status_sync_failed, detail),
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
                            statusMessage = text(R.string.schedule_status_sync_failed, error.message ?: text(R.string.schedule_status_sync_postprocess_failed)),
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
                        timingProfile = syncedTimingProfile ?: it.timingProfile,
                        alarmRecommendations = result.recommendations,
                        messages = result.messages,
                        missingComponents = emptyList(),
                        statusMessage = text(R.string.schedule_status_sync_completed),
                        syncCompletedCount = it.syncCompletedCount + 1,
                    )
                }
            }

            is WorkflowExecutionResult.NeedsComponents -> {
                val componentIds = result.components.joinToString { it.id }
                PluginLogger.warn(
                    "plugin.schedule.result.needs_components",
                    mapOf(
                        "pluginId" to result.pluginId,
                        "componentCount" to result.components.size,
                        "componentIds" to componentIds,
                        "elapsedMs" to (System.currentTimeMillis() - startedAt),
                    ),
                )
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        pendingWebSession = null,
                        missingComponents = result.components,
                        messages = listOf(result.message),
                        statusMessage = if (componentIds.isBlank()) {
                            result.message
                        } else {
                            text(R.string.schedule_status_missing_components, result.message, componentIds)
                        },
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
                        missingComponents = emptyList(),
                        statusMessage = text(R.string.schedule_status_web_session_pending),
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
                        missingComponents = emptyList(),
                        statusMessage = text(R.string.schedule_status_sync_failed, result.message),
                    )
                }
            }
        }
    }

    /** 单课提醒：候选范围锁死到这门课，同节次的其他课不会被带上。 */
    private suspend fun createCourseReminderRule(
        state: ScheduleUiState,
        courseId: String,
        advanceMinutes: Int,
        ringtoneUri: String?,
    ): ReminderRule? {
        val timingProfile = state.timingProfile ?: return null
        val course = (state.schedule?.dailySchedules.orEmpty().flatMap { it.courses } + state.manualCourses)
            .firstOrNull { it.id == courseId }
            ?: return null
        if (timingProfile.findSlot(course.time.startNode, course.time.endNode) == null) return null
        val existing = reminderCoordinator.getRules().firstOrNull {
            it.pluginId == state.pluginId && it.isCourseReminderRule() && it.courseId == course.id
        }
        val rule = buildCourseReminderRule(
            existing = existing,
            course = course,
            pluginId = state.pluginId,
            advanceMinutes = advanceMinutes.coerceIn(0, 720),
            ringtoneUri = ringtoneUri,
            now = OffsetDateTime.now().toString(),
            newRuleId = UUID.randomUUID().toString(),
        )
        reminderCoordinator.saveRule(rule)
        return rule
    }

    /** 把旧版按节次名匹配的单课规则就地升级成锁定课程的规则。 */
    private suspend fun migrateLegacyCourseReminderRules() {
        val state = _uiState.value
        val timingProfile = state.timingProfile ?: return
        val courses = state.schedule?.dailySchedules.orEmpty().flatMap { it.courses } + state.manualCourses
        if (courses.isEmpty()) return
        planLegacyCourseReminderMigration(
            rules = state.reminderRules,
            courses = courses,
            timingProfile = timingProfile,
            now = OffsetDateTime.now().toString(),
        ).forEach { reminderCoordinator.saveRule(it) }
    }

    private data class KernelSnapshot(
        val schedule: TermSchedule?,
        val pluginId: String,
        val username: String,
        val termId: String,
        val installedPlugins: List<InstalledPluginRecord>,
        val reminderRules: List<ReminderRule>,
        val customOccupancies: List<ReminderCustomOccupancy> = emptyList(),
        val systemAlarmRecords: List<SystemAlarmRecord> = emptyList(),
    )

    private data class BaseSnapshot(
        val schedule: TermSchedule?,
        val pluginId: String,
        val username: String,
        val termId: String,
    )
}

private fun resolveSelectedPluginKey(
    savedPluginId: String,
    installedPlugins: List<InstalledPluginRecord>,
): String {
    val normalized = savedPluginId.trim()
    if (normalized.isBlank()) {
        return installedPlugins.firstOrNull()?.installKey.orEmpty()
    }
    installedPlugins.firstOrNull { it.installKey == normalized }?.let {
        return it.installKey
    }
    installedPlugins.firstOrNull { it.pluginId == normalized }?.let {
        return it.installKey
    }
    return normalized
}

internal fun validatePluginSchedule(schedule: TermSchedule): TermSchedule {
    val courses = schedule.dailySchedules.flatMap { daily ->
        scheduleValidationRequire(daily.dayOfWeek in 1..7, R.string.schedule_validation_invalid_weekday, daily.dayOfWeek)
        daily.courses.onEach { course ->
            scheduleValidationRequire(course.id.isNotBlank(), R.string.schedule_validation_blank_course_id)
            scheduleValidationRequire(course.title.isNotBlank(), R.string.schedule_validation_blank_course_title)
            scheduleValidationRequire(course.time.dayOfWeek in 1..7, R.string.schedule_validation_invalid_course_weekday, course.time.dayOfWeek)
            scheduleValidationRequire(course.time.dayOfWeek == daily.dayOfWeek, R.string.schedule_validation_weekday_mismatch)
            scheduleValidationRequire(course.time.startNode in 1..32, R.string.schedule_validation_invalid_start_node, course.time.startNode)
            scheduleValidationRequire(course.time.endNode in course.time.startNode..32, R.string.schedule_validation_invalid_end_node, course.time.endNode)
            scheduleValidationRequire(course.weeks.all { it in 1..60 }, R.string.schedule_validation_invalid_weeks)
        }
    }
    scheduleValidationRequire(courses.size <= 1000, R.string.schedule_validation_too_many_courses)
    return schedule
}

private fun reminderSchedule(state: ScheduleUiState): TermSchedule? =
    mergeManualCoursesForReminders(state.schedule, state.manualCourses)

private fun courseTitleById(state: ScheduleUiState, courseId: String): String? =
    (state.schedule?.dailySchedules.orEmpty().flatMap { it.courses } + state.manualCourses)
        .firstOrNull { it.id == courseId }
        ?.title

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

internal fun String.placeholderGroupId(): String =
    replace(Regex("""(?:-[1-7])+$"""), "").lowercase(Locale.ROOT)

internal const val EXAM_RULE_PREFIX = "考试提醒："

internal const val COURSE_RULE_PREFIX = "课程提醒："

private const val LEGACY_COURSE_RULE_PREFIX = "提醒 "

/** 每门课独占一条规则，候选范围锁死到这门课自身的节次、类别和名称。 */
internal fun courseReminderCandidateScope(course: CourseItem): FirstCourseCandidateScope =
    FirstCourseCandidateScope(
        nodeRange = ReminderNodeRange(course.time.startNode, course.time.endNode).normalized(),
        categories = listOf(course.category),
        titleContains = course.title.takeIf { it.isNotBlank() },
    )

internal fun examReminderCandidateScope(course: CourseItem): FirstCourseCandidateScope =
    courseReminderCandidateScope(course).copy(categories = listOf(CourseCategory.Exam))

internal fun ReminderRule.isExamReminderRule(): Boolean =
    scopeType == ReminderScopeType.FirstCourseOfPeriod &&
        !courseId.isNullOrBlank() &&
        firstCourseCandidate?.categories == listOf(CourseCategory.Exam) &&
        displayName?.startsWith(EXAM_RULE_PREFIX) == true

/** 用户给某一门课单独设的提醒。 */
internal fun ReminderRule.isCourseReminderRule(): Boolean =
    scopeType == ReminderScopeType.FirstCourseOfPeriod &&
        !courseId.isNullOrBlank() &&
        firstCourseCandidate != null &&
        displayName?.startsWith(COURSE_RULE_PREFIX) == true

/** 旧版按节次名匹配的考试规则，会波及同节次的普通课。 */
internal fun ReminderRule.isLegacyExamLabelRule(): Boolean =
    scopeType == ReminderScopeType.LabelRule && displayName?.startsWith(EXAM_RULE_PREFIX) == true

internal fun examReminderEnabled(rules: List<ReminderRule>): Boolean =
    rules.any { it.isExamReminderRule() }

private fun buildCourseScopedReminderRule(
    existing: ReminderRule?,
    course: CourseItem,
    pluginId: String,
    displayName: String,
    candidate: FirstCourseCandidateScope,
    advanceMinutes: Int,
    ringtoneUri: String?,
    now: String,
    newRuleId: String,
): ReminderRule {
    val mutedCourseIds = existing?.mutedCourseIds.orEmpty()
    val base = existing ?: ReminderRule(
        ruleId = newRuleId,
        pluginId = pluginId,
        scopeType = ReminderScopeType.FirstCourseOfPeriod,
        advanceMinutes = advanceMinutes,
        createdAt = now,
        updatedAt = now,
    )
    return base.copy(
        pluginId = pluginId,
        scopeType = ReminderScopeType.FirstCourseOfPeriod,
        courseId = course.id,
        displayName = displayName,
        firstCourseCandidate = candidate,
        periodStartNode = candidate.nodeRange?.startNode,
        periodEndNode = candidate.nodeRange?.endNode,
        period = null,
        conditions = emptyList(),
        actions = emptyList(),
        labelConditions = emptyList(),
        labelActions = emptyList(),
        mutedNodeRanges = emptyList(),
        mutedCourseIds = mutedCourseIds,
        advanceMinutes = advanceMinutes,
        ringtoneUri = ringtoneUri,
        enabled = course.id !in mutedCourseIds,
        updatedAt = now,
    )
}

internal fun buildExamReminderRule(
    existing: ReminderRule?,
    course: CourseItem,
    pluginId: String,
    advanceMinutes: Int,
    ringtoneUri: String?,
    now: String,
    newRuleId: String,
): ReminderRule = buildCourseScopedReminderRule(
    existing = existing,
    course = course,
    pluginId = pluginId,
    displayName = "$EXAM_RULE_PREFIX${course.title}",
    candidate = examReminderCandidateScope(course),
    advanceMinutes = advanceMinutes,
    ringtoneUri = ringtoneUri,
    now = now,
    newRuleId = newRuleId,
)

internal fun buildCourseReminderRule(
    existing: ReminderRule?,
    course: CourseItem,
    pluginId: String,
    advanceMinutes: Int,
    ringtoneUri: String?,
    now: String,
    newRuleId: String,
): ReminderRule = buildCourseScopedReminderRule(
    existing = existing,
    course = course,
    pluginId = pluginId,
    displayName = "$COURSE_RULE_PREFIX${course.title}",
    candidate = courseReminderCandidateScope(course),
    advanceMinutes = advanceMinutes,
    ringtoneUri = ringtoneUri,
    now = now,
    newRuleId = newRuleId,
)

/**
 * 旧版单课提醒写成了按节次名匹配的规则，形状是「某节次存在 → 提醒某节次」，
 * 名称里带的却是课程名。能唯一对上一门课时返回那门课，对不上就返回 null。
 */
internal fun ReminderRule.legacyCourseReminderTarget(
    courses: List<CourseItem>,
    timingProfile: TermTimingProfile,
): CourseItem? {
    if (scopeType != ReminderScopeType.LabelRule) return null
    val action = labelActions.singleOrNull()?.takeIf { it.action == ReminderLabelActionType.Remind } ?: return null
    val condition = labelConditions.singleOrNull() ?: return null
    if (condition.presence != ReminderLabelPresence.Exists) return null
    if (condition.slotLabel != action.slotLabel) return null
    val title = displayName
        ?.takeIf { it.startsWith(LEGACY_COURSE_RULE_PREFIX) }
        ?.removePrefix(LEGACY_COURSE_RULE_PREFIX)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    // 名称与节次名一致的是按节次创建的规则，本来就该按节次匹配
    if (title == action.slotLabel) return null
    val matches = courses.filter { course ->
        course.title == title &&
            course.reminderSlotLabel(timingProfile) == action.slotLabel &&
            timingProfile.findSlot(course.time.startNode, course.time.endNode) != null
    }
    if (matches.isEmpty()) return null
    if (matches.map(::courseReminderCandidateScope).distinct().size != 1) return null
    return matches.first()
}

internal fun planLegacyCourseReminderMigration(
    rules: List<ReminderRule>,
    courses: List<CourseItem>,
    timingProfile: TermTimingProfile,
    now: String,
): List<ReminderRule> = rules.mapNotNull { rule ->
    val course = rule.legacyCourseReminderTarget(courses, timingProfile) ?: return@mapNotNull null
    buildCourseReminderRule(
        existing = rule,
        course = course,
        pluginId = rule.pluginId,
        advanceMinutes = rule.advanceMinutes,
        ringtoneUri = rule.ringtoneUri,
        now = now,
        newRuleId = rule.ruleId,
    ).copy(enabled = rule.enabled)
}

/** 提醒创建失败时最多列出几个课程标题。 */
private const val REMINDER_TITLE_PREVIEW_LIMIT = 3

/** 没能建成提醒的课程，[titles] 最多保留 3 个，[totalCount] 是全部数量。 */
internal data class ReminderTitlePreview(
    val titles: List<String>,
    val totalCount: Int,
)

private fun reminderTitlePreview(titles: List<String>): ReminderTitlePreview = ReminderTitlePreview(
    titles = titles.take(REMINDER_TITLE_PREVIEW_LIMIT),
    totalCount = titles.size,
)

private fun Context.reminderTitlePreviewText(preview: ReminderTitlePreview, overflowRes: Int): String {
    val joined = preview.titles.joinToString(getString(R.string.schedule_reminder_title_separator))
    return if (preview.totalCount > preview.titles.size) {
        joined + getString(overflowRes, preview.totalCount)
    } else {
        joined
    }
}

/** 批量创建课程提醒的结果。 */
internal sealed interface BulkReminderStatus {
    /** 选中的课程全部建好了提醒。 */
    data class AllCreated(val successCount: Int) : BulkReminderStatus

    /** 一部分建好了，另一部分因为节次问题没建成。 */
    data class PartiallyCreated(
        val successCount: Int,
        val failed: ReminderTitlePreview,
        val hasTimingProfile: Boolean,
    ) : BulkReminderStatus

    /** 一门都没建成。 */
    data class NoneCreated(
        val failed: ReminderTitlePreview,
        val hasTimingProfile: Boolean,
    ) : BulkReminderStatus
}

internal fun bulkReminderStatusMessage(
    successCount: Int,
    failedTitles: List<String>,
    hasTimingProfile: Boolean,
): BulkReminderStatus {
    if (failedTitles.isEmpty()) return BulkReminderStatus.AllCreated(successCount)
    val failed = reminderTitlePreview(failedTitles)
    return if (successCount == 0) {
        BulkReminderStatus.NoneCreated(failed, hasTimingProfile)
    } else {
        BulkReminderStatus.PartiallyCreated(successCount, failed, hasTimingProfile)
    }
}

private fun Context.bulkReminderFailureText(
    failed: ReminderTitlePreview,
    hasTimingProfile: Boolean,
): String {
    val reason = if (hasTimingProfile) {
        getString(R.string.schedule_bulk_reminder_reason_mismatch)
    } else {
        getString(R.string.schedule_bulk_reminder_reason_no_profile)
    }
    val titles = reminderTitlePreviewText(failed, R.string.schedule_bulk_reminder_failure_overflow)
    return getString(R.string.schedule_bulk_reminder_failure, titles, reason)
}

internal fun Context.bulkReminderStatusText(status: BulkReminderStatus): String = when (status) {
    is BulkReminderStatus.AllCreated ->
        getString(R.string.schedule_bulk_reminder_all_created, status.successCount)

    is BulkReminderStatus.NoneCreated -> getString(
        R.string.schedule_bulk_reminder_none,
        bulkReminderFailureText(status.failed, status.hasTimingProfile),
    )

    is BulkReminderStatus.PartiallyCreated -> getString(
        R.string.schedule_bulk_reminder_partial,
        status.successCount,
        bulkReminderFailureText(status.failed, status.hasTimingProfile),
    )
}

/** 开关考试提醒后的结果。 */
internal sealed interface ExamReminderStatus {
    /** 提醒被关掉了。 */
    data object Disabled : ExamReminderStatus

    /** 提醒已开启，但课表里没有考试。 */
    data object NoExams : ExamReminderStatus

    /** 所有考试都建好了提醒。 */
    data class AllCovered(val coveredCount: Int) : ExamReminderStatus

    /** 一部分考试建好了提醒，另一部分节次不在时间表内。 */
    data class PartiallyCovered(
        val coveredCount: Int,
        val skipped: ReminderTitlePreview,
    ) : ExamReminderStatus

    /** 一场都没建成。 */
    data class NoneCovered(val skipped: ReminderTitlePreview) : ExamReminderStatus
}

internal fun examReminderStatusMessage(
    enabled: Boolean,
    coveredCount: Int,
    unresolvedTitles: List<String>,
): ExamReminderStatus {
    if (!enabled) return ExamReminderStatus.Disabled
    val skipped = unresolvedTitles.takeIf { it.isNotEmpty() }?.let(::reminderTitlePreview)
    return when {
        coveredCount == 0 && skipped != null -> ExamReminderStatus.NoneCovered(skipped)
        coveredCount == 0 -> ExamReminderStatus.NoExams
        skipped != null -> ExamReminderStatus.PartiallyCovered(coveredCount, skipped)
        else -> ExamReminderStatus.AllCovered(coveredCount)
    }
}

private fun Context.examReminderSkippedText(skipped: ReminderTitlePreview): String = getString(
    R.string.schedule_exam_reminder_skipped,
    reminderTitlePreviewText(skipped, R.string.schedule_exam_reminder_skipped_overflow),
)

internal fun Context.examReminderStatusText(status: ExamReminderStatus): String = when (status) {
    ExamReminderStatus.Disabled -> getString(R.string.schedule_exam_reminder_disabled)
    ExamReminderStatus.NoExams -> getString(R.string.schedule_exam_reminder_no_exams)
    is ExamReminderStatus.AllCovered ->
        getString(R.string.schedule_exam_reminder_covered, status.coveredCount)

    is ExamReminderStatus.NoneCovered -> getString(
        R.string.schedule_exam_reminder_none,
        examReminderSkippedText(status.skipped),
    )

    is ExamReminderStatus.PartiallyCovered -> getString(
        R.string.schedule_exam_reminder_partial,
        status.coveredCount,
        examReminderSkippedText(status.skipped),
    )
}

class ScheduleViewModelFactory(
    private val appContext: Context,
    private val scheduleRepository: ScheduleRepository,
    private val pluginManager: PluginManager,
    private val reminderCoordinator: ReminderCoordinator,
    private val manualCourseRepository: ManualCourseRepository,
    private val courseNoteRepository: CourseNoteRepository? = null,
    private val normalizeTimingProfile: suspend (TermTimingProfile?) -> TermTimingProfile? = { it },
    private val onSyncCompleted: suspend (TermTimingProfile?) -> Unit = {},
    private val onAlarmSyncChecked: suspend () -> Unit = {},
    private val resolveTimingProfile: suspend () -> TermTimingProfile? = { null },
    private val timingProfileFlow: Flow<TermTimingProfile?> = flowOf(null),
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScheduleViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ScheduleViewModel(
                appContext = appContext,
                scheduleRepository = scheduleRepository,
                pluginManager = pluginManager,
                reminderCoordinator = reminderCoordinator,
                manualCourseRepository = manualCourseRepository,
                courseNoteRepository = courseNoteRepository,
                normalizeTimingProfile = normalizeTimingProfile,
                onSyncCompleted = onSyncCompleted,
                onAlarmSyncChecked = onAlarmSyncChecked,
                resolveTimingProfile = resolveTimingProfile,
                timingProfileFlow = timingProfileFlow,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
