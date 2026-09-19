package com.x500x.cursimple.feature.schedule

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.foundation.verticalScroll
import com.x500x.cursimple.core.kernel.time.ScheduleRowFitMode
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x500x.cursimple.core.data.ScheduleBackgroundPreferences
import com.x500x.cursimple.core.data.ScheduleBackgroundType
import com.x500x.cursimple.core.data.ScheduleCardStylePreferences
import com.x500x.cursimple.core.data.ScheduleDisplayPreferences
import com.x500x.cursimple.core.data.ScheduleTextStylePreferences
import com.x500x.cursimple.core.data.adaptScheduleBackgroundColorArgb
import com.x500x.cursimple.core.data.adaptScheduleForegroundColorArgb
import com.x500x.cursimple.core.data.widget.classSlotLabelOfBlock
import com.x500x.cursimple.core.data.widget.classSlotLabelOfIndex
import com.x500x.cursimple.core.data.widget.classSlotLabelText
import com.x500x.cursimple.core.data.widget.slotBlockIndex
import androidx.compose.runtime.CompositionLocalProvider
import com.x500x.cursimple.feature.schedule.theme.LocalScheduleLocationSuffix
import com.x500x.cursimple.core.kernel.model.stripLocationSuffix
import com.x500x.cursimple.core.kernel.model.sharedLocationSuffix
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.allCoursesWith
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.HolidayCalendarEntry
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.HolidayEntryKind
import com.x500x.cursimple.core.kernel.model.userEntryOn
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverrideType
import com.x500x.cursimple.core.kernel.model.cancelsCourseOn
import com.x500x.cursimple.core.kernel.model.isActiveInTermWeekNumber
import com.x500x.cursimple.core.kernel.model.isCourseTemporarilyCancelled
import com.x500x.cursimple.core.kernel.model.reminderSlotLabel
import com.x500x.cursimple.core.kernel.model.ScheduleDayResolution
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import com.x500x.cursimple.core.kernel.model.resolveTermWeekNumber
import com.x500x.cursimple.core.kernel.model.visibleScheduleCourses
import com.x500x.cursimple.core.kernel.model.weekdayNameRes
import com.x500x.cursimple.core.kernel.model.startLocalTime
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.data.note.CourseNoteIndex
import com.x500x.cursimple.core.kernel.time.WeekStartDay
import com.x500x.cursimple.core.kernel.time.columnDate
import com.x500x.cursimple.core.kernel.time.columnDayOfWeeks
import com.x500x.cursimple.core.kernel.time.displayWeekStartOf
import com.x500x.cursimple.core.kernel.time.displayWeekTermIndex
import com.x500x.cursimple.core.plugin.ui.CourseBadgeRule
import com.x500x.cursimple.core.plugin.ui.PluginUiSchema
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderScopeType
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import com.x500x.cursimple.feature.schedule.time.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

enum class ScheduleViewMode { Week, Day }

@Composable
fun ScheduleRoute(
    viewModel: ScheduleViewModel,
    onOpenPluginMarket: () -> Unit,
    weekOffset: Int,
    minWeekOffset: Int,
    maxWeekOffset: Int,
    onAddWeek: (() -> Unit)? = null,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onWeekOffsetChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    overrideTermStart: LocalDate? = null,
    viewMode: ScheduleViewMode = ScheduleViewMode.Week,
    dayOffset: Int = 0,
    onPrevDay: () -> Unit = {},
    onNextDay: () -> Unit = {},
    onDayOffsetChange: (Int) -> Unit = {},
    onResetDay: () -> Unit = {},
    scheduleTextStyle: ScheduleTextStylePreferences = ScheduleTextStylePreferences(),
    scheduleCardStyle: ScheduleCardStylePreferences = ScheduleCardStylePreferences(),
    scheduleBackground: ScheduleBackgroundPreferences = ScheduleBackgroundPreferences(),
    scheduleDisplay: ScheduleDisplayPreferences = ScheduleDisplayPreferences(),
    customColorsAdaptToTheme: Boolean = false,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride> = emptyList(),
    holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
    onUpsertTemporaryScheduleOverride: (TemporaryScheduleOverride) -> Unit = {},
    onRemoveTemporaryScheduleOverride: (String) -> Unit = {},
    onUpsertHolidayEntry: (HolidayCalendarEntry) -> Unit = {},
    onRemoveHolidayEntry: (LocalDate) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 拖动是易误触的操作，改动先记在这里等用户确认，确认前不落库
    var pendingDrag by remember { mutableStateOf<PendingCourseDrag?>(null) }
    pendingDrag?.let { pending ->
        CourseDragConfirmDialog(
            pending = pending,
            onDismiss = { pendingDrag = null },
            onConfirm = {
                when (pending.kind) {
                    PendingCourseDrag.Kind.Move -> viewModel.moveManualCourse(pending.courseId, pending.time)
                    PendingCourseDrag.Kind.Resize -> viewModel.resizeManualCourse(pending.courseId, pending.time)
                }
                pendingDrag = null
            },
        )
    }
    ScheduleScreen(
        state = state,
        weekOffset = weekOffset,
        minWeekOffset = minWeekOffset,
        maxWeekOffset = maxWeekOffset,
        onAddWeek = onAddWeek,
        overrideTermStart = overrideTermStart,
        viewMode = viewMode,
        dayOffset = dayOffset,
        onDayOffsetChange = onDayOffsetChange,
        onCreateCourseReminder = viewModel::createReminderForCourse,
        onMuteExamReminder = viewModel::muteExamReminder,
        onRestoreExamReminder = viewModel::restoreExamReminder,
        onRemoveReminderRule = viewModel::removeReminderRule,
        onRemoveManualCourse = viewModel::removeManualCourse,
        onAddManualCourse = viewModel::addManualCourse,
        onSaveCourse = viewModel::updateManualCourse,
        onMoveManualCourse = { id, time ->
            pendingDrag = PendingCourseDrag(id, courseTitleOf(state, id), time, PendingCourseDrag.Kind.Move)
        },
        onResizeManualCourse = { id, time ->
            pendingDrag = PendingCourseDrag(id, courseTitleOf(state, id), time, PendingCourseDrag.Kind.Resize)
        },
        onMoveBlocked = viewModel::reportCourseMoveBlocked,
        onSaveCourseNote = viewModel::setCourseNote,
        onCreateBulkReminder = viewModel::createReminderForCourses,
        onPrevWeek = onPrevWeek,
        onNextWeek = onNextWeek,
        onWeekOffsetChange = onWeekOffsetChange,
        onPrevDay = onPrevDay,
        onNextDay = onNextDay,
        onResetDay = onResetDay,
        onOpenPluginMarket = onOpenPluginMarket,
        scheduleTextStyle = scheduleTextStyle,
        scheduleCardStyle = scheduleCardStyle,
        scheduleBackground = scheduleBackground,
        scheduleDisplay = scheduleDisplay,
        customColorsAdaptToTheme = customColorsAdaptToTheme,
        temporaryScheduleOverrides = temporaryScheduleOverrides,
        holidayCalendar = holidayCalendar,
        onUpsertTemporaryScheduleOverride = onUpsertTemporaryScheduleOverride,
        onRemoveTemporaryScheduleOverride = onRemoveTemporaryScheduleOverride,
        onUpsertHolidayEntry = onUpsertHolidayEntry,
        onRemoveHolidayEntry = onRemoveHolidayEntry,
        modifier = modifier,
    )
}

@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    onCreateCourseReminder: (String, Int, String?) -> Unit,
    onMuteExamReminder: (String) -> Unit,
    onRestoreExamReminder: (String) -> Unit,
    onRemoveReminderRule: (String) -> Unit,
    onRemoveManualCourse: (String) -> Unit,
    onAddManualCourse: (CourseItem) -> Unit = {},
    /** 详情里就地改课；插件课保存后转为手动课程。 */
    onSaveCourse: (CourseItem) -> Unit = {},
    onMoveManualCourse: (String, CourseTimeSlot) -> Unit = { _, _ -> },
    onResizeManualCourse: (String, CourseTimeSlot) -> Unit = { _, _ -> },
    onMoveBlocked: () -> Unit = {},
    onSaveCourseNote: (CourseItem, String) -> Unit = { _, _ -> },
    onCreateBulkReminder: (Set<String>, Int, String?) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onDayOffsetChange: (Int) -> Unit = {},
    onResetDay: () -> Unit,
    onOpenPluginMarket: () -> Unit,
    onWeekOffsetChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    weekOffset: Int = 0,
    minWeekOffset: Int = Int.MIN_VALUE / 2,
    maxWeekOffset: Int = Int.MAX_VALUE / 2,
    onAddWeek: (() -> Unit)? = null,
    overrideTermStart: LocalDate? = null,
    viewMode: ScheduleViewMode = ScheduleViewMode.Week,
    dayOffset: Int = 0,
    scheduleTextStyle: ScheduleTextStylePreferences = ScheduleTextStylePreferences(),
    scheduleCardStyle: ScheduleCardStylePreferences = ScheduleCardStylePreferences(),
    scheduleBackground: ScheduleBackgroundPreferences = ScheduleBackgroundPreferences(),
    scheduleDisplay: ScheduleDisplayPreferences = ScheduleDisplayPreferences(),
    customColorsAdaptToTheme: Boolean = false,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride> = emptyList(),
    holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
    onUpsertTemporaryScheduleOverride: (TemporaryScheduleOverride) -> Unit = {},
    onRemoveTemporaryScheduleOverride: (String) -> Unit = {},
    /** 双击日期栏改这一天的放假状态。 */
    onUpsertHolidayEntry: (HolidayCalendarEntry) -> Unit = {},
    onRemoveHolidayEntry: (LocalDate) -> Unit = {},
) {
    var detailRequest by remember { mutableStateOf<CourseDetailRequest?>(null) }
    var pendingReminderCourse by remember { mutableStateOf<CourseItem?>(null) }
    // 建提醒前的权限闸门：缺通知或精确闹钟权限时先把人送去授权
    val context = LocalContext.current
    val alarmPermissionGate = rememberAlarmPermissionGateState()
    var multiSelectMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showBulkReminder by rememberSaveable { mutableStateOf(false) }
    // 长按课程后的三条去向：操作面板、就地编辑、移动选点
    var actionSheetCourse by remember { mutableStateOf<CourseItem?>(null) }
    var editRequest by remember { mutableStateOf<CourseItem?>(null) }
    var moveRequest by remember { mutableStateOf<CourseItem?>(null) }
    // 双击日期栏打开的「这一天」设置
    var daySheetDate by remember { mutableStateOf<LocalDate?>(null) }
    val zone = LocalAppZone.current

    val allVisibleCourses = remember(state.schedule, state.manualCourses) {
        state.schedule.allCoursesWith(state.manualCourses).visibleScheduleCourses()
    }
    val pluginCourseIds = remember(state.schedule) {
        state.schedule?.dailySchedules.orEmpty().flatMap { it.courses }.mapTo(mutableSetOf()) { it.id }
    }
    // 节次上限跟随当前作息，作息未设置时退回表单默认值
    val maxNodeCount = state.timingProfile?.slotTimes?.maxOfOrNull { it.endNode } ?: 12
    // 周次上限跟着本学期实际排到第几周走，学期比默认长时才改得动后面的周次
    val editableMaxWeek = remember(allVisibleCourses) {
        maxOf(DefaultEditableWeekCount, allVisibleCourses.flatMap { it.weeks }.maxOrNull() ?: 0)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            if (!state.initialized) {
                ScheduleInitializingState(modifier = Modifier.fillMaxSize())
            } else {
                val onCellClickHandler: (List<CourseItem>, LocalDate) -> Unit = { coursesAtCell, targetDate ->
                    if (multiSelectMode) {
                        val next = toggleCellSelection(selectedIds, coursesAtCell)
                        if (next != selectedIds) {
                            selectedIds = next
                            if (selectedIds.isEmpty()) multiSelectMode = false
                        }
                    } else {
                        detailRequest = CourseDetailRequest(coursesAtCell, targetDate)
                    }
                }
                // 已在多选里长按就继续加选；否则先给操作面板，改课不必再点进详情
                val onLongClickHandler: (String) -> Unit = { id ->
                    if (multiSelectMode) {
                        selectedIds = selectedIds + id
                    } else {
                        val target = allVisibleCourses.firstOrNull { it.id == id }
                        if (target == null) {
                            multiSelectMode = true
                            selectedIds = selectedIds + id
                        } else {
                            actionSheetCourse = target
                        }
                    }
                }

                val locationSuffix = remember(allVisibleCourses) {
                    allVisibleCourses.map { it.location }.let(::sharedLocationSuffix)
                }

                CompositionLocalProvider(
                    LocalScheduleLocationSuffix provides locationSuffix,
                ) {
                    when (viewMode) {
                        ScheduleViewMode.Week -> WeeklyScheduleSection(
                            modifier = Modifier.fillMaxSize(),
                            schedule = state.schedule,
                            manualCourses = state.manualCourses,
                            timingProfile = state.timingProfile,
                            uiSchema = state.uiSchema,
                            reminderRules = state.reminderRules,
                            courseNotes = state.courseNotes,
                            weekOffset = weekOffset,
                            minWeekOffset = minWeekOffset,
                            maxWeekOffset = maxWeekOffset,
                            onAddWeek = onAddWeek,
                            overrideTermStart = overrideTermStart,
                            zone = zone,
                            selectedCourseId = (state.selectionState as? ScheduleSelectionState.SingleCourse)?.courseId,
                            multiSelectMode = multiSelectMode,
                            multiSelectedIds = selectedIds,
                            onCellClick = onCellClickHandler,
                            onCourseLongClick = onLongClickHandler,
                            onWeekOffsetChange = onWeekOffsetChange,
                            onAddManualCourse = onAddManualCourse,
                            movableCourseIds = remember(state.manualCourses, scheduleDisplay.courseDragEnabled) {
                                if (scheduleDisplay.courseDragEnabled) {
                                    state.manualCourses.map { it.id }.toSet()
                                } else {
                                    emptySet()
                                }
                            },
                            onMoveCourse = onMoveManualCourse,
                            onResizeCourse = onResizeManualCourse,
                            onMoveBlocked = onMoveBlocked,
                            scheduleTextStyle = scheduleTextStyle,
                            scheduleCardStyle = scheduleCardStyle,
                            scheduleBackground = scheduleBackground,
                            scheduleDisplay = scheduleDisplay,
                            customColorsAdaptToTheme = customColorsAdaptToTheme,
                            temporaryScheduleOverrides = temporaryScheduleOverrides,
                            holidayCalendar = holidayCalendar,
                            onDayHeaderDoubleTap = { date -> daySheetDate = date },
                        )

                        ScheduleViewMode.Day -> DailyScheduleSection(
                            modifier = Modifier.fillMaxSize(),
                            schedule = state.schedule,
                            manualCourses = state.manualCourses,
                            timingProfile = state.timingProfile,
                            reminderRules = state.reminderRules,
                            courseNotes = state.courseNotes,
                            targetDate = zone.today().plusDays(dayOffset.toLong()),
                            targetWeekNumber = computeWeekNumber(overrideTermStart, dayOffset, zone),
                            termStartDate = overrideTermStart,
                            temporaryScheduleOverrides = temporaryScheduleOverrides,
                            holidayCalendar = holidayCalendar,
                            selectedCourseId = (state.selectionState as? ScheduleSelectionState.SingleCourse)?.courseId,
                            multiSelectedIds = selectedIds,
                            dayOffset = dayOffset,
                            onDayOffsetChange = onDayOffsetChange,
                            onCellClick = onCellClickHandler,
                            onCourseLongClick = onLongClickHandler,
                            onPrevDay = onPrevDay,
                            onNextDay = onNextDay,
                            scheduleTextStyle = scheduleTextStyle,
                            scheduleCardStyle = scheduleCardStyle,
                            scheduleDisplay = scheduleDisplay,
                            customColorsAdaptToTheme = customColorsAdaptToTheme,
                            onDayHeaderDoubleTap = { date -> daySheetDate = date },
                        )
                    }
                }
            }
        }

        if (viewMode == ScheduleViewMode.Day && dayOffset != 0) {
            BackToTodayButton(
                onClick = onResetDay,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 24.dp),
            )
        }

        if (multiSelectMode) {
            MultiSelectActionBar(
                selectedCount = selectedIds.size,
                onSetReminder = { alarmPermissionGate.require(context) { showBulkReminder = true } },
                onClear = {
                    multiSelectMode = false
                    selectedIds = emptySet()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }

        if (showBulkReminder) {
            val selectedCourses = remember(selectedIds, state.schedule, state.manualCourses) {
                state.schedule.allCoursesWith(state.manualCourses)
                    .visibleScheduleCourses()
                    .filter { it.id in selectedIds }
            }
            val containsExam = selectedCourses.any { it.category == CourseCategory.Exam }
            BulkReminderDialog(
                selectedCount = selectedIds.size,
                defaultAdvanceMinutes = if (containsExam) 40 else 20,
                containsExam = containsExam,
                onDismiss = { showBulkReminder = false },
                onConfirm = { advance, ringtone ->
                    onCreateBulkReminder(selectedIds, advance, ringtone)
                    showBulkReminder = false
                    multiSelectMode = false
                    selectedIds = emptySet()
                },
            )
        }

        detailRequest?.let { request ->
            val examRules = state.reminderRules.filter {
                it.pluginId == state.pluginId && it.isExamReminderRule()
            }
            CourseDetailDialog(
                courses = request.courses,
                timingProfile = state.timingProfile,
                visibleWeekNumber = detailWeekNumber(
                    request.targetDate,
                    overrideTermStart,
                    temporaryScheduleOverrides,
                    holidayCalendar,
                ),
                // 真实的当前周，用来区分「本周」和「你正在看的那一周」
                currentWeekNumber = computeWeekNumberForDate(overrideTermStart, zone.today()),
                isManual = { c -> state.manualCourses.any { it.id == c.id } },
                examReminderEnabled = examRules.isNotEmpty(),
                mutedExamCourseIds = examRules.flatMap { it.mutedCourseIds }.toSet(),
                targetDate = request.targetDate,
                isTemporarilyCancelled = { c ->
                    matchingTemporaryCancelRule(c, request.targetDate, temporaryScheduleOverrides) != null
                },
                noteTextOf = { c -> state.courseNotes.textOf(c.id) },
                isPluginOverride = { c -> c.id in pluginCourseIds },
                existingCourses = allVisibleCourses,
                maxNodeCount = maxNodeCount,
                maxWeekCount = editableMaxWeek,
                onSaveNote = onSaveCourseNote,
                onSaveCourse = { c ->
                    onSaveCourse(c)
                    // 详情里的课程是点开那一刻的快照，就地换成刚存的这份，弹窗不用关掉重开
                    detailRequest = request.copy(
                        courses = request.courses.map { if (it.id == c.id) c else it },
                    )
                },
                onRestorePluginCourse = { c ->
                    onRemoveManualCourse(c.id)
                    detailRequest = null
                },
                onTemporaryCancel = { c ->
                    onUpsertTemporaryScheduleOverride(
                        TemporaryScheduleOverride(
                            id = UUID.randomUUID().toString(),
                            type = TemporaryScheduleOverrideType.CancelCourse,
                            targetDate = request.targetDate.toString(),
                            cancelStartNode = c.time.startNode,
                            cancelEndNode = c.time.endNode,
                            cancelCourseId = c.id,
                        ),
                    )
                    detailRequest = null
                },
                onRestoreTemporaryCancel = { c ->
                    matchingTemporaryCancelRule(c, request.targetDate, temporaryScheduleOverrides)?.let {
                        onRemoveTemporaryScheduleOverride(it.id)
                    }
                    detailRequest = null
                },
                onDismiss = { detailRequest = null },
                onSetReminder = { c ->
                    // 没有通知或精确闹钟权限时先拦住：建出来的提醒到点也不会响
                    alarmPermissionGate.require(context) {
                        pendingReminderCourse = c
                        detailRequest = null
                    }
                },
                hasCancellableReminder = { c ->
                    cancellableReminderRuleIds(c, state.reminderRules).isNotEmpty()
                },
                onCancelReminder = { c ->
                    // 弹窗留在原地，规则删完按钮自己翻回「提醒」，用户当场看得见结果
                    cancellableReminderRuleIds(c, state.reminderRules).forEach(onRemoveReminderRule)
                },
                onMuteExamReminder = { c -> onMuteExamReminder(c.id) },
                onRestoreExamReminder = { c -> onRestoreExamReminder(c.id) },
                onDelete = { c ->
                    onRemoveManualCourse(c.id)
                    val remaining = request.courses.filterNot { it.id == c.id }
                    detailRequest = remaining.takeIf { it.isNotEmpty() }?.let {
                        request.copy(courses = it)
                    }
                },
            )
        }

        AlarmPermissionGateHost(alarmPermissionGate)

        pendingReminderCourse?.let { course ->
            CourseReminderDialog(
                course = course,
                defaultAdvanceMinutes = if (course.category == CourseCategory.Exam) 40 else 20,
                onDismiss = { pendingReminderCourse = null },
                onConfirm = { advance, ringtone ->
                    onCreateCourseReminder(course.id, advance, ringtone)
                    pendingReminderCourse = null
                },
            )
        }

        actionSheetCourse?.let { course ->
            CourseActionSheet(
                course = course,
                manual = state.manualCourses.any { it.id == course.id },
                pluginOverride = state.manualCourses.any { it.id == course.id } && course.id in pluginCourseIds,
                onDismiss = { actionSheetCourse = null },
                onEdit = {
                    actionSheetCourse = null
                    editRequest = course
                },
                onMove = {
                    actionSheetCourse = null
                    moveRequest = course
                },
                onSetReminder = {
                    actionSheetCourse = null
                    alarmPermissionGate.require(context) { pendingReminderCourse = course }
                },
                onMultiSelect = {
                    actionSheetCourse = null
                    multiSelectMode = true
                    selectedIds = selectedIds + course.id
                },
                onDelete = {
                    actionSheetCourse = null
                    onRemoveManualCourse(course.id)
                },
                onRestorePlugin = {
                    actionSheetCourse = null
                    onRemoveManualCourse(course.id)
                },
            )
        }

        editRequest?.let { course ->
            AddCourseDialog(
                onDismiss = { editRequest = null },
                onConfirm = {
                    onSaveCourse(it)
                    editRequest = null
                },
                existingCourses = allVisibleCourses.filterNot { it.id == course.id },
                maxNodeCount = maxNodeCount,
                maxWeekCount = editableMaxWeek,
                initial = course,
            )
        }

        moveRequest?.let { course ->
            MoveCourseDialog(
                course = course,
                maxNodeCount = maxNodeCount,
                existingCourses = allVisibleCourses,
                onDismiss = { moveRequest = null },
                // 真正落库前还有一道确认弹窗，这里只是选好落点
                onConfirm = { time ->
                    moveRequest = null
                    onMoveManualCourse(course.id, time)
                },
            )
        }

        daySheetDate?.let { date ->
            val resolution = resolveScheduleDay(date, temporaryScheduleOverrides, holidayCalendar)
            val userEntry = holidayCalendar.userEntryOn(date)
            ScheduleDaySheet(
                date = date,
                weekdayLabel = stringResource(scheduleWeekdayFullRes(date.dayOfWeek.value)),
                effectiveHolidayName = resolution.takeIf { it.isHoliday }?.let { day ->
                    day.holidayNameRes?.let { stringResource(it) }
                        ?: day.holidayName
                        ?: stringResource(R.string.schedule_holiday_unnamed)
                },
                makeUpWorkday = resolution.isMakeUpWorkday,
                isToday = date == zone.today(),
                sourceDate = resolution.sourceDate.takeIf { it != date && !resolution.isHoliday },
                sourceWeekdayLabel = resolution.sourceDate
                    .takeIf { it != date && !resolution.isHoliday }
                    ?.let { stringResource(scheduleWeekdayFullRes(it.dayOfWeek.value)) },
                initialChoice = when (userEntry?.kind) {
                    HolidayEntryKind.Holiday -> ScheduleDayChoice.Holiday
                    HolidayEntryKind.Workday -> ScheduleDayChoice.Workday
                    null -> ScheduleDayChoice.Default
                },
                initialHolidayName = userEntry?.name.orEmpty(),
                onDismiss = { daySheetDate = null },
                onApply = { choice, name ->
                    daySheetDate = null
                    when (choice) {
                        ScheduleDayChoice.Default -> onRemoveHolidayEntry(date)
                        ScheduleDayChoice.Workday -> onUpsertHolidayEntry(
                            HolidayCalendarEntry(date = date.toString(), kind = HolidayEntryKind.Workday),
                        )
                        ScheduleDayChoice.Holiday -> onUpsertHolidayEntry(
                            HolidayCalendarEntry(
                                date = date.toString(),
                                kind = HolidayEntryKind.Holiday,
                                name = name,
                            ),
                        )
                    }
                },
            )
        }
    }
}

