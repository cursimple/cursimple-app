package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedTextField
import com.x500x.cursimple.core.data.ScheduleCardStylePreferences
import com.x500x.cursimple.core.kernel.model.DEFAULT_TERM_WEEK_COUNT
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import com.x500x.cursimple.feature.schedule.theme.CoursePaletteEntry
import androidx.compose.ui.input.pointer.pointerInput
import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.x500x.cursimple.core.data.note.COURSE_NOTE_MAX_LENGTH
import com.x500x.cursimple.core.data.note.CourseNoteInput
import com.x500x.cursimple.core.data.note.courseNoteLength
import com.x500x.cursimple.core.data.note.validateCourseNote
import com.x500x.cursimple.core.kernel.model.CourseDetailField
import com.x500x.cursimple.core.data.widget.courseSlotLabelText
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.ExamCountdown
import com.x500x.cursimple.core.kernel.model.locationForWeek
import com.x500x.cursimple.core.kernel.model.teacherForWeek
import com.x500x.cursimple.core.kernel.model.titleForWeek
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.typedClock
import com.x500x.cursimple.core.kernel.model.examCountdownOrNull
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import com.x500x.cursimple.feature.schedule.time.today
import java.time.LocalDate
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import com.x500x.cursimple.feature.plugin.ui.AppAlertDialog
import com.x500x.cursimple.feature.plugin.ui.AppDialogPrimaryButton
import com.x500x.cursimple.feature.plugin.ui.AppDialogSecondaryButton


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CourseDetailDialog(
    courses: List<CourseItem>,
    timingProfile: TermTimingProfile?,
    visibleWeekNumber: Int?,
    /** Use the current-week label only when the viewed week is actually current. */
    currentWeekNumber: Int? = null,
    isManual: (CourseItem) -> Boolean,
    /** Plugin display name for courses from a plugin timetable; shown in place of the generic label. */
    sourceName: String? = null,
    examReminderEnabled: Boolean = false,
    mutedExamCourseIds: Set<String> = emptySet(),
    targetDate: LocalDate? = null,
    dayIsHoliday: Boolean = false,
    isTemporarilyCancelled: (CourseItem) -> Boolean = { false },
    noteTextOf: (CourseItem) -> String = { "" },
    noteMaxLength: Int = COURSE_NOTE_MAX_LENGTH,
    isPluginOverride: (CourseItem) -> Boolean = { false },
    existingCourses: List<CourseItem> = emptyList(),
    maxNodeCount: Int = 12,
    maxWeekCount: Int = DEFAULT_TERM_WEEK_COUNT,
    sessionWeekCount: Int = maxWeekCount,
    termStartDate: LocalDate? = null,
    temporaryScheduleOverrides: List<com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride> = emptyList(),
    holidayCalendar: com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings = com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings.NONE,
    countHolidays: Boolean = true,
    courseEndWarningMode: com.x500x.cursimple.core.kernel.model.CourseEndWarningMode = com.x500x.cursimple.core.kernel.model.CourseEndWarningMode.Off,
    onSaveNote: (CourseItem, String) -> Unit = { _, _ -> },
    onSaveCourse: (CourseItem) -> Unit = {},
    sourceWeekOf: (CourseItem) -> Int? = { visibleWeekNumber },
    onRestorePluginCourse: (CourseItem) -> Unit = {},
    onDismiss: () -> Unit,
    onTemporaryCancel: (CourseItem) -> Unit = {},
    onRestoreTemporaryCancel: (CourseItem) -> Unit = {},
    onSetReminder: (CourseItem) -> Unit,
    hasCancellableReminder: (CourseItem) -> Boolean = { false },
    onCancelReminder: (CourseItem) -> Unit = {},
    onMuteExamReminder: (CourseItem) -> Unit = {},
    onRestoreExamReminder: (CourseItem) -> Unit = {},
    onDelete: (CourseItem) -> Unit,
    readOnly: Boolean = false,
    scheduleCardStyle: ScheduleCardStylePreferences = ScheduleCardStylePreferences(),
    onSetCustomCourseColor: (String, Long?) -> Unit = { _, _ -> },
    /** All notes; the ones in this course's notebook are shown as its homework and to-dos. */
    memos: List<com.x500x.cursimple.core.kernel.model.MemoNote> = emptyList(),
    onSaveMemo: (com.x500x.cursimple.core.kernel.model.MemoNote) -> Unit = {},
    attendance: com.x500x.cursimple.core.data.attendance.AttendanceData = com.x500x.cursimple.core.data.attendance.AttendanceData(),
    onMarkAttendance: (String, String, com.x500x.cursimple.core.data.attendance.AttendanceStatus?) -> Unit = { _, _, _ -> },
    onSetAbsenceAllowance: (String, Int?) -> Unit = { _, _ -> },
) {
    if (courses.isEmpty()) return
    // Reset selection when course IDs change so an old index cannot select another cell's course.
    val courseIdsKey = remember(courses) { courses.joinToString("|") { it.id } }
    var selectedIndex by rememberSaveable(courseIdsKey) { mutableIntStateOf(0) }
    val course = courses[selectedIndex.coerceIn(0, courses.size - 1)]
    val progress = remember(course, targetDate, existingCourses, termStartDate, sessionWeekCount,
        temporaryScheduleOverrides, holidayCalendar, countHolidays) {
        targetDate?.let { date -> courseSessionProgress(course, date, existingCourses.ifEmpty { listOf(course) },
            termStartDate, sessionWeekCount, temporaryScheduleOverrides, holidayCalendar, countHolidays) }
    }
    val sourceWeekNumber = sourceWeekOf(course)
    var editing by remember(course.id) { mutableStateOf(false) }
    // The inline editor's buttons live in a pinned bar, so saving never needs a scroll to the end.
    var editActions by remember(course.id) { mutableStateOf<CourseEditActions?>(null) }
    var confirmRestore by remember(course.id) { mutableStateOf(false) }
    var showColorPicker by remember(course.id) { mutableStateOf(false) }
    val accents = com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents.current
    val legacyColor = scheduleCardStyle.customCourseColors[course.id]
        ?: scheduleCardStyle.customCourseColors[course.title]
    val customColor = course.colorArgb ?: legacyColor
    val palette = remember(course.title, course.category, accents, customColor, course.colorEndArgb) {
        courseColor(course.title, accents.coursePalette, customColor, course.colorEndArgb)
    }
    val headerContainer = if (course.category == CourseCategory.Exam) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        palette.container
    }
    val headerContent = if (course.category == CourseCategory.Exam) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        palette.onContainer
    }
    val isThisWeek = sourceWeekNumber?.let { course.isActiveInWeek(it) }
    val manual = isManual(course)
    val pluginOverride = manual && isPluginOverride(course)
    val weekday = if (course.time.dayOfWeek in 1..7) {
        stringResource(scheduleWeekdayFullRes(course.time.dayOfWeek))
    } else {
        "?"
    }
    val context = LocalContext.current
    // Prefer the timetable's timing-slot name, otherwise use period numbers.
    val slotName = remember(course.time, timingProfile, context) {
        context.courseSlotLabelText(timingProfile, course.time.startNode, course.time.endNode)
    }
    val nodeRange = slotName ?: if (course.time.startNode == course.time.endNode) {
        stringResource(R.string.schedule_node_single, course.time.startNode)
    } else {
        stringResource(R.string.schedule_node_range, course.time.startNode, course.time.endNode)
    }
    val classTime = remember(course, timingProfile) { resolveClassTime(course, timingProfile) }
    val classTimeText = remember(classTime, context) { context.classTimeText(classTime) }
    // Avoid repeating period-only time text in both subtitle and body.
    val headerTimeText = (classTime as? ClassTimeInfo.Range)?.let { classTimeText }
    val weeksText = remember(course.weeks, context) {
        context.weeksDetailText(describeWeeksDetail(course.weeks))
    }
    val today = LocalAppZone.current.today()
    val examCountdown = remember(course, targetDate, today) {
        if (course.category == CourseCategory.Exam && targetDate != null) {
            examCountdownOrNull(course, targetDate, today)
        } else {
            null
        }
    }
    val sourceTerm = remember(course.details) {
        course.details.firstOrNull { it.label in setOf("来源学期", "Source Term", "來源學期") }?.value
    }

    if (!readOnly && confirmRestore) {
        AppAlertDialog(
            onDismissRequest = { confirmRestore = false },
            icon = { Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(22.dp)) },
            title = { Text(stringResource(R.string.schedule_course_detail_restore_plugin_title)) },
            text = { Text(stringResource(R.string.schedule_course_detail_restore_plugin_body, course.title)) },
            confirmButton = {
                AppDialogPrimaryButton(onClick = {
                    confirmRestore = false
                    onRestorePluginCourse(course)
                }) { Text(stringResource(R.string.schedule_action_restore_plugin)) }
            },
            dismissButton = {
                AppDialogSecondaryButton(onClick = { confirmRestore = false }) {
                    Text(stringResource(R.string.schedule_action_cancel))
                }
            },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Centered card like the original dialog; safeDrawing includes the keyboard, so the card moves up with it.
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
            Surface(
                // Wrap short content instead of always filling a fixed height.
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = minOf(maxHeight, 640.dp))
                    .pointerInput(Unit) { detectTapGestures {} }
                    // Same-slot courses can be flipped with a horizontal swipe as well as the arrows.
                    .pointerInput(courses.size, editing) {
                        if (courses.size <= 1) return@pointerInput
                        val threshold = 56.dp.toPx()
                        var travelled = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { travelled = 0f },
                            onDragCancel = { travelled = 0f },
                            onHorizontalDrag = { change, delta ->
                                // Editing a form scrolls horizontally nothing; keep the gesture for the form.
                                if (!editing) {
                                    travelled += delta
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                if (!editing && kotlin.math.abs(travelled) >= threshold) {
                                    selectedIndex = if (travelled < 0) {
                                        (selectedIndex + 1) % courses.size
                                    } else {
                                        (selectedIndex - 1 + courses.size) % courses.size
                                    }
                                }
                                travelled = 0f
                            },
                        )
                    }
                    .testTag("course-detail-card"),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    val chipSurface = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
                    Column(Modifier.fillMaxWidth().background(headerContainer)) {
                        Column(
                            modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (courses.size > 1) {
                                    AppCircleIconButton(
                                        onClick = { selectedIndex = (selectedIndex - 1 + courses.size) % courses.size; editing = false },
                                        size = 40.dp, containerColor = headerContent, borderColor = Color.Transparent, contentColor = headerContainer,
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.schedule_same_slot_prev), modifier = Modifier.size(24.dp))
                                    }
                                    AppCircleIconButton(
                                        onClick = { selectedIndex = (selectedIndex + 1) % courses.size; editing = false },
                                        size = 40.dp, containerColor = headerContent, borderColor = Color.Transparent, contentColor = headerContainer,
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.schedule_same_slot_next), modifier = Modifier.size(24.dp))
                                    }
                                    // Count and position on two lines so neither is shrunk or cut on a narrow screen.
                                    Column(Modifier.weight(1f)) {
                                        AppAtomicText(
                                            text = stringResource(R.string.schedule_course_detail_same_slot, courses.size).trimEnd('：', ':', ' '),
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = headerContent,
                                        )
                                        AppAtomicText(
                                            text = stringResource(R.string.schedule_same_slot_position, selectedIndex.coerceIn(0, courses.size - 1) + 1, courses.size),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = headerContent,
                                        )
                                    }
                                } else {
                                    CourseTitle(course.titleForWeek(sourceWeekNumber), headerContent, Modifier.weight(1f).padding(vertical = 4.dp))
                                }
                                AppCircleIconButton(
                                    onClick = onDismiss,
                                    size = 40.dp, containerColor = chipSurface, borderColor = Color.Transparent, contentColor = headerContent,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.schedule_action_close),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            if (courses.size > 1) {
                                CourseTitle(course.titleForWeek(sourceWeekNumber), headerContent, Modifier)
                            }
                            val titleOverridden = sourceWeekNumber != null &&
                                course.titleForWeek(sourceWeekNumber).trim() != course.title.trim()
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                StatusChip(
                                    activeInVisibleWeek = isThisWeek,
                                    visibleWeekNumber = visibleWeekNumber,
                                    viewingCurrentWeek = visibleWeekNumber != null &&
                                        visibleWeekNumber == currentWeekNumber,
                                    manual = manual,
                                    headerContent = headerContent,
                                    // Whole-day holiday state takes priority over per-course cancellation.
                                    notOccurringLabel = when {
                                        dayIsHoliday -> stringResource(R.string.schedule_status_on_holiday)
                                        targetDate != null && isTemporarilyCancelled(course) ->
                                            stringResource(R.string.schedule_status_cancelled)
                                        else -> null
                                    },
                                )
                                HeaderPill(
                                    text = stringResource(
                                        R.string.schedule_course_detail_header,
                                        if (course.category == CourseCategory.Exam) {
                                            stringResource(R.string.schedule_course_detail_exam_prefix)
                                        } else {
                                            ""
                                        },
                                        weekday,
                                        nodeRange,
                                    ) + headerTimeText.orEmpty().let { if (it.isBlank()) "" else " · $it" },
                                    content = headerContent,
                                )
                                if (progress != null && progress.current != null) {
                                    SessionProgressPill(
                                        label = stringResource(R.string.schedule_course_session_label),
                                        current = progress.current,
                                        total = progress.total,
                                        content = headerContent,
                                        modifier = Modifier.testTag("course-session-progress"),
                                    )
                                } else {
                                    HeaderPill(
                                        text = if (progress == null) {
                                            stringResource(R.string.schedule_course_session_needs_start)
                                        } else {
                                            stringResource(R.string.schedule_course_session_not_scheduled, progress.total)
                                        },
                                        content = headerContent,
                                        modifier = Modifier.testTag("course-session-progress"),
                                    )
                                }
                                val endWarning = if (courseEndWarningMode != com.x500x.cursimple.core.kernel.model.CourseEndWarningMode.Off) {
                                    com.x500x.cursimple.core.kernel.model.courseEndWarning(visibleWeekNumber, course.weeks, courseEndWarningMode)
                                } else {
                                    com.x500x.cursimple.core.kernel.model.CourseEndWarning.None
                                }
                                if (endWarning != com.x500x.cursimple.core.kernel.model.CourseEndWarning.None) {
                                    val final = endWarning == com.x500x.cursimple.core.kernel.model.CourseEndWarning.FinalWeek
                                    HeaderPill(
                                        text = stringResource(
                                            if (final) R.string.schedule_course_end_final else R.string.schedule_course_end_near,
                                        ),
                                        content = if (final) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onTertiary,
                                        containerOverride = if (final) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.testTag("course-end-warning"),
                                    )
                                }
                                if (titleOverridden) {
                                    HeaderPill(
                                        text = stringResource(R.string.schedule_course_override_badge) + " · " +
                                            stringResource(R.string.schedule_course_override_default, course.title),
                                        content = headerContent,
                                    )
                                }
                                if (sourceTerm != null) {
                                    HeaderPill(text = sourceTerm, content = headerContent, icon = Icons.Rounded.School)
                                }
                            }
                        }
                    }

                    Column(
                        // fill = false lets short details wrap instead of stretching to the height cap.
                        modifier = Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(androidx.compose.runtime.key(course.id) { rememberScrollState() })
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (courses.size > 1) {
                            val otherCourse = courses.firstOrNull { it.id != course.id }
                            val overlappingWeeks = remember(course.weeks, otherCourse?.weeks) {
                                if (otherCourse == null) emptySet()
                                else course.weeks.toSet().intersect(otherCourse.weeks.toSet())
                            }
                            if (overlappingWeeks.isNotEmpty() && !readOnly) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("course-conflict-resolution-card"),
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.SwapHoriz,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Text(
                                                text = stringResource(R.string.schedule_conflict_resolve_title),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            AppOutlinedButton(
                                                onClick = {
                                                    val oddCourse = course.copy(weeks = course.weeks.filter { it % 2 != 0 })
                                                    val evenCourse = otherCourse!!.copy(weeks = otherCourse.weeks.filter { it % 2 == 0 })
                                                    onSaveCourse(oddCourse)
                                                    onSaveCourse(evenCourse)
                                                },
                                                modifier = Modifier.weight(1f).testTag("course-conflict-odd-even"),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            ) {
                                                // May wrap to two lines on a narrow screen or a large font instead of being cut.
                                                Text(
                                                    text = stringResource(R.string.schedule_conflict_odd_even_split),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 2,
                                                )
                                            }
                                            AppOutlinedButton(
                                                onClick = {
                                                    val all = (course.weeks + otherCourse!!.weeks).sorted()
                                                    val mid = if (all.isNotEmpty()) (all.first() + all.last()) / 2 else 8
                                                    val firstHalfCourse = course.copy(weeks = course.weeks.filter { it <= mid })
                                                    val secondHalfCourse = otherCourse.copy(weeks = otherCourse.weeks.filter { it > mid })
                                                    onSaveCourse(firstHalfCourse)
                                                    onSaveCourse(secondHalfCourse)
                                                },
                                                modifier = Modifier.weight(1f).testTag("course-conflict-half-term"),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            ) {
                                                // May wrap to two lines on a narrow screen or a large font instead of being cut.
                                                Text(
                                                    text = stringResource(R.string.schedule_conflict_half_term_split),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 2,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (!readOnly && editing) {
                            CourseEditSection(
                                course = course,
                                existingCourses = remember(existingCourses, course.id) {
                                    existingCourses.filterNot { it.id == course.id }
                                },
                                maxNodeCount = maxNodeCount,
                                maxWeekCount = maxWeekCount,
                                slotTimes = timingProfile?.slotTimes.orEmpty(),
                                convertsToManual = !manual,
                                sourceWeekNumber = sourceWeekNumber,
                                viewingCurrentWeek = visibleWeekNumber != null && visibleWeekNumber == currentWeekNumber,
                                legacyColorArgb = legacyColor,
                                onCancel = { editing = false },
                                actionsHost = { editActions = it },
                                onSave = {
                                    onSaveCourse(it)
                                    // The editor now owns the colors; drop the older per-title pick so it cannot resurface.
                                    if (legacyColor != null) {
                                        onSetCustomCourseColor(course.id, null)
                                        onSetCustomCourseColor(course.title, null)
                                    }
                                    editing = false
                                },
                            )
                        } else {
                            Crossfade(targetState = CourseFactsSnapshot(course, classTimeText, weeksText, examCountdown, sourceWeekNumber, manual, pluginOverride),
                                animationSpec = tween(180), label = "course-detail-facts") { shown ->
                                val loc = shown.course.locationForWeek(shown.sourceWeek)
                                val teach = shown.course.teacherForWeek(shown.sourceWeek)
                                val locOverridden = shown.sourceWeek != null && loc.trim() != shown.course.location.trim()
                                val teachOverridden = shown.sourceWeek != null && teach.trim() != shown.course.teacher.trim()
                                CourseFactsCard(
                                    classTimeText = shown.time,
                                    weeksText = shown.weeks,
                                    examCountdown = shown.countdown,
                                    location = loc,
                                    defaultLocation = shown.course.location,
                                    locationOverridden = locOverridden,
                                    teacher = teach,
                                    defaultTeacher = shown.course.teacher,
                                    teacherOverridden = teachOverridden,
                                    details = shown.course.details,
                                    manual = shown.manual,
                                    pluginOverride = shown.pluginOverride,
                                    sourceName = sourceName,
                                    isExam = shown.course.category == CourseCategory.Exam,
                                )
                            }

                            CourseNoteSection(
                                courseKey = course.id,
                                savedNote = noteTextOf(course),
                                maxLength = noteMaxLength,
                                onSave = { text -> onSaveNote(course, text) },
                                readOnly = readOnly,
                            )

                            CourseTodosSection(
                                course = course,
                                todos = remember(memos, course.title) { courseTodosFor(memos, course) },
                                onSave = onSaveMemo,
                                classDate = targetDate,
                                readOnly = readOnly,
                                today = LocalAppZone.current.today(),
                            )

                            if (!readOnly && course.category == CourseCategory.Course && !course.reminderOnly) {
                                val limitToast = stringResource(R.string.attendance_limit_toast)
                                val toastContext = LocalContext.current
                                CourseAttendanceSection(
                                    course = course,
                                    sessionDate = targetDate,
                                    today = LocalAppZone.current.today(),
                                    attendance = attendance,
                                    onMark = onMarkAttendance,
                                    onSetAllowance = onSetAbsenceAllowance,
                                    onLimitReached = { Toast.makeText(toastContext, limitToast, Toast.LENGTH_SHORT).show() },
                                )
                            }

                            if (!readOnly && course.category == CourseCategory.Exam) {
                                ExamReminderMuteRow(
                                    enabled = examReminderEnabled,
                                    muted = course.id in mutedExamCourseIds,
                                    onMute = { onMuteExamReminder(course) },
                                    onRestore = { onRestoreExamReminder(course) },
                                )
                            }
                        }
                    }
                    if (showColorPicker) {
                        CourseColorSheet(
                            course = course,
                            palette = accents.coursePalette,
                            onChange = { appearance ->
                                onSaveCourse(course.copy(colorArgb = appearance.fill, colorEndArgb = appearance.fillEnd, borderArgb = appearance.border))
                            },
                            onDismiss = { showColorPicker = false },
                        )
                    }
                    if (!readOnly && editing) {
                        editActions?.let { actions ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp)
                                    .testTag("course-edit-actions-pinned"),
                            ) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    AppOutlinedButton(
                                        onClick = actions.onCancel,
                                        shape = CircleShape,
                                        modifier = Modifier.heightIn(min = 46.dp),
                                    ) {
                                        Text(stringResource(R.string.schedule_action_cancel))
                                    }
                                    if (actions.applyWeekLabel != null && actions.onApplyWeek != null) {
                                        AppOutlinedButton(
                                            onClick = actions.onApplyWeek,
                                            shape = CircleShape,
                                            modifier = Modifier.heightIn(min = 46.dp).testTag("course-edit-apply-this-week"),
                                        ) {
                                            Text(actions.applyWeekLabel, maxLines = 1, softWrap = false)
                                        }
                                    }
                                    Button(
                                        onClick = actions.onSave,
                                        enabled = actions.canSave,
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                        modifier = Modifier.heightIn(min = 46.dp).testTag("course-edit-save"),
                                    ) {
                                        Text(stringResource(R.string.schedule_action_save), maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }
                    }
                    // The keyboard needs the room more than the actions do (and the note editor has its own Save).
                    if (!readOnly && !editing && !WindowInsets.isImeVisible) {
                        Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp).testTag("course-detail-actions")) {
                            CourseActionBar(
                                temporarilyCancelled = targetDate != null && isTemporarilyCancelled(course),
                                showTemporaryCancel = targetDate != null,
                                showDelete = manual && !pluginOverride,
                                showRestorePlugin = pluginOverride,
                                onEdit = { editing = true },
                                showColor = course.category != CourseCategory.Exam,
                                onColor = { showColorPicker = true },
                                hasReminder = hasCancellableReminder(course),
                                onSetReminder = { onSetReminder(course) },
                                onCancelReminder = { onCancelReminder(course) },
                                onTemporaryCancel = { onTemporaryCancel(course) },
                                onRestoreTemporaryCancel = { onRestoreTemporaryCancel(course) },
                                onDelete = { onDelete(course) },
                                onRestorePlugin = { confirmRestore = true },
                            )
                        }
                    }
                    // Let the sheet run under the system navigation bar instead of floating above it.
                    Spacer(Modifier.fillMaxWidth().navigationBarsPadding())
                }
            }
        }
    }
}

