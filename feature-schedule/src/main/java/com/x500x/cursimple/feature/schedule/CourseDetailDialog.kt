package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.DEFAULT_TERM_WEEK_COUNT
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight

@OptIn(ExperimentalLayoutApi::class)

/** Page through overlapping courses individually with position indicators. */
@Composable
private fun SameSlotPager(courses: List<CourseItem>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AppCircleIconButton(onClick = { onSelect((selectedIndex - 1 + courses.size) % courses.size) }) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.schedule_same_slot_prev))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.schedule_course_detail_same_slot, courses.size), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppAtomicText(stringResource(R.string.schedule_same_slot_position, selectedIndex + 1, courses.size),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        AppCircleIconButton(onClick = { onSelect((selectedIndex + 1) % courses.size) }) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.schedule_same_slot_next))
        }
    }
}

@Composable
fun CourseDetailDialog(
    courses: List<CourseItem>,
    timingProfile: TermTimingProfile?,
    visibleWeekNumber: Int?,
    /** Use the current-week label only when the viewed week is actually current. */
    currentWeekNumber: Int? = null,
    isManual: (CourseItem) -> Boolean,
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
    var confirmRestore by remember(course.id) { mutableStateOf(false) }
    val accents = com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents.current
    val palette = remember(course.title, course.category, accents) { courseColor(course.title, accents.coursePalette) }
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
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.schedule_course_detail_restore_plugin_title)) },
            text = { Text(stringResource(R.string.schedule_course_detail_restore_plugin_body, course.title)) },
            confirmButton = {
                AppOutlinedButton(onClick = {
                    confirmRestore = false
                    onRestorePluginCourse(course)
                }) { Text(stringResource(R.string.schedule_action_restore_plugin)) }
            },
            dismissButton = {
                AppOutlinedButton(onClick = { confirmRestore = false }) {
                    Text(stringResource(R.string.schedule_action_cancel))
                }
            },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
            Surface(
                // Wrap short content instead of always filling a fixed height.
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = minOf(maxHeight, 640.dp))
                    .pointerInput(Unit) { detectTapGestures {} }.testTag("course-detail-card"),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(headerContainer)
                            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 14.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = course.titleForWeek(sourceWeekNumber),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = headerContent,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                StatusChip(
                                    activeInVisibleWeek = isThisWeek,
                                    visibleWeekNumber = visibleWeekNumber,
                                    viewingCurrentWeek = visibleWeekNumber != null &&
                                        visibleWeekNumber == currentWeekNumber,
                                    manual = manual,
                                    // Whole-day holiday state takes priority over per-course cancellation.
                                    notOccurringLabel = when {
                                        dayIsHoliday -> stringResource(R.string.schedule_status_on_holiday)
                                        targetDate != null && isTemporarilyCancelled(course) ->
                                            stringResource(R.string.schedule_status_cancelled)
                                        else -> null
                                    },
                                )
                                AppCircleIconButton(onClick = onDismiss) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.schedule_action_close),
                                        tint = headerContent,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            val titleOverridden = sourceWeekNumber != null &&
                                course.titleForWeek(sourceWeekNumber).trim() != course.title.trim()
                            if (titleOverridden) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(bottom = 2.dp),
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = headerContent.copy(alpha = 0.2f),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.schedule_course_override_badge),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = headerContent,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.schedule_course_override_default, course.title),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = headerContent.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (sourceTerm != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = headerContent.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(bottom = 2.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.School,
                                            contentDescription = null,
                                            tint = headerContent,
                                            modifier = Modifier.size(12.dp),
                                        )
                                        Text(
                                            text = sourceTerm,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = headerContent,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            Text(
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
                                style = MaterialTheme.typography.bodySmall,
                                color = headerContent.copy(alpha = 0.85f),
                            )
                            Text(
                                text = when {
                                    progress == null -> stringResource(R.string.schedule_course_session_needs_start)
                                    progress.current == null -> stringResource(R.string.schedule_course_session_not_scheduled, progress.total)
                                    else -> stringResource(R.string.schedule_course_session_progress, progress.current, progress.total)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = headerContent.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("course-session-progress"),
                            )
                        }
                    }

                    if (courses.size > 1) {
                        SameSlotPager(
                            courses = courses,
                            selectedIndex = selectedIndex,
                            onSelect = {
                                selectedIndex = it
                                editing = false
                            },
                        )
                        val otherCourse = courses.firstOrNull { it.id != course.id }
                        val overlappingWeeks = remember(course.weeks, otherCourse?.weeks) {
                            if (otherCourse == null) emptySet()
                            else course.weeks.toSet().intersect(otherCourse.weeks.toSet())
                        }
                        if (overlappingWeeks.isNotEmpty() && !readOnly) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp)
                                    .testTag("course-conflict-resolution-card"),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
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
                                            AppAtomicText(
                                                text = stringResource(R.string.schedule_conflict_odd_even_split),
                                                style = MaterialTheme.typography.labelSmall,
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
                                            AppAtomicText(
                                                text = stringResource(R.string.schedule_conflict_half_term_split),
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    Column(
                        // fill = false lets short details wrap instead of stretching to the height cap.
                        modifier = Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(androidx.compose.runtime.key(course.id) { rememberScrollState() })
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
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
                                onCancel = { editing = false },
                                onSave = {
                                    onSaveCourse(it)
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
                                )
                            }

                            CourseNoteSection(
                                courseKey = course.id,
                                savedNote = noteTextOf(course),
                                maxLength = noteMaxLength,
                                onSave = { text -> onSaveNote(course, text) },
                                readOnly = readOnly,
                            )

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
                    if (!readOnly && !editing) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).testTag("course-detail-actions")) {
                            CourseActionBar(
                                temporarilyCancelled = targetDate != null && isTemporarilyCancelled(course),
                                showTemporaryCancel = targetDate != null,
                                showDelete = manual && !pluginOverride,
                                showRestorePlugin = pluginOverride,
                                onEdit = { editing = true },
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
    onCancel: () -> Unit,
    onSave: (CourseItem) -> Unit,
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
        Text(
            text = stringResource(R.string.schedule_course_detail_edit_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )

        if (convertsToManual) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.schedule_course_detail_converts_to_manual),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        CourseEditFormFields(
            initial = course,
            existingCourses = existingCourses,
            maxNodeCount = maxNodeCount,
            maxWeekCount = maxWeekCount,
            onDraftChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            slotTimes = slotTimes,
            sourceWeekNumber = sourceWeekNumber,
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

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppOutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.schedule_action_cancel))
            }
            if (hasFieldEdits && sourceWeekNumber != null) {
                val applyLabel = if (viewingCurrentWeek) {
                    stringResource(R.string.schedule_course_edit_apply_this_week)
                } else {
                    stringResource(R.string.schedule_course_edit_apply_week_n, sourceWeekNumber)
                }
                AppOutlinedButton(
                    onClick = {
                        val currentDraft = draft ?: return@AppOutlinedButton
                        val weekCourse = applyDraftToTargetWeek(course, currentDraft, sourceWeekNumber)
                        Toast.makeText(
                            context,
                            context.getString(R.string.schedule_course_edit_applied_feedback, sourceWeekNumber),
                            Toast.LENGTH_SHORT,
                        ).show()
                        onSave(weekCourse)
                    },
                    modifier = Modifier.testTag("course-edit-apply-this-week"),
                ) {
                    Text(applyLabel, maxLines = 1, softWrap = false)
                }
            }
            Button(
                onClick = {
                    val currentDraft = draft ?: return@Button
                    val resolvedCourse = resolveSaveDraft(
                        baseCourse = course,
                        draftCourse = currentDraft,
                        sourceWeekNumber = sourceWeekNumber,
                        initialWeekTitle = initialWeekTitle,
                        initialWeekTeacher = initialWeekTeacher,
                        initialWeekLocation = initialWeekLocation,
                    )
                    onSave(resolvedCourse)
                },
                enabled = draft != null,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.testTag("course-edit-save"),
            ) {
                Text(stringResource(R.string.schedule_action_save), maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Compact label/value rows keep multiple-course details readable. */
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
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
                body = location.ifBlank { stringResource(R.string.schedule_metadata_unset) },
                badge = if (locationOverridden) stringResource(R.string.schedule_course_override_badge) else null,
                defaultHint = if (locationOverridden) {
                    stringResource(
                        R.string.schedule_course_override_default,
                        defaultLocation.ifBlank { stringResource(R.string.schedule_metadata_unset) },
                    )
                } else null,
            )
            DetailRow(
                icon = Icons.Rounded.Person,
                title = stringResource(R.string.schedule_course_detail_teacher),
                body = teacher.ifBlank { stringResource(R.string.schedule_metadata_unset) },
                badge = if (teacherOverridden) stringResource(R.string.schedule_course_override_badge) else null,
                defaultHint = if (teacherOverridden) {
                    stringResource(
                        R.string.schedule_course_override_default,
                        defaultTeacher.ifBlank { stringResource(R.string.schedule_metadata_unset) },
                    )
                } else null,
            )
            details.forEach { field ->
                val isSourceTerm = field.label in setOf("来源学期", "Source Term", "來源學期")
                DetailRow(
                    icon = if (isSourceTerm) Icons.Rounded.School else Icons.Rounded.Info,
                    title = if (isSourceTerm) stringResource(R.string.schedule_course_detail_source_term) else field.label,
                    body = field.value,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Source,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(R.string.schedule_course_detail_source) + " · " +
                        stringResource(
                            when {
                                pluginOverride -> R.string.schedule_source_plugin_edited
                                manual -> R.string.schedule_source_manual
                                else -> R.string.schedule_source_plugin
                            },
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Equal-width actions adapt to the course's available operations. */
@Composable
private fun CourseActionBar(
    temporarilyCancelled: Boolean,
    showTemporaryCancel: Boolean,
    showDelete: Boolean,
    showRestorePlugin: Boolean,
    hasReminder: Boolean,
    onEdit: () -> Unit,
    onSetReminder: () -> Unit,
    onCancelReminder: () -> Unit,
    onTemporaryCancel: () -> Unit,
    onRestoreTemporaryCancel: () -> Unit,
    onDelete: () -> Unit,
    onRestorePlugin: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            DetailAction(
                icon = Icons.Rounded.Edit,
                label = stringResource(R.string.schedule_action_edit),
                onClick = onEdit,
            )
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
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RowScope.DetailAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        AppAtomicText(text = label, modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall.copy(textAlign = TextAlign.Center), color = tint)
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

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.schedule_note_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (!readOnly && !editing) {
                AppOutlinedButton(
                    onClick = { editing = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    Text(stringResource(if (savedNote.isBlank()) R.string.schedule_action_add else R.string.schedule_action_edit))
                }
            }
        }

        if (!readOnly && editing) {
            OutlinedTextField(
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
            ) {
                if (savedNote.isNotBlank()) {
                    AppOutlinedButton(
                        onClick = {
                            draft = ""
                            onSave("")
                            editing = false
                        },
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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.schedule_action_save), maxLines = 1, softWrap = false)
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = savedNote.ifBlank { stringResource(R.string.schedule_note_empty) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (savedNote.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.schedule_exam_mute_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
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
                AppOutlinedButton(onClick = if (muted) onRestore else onMute) {
                    Text(stringResource(if (muted) R.string.schedule_action_restore else R.string.schedule_exam_mute_this))
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
    // Resolved holiday or cancellation status takes priority over active-week labeling.
    notOccurringLabel: String? = null,
) {
    val (label, container, content) = when {
        notOccurringLabel != null -> Triple(
            notOccurringLabel,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        activeInVisibleWeek == null -> Triple(
            stringResource(R.string.schedule_status_week_unknown),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        !activeInVisibleWeek -> Triple(
            if (viewingCurrentWeek || visibleWeekNumber == null) {
                stringResource(R.string.schedule_status_other_week)
            } else {
                stringResource(R.string.schedule_status_week_inactive, visibleWeekNumber)
            },
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        manual -> Triple(
            stringResource(R.string.schedule_status_manual),
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        else -> Triple(
            if (viewingCurrentWeek || visibleWeekNumber == null) {
                stringResource(R.string.schedule_status_this_week)
            } else {
                stringResource(R.string.schedule_status_week_active, visibleWeekNumber)
            },
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = container,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp),
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
                    fontWeight = FontWeight.Medium,
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

private fun describeWeeksDetail(weeks: List<Int>): WeeksDetail {
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