@Composable
fun ScheduleAppearancePreview(
    scheduleTextStyle: ScheduleTextStylePreferences,
    scheduleCardStyle: ScheduleCardStylePreferences,
    scheduleBackground: ScheduleBackgroundPreferences,
    scheduleDisplay: ScheduleDisplayPreferences,
    customColorsAdaptToTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val previewWeek = remember(scheduleDisplay.weekStartDay) {
        appearancePreviewWeek(scheduleDisplay.weekStartDay)
    }
    val previewSlots = remember { appearancePreviewSlots() }
    val previewCourses = remember { appearancePreviewCourses() }
    val columnDayOfWeeks = remember(
        scheduleDisplay.saturdayVisible,
        scheduleDisplay.weekendVisible,
        scheduleDisplay.weekStartDay,
    ) {
        visibleColumnDayOfWeeks(scheduleDisplay)
    }
    val activeEntries = remember(
        previewCourses,
        previewSlots,
        scheduleDisplay.totalScheduleDisplayEnabled,
        columnDayOfWeeks,
    ) {
        buildWeekRenderEntries(
            allCourses = previewCourses,
            slots = previewSlots,
            weekIndex = previewWeek.weekIndex,
            totalScheduleDisplayEnabled = scheduleDisplay.totalScheduleDisplayEnabled,
            weekStart = previewWeek.weekStart,
            termStart = previewWeek.weekStart,
            columnDayOfWeeks = columnDayOfWeeks,
        )
    }
    val slotHeight = scheduleCardStyle.courseCardHeightDp.dp
    val dayHeaderHeight = 52.dp
    val previewHeight = dayHeaderHeight + slotHeight * previewSlots.size + 16.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(previewHeight)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        val cellGroups = remember(activeEntries) {
            activeEntries
                .groupBy { it.placement.dayIndex to it.placement.rowIndex }
                .map { (_, list) ->
                    val main = list.first()
                    val sorted = list.map { it.course }.distinctBy { it.id }
                    Triple(main, sorted, sorted.size)
                }
        }
        val visibleDays = remember(previewWeek.days, columnDayOfWeeks) {
            previewWeek.days.filter { it.dayOfWeek in columnDayOfWeeks }
        }
        val availableWidth = (maxWidth - 8.dp).coerceAtLeast(0.dp)
        val dayColumnCount = visibleDays.size.coerceAtLeast(1)
        val timeColumnWidth = timeColumnWidth(availableWidth, scheduleTextStyle.headerTextSizeSp, previewSlots.map { it.label })
        val gridWidth = (availableWidth - timeColumnWidth).coerceAtLeast(0.dp)
        val dayColumnWidth = (gridWidth / dayColumnCount).coerceAtLeast(36.dp)
        val gridHeight = slotHeight * previewSlots.size

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.height(dayHeaderHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonthCornerCell(
                    monthNumber = previewWeek.days.firstOrNull()?.monthNumber,
                    width = timeColumnWidth,
                    scheduleTextStyle = scheduleTextStyle,
                    customColorsAdaptToTheme = customColorsAdaptToTheme,
                )
                visibleDays.forEach { day ->
                    DayHeader(
                        day = day,
                        width = dayColumnWidth,
                        scheduleTextStyle = scheduleTextStyle,
                        customColorsAdaptToTheme = customColorsAdaptToTheme,
                    )
                }
            }

            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier.width(timeColumnWidth),
                ) {
                    previewSlots.forEach { slot ->
                        TimeCell(
                            slot = slot,
                            height = slotHeight,
                            showTime = scheduleDisplay.nodeColumnTimeEnabled,
                            scheduleTextStyle = scheduleTextStyle,
                            customColorsAdaptToTheme = customColorsAdaptToTheme,
                        )
                    }
                }

                val darkTheme = isDarkColorScheme()
                Box(
                    modifier = Modifier
                        .width(dayColumnWidth * dayColumnCount)
                        .height(gridHeight)
                        .clip(RoundedCornerShape(16.dp)),
                ) {
                    ScheduleGridBackground(
                        scheduleBackground = scheduleBackground,
                        scheduleCardStyle = scheduleCardStyle,
                        customColorsAdaptToTheme = customColorsAdaptToTheme,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val lineColor = colorFromArgb(
                                    scheduleCardStyle.gridBorderColorArgb,
                                    darkTheme = darkTheme,
                                    adaptToTheme = customColorsAdaptToTheme,
                                    role = ScheduleCustomColorRole.Foreground,
                                ).withOpacityPercent(scheduleCardStyle.gridBorderOpacityPercent)
                                val strokeWidth = scheduleCardStyle.gridBorderWidthDp.dp.toPx()
                                if (strokeWidth <= 0f) return@drawBehind
                                val pathEffect = if (scheduleCardStyle.gridBorderDashed) {
                                    PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()), 0f)
                                } else {
                                    null
                                }
                                for (i in 1 until previewSlots.size) {
                                    val y = slotHeight.toPx() * i
                                    drawLine(
                                        color = lineColor,
                                        start = androidx.compose.ui.geometry.Offset(0f, y),
                                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                                        strokeWidth = strokeWidth,
                                        pathEffect = pathEffect,
                                    )
                                }
                                for (i in 1 until dayColumnCount) {
                                    val x = dayColumnWidth.toPx() * i
                                    drawLine(
                                        color = lineColor,
                                        start = androidx.compose.ui.geometry.Offset(x, 0f),
                                        end = androidx.compose.ui.geometry.Offset(x, size.height),
                                        strokeWidth = strokeWidth,
                                        pathEffect = pathEffect,
                                    )
                                }
                            },
                    ) {
                        cellGroups.forEach { (mainEntry, _, count) ->
                            val placement = mainEntry.placement
                            val course = mainEntry.course
                            val courseHeight = (slotHeight * placement.rowSpan) - 3.dp
                            CourseBlock(
                                course = course,
                                badges = emptyList(),
                                hasReminder = false,
                                selected = false,
                                inactive = mainEntry.inactive,
                                temporarilyCancelled = false,
                                cellCount = count,
                                multiSelectMode = false,
                                multiSelected = false,
                                scheduleTextStyle = scheduleTextStyle,
                                scheduleCardStyle = scheduleCardStyle,
                                scheduleDisplay = scheduleDisplay,
                                customColorsAdaptToTheme = customColorsAdaptToTheme,
                                width = dayColumnWidth - 2.dp,
                                height = courseHeight,
                                offsetX = dayColumnWidth * placement.dayIndex + 1.dp,
                                offsetY = slotHeight * placement.rowIndex + 1.dp,
                                interactive = false,
                                onClick = {},
                                onLongClick = {},
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeeklyScheduleSection(
    schedule: TermSchedule?,
    manualCourses: List<CourseItem>,
    timingProfile: TermTimingProfile?,
    uiSchema: PluginUiSchema,
    reminderRules: List<com.x500x.cursimple.core.reminder.model.ReminderRule>,
    courseNotes: CourseNoteIndex = CourseNoteIndex(),
    weekOffset: Int,
    minWeekOffset: Int,
    maxWeekOffset: Int,
    onAddWeek: (() -> Unit)? = null,
    overrideTermStart: LocalDate?,
    zone: java.time.ZoneId,
    selectedCourseId: String?,
    multiSelectMode: Boolean,
    multiSelectedIds: Set<String>,
    onCellClick: (List<CourseItem>, LocalDate) -> Unit,
    onCourseLongClick: (String) -> Unit,
    onWeekOffsetChange: (Int) -> Unit,
    onAddManualCourse: (CourseItem) -> Unit = {},
    movableCourseIds: Set<String> = emptySet(),
    onMoveCourse: (String, CourseTimeSlot) -> Unit = { _, _ -> },
    onResizeCourse: (String, CourseTimeSlot) -> Unit = { _, _ -> },
    onMoveBlocked: () -> Unit = {},
    scheduleTextStyle: ScheduleTextStylePreferences,
    scheduleCardStyle: ScheduleCardStylePreferences,
    scheduleBackground: ScheduleBackgroundPreferences,
    scheduleDisplay: ScheduleDisplayPreferences,
    customColorsAdaptToTheme: Boolean,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride> = emptyList(),
    holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
    onDayHeaderDoubleTap: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val slotContext = LocalContext.current
    val slots = remember(slotContext, schedule, timingProfile, manualCourses) {
        displaySlots(slotContext, schedule, timingProfile, manualCourses)
    }
    val allCourses = remember(schedule, manualCourses) {
        schedule.allCoursesWith(manualCourses).visibleScheduleCourses()
    }
    val columnDayOfWeeks = remember(
        scheduleDisplay.saturdayVisible,
        scheduleDisplay.weekendVisible,
        scheduleDisplay.weekStartDay,
    ) {
        visibleColumnDayOfWeeks(scheduleDisplay)
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (slots.isEmpty()) {
                EmptyWeekState(schedule = schedule)
            } else {
                val safeMin = minWeekOffset.coerceAtMost(weekOffset)
                val safeMax = maxWeekOffset.coerceAtLeast(weekOffset)
                val weekPageCount = safeMax - safeMin + 1
                // 最后再挂一页「添加周」，翻到底就能接着往后加空白周
                val addPageEnabled = onAddWeek != null
                val pageCount = weekPageCount + if (addPageEnabled) 1 else 0
                val initialPage = (weekOffset - safeMin).coerceIn(0, weekPageCount - 1)
                val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                    initialPage = initialPage,
                    pageCount = { pageCount },
                )
                val context = androidx.compose.ui.platform.LocalContext.current
                val lastEdgeToastAt = androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
                val firstWeekText = stringResource(R.string.schedule_edge_first_week)
                val lastWeekText = stringResource(R.string.schedule_edge_last_week)
                val edgeNestedScroll = androidx.compose.runtime.remember(pagerState, pageCount) {
                    object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                        override fun onPostScroll(
                            consumed: androidx.compose.ui.geometry.Offset,
                            available: androidx.compose.ui.geometry.Offset,
                            source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
                        ): androidx.compose.ui.geometry.Offset {
                            if (kotlin.math.abs(available.x) < 0.5f) return androidx.compose.ui.geometry.Offset.Zero
                            val atStart = pagerState.currentPage == 0 && available.x > 0f
                            val atEnd = pagerState.currentPage == pageCount - 1 && available.x < 0f
                            if (atStart || atEnd) {
                                val now = System.currentTimeMillis()
                                if (now - lastEdgeToastAt.longValue > 1500L) {
                                    lastEdgeToastAt.longValue = now
                                    android.widget.Toast.makeText(
                                        context,
                                        if (atStart) firstWeekText else lastWeekText,
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                            return androidx.compose.ui.geometry.Offset.Zero
                        }
                    }
                }
                val pagerLatestRequest = androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableIntStateOf(weekOffset)
                }
                val isReconciling = androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                androidx.compose.runtime.LaunchedEffect(weekOffset, safeMin, weekPageCount) {
                    val target = (weekOffset - safeMin).coerceIn(0, weekPageCount - 1)
                    if (pagerState.currentPage == target && pagerLatestRequest.intValue == weekOffset) {
                        return@LaunchedEffect
                    }
                    pagerLatestRequest.intValue = weekOffset
                    if (pagerState.currentPage != target) {
                        isReconciling.value = true
                        try {
                            pagerState.animateScrollToPage(target)
                        } finally {
                            isReconciling.value = false
                        }
                    }
                }
                // 在「添加周」那一页点了加号：页数变多，用户原地就落到了新加的那一周上。
                // 页号没变，翻页的 snapshotFlow 不会再发，周偏移也就不会更新——
                // 顶部还写着上一周。这里按页数变化补一次同步。
                androidx.compose.runtime.LaunchedEffect(weekPageCount) {
                    val page = pagerState.currentPage
                    if (page >= weekPageCount) return@LaunchedEffect
                    val newOffset = page + safeMin
                    if (newOffset != weekOffset) {
                        pagerLatestRequest.intValue = newOffset
                        onWeekOffsetChange(newOffset)
                    }
                }
                androidx.compose.runtime.LaunchedEffect(pagerState, safeMin) {
                    androidx.compose.runtime.snapshotFlow {
                        if (pagerState.isScrollInProgress) pagerState.targetPage
                        else pagerState.currentPage
                    }
                        .drop(1)
                        .collect { page ->
                            if (isReconciling.value) return@collect
                            // 停在「添加周」那一页时不改周偏移，否则会被当成翻到了不存在的一周
                            if (page >= weekPageCount) return@collect
                            val newOffset = page + safeMin
                            if (newOffset != pagerLatestRequest.intValue) {
                                pagerLatestRequest.intValue = newOffset
                                onWeekOffsetChange(newOffset)
                            }
                        }
                }

                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(edgeNestedScroll),
                    beyondViewportPageCount = 1,
                ) { page ->
                    if (addPageEnabled && page == pageCount - 1) {
                        AddWeekPage(onClick = { onAddWeek?.invoke() })
                        return@HorizontalPager
                    }
                    val pageOffset = page + safeMin
                    val pageWeek = remember(
                        pageOffset,
                        overrideTermStart,
                        zone,
                        temporaryScheduleOverrides,
                        holidayCalendar,
                        scheduleDisplay.weekStartDay,
                    ) {
                        buildWeekModel(
                            weekOffset = pageOffset,
                            termStart = overrideTermStart,
                            zone = zone,
                            temporaryScheduleOverrides = temporaryScheduleOverrides,
                            holidayCalendar = holidayCalendar,
                            weekStartDay = scheduleDisplay.weekStartDay,
                        )
                    }
                    val active = remember(
                        allCourses,
                        slots,
                        pageWeek.weekIndex,
                        pageWeek.weekStart,
                        scheduleDisplay.totalScheduleDisplayEnabled,
                        columnDayOfWeeks,
                        temporaryScheduleOverrides,
                        holidayCalendar,
                        overrideTermStart,
                    ) {
                        buildWeekRenderEntries(
                            allCourses = allCourses,
                            slots = slots,
                            weekIndex = pageWeek.weekIndex,
                            totalScheduleDisplayEnabled = scheduleDisplay.totalScheduleDisplayEnabled,
                            weekNumberKnown = overrideTermStart != null,
                            weekStart = pageWeek.weekStart,
                            termStart = overrideTermStart,
                            temporaryScheduleOverrides = temporaryScheduleOverrides,
                            holidayCalendar = holidayCalendar,
                            columnDayOfWeeks = columnDayOfWeeks,
                        )
                    }
                    if (pageWeek.weekIndex < 1 && active.isEmpty()) {
                        EmptyWeekState(
                            schedule = schedule,
                            notStarted = true,
                            termStartDate = overrideTermStart,
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            emptyScheduleHint(
                                hasSchedule = schedule != null,
                                hasAnyCourse = allCourses.isNotEmpty(),
                                hasCoursesThisWeek = active.isNotEmpty(),
                            )?.let { hint ->
                                EmptyScheduleHintRow(
                                    text = LocalContext.current.emptyScheduleHintText(hint),
                                )
                            }
                            ScheduleGrid(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                week = pageWeek,
                                slots = slots,
                                activeEntries = active,
                                timingProfile = timingProfile,
                                uiSchema = uiSchema,
                                reminderRules = reminderRules,
                                courseNotes = courseNotes,
                                columnDayOfWeeks = columnDayOfWeeks,
                                scheduleTextStyle = scheduleTextStyle,
                                scheduleCardStyle = scheduleCardStyle,
                                scheduleBackground = scheduleBackground,
                                scheduleDisplay = scheduleDisplay,
                                customColorsAdaptToTheme = customColorsAdaptToTheme,
                                selectedCourseId = selectedCourseId,
                                multiSelectMode = multiSelectMode,
                                multiSelectedIds = multiSelectedIds,
                                onCellClick = onCellClick,
                                onCourseLongClick = onCourseLongClick,
                                currentWeekIndex = pageWeek.weekIndex.coerceAtLeast(1),
                                onAddManualCourse = onAddManualCourse,
                                existingCourses = allCourses,
                                movableCourseIds = movableCourseIds,
                                onMoveCourse = onMoveCourse,
                                onResizeCourse = onResizeCourse,
                                onMoveBlocked = onMoveBlocked,
                                onDayHeaderDoubleTap = onDayHeaderDoubleTap,
                            )
                        }
                    }
                }
            }
        }
    }
}


/**
 * 周课表翻到底之后的那一页。
 *
 * 整页只有一个大加号：实习周、考试周这种没有课但确实存在的周，
 * 课程里推不出来，只能由用户自己接在后面。
 */
@Composable
private fun AddWeekPage(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.schedule_add_week),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.schedule_add_week_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DailyScheduleSection(
    schedule: TermSchedule?,
    manualCourses: List<CourseItem>,
    timingProfile: TermTimingProfile?,
    reminderRules: List<com.x500x.cursimple.core.reminder.model.ReminderRule>,
    courseNotes: CourseNoteIndex = CourseNoteIndex(),
    targetDate: LocalDate,
    targetWeekNumber: Int?,
    termStartDate: LocalDate?,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings,
    selectedCourseId: String?,
    multiSelectedIds: Set<String>,
    dayOffset: Int,
    onDayOffsetChange: (Int) -> Unit,
    onCellClick: (List<CourseItem>, LocalDate) -> Unit,
    onCourseLongClick: (String) -> Unit,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    scheduleTextStyle: ScheduleTextStylePreferences,
    scheduleCardStyle: ScheduleCardStylePreferences,
    scheduleDisplay: ScheduleDisplayPreferences,
    customColorsAdaptToTheme: Boolean,
    onDayHeaderDoubleTap: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val slotContext = LocalContext.current
    val slots = remember(slotContext, schedule, timingProfile, manualCourses) {
        displaySlots(slotContext, schedule, timingProfile, manualCourses)
    }
    val allCourses = remember(schedule, manualCourses) {
        schedule.allCoursesWith(manualCourses).visibleScheduleCourses()
    }
    val today = LocalAppZone.current.today()
    val dayResolution = resolveScheduleDay(targetDate, temporaryScheduleOverrides, holidayCalendar)
    val sourceDate = dayResolution.sourceDate
    val overrideLabel = sourceDate.takeIf { it != targetDate }?.let { sourceDateLabel(it) }
    val holidayLabel = dayResolution.takeIf { it.isHoliday }?.let { holidayDisplayLabel(it.holidayName, it.holidayNameRes) }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DailyHeaderRow(
                date = targetDate,
                isToday = targetDate == today,
                overrideLabel = overrideLabel,
                holidayLabel = holidayLabel,
                onDoubleTap = { onDayHeaderDoubleTap(targetDate) },
            )

            if (slots.isEmpty() || allCourses.isEmpty()) {
                EmptyWeekState(schedule = schedule, holidayLabel = holidayLabel)
                return@Column
            }


            // 用分页器承载相邻日，拖动时左右两天会跟着露出来，松手才落到整页
            val pageCount = DAY_PAGE_SPAN * 2 + 1
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                initialPage = (dayOffset + DAY_PAGE_SPAN).coerceIn(0, pageCount - 1),
                pageCount = { pageCount },
            )
            val latestRequest = remember { androidx.compose.runtime.mutableIntStateOf(dayOffset) }
            val reconciling = remember { androidx.compose.runtime.mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(dayOffset) {
                val target = (dayOffset + DAY_PAGE_SPAN).coerceIn(0, pageCount - 1)
                if (pagerState.currentPage == target && latestRequest.intValue == dayOffset) {
                    return@LaunchedEffect
                }
                latestRequest.intValue = dayOffset
                if (pagerState.currentPage != target) {
                    reconciling.value = true
                    try {
                        pagerState.animateScrollToPage(target)
                    } finally {
                        reconciling.value = false
                    }
                }
            }
            androidx.compose.runtime.LaunchedEffect(pagerState) {
                androidx.compose.runtime.snapshotFlow {
                    if (pagerState.isScrollInProgress) pagerState.targetPage else pagerState.currentPage
                }
                    .drop(1)
                    .collect { page ->
                        if (reconciling.value) return@collect
                        val offset = page - DAY_PAGE_SPAN
                        if (offset != latestRequest.intValue) {
                            latestRequest.intValue = offset
                            onDayOffsetChange(offset)
                        }
                    }
            }

            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                pageSpacing = 8.dp,
            ) { page ->
                val animatedDate = today.plusDays((page - DAY_PAGE_SPAN).toLong())
                // 解析当天 + 按星期/周次过滤+排序全量课程较重，beyondViewportPageCount=1 时约 3 页同时组合，
                // 每次高亮/多选切换都会重组，缓存住避免每页每次重算
                val dayEntry = remember(
                    animatedDate,
                    allCourses,
                    temporaryScheduleOverrides,
                    holidayCalendar,
                    termStartDate,
                    targetWeekNumber,
                ) {
                    val resolution =
                        resolveScheduleDay(animatedDate, temporaryScheduleOverrides, holidayCalendar)
                    val sourceDate = resolution.sourceDate
                    val weekNumber =
                        computeWeekNumberForDate(termStartDate, sourceDate).takeIf {
                            sourceDate != animatedDate
                        } ?: targetWeekNumber
                    // 还没开学时不按周过滤，课程照常列出并按不可用态显示
                    val beforeTerm = weekNumber != null && weekNumber < 1
                    val courses = allCourses
                        .filter { it.time.dayOfWeek == sourceDate.dayOfWeek.value }
                        .filter { beforeTerm || weekNumber == null || it.isActiveInWeek(weekNumber) }
                        .sortedBy { it.time.startNode }
                    DailyPageEntry(resolution = resolution, beforeTerm = beforeTerm, courses = courses)
                }
                val animatedResolution = dayEntry.resolution
                val beforeTerm = dayEntry.beforeTerm
                val active = dayEntry.courses
                DayList(
                    slots = slots,
                    courses = active,
                    onHoliday = animatedResolution.isHoliday,
                    beforeTerm = beforeTerm,
                    timingProfile = timingProfile,
                    targetDate = animatedDate,
                    temporaryScheduleOverrides = temporaryScheduleOverrides,
                    reminderRules = reminderRules,
                    courseNotes = courseNotes,
                    selectedCourseId = selectedCourseId,
                    multiSelectedIds = multiSelectedIds,
                    onCellClick = onCellClick,
                    onCourseLongClick = onCourseLongClick,
                    scheduleTextStyle = scheduleTextStyle,
                    scheduleCardStyle = scheduleCardStyle,
                    scheduleDisplay = scheduleDisplay,
                    customColorsAdaptToTheme = customColorsAdaptToTheme,
                )
            }
        }
    }
}