private data class CourseFactsSnapshot(val course: CourseItem, val time: String, val weeks: String,
    val countdown: ExamCountdown?, val sourceWeek: Int?, val manual: Boolean, val pluginOverride: Boolean)

internal fun applyDraftToTargetWeek(
    baseCourse: CourseItem,
    draftCourse: CourseItem,
    targetWeek: Int,
): CourseItem {
    fun updateMap(
        baseValue: String,
        currentValue: String,
        existingMap: Map<Int, String>,
    ): Map<Int, String> {
        val trimmedCurrent = currentValue.trim()
        val trimmedBase = baseValue.trim()
        return if (trimmedCurrent.isNotBlank() && trimmedCurrent != trimmedBase) {
            existingMap + (targetWeek to trimmedCurrent)
        } else {
            existingMap - targetWeek
        }
    }

    val updatedWeekTitles = updateMap(baseCourse.title, draftCourse.title, draftCourse.weekTitles)
    val updatedWeekTeachers = updateMap(baseCourse.teacher, draftCourse.teacher, draftCourse.weekTeachers)
    val updatedWeekLocations = updateMap(baseCourse.location, draftCourse.location, draftCourse.weekLocations)
    val effectiveWeeks = if (targetWeek in draftCourse.weeks) draftCourse.weeks else (draftCourse.weeks + targetWeek).sorted()

    return draftCourse.copy(
        title = baseCourse.title,
        teacher = baseCourse.teacher,
        location = baseCourse.location,
        weeks = effectiveWeeks,
        weekTitles = updatedWeekTitles,
        weekTeachers = updatedWeekTeachers,
        weekLocations = updatedWeekLocations,
    )
}

