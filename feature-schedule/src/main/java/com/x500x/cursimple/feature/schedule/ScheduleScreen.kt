package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.Constraints
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
import com.x500x.cursimple.core.kernel.model.sharedLocationSuffix
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.allCoursesWith
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.core.kernel.model.occurrencesOn
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
import com.x500x.cursimple.core.kernel.model.temporaryScheduleCourseSourceDate
import com.x500x.cursimple.core.kernel.model.resolveTermWeekNumber
import com.x500x.cursimple.core.kernel.model.coursesMovedTo
import com.x500x.cursimple.core.kernel.model.isCourseMovedAwayFrom
import com.x500x.cursimple.core.kernel.model.isCourseMovedTo
import com.x500x.cursimple.core.kernel.model.locationForWeek
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
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import com.x500x.cursimple.core.kernel.model.strippedLocationOrNull

enum class ScheduleViewMode { Week, Day }

@Composable
fun ScheduleRoute(
    viewModel: ScheduleViewModel,
    onOpenPluginMarket: () -> Unit,
    onSetViewMode: (ScheduleViewMode) -> Unit = {},
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
    onOpenLinkedEvent: (ScheduleEvent) -> Boolean = { false },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
        onSetViewMode = onSetViewMode,
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
        onSaveEvent = viewModel::saveEvent,
        onRemoveEvent = viewModel::removeEvent,
        onOpenLinkedEvent = onOpenLinkedEvent,
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
    onSetViewMode: (ScheduleViewMode) -> Unit = {},
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
    onUpsertHolidayEntry: (HolidayCalendarEntry) -> Unit = {},
    onRemoveHolidayEntry: (LocalDate) -> Unit = {},
    onSaveEvent: (ScheduleEvent) -> Unit = {},
    onRemoveEvent: (String) -> Unit = {},
    onOpenLinkedEvent: (ScheduleEvent) -> Boolean = { false },
) {
    var detailRequest by remember { mutableStateOf<CourseDetailRequest?>(null) }
    var eventDetail by remember { mutableStateOf<Pair<ScheduleEvent, LocalDate>?>(null) }
    var eventEditing by remember { mutableStateOf<ScheduleEvent?>(null) }
    var eventGroup by remember { mutableStateOf<Pair<List<ScheduleEvent>, LocalDate>?>(null) }
    fun openEvent(event: ScheduleEvent, date: LocalDate) {
        if (!onOpenLinkedEvent(event)) eventDetail = event to date
    }
    fun editEvent(event: ScheduleEvent) {
        if (!onOpenLinkedEvent(event)) eventEditing = event
    }
    eventGroup?.let { (events, date) ->
        ScheduleEventGroupDialog(
            events = events,
            date = date,
            onPick = { picked ->
                eventGroup = null
                openEvent(picked, date)
            },
            onDismiss = { eventGroup = null },
        )
    }
    eventDetail?.let { (event, date) ->
        ScheduleEventDetailDialog(
            event = event,
            date = date,
            onEdit = {
                eventDetail = null
                eventEditing = event
            },
            onDelete = {
                eventDetail = null
                onRemoveEvent(event.id)
            },
            onDismiss = { eventDetail = null },
        )
    }
    eventEditing?.let { event ->
        ScheduleEventEditorDialog(
            initial = event,
            defaultDate = event.localDate ?: LocalAppZone.current.today(),
            onDismiss = { eventEditing = null },
            onSave = {
                onSaveEvent(it)
                eventEditing = null
            },
        )
    }
    var pendingReminderCourse by remember { mutableStateOf<CourseItem?>(null) }
    // Check blocking access before creating reminders.
    val context = LocalContext.current
    val alarmPermissionGate = rememberAlarmPermissionGateState()
    var multiSelectMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showBulkReminder by rememberSaveable { mutableStateOf(false) }
    var actionSheetCourse by remember { mutableStateOf<CourseItem?>(null) }
    var editRequest by remember { mutableStateOf<CourseItem?>(null) }
    var moveRequest by remember { mutableStateOf<CourseItem?>(null) }
    var daySheetDate by remember { mutableStateOf<LocalDate?>(null) }
    val zone = LocalAppZone.current

    val allVisibleCourses = remember(state.schedule, state.manualCourses) {
        state.schedule.allCoursesWith(state.manualCourses).visibleScheduleCourses()
    }
    val pluginCourseIds = remember(state.schedule) {
        state.schedule?.dailySchedules.orEmpty().flatMap { it.courses }.mapTo(mutableSetOf()) { it.id }
    }
    // Period bounds follow active timing, otherwise form defaults.
    val maxNodeCount = state.timingProfile?.slotTimes?.maxOfOrNull { it.endNode } ?: 12
    // Week bounds include the actual term coverage.
    val editableMaxWeek = remember(allVisibleCourses) {
        maxOf(DefaultEditableWeekCount, allVisibleCourses.flatMap { it.weeks }.maxOrNull() ?: 0)
    }
    // Show today's overview only for today's day view.
    var todaySheetOpen by rememberSaveable { mutableStateOf(false) }
    val todayCardVisible = state.initialized && scheduleDisplay.todayOverviewEnabled &&
        viewMode == ScheduleViewMode.Day && dayOffset == 0
    val sheetVisible = todaySheetOpen && scheduleDisplay.todayOverviewEnabled
    val closeTodaySheet = { todaySheetOpen = false }
    val overviewActive = todayCardVisible || sheetVisible
    val now by remember(zone, overviewActive) {
        kotlinx.coroutines.flow.flow {
            do {
                emit(BeijingTime.nowDateTimeIn(zone))
                if (!overviewActive) break
                kotlinx.coroutines.delay(30_000L)
            } while (true)
        }
    }.collectAsStateWithLifecycle(initialValue = BeijingTime.nowDateTimeIn(zone))
    val todayOverview = remember(
        allVisibleCourses,
        state.timingProfile,
        overrideTermStart,
        temporaryScheduleOverrides,
        holidayCalendar,
        now,
        zone,
    ) {
        buildTodayOverview(
            now = now,
            courses = allVisibleCourses,
            timingProfile = state.timingProfile,
            termStartDate = overrideTermStart,
            overrides = temporaryScheduleOverrides,
            holidayCalendar = holidayCalendar,
        )
    }
    if (sheetVisible) {
        TodayOverviewSheet(
            state = todayOverview, display = scheduleDisplay, onDismiss = closeTodaySheet,
            onOpenCourse = { course ->
                closeTodaySheet()
                detailRequest = CourseDetailRequest(listOf(course), todayOverview.date)
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("schedule-grid")
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
                // Long-press adds to active multiselection, otherwise opens course actions.
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

                // Infer shared institution text from both locations and badges because either can contain it.
                val locationSuffix = remember(allVisibleCourses, state.uiSchema.courseBadges) {
                    val texts = allVisibleCourses.map { it.location } +
                        allVisibleCourses.flatMap {
                            matchedBadgeLabels(it, state.uiSchema.courseBadges)
                        }
                    sharedLocationSuffix(texts)
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
                            events = state.events,
                            onEventClick = ::openEvent,
                            onEventLongClick = { event, _ -> editEvent(event) },
                            onEventGroupClick = { events, date -> eventGroup = events to date },
                        )

                        ScheduleViewMode.Day -> Column(Modifier.fillMaxSize()) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = todayCardVisible,
                            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
                        ) {
                            TodayOverviewCard(
                                state = todayOverview,
                                display = scheduleDisplay,
                                onClick = { todaySheetOpen = true },
                                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 8.dp),
                            )
                        }
                        DailyScheduleSection(
                            modifier = Modifier.fillMaxWidth().weight(1f),
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
                            events = state.events,
                            onEventClick = ::openEvent,
                            onEventLongClick = { event, _ -> editEvent(event) },
                        )
                        }
                    }
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
                currentWeekNumber = computeWeekNumberForDate(overrideTermStart, zone.today()),
                isManual = { c -> state.manualCourses.any { it.id == c.id } },
                examReminderEnabled = examRules.isNotEmpty(),
                mutedExamCourseIds = examRules.flatMap { it.mutedCourseIds }.toSet(),
                targetDate = request.targetDate,
                dayIsHoliday = resolveScheduleDay(
                    request.targetDate,
                    temporaryScheduleOverrides,
                    holidayCalendar,
                ).isHoliday,
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
                    // Gate reminder creation on required notification and scheduling access.
                    alarmPermissionGate.require(context) {
                        pendingReminderCourse = c
                        detailRequest = null
                    }
                },
                hasCancellableReminder = { c ->
                    cancellableReminderRuleIds(c, state.reminderRules).isNotEmpty()
                },
                onCancelReminder = { c ->
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
                // Destination selection is provisional until confirmation.
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
    /** Limit preview to the first periods when space is constrained. */
    maxSlots: Int = Int.MAX_VALUE,
    maxHeight: Dp = Dp.Unspecified,
) {
    val previewWeek = remember(scheduleDisplay.weekStartDay) {
        appearancePreviewWeek(scheduleDisplay.weekStartDay)
    }
    val previewSlots = remember(maxSlots) { appearancePreviewSlots().take(maxSlots) }
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
    val baseDensity = LocalDensity.current
    val fitScale = if (maxHeight.isSpecified && previewHeight > maxHeight) maxHeight / previewHeight else 1f

    CompositionLocalProvider(
        LocalDensity provides Density(baseDensity.density * fitScale, baseDensity.fontScale),
    ) {
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
                                    displayLocation = course.locationForWeek(mainEntry.sourceWeekIndex),
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
    events: List<ScheduleEvent> = emptyList(),
    onEventClick: (ScheduleEvent, LocalDate) -> Unit = { _, _ -> },
    onEventLongClick: (ScheduleEvent, LocalDate) -> Unit = { _, _ -> },
    onEventGroupClick: (List<ScheduleEvent>, LocalDate) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val slotContext = LocalContext.current
    val slots = remember(slotContext, schedule, timingProfile, manualCourses) {
        displaySlots(slotContext, schedule, timingProfile, manualCourses)
    }
    var zoom by rememberSaveable { mutableStateOf(1f) }
    if (!scheduleDisplay.pinchZoomEnabled && zoom != 1f) zoom = 1f
    val zoomed = zoom > SCHEDULE_ZOOM_EPSILON
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
                // Synchronize week offset after page-count changes even when pager index remains unchanged.
                androidx.compose.runtime.LaunchedEffect(weekPageCount, safeMin) {
                    val page = pagerState.currentPage
                    if (page >= weekPageCount) return@LaunchedEffect
                    val newOffset = page + safeMin
                    if (newOffset != weekOffset) {
                        pagerLatestRequest.intValue = newOffset
                        onWeekOffsetChange(newOffset)
                    }
                }
                // Long-lived collectors must read current page count and range rather than captured initial values.
                val latestWeekPageCount = androidx.compose.runtime.rememberUpdatedState(weekPageCount)
                val latestSafeMin = androidx.compose.runtime.rememberUpdatedState(safeMin)
                androidx.compose.runtime.LaunchedEffect(pagerState) {
                    androidx.compose.runtime.snapshotFlow {
                        if (pagerState.isScrollInProgress) pagerState.targetPage
                        else pagerState.currentPage
                    }
                        .drop(1)
                        .collect { page ->
                            if (isReconciling.value) return@collect
                            // The add-week page does not represent a teaching-week offset.
                            if (page >= latestWeekPageCount.value) return@collect
                            val newOffset = page + latestSafeMin.value
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
                    // Disable week paging while one-finger dragging a zoomed timetable.
                    userScrollEnabled = !zoomed,
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
                            ZoomableScheduleBox(
                                enabled = scheduleDisplay.pinchZoomEnabled,
                                zoom = zoom,
                                onZoomChange = { zoom = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            ) { stickyOffset, boxZoomed ->
                            ScheduleGrid(
                                modifier = Modifier.fillMaxSize(),
                                stickyOffset = stickyOffset,
                                pinnedHeaders = boxZoomed,
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
                                events = events,
                                onEventClick = onEventClick,
                                onEventLongClick = onEventLongClick,
                                onEventGroupClick = onEventGroupClick,
                            )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Trailing page for explicitly adding blank term weeks. */
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
    events: List<ScheduleEvent> = emptyList(),
    onEventClick: (ScheduleEvent, LocalDate) -> Unit = { _, _ -> },
    onEventLongClick: (ScheduleEvent, LocalDate) -> Unit = { _, _ -> },
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
                // Cache resolved day courses across selection recompositions and adjacent pager pages.
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
                    val beforeTerm = weekNumber != null && weekNumber < 1
                    val movedInHere = coursesMovedTo(
                        date = animatedDate,
                        overrides = temporaryScheduleOverrides,
                        courseById = { id -> allCourses.firstOrNull { it.id == id } },
                        isOriginallyActive = { course, from ->
                            val fromWeek = computeWeekNumberForDate(termStartDate, from)
                            beforeTerm || fromWeek == null || course.isActiveInWeek(fromWeek)
                        },
                    )
                    val courses = allCourses
                        .filterNot { isCourseMovedAwayFrom(animatedDate, it, temporaryScheduleOverrides) }
                        // Resolve each course's source day and week under partial swaps.
                        .mapNotNull { course ->
                            temporaryScheduleCourseSourceDate(
                                date = animatedDate,
                                course = course,
                                sourceDate = sourceDate,
                                overrides = temporaryScheduleOverrides,
                            )?.let { course to it }
                        }
                        .filter { (course, courseSource) ->
                            val courseWeek = computeWeekNumberForDate(termStartDate, courseSource) ?: weekNumber
                            beforeTerm || courseWeek == null || course.isActiveInWeek(courseWeek)
                        }
                        .map { (course, _) -> course }
                        .plus(movedInHere)
                        .sortedBy { it.time.startNode }
                    // Explicit moves into holidays render active; untouched holiday courses remain visible but unavailable.
                    val holidayOverridden = resolution.isHoliday && movedInHere.isNotEmpty()
                    DailyPageEntry(
                        resolution = if (holidayOverridden) resolution.copy(isHoliday = false) else resolution,
                        beforeTerm = beforeTerm,
                        courses = if (holidayOverridden) {
                            movedInHere.sortedBy { it.time.startNode }
                        } else {
                            courses
                        },
                        weekNumber = weekNumber,
                    )
                }
                val animatedResolution = dayEntry.resolution
                val beforeTerm = dayEntry.beforeTerm
                val active = dayEntry.courses
                DayList(
                    slots = slots,
                    courses = active,
                    displayWeek = dayEntry.weekNumber,
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
                    events = events,
                    onEventClick = onEventClick,
                    onEventLongClick = onEventLongClick,
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
    displayWeek: Int?,
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
    events: List<ScheduleEvent> = emptyList(),
    onEventClick: (ScheduleEvent, LocalDate) -> Unit = { _, _ -> },
    onEventLongClick: (ScheduleEvent, LocalDate) -> Unit = { _, _ -> },
) {
    val dayEvents = remember(events, targetDate) { events.occurrencesOn(targetDate) }
    val timedOrder = remember(slots, dayEvents) {
        val slotKeys = slots.mapIndexed { index, slot ->
            (clockMinute(slot.startTime) ?: (24 * 60 + index)) to slot
        }
        val eventKeys = dayEvents.map { (it.startMinute ?: 0) to it }
        (slotKeys + eventKeys).sortedWith(compareBy({ it.first }, { if (it.second is ScheduleEvent) 1 else 0 }))
            .map { it.second }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        timedOrder.forEach { item ->
            if (item is ScheduleEvent) {
                DayEventRow(
                    event = item,
                    cornerRadius = scheduleCardStyle.courseCornerRadiusDp.dp,
                    titleSizeSp = scheduleTextStyle.courseTextSizeSp.toFloat(),
                    onClick = { onEventClick(item, targetDate) },
                    onLongClick = { onEventLongClick(item, targetDate) },
                )
                return@forEach
            }
            val slot = item as DisplaySlot
            val coursesInSlot = courses.filter { course ->
                course.time.startNode <= slot.endNode && course.time.endNode >= slot.startNode
            }
            // Draw a spanning course only where its first slot begins.
            val starting = coursesInSlot.filter { it.time.startNode in slot.startNode..slot.endNode }
            if (starting.isEmpty()) {
                return@forEach
            }
            DayRow(
                slot = slot,
                courses = starting,
                displayWeek = displayWeek,
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
        if (courses.isEmpty() && dayEvents.isEmpty()) {
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
    displayWeek: Int?,
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
                    val textOverflow = if (scheduleTextStyle.truncationEllipsis) {
                        TextOverflow.Ellipsis
                    } else {
                        TextOverflow.Clip
                    }
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
                            overflow = textOverflow,
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
                        val rowLocation = course.locationForWeek(displayWeek)
                        if (scheduleDisplay.locationVisible && rowLocation.isNotBlank()) {
                            Text(
                                text = formatCourseLocation(rowLocation, scheduleDisplay, LocalScheduleLocationSuffix.current),
                                color = onColor.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = textOverflow,
                            )
                        }
                        if (scheduleDisplay.teacherVisible && course.teacher.isNotBlank()) {
                            Text(
                                text = course.teacher,
                                color = onColor.copy(alpha = 0.82f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = textOverflow,
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
                        if (isCourseMovedTo(targetDate, course, temporaryScheduleOverrides)) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.schedule_moved_badge),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 11.sp,
                                    lineHeight = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
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

internal fun computeWeekNumberForDate(
    termStart: LocalDate?,
    target: LocalDate,
): Int? = termStart?.let { resolveTermWeekNumber(it, target) }

/** Detail badges use the cell's resolved source date, matching course-week filtering. */
internal fun detailWeekNumber(
    targetDate: LocalDate,
    termStart: LocalDate?,
    temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
): Int? = computeWeekNumberForDate(
    termStart,
    resolveScheduleDay(targetDate, temporaryScheduleOverrides, holidayCalendar).sourceDate,
)

internal sealed interface HolidayLabel {
    data class Named(val name: String) : HolidayLabel

    data class BuiltIn(val nameRes: Int) : HolidayLabel

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

internal sealed interface EmptyScheduleHint {
    data object NeedsSync : EmptyScheduleHint

    data object NoCourses : EmptyScheduleHint

    /** Courses exist, but none occur this week. */
    data object NoCourseThisWeek : EmptyScheduleHint
}

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

internal sealed interface ScheduleEmptyState {
    data class Holiday(val label: HolidayLabel) : ScheduleEmptyState

    data class NotStarted(val termStartMonth: Int, val termStartDay: Int) : ScheduleEmptyState

    data object NotStartedWithoutDate : ScheduleEmptyState

    data object NoSchedule : ScheduleEmptyState

    /** A timetable exists without courses in this week. */
    data object EmptyWeek : ScheduleEmptyState
}

/** Empty-state precedence: holiday, pre-term, then missing timetable. */
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
    events: List<ScheduleEvent> = emptyList(),
    onEventClick: (ScheduleEvent, LocalDate) -> Unit = { _, _ -> },
    onEventLongClick: (ScheduleEvent, LocalDate) -> Unit = { _, _ -> },
    onEventGroupClick: (List<ScheduleEvent>, LocalDate) -> Unit = { _, _ -> },
    stickyOffset: () -> androidx.compose.ui.unit.IntOffset = { androidx.compose.ui.unit.IntOffset.Zero },
    pinnedHeaders: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val cellGroups = remember(activeEntries) {
        activeEntries
            .groupBy { it.placement.dayIndex to it.placement.rowIndex }
            .map { (_, list) ->
                val main = list.first()
                val sorted = list.map { it.course }.distinctBy { it.id }
                // Use deduplicated current-cell course counts for expandable badges.
                Triple(main, sorted, sorted.size)
            }
    }
    val visibleDays = remember(week.days, columnDayOfWeeks) {
        week.days.filter { it.dayOfWeek in columnDayOfWeeks }
    }
    val dayColumnCount = visibleDays.size.coerceAtLeast(1)
    val dayEvents = remember(events, visibleDays) { visibleDays.map { events.occurrencesOn(it.date) } }
    val timeline = remember(slots, dayEvents) {
        GridTimeline.build(
            slots = slots.map { it.clock() },
            events = dayEvents.flatten().mapNotNull { event ->
                val start = event.startMinute ?: return@mapNotNull null
                val end = event.endMinute ?: return@mapNotNull null
                MinuteRange(start, end)
            },
        )
    }
    val rows = timeline.rows

    // Keep quick-add hint state at grid scope; expire it after 2.5 seconds.
    var hintCell by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Pair<Int, Int>?>(null) }
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
        // Calculate header height with font scaling for weekday, date and override text.
        val headerDensity = androidx.compose.ui.platform.LocalDensity.current
        val dayHeaderMinHeight = dayHeaderHeight(
            density = headerDensity,
            headerTextSizeSp = scheduleTextStyle.headerTextSizeSp,
            hasExtraLine = visibleDays.any {
                it.overrideLabel != null || it.holidayLabel != null || it.isMakeUpWorkday
            },
        )
        val totalWidth = maxWidth
        // Measure whether the complete period-and-event timeline fits; otherwise permit vertical scrolling.
        val fitSlotHeight = if (slots.isEmpty()) {
            MIN_FIT_SLOT_HEIGHT
        } else {
            (maxHeight - dayHeaderMinHeight) / timeline.totalUnits
        }
        val fitRequested = scheduleDisplay.rowFitMode == ScheduleRowFitMode.Fit && slots.isNotEmpty()
        val fitMode = fitRequested && fitSlotHeight >= MIN_FIT_SLOT_HEIGHT
        // Keep the minimum compact row height when inserted event bands alone require scrolling.
        val squeezedByEvents = fitRequested && !fitMode && timeline.hasInsertedRows &&
            (maxHeight - dayHeaderMinHeight) / slots.size >= MIN_FIT_SLOT_HEIGHT
        val slotHeight = when {
            fitMode -> fitSlotHeight
            squeezedByEvents -> MIN_FIT_SLOT_HEIGHT
            else -> scheduleCardStyle.courseCardHeightDp.dp
        }
        val gridHeight = slotHeight * timeline.totalUnits

        val minEventUnits = EVENT_MIN_HEIGHT / slotHeight
        val dayEventGroups = remember(dayEvents, timeline, minEventUnits) {
            dayEvents.map { list ->
                val placed = list.map { event ->
                    val top = timeline.yOf(event.startMinute ?: 0)
                    val bottom = maxOf(timeline.yOf(event.endMinute ?: 0), top + minEventUnits)
                        .coerceAtMost(timeline.totalUnits)
                    Triple(event, top, bottom)
                }
                clusterByOverlap(placed, { it.second }, { it.third }).map { group ->
                    PlacedEventGroup(
                        events = group.map { it.first },
                        top = group.minOf { it.second },
                        bottom = group.maxOf { it.third },
                    )
                }
            }
        }
        val lanes = remember(cellGroups, dayEventGroups, timeline) {
            val items = cellGroups.map { (entry, _, _) ->
                val p = entry.placement
                LaneItem<Any>(
                    key = p.dayIndex to p.rowIndex,
                    top = timeline.slotTop(p.rowIndex),
                    bottom = timeline.slotBottom(p.rowIndex + p.rowSpan - 1),
                    order = 0,
                ) to p.dayIndex
            } + dayEventGroups.flatMapIndexed { dayIndex, groups ->
                groups.map { group ->
                    LaneItem<Any>(
                        key = EventLaneKey(dayIndex, group.key),
                        top = group.top,
                        bottom = group.bottom,
                        order = 1,
                    ) to dayIndex
                }
            }
            items.groupBy({ it.second }, { it.first })
                .values
                .fold(emptyMap<Any, LanePosition>()) { acc, day -> acc + assignLanes(day) }
        }
        val columns = remember(lanes, dayColumnCount) {
            val laneCounts = IntArray(dayColumnCount) { 1 }
            lanes.forEach { (key, position) ->
                val day = when (key) {
                    is EventLaneKey -> key.dayIndex
                    is Pair<*, *> -> key.first as? Int
                    else -> null
                } ?: return@forEach
                if (day in laneCounts.indices) laneCounts[day] = maxOf(laneCounts[day], position.laneCount)
            }
            DayColumns.of(laneCounts.toList())
        }
        // Redistribute expanded columns only while every day remains within the viewport.
        val gridAvailable = totalWidth - timeColumnWidth
        val fittedColumns = remember(columns, gridAvailable) {
            columns.fitInto(availableDp = gridAvailable.value, minUnitDp = MIN_DAY_COLUMN_DP)
        }
        val dayColumnWidth = (gridAvailable / fittedColumns.totalUnits).coerceAtLeast(MIN_DAY_COLUMN_DP.dp)
        val gridWidth = dayColumnWidth * fittedColumns.totalUnits
        val columnX: (Int) -> Dp = { index -> dayColumnWidth * fittedColumns.start(index) }
        val columnWidth: (Int) -> Dp = { index -> dayColumnWidth * fittedColumns.width(index) }

        // Apply backgrounds beneath both header and period columns.
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
            val pinnedColor = MaterialTheme.colorScheme.surface
            val pinnedBackground = if (pinnedHeaders) Modifier.background(pinnedColor) else Modifier
            Row(
                modifier = Modifier
                    .zIndex(2f)
                    .graphicsLayer { translationY = stickyOffset().y.toFloat() }
                    .then(pinnedBackground)
                    .heightIn(min = dayHeaderMinHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .zIndex(1f)
                        .graphicsLayer { translationX = stickyOffset().x.toFloat() }
                        .then(pinnedBackground),
                ) {
                    MonthCornerCell(
                        monthNumber = week.days.firstOrNull()?.monthNumber,
                        width = timeColumnWidth,
                        scheduleTextStyle = scheduleTextStyle,
                        customColorsAdaptToTheme = customColorsAdaptToTheme,
                    )
                }
                visibleDays.forEachIndexed { index, day ->
                    DayHeader(
                        day = day,
                        width = columnWidth(index),
                        scheduleTextStyle = scheduleTextStyle,
                        customColorsAdaptToTheme = customColorsAdaptToTheme,
                        onDoubleTap = { onDayHeaderDoubleTap(day.date) },
                    )
                }
            }

            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier
                        .zIndex(1f)
                        .graphicsLayer { translationX = stickyOffset().x.toFloat() }
                        .then(pinnedBackground)
                        .width(timeColumnWidth),
                ) {
                    rows.forEach { row ->
                        when (row) {
                            is TimelineRow.Slot -> TimeCell(
                                slot = slots[row.index],
                                height = slotHeight,
                                showTime = scheduleDisplay.nodeColumnTimeEnabled,
                                scheduleTextStyle = scheduleTextStyle,
                                customColorsAdaptToTheme = customColorsAdaptToTheme,
                            )
                            is TimelineRow.Gap -> InsertedTimeCell(
                                startMinute = row.startMinute,
                                endMinute = row.endMinute,
                                height = slotHeight * row.height,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .width(gridWidth)
                        .height(gridHeight),
                ) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val darkTheme = isDarkColorScheme()
                    val insertedTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
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
                                rows.filterIsInstance<TimelineRow.Gap>().forEach { gap ->
                                    drawRect(
                                        color = insertedTint,
                                        topLeft = androidx.compose.ui.geometry.Offset(0f, gap.top * slotHeight.toPx()),
                                        size = androidx.compose.ui.geometry.Size(size.width, gap.height * slotHeight.toPx()),
                                    )
                                }
                                val strokeWidth = scheduleCardStyle.gridBorderWidthDp.dp.toPx()
                                if (strokeWidth <= 0f) return@drawBehind
                                val pathEffect = if (scheduleCardStyle.gridBorderDashed) {
                                    PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()), 0f)
                                } else {
                                    null
                                }
                                for (i in 1 until rows.size) {
                                    val y = slotHeight.toPx() * rows[i].top
                                    drawLine(
                                        color = lineColor,
                                        start = androidx.compose.ui.geometry.Offset(0f, y),
                                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                                        strokeWidth = strokeWidth,
                                        pathEffect = pathEffect,
                                    )
                                }
                                for (i in 1 until dayColumnCount) {
                                    val x = columnX(i).toPx()
                                    drawLine(
                                        color = lineColor,
                                        start = androidx.compose.ui.geometry.Offset(x, 0f),
                                        end = androidx.compose.ui.geometry.Offset(x, size.height),
                                        strokeWidth = strokeWidth,
                                        pathEffect = pathEffect,
                                    )
                                }
                            }
                            .pointerInput(slots.size, dayColumnWidth, slotHeight, occupiedCells, fittedColumns, timeline) {
                                detectTapGestures(
                                    onTap = { offset: androidx.compose.ui.geometry.Offset ->
                                        val dayWidthPx = with(density) { dayColumnWidth.toPx() }
                                        val slotHeightPx = with(density) { slotHeight.toPx() }
                                        val day = fittedColumns.indexAt(offset.x / dayWidthPx)
                                        val slot = timeline.slotIndexAt(offset.y / slotHeightPx)
                                            ?: return@detectTapGestures
                                        if ((day to slot) !in occupiedCells) {
                                            hintCell = day to slot
                                        }
                                    },
                                    // Blank-cell double taps open the same day policy sheet as header taps.
                                    onDoubleTap = { offset: androidx.compose.ui.geometry.Offset ->
                                        val dayWidthPx = with(density) { dayColumnWidth.toPx() }
                                        val slotHeightPx = with(density) { slotHeight.toPx() }
                                        val day = fittedColumns.indexAt(offset.x / dayWidthPx)
                                        val slot = timeline.slotIndexAt(offset.y / slotHeightPx)
                                            ?: return@detectTapGestures
                                        if ((day to slot) !in occupiedCells) {
                                            visibleDays.getOrNull(day)?.let { onDayHeaderDoubleTap(it.date) }
                                        }
                                    },
                                )
                            },
                    ) {

                        val dayWidthPx = with(density) { dayColumnWidth.toPx() }
                        val slotHeightPx = with(density) { slotHeight.toPx() }
                        // Map timeline positions to nearest periods before applying equal-row drag calculations.
                        val snapDragY: (Int, Float) -> Float = { startRow, dy ->
                            if (!timeline.hasInsertedRows) {
                                dy
                            } else {
                                val target = timeline.nearestSlotIndex(timeline.slotTop(startRow) + dy / slotHeightPx)
                                (target - startRow) * slotHeightPx
                            }
                        }
                        val snapDragX: (Int, Float) -> Float = { startDay, dx ->
                            if (fittedColumns.isUniform) {
                                dx
                            } else {
                                val center = fittedColumns.start(startDay) + fittedColumns.width(startDay) / 2f
                                val target = fittedColumns.indexAt(center + dx / dayWidthPx)
                                (target - startDay) * dayWidthPx
                            }
                        }
                        val snapResizeY: (CourseResizeEdge, Int, Int, Float) -> Float = { edge, startRow, span, dy ->
                            if (!timeline.hasInsertedRows) {
                                dy
                            } else if (edge == CourseResizeEdge.Top) {
                                snapDragY(startRow, dy)
                            } else {
                                val endRow = startRow + span - 1
                                val target = timeline.nearestSlotIndex(timeline.slotBottom(endRow) + dy / slotHeightPx - 1f)
                                (target - endRow) * slotHeightPx
                            }
                        }
                        val dragTarget = draggingCourseId?.let { id ->
                            cellGroups.firstOrNull { it.first.course.id == id }?.let { (entry, _, _) ->
                                resolveCourseDragTarget(
                                    startDayIndex = entry.placement.dayIndex,
                                    startRowIndex = entry.placement.rowIndex,
                                    rowSpan = entry.placement.rowSpan,
                                    dragOffsetX = snapDragX(entry.placement.dayIndex, dragOffset.x),
                                    dragOffsetY = snapDragY(entry.placement.rowIndex, dragOffset.y),
                                    dayColumnWidthPx = dayWidthPx,
                                    slotHeightPx = slotHeightPx,
                                    dayColumnCount = dayColumnCount,
                                    slotCount = slots.size,
                                    occupiedByOthers = occupiedByOthers,
                                )
                            }
                        }
                        dragTarget?.let { target ->
                            val span = cellGroups
                                .firstOrNull { it.first.course.id == draggingCourseId }
                                ?.first?.placement?.rowSpan ?: 1
                            Box(
                                modifier = Modifier
                                    .width(columnWidth(target.dayIndex) - 2.dp)
                                    .height(
                                        slotHeight * (timeline.slotBottom(target.rowIndex + span - 1) - timeline.slotTop(target.rowIndex)) - 3.dp,
                                    )
                                    .offset(
                                        x = columnX(target.dayIndex) + 1.dp,
                                        y = slotHeight * timeline.slotTop(target.rowIndex) + 1.dp,
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
                            val courseTop = timeline.slotTop(placement.rowIndex)
                            val courseBottom = timeline.slotBottom(placement.rowIndex + placement.rowSpan - 1)
                            val courseHeight = slotHeight * (courseBottom - courseTop) - 3.dp
                            val lane = lanes[placement.dayIndex to placement.rowIndex] ?: LanePosition(0, 1)
                            val (laneStart, laneSpan) = laneFraction(lane)
                            val colWidth = columnWidth(placement.dayIndex)
                            val isDragging = course.id == draggingCourseId
                            CourseBlock(
                                course = course,
                                displayLocation = course.locationForWeek(mainEntry.sourceWeekIndex),
                                badges = badgesForCourse(
                                    course,
                                    uiSchema.courseBadges,
                                    LocalScheduleLocationSuffix.current,
                                ),
                                hasReminder = hasReminderForCourse(course, reminderRules, timingProfile),
                                hasNote = courseNotes.hasNote(course.id),
                                selected = course.id == selectedCourseId,
                                inactive = mainEntry.inactive,
                                onHoliday = mainEntry.onHoliday,
                                temporarilyCancelled = mainEntry.temporarilyCancelled,
                                movedIn = mainEntry.movedIn,
                                cellCount = count,
                                multiSelectMode = multiSelectMode,
                                multiSelected = isMultiSelected,
                                scheduleTextStyle = scheduleTextStyle,
                                scheduleCardStyle = scheduleCardStyle,
                                scheduleDisplay = scheduleDisplay,
                                customColorsAdaptToTheme = customColorsAdaptToTheme,
                                width = colWidth * laneSpan - 2.dp,
                                height = courseHeight,
                                offsetX = columnX(placement.dayIndex) + colWidth * laneStart + 1.dp,
                                offsetY = slotHeight * courseTop + 1.dp,
                                onClick = {
                                    val columnDate = visibleDays.getOrNull(placement.dayIndex)?.date
                                        ?: week.weekStart
                                    onCellClick(sortedCourses, columnDate)
                                },
                                onLongClick = {
                                    // Select among overlapping courses before editing.
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
                                        dragOffsetY = snapResizeY(edge, placement.rowIndex, placement.rowSpan, settled),
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
                                        dragOffsetX = snapDragX(placement.dayIndex, dragOffset.x),
                                        dragOffsetY = snapDragY(placement.rowIndex, dragOffset.y),
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

                        // Place events at actual clock coordinates without filling whole course cells.
                        dayEventGroups.forEachIndexed { dayIndex, groups ->
                            val date = visibleDays.getOrNull(dayIndex)?.date ?: return@forEachIndexed
                            groups.forEach { group ->
                                val lane = lanes[EventLaneKey(dayIndex, group.key)] ?: LanePosition(0, 1)
                                val (laneStart, laneSpan) = laneFraction(lane)
                                val colWidth = columnWidth(dayIndex)
                                val blockWidth = colWidth * laneSpan - 2.dp
                                val blockHeight = slotHeight * (group.bottom - group.top) - 2.dp
                                val blockX = columnX(dayIndex) + colWidth * laneStart + 1.dp
                                val blockY = slotHeight * group.top + 1.dp
                                val single = group.events.singleOrNull()
                                if (single != null) {
                                    EventBlock(
                                        event = single,
                                        width = blockWidth,
                                        height = blockHeight,
                                        offsetX = blockX,
                                        offsetY = blockY,
                                        cornerRadius = scheduleCardStyle.courseCornerRadiusDp.dp,
                                        titleSizeSp = scheduleTextStyle.courseTextSizeSp.toFloat(),
                                        onClick = { onEventClick(single, date) },
                                        onLongClick = { onEventLongClick(single, date) },
                                    )
                                } else {
                                    EventGroupBlock(
                                        events = group.events,
                                        width = blockWidth,
                                        height = blockHeight,
                                        offsetX = blockX,
                                        offsetY = blockY,
                                        cornerRadius = scheduleCardStyle.courseCornerRadiusDp.dp,
                                        onClick = { onEventGroupClick(group.events, date) },
                                    )
                                }
                            }
                        }

                        // Retain the prior quick-add hint during fade-out rather than removing it immediately.
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
                                            .width(columnWidth(day) - 2.dp)
                                            .height(slotHeight - 3.dp)
                                            .offset(
                                                x = columnX(day) + 1.dp,
                                                y = slotHeight * timeline.slotTop(slotIdx) + 1.dp,
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

            // Add bottom space only to scrolling layouts; compact mode needs its full row height.
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
                existingCourses = existingCourses,
                slotLabel = slots.firstOrNull { it.startNode == startNode && it.endNode == endNode }
                    ?.label?.takeIf { it.isNotBlank() },
                onDismiss = { addRequest = null },
                onConfirm = { course ->
                    onAddManualCourse(course)
                    addRequest = null
                },
            )
        }
    }
}

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

private const val DAY_PAGE_SPAN = 200

/** Minimum normal-column width. */
private const val MIN_DAY_COLUMN_DP = 36f

/** Minimum event height must contain one readable line. */
private val EVENT_MIN_HEIGHT = 22.dp

/** Per-occurrence lane key distinguishes repeated events across dates. */
private data class EventLaneKey(val dayIndex: Int, val eventId: String)

private data class PlacedEventGroup(
    val events: List<ScheduleEvent>,
    val top: Float,
    val bottom: Float,
) {
    val key: String get() = events.first().id
}

private val MIN_FIT_SLOT_HEIGHT = 52.dp

private val GRID_BOTTOM_SPACING = 28.dp

/** Optional proportional title shrinking; disabled returns the configured size. */
internal fun courseTitleFontSizeSp(
    baseSizeSp: Int,
    titleLength: Int,
    enabled: Boolean = true,
): Float {
    if (!enabled) return baseSizeSp.toFloat()
    // Choose shrink steps by title length.
    val scale = when {
        titleLength <= 5 -> 1f
        titleLength <= 7 -> 0.92f
        titleLength <= 9 -> 0.84f
        else -> 0.76f
    }
    // Keep a 9sp readability floor.
    return (baseSizeSp * scale).coerceAtLeast(9f)
}

/** Count complete lines only, retaining at least one title line. */
internal fun fitLineCount(availableHeightDp: Float, lineHeightDp: Float): Int {
    if (lineHeightDp <= 0f || !availableHeightDp.isFinite()) return Int.MAX_VALUE
    return kotlin.math.floor(availableHeightDp / lineHeightDp).toInt().coerceAtLeast(1)
}

/**
 * Measure actual blocks and omit those beyond available height; always retain the title with
 * its own overflow policy.
 */
@Composable
private fun FitOrDropColumn(
    centered: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.ui.layout.Layout(content = content, modifier = modifier) { measurables, constraints ->
        val childConstraints = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var used = 0
        for ((index, measurable) in measurables.withIndex()) {
            val placeable = measurable.measure(childConstraints)
            if (index > 0 && used + placeable.height > constraints.maxHeight) break
            placeables += placeable
            used += placeable.height
        }
        val width = constraints.maxWidth
        layout(width, used.coerceAtMost(constraints.maxHeight)) {
            var y = 0
            placeables.forEach { placeable ->
                val x = if (centered) (width - placeable.width) / 2 else 0
                placeable.placeRelative(x, y)
                y += placeable.height
            }
        }
    }
}

/**
 * Allocate title, location, then badges; do not show lower-priority text before the preceding
 * content fits.
 */
internal data class CourseCardTextPlan(
    val titleLines: Int,
    val titleComplete: Boolean,
    val showLocation: Boolean,
    val locationLines: Int,
    val locationComplete: Boolean,
    val showBadges: Boolean,
)

/**
 * Use measured titleLineBottoms and locationLineBottoms, including descenders; badgeHeight is
 * zero when absent.
 */
internal fun courseCardTextPlan(
    availableHeight: Float,
    titleLineBottoms: List<Float>,
    locationLineBottoms: List<Float>,
    badgeHeight: Float,
): CourseCardTextPlan {
    fun linesThatFit(bottoms: List<Float>, height: Float) = bottoms.count { it <= height + FIT_TOLERANCE_PX }

    val titleLines = linesThatFit(titleLineBottoms, availableHeight)
        .coerceAtLeast(1)
        .coerceAtMost(titleLineBottoms.size.coerceAtLeast(1))
    val titleComplete = titleLines >= titleLineBottoms.size
    var remaining = availableHeight - (titleLineBottoms.getOrNull(titleLines - 1) ?: 0f)

    var locationLines = 0
    if (titleComplete && locationLineBottoms.isNotEmpty()) {
        locationLines = linesThatFit(locationLineBottoms, remaining)
        if (locationLines > 0) {
            remaining -= locationLineBottoms[locationLines - 1]
        }
    }
    val locationComplete = locationLines >= locationLineBottoms.size
    val showBadges = badgeHeight > 0f && titleComplete && locationComplete &&
        badgeHeight <= remaining + FIT_TOLERANCE_PX
    return CourseCardTextPlan(
        titleLines = titleLines,
        titleComplete = titleComplete,
        showLocation = locationLines > 0,
        locationLines = locationLines,
        locationComplete = locationComplete,
        showBadges = showBadges,
    )
}

private const val MIN_FIT_TITLE_SP = 8f

private const val CARD_LOCATION_SP = 10f
private const val MIN_FIT_LOCATION_SP = 8f

private fun androidx.compose.ui.text.TextStyle.scaledCardText(
    baseSp: Float,
    scale: Float,
    minSp: Float = MIN_FIT_TITLE_SP,
): androidx.compose.ui.text.TextStyle {
    val size = (baseSp * scale).coerceAtLeast(minSp)
    return copy(fontSize = size.sp, lineHeight = (size + 1f).sp)
}

/**
 * Shrink title and location together to [minScale]; preserve full title and maximize readable
 * location lines, or avoid shrinking when it gains nothing.
 */
internal fun fitCourseCardTextScale(
    availableHeight: Float,
    minScale: Float,
    measure: (scale: Float) -> Pair<List<Float>, List<Float>>,
): Float {
    fun plan(scale: Float) = measure(scale).let { (title, location) ->
        courseCardTextPlan(availableHeight, title, location, badgeHeight = 0f)
    }
    fun fitsAll(scale: Float) = plan(scale).let { it.titleComplete && it.locationComplete }
    if (minScale >= 1f || fitsAll(1f)) return 1f
    val target: (Float) -> Boolean = if (fitsAll(minScale)) {
        ::fitsAll
    } else {
        val bestLocationLines = plan(minScale).takeIf { it.titleComplete }?.locationLines ?: 0
        { scale -> plan(scale).let { it.titleComplete && it.locationLines >= bestLocationLines } }
    }
    if (target(1f)) return 1f
    if (!target(minScale)) return minScale
    var low = minScale
    var high = 1f
    while (high - low > FIT_SCALE_PRECISION) {
        val mid = (low + high) / 2f
        if (target(mid)) low = mid else high = mid
    }
    return low
}

private const val FIT_SCALE_PRECISION = 0.01f

/** Normalize room separators before character wrapping to avoid isolating building prefixes. */
internal fun cardLocationText(location: String): String {
    if (location.isEmpty()) return location
    val codePoints = location.codePoints().toArray()
    return buildString {
        codePoints.forEachIndexed { index, codePoint ->
            val previous = codePoints.getOrNull(index - 1)
            if (previous != null && previous >= CJK_START && codePoint < 0x80 && Character.isLetterOrDigit(codePoint)) {
                append('-')
            }
            appendCodePoint(codePoint)
        }
    }
}

/**
 * Greedily insert line breaks using measured width, independent of word runs; avoid
 * nonzero-width joiner spacing.
 */
internal fun wrapByCharacter(text: String, maxWidth: Int, measureWidth: (String) -> Int): String {
    if (text.isEmpty() || maxWidth <= 0 || measureWidth(text) <= maxWidth) return text
    val codePoints = text.codePoints().toArray()
    val lines = mutableListOf<String>()
    var start = 0
    while (start < codePoints.size) {
        var end = start + 1
        while (end < codePoints.size && measureWidth(String(codePoints, start, end + 1 - start)) <= maxWidth) {
            end += 1
        }
        lines += String(codePoints, start, end - start)
        start = end
    }
    return lines.joinToString(separator = "\n")
}

private const val CJK_START = 0x2E80

/** Include final-line descender allowance when computing each truncated line height. */
private fun androidx.compose.ui.text.TextLayoutResult.cutHeights(lastLineExtra: Float): List<Float> =
    List(lineCount) { line -> getLineBottom(line) + if (line == lineCount - 1) 0f else lastLineExtra }

private fun androidx.compose.ui.text.TextMeasurer.lastLineExtra(style: androidx.compose.ui.text.TextStyle): Float {
    val alone = measure("汉", style, softWrap = false, maxLines = 1).size.height.toFloat()
    val inMiddle = measure("汉\n汉", style).getLineBottom(0)
    return (alone - inMiddle).coerceAtLeast(0f)
}

private const val FIT_TOLERANCE_PX = 0.5f

/**
 * Pass only the visible prefix when ellipsis is disabled to avoid leaking partial glyphs from
 * tightly spaced hidden lines.
 */
internal fun visibleLinesText(
    text: String,
    layout: androidx.compose.ui.text.TextLayoutResult,
    lines: Int,
): String {
    if (lines <= 0) return ""
    if (lines >= layout.lineCount) return text
    return text.substring(0, layout.getLineEnd(lines - 1, visibleEnd = true)).trimEnd()
}

/** Maximum decoded background edge; use power-of-two sampling above it. */
private const val BACKGROUND_MAX_EDGE_PX = 2048

/** Scale header height with system font size, including optional day-policy labels. */
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
    return (mainLine * 2 + extraLine + DAY_HEADER_PADDING).coerceAtLeast(DAY_HEADER_MIN_HEIGHT)
}

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
                it.padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(todayContainer)
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
        // Shrink header text to column width rather than clipping partial characters.
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
            DayHeaderTagText(
                text = stringResource(R.string.schedule_makeup_workday_tag),
                color = if (day.isToday) todayContent else MaterialTheme.colorScheme.error,
            )
        } else if (day.overrideLabel != null) {
            // Column position already denotes weekday; source labels need only the date.
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

/** Shrink within readable bounds, then ellipsize rather than clipping glyphs. */
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

/** Compact header policy label with a 10sp maximum. */
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
        // Allow extra shrink room for the widest source-date label inside the today badge.
        minFontSize = 5.sp,
        modifier = modifier,
    )
}

/** Long-press overlapping cells requires choosing the intended course first. */
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
            AppOutlinedButton(onClick = onDismiss) {
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
    // Resolve per-week location, falling back to the default.
    displayLocation: String,
    badges: List<String>,
    hasReminder: Boolean,
    hasNote: Boolean = false,
    selected: Boolean,
    inactive: Boolean,
    onHoliday: Boolean = false,
    temporarilyCancelled: Boolean,
    movedIn: Boolean = false,
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
    val cellTextOverflow = if (scheduleTextStyle.truncationEllipsis) {
        TextOverflow.Ellipsis
    } else {
        TextOverflow.Clip
    }
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
                // Keep annotation badges compact so titles retain line space.
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
                // Allocate title height before location; full details can supply omitted room text.
                val titleFontSizeSp = remember(titleSizeSp, course.title, scheduleTextStyle.autoShrinkLongTitles) {
                    courseTitleFontSizeSp(
                        baseSizeSp = titleSizeSp,
                        titleLength = course.title.length,
                        enabled = scheduleTextStyle.autoShrinkLongTitles,
                    )
                }
                // Fill title, location and annotation in priority order.
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val badgeText = badges.joinToString(separator = " · ")
                    val rawLocationText = cardLocationText(
                        formatCourseLocation(
                            displayLocation,
                            scheduleDisplay,
                            LocalScheduleLocationSuffix.current,
                        ),
                    )
                    val showLocationText = scheduleDisplay.locationVisible && rawLocationText.isNotBlank()
                    val showBadgeText = badges.isNotEmpty() && !inactive
                    val textAlign = if (horizontalCentered) TextAlign.Center else TextAlign.Start
                    val baseStyle = androidx.compose.material3.LocalTextStyle.current
                    // Measure and render with identical styles and greedy wrapping so prefix extraction preserves breaks.
                    val baseTitleStyle = remember(baseStyle, titleFontSizeSp, titleColor, textAlign) {
                        baseStyle.merge(
                            androidx.compose.ui.text.TextStyle(
                                color = titleColor,
                                fontSize = titleFontSizeSp.sp,
                                // Use compact line spacing for multiline titles.
                                lineHeight = (titleFontSizeSp + 1f).sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = textAlign,
                                lineBreak = androidx.compose.ui.text.style.LineBreak.Simple,
                            ),
                        )
                    }
                    val baseLocationStyle = remember(baseStyle, onColor, textAlign) {
                        baseStyle.merge(
                            androidx.compose.ui.text.TextStyle(
                                color = onColor.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                lineHeight = 11.sp,
                                textAlign = textAlign,
                                lineBreak = androidx.compose.ui.text.style.LineBreak.Simple,
                            ),
                        )
                    }
                    val badgeStyle = remember(baseStyle, onColor, textAlign) {
                        baseStyle.merge(
                            androidx.compose.ui.text.TextStyle(
                                color = onColor.copy(alpha = 0.9f),
                                fontSize = 9.sp,
                                lineHeight = 11.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = textAlign,
                            ),
                        )
                    }
                    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
                    val widthConstraints = Constraints(maxWidth = constraints.maxWidth)
                    // Fit title and location together against measured available height when shrinking is enabled.
                    val fitScale = remember(
                        course.title,
                        rawLocationText,
                        showLocationText,
                        baseTitleStyle,
                        baseLocationStyle,
                        constraints.maxWidth,
                        constraints.maxHeight,
                        scheduleTextStyle.autoShrinkLongTitles,
                    ) {
                        if (!scheduleTextStyle.autoShrinkLongTitles || !constraints.hasBoundedHeight) {
                            1f
                        } else {
                            fitCourseCardTextScale(
                                availableHeight = constraints.maxHeight.toFloat(),
                                minScale = (MIN_FIT_TITLE_SP / titleFontSizeSp).coerceAtMost(1f),
                            ) { scale ->
                                val fittedTitle = baseTitleStyle.scaledCardText(titleFontSizeSp, scale)
                                val fittedLocation = baseLocationStyle.scaledCardText(CARD_LOCATION_SP, scale, MIN_FIT_LOCATION_SP)
                                val titleBottoms = textMeasurer.measure(course.title, fittedTitle, constraints = widthConstraints)
                                    .cutHeights(textMeasurer.lastLineExtra(fittedTitle))
                                val locationBottoms = if (showLocationText) {
                                    val wrapped = wrapByCharacter(rawLocationText, constraints.maxWidth) { piece ->
                                        textMeasurer.measure(piece, fittedLocation, softWrap = false, maxLines = 1).size.width
                                    }
                                    textMeasurer.measure(wrapped, fittedLocation, constraints = widthConstraints)
                                        .cutHeights(textMeasurer.lastLineExtra(fittedLocation))
                                } else {
                                    emptyList()
                                }
                                titleBottoms to locationBottoms
                            }
                        }
                    }
                    val titleStyle = remember(baseTitleStyle, fitScale) {
                        if (fitScale >= 1f) baseTitleStyle else baseTitleStyle.scaledCardText(titleFontSizeSp, fitScale)
                    }
                    val locationStyle = remember(baseLocationStyle, fitScale) {
                        if (fitScale >= 1f) {
                            baseLocationStyle
                        } else {
                            baseLocationStyle.scaledCardText(CARD_LOCATION_SP, fitScale, MIN_FIT_LOCATION_SP)
                        }
                    }
                    val locationText = remember(rawLocationText, locationStyle, constraints.maxWidth) {
                        wrapByCharacter(rawLocationText, constraints.maxWidth) { piece ->
                            textMeasurer.measure(piece, locationStyle, softWrap = false, maxLines = 1).size.width
                        }
                    }
                    val titleLayout = remember(course.title, titleStyle, constraints.maxWidth) {
                        textMeasurer.measure(course.title, titleStyle, constraints = widthConstraints)
                    }
                    val locationLayout = remember(locationText, locationStyle, constraints.maxWidth, showLocationText) {
                        if (showLocationText) {
                            textMeasurer.measure(locationText, locationStyle, constraints = widthConstraints)
                        } else {
                            null
                        }
                    }
                    val titleLastLineExtra = remember(titleStyle) { textMeasurer.lastLineExtra(titleStyle) }
                    val locationLastLineExtra = remember(locationStyle) { textMeasurer.lastLineExtra(locationStyle) }
                    val badgeHeight = remember(badgeText, badgeStyle, showBadgeText) {
                        if (showBadgeText) {
                            textMeasurer.measure(badgeText, badgeStyle, softWrap = false, maxLines = 1).size.height
                        } else {
                            0
                        }
                    }
                    val plan = courseCardTextPlan(
                        availableHeight = if (constraints.hasBoundedHeight) {
                            constraints.maxHeight.toFloat()
                        } else {
                            Float.MAX_VALUE
                        },
                        titleLineBottoms = titleLayout.cutHeights(titleLastLineExtra),
                        locationLineBottoms = locationLayout?.cutHeights(locationLastLineExtra).orEmpty(),
                        badgeHeight = badgeHeight.toFloat(),
                    )
                    // Use Text ellipsis only when requested; otherwise render the fitting line prefix.
                    val ellipsize = scheduleTextStyle.truncationEllipsis
                    FitOrDropColumn(centered = horizontalCentered) {
                        Text(
                            text = if (ellipsize) course.title else visibleLinesText(course.title, titleLayout, plan.titleLines),
                            style = titleStyle,
                            maxLines = plan.titleLines,
                            overflow = cellTextOverflow,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (plan.showLocation && locationLayout != null) {
                            Text(
                                text = if (ellipsize) {
                                    locationText
                                } else {
                                    visibleLinesText(locationText, locationLayout, plan.locationLines)
                                },
                                style = locationStyle,
                                maxLines = plan.locationLines,
                                overflow = cellTextOverflow,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (plan.showBadges) {
                            Text(
                                text = badgeText,
                                style = badgeStyle,
                                maxLines = 1,
                                softWrap = false,
                                overflow = cellTextOverflow,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

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

        // Render span handles only for editable courses.
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
                        // Consume handle Down events before parent vertical scrolling can claim them.
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

        // Distinct move marker highlights individually rescheduled courses.
        if (movedIn && !inactive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-2).dp, y = (-2).dp)
                    .size(15.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.schedule_moved_badge),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 9.sp,
                    lineHeight = 9.sp,
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

        if (hasNote && !inactive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = if (movedIn) (-19).dp else (-2).dp, y = (-2).dp)
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

        // Inset overlap-count badge avoids parent clipping.
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

/** Separate mutually exclusive override and holiday fields for semantic header coloring. */
internal data class DayHeaderModel(
    val date: LocalDate,
    val dayOfWeek: Int,
    val monthNumber: Int?,
    val weekdayLabelRes: Int,
    val dateLabel: DayDateLabel,
    val isToday: Boolean,
    val overrideLabel: SourceDateLabel? = null,
    val holidayLabel: HolidayLabel? = null,
    val isMakeUpWorkday: Boolean = false,
)

internal sealed interface DayDateLabel {
    data class Day(val dayOfMonth: Int) : DayDateLabel
    data class MonthStart(val month: Int) : DayDateLabel
}

/** Source-date fields localized by the UI. */
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
    val onHoliday: Boolean = false,
    val sourceWeekIndex: Int = 1,
    val movedIn: Boolean = false,
)

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
                        // Downsample to display size rather than retaining a full-resolution background.
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
): String = strippedLocationOrNull(location, locationSuffix)?.let { "@$it" }.orEmpty()

private data class CourseDetailRequest(
    val courses: List<CourseItem>,
    val targetDate: LocalDate,
)

private data class DailyPageEntry(
    val resolution: ScheduleDayResolution,
    val beforeTerm: Boolean,
    val courses: List<CourseItem>,
    val weekNumber: Int? = null,
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
            AppOutlinedButton(onClick = onClear) {
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
    // Unset dates use week one for pager arithmetic; actual numbering uses the contained Monday and separate known-state flag.
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
    // Preview fixed sample data while applying the real display column order.
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

/** Minimum editable week range expands to actual term coverage. */
private const val DefaultEditableWeekCount = 20

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
        // Pad untimed slots for courses beyond the timing profile so they remain visible.
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

/** Pad to [minimum] rows; [blockLabelFrom] and [nodesPerPad] control generated block labels. */
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

internal fun badgesForCourse(
    course: CourseItem,
    rules: List<CourseBadgeRule>,
    schoolName: String = "",
): List<String> = matchedBadgeLabels(course, rules)
    // Hide institution-only badges using strippedLocationOrNull.
    .filter { label -> schoolName.isBlank() || strippedLocationOrNull(label, schoolName) != null }

/** Infer institution text from unfiltered badge labels to avoid circular filtering. */
internal fun matchedBadgeLabels(
    course: CourseItem,
    rules: List<CourseBadgeRule>,
): List<String> {
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
 * Remove only rules dedicated to one course; shared period/label rules and exam muting have
 * separate controls.
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

/** Cell taps complete partial multiselection or clear a fully selected cell. */
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
        /**
         * Moved courses already passed source-week validation; do not recheck their destination
         * week.
         */
        val forceActive: Boolean = false,
    ) {
        /**
         * Explicit moved-in forceActive overrides holiday rendering; other holiday courses are
         * unavailable.
         */
        fun isInactive(weekNumberKnown: Boolean): Boolean = when {
            forceActive -> false
            onHoliday -> true
            else -> weekNumberKnown && !course.isActiveInWeek(sourceWeekIndex)
        }
    }
    val visibleColumns = columnDayOfWeeks
        .filter { it in 1..7 }
        .distinct()
        .mapIndexed { columnIndex, dayOfWeek -> dayOfWeek to columnIndex }
        .toMap()

    val displayCourses = allCourses.visibleScheduleCourses()
    val showEveryCourse = totalScheduleDisplayEnabled || !weekNumberKnown
    val resolved = if (weekStart != null) {
        visibleColumns.keys.flatMap { dayOfWeek ->
            val actualDate = columnDate(weekStart, dayOfWeek)
            val resolution = resolveScheduleDay(actualDate, temporaryScheduleOverrides, holidayCalendar)
            // Display holiday courses as unavailable while reminder planning skips them.
            val sourceDate = resolution.sourceDate
            // Resolve source dates per course after partial swaps.
            val stayingCourses = displayCourses
                .filterNot { isCourseMovedAwayFrom(actualDate, it, temporaryScheduleOverrides) }
                .mapNotNull { course ->
                    temporaryScheduleCourseSourceDate(
                        date = actualDate,
                        course = course,
                        sourceDate = sourceDate,
                        overrides = temporaryScheduleOverrides,
                    )?.let { course to (computeWeekNumberForDate(termStart, it) ?: weekIndex) }
                }
            val movedIn = coursesMovedTo(
                date = actualDate,
                overrides = temporaryScheduleOverrides,
                courseById = { id -> displayCourses.firstOrNull { it.id == id } },
                isOriginallyActive = { course, from ->
                    val fromWeek = computeWeekNumberForDate(termStart, from)
                    showEveryCourse || fromWeek == null || course.isActiveInWeek(fromWeek)
                },
            ).filterNot { it.reminderOnly }
                .map { it to (computeWeekNumberForDate(termStart, actualDate) ?: weekIndex) }
            // Filter weeks only for unmoved courses; moved occurrences already use original-week coverage.
            stayingCourses
                .filter { (course, courseWeekIndex) ->
                    showEveryCourse || (!course.reminderOnly && course.isActiveInWeek(courseWeekIndex))
                }
                .map { (course, week) -> Triple(course, week, false) }
                .plus(movedIn.map { (course, week) -> Triple(course, week, true) })
                .mapNotNull { (course, sourceWeekIndex, moved) ->
                    val columnIndex = visibleColumns[dayOfWeek] ?: return@mapNotNull null
                    val placement = coursePlacement(course, slots, columnIndex) ?: return@mapNotNull null
                    Resolved(
                        course = course,
                        placement = placement,
                        sourceWeekIndex = sourceWeekIndex,
                        forceActive = moved,
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
                    sourceWeekIndex = it.sourceWeekIndex,
                    movedIn = it.forceActive,
                )
        }
    }
    return entries
}

private fun shortWeekdayRes(dayOfWeek: DayOfWeek): Int = when (dayOfWeek) {
    DayOfWeek.MONDAY -> R.string.schedule_weekday_short_monday
    DayOfWeek.TUESDAY -> R.string.schedule_weekday_short_tuesday
    DayOfWeek.WEDNESDAY -> R.string.schedule_weekday_short_wednesday
    DayOfWeek.THURSDAY -> R.string.schedule_weekday_short_thursday
    DayOfWeek.FRIDAY -> R.string.schedule_weekday_short_friday
    DayOfWeek.SATURDAY -> R.string.schedule_weekday_short_saturday
    DayOfWeek.SUNDAY -> R.string.schedule_weekday_short_sunday
}

/** Scale period-column width with labels and fonts, capped at 30 percent of available width. */
@Composable
private fun timeColumnWidth(availableWidth: Dp, headerTextSizeSp: Int, labels: List<String>): Dp {
    val base = when {
        availableWidth < 360.dp -> 38.dp
        availableWidth < 420.dp -> 42.dp
        else -> 44.dp
    }
    val labelWidth =
        (headerTextSizeSp * timeColumnLabelChars(labels) * TIME_COLUMN_CHAR_WIDTH_FACTOR).dp + TIME_COLUMN_PADDING
    val scale = LocalDensity.current.fontScale.coerceIn(1f, 1.8f)
    return (maxOf(base, labelWidth) * scale).coerceAtMost(availableWidth * 0.34f)
}

private const val TIME_COLUMN_CHAR_WIDTH_FACTOR = 1.12f
private val TIME_COLUMN_PADDING = 8.dp

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
            AppOutlinedButton(onClick = onConfirm) {
                Text(stringResource(R.string.schedule_drag_confirm_apply))
            }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.schedule_drag_confirm_cancel))
            }
        },
    )
}
