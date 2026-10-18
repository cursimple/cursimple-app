package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.DEFAULT_TERM_WEEK_COUNT
import com.x500x.cursimple.feature.plugin.ui.AppAssistChip
import com.x500x.cursimple.feature.plugin.ui.AppFilterChip
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import com.x500x.cursimple.feature.schedule.time.today
import java.time.LocalDate
import java.util.UUID

/**
 * Quick-add fixes the tapped day and defaults to its periods; typed times can replace them.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddCourseDialog(
    dayOfWeek: Int,
    startNode: Int,
    endNode: Int,
    existingCourses: List<CourseItem> = emptyList(),
    maxWeekCount: Int = DEFAULT_TERM_WEEK_COUNT,
    slotLabel: String? = null,
    slotTimes: List<ClassSlotTime> = emptyList(),
    defaultDate: LocalDate? = null,
    termStartDate: LocalDate? = null,
    onDismiss: () -> Unit,
    onConfirm: (CourseItem) -> Unit,
    onConfirmEvent: ((ScheduleEvent) -> Unit)? = null,
) {
    var scheduleType by rememberSaveable { mutableStateOf(ScheduleItemType.Course) }
    val isEventMode = scheduleType == ScheduleItemType.Event && onConfirmEvent != null

    val reuse = rememberCourseReuseValues(existingCourses)
    var title by rememberSaveable { mutableStateOf("") }
    var weekTitlesRaw by rememberSaveable { mutableStateOf("") }
    var pickingWeekTitles by rememberSaveable { mutableStateOf(false) }
    var location by rememberSaveable { mutableStateOf("") }
    var weekLocationsRaw by rememberSaveable { mutableStateOf("") }
    var pickingWeekLocations by rememberSaveable { mutableStateOf(false) }
    var teacher by rememberSaveable { mutableStateOf("") }
    var weekTeachersRaw by rememberSaveable { mutableStateOf("") }
    var pickingWeekTeachers by rememberSaveable { mutableStateOf(false) }
    // Leave new-course weeks unset rather than assuming current-to-term-end coverage.
    var startWeekText by rememberSaveable { mutableStateOf("") }
    var endWeekText by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(CourseCategory.Course) }
    var byClock by rememberSaveable { mutableStateOf(false) }
    val tappedClock = remember(slotTimes, startNode, endNode) { clockForNodes(slotTimes, startNode, endNode) }
    var clockStart by rememberSaveable { mutableIntStateOf(tappedClock?.first ?: DEFAULT_CLOCK_START) }
    var clockEnd by rememberSaveable { mutableIntStateOf(tappedClock?.second ?: (DEFAULT_CLOCK_START + DEFAULT_CLOCK_LENGTH)) }
    // Typed times move the course to the periods they overlap; the tapped cell is only the default.
    val clockValid = clockEnd > clockStart
    val nodes = (if (byClock && clockValid) nodesForClock(slotTimes, clockStart, clockEnd) else null) ?: (startNode to endNode)

    val titleTrimmed = title.trim()
    val weekTitlesMap = remember(weekTitlesRaw) { decodeWeekLocations(weekTitlesRaw) }
    val weekLocationsMap = remember(weekLocationsRaw) { decodeWeekLocations(weekLocationsRaw) }
    val weekTeachersMap = remember(weekTeachersRaw) { decodeWeekLocations(weekTeachersRaw) }
    val startWeek = startWeekText.toIntOrNull()
    val endWeek = endWeekText.toIntOrNull()
    val weeksValid = startWeek != null && endWeek != null &&
        startWeek in 1..maxWeekCount && endWeek in startWeek..maxWeekCount
    val canSave = titleTrimmed.isNotBlank() && weeksValid && (!byClock || clockValid)
    val conflictWarning = remember(
        existingCourses, dayOfWeek, nodes, startWeek, endWeek, weeksValid, category, maxWeekCount,
    ) {
        addCourseConflictWarning(
            draftCourseConflicts(
                existingCourses = existingCourses,
                dayOfWeek = dayOfWeek,
                startNode = nodes.first,
                endNode = nodes.second,
                weeks = if (weeksValid) (startWeek!!..endWeek!!).toList() else null,
                category = category,
                maxWeekCount = maxWeekCount,
            ),
        )
    }

    // Event state
    val zone = LocalAppZone.current
    val eventDateDefault = remember(defaultDate, dayOfWeek, zone) {
        if (defaultDate != null) {
            defaultDate
        } else {
            val currentDow = zone.today().dayOfWeek.value
            val daysToAdd = (dayOfWeek - currentDow).let { if (it < 0) it + 7 else it }
            zone.today().plusDays(daysToAdd.toLong())
        }
    }
    var eventDate by rememberSaveable { mutableStateOf(eventDateDefault) }
    var eventStart by rememberSaveable {
        mutableStateOf(tappedClock?.first?.let { minuteText(it) } ?: "08:00")
    }
    var eventEnd by rememberSaveable {
        mutableStateOf(tappedClock?.second?.let { minuteText(it) } ?: "09:35")
    }
    var eventNote by rememberSaveable { mutableStateOf("") }
    var eventRepeat by rememberSaveable { mutableStateOf(EventRepeat.Once) }
    var eventRepeatUntil by rememberSaveable { mutableStateOf<String?>(null) }
    val weekAnchor = remember { eventWeekAnchor(termStartDate, eventDateDefault) }
    var eventPickedWeeks by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var eventColorArgb by rememberSaveable { mutableStateOf<Long?>(null) }
    var eventSubmitted by remember { mutableStateOf(false) }

    val eventStartMinute = clockMinute(eventStart)
    val eventEndMinute = clockMinute(eventEnd)
    val eventTitleMissing = titleTrimmed.isBlank()
    val eventTimeInvalid = eventStartMinute == null || eventEndMinute == null || eventEndMinute <= eventStartMinute
    val eventWeeksMissing = eventRepeat == EventRepeat.Weeks && eventPickedWeeks.isEmpty()
    val canSaveEvent = !eventTitleMissing && !eventTimeInvalid && !eventWeeksMissing

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(
                        if (isEventMode) R.string.schedule_event_add_title
                        else if (scheduleType == ScheduleItemType.Exam) R.string.schedule_quick_add_exam_title
                        else R.string.schedule_quick_add_title
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                if (onConfirmEvent != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppFilterChip(
                            selected = scheduleType == ScheduleItemType.Course,
                            onClick = {
                                scheduleType = ScheduleItemType.Course
                                category = CourseCategory.Course
                            },
                            label = { Text(stringResource(R.string.schedule_category_course)) },
                            modifier = Modifier.testTag("quick-add-type-course"),
                        )
                        AppFilterChip(
                            selected = scheduleType == ScheduleItemType.Exam,
                            onClick = {
                                scheduleType = ScheduleItemType.Exam
                                category = CourseCategory.Exam
                            },
                            label = { Text(stringResource(R.string.schedule_category_exam)) },
                            modifier = Modifier.testTag("quick-add-type-exam"),
                        )
                        AppFilterChip(
                            selected = scheduleType == ScheduleItemType.Event,
                            onClick = { scheduleType = ScheduleItemType.Event },
                            label = { Text(stringResource(R.string.schedule_category_event)) },
                            modifier = Modifier.testTag("quick-add-type-event"),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Show fixed day and periods as read-only destination chips.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AppAssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(stringResource(scheduleWeekdayFullRes(dayOfWeek))) },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = if (isEventMode) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            disabledLabelColor = if (isEventMode) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                        ),
                    )
                    AppAssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            val slotText = if (byClock) "${formatMinuteOfDay(clockStart)}–${formatMinuteOfDay(clockEnd)}"
                            else slotLabel
                                ?: if (startNode == endNode) stringResource(R.string.schedule_node_single, startNode)
                                else stringResource(R.string.schedule_node_range, startNode, endNode)
                            Text(
                                if (isEventMode) stringResource(R.string.schedule_event_preset_from_slot, slotText)
                                else slotText
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = if (isEventMode) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            disabledLabelColor = if (isEventMode) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isEventMode) {
                    ScheduleEventEditForm(
                        title = title,
                        onTitleChange = { title = it },
                        date = eventDate,
                        onDateChange = { eventDate = it },
                        start = eventStart,
                        onStartChange = { eventStart = it },
                        end = eventEnd,
                        onEndChange = { eventEnd = it },
                        location = location,
                        onLocationChange = { location = it },
                        note = eventNote,
                        onNoteChange = { eventNote = it },
                        repeat = eventRepeat,
                        onRepeatChange = { eventRepeat = it },
                        repeatUntil = eventRepeatUntil,
                        onRepeatUntilChange = { eventRepeatUntil = it },
                        pickedWeeks = eventPickedWeeks,
                        onPickedWeeksChange = { eventPickedWeeks = it },
                        colorArgb = eventColorArgb,
                        onColorArgbChange = { eventColorArgb = it },
                        submitted = eventSubmitted,
                        weekAnchor = weekAnchor,
                        weekLimit = maxOf(maxWeekCount, eventPickedWeeks.maxOrNull() ?: 0),
                        locationOptions = remember(reuse.locations) { reuse.locations.map { CourseReuseOption(it) } },
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    )
                } else {
                    Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CourseTitleField(
                            value = title,
                            onChange = { title = it },
                            courses = reuse.courses,
                            onReuse = { course ->
                                title = course.title.trim()
                                teacher = course.teacher.trim()
                                location = course.location.trim()
                            },
                            weekTitles = weekTitlesMap,
                            onPickWeekTitles = { pickingWeekTitles = true },
                        )
                        CourseLocationField(
                            location = location,
                            onLocationChange = { location = it },
                            weekLocations = weekLocationsMap,
                            onPickWeekLocations = { pickingWeekLocations = true },
                            suggestions = reuse.locations,
                        )
                        CourseTeacherField(
                            teacher = teacher,
                            onTeacherChange = { teacher = it },
                            weekTeachers = weekTeachersMap,
                            onPickWeekTeachers = { pickingWeekTeachers = true },
                            suggestions = reuse.teachers,
                        )

                        if (onConfirmEvent == null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppAssistChip(
                                    onClick = {
                                        category = CourseCategory.Course
                                        scheduleType = ScheduleItemType.Course
                                    },
                                    label = { Text(stringResource(R.string.schedule_category_course)) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (category == CourseCategory.Course) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        labelColor = if (category == CourseCategory.Course) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    ),
                                )
                                AppAssistChip(
                                    onClick = {
                                        category = CourseCategory.Exam
                                        scheduleType = ScheduleItemType.Exam
                                    },
                                    label = { Text(stringResource(R.string.schedule_category_exam)) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (category == CourseCategory.Exam) {
                                            MaterialTheme.colorScheme.errorContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        labelColor = if (category == CourseCategory.Exam) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    ),
                                )
                            }
                        }

                        CourseTimeModeChips(byClock = byClock, onByClock = { byClock = it })
                        if (byClock) CourseClockFields(clockStart, clockEnd, onStartChange = { clockStart = it }, onEndChange = { clockEnd = it })

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = startWeekText,
                                onValueChange = { startWeekText = it.filter(Char::isDigit).take(2) },
                                label = { Text(stringResource(R.string.schedule_week_start_label)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = endWeekText,
                                onValueChange = { endWeekText = it.filter(Char::isDigit).take(2) },
                                label = { Text(stringResource(R.string.schedule_week_end_label)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                modifier = Modifier.weight(1f),
                            )
                        }

                        conflictWarning?.let { CourseConflictWarning(warning = it) }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = if (isEventMode) true else canSave,
                        onClick = {
                            if (isEventMode) {
                                eventSubmitted = true
                                if (!canSaveEvent) return@Button
                                val event = ScheduleEvent(
                                    id = "event-${UUID.randomUUID()}",
                                    title = titleTrimmed,
                                    date = eventDate.toString(),
                                    startTime = eventStart,
                                    endTime = eventEnd,
                                    location = location.trim(),
                                    note = eventNote.trim(),
                                    colorArgb = eventColorArgb,
                                ).withRepeat(eventRepeat, eventRepeatUntil, eventPickedWeeks, weekAnchor)
                                onConfirmEvent?.invoke(event)
                            } else {
                            val weeks = (startWeek!!..endWeek!!).toList()
                            val course = CourseItem(
                                id = "manual-" + UUID.randomUUID().toString().take(12),
                                title = titleTrimmed,
                                teacher = teacher.trim(),
                                weekTeachers = weekTeachersMap.filterKeys { it in weeks }
                                    .mapValues { it.value.trim() }.filterValues { it.isNotBlank() },
                                location = location.trim(),
                                // Retain nonblank locations and titles for selected weeks only.
                                weekLocations = weekLocationsMap
                                    .filterKeys { it in weeks }
                                    .mapValues { it.value.trim() }
                                    .filterValues { it.isNotBlank() },
                                weekTitles = weekTitlesMap
                                    .filterKeys { it in weeks }
                                    .mapValues { it.value.trim() }
                                    .filterValues { it.isNotBlank() },
                                weeks = weeks,
                                category = category,
                                time = CourseTimeSlot(
                                    dayOfWeek = dayOfWeek,
                                    startNode = nodes.first,
                                    endNode = nodes.second,
                                ),
                                reminderStartTime = formatMinuteOfDay(clockStart).takeIf { byClock },
                                reminderEndTime = formatMinuteOfDay(clockEnd).takeIf { byClock },
                            )
                            onConfirm(course)
                        }
                    },
                ) { Text(stringResource(R.string.schedule_action_save)) }
                }
            }
        }
    }

    if (pickingWeekTitles) {
        val startWeek = startWeekText.toIntOrNull()
        val endWeek = endWeekText.toIntOrNull()
        WeekTitleDialog(
            weeks = if (startWeek != null && endWeek != null && startWeek in 1..endWeek) {
                (startWeek..endWeek).toList()
            } else {
                (1..maxWeekCount).toList()
            },
            courseTitle = title.trim(),
            baseTitle = title.trim(),
            weekTitles = weekTitlesMap,
            suggestions = reuse.courses.map { it.title }.distinct(),
            onConfirm = { values ->
                weekTitlesRaw = encodeWeekLocations(values)
                pickingWeekTitles = false
            },
            onDismiss = { pickingWeekTitles = false },
        )
    }

    if (pickingWeekTeachers) {
        WeekTeacherDialog(
            weeks = if (weeksValid) (startWeek!!..endWeek!!).toList() else (1..maxWeekCount).toList(),
            courseTitle = title.trim(),
            baseTeacher = teacher.trim(),
            weekTeachers = weekTeachersMap,
            suggestions = reuse.teachers,
            onConfirm = { values ->
                weekTeachersRaw = encodeWeekLocations(values)
                pickingWeekTeachers = false
            },
            onDismiss = { pickingWeekTeachers = false },
        )
    }

    if (pickingWeekLocations) {
        val startWeek = startWeekText.toIntOrNull()
        val endWeek = endWeekText.toIntOrNull()
        WeekLocationDialog(
            weeks = if (startWeek != null && endWeek != null && startWeek in 1..endWeek) {
                (startWeek..endWeek).toList()
            } else {
                (1..maxWeekCount).toList()
            },
            courseTitle = title.trim(),
            baseLocation = location.trim(),
            weekLocations = weekLocationsMap,
            suggestions = reuse.locations,
            onConfirm = { values ->
                weekLocationsRaw = encodeWeekLocations(values)
                pickingWeekLocations = false
            },
            onDismiss = { pickingWeekLocations = false },
        )
    }
}