internal fun resolveSaveDraft(
    baseCourse: CourseItem,
    draftCourse: CourseItem,
    sourceWeekNumber: Int?,
    initialWeekTitle: String,
    initialWeekTeacher: String,
    initialWeekLocation: String,
): CourseItem {
    if (sourceWeekNumber == null) return draftCourse

    val titleEdited = draftCourse.title.trim() != initialWeekTitle.trim()
    val teacherEdited = draftCourse.teacher.trim() != initialWeekTeacher.trim()
    val locationEdited = draftCourse.location.trim() != initialWeekLocation.trim()

    val effectiveTitle = if (titleEdited) draftCourse.title.trim() else baseCourse.title
    val effectiveTeacher = if (teacherEdited) draftCourse.teacher.trim() else baseCourse.teacher
    val effectiveLocation = if (locationEdited) draftCourse.location.trim() else baseCourse.location

    val effectiveWeekTitles = if (titleEdited) draftCourse.weekTitles - sourceWeekNumber else draftCourse.weekTitles
    val effectiveWeekTeachers = if (teacherEdited) draftCourse.weekTeachers - sourceWeekNumber else draftCourse.weekTeachers
    val effectiveWeekLocations = if (locationEdited) draftCourse.weekLocations - sourceWeekNumber else draftCourse.weekLocations

    return draftCourse.copy(
        title = effectiveTitle,
        teacher = effectiveTeacher,
        location = effectiveLocation,
        weekTitles = effectiveWeekTitles,
        weekTeachers = effectiveWeekTeachers,
        weekLocations = effectiveWeekLocations,
    )
}

