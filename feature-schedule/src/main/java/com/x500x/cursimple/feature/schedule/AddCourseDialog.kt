package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.DEFAULT_TERM_WEEK_COUNT
import com.x500x.cursimple.feature.plugin.ui.AppFilterChip
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.feature.schedule.time.today
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Composable
fun AddCourseDialog(
    onDismiss: () -> Unit,
    onConfirm: (CourseItem) -> Unit,
    onConfirmEvent: ((ScheduleEvent) -> Unit)? = null,
    existingCourses: List<CourseItem> = emptyList(),
    maxNodeCount: Int = 12,
    maxWeekCount: Int = DEFAULT_TERM_WEEK_COUNT,
    initial: CourseItem? = null,
    slotTimes: List<ClassSlotTime> = emptyList(),
    initialScheduleType: ScheduleItemType = ScheduleItemType.Course,
    defaultDate: LocalDate? = null,
    termStartDate: LocalDate? = null,
) {
    var scheduleType by rememberSaveable(initial, initialScheduleType) {
        mutableStateOf(if (initial != null) ScheduleItemType.Course else initialScheduleType)
    }

    // Shared state between Course and Event forms so user inputs are retained if they toggle
    var sharedTitle by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var sharedLocation by rememberSaveable { mutableStateOf(initial?.location.orEmpty()) }

    // Course draft state
    var draft by remember(initial) { mutableStateOf<CourseItem?>(null) }

    // Event state
    val zone = com.x500x.cursimple.feature.schedule.time.LocalAppZone.current
    val today = remember(zone) { defaultDate ?: zone.today() }
    val now = remember(zone) { com.x500x.cursimple.core.kernel.time.BeijingTime.nowTimeIn(zone) }
    val defaultStart = remember { LocalTime.of((now.hour + 1).coerceAtMost(22), 0) }
    var eventDate by rememberSaveable { mutableStateOf(today) }
    var eventStart by rememberSaveable { mutableStateOf(minuteText(defaultStart.hour * 60)) }
    var eventEnd by rememberSaveable { mutableStateOf(minuteText((defaultStart.hour + 1) * 60)) }
    var eventNote by rememberSaveable { mutableStateOf("") }
    var eventRepeat by rememberSaveable { mutableStateOf(EventRepeat.Once) }
    var eventRepeatUntil by rememberSaveable { mutableStateOf<String?>(null) }
    val weekAnchor = remember { eventWeekAnchor(termStartDate, today) }
    var eventPickedWeeks by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var eventColorArgb by rememberSaveable { mutableStateOf<Long?>(null) }
    var eventSubmitted by remember { mutableStateOf(false) }

    val reuse = rememberCourseReuseValues(existingCourses)
    val locationOptions = remember(reuse.locations) { reuse.locations.map { CourseReuseOption(it) } }

    val isEventMode = initial == null && scheduleType == ScheduleItemType.Event && onConfirmEvent != null

    val eventStartMinute = clockMinute(eventStart)
    val eventEndMinute = clockMinute(eventEnd)
    val eventTitleMissing = sharedTitle.isBlank()
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
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(
                        if (initial != null) R.string.schedule_edit_course_title
                        else if (isEventMode) R.string.schedule_event_add_title
                        else if (scheduleType == ScheduleItemType.Exam) R.string.schedule_add_exam_title
                        else R.string.schedule_add_course_title,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                if (initial == null && onConfirmEvent != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppFilterChip(
                            selected = scheduleType == ScheduleItemType.Course,
                            onClick = { scheduleType = ScheduleItemType.Course },
                            label = { Text(stringResource(R.string.schedule_category_course)) },
                            modifier = Modifier.testTag("add-dialog-type-course"),
                        )
                        AppFilterChip(
                            selected = scheduleType == ScheduleItemType.Exam,
                            onClick = { scheduleType = ScheduleItemType.Exam },
                            label = { Text(stringResource(R.string.schedule_category_exam)) },
                            modifier = Modifier.testTag("add-dialog-type-exam"),
                        )
                        AppFilterChip(
                            selected = scheduleType == ScheduleItemType.Event,
                            onClick = { scheduleType = ScheduleItemType.Event },
                            label = { Text(stringResource(R.string.schedule_category_event)) },
                            modifier = Modifier.testTag("add-dialog-type-event"),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isEventMode) {
                    ScheduleEventEditForm(
                        title = sharedTitle,
                        onTitleChange = { sharedTitle = it },
                        date = eventDate,
                        onDateChange = { eventDate = it },
                        start = eventStart,
                        onStartChange = { eventStart = it },
                        end = eventEnd,
                        onEndChange = { eventEnd = it },
                        location = sharedLocation,
                        onLocationChange = { sharedLocation = it },
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
                        locationOptions = locationOptions,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    )
                } else {
                    CourseEditFormFields(
                        initial = initial,
                        initialCategory = if (scheduleType == ScheduleItemType.Exam) CourseCategory.Exam else CourseCategory.Course,
                        existingCourses = existingCourses,
                        maxNodeCount = maxNodeCount,
                        maxWeekCount = maxWeekCount,
                        showCategorySelector = (initial != null || onConfirmEvent == null),
                        onDraftChange = {
                            draft = it
                            if (it != null) {
                                sharedTitle = it.title
                                sharedLocation = it.location
                            }
                        },
                        onCategoryChange = { cat ->
                            scheduleType = if (cat == CourseCategory.Exam) ScheduleItemType.Exam else ScheduleItemType.Course
                        },
                        onSelectEvent = if (initial == null && onConfirmEvent != null) {
                            { scheduleType = ScheduleItemType.Event }
                        } else null,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        slotTimes = slotTimes,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (isEventMode) {
                                eventSubmitted = true
                                if (!canSaveEvent) return@Button
                                val event = ScheduleEvent(
                                    id = "event-${UUID.randomUUID()}",
                                    title = sharedTitle.trim(),
                                    date = eventDate.toString(),
                                    startTime = eventStart,
                                    endTime = eventEnd,
                                    location = sharedLocation.trim(),
                                    note = eventNote.trim(),
                                    colorArgb = eventColorArgb,
                                ).withRepeat(eventRepeat, eventRepeatUntil, eventPickedWeeks, weekAnchor)
                                onConfirmEvent?.invoke(event)
                            } else {
                                draft?.let(onConfirm)
                            }
                        },
                        enabled = if (isEventMode) true else draft != null,
                    ) { Text(stringResource(R.string.schedule_action_save)) }
                }
            }
        }
    }
}

/**
 * Conflicts are advisory, using tertiary rather than error styling; saving remains available.
 */
@Composable
internal fun CourseConflictWarning(warning: AddCourseConflictWarning) {
    val text = LocalContext.current.addCourseConflictWarningText(warning)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}