@Composable
private fun DailyHeaderRow(
    date: LocalDate,
    isToday: Boolean,
    overrideLabel: SourceDateLabel?,
    holidayLabel: HolidayLabel? = null,
    onDoubleTap: (() -> Unit)? = null,
) {
    val accents = com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 双击日期栏改当天的放假状态，与周视图表头一致
            .let {
                if (onDoubleTap == null) {
                    it
                } else {
                    it.pointerInput(date) {
                        detectTapGestures(onDoubleTap = { onDoubleTap() })
                    }
                }
            }
            .padding(start = 4.dp, top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isToday) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accents.todayContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.schedule_month_day, date.monthValue, date.dayOfMonth),
                    color = accents.todayOnContainer,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.schedule_month_day, date.monthValue, date.dayOfMonth),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = stringResource(scheduleWeekdayFullRes(date.dayOfWeek.value)),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (holidayLabel != null) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    text = LocalContext.current.holidayLabelText(holidayLabel),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        } else if (overrideLabel != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    text = stringResource(R.string.schedule_override_day, LocalContext.current.formatSourceDateLabel(overrideLabel)),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun DayList(
    slots: List<DisplaySlot>,
    courses: List<CourseItem>,
    onHoliday: Boolean,
    beforeTerm: Boolean,
    timingProfile: TermTimingProfile?,
    targetDate: LocalDate,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    reminderRules: List<com.x500x.cursimple.core.reminder.model.ReminderRule>,
    courseNotes: CourseNoteIndex,
    selectedCourseId: String?,
    multiSelectedIds: Set<String>,
    onCellClick: (List<CourseItem>, LocalDate) -> Unit,
    onCourseLongClick: (String) -> Unit,
    scheduleTextStyle: ScheduleTextStylePreferences,
    scheduleCardStyle: ScheduleCardStylePreferences,
    scheduleDisplay: ScheduleDisplayPreferences,
    customColorsAdaptToTheme: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        slots.forEach { slot ->
            val coursesInSlot = courses.filter { course ->
                course.time.startNode <= slot.endNode && course.time.endNode >= slot.startNode
            }
            // 只渲染从此 slot 起始的课程，避免跨节多次绘制
            val starting = coursesInSlot.filter { it.time.startNode in slot.startNode..slot.endNode }
            if (starting.isEmpty()) {
                return@forEach
            }
            DayRow(
                slot = slot,
                courses = starting,
                onHoliday = onHoliday,
                beforeTerm = beforeTerm,
                timingProfile = timingProfile,
                targetDate = targetDate,
                temporaryScheduleOverrides = temporaryScheduleOverrides,
                reminderRules = reminderRules,
                courseNotes = courseNotes,
                selectedCourseId = selectedCourseId,
                multiSelectedIds = multiSelectedIds,
                onCellClick = onCellClick,
                onCourseLongClick = onCourseLongClick,
                scheduleTextStyle = scheduleTextStyle,
                scheduleCardStyle = scheduleCardStyle,
                scheduleDisplay = scheduleDisplay,
                customColorsAdaptToTheme = customColorsAdaptToTheme,
            )
        }
        if (courses.isEmpty()) {
            Text(
                text = stringResource(R.string.schedule_day_no_courses),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DayRow(
    slot: DisplaySlot,
    courses: List<CourseItem>,
    onHoliday: Boolean,
    beforeTerm: Boolean,
    timingProfile: TermTimingProfile?,
    targetDate: LocalDate,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    reminderRules: List<com.x500x.cursimple.core.reminder.model.ReminderRule>,
    courseNotes: CourseNoteIndex,
    selectedCourseId: String?,
    multiSelectedIds: Set<String>,
    onCellClick: (List<CourseItem>, LocalDate) -> Unit,
    onCourseLongClick: (String) -> Unit,
    scheduleTextStyle: ScheduleTextStylePreferences,
    scheduleCardStyle: ScheduleCardStylePreferences,
    scheduleDisplay: ScheduleDisplayPreferences,
    customColorsAdaptToTheme: Boolean,
) {
    val accents = com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.widthIn(min = 38.dp, max = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = slot.label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = slotTimeRange(slot),
                fontSize = 9.sp,
                lineHeight = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            courses.forEach { course ->
                val palette = courseColor(course.title, accents.coursePalette)
                val isExam = course.category == CourseCategory.Exam
                val temporarilyCancelled = isCourseTemporarilyCancelled(
                    date = targetDate,
                    course = course,
                    overrides = temporaryScheduleOverrides,
                )
                val unavailable = onHoliday || beforeTerm
                val containerColor = when {
                    unavailable -> accents.inactiveContainer
                    isExam -> MaterialTheme.colorScheme.errorContainer
                    else -> palette.container
                }
                val onColor = when {
                    unavailable -> accents.inactiveOnContainer
                    isExam -> MaterialTheme.colorScheme.onErrorContainer
                    else -> palette.onContainer
                }
                val isSelected = course.id == selectedCourseId
                val isMultiSelected = course.id in multiSelectedIds
                val highlight = isSelected || isMultiSelected
                val shape = RoundedCornerShape(scheduleCardStyle.courseCornerRadiusDp.dp)
                val titleTextSize = if (isExam) {
                    scheduleTextStyle.examTextSizeSp
                } else {
                    scheduleTextStyle.courseTextSizeSp
                }
                val customTitleArgb =
                    if (isExam) scheduleTextStyle.examTextColorArgb else scheduleTextStyle.courseTextColorArgb
                val titleTextColor =
                    if (customTitleArgb == ScheduleTextStylePreferences.DEFAULT_TEXT_COLOR_ARGB) {
                        onColor
                    } else {
                        colorFromArgb(
                            customTitleArgb,
                            darkTheme = isDarkColorScheme(),
                            adaptToTheme = customColorsAdaptToTheme,
                            role = ScheduleCustomColorRole.Foreground,
                        )
                    }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(
                            containerColor.withOpacityPercent(
                                if (unavailable) {
                                    scheduleCardStyle.inactiveCourseOpacityPercent
                                } else {
                                    scheduleCardStyle.scheduleOpacityPercent
                                },
                            ),
                        )
                        .border(
                            BorderStroke(
                                when {
                                    highlight -> 2.dp
                                    isExam -> 1.5.dp
                                    else -> 0.dp
                                },
                                when {
                                    highlight -> MaterialTheme.colorScheme.primary
                                    isExam -> MaterialTheme.colorScheme.error
                                    else -> Color.Transparent
                                },
                            ),
                            shape,
                        )
                        .drawWithContent {
                            drawContent()
                            if (temporarilyCancelled) {
                                val strokeWidth = 2.dp.toPx()
                                drawLine(
                                    color = onColor.copy(alpha = 0.78f),
                                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                                    strokeWidth = strokeWidth,
                                    cap = StrokeCap.Round,
                                )
                                drawLine(
                                    color = onColor.copy(alpha = 0.78f),
                                    start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                    end = androidx.compose.ui.geometry.Offset(0f, size.height),
                                    strokeWidth = strokeWidth,
                                    cap = StrokeCap.Round,
                                )
                            }
                        }
                        .combinedClickable(
                            onClick = { onCellClick(listOf(course), targetDate) },
                            onLongClick = { onCourseLongClick(course.id) },
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(onColor.copy(alpha = 0.9f)),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = course.title,
                            color = titleTextColor,
                            fontSize = titleTextSize.sp,
                            lineHeight = (titleTextSize + 2).sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (unavailable) {
                            Text(
                                text = stringResource(
                                    if (onHoliday) {
                                        R.string.schedule_status_on_holiday
                                    } else {
                                        R.string.schedule_status_other_week
                                    },
                                ),
                                color = onColor,
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }
                        if (isExam && !unavailable) {
                            Text(
                                text = stringResource(R.string.schedule_category_exam),
                                color = onColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                        if (scheduleDisplay.locationVisible && course.location.isNotBlank()) {
                            Text(
                                text = formatCourseLocation(course.location, scheduleDisplay, LocalScheduleLocationSuffix.current),
                                color = onColor.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (scheduleDisplay.teacherVisible && course.teacher.isNotBlank()) {
                            Text(
                                text = course.teacher,
                                color = onColor.copy(alpha = 0.82f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.padding(end = 14.dp, top = 12.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.schedule_period_compact, course.time.startNode),
                            color = onColor,
                            fontSize = 12.sp,
                        )
                        if (hasReminderForCourse(course, reminderRules, timingProfile)) {
                            Icon(
                                imageVector = Icons.Rounded.Notifications,
                                contentDescription = null,
                                tint = onColor,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                        if (courseNotes.hasNote(course.id)) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = onColor,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleInitializingState(modifier: Modifier = Modifier) {
    val tips = listOf(
        stringResource(R.string.schedule_loading_tip_1),
        stringResource(R.string.schedule_loading_tip_2),
        stringResource(R.string.schedule_loading_tip_3),
        stringResource(R.string.schedule_loading_tip_4),
        stringResource(R.string.schedule_loading_tip_5),
        stringResource(R.string.schedule_loading_tip_6),
        stringResource(R.string.schedule_loading_tip_7),
    )
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "init-loader")
    val tipIndex = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableIntStateOf(tips.indices.random())
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1600)
            tipIndex.intValue = (tipIndex.intValue + 1) % tips.size
        }
    }

    val cubeCount = 3
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(cubeCount) { index ->
                val phase = index * 0.18f
                val offset by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(
                            durationMillis = 800,
                            delayMillis = (phase * 800).toInt(),
                            easing = androidx.compose.animation.core.FastOutSlowInEasing,
                        ),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                    ),
                    label = "cube-$index",
                )
                val translation = -16.dp * offset
                Box(
                    modifier = Modifier
                        .offset(y = translation)
                        .size(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f + 0.5f * offset)),
                )
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        androidx.compose.animation.AnimatedContent(
            targetState = tipIndex.intValue,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(400),
                ) + androidx.compose.animation.slideInVertically(
                    animationSpec = androidx.compose.animation.core.tween(400),
                ) { it / 4 }).togetherWith(
                    androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(300),
                    ) + androidx.compose.animation.slideOutVertically(
                        animationSpec = androidx.compose.animation.core.tween(300),
                    ) { -it / 4 },
                )
            },
            label = "init-tip",
        ) { idx ->
            Text(
                text = tips[idx],
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

@Composable
private fun BackToTodayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Text(
            text = stringResource(R.string.schedule_back_to_today),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun computeWeekNumber(
    termStart: LocalDate?,
    dayOffset: Int,
    zone: ZoneId,
): Int? {
    val target = BeijingTime.todayIn(zone).plusDays(dayOffset.toLong())
    return computeWeekNumberForDate(termStart, target)
}

/** 没有开学日期就算不出周次，返回 null 交给调用方决定怎么显示。 */
internal fun computeWeekNumberForDate(
    termStart: LocalDate?,
    target: LocalDate,
): Int? = termStart?.let { resolveTermWeekNumber(it, target) }

/**
 * 课程详情弹窗判断“本周/非本周”所用的周次：按格子实际所在日期取，
 * 调课日则取被借用的来源日期，与列表按周次取课的口径一致。
 */
internal fun detailWeekNumber(
    targetDate: LocalDate,
    termStart: LocalDate?,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
): Int? = computeWeekNumberForDate(
    termStart,
    resolveScheduleDay(targetDate, temporaryScheduleOverrides, holidayCalendar).sourceDate,
)

/** 假日在表头与空态里显示的名字来源。 */
internal sealed interface HolidayLabel {
    /** 用户自建条目自带的名字，按原文显示。 */
    data class Named(val name: String) : HolidayLabel

    /** 内置假日，名字随语言变化。 */
    data class BuiltIn(val nameRes: Int) : HolidayLabel

    /** 用户手动加的假日可以没有名字，用通用文案。 */
    data object Unnamed : HolidayLabel
}

internal fun holidayDisplayLabel(holidayName: String?, holidayNameRes: Int? = null): HolidayLabel = when {
    holidayNameRes != null -> HolidayLabel.BuiltIn(holidayNameRes)
    !holidayName.isNullOrBlank() -> HolidayLabel.Named(holidayName)
    else -> HolidayLabel.Unnamed
}

internal fun Context.holidayLabelText(label: HolidayLabel): String = when (label) {
    is HolidayLabel.Named -> label.name
    is HolidayLabel.BuiltIn -> getString(label.nameRes)
    HolidayLabel.Unnamed -> getString(R.string.schedule_holiday_unnamed)
}

/** 网格里没有课时的提示分类。 */
internal sealed interface EmptyScheduleHint {
    /** 既没有课表来源，也没有任何课程。 */
    data object NeedsSync : EmptyScheduleHint

    /** 有课表来源，但一门课都没有。 */
    data object NoCourses : EmptyScheduleHint

    /** 有课程，只是这一周没有排到。 */
    data object NoCourseThisWeek : EmptyScheduleHint
}

/** 网格里没有课时提示去哪儿补课表；返回 null 表示这一周有课，不需要提示。 */
internal fun emptyScheduleHint(
    hasSchedule: Boolean,
    hasAnyCourse: Boolean,
    hasCoursesThisWeek: Boolean,
): EmptyScheduleHint? = when {
    hasCoursesThisWeek -> null
    !hasAnyCourse && !hasSchedule -> EmptyScheduleHint.NeedsSync
    !hasAnyCourse -> EmptyScheduleHint.NoCourses
    else -> EmptyScheduleHint.NoCourseThisWeek
}

internal fun Context.emptyScheduleHintText(hint: EmptyScheduleHint): String = when (hint) {
    EmptyScheduleHint.NeedsSync -> getString(R.string.schedule_hint_needs_sync)
    EmptyScheduleHint.NoCourses -> getString(R.string.schedule_hint_no_courses)
    EmptyScheduleHint.NoCourseThisWeek -> getString(R.string.schedule_hint_no_course_this_week)
}

@Composable
private fun EmptyScheduleHintRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

internal data class ScheduleEmptyStateText(
    val title: String,
    val subtitle: String,
)

/** 这一周（或这一天）没有课的原因。 */
internal sealed interface ScheduleEmptyState {
    /** 整天放假。 */
    data class Holiday(val label: HolidayLabel) : ScheduleEmptyState

    /** 还没开学，且知道开学是哪一天。 */
    data class NotStarted(val termStartMonth: Int, val termStartDay: Int) : ScheduleEmptyState

    /** 还没开学，但没有具体日期可说。 */
    data object NotStartedWithoutDate : ScheduleEmptyState

    /** 一次课表都没有同步过。 */
    data object NoSchedule : ScheduleEmptyState

    /** 有课表，只是这一周没有课。 */
    data object EmptyWeek : ScheduleEmptyState
}

/**
 * 空态的原因判定。假日排在最前：这一天不上课是由日期本身决定的，
 * 无论课表是否同步、是否已开学，说明放假都比其余文案更贴近实际。
 * 其后未开学优先于课表为空，避免开学前把“还没有同步到课表”盖在“还没开学”上面。
 */
internal fun scheduleEmptyState(
    hasSchedule: Boolean,
    notStarted: Boolean = false,
    termStartDate: LocalDate? = null,
    holidayLabel: HolidayLabel? = null,
): ScheduleEmptyState = when {
    holidayLabel != null -> ScheduleEmptyState.Holiday(holidayLabel)

    notStarted -> termStartDate
        ?.let { ScheduleEmptyState.NotStarted(it.monthValue, it.dayOfMonth) }
        ?: ScheduleEmptyState.NotStartedWithoutDate

    !hasSchedule -> ScheduleEmptyState.NoSchedule

    else -> ScheduleEmptyState.EmptyWeek
}

internal fun Context.scheduleEmptyStateText(state: ScheduleEmptyState): ScheduleEmptyStateText =
    when (state) {
        is ScheduleEmptyState.Holiday -> ScheduleEmptyStateText(
            title = holidayLabelText(state.label),
            subtitle = getString(R.string.schedule_empty_holiday_subtitle),
        )

        is ScheduleEmptyState.NotStarted -> ScheduleEmptyStateText(
            title = getString(R.string.schedule_empty_not_started_title),
            subtitle = getString(
                R.string.schedule_empty_not_started_subtitle_date,
                state.termStartMonth,
                state.termStartDay,
            ),
        )

        ScheduleEmptyState.NotStartedWithoutDate -> ScheduleEmptyStateText(
            title = getString(R.string.schedule_empty_not_started_title),
            subtitle = getString(R.string.schedule_empty_not_started_subtitle),
        )

        ScheduleEmptyState.NoSchedule -> ScheduleEmptyStateText(
            title = getString(R.string.schedule_empty_no_schedule_title),
            subtitle = getString(R.string.schedule_empty_no_schedule_subtitle),
        )

        ScheduleEmptyState.EmptyWeek -> ScheduleEmptyStateText(
            title = getString(R.string.schedule_empty_week_title),
            subtitle = getString(R.string.schedule_empty_week_subtitle),
        )
    }

@Composable
private fun EmptyWeekState(
    schedule: TermSchedule?,
    notStarted: Boolean = false,
    termStartDate: LocalDate? = null,
    holidayLabel: HolidayLabel? = null,
) {
    val (title, subtitle) = LocalContext.current.scheduleEmptyStateText(
        scheduleEmptyState(
            hasSchedule = schedule != null,
            notStarted = notStarted,
            termStartDate = termStartDate,
            holidayLabel = holidayLabel,
        ),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScheduleGrid(
    week: WeekModel,
    slots: List<DisplaySlot>,
    activeEntries: List<CourseRenderEntry>,
    timingProfile: TermTimingProfile?,
    uiSchema: PluginUiSchema,
    reminderRules: List<com.x500x.cursimple.core.reminder.model.ReminderRule>,
    courseNotes: CourseNoteIndex = CourseNoteIndex(),
    columnDayOfWeeks: List<Int>,
    scheduleTextStyle: ScheduleTextStylePreferences,
    scheduleCardStyle: ScheduleCardStylePreferences,
    scheduleBackground: ScheduleBackgroundPreferences,
    scheduleDisplay: ScheduleDisplayPreferences,
    customColorsAdaptToTheme: Boolean,
    selectedCourseId: String?,
    multiSelectMode: Boolean,
    multiSelectedIds: Set<String>,
    onCellClick: (List<CourseItem>, LocalDate) -> Unit,
    onCourseLongClick: (String) -> Unit,
    currentWeekIndex: Int = 1,
    onAddManualCourse: (CourseItem) -> Unit = {},
    existingCourses: List<CourseItem> = emptyList(),
    movableCourseIds: Set<String> = emptySet(),
    onMoveCourse: (String, CourseTimeSlot) -> Unit = { _, _ -> },
    onResizeCourse: (String, CourseTimeSlot) -> Unit = { _, _ -> },
    onMoveBlocked: () -> Unit = {},
    onDayHeaderDoubleTap: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val cellGroups = remember(activeEntries) {
        activeEntries
            .groupBy { it.placement.dayIndex to it.placement.rowIndex }
            .map { (_, list) ->
                val main = list.first()
                val sorted = list.map { it.course }.distinctBy { it.id }
                // 角标数字：以去重后的本周课程数为准，点击可展开查看同格课程
                Triple(main, sorted, sorted.size)
            }
    }
    // week.days 已按显示窗口顺序排好，按可见星期过滤即得列序
    val visibleDays = remember(week.days, columnDayOfWeeks) {
        week.days.filter { it.dayOfWeek in columnDayOfWeeks }
    }
    val dayColumnCount = visibleDays.size.coerceAtLeast(1)

    // 空白格点击添加的浮层状态。提升到网格作用域，使对话框能在内层定位 Box 之外读取。提示格在 2.5 秒后自动清除。
    var hintCell by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Pair<Int, Int>?>(null) }
    // 同一格里叠着好几门课时，长按先问要编辑哪一门
    var longPressPick by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<List<CourseItem>?>(null)
    }
    var addRequest by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<Triple<Int, Int, Int>?>(null)
    }
    val occupiedCells = androidx.compose.runtime.remember(cellGroups) {
        buildSet {
            cellGroups.forEach { (entry, _, _) ->
                val p = entry.placement
                for (r in 0 until p.rowSpan) {
                    add(p.dayIndex to (p.rowIndex + r))
                }
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(hintCell) {
        if (hintCell != null) {
            kotlinx.coroutines.delay(2500)
            hintCell = null
        }
    }

    // 长按拖动课程块：起拖的课程 id 与累计位移，位移为零时按长按处理
    var draggingCourseId by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<String?>(null)
    }
    var dragOffset by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    }
    var resizingCourseId by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<String?>(null)
    }
    var resizingEdge by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(CourseResizeEdge.Bottom)
    }
    var resizeOffsetY by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(0f)
    }
    val occupiedByOthers = androidx.compose.runtime.remember(
        activeEntries,
        draggingCourseId,
        resizingCourseId,
    ) {
        (draggingCourseId ?: resizingCourseId)
            ?.let { occupiedCellsExcluding(activeEntries, it) }
            .orEmpty()
    }

    val gridScrollState = rememberScrollState()
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier) {
        val timeColumnWidth = timeColumnWidth(maxWidth, scheduleTextStyle.headerTextSizeSp, slots.map { it.label })
        // 调课与假日都会在日期下方多出一行说明，表头需要更高。
        // 高度按当前字号与系统字体缩放算，写死 dp 会在大字号下把星期或日号切掉半行。
        val headerDensity = androidx.compose.ui.platform.LocalDensity.current
        val dayHeaderMinHeight = dayHeaderHeight(
            density = headerDensity,
            headerTextSizeSp = scheduleTextStyle.headerTextSizeSp,
            hasExtraLine = visibleDays.any {
                it.overrideLabel != null || it.holidayLabel != null || it.isMakeUpWorkday
            },
        )
        val totalWidth = maxWidth
        val dayColumnWidth = ((totalWidth - timeColumnWidth) / dayColumnCount).coerceAtLeast(36.dp)
        val gridWidth = dayColumnWidth * dayColumnCount
        // 平铺是「把所有节次塞进一屏」，塞不下时它既不滚动也不缩，底下几节直接够不着。
        // 横屏高度只有竖屏的一半，节次一多必然塞不下；竖屏节次很多时同样会。
        // 所以先算一遍塞不塞得下，塞不下就自动按可滚动处理。
        val fitSlotHeight = if (slots.isEmpty()) {
            MIN_FIT_SLOT_HEIGHT
        } else {
            (maxHeight - dayHeaderMinHeight) / slots.size
        }
        val fitMode = scheduleDisplay.rowFitMode == ScheduleRowFitMode.Fit &&
            slots.isNotEmpty() &&
            fitSlotHeight >= MIN_FIT_SLOT_HEIGHT
        val slotHeight = if (fitMode) {
            // 平铺时把剩余高度均分给每节，课名靠自身省略号收尾
            fitSlotHeight
        } else {
            scheduleCardStyle.courseCardHeightDp.dp
        }
        val gridHeight = slotHeight * slots.size

        // 背景铺满整块课表，节次列与日期行都在其上，否则图片只盖住中间一块
        ScheduleGridBackground(
            scheduleBackground = scheduleBackground,
            scheduleCardStyle = scheduleCardStyle,
            customColorsAdaptToTheme = customColorsAdaptToTheme,
            modifier = Modifier
                .width(totalWidth)
                .height(dayHeaderMinHeight + gridHeight)
                .clip(RoundedCornerShape(16.dp)),
        )

        Column(
            modifier = if (fitMode) {
                Modifier
            } else {
                Modifier.verticalScroll(gridScrollState)
            },
        ) {
            // 顶部周日期头
            Row(
                modifier = Modifier.heightIn(min = dayHeaderMinHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonthCornerCell(
                    monthNumber = week.days.firstOrNull()?.monthNumber,
                    width = timeColumnWidth,
                    scheduleTextStyle = scheduleTextStyle,
                    customColorsAdaptToTheme = customColorsAdaptToTheme,
                )
                visibleDays.forEach { day ->
                    DayHeader(
                        day = day,
                        width = dayColumnWidth,
                        scheduleTextStyle = scheduleTextStyle,
                        customColorsAdaptToTheme = customColorsAdaptToTheme,
                        onDoubleTap = { onDayHeaderDoubleTap(day.date) },
                    )
                }
            }

            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier.width(timeColumnWidth),
                ) {
                    slots.forEach { slot ->
                        TimeCell(
                            slot = slot,
                            height = slotHeight,
                            showTime = scheduleDisplay.nodeColumnTimeEnabled,
                            scheduleTextStyle = scheduleTextStyle,
                            customColorsAdaptToTheme = customColorsAdaptToTheme,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .width(gridWidth)
                        .height(gridHeight),
                ) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val darkTheme = isDarkColorScheme()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val lineColor = colorFromArgb(
                                    scheduleCardStyle.gridBorderColorArgb,
                                    darkTheme = darkTheme,
                                    adaptToTheme = customColorsAdaptToTheme,
                                    role = ScheduleCustomColorRole.Foreground,
                                )
                                    .withOpacityPercent(scheduleCardStyle.gridBorderOpacityPercent)
                                val strokeWidth = scheduleCardStyle.gridBorderWidthDp.dp.toPx()
                                if (strokeWidth <= 0f) return@drawBehind
                                val pathEffect = if (scheduleCardStyle.gridBorderDashed) {
                                    PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()), 0f)
                                } else {
                                    null
                                }
                                for (i in 1 until slots.size) {
                                    val y = (slotHeight.toPx() * i)
                                    drawLine(
                                        color = lineColor,
                                        start = androidx.compose.ui.geometry.Offset(0f, y),
                                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                                        strokeWidth = strokeWidth,
                                        pathEffect = pathEffect,
                                    )
                                }
                                for (i in 1 until dayColumnCount) {
                                    val x = dayColumnWidth.toPx() * i
                                    drawLine(
                                        color = lineColor,
                                        start = androidx.compose.ui.geometry.Offset(x, 0f),
                                        end = androidx.compose.ui.geometry.Offset(x, size.height),
                                        strokeWidth = strokeWidth,
                                        pathEffect = pathEffect,
                                    )
                                }
                            }
                            .pointerInput(slots.size, dayColumnWidth, slotHeight, occupiedCells, dayColumnCount) {
                                detectTapGestures(
                                    onTap = { offset: androidx.compose.ui.geometry.Offset ->
                                        val dayWidthPx = with(density) { dayColumnWidth.toPx() }
                                        val slotHeightPx = with(density) { slotHeight.toPx() }
                                        val day = (offset.x / dayWidthPx).toInt().coerceIn(0, dayColumnCount - 1)
                                        val slot = (offset.y / slotHeightPx).toInt().coerceIn(0, slots.size - 1)
                                        if ((day to slot) !in occupiedCells) {
                                            hintCell = day to slot
                                        }
                                    },
                                    // 空白处双击当成「这天要改放假/调休」，和双击日期栏是同一个入口：
                                    // 表头那一条窄，课少的日子用户更容易点在空格子上
                                    onDoubleTap = { offset: androidx.compose.ui.geometry.Offset ->
                                        val dayWidthPx = with(density) { dayColumnWidth.toPx() }
                                        val slotHeightPx = with(density) { slotHeight.toPx() }
                                        val day = (offset.x / dayWidthPx).toInt().coerceIn(0, dayColumnCount - 1)
                                        val slot = (offset.y / slotHeightPx).toInt().coerceIn(0, slots.size - 1)
                                        if ((day to slot) !in occupiedCells) {
                                            visibleDays.getOrNull(day)?.let { onDayHeaderDoubleTap(it.date) }
                                        }
                                    },
                                )
                            },
                    ) {

                        val dayWidthPx = with(density) { dayColumnWidth.toPx() }
                        val slotHeightPx = with(density) { slotHeight.toPx() }
                        val dragTarget = draggingCourseId?.let { id ->
                            cellGroups.firstOrNull { it.first.course.id == id }?.let { (entry, _, _) ->
                                resolveCourseDragTarget(
                                    startDayIndex = entry.placement.dayIndex,
                                    startRowIndex = entry.placement.rowIndex,
                                    rowSpan = entry.placement.rowSpan,
                                    dragOffsetX = dragOffset.x,
                                    dragOffsetY = dragOffset.y,
                                    dayColumnWidthPx = dayWidthPx,
                                    slotHeightPx = slotHeightPx,
                                    dayColumnCount = dayColumnCount,
                                    slotCount = slots.size,
                                    occupiedByOthers = occupiedByOthers,
                                )
                            }
                        }
                        // 落点提示：可放下用主色，被占用用错误色
                        dragTarget?.let { target ->
                            val span = cellGroups
                                .firstOrNull { it.first.course.id == draggingCourseId }
                                ?.first?.placement?.rowSpan ?: 1
                            Box(
                                modifier = Modifier
                                    .width(dayColumnWidth - 2.dp)
                                    .height(slotHeight * span - 3.dp)
                                    .offset(
                                        x = dayColumnWidth * target.dayIndex + 1.dp,
                                        y = slotHeight * target.rowIndex + 1.dp,
                                    )
                                    .background(
                                        color = if (target.isValid) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                        } else {
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .border(
                                        BorderStroke(
                                            2.dp,
                                            if (target.isValid) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.error
                                            },
                                        ),
                                        RoundedCornerShape(8.dp),
                                    ),
                            )
                        }

                        cellGroups.forEach { (mainEntry, sortedCourses, count) ->
                            val placement = mainEntry.placement
                            val course = mainEntry.course
                            val isMultiSelected = course.id in multiSelectedIds
                            val courseHeight = (slotHeight * placement.rowSpan) - 3.dp
                            val isDragging = course.id == draggingCourseId
                            CourseBlock(
                                course = course,
                                badges = badgesForCourse(course, uiSchema.courseBadges),
                                hasReminder = hasReminderForCourse(course, reminderRules, timingProfile),
                                hasNote = courseNotes.hasNote(course.id),
                                selected = course.id == selectedCourseId,
                                inactive = mainEntry.inactive,
                                onHoliday = mainEntry.onHoliday,
                                temporarilyCancelled = mainEntry.temporarilyCancelled,
                                cellCount = count,
                                multiSelectMode = multiSelectMode,
                                multiSelected = isMultiSelected,
                                scheduleTextStyle = scheduleTextStyle,
                                scheduleCardStyle = scheduleCardStyle,
                                scheduleDisplay = scheduleDisplay,
                                customColorsAdaptToTheme = customColorsAdaptToTheme,
                                width = dayColumnWidth - 2.dp,
                                height = courseHeight,
                                offsetX = dayColumnWidth * placement.dayIndex + 1.dp,
                                offsetY = slotHeight * placement.rowIndex + 1.dp,
                                onClick = {
                                    val columnDate = visibleDays.getOrNull(placement.dayIndex)?.date
                                        ?: week.weekStart
                                    onCellClick(sortedCourses, columnDate)
                                },
                                onLongClick = {
                                    // 叠了好几门时不能闷头编辑最上面那一门——那多半不是用户想改的那门
                                    if (sortedCourses.size > 1) {
                                        longPressPick = sortedCourses
                                    } else {
                                        onCourseLongClick(course.id)
                                    }
                                },
                                dragEnabled = course.id in movableCourseIds &&
                                    !multiSelectMode &&
                                    resizingCourseId == null,
                                resizeEnabled = course.id in movableCourseIds &&
                                    !multiSelectMode &&
                                    draggingCourseId == null,
                                onResizeStart = { edge ->
                                    resizingCourseId = course.id
                                    resizingEdge = edge
                                    resizeOffsetY = 0f
                                },
                                onResizeDelta = { delta -> resizeOffsetY += delta },
                                onResizeStop = {
                                    val settled = resizeOffsetY
                                    val edge = resizingEdge
                                    resizingCourseId = null
                                    resizeOffsetY = 0f
                                    val target = resolveCourseResizeTarget(
                                        startRowIndex = placement.rowIndex,
                                        rowSpan = placement.rowSpan,
                                        edge = edge,
                                        dragOffsetY = settled,
                                        slotHeightPx = slotHeightPx,
                                        slotCount = slots.size,
                                        dayIndex = placement.dayIndex,
                                        occupiedByOthers = occupiedByOthers,
                                    )
                                    when {
                                        !target.isValid -> onMoveBlocked()
                                        !target.isResizeFrom(placement.rowIndex, placement.rowSpan) -> Unit
                                        else -> {
                                            val dayOfWeek = columnDayOfWeeks.getOrNull(placement.dayIndex)
                                            if (dayOfWeek != null) {
                                                resizedCourseTime(target, dayOfWeek, slots)
                                                    ?.let { time -> onResizeCourse(course.id, time) }
                                            }
                                        }
                                    }
                                },
                                dragging = isDragging,
                                dragPixelOffset = if (isDragging) dragOffset else androidx.compose.ui.geometry.Offset.Zero,
                                onDragStart = {
                                    draggingCourseId = course.id
                                    dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                },
                                onDragDelta = { delta -> dragOffset += delta },
                                onDragStop = {
                                    val target = resolveCourseDragTarget(
                                        startDayIndex = placement.dayIndex,
                                        startRowIndex = placement.rowIndex,
                                        rowSpan = placement.rowSpan,
                                        dragOffsetX = dragOffset.x,
                                        dragOffsetY = dragOffset.y,
                                        dayColumnWidthPx = dayWidthPx,
                                        slotHeightPx = slotHeightPx,
                                        dayColumnCount = dayColumnCount,
                                        slotCount = slots.size,
                                        occupiedByOthers = occupiedCellsExcluding(activeEntries, course.id),
                                    )
                                    val settled = dragOffset
                                    draggingCourseId = null
                                    dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                    val movedFar = kotlin.math.abs(settled.x) > dayWidthPx / 2f ||
                                        kotlin.math.abs(settled.y) > slotHeightPx / 2f
                                    when {
                                        !movedFar -> onCourseLongClick(course.id)
                                        !target.isValid -> onMoveBlocked()
                                        !target.isMoveFrom(placement.dayIndex, placement.rowIndex) -> Unit
                                        else -> movedCourseTime(
                                            target = target,
                                            rowSpan = placement.rowSpan,
                                            columnDayOfWeeks = columnDayOfWeeks,
                                            slots = slots,
                                        )?.let { time -> onMoveCourse(course.id, time) }
                                    }
                                },
                            )
                        }

                        // 点击提示浮层：半透明底色加居中的加号按钮。保留上一个提示格，用透明度做淡出动画，
                        // 而不是在计时器清空 hintCell 的瞬间直接从组合树里移除。
                        val lastHintCell = androidx.compose.runtime.remember { mutableStateOf<Pair<Int, Int>?>(null) }
                        androidx.compose.runtime.LaunchedEffect(hintCell) {
                            if (hintCell != null) lastHintCell.value = hintCell
                        }
                        val hintAlpha by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (hintCell != null) 1f else 0f,
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 280),
                            label = "hintCellAlpha",
                        )
                        if (hintAlpha > 0.01f) {
                            lastHintCell.value?.let { (day, slotIdx) ->
                                val slot = slots.getOrNull(slotIdx)
                                if (slot != null) {
                                    Box(
                                        modifier = Modifier
                                            .width(dayColumnWidth - 2.dp)
                                            .height(slotHeight - 3.dp)
                                            .offset(
                                                x = dayColumnWidth * day + 1.dp,
                                                y = slotHeight * slotIdx + 1.dp,
                                            )
                                            .alpha(hintAlpha)
                                            .background(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                                shape = RoundedCornerShape(8.dp),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        androidx.compose.material3.Surface(
                                            shape = androidx.compose.foundation.shape.CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clickable(enabled = hintCell != null) {
                                                    val dayOfWeek = columnDayOfWeeks.getOrElse(day) { day + 1 }
                                                    addRequest = Triple(dayOfWeek, slot.startNode, slot.endNode)
                                                    hintCell = null
                                                },
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                androidx.compose.material3.Icon(
                                                    imageVector = Icons.Rounded.Add,
                                                    contentDescription = stringResource(R.string.schedule_add_course_title),
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 滚到底时给最后一节留一段余量，否则贴着容器边缘看起来像被截断了。
            // 平铺模式本来就把全部节次塞进一屏，再加留白反而会把格子压矮。
            if (!fitMode) {
                Spacer(modifier = Modifier.height(GRID_BOTTOM_SPACING))
            }
        }

        if (!fitMode) {
            GridScrollIndicator(
                scrollState = gridScrollState,
                topInset = dayHeaderMinHeight,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }

        longPressPick?.let { courses ->
            OverlappingCoursePickerDialog(
                courses = courses,
                onPick = { picked ->
                    longPressPick = null
                    onCourseLongClick(picked.id)
                },
                onDismiss = { longPressPick = null },
            )
        }

        addRequest?.let { (day, startNode, endNode) ->
            QuickAddCourseDialog(
                dayOfWeek = day,
                startNode = startNode,
                endNode = endNode,
                initialWeek = currentWeekIndex,
                existingCourses = existingCourses,
                onDismiss = { addRequest = null },
                onConfirm = { course ->
                    onAddManualCourse(course)
                    addRequest = null
                },
            )
        }
    }
}

/** 平铺以外的模式下课表要滚动，右侧画一条滑块告诉用户下面还有节次。 */
@Composable
private fun GridScrollIndicator(
    scrollState: ScrollState,
    topInset: Dp,
    modifier: Modifier = Modifier,
) {
    val range = scrollState.maxValue
    if (range <= 0) return
    val progress = (scrollState.value.toFloat() / range).coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier = modifier
            .padding(top = topInset + 4.dp, bottom = 4.dp, end = 2.dp)
            .fillMaxHeight()
            .width(4.dp),
    ) {
        val thumbHeight = (maxHeight * 0.28f).coerceAtLeast(28.dp)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
        )
        Box(
            modifier = Modifier
                .offset(y = (maxHeight - thumbHeight) * progress)
                .height(thumbHeight)
                .width(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        )
    }
}

/** 日视图向前后各铺这么多天，够覆盖一整学年。 */
private const val DAY_PAGE_SPAN = 200

/** 平铺时每节至少留出的高度，再挤就连课名都放不下。 */
private val MIN_FIT_SLOT_HEIGHT = 52.dp

/** 可滚动时网格底部的留白，让最后一节看起来是「到底了」而不是「被切了」。 */
private val GRID_BOTTOM_SPACING = 28.dp

/**
 * 课名长到放不下时按比例缩字号。
 *
 * 默认不启用：缩过之后长课名和短课名字号不一样，整屏看下来大小参差不齐，
 * 反倒比统一字号难看。课名普遍很长、宁可小一点也要看全的人再到设置里打开。
 * [enabled] 为 false 时原样返回配置字号。
 */
internal fun courseTitleFontSizeSp(
    baseSizeSp: Int,
    titleLength: Int,
    enabled: Boolean = true,
): Float {
    if (!enabled) return baseSizeSp.toFloat()
    // 分档按真实课名定：「数据结构」4 字不动，「数据库原理及应用」8 字小一档，
    // 「计算机组成与系统结构」10 字要小两档才塞得下
    val scale = when {
        titleLength <= 5 -> 1f
        titleLength <= 7 -> 0.92f
        titleLength <= 9 -> 0.84f
        else -> 0.76f
    }
    // 下限按 9sp 兜底：再小连笔画都糊成一团
    return (baseSizeSp * scale).coerceAtLeast(9f)
}


/**
 * 给定高度里能完整放下几行。
 *
 * 露出小半个字既看不出是什么，又白占一行高度，所以只数放得下的整行。
 * 至少返回 1：再挤也要给课名留一行，否则这一格等于白画。
 */
internal fun fitLineCount(availableHeightDp: Float, lineHeightDp: Float): Int {
    if (lineHeightDp <= 0f || !availableHeightDp.isFinite()) return Int.MAX_VALUE
    return kotlin.math.floor(availableHeightDp / lineHeightDp).toInt().coerceAtLeast(1)
}


/**
 * 课程卡片里各行文字的排布方案。
 *
 * 规矩是「按优先级从上往下填，上一项没显示完就不显示下一项」：
 * 课名 > 地点 > 附注。课名被省略却还在下面挂着「@东…」，既没把最要紧的显示全，
 * 又多出一行看不懂的残句。
 */
internal data class CourseCardTextPlan(
    val titleLines: Int,
    val titleComplete: Boolean,
    val showLocation: Boolean,
    val locationLines: Int,
    val showBadges: Boolean,
)

/**
 * 估算一段文字在给定宽度里要占几行。
 *
 * 中日韩字按一个字号宽算，其余按半个——课名与教室号正好是这两类的混合。
 */
internal fun estimatedTextLines(text: String, fontSizeDp: Float, widthDp: Float): Int {
    if (text.isEmpty() || fontSizeDp <= 0f || widthDp <= 0f) return 0
    val fullWidth = text.count { it.code > 0x2E80 }
    val halfWidth = text.length - fullWidth
    val totalWidth = (fullWidth + halfWidth * 0.5f) * fontSizeDp
    return kotlin.math.ceil(totalWidth / widthDp).toInt().coerceAtLeast(1)
}

/**
 * 按可用高度排出课名、地点、附注各占几行。
 *
 * 课名永远先拿：它占不满才轮到地点，地点整段放不下就干脆不显示——
 * 半截的「@东…」对谁都没用，不如把高度留给课名。
 */
internal fun courseCardTextPlan(
    availableHeightDp: Float,
    contentWidthDp: Float,
    title: String,
    titleFontSizeDp: Float,
    titleLineHeightDp: Float,
    location: String,
    locationFontSizeDp: Float,
    locationLineHeightDp: Float,
    locationVisible: Boolean,
    hasBadges: Boolean,
    badgeLineHeightDp: Float,
): CourseCardTextPlan {
    if (titleLineHeightDp <= 0f || availableHeightDp <= 0f) {
        return CourseCardTextPlan(
            titleLines = Int.MAX_VALUE,
            titleComplete = true,
            showLocation = locationVisible && location.isNotBlank(),
            locationLines = Int.MAX_VALUE,
            showBadges = hasBadges,
        )
    }
    val titleNeeded = estimatedTextLines(title, titleFontSizeDp, contentWidthDp)
    val titleFits = kotlin.math.floor(availableHeightDp / titleLineHeightDp).toInt()
    val titleLines = minOf(titleNeeded, titleFits).coerceAtLeast(1)
    val titleComplete = titleLines >= titleNeeded
    var remaining = availableHeightDp - titleLines * titleLineHeightDp

    var showLocation = false
    var locationLines = 0
    if (titleComplete && locationVisible && location.isNotBlank() && locationLineHeightDp > 0f) {
        val needed = estimatedTextLines(location, locationFontSizeDp, contentWidthDp)
        val fits = kotlin.math.floor(remaining / locationLineHeightDp).toInt()
        // 地点要么整段显示，要么不显示：只露「@东…」等于没说
        if (fits >= needed) {
            showLocation = true
            locationLines = needed
            remaining -= needed * locationLineHeightDp
        }
    }

    val showBadges = hasBadges && titleComplete && remaining >= badgeLineHeightDp
    return CourseCardTextPlan(
        titleLines = titleLines,
        titleComplete = titleComplete,
        showLocation = showLocation,
        locationLines = locationLines,
        showBadges = showBadges,
    )
}

/** 背景图解码后的长边上限，超过按 2 的幂降采样。 */
private const val BACKGROUND_MAX_EDGE_PX = 2048

/**
 * 日期表头需要的高度：星期与日号各一行，调课或放假时再加一行小字。
 * 行高按 sp 换算成 dp，跟随系统字体缩放，字放大后表头一起长高而不是把字裁掉。
 */
internal fun dayHeaderHeight(
    density: androidx.compose.ui.unit.Density,
    headerTextSizeSp: Int,
    hasExtraLine: Boolean,
): androidx.compose.ui.unit.Dp {
    val mainLine = with(density) { headerTextSizeSp.sp.toDp() } * TEXT_LINE_HEIGHT_RATIO
    val extraLine = if (hasExtraLine) {
        with(density) { DAY_HEADER_EXTRA_TEXT_SIZE_SP.sp.toDp() } * TEXT_LINE_HEIGHT_RATIO
    } else {
        0.dp
    }
    // 两行正文 + 说明行，外加行距与今天胶囊的上下内边距
    return (mainLine * 2 + extraLine + DAY_HEADER_PADDING).coerceAtLeast(DAY_HEADER_MIN_HEIGHT)
}

/** 字号到行高的放大比例，留出中文的上下伸展空间。 */
private const val TEXT_LINE_HEIGHT_RATIO = 1.45f
private const val DAY_HEADER_EXTRA_TEXT_SIZE_SP = 10f
private val DAY_HEADER_PADDING = 14.dp
private val DAY_HEADER_MIN_HEIGHT = 52.dp

@Composable
private fun DayHeader(
    day: DayHeaderModel,
    width: androidx.compose.ui.unit.Dp,
    scheduleTextStyle: ScheduleTextStylePreferences,
    customColorsAdaptToTheme: Boolean,
    onDoubleTap: (() -> Unit)? = null,
) {
    val darkTheme = isDarkColorScheme()
    val headerColor = scheduleTextStyle.resolvedHeaderTextColor(darkTheme, customColorsAdaptToTheme)
    val todayContainer = scheduleTextStyle.resolvedTodayHeaderBackgroundColor(darkTheme, customColorsAdaptToTheme)
    val todayContent = readableContentColor(todayContainer)
    val headerSize = scheduleTextStyle.headerTextSizeSp.sp
    val columnModifier = Modifier
        .width(width)
        .padding(horizontal = 2.dp)
        // 双击这一列的日期改当天的放假状态；单击不接管，横向翻周仍然照常
        .let {
            if (onDoubleTap == null) {
                it
            } else {
                it.pointerInput(day.date) {
                    detectTapGestures(onDoubleTap = { onDoubleTap() })
                }
            }
        }
        .let {
            if (day.isToday) {
                // 胶囊外先留一圈余量，表头压到最小高度时它也不会顶着行边被切
                it.padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(todayContainer)
                    // 今天这一列本来就比别人窄，内边距再宽标签就没地方了
                    .padding(horizontal = 3.dp, vertical = 2.dp)
            } else {
                it
            }
        }
    Column(
        modifier = columnModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // 原先是 softWrap=false 且没给 overflow，默认直接硬切——
        // 大字号或窄列时「10月」会被切掉半个字，连省略号都没有。改成同样按列宽缩字号
        AutoFitHeaderText(
            text = stringResource(day.weekdayLabelRes),
            color = if (day.isToday) todayContent else headerColor.copy(alpha = 0.88f),
            fontWeight = FontWeight.SemiBold,
            maxFontSize = headerSize,
        )
        AutoFitHeaderText(
            text = LocalContext.current.dayDateLabelText(day.dateLabel),
            color = if (day.isToday) todayContent else headerColor,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
            maxFontSize = headerSize,
        )
        if (day.holidayLabel != null) {
            DayHeaderTagText(
                text = LocalContext.current.holidayLabelText(day.holidayLabel),
                color = if (day.isToday) todayContent else MaterialTheme.colorScheme.tertiary,
            )
        } else if (day.isMakeUpWorkday) {
            // 周末被调成上课日，日历上看着是休息日，不标一下容易睡过去
            DayHeaderTagText(
                text = stringResource(R.string.schedule_makeup_workday_tag),
                color = if (day.isToday) todayContent else MaterialTheme.colorScheme.error,
            )
        } else if (day.overrideLabel != null) {
            // 列本身就代表星期几，这里只留「按 月/日」，写全「按10/5周日」在一列宽里必被截掉
            DayHeaderTagText(
                text = stringResource(
                    R.string.schedule_override_source_short,
                    day.overrideLabel.month,
                    day.overrideLabel.dayOfMonth,
                ),
                color = if (day.isToday) todayContent else MaterialTheme.colorScheme.primary,
            )
        }
    }
}


/**
 * 表头里按列宽自动缩字号的文字。
 *
 * 一列就那么宽，固定字号时「10月」「按10/5」在大字号或窄屏上会被切掉或省略成「按1…」。
 * 缩到下限还放不下才用省略号，至少不会出现半个字。
 */
@Composable
private fun AutoFitHeaderText(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    fontWeight: FontWeight,
    maxFontSize: androidx.compose.ui.unit.TextUnit,
    minFontSize: androidx.compose.ui.unit.TextUnit = (maxFontSize.value * 0.6f).coerceAtLeast(6f).sp,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = modifier,
        style = androidx.compose.ui.text.TextStyle(
            color = color,
            fontWeight = fontWeight,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
            minFontSize = minFontSize,
            maxFontSize = maxFontSize,
            stepSize = 0.5.sp,
        ),
    )
}

/** 表头下方那行小标签（放假名、调休、按某天），上限 10sp。 */
@Composable
private fun DayHeaderTagText(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    AutoFitHeaderText(
        text = text,
        color = color,
        fontWeight = FontWeight.Bold,
        maxFontSize = DAY_HEADER_EXTRA_TEXT_SIZE_SP.sp,
        // 「按10/10」是最宽的一种：一个全角字加五个半角字符。
        // 今天那一列还要减去胶囊的内边距，6sp 在大字号下仍会被省略，这里再留一档
        minFontSize = 5.sp,
        modifier = modifier,
    )
}


/**
 * 同一格里叠了好几门课时，长按先选一门。
 *
 * 格子里只画得下最上面那一门，长按却总是编辑它——用户想改的往往是被压在下面的那门。
 */
@Composable
private fun OverlappingCoursePickerDialog(
    courses: List<CourseItem>,
    onPick: (CourseItem) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_overlap_pick_title, courses.size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.schedule_overlap_pick_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                courses.forEach { course ->
                    androidx.compose.material3.Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(course) },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                text = course.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            val detail = listOfNotNull(
                                course.location.takeIf { it.isNotBlank() },
                                course.teacher.takeIf { it.isNotBlank() },
                            ).joinToString(" · ")
                            if (detail.isNotBlank()) {
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.schedule_action_cancel))
            }
        },
    )
}

@Composable
private fun MonthCornerCell(
    monthNumber: Int?,
    width: androidx.compose.ui.unit.Dp,
    scheduleTextStyle: ScheduleTextStylePreferences,
    customColorsAdaptToTheme: Boolean,
) {
    val muted = scheduleTextStyle
        .resolvedHeaderTextColor(isDarkColorScheme(), customColorsAdaptToTheme)
        .copy(alpha = 0.82f)
    val headerSize = scheduleTextStyle.headerTextSizeSp.sp
    Column(
        modifier = Modifier
            .width(width)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (monthNumber != null) {
            // 中文把月份数字与「月」上下两行分开排；英文用月份缩写单行呈现
            if (booleanResource(R.bool.schedule_month_stacked)) {
                Text(
                    text = monthNumber.toString(),
                    fontSize = headerSize,
                    fontWeight = FontWeight.SemiBold,
                    color = muted,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = stringResource(R.string.schedule_month_suffix),
                    fontSize = headerSize,
                    fontWeight = FontWeight.Medium,
                    color = muted,
                    maxLines = 1,
                    softWrap = false,
                )
            } else {
                Text(
                    text = stringArrayResource(R.array.schedule_month_of_year)[monthNumber - 1],
                    fontSize = headerSize,
                    fontWeight = FontWeight.SemiBold,
                    color = muted,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun TimeCell(
    slot: DisplaySlot,
    height: androidx.compose.ui.unit.Dp,
    showTime: Boolean,
    scheduleTextStyle: ScheduleTextStylePreferences,
    customColorsAdaptToTheme: Boolean,
) {
    val headerColor = scheduleTextStyle.resolvedHeaderTextColor(isDarkColorScheme(), customColorsAdaptToTheme)
    val headerSize = scheduleTextStyle.headerTextSizeSp.sp
    Column(
        modifier = Modifier
            .height(height)
            .padding(top = 4.dp, end = 2.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = slot.label,
            modifier = Modifier.weight(1f, fill = false),
            color = headerColor,
            fontSize = headerSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (showTime) {
            Text(
                text = slotTimeRange(slot),
                color = headerColor.copy(alpha = 0.72f),
                fontSize = (scheduleTextStyle.headerTextSizeSp - 3).coerceAtLeast(8).sp,
                lineHeight = ((scheduleTextStyle.headerTextSizeSp - 3).coerceAtLeast(8) + 2).sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

private fun slotTimeRange(slot: DisplaySlot): String {
    return if (slot.startTime.isBlank() && slot.endTime.isBlank()) {
        ""
    } else {
        "${slot.startTime}\n${slot.endTime}"
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CourseBlock(
    course: CourseItem,
    badges: List<String>,
    hasReminder: Boolean,
    hasNote: Boolean = false,
    selected: Boolean,
    inactive: Boolean,
    onHoliday: Boolean = false,
    temporarilyCancelled: Boolean,
    cellCount: Int,
    multiSelectMode: Boolean,
    multiSelected: Boolean,
    scheduleTextStyle: ScheduleTextStylePreferences,
    scheduleCardStyle: ScheduleCardStylePreferences,
    scheduleDisplay: ScheduleDisplayPreferences,
    customColorsAdaptToTheme: Boolean,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    offsetX: androidx.compose.ui.unit.Dp,
    offsetY: androidx.compose.ui.unit.Dp,
    interactive: Boolean = true,
    dragEnabled: Boolean = false,
    dragging: Boolean = false,
    dragPixelOffset: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
    onDragStart: () -> Unit = {},
    onDragDelta: (androidx.compose.ui.geometry.Offset) -> Unit = {},
    onDragStop: () -> Unit = {},
    resizeEnabled: Boolean = false,
    onResizeStart: (CourseResizeEdge) -> Unit = {},
    onResizeDelta: (Float) -> Unit = {},
    onResizeStop: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val accents = com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents.current
    val palette = remember(course.title, accents) { courseColor(course.title, accents.coursePalette) }
    val isExam = course.category == CourseCategory.Exam
    val shape = RoundedCornerShape(scheduleCardStyle.courseCornerRadiusDp.dp)
    val titleSizeSp = if (isExam) {
        scheduleTextStyle.examTextSizeSp
    } else {
        scheduleTextStyle.courseTextSizeSp
    }
    val horizontalCentered = scheduleTextStyle.horizontalCenter
    val verticalCentered = scheduleTextStyle.verticalCenter
    val containerColor = when {
        inactive -> accents.inactiveContainer
        isExam -> MaterialTheme.colorScheme.errorContainer
        else -> palette.container
    }
    val onColor = when {
        inactive -> accents.inactiveOnContainer
        isExam -> MaterialTheme.colorScheme.onErrorContainer
        else -> palette.onContainer
    }
    val customTitleArgb =
        if (isExam) scheduleTextStyle.examTextColorArgb else scheduleTextStyle.courseTextColorArgb
    val titleColor =
        if (customTitleArgb == ScheduleTextStylePreferences.DEFAULT_TEXT_COLOR_ARGB) {
            onColor
        } else {
            colorFromArgb(
                customTitleArgb,
                darkTheme = isDarkColorScheme(),
                adaptToTheme = customColorsAdaptToTheme,
                role = ScheduleCustomColorRole.Foreground,
            )
        }
    val highlight = multiSelected || selected
    val borderColor = when {
        multiSelected -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary
        isExam && !inactive -> MaterialTheme.colorScheme.error
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val borderWidth = when {
        highlight -> 2.dp
        isExam && !inactive -> 1.5.dp
        else -> 0.dp
    }

    Box(
        modifier = Modifier
            .offset(offsetX, offsetY)
            .offset {
                androidx.compose.ui.unit.IntOffset(
                    dragPixelOffset.x.roundToInt(),
                    dragPixelOffset.y.roundToInt(),
                )
            }
            .zIndex(if (dragging) 1f else 0f)
            .alpha(if (dragging) 0.9f else 1f)
            .width(width)
            .height(height),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    containerColor.withOpacityPercent(
                        if (inactive) {
                            scheduleCardStyle.inactiveCourseOpacityPercent
                        } else {
                            scheduleCardStyle.scheduleOpacityPercent
                        },
                    ),
                )
                .border(BorderStroke(borderWidth, borderColor), shape)
                .drawWithContent {
                    drawContent()
                    if (temporarilyCancelled) {
                        val strokeWidth = 2.dp.toPx()
                        drawLine(
                            color = onColor.copy(alpha = 0.78f),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            color = onColor.copy(alpha = 0.78f),
                            start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                            end = androidx.compose.ui.geometry.Offset(0f, size.height),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                .then(
                    when {
                        !interactive -> Modifier
                        dragEnabled -> Modifier
                            .clickable(onClick = onClick)
                            .pointerInput(dragEnabled) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { onDragStart() },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        onDragDelta(delta)
                                    },
                                    onDragEnd = { onDragStop() },
                                    onDragCancel = { onDragStop() },
                                )
                            }
                        else -> Modifier.combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                    },
                ),
        ) {
            // 左侧深色竖条
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(onColor.copy(alpha = if (inactive) 0.4f else 0.9f)),
            )
            val hasCountBadge = cellCount > 1 && !(multiSelectMode && multiSelected)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
                    .padding(
                        start = 3.dp,
                        end = 3.dp,
                        top = if (hasCountBadge && !verticalCentered) 16.dp else 3.dp,
                        bottom = 3.dp,
                    ),
                verticalArrangement = if (verticalCentered) Arrangement.Center else Arrangement.spacedBy(1.dp),
                horizontalAlignment = if (horizontalCentered) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                // 「非本周」「考试」这类标记只是注解，字号和行距都压到最小，
                // 免得它占掉一整行、把本来就长的课名再挤掉一行
                if (inactive) {
                    Text(
                        text = stringResource(
                            if (onHoliday) R.string.schedule_status_on_holiday else R.string.schedule_status_other_week,
                        ),
                        color = onColor,
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                    )
                }
                if (isExam && !inactive) {
                    Text(
                        text = stringResource(R.string.schedule_category_exam),
                        color = onColor,
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                    )
                }
                // 课名优先：它按自身需要的行数占位，地点拿剩下的高度。
                // 反过来先给地点占三行的话，「数据结构」这种长课名会被挤成「数据」，
                // 而地点缺几个字还能靠详情页补——课名认不出来这一格就白画了。
                val titleFontSizeSp = remember(titleSizeSp, course.title, scheduleTextStyle.autoShrinkLongTitles) {
                    courseTitleFontSizeSp(
                        baseSizeSp = titleSizeSp,
                        titleLength = course.title.length,
                        enabled = scheduleTextStyle.autoShrinkLongTitles,
                    )
                }
                // 按优先级从上往下填：课名 > 地点 > 附注。
                // 上一项没显示完就不显示下一项——课名被省略却还挂着「@东…」，
                // 等于最要紧的没看全、又多一行残句
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val density = LocalDensity.current
                    val titleLineHeightSp = titleFontSizeSp + 1f
                    val locationText = formatCourseLocation(
                        course.location,
                        scheduleDisplay,
                        LocalScheduleLocationSuffix.current,
                    )
                    val plan = with(density) {
                        courseCardTextPlan(
                            availableHeightDp = maxHeight.value,
                            contentWidthDp = maxWidth.value,
                            title = course.title,
                            titleFontSizeDp = titleFontSizeSp.sp.toDp().value,
                            titleLineHeightDp = titleLineHeightSp.sp.toDp().value,
                            location = locationText,
                            locationFontSizeDp = 10.sp.toDp().value,
                            locationLineHeightDp = 11.sp.toDp().value,
                            locationVisible = scheduleDisplay.locationVisible,
                            hasBadges = badges.isNotEmpty() && !inactive,
                            badgeLineHeightDp = 11.sp.toDp().value,
                        )
                    }
                    Column(
                        horizontalAlignment = if (horizontalCentered) {
                            Alignment.CenterHorizontally
                        } else {
                            Alignment.Start
                        },
                    ) {
                        Text(
                            text = course.title,
                            color = titleColor,
                            fontSize = titleFontSizeSp.sp,
                            // 行距压到比字号只高 1sp：课名常要折三四行，行距是最占地方的一项
                            lineHeight = titleLineHeightSp.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = plan.titleLines,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = if (horizontalCentered) TextAlign.Center else TextAlign.Start,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (plan.showLocation) {
                            Text(
                                text = locationText,
                                color = onColor.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                lineHeight = 11.sp,
                                maxLines = plan.locationLines,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = if (horizontalCentered) TextAlign.Center else TextAlign.Start,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (plan.showBadges) {
                            Text(
                                text = badges.joinToString(separator = " · "),
                                color = onColor.copy(alpha = 0.9f),
                                fontSize = 9.sp,
                                lineHeight = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                                textAlign = if (horizontalCentered) TextAlign.Center else TextAlign.Start,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        // 左下角响铃标识
        if (hasReminder && !inactive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(13.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(onColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Notifications,
                    contentDescription = null,
                    tint = onColor,
                    modifier = Modifier.size(9.dp),
                )
            }
        }

        // 上下边缘的改跨度手柄，只对可编辑的课程渲染
        if (interactive && resizeEnabled) {
            listOf(
                CourseResizeEdge.Top to Alignment.TopCenter,
                CourseResizeEdge.Bottom to Alignment.BottomCenter,
            ).forEach { (edge, alignment) ->
                Box(
                    modifier = Modifier
                        .align(alignment)
                        .fillMaxWidth()
                        .height(14.dp)
                        // 外层课表可纵向滚动，按下事件不当场吃掉就会被它接管，
                        // 所以这里自己处理手势：按下即消费，滚动无从开始
                        .pointerInput(edge, resizeEnabled) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                onResizeStart(edge)
                                var dragging = true
                                while (dragging) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed) {
                                        dragging = false
                                    } else {
                                        val delta = change.positionChange().y
                                        if (delta != 0f) {
                                            change.consume()
                                            onResizeDelta(delta)
                                        }
                                    }
                                }
                                onResizeStop()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(onColor.copy(alpha = 0.45f)),
                    )
                }
            }
        }

        // 右下角备注标识
        if (hasNote && !inactive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-2).dp, y = (-2).dp)
                    .size(13.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(onColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = onColor,
                    modifier = Modifier.size(9.dp),
                )
            }
        }

        // 左上角课程数角标（多个课程占同一格时显示，贴角内嵌避免被父级裁切）
        if (cellCount > 1 && !(multiSelectMode && multiSelected)) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 2.dp, start = 2.dp)
                    .size(14.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = cellCount.toString(),
                    color = MaterialTheme.colorScheme.onSecondary,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    style = androidx.compose.ui.text.TextStyle(
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                        ),
                    ),
                )
            }
        }

        // 多选选中标识：右上角
        if (multiSelectMode && multiSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(16.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

@StringRes
internal fun scheduleWeekdayFullRes(dayOfWeek: Int): Int = weekdayNameRes(dayOfWeek)

/**
 * [overrideLabel] 与 [holidayLabel] 不会同时有值：一天要么按别的日期上课，要么整天放假。
 * 两者分开存放，是为了让表头能按各自的语义着色，而不是让一个字段既表示调课又表示放假。
 */
internal data class DayHeaderModel(
    /** 这一列的实际日期，消费方不再按列下标反推。 */
    val date: LocalDate,
    /** 星期值，1 为周一，7 为周日。 */
    val dayOfWeek: Int,
    val monthNumber: Int?,
    val weekdayLabelRes: Int,
    val dateLabel: DayDateLabel,
    val isToday: Boolean,
    val overrideLabel: SourceDateLabel? = null,
    val holidayLabel: HolidayLabel? = null,
    /** 调休补班日：本该休息却要上课，表头单独标一下。 */
    val isMakeUpWorkday: Boolean = false,
)

/** 日期格的显示内容：月首显示所在月份，其余显示当天日号。 */
internal sealed interface DayDateLabel {
    data class Day(val dayOfMonth: Int) : DayDateLabel
    data class MonthStart(val month: Int) : DayDateLabel
}

/** 调课来源日期，逻辑层只给字段，文字由界面层按当前语言渲染。 */
internal data class SourceDateLabel(val month: Int, val dayOfMonth: Int, val dayOfWeek: Int)

internal fun sourceDateLabel(date: LocalDate): SourceDateLabel =
    SourceDateLabel(date.monthValue, date.dayOfMonth, date.dayOfWeek.value)

internal fun Context.formatSourceDateLabel(label: SourceDateLabel): String =
    getString(R.string.schedule_source_date, label.month, label.dayOfMonth, getString(weekdayNameRes(label.dayOfWeek)))

internal fun Context.dayDateLabelText(label: DayDateLabel): String = when (label) {
    is DayDateLabel.Day -> label.dayOfMonth.toString()
    is DayDateLabel.MonthStart -> resources.getStringArray(R.array.schedule_month_of_year)[label.month - 1]
}

internal data class WeekModel(
    val weekIndex: Int,
    val weekStart: LocalDate,
    val days: List<DayHeaderModel>,
)

internal data class DisplaySlot(
    val startNode: Int,
    val endNode: Int,
    val label: String,
    val startTime: String,
    val endTime: String,
)

internal data class CoursePlacement(
    val dayIndex: Int,
    val rowIndex: Int,
    val rowSpan: Int,
)

internal data class CourseRenderEntry(
    val course: CourseItem,
    val placement: CoursePlacement,
    val inactive: Boolean,
    val temporarilyCancelled: Boolean = false,
    /** 不可用的原因是放假而不是课程不在本周，徽标据此换文案。 */
    val onHoliday: Boolean = false,
)

/** 按列序排列的星期值。起始日一变「列下标 + 1 = 星期」就不成立，所以直接给星期本身。 */
internal fun visibleColumnDayOfWeeks(display: ScheduleDisplayPreferences): List<Int> = columnDayOfWeeks(
    weekStart = display.weekStartDay,
    weekendVisible = display.weekendVisible,
    saturdayVisible = display.saturdayVisible,
)

private data class ScheduleBackgroundImageState(
    val image: ImageBitmap? = null,
    val errorMessage: String? = null,
)

@Composable
private fun ScheduleGridBackground(
    scheduleBackground: ScheduleBackgroundPreferences,
    scheduleCardStyle: ScheduleCardStylePreferences,
    customColorsAdaptToTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val accents = com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents.current
    val baseBackgroundColor = when (scheduleBackground.type) {
        ScheduleBackgroundType.Header -> accents.gridBackground
        ScheduleBackgroundType.Color,
        ScheduleBackgroundType.Image -> colorFromArgb(
            scheduleBackground.colorArgb,
            darkTheme = isDarkColorScheme(),
            adaptToTheme = customColorsAdaptToTheme,
            role = ScheduleCustomColorRole.Background,
        )
    }
    val backgroundColor = baseBackgroundColor.withOpacityPercent(scheduleCardStyle.scheduleOpacityPercent)
    Box(modifier = modifier.background(backgroundColor)) {
        val imageUri = scheduleBackground.imageUri?.takeIf(String::isNotBlank)
        if (scheduleBackground.type == ScheduleBackgroundType.Image && imageUri != null) {
            val context = LocalContext.current
            val openFailedText = stringResource(R.string.schedule_bg_open_failed)
            val decodeFailedText = stringResource(R.string.schedule_bg_decode_failed)
            val readFailedText = stringResource(R.string.schedule_bg_read_failed)
            val imageState by androidx.compose.runtime.produceState(
                initialValue = ScheduleBackgroundImageState(),
                key1 = imageUri,
            ) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        // 按屏幕能显示的规模降采样，避免整张大图常驻内存
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.contentResolver.openInputStream(Uri.parse(imageUri)).use { input ->
                            requireNotNull(input) { openFailedText }
                            BitmapFactory.decodeStream(input, null, bounds)
                        }
                        val longest = maxOf(bounds.outWidth, bounds.outHeight)
                        var sample = 1
                        while (longest > 0 && longest / sample > BACKGROUND_MAX_EDGE_PX) sample *= 2
                        val options = BitmapFactory.Options().apply { inSampleSize = sample }
                        context.contentResolver.openInputStream(Uri.parse(imageUri)).use { input ->
                            requireNotNull(input) { openFailedText }
                            requireNotNull(BitmapFactory.decodeStream(input, null, options)) { decodeFailedText }
                                .asImageBitmap()
                        }
                    }.fold(
                        onSuccess = { ScheduleBackgroundImageState(image = it) },
                        onFailure = { ScheduleBackgroundImageState(errorMessage = it.message ?: readFailedText) },
                    )
                }
            }
            imageState.image?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        // 图片自身的透明度与课表整体透明度叠乘
                        .alpha(
                            scheduleCardStyle.scheduleOpacityPercent.asAlpha() *
                                scheduleBackground.imageTransparencyPercent.asAlpha(),
                        ),
                )
            }
            imageState.errorMessage?.let { message ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.schedule_bg_read_failed_prefix, message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private enum class ScheduleCustomColorRole {
    Foreground,
    Background,
}

private fun colorFromArgb(
    argb: Long,
    darkTheme: Boolean = false,
    adaptToTheme: Boolean = false,
    role: ScheduleCustomColorRole = ScheduleCustomColorRole.Foreground,
): Color {
    val adapted = when (role) {
        ScheduleCustomColorRole.Foreground -> adaptScheduleForegroundColorArgb(argb, darkTheme, adaptToTheme)
        ScheduleCustomColorRole.Background -> adaptScheduleBackgroundColorArgb(argb, darkTheme, adaptToTheme)
    }
    return Color(adapted and 0xFFFF_FFFFL)
}

@Composable
private fun isDarkColorScheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

private fun ScheduleTextStylePreferences.resolvedHeaderTextColor(
    darkTheme: Boolean,
    customColorsAdaptToTheme: Boolean,
): Color =
    colorFromArgb(
        if (headerTextColorCustomized) {
            headerTextColorArgb
        } else if (darkTheme) {
            ScheduleTextStylePreferences.DEFAULT_DARK_HEADER_TEXT_COLOR_ARGB
        } else {
            ScheduleTextStylePreferences.DEFAULT_HEADER_TEXT_COLOR_ARGB
        },
        darkTheme = darkTheme,
        adaptToTheme = customColorsAdaptToTheme && headerTextColorCustomized,
        role = ScheduleCustomColorRole.Foreground,
    )

private fun ScheduleTextStylePreferences.resolvedTodayHeaderBackgroundColor(
    darkTheme: Boolean,
    customColorsAdaptToTheme: Boolean,
): Color =
    colorFromArgb(
        if (todayHeaderBackgroundColorCustomized) {
            todayHeaderBackgroundColorArgb
        } else if (darkTheme) {
            ScheduleTextStylePreferences.DEFAULT_DARK_TODAY_HEADER_BACKGROUND_COLOR_ARGB
        } else {
            ScheduleTextStylePreferences.DEFAULT_TODAY_HEADER_BACKGROUND_COLOR_ARGB
        },
        darkTheme = darkTheme,
        adaptToTheme = customColorsAdaptToTheme && todayHeaderBackgroundColorCustomized,
        role = ScheduleCustomColorRole.Background,
    )

private fun readableContentColor(background: Color): Color =
    if (background.luminance() < 0.5f) Color.White else Color.Black

private fun Int.asAlpha(): Float = 1f - (coerceIn(0, 100) / 100f)

private fun Color.withOpacityPercent(percent: Int): Color = copy(alpha = alpha * percent.asAlpha())

private fun formatCourseLocation(
    location: String,
    scheduleDisplay: ScheduleDisplayPreferences,
    locationSuffix: String = "",
): String = "@${stripLocationSuffix(location, locationSuffix)}"

private data class CourseDetailRequest(
    val courses: List<CourseItem>,
    val targetDate: LocalDate,
)

/** 日视图单页缓存：当天归属、是否开学前、已过滤排序的课程。 */
private data class DailyPageEntry(
    val resolution: ScheduleDayResolution,
    val beforeTerm: Boolean,
    val courses: List<CourseItem>,
)

private fun matchingTemporaryCancelRule(
    course: CourseItem,
    targetDate: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
): TemporaryScheduleOverride? {
    return overrides.asReversed().firstOrNull { it.cancelsCourseOn(targetDate, course) }
}

@Composable
private fun MultiSelectActionBar(
    selectedCount: Int,
    onSetReminder: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.schedule_selected_count, selectedCount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(
                onClick = onSetReminder,
                enabled = selectedCount > 0,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.schedule_multiselect_set_reminder))
            }
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.schedule_action_cancel))
            }
        }
    }
}

internal fun buildWeekModel(
    weekOffset: Int,
    termStart: LocalDate? = null,
    zone: ZoneId = BeijingTime.zone,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride> = emptyList(),
    holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
    weekStartDay: WeekStartDay = WeekStartDay.Monday,
): WeekModel {
    val today = BeijingTime.todayIn(zone)
    val weekStart = displayWeekStartOf(today, weekStartDay).plusWeeks(weekOffset.toLong())
    // 没有开学日期时用 1 让翻页算术成立；是否显示周次由 weekNumberKnown 单独决定
    // 周日起时窗口第一天属于上一教学周，周次按窗口内的周一算，否则页头周次会和页内课程差一周
    val weekIndex = termStart?.let { displayWeekTermIndex(it, weekStart) } ?: 1
    val days = (0..6).map { index ->
        val date = weekStart.plusDays(index.toLong())
        val resolution = resolveScheduleDay(date, temporaryScheduleOverrides, holidayCalendar)
        DayHeaderModel(
            date = date,
            dayOfWeek = date.dayOfWeek.value,
            monthNumber = if (index == 0) date.monthValue else null,
            weekdayLabelRes = shortWeekdayRes(date.dayOfWeek),
            dateLabel = if (date.dayOfMonth == 1) DayDateLabel.MonthStart(date.monthValue) else DayDateLabel.Day(date.dayOfMonth),
            isToday = date == today,
            overrideLabel = if (!resolution.isHoliday && resolution.sourceDate != date) {
                sourceDateLabel(resolution.sourceDate)
            } else null,
            holidayLabel = if (resolution.isHoliday) holidayDisplayLabel(resolution.holidayName, resolution.holidayNameRes) else null,
            isMakeUpWorkday = resolution.isMakeUpWorkday,
        )
    }
    return WeekModel(
        weekIndex = weekIndex,
        weekStart = weekStart,
        days = days,
    )
}

private fun appearancePreviewWeek(weekStartDay: WeekStartDay): WeekModel {
    // 固定一周样例数据，只让列序跟随起始日，保证设置页预览与真实课表排列一致
    val monday = LocalDate.of(2026, 3, 2)
    val weekStart = if (weekStartDay == WeekStartDay.Sunday) monday.minusDays(1) else monday
    return WeekModel(
        weekIndex = 2,
        weekStart = weekStart,
        days = (0..6).map { index ->
            val date = weekStart.plusDays(index.toLong())
            DayHeaderModel(
                date = date,
                dayOfWeek = date.dayOfWeek.value,
                monthNumber = if (index == 0) date.monthValue else null,
                weekdayLabelRes = shortWeekdayRes(date.dayOfWeek),
                dateLabel = DayDateLabel.Day(date.dayOfMonth),
                isToday = false,
            )
        },
    )
}

private fun appearancePreviewSlots(): List<DisplaySlot> = listOf(
    DisplaySlot(startNode = 1, endNode = 1, label = "1", startTime = "08:00", endTime = "08:50"),
    DisplaySlot(startNode = 2, endNode = 2, label = "2", startTime = "09:00", endTime = "09:50"),
    DisplaySlot(startNode = 3, endNode = 3, label = "3", startTime = "10:10", endTime = "11:00"),
    DisplaySlot(startNode = 4, endNode = 4, label = "4", startTime = "11:10", endTime = "12:00"),
)

private fun appearancePreviewCourses(): List<CourseItem> = listOf(
    CourseItem(
        id = "preview-math",
        title = "高等数学",
        teacher = "小浩",
        location = "理工楼110",
        weeks = listOf(2),
        time = com.x500x.cursimple.core.kernel.model.CourseTimeSlot(
            dayOfWeek = 1,
            startNode = 1,
            endNode = 2,
        ),
    ),
    CourseItem(
        id = "preview-english",
        title = "大学英语",
        teacher = "Louis",
        location = "逸夫楼201",
        weeks = listOf(1),
        time = com.x500x.cursimple.core.kernel.model.CourseTimeSlot(
            dayOfWeek = 2,
            startNode = 2,
            endNode = 4,
        ),
    ),
    CourseItem(
        id = "preview-computer",
        title = "计算机基础",
        teacher = "老陈",
        location = "文成楼125",
        weeks = listOf(2),
        time = com.x500x.cursimple.core.kernel.model.CourseTimeSlot(
            dayOfWeek = 3,
            startNode = 1,
            endNode = 3,
        ),
    ),
    CourseItem(
        id = "preview-linear",
        title = "线性代数",
        teacher = "小邱",
        location = "东教学楼502",
        weeks = listOf(2),
        time = com.x500x.cursimple.core.kernel.model.CourseTimeSlot(
            dayOfWeek = 4,
            startNode = 2,
            endNode = 4,
        ),
    ),
    CourseItem(
        id = "preview-mechanics",
        title = "理论力学",
        teacher = "小刘",
        location = "文思楼202",
        weeks = listOf(2),
        time = com.x500x.cursimple.core.kernel.model.CourseTimeSlot(
            dayOfWeek = 7,
            startNode = 1,
            endNode = 2,
        ),
    ),
)

/** 详情里改周次时的周数下限，学期排得更长时按实际周次往上放。 */
private const val DefaultEditableWeekCount = 30

private fun displaySlots(
    context: Context,
    schedule: TermSchedule?,
    timingProfile: TermTimingProfile?,
    manualCourses: List<CourseItem> = emptyList(),
): List<DisplaySlot> {
    val profileSlots = timingProfile?.slotTimes.orEmpty().sortedWith(
        compareBy<ClassSlotTime>({ it.startLocalTime() }, { it.startNode }, { it.endNode }),
    )
    val allCoursesForExtras = schedule.allCoursesWith(manualCourses)
    if (profileSlots.isNotEmpty()) {
        val coveredMax = profileSlots.maxOf { it.endNode }
        // 课程节号超出 timing 配置范围时，按顺次补无时间的大节占位（避免课丢失）
        val extraNodes = allCoursesForExtras
            .flatMap { listOf(it.time.startNode, it.time.endNode) }
            .filter { it > coveredMax }
            .distinct()
            .sorted()
        val baseSlots = profileSlots.mapIndexed { index, slot ->
            DisplaySlot(
                startNode = slot.startNode,
                endNode = slot.endNode,
                label = context.classSlotLabelText(slot, index + 1),
                startTime = slot.startTime,
                endTime = slot.endTime,
            )
        }
        val extraSlots = extraNodes.map { node ->
            DisplaySlot(
                startNode = node,
                endNode = node,
                label = context.classSlotLabelOfIndex(node),
                startTime = "",
                endTime = "",
            )
        }
        // 即使没有课时数据也补到至少 8 节，方便用户在下半段加课。
        val combined = baseSlots + extraSlots
        val blockCount = profileSlots.count { context.slotBlockIndex(it) != null }
        val blockSpan = profileSlots.lastOrNull()?.let { it.endNode - it.startNode + 1 } ?: 1
        val padded = padToMinimumSlots(
            context = context,
            slots = combined,
            minimum = 8,
            blockLabelFrom = (blockCount + 1).takeIf { blockCount == profileSlots.size },
            nodesPerPad = blockSpan,
        )
        return padded
    }
    val allCourses = schedule.allCoursesWith(manualCourses)
    val maxNode = allCourses.maxOfOrNull { maxOf(it.time.startNode, it.time.endNode) } ?: 0
    val derivedSlots = (1..maxNode).map { node ->
        DisplaySlot(
            startNode = node,
            endNode = node,
            label = context.classSlotLabelOfIndex(node),
            startTime = "",
            endTime = "",
        )
    }
    return padToMinimumSlots(context, derivedSlots, minimum = 8)
}

/**
 * 把节次补到 [minimum] 行，让用户能在下半段加课。
 * [blockLabelFrom] 不为 null 时补出来的行沿用大节命名并从该序号往下排，每行占 [nodesPerPad] 个节号。
 */
private fun padToMinimumSlots(
    context: Context,
    slots: List<DisplaySlot>,
    minimum: Int,
    blockLabelFrom: Int? = null,
    nodesPerPad: Int = 1,
): List<DisplaySlot> {
    if (slots.size >= minimum) return slots
    val lastEnd = slots.maxOfOrNull { it.endNode } ?: 0
    val span = nodesPerPad.coerceAtLeast(1)
    val pads = (slots.size until minimum).mapIndexed { offset, _ ->
        val startNode = lastEnd + offset * span + 1
        val endNode = startNode + span - 1
        val blockLabel = blockLabelFrom?.let { context.classSlotLabelOfBlock(it + offset) }
        DisplaySlot(
            startNode = startNode,
            endNode = if (blockLabel == null) startNode else endNode,
            label = blockLabel ?: context.classSlotLabelOfIndex(startNode),
            startTime = "",
            endTime = "",
        )
    }
    return slots + pads
}

private fun coursePlacement(
    course: CourseItem,
    slots: List<DisplaySlot>,
    dayIndexOverride: Int? = null,
): CoursePlacement? {
    val dayIndex = dayIndexOverride ?: (course.time.dayOfWeek - 1)
    if (dayIndex !in 0..6) {
        return null
    }
    val startIndex = slots.indexOfFirst { course.time.startNode in it.startNode..it.endNode }
    val endIndex = slots.indexOfFirst { course.time.endNode in it.startNode..it.endNode }
    if (startIndex == -1 || endIndex == -1) {
        return null
    }
    return CoursePlacement(
        dayIndex = dayIndex,
        rowIndex = startIndex,
        rowSpan = max(1, endIndex - startIndex + 1),
    )
}

internal fun courseColor(
    seed: String,
    palette: List<com.x500x.cursimple.feature.schedule.theme.CoursePaletteEntry>,
): com.x500x.cursimple.feature.schedule.theme.CoursePaletteEntry {
    if (palette.isEmpty()) {
        return com.x500x.cursimple.feature.schedule.theme.CoursePaletteEntry(
            container = Color(0xFFE2EEE3),
            onContainer = Color(0xFF1F2A24),
        )
    }
    return palette[seed.hashCode().mod(palette.size)]
}

private fun badgesForCourse(course: CourseItem, rules: List<CourseBadgeRule>): List<String> {
    return rules.filter { rule ->
        ((rule.titleContains?.let { titleContains ->
            course.title.contains(titleContains, ignoreCase = true)
        } ?: true)) &&
            (rule.dayOfWeek == null || course.time.dayOfWeek == rule.dayOfWeek) &&
            (rule.startNode == null || course.time.startNode == rule.startNode) &&
            (rule.endNode == null || course.time.endNode == rule.endNode)
    }.map { it.label }
}

/**
 * 详情页能直接撤掉的提醒规则 id。
 *
 * 只挑单独服务这一门课的规则；按节次或按 label 的规则同时管着好几门课，
 * 在这里删会误伤别的课，那类提醒仍然只能去提醒设置里改。
 * 考试提醒有自己的「本场静音」入口，也不走这条路。
 */
internal fun cancellableReminderRuleIds(
    course: CourseItem,
    rules: List<com.x500x.cursimple.core.reminder.model.ReminderRule>,
): List<String> = rules
    .filter { rule ->
        rule.enabled &&
            rule.courseId == course.id &&
            rule.scopeType in CANCELLABLE_REMINDER_SCOPES &&
            !rule.isExamReminderRule()
    }
    .map { it.ruleId }

private val CANCELLABLE_REMINDER_SCOPES = setOf(
    ReminderScopeType.SingleCourse,
    ReminderScopeType.FirstCourseOfPeriod,
)

private fun hasReminderForCourse(
    course: CourseItem,
    rules: List<com.x500x.cursimple.core.reminder.model.ReminderRule>,
    timingProfile: TermTimingProfile?,
): Boolean {
    return rules.any { rule ->
        when (rule.scopeType) {
            ReminderScopeType.SingleCourse -> rule.enabled && rule.courseId == course.id
            ReminderScopeType.TimeSlot ->
                rule.enabled && rule.startNode == course.time.startNode && rule.endNode == course.time.endNode
            ReminderScopeType.Exam ->
                rule.enabled && course.category == CourseCategory.Exam && course.id !in rule.mutedCourseIds
            ReminderScopeType.FirstCourseOfPeriod ->
                rule.enabled &&
                    (rule.isExamReminderRule() || rule.isCourseReminderRule()) &&
                    rule.courseId == course.id
            ReminderScopeType.LabelRule -> rule.enabled &&
                timingProfile != null &&
                rule.labelActions.any { action ->
                    action.action == com.x500x.cursimple.core.reminder.model.ReminderLabelActionType.Remind &&
                        course.reminderSlotLabel(timingProfile) == action.slotLabel
                }
        }
    }
}

/**
 * 多选模式下点一格的结果：整格还没全选中就补齐，已经全选中就整格取消，
 * 这样同一格里叠放的每一门课都够得着。
 */
internal fun toggleCellSelection(
    selectedIds: Set<String>,
    coursesAtCell: List<CourseItem>,
): Set<String> {
    val cellIds = coursesAtCell.map { it.id }.toSet()
    if (cellIds.isEmpty()) return selectedIds
    return if (cellIds.all { it in selectedIds }) selectedIds - cellIds else selectedIds + cellIds
}

internal fun CourseItem.isActiveInWeek(weekNumber: Int): Boolean =
    isActiveInTermWeekNumber(weekNumber)

internal fun activeCoursesForWeek(courses: List<CourseItem>, weekNumber: Int): List<CourseItem> {
    return courses.visibleScheduleCourses().filter { it.isActiveInWeek(weekNumber) }
}

internal fun buildWeekRenderEntries(
    allCourses: List<CourseItem>,
    slots: List<DisplaySlot>,
    weekIndex: Int,
    totalScheduleDisplayEnabled: Boolean = false,
    weekNumberKnown: Boolean = true,
    weekStart: LocalDate? = null,
    termStart: LocalDate? = null,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride> = emptyList(),
    holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
    columnDayOfWeeks: List<Int> = (1..7).toList(),
): List<CourseRenderEntry> {
    data class Resolved(
        val course: CourseItem,
        val placement: CoursePlacement,
        val sourceWeekIndex: Int,
        val temporarilyCancelled: Boolean,
        val onHoliday: Boolean = false,
    ) {
        /** 放假当天与不在本周的课程一样按不可用态渲染。 */
        fun isInactive(weekNumberKnown: Boolean): Boolean =
            onHoliday || (weekNumberKnown && !course.isActiveInWeek(sourceWeekIndex))
    }
    // 列序由 columnDayOfWeeks 决定，星期几落在第几列不再等于「星期 - 1」
    val visibleColumns = columnDayOfWeeks
        .filter { it in 1..7 }
        .distinct()
        .mapIndexed { columnIndex, dayOfWeek -> dayOfWeek to columnIndex }
        .toMap()

    val displayCourses = allCourses.visibleScheduleCourses()
    // 周次未知时既不按周过滤，也不给任何课程打“非本周”，界面不对周次做断言
    val showEveryCourse = totalScheduleDisplayEnabled || !weekNumberKnown
    // 拿得到列日期就一律按列解析：周日起时最左那列属于上一教学周，不按列解析会显示错周次的课
    val resolved = if (weekStart != null) {
        visibleColumns.keys.flatMap { dayOfWeek ->
            val actualDate = columnDate(weekStart, dayOfWeek)
            val resolution = resolveScheduleDay(actualDate, temporaryScheduleOverrides, holidayCalendar)
            // 假日当天照常排出课程，只是渲染成不可用态；提醒仍按假日跳过。
            val sourceDate = resolution.sourceDate
            val sourceDayOfWeek = sourceDate.dayOfWeek.value
            val sourceWeekIndex = computeWeekNumberForDate(termStart, sourceDate) ?: weekIndex
            val source = if (showEveryCourse) {
                displayCourses
            } else {
                activeCoursesForWeek(displayCourses, sourceWeekIndex)
            }
            source
                .filter { it.time.dayOfWeek == sourceDayOfWeek }
                .mapNotNull { course ->
                    val columnIndex = visibleColumns[dayOfWeek] ?: return@mapNotNull null
                    val placement = coursePlacement(course, slots, columnIndex) ?: return@mapNotNull null
                    Resolved(
                        course = course,
                        placement = placement,
                        sourceWeekIndex = sourceWeekIndex,
                        temporarilyCancelled = isCourseTemporarilyCancelled(
                            date = actualDate,
                            course = course,
                            overrides = temporaryScheduleOverrides,
                        ),
                        onHoliday = resolution.isHoliday,
                    )
                }
        }
    } else {
        val source = if (showEveryCourse) {
            displayCourses
        } else {
            activeCoursesForWeek(displayCourses, weekIndex)
        }
        source
            .mapNotNull { course ->
                val columnIndex = visibleColumns[course.time.dayOfWeek] ?: return@mapNotNull null
                val placement = coursePlacement(course, slots, columnIndex) ?: return@mapNotNull null
                Resolved(
                    course = course,
                    placement = placement,
                    sourceWeekIndex = weekIndex,
                    temporarilyCancelled = false,
                )
            }
    }
    val grouped = resolved.groupBy { it.placement.dayIndex to it.placement.rowIndex }
    val entries = mutableListOf<CourseRenderEntry>()
    for ((_, list) in grouped) {
        list.distinctBy { it.course.id }
            .sortedWith(
                compareBy<Resolved>(
                    { it.isInactive(weekNumberKnown) },
                    { it.course.time.startNode },
                    { it.course.time.endNode },
                    { it.course.title },
                    { it.course.id },
                ),
            )
            .forEach {
                entries += CourseRenderEntry(
                    course = it.course,
                    placement = it.placement,
                    inactive = it.isInactive(weekNumberKnown),
                    temporarilyCancelled = it.temporarilyCancelled,
                    onHoliday = it.onHoliday,
                )
        }
    }
    return entries
}

/** 表头用的短星期文案资源。 */
private fun shortWeekdayRes(dayOfWeek: DayOfWeek): Int = when (dayOfWeek) {
    DayOfWeek.MONDAY -> R.string.schedule_weekday_short_monday
    DayOfWeek.TUESDAY -> R.string.schedule_weekday_short_tuesday
    DayOfWeek.WEDNESDAY -> R.string.schedule_weekday_short_wednesday
    DayOfWeek.THURSDAY -> R.string.schedule_weekday_short_thursday
    DayOfWeek.FRIDAY -> R.string.schedule_weekday_short_friday
    DayOfWeek.SATURDAY -> R.string.schedule_weekday_short_saturday
    DayOfWeek.SUNDAY -> R.string.schedule_weekday_short_sunday
}

/**
 * 节次列的宽度。
 *
 * 至少要放得下「第一节」这样三个汉字的标签，否则标签会被折成每行一个字，
 * 把下面的起止时间挤出行高。表头字号与系统字号放大时同比放宽，
 * 上限为可用宽度的三成，保证课程列还有位置。
 */
@Composable
private fun timeColumnWidth(availableWidth: Dp, headerTextSizeSp: Int, labels: List<String>): Dp {
    val base = when {
        availableWidth < 360.dp -> 38.dp
        availableWidth < 420.dp -> 42.dp
        else -> 44.dp
    }
    // 全角字实际步进略大于字号，标签字宽乘上步进系数再加左右内边距即为不折行所需的宽度
    val labelWidth =
        (headerTextSizeSp * timeColumnLabelChars(labels) * TIME_COLUMN_CHAR_WIDTH_FACTOR).dp + TIME_COLUMN_PADDING
    val scale = LocalDensity.current.fontScale.coerceIn(1f, 1.8f)
    return (maxOf(base, labelWidth) * scale).coerceAtMost(availableWidth * 0.34f)
}

private const val TIME_COLUMN_CHAR_WIDTH_FACTOR = 1.12f
private val TIME_COLUMN_PADDING = 8.dp

/** 等待确认的拖动改动。 */
internal data class PendingCourseDrag(
    val courseId: String,
    val courseTitle: String,
    val time: CourseTimeSlot,
    val kind: Kind,
) {
    enum class Kind { Move, Resize }
}

private fun courseTitleOf(state: ScheduleUiState, courseId: String): String =
    state.manualCourses.firstOrNull { it.id == courseId }?.title.orEmpty()

@Composable
private fun CourseDragConfirmDialog(
    pending: PendingCourseDrag,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_drag_confirm_title)) },
        text = {
            Text(
                when (pending.kind) {
                    PendingCourseDrag.Kind.Move -> stringResource(
                        R.string.schedule_drag_confirm_move,
                        pending.courseTitle,
                        stringResource(weekdayNameRes(pending.time.dayOfWeek)),
                        pending.time.startNode,
                        pending.time.endNode,
                    )

                    PendingCourseDrag.Kind.Resize -> stringResource(
                        R.string.schedule_drag_confirm_resize,
                        pending.courseTitle,
                        pending.time.startNode,
                        pending.time.endNode,
                    )
                },
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.schedule_drag_confirm_apply))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.schedule_drag_confirm_cancel))
            }
        },
    )
}