/** Save/cancel for the inline editor, so the dialog can pin them while the form scrolls. */
internal data class CourseEditActions(
    val canSave: Boolean,
    val applyWeekLabel: String?,
    val onCancel: () -> Unit,
    val onApplyWeek: (() -> Unit)?,
    val onSave: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseEditSection(
    course: CourseItem,
    existingCourses: List<CourseItem>,
    maxNodeCount: Int,
    maxWeekCount: Int,
    slotTimes: List<ClassSlotTime>,
    convertsToManual: Boolean,
    sourceWeekNumber: Int? = null,
    viewingCurrentWeek: Boolean = false,
    legacyColorArgb: Long? = null,
    onCancel: () -> Unit,
    onSave: (CourseItem) -> Unit,
    /** When set, the caller draws the buttons (pinned at the bottom) instead of the scrolling form. */
    actionsHost: ((CourseEditActions) -> Unit)? = null,
) {
    val context = LocalContext.current
    var draft by remember(course.id) { mutableStateOf<CourseItem?>(null) }
    val initialWeekTitle = remember(course, sourceWeekNumber) {
        sourceWeekNumber?.let { course.titleForWeek(it) } ?: course.title
    }
    val initialWeekTeacher = remember(course, sourceWeekNumber) {
        sourceWeekNumber?.let { course.teacherForWeek(it) } ?: course.teacher
    }
    val initialWeekLocation = remember(course, sourceWeekNumber) {
        sourceWeekNumber?.let { course.locationForWeek(it) } ?: course.location
    }
    val hasFieldEdits = draft != null && (
        draft?.title?.trim() != initialWeekTitle.trim() ||
        draft?.teacher?.trim() != initialWeekTeacher.trim() ||
        draft?.location?.trim() != initialWeekLocation.trim()
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.schedule_course_detail_edit_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (convertsToManual) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        text = stringResource(R.string.schedule_source_plugin),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                    )
                }
            }
        }

        if (convertsToManual) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.schedule_course_detail_converts_to_manual),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }

        CourseEditFormFields(
            initial = course,
            existingCourses = existingCourses,
            maxNodeCount = maxNodeCount,
            maxWeekCount = maxWeekCount,
            onDraftChange = { draft = it.singleOrNull() },
            modifier = Modifier.fillMaxWidth(),
            slotTimes = slotTimes,
            sourceWeekNumber = sourceWeekNumber,
            legacyColorArgb = legacyColorArgb,
        )

        if (hasFieldEdits && sourceWeekNumber != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(R.string.schedule_course_edit_apply_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val applyLabel = if (hasFieldEdits && sourceWeekNumber != null) {
            if (viewingCurrentWeek) {
                stringResource(R.string.schedule_course_edit_apply_this_week)
            } else {
                stringResource(R.string.schedule_course_edit_apply_week_n, sourceWeekNumber)
            }
        } else null
        val appliedFeedback = sourceWeekNumber?.let {
            stringResource(R.string.schedule_course_edit_applied_feedback, it)
        }.orEmpty()
        val onApplyWeek: (() -> Unit)? = if (applyLabel != null) {
            {
                draft?.let { currentDraft ->
                    onSave(applyDraftToTargetWeek(course, currentDraft, sourceWeekNumber!!))
                    Toast.makeText(context, appliedFeedback, Toast.LENGTH_SHORT).show()
                }
            }
        } else null
        val onSaveResolved: () -> Unit = {
            draft?.let { currentDraft ->
                onSave(
                    resolveSaveDraft(
                        baseCourse = course,
                        draftCourse = currentDraft,
                        sourceWeekNumber = sourceWeekNumber,
                        initialWeekTitle = initialWeekTitle,
                        initialWeekTeacher = initialWeekTeacher,
                        initialWeekLocation = initialWeekLocation,
                    ),
                )
            }
        }
        if (actionsHost != null) {
            actionsHost(
                CourseEditActions(
                    canSave = draft != null,
                    applyWeekLabel = applyLabel,
                    onCancel = onCancel,
                    onApplyWeek = onApplyWeek,
                    onSave = onSaveResolved,
                ),
            )
        } else {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppOutlinedButton(onClick = onCancel, shape = CircleShape, modifier = Modifier.heightIn(min = 46.dp)) {
                Text(stringResource(R.string.schedule_action_cancel))
            }
            if (applyLabel != null && onApplyWeek != null) {
                AppOutlinedButton(
                    onClick = { onApplyWeek?.invoke() },
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 46.dp).testTag("course-edit-apply-this-week"),
                ) {
                    Text(applyLabel, maxLines = 1, softWrap = false)
                }
            }
            Button(
                onClick = onSaveResolved,
                enabled = draft != null,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                modifier = Modifier.heightIn(min = 46.dp).testTag("course-edit-save"),
            ) {
                Text(stringResource(R.string.schedule_action_save), maxLines = 1, softWrap = false)
            }
        }
        }
    }
}

/** One grouped card: time, weeks, place, teacher and extra fields read together as a labelled list. */
@Composable
private fun CourseFactsCard(
    classTimeText: String,
    weeksText: String,
    examCountdown: ExamCountdown?,
    location: String,
    defaultLocation: String,
    locationOverridden: Boolean,
    teacher: String,
    defaultTeacher: String,
    teacherOverridden: Boolean,
    details: List<CourseDetailField>,
    manual: Boolean,
    pluginOverride: Boolean,
    sourceName: String? = null,
    isExam: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val unset = stringResource(R.string.schedule_metadata_unset)
    val sourceText = if (!manual && !pluginOverride && !sourceName.isNullOrBlank()) {
        sourceName
    } else {
        stringResource(
            when {
                pluginOverride -> R.string.schedule_source_plugin_edited
                manual -> R.string.schedule_source_manual
                else -> R.string.schedule_source_plugin
            },
        )
    }
    val seat = if (isExam) {
        details.firstOrNull { it.label.contains("座") || it.label.contains("Seat", ignoreCase = true) }
    } else null
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isExam) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = scheme.errorContainer,
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.School,
                                contentDescription = null,
                                tint = scheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.schedule_course_detail_exam_prefix),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onErrorContainer,
                            )
                            examCountdown?.let { cd ->
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = examCountdownText(cd),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = scheme.error,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        if (seat != null && seat.value.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.schedule_exam_seat_line, seat.value),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onErrorContainer,
                                modifier = Modifier.testTag("course-exam-seat"),
                            )
                        }
                        Text(
                            text = stringResource(R.string.schedule_exam_bring_items),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onErrorContainer.copy(alpha = 0.85f),
                        )
                    }
                }
            }
            DetailRow(
                icon = Icons.Rounded.AccessTime,
                title = stringResource(R.string.schedule_course_detail_class_time),
                body = classTimeText,
            )
            DetailRow(
                icon = Icons.Rounded.CalendarMonth,
                title = stringResource(R.string.schedule_course_detail_weeks),
                body = weeksText,
            )
            examCountdown?.let { countdown ->
                DetailRow(
                    icon = Icons.Rounded.AccessTime,
                    title = stringResource(R.string.schedule_course_detail_countdown),
                    body = stringResource(
                        R.string.schedule_course_detail_countdown_body,
                        countdown.date.monthValue,
                        countdown.date.dayOfMonth,
                        examCountdownText(countdown),
                    ),
                )
            }
            DetailRow(
                icon = Icons.Rounded.LocationOn,
                title = stringResource(R.string.schedule_course_detail_location),
                body = location.ifBlank { unset },
                badge = if (locationOverridden) stringResource(R.string.schedule_course_override_badge) else null,
                defaultHint = if (locationOverridden) {
                    stringResource(R.string.schedule_course_override_default, defaultLocation.ifBlank { unset })
                } else null,
            )
            DetailRow(
                icon = Icons.Rounded.Person,
                title = stringResource(R.string.schedule_course_detail_teacher),
                body = teacher.ifBlank { unset },
                badge = if (teacherOverridden) stringResource(R.string.schedule_course_override_badge) else null,
                defaultHint = if (teacherOverridden) {
                    stringResource(R.string.schedule_course_override_default, defaultTeacher.ifBlank { unset })
                } else null,
            )
            details.filter { it !== seat }.forEach { field ->
                val isSourceTerm = field.label in setOf("来源学期", "Source Term", "來源學期")
                DetailRow(
                    icon = if (isSourceTerm) Icons.Rounded.School else Icons.Rounded.Info,
                    title = if (isSourceTerm) stringResource(R.string.schedule_course_detail_source_term) else field.label,
                    body = field.value,
                )
            }
            HorizontalDivider(color = scheme.outlineVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Source,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(R.string.schedule_course_detail_source) + " · " + sourceText,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Separate tiles, one per operation, adapting to what the course allows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseActionBar(
    temporarilyCancelled: Boolean,
    showTemporaryCancel: Boolean,
    showDelete: Boolean,
    showRestorePlugin: Boolean,
    showColor: Boolean,
    hasReminder: Boolean,
    onEdit: () -> Unit,
    onColor: () -> Unit,
    onSetReminder: () -> Unit,
    onCancelReminder: () -> Unit,
    onTemporaryCancel: () -> Unit,
    onRestoreTemporaryCancel: () -> Unit,
    onDelete: () -> Unit,
    onRestorePlugin: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DetailAction(
            icon = Icons.Rounded.Edit,
            label = stringResource(R.string.schedule_action_edit),
            onClick = onEdit,
        )
        if (showColor) {
            DetailAction(
                icon = Icons.Rounded.Palette,
                label = stringResource(R.string.schedule_action_color),
                onClick = onColor,
            )
        }
        // Existing reminders expose removal rather than another creation action.
        DetailAction(
            icon = if (hasReminder) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive,
            label = stringResource(
                if (hasReminder) R.string.schedule_action_cancel_reminder else R.string.schedule_action_reminder,
            ),
            onClick = if (hasReminder) onCancelReminder else onSetReminder,
        )
        if (showTemporaryCancel) {
            DetailAction(
                icon = if (temporarilyCancelled) Icons.Rounded.Restore else Icons.Rounded.Close,
                label = stringResource(
                    if (temporarilyCancelled) {
                        R.string.schedule_course_detail_restore_cancel_short
                    } else {
                        R.string.schedule_course_detail_temp_cancel
                    },
                ),
                onClick = if (temporarilyCancelled) onRestoreTemporaryCancel else onTemporaryCancel,
            )
        }
        if (showRestorePlugin) {
            DetailAction(
                icon = Icons.Rounded.SettingsBackupRestore,
                label = stringResource(R.string.schedule_action_restore_plugin),
                onClick = onRestorePlugin,
            )
        }
        if (showDelete) {
            DetailAction(
                icon = Icons.Rounded.DeleteOutline,
                label = stringResource(R.string.schedule_action_delete),
                onClick = onDelete,
                danger = true,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowScope.DetailAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (danger) scheme.error else scheme.onSurface
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .weight(1f)
            .widthIn(min = 56.dp)
            .heightIn(min = 48.dp)
            .fillMaxRowHeight()
            .clip(shape)
            .background(if (danger) scheme.errorContainer else scheme.surface)
            .border(1.dp, if (danger) scheme.error.copy(alpha = 0.3f) else scheme.outlineVariant, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (danger) scheme.onErrorContainer else scheme.primary,
            modifier = Modifier.size(18.dp),
        )
        // Two lines at most: long labels ("Cancel once") wrap at large font sizes instead of being cut off.
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
            color = if (danger) scheme.onErrorContainer else tint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CourseNoteSection(
    courseKey: String,
    savedNote: String,
    maxLength: Int,
    onSave: (String) -> Unit,
    readOnly: Boolean = false,
) {
    var editing by remember(courseKey) { mutableStateOf(false) }
    var draft by remember(courseKey, savedNote) { mutableStateOf(savedNote) }
    val draftLength = courseNoteLength(draft)
    val tooLong = draftLength > maxLength
    val scheme = MaterialTheme.colorScheme
    val hasNote = savedNote.isNotBlank()
    // A saved note reads as an amber sticky note; an empty one stays neutral.
    val container = if (hasNote && !editing) scheme.tertiaryContainer.copy(alpha = 0.55f) else scheme.surface
    val accent = if (hasNote && !editing) scheme.tertiary else scheme.primary

    Surface(
        modifier = Modifier.fillMaxWidth().testTag("course-note-card"),
        shape = RoundedCornerShape(18.dp),
        color = container,
        border = BorderStroke(1.dp, if (hasNote && !editing) scheme.tertiary.copy(alpha = 0.35f) else scheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 12.dp).padding(end = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.StickyNote2,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.schedule_note_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (hasNote && !editing) scheme.onTertiaryContainer else scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (!readOnly && !editing) {
                    TextButton(
                        onClick = { editing = true },
                        modifier = Modifier.heightIn(min = 40.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    ) {
                        Text(
                            stringResource(if (hasNote) R.string.schedule_action_edit else R.string.schedule_action_add),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            if (!readOnly && editing) {
                AppOutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp),
                    placeholder = { Text(stringResource(R.string.schedule_note_hint)) },
                    isError = tooLong,
                    supportingText = {
                        Text(
                            text = if (tooLong) {
                                stringResource(R.string.schedule_note_over_limit, draftLength - maxLength, maxLength)
                            } else {
                                "$draftLength / $maxLength"
                            },
                        )
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasNote) {
                        AppOutlinedButton(
                            onClick = {
                                draft = ""
                                onSave("")
                                editing = false
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(stringResource(R.string.schedule_action_clear), maxLines = 1, softWrap = false)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    AppOutlinedButton(
                        onClick = {
                            draft = savedNote
                            editing = false
                        },
                        shape = CircleShape,
                    ) {
                        Text(stringResource(R.string.schedule_action_cancel))
                    }
                    Button(
                        onClick = {
                            when (val result = validateCourseNote(draft, maxLength)) {
                                is CourseNoteInput.Accepted -> {
                                    draft = result.text
                                    onSave(result.text)
                                    editing = false
                                }

                                is CourseNoteInput.TooLong -> Unit
                            }
                        },
                        enabled = !tooLong,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(stringResource(R.string.schedule_action_save), maxLines = 1, softWrap = false)
                    }
                }
            } else {
                Text(
                    text = savedNote.ifBlank { stringResource(R.string.schedule_note_empty) },
                    modifier = Modifier.padding(end = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasNote) scheme.onTertiaryContainer else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExamReminderMuteRow(
    enabled: Boolean,
    muted: Boolean,
    onMute: () -> Unit,
    onRestore: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (enabled && !muted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.schedule_exam_mute_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when {
                        !enabled -> stringResource(R.string.schedule_exam_mute_disabled)
                        muted -> stringResource(R.string.schedule_exam_mute_muted)
                        else -> stringResource(R.string.schedule_exam_mute_active)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (enabled) {
                AppOutlinedButton(
                    onClick = if (muted) onRestore else onMute,
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 40.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(if (muted) R.string.schedule_action_restore else R.string.schedule_exam_mute_this), maxLines = 1)
                }
            }
        }
    }
}

/** The status badge describes the viewed week, not always the current real week. */
@Composable
private fun StatusChip(
    activeInVisibleWeek: Boolean?,
    visibleWeekNumber: Int?,
    viewingCurrentWeek: Boolean,
    manual: Boolean,
    headerContent: Color,
    // Resolved holiday or cancellation status takes priority over active-week labeling.
    notOccurringLabel: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val neutral = scheme.surface.copy(alpha = 0.62f)
    val label: String
    val container: Color
    val content: Color
    var active = false
    when {
        notOccurringLabel != null -> { label = notOccurringLabel; container = neutral; content = headerContent }
        activeInVisibleWeek == null -> {
            label = stringResource(R.string.schedule_status_week_unknown); container = neutral; content = headerContent
        }
        !activeInVisibleWeek -> {
            label = if (viewingCurrentWeek || visibleWeekNumber == null) {
                stringResource(R.string.schedule_status_other_week)
            } else {
                stringResource(R.string.schedule_status_week_inactive, visibleWeekNumber)
            }
            container = neutral; content = headerContent
        }
        manual -> {
            label = stringResource(R.string.schedule_status_manual)
            container = scheme.tertiaryContainer; content = scheme.onTertiaryContainer
        }
        else -> {
            label = if (viewingCurrentWeek || visibleWeekNumber == null) {
                stringResource(R.string.schedule_status_this_week)
            } else {
                stringResource(R.string.schedule_status_week_active, visibleWeekNumber)
            }
            container = scheme.primary; content = scheme.onPrimary; active = true
        }
    }
    HeaderPill(text = label, content = content, containerOverride = container, icon = if (active) Icons.Rounded.Check else null)
}

/** Course name in the header: 18sp keeps one or two lines readable without dominating the card. */
@Composable
private fun CourseTitle(text: String, color: Color, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A compact pill in the course header; tinted by the header's own colours so it works in every theme. */
/** Session count pill: the label is regular weight and the "n/total" figure is large and heavy so it reads at a glance. */
@Composable
private fun SessionProgressPill(
    label: String,
    current: Int,
    total: Int,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 24.dp).padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                maxLines = 1,
            )
            Text(
                text = "$current/$total",
                style = MaterialTheme.typography.titleSmall,
                color = content,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HeaderPill(
    text: String,
    content: Color,
    modifier: Modifier = Modifier,
    containerOverride: Color? = null,
    icon: ImageVector? = null,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerOverride ?: MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 24.dp).padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(13.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    title: String,
    body: String,
    badge: String? = null,
    defaultHint: String? = null,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(18.dp),
        )
        // Minimum label width aligns values without truncating longer labels.
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 1.dp)
                .widthIn(min = 56.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            if (defaultHint != null) {
                Text(
                    text = defaultHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun examCountdownText(countdown: ExamCountdown): String = when (countdown.daysRemaining) {
    0L -> stringResource(R.string.schedule_exam_countdown_today)
    1L -> stringResource(R.string.schedule_exam_countdown_tomorrow)
    else -> pluralStringResource(
        R.plurals.schedule_exam_countdown_days,
        countdown.daysRemaining.toInt(),
        countdown.daysRemaining,
    )
}

/** Resolved timing ranges fall back to slot or period labels. */
internal sealed interface ClassTimeInfo {
    data class Range(val startTime: String, val endTime: String) : ClassTimeInfo
    data class MajorPeriod(val index: Int) : ClassTimeInfo
    data class MajorPeriodRange(val start: Int, val end: Int) : ClassTimeInfo
    data class NodeRange(val start: Int, val end: Int) : ClassTimeInfo
}

private fun resolveClassTime(course: CourseItem, timingProfile: TermTimingProfile?): ClassTimeInfo {
    course.typedClock()?.let { (start, end) -> return ClassTimeInfo.Range(start.toString(), end.toString()) }
    val slots: List<ClassSlotTime> = timingProfile?.slotTimes.orEmpty().sortedBy { it.startNode }
    val matchStart = slots.firstOrNull { course.time.startNode in it.startNode..it.endNode }
    val matchEnd = slots.firstOrNull { course.time.endNode in it.startNode..it.endNode }
    if (matchStart != null && matchEnd != null) {
        return ClassTimeInfo.Range(matchStart.startTime, matchEnd.endTime)
    }
    val baseCount = slots.size
    val extraStart = course.time.startNode - (slots.lastOrNull()?.endNode ?: 0)
    val extraEnd = course.time.endNode - (slots.lastOrNull()?.endNode ?: 0)
    return if (extraStart >= 1 && extraEnd >= 1) {
        if (extraStart == extraEnd) ClassTimeInfo.MajorPeriod(baseCount + extraStart)
        else ClassTimeInfo.MajorPeriodRange(baseCount + extraStart, baseCount + extraEnd)
    } else {
        ClassTimeInfo.NodeRange(course.time.startNode, course.time.endNode)
    }
}

internal fun Context.classTimeText(info: ClassTimeInfo): String = when (info) {
    is ClassTimeInfo.Range -> getString(R.string.schedule_class_time_range, info.startTime, info.endTime)
    is ClassTimeInfo.MajorPeriod -> getString(R.string.schedule_major_period_single, info.index)
    is ClassTimeInfo.MajorPeriodRange -> getString(R.string.schedule_major_period_range, info.start, info.end)
    is ClassTimeInfo.NodeRange -> getString(R.string.schedule_node_range, info.start, info.end)
}

internal sealed interface WeeksDetail {
    object Unspecified : WeeksDetail
    data class Consecutive(val first: Int, val last: Int, val count: Int, val weeksList: String) : WeeksDetail
    data class Odd(val first: Int, val last: Int, val count: Int, val weeksList: String) : WeeksDetail
    data class Even(val first: Int, val last: Int, val count: Int, val weeksList: String) : WeeksDetail
    data class Count(val count: Int, val weeksList: String) : WeeksDetail
}

internal fun describeWeeksDetail(weeks: List<Int>): WeeksDetail {
    if (weeks.isEmpty()) return WeeksDetail.Unspecified
    val sorted = weeks.sorted().distinct()
    val first = sorted.first()
    val last = sorted.last()
    val full = (first..last).toList()
    val odd = full.filter { it % 2 == 1 }
    val even = full.filter { it % 2 == 0 }
    val list = sorted.joinToString(", ")
    return when {
        sorted == full -> WeeksDetail.Consecutive(first, last, sorted.size, list)
        sorted == odd -> WeeksDetail.Odd(first, last, sorted.size, list)
        sorted == even -> WeeksDetail.Even(first, last, sorted.size, list)
        else -> WeeksDetail.Count(sorted.size, list)
    }
}

internal fun Context.weeksDetailText(detail: WeeksDetail): String = when (detail) {
    WeeksDetail.Unspecified -> getString(R.string.schedule_weeks_detail_unspecified)
    is WeeksDetail.Consecutive ->
        resources.getQuantityString(
            R.plurals.schedule_weeks_detail_consecutive,
            detail.count,
            detail.first,
            detail.last,
            detail.count,
        )
    is WeeksDetail.Odd ->
        getString(R.string.schedule_weeks_detail_odd, detail.first, detail.last, detail.count)
    is WeeksDetail.Even ->
        getString(R.string.schedule_weeks_detail_even, detail.first, detail.last, detail.count)
    is WeeksDetail.Count ->
        resources.getQuantityString(R.plurals.schedule_weeks_detail_count, detail.count, detail.count) +
            "\n" + detail.weeksList
}

/** Course colour, border and gradient, using the same appearance editor as the course form. */
@Composable
private fun CourseColorSheet(
    course: CourseItem,
    palette: List<CoursePaletteEntry>,
    onChange: (ItemAppearance) -> Unit,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("course-color-sheet"),
        icon = { Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(22.dp)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                ItemAppearanceSection(
                    appearance = course.appearance(),
                    onChange = onChange,
                    autoColor = courseColor(course.title, palette).container,
                    previewTitle = course.title,
                )
            }
        },
        confirmButton = {
            AppDialogPrimaryButton(onClick = onDismiss, shape = CircleShape) {
                Text(stringResource(R.string.schedule_action_close))
            }
        },
    )
}
