package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.x500x.cursimple.core.kernel.model.DEFAULT_TERM_WEEK_COUNT
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.core.kernel.model.resolveTermWeekNumber
import com.x500x.cursimple.core.kernel.model.termWeekDates
import com.x500x.cursimple.feature.plugin.ui.AppFilterChip
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.schedule.theme.CoursePaletteEntry
import com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/** Use an explicit event color, otherwise choose from the course palette by title. */
@Composable
internal fun eventColors(event: ScheduleEvent): CoursePaletteEntry {
    val argb = event.colorArgb
    if (argb != null) {
        val container = Color(argb.toInt())
        // A blend reads against the mix of both ends, so the text stays legible across the whole block.
        val on = readableOn(blendedFill(container, event.colorEndArgb?.let { Color(it.toInt()) }))
        return CoursePaletteEntry(container, on)
    }
    return courseColor("event:${event.title}", LocalScheduleAccents.current.coursePalette)
}

internal fun ScheduleEvent.timeRangeText(): String = "$startTime–$endTime"

/**
 * Event blocks retain title priority and use a stripe, outline and clock label to distinguish
 * them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EventBlock(
    event: ScheduleEvent,
    width: Dp,
    height: Dp,
    offsetX: Dp,
    offsetY: Dp,
    cornerRadius: Dp,
    titleSizeSp: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = eventColors(event)
    val shape = RoundedCornerShape(cornerRadius.coerceAtMost(height / 2))
    Box(
        modifier = Modifier
            .offset(offsetX, offsetY)
            .width(width)
            .height(height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (event.colorArgb != null && event.colorEndArgb != null) {
                    Modifier.background(fillBrush(colors.container, Color(event.colorEndArgb!!.toInt()), 0.94f))
                } else {
                    Modifier.background(colors.container.copy(alpha = 0.94f))
                },
            )
            .border(
                event.borderArgb?.let { BorderStroke(1.5.dp, Color(it.toInt())) }
                    ?: BorderStroke(1.dp, colors.onContainer.copy(alpha = 0.28f)),
                shape,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        val slim = width < 44.dp
        Row(modifier = Modifier.fillMaxHeight()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(if (slim) 2.dp else 3.dp)
                    .background(colors.onContainer.copy(alpha = 0.9f)),
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = if (slim) 1.5.dp else 3.dp, vertical = 2.dp),
            ) {
                val tight = maxHeight < 30.dp
                val roomy = maxHeight >= 56.dp
                val narrow = maxWidth < 58.dp
                val titleSize = (titleSizeSp - 1f).coerceAtLeast(9f)
                // Wrap narrow text within available height rather than replacing it entirely with ellipsis.
                val timeLineDp = if (tight) 0f else 11f
                val estimatedLines = (((maxHeight.value - timeLineDp) / ((titleSize + 1f) * 1.15f)).toInt())
                    .coerceIn(1, 4)
                // Shrink short one-line titles when width is limited.
                val fittedSize = if (estimatedLines == 1) {
                    fitSingleLineSp(event.title, maxWidth.value, titleSize)
                } else {
                    titleSize
                }
                val timeText = if (narrow) event.startTime else event.timeRangeText()
                // Measure and render with the same merged styles.
                val baseStyle = LocalTextStyle.current.let { style ->
                    if (event.colorEndArgb != null && event.colorArgb != null) {
                        style.merge(TextStyle(shadow = blendTextShadow(colors.onContainer)))
                    } else {
                        style
                    }
                }
                val timeStyle = baseStyle.merge(TextStyle(fontSize = 9.sp, lineHeight = 10.sp))
                val titleStyle = baseStyle.merge(
                    TextStyle(
                        fontSize = fittedSize.sp,
                        lineHeight = (fittedSize + 1f).sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                // Measure real line bottoms so a half-visible title line is never drawn.
                val textMeasurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val plan = remember(event.title, timeText, titleStyle, timeStyle, maxWidth, maxHeight, tight) {
                    val widthPx = with(density) { maxWidth.roundToPx() }
                    val title = textMeasurer.measure(
                        event.title,
                        titleStyle,
                        constraints = Constraints(maxWidth = widthPx),
                        maxLines = 4,
                    )
                    eventBlockTextPlan(
                        availableHeight = with(density) { maxHeight.toPx() },
                        timeHeight = if (tight) null else {
                            textMeasurer.measure(timeText, timeStyle, softWrap = false, maxLines = 1).size.height.toFloat() +
                                with(density) { 1.dp.toPx() }
                        },
                        titleLineBottoms = List(title.lineCount) { title.getLineBottom(it) },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    if (plan.showTime) {
                        // Show only start time when a full time range cannot fit.
                        Text(
                            text = timeText,
                            style = timeStyle,
                            color = colors.onContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                    Text(
                        text = event.title,
                        style = titleStyle,
                        color = colors.onContainer,
                        maxLines = plan.titleLines,
                        overflow = if (narrow) TextOverflow.Clip else TextOverflow.Ellipsis,
                    )
                    if (roomy && event.location.isNotBlank()) {
                        Text(
                            text = event.location,
                            fontSize = 9.sp,
                            lineHeight = 10.sp,
                            color = colors.onContainer.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Inserted time bands display clock boundaries instead of period numbers. */
@Composable
internal fun InsertedTimeCell(startMinute: Int, endMinute: Int, height: Dp, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = minuteText(startMinute),
                fontSize = 9.sp,
                lineHeight = 10.sp,
                color = color,
                maxLines = 1,
            )
            if (height >= 40.dp) {
                Text(
                    text = minuteText(endMinute),
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    color = color.copy(alpha = color.alpha * 0.7f),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Estimate one-line size using CJK and Latin widths, bounded by [preferredSp] and an 8sp
 * minimum.
 */
internal data class EventBlockTextPlan(val showTime: Boolean, val titleLines: Int)

/** Keep the time line only when dropping it would not reveal more of the title. */
internal fun eventBlockTextPlan(
    availableHeight: Float,
    timeHeight: Float?,
    titleLineBottoms: List<Float>,
): EventBlockTextPlan {
    fun fitting(height: Float) = titleLineBottoms.count { it <= height + 0.5f }
    val withoutTime = fitting(availableHeight).coerceAtLeast(1)
    if (timeHeight == null) return EventBlockTextPlan(showTime = false, titleLines = withoutTime)
    val withTime = fitting(availableHeight - timeHeight)
    return if (withTime >= 1 && withTime >= withoutTime) {
        EventBlockTextPlan(showTime = true, titleLines = withTime)
    } else {
        EventBlockTextPlan(showTime = false, titleLines = withoutTime)
    }
}

internal fun fitSingleLineSp(text: String, widthDp: Float, preferredSp: Float): Float {
    val ems = text.sumOf { c -> if (c.code < 0x2E80) 0.6 else 1.0 }.toFloat().coerceAtLeast(1f)
    return (widthDp / (ems * 1.05f)).coerceIn(8f, preferredSp)
}

internal fun minuteText(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

@Composable
internal fun ScheduleEventDetailDialog(
    event: ScheduleEvent,
    date: LocalDate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.schedule_event_delete_title)) },
            text = {
                Text(
                    stringResource(
                        if (event.isRecurring) {
                            R.string.schedule_event_delete_body_repeat
                        } else {
                            R.string.schedule_event_delete_body
                        },
                        event.title,
                    ),
                )
            },
            confirmButton = {
                AppOutlinedButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.schedule_action_delete)) }
            },
            dismissButton = {
                AppOutlinedButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.schedule_action_cancel))
                }
            },
        )
        return
    }
    val colors = eventColors(event)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(colors.container)
                        .border(1.dp, colors.onContainer.copy(alpha = 0.4f), CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Text(event.title)
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EventDetailLine(Icons.Rounded.AccessTime, "${formatPickerDate(date)}  ${event.timeRangeText()}")
                if (event.isRecurring) {
                    EventDetailLine(Icons.Rounded.Repeat, repeatText(event))
                }
                if (event.location.isNotBlank()) {
                    EventDetailLine(Icons.Rounded.LocationOn, event.location)
                }
                if (event.note.isNotBlank()) {
                    Text(
                        text = event.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            AppOutlinedButton(onClick = onEdit) { Text(stringResource(R.string.schedule_event_edit)) }
        },
        dismissButton = {
            AppOutlinedButton(onClick = { confirmDelete = true }) {
                Text(stringResource(R.string.schedule_action_delete))
            }
        },
    )
}

@Composable
private fun EventDetailLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun repeatText(event: ScheduleEvent): String {
    if (event.weekDates.isNotEmpty()) {
        val dates = event.pickedDates
        return stringResource(
            R.string.schedule_event_repeat_picked,
            dates.size,
            dates.joinToString("、") { "${it.monthValue}/${it.dayOfMonth}" },
        )
    }
    val until = event.repeatUntil?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    return if (until == null) {
        stringResource(R.string.schedule_event_repeat_forever)
    } else {
        stringResource(R.string.schedule_event_repeat_until, formatPickerDate(until))
    }
}

internal enum class EventRepeat { Once, Weekly, Weeks }

internal val ScheduleEvent.repeatMode: EventRepeat
    get() = when {
        weekDates.isNotEmpty() -> EventRepeat.Weeks
        repeatWeekly -> EventRepeat.Weekly
        else -> EventRepeat.Once
    }

/** Week 1 is the term's first week, or the event's own week when no term start is set. */
internal fun eventWeekAnchor(termStart: LocalDate?, date: LocalDate): LocalDate =
    termStart ?: date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Picked weeks fall on the event's weekday; the stored date becomes the earliest of them. */
internal fun ScheduleEvent.withRepeat(
    repeat: EventRepeat,
    repeatUntil: String?,
    weeks: Collection<Int>,
    anchor: LocalDate,
): ScheduleEvent = when (repeat) {
    EventRepeat.Once -> copy(repeatWeekly = false, repeatUntil = null, weekDates = emptyList())
    EventRepeat.Weekly -> copy(repeatWeekly = true, repeatUntil = repeatUntil, weekDates = emptyList())
    EventRepeat.Weeks -> {
        val day = localDate
        val dates = if (day == null) emptyList() else termWeekDates(anchor, day.dayOfWeek, weeks)
        copy(
            date = dates.firstOrNull()?.toString() ?: date,
            repeatWeekly = false,
            repeatUntil = null,
            weekDates = dates.map { it.toString() },
        )
    }
}

/**
 * Event editor requires title and valid times; defaults to the selected date and next whole
 * hour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduleEventEditorDialog(
    initial: ScheduleEvent?,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (ScheduleEvent) -> Unit,
    termStartDate: LocalDate? = null,
    maxWeekCount: Int = DEFAULT_TERM_WEEK_COUNT,
) {
    val reuse = rememberCourseReuseValues(emptyList())
    val locationOptions = remember(reuse.locations) { reuse.locations.map { CourseReuseOption(it) } }
    val zone = com.x500x.cursimple.feature.schedule.time.LocalAppZone.current
    val now = remember(zone) { com.x500x.cursimple.core.kernel.time.BeijingTime.nowTimeIn(zone) }
    val defaultStart = remember { LocalTime.of((now.hour + 1).coerceAtMost(22), 0) }
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(initial?.localDate ?: defaultDate) }
    var start by rememberSaveable { mutableStateOf(initial?.startTime ?: minuteText(defaultStart.hour * 60)) }
    var end by rememberSaveable {
        mutableStateOf(initial?.endTime ?: minuteText((defaultStart.hour + 1) * 60))
    }
    var location by rememberSaveable { mutableStateOf(initial?.location.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }
    var repeat by rememberSaveable { mutableStateOf(initial?.repeatMode ?: EventRepeat.Once) }
    var repeatUntil by rememberSaveable { mutableStateOf(initial?.repeatUntil) }
    // Fixed for the whole edit so changing the date never shifts the picked week numbers.
    val weekAnchor = remember { eventWeekAnchor(termStartDate, initial?.localDate ?: defaultDate) }
    var pickedWeeks by rememberSaveable { mutableStateOf(initial?.termWeeks(weekAnchor).orEmpty()) }
    var appearance by rememberSaveable(stateSaver = ItemAppearanceSaver) { mutableStateOf(initial?.appearance() ?: ItemAppearance()) }

    var picking by remember { mutableStateOf<EventPick?>(null) }
    var submitted by remember { mutableStateOf(false) }

    val startMinute = clockMinute(start)
    val endMinute = clockMinute(end)
    val titleMissing = title.isBlank()
    val timeInvalid = startMinute == null || endMinute == null || endMinute <= startMinute
    val weeksMissing = repeat == EventRepeat.Weeks && pickedWeeks.isEmpty()
    val weekLimit = maxOf(maxWeekCount, pickedWeeks.maxOrNull() ?: 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.schedule_event_add_title else R.string.schedule_event_edit_title,
                ),
            )
        },
        text = {
            ScheduleEventEditForm(
                title = title,
                onTitleChange = { title = it },
                date = date,
                onDateChange = { date = it },
                start = start,
                onStartChange = { start = it },
                end = end,
                onEndChange = { end = it },
                location = location,
                onLocationChange = { location = it },
                note = note,
                onNoteChange = { note = it },
                repeat = repeat,
                onRepeatChange = { repeat = it },
                repeatUntil = repeatUntil,
                onRepeatUntilChange = { repeatUntil = it },
                pickedWeeks = pickedWeeks,
                onPickedWeeksChange = { pickedWeeks = it },
                appearance = appearance,
                onAppearanceChange = { appearance = it },
                submitted = submitted,
                weekAnchor = weekAnchor,
                weekLimit = weekLimit,
                locationOptions = locationOptions,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            AppOutlinedButton(onClick = {
                submitted = true
                if (titleMissing || timeInvalid || weeksMissing) return@AppOutlinedButton
                onSave(
                    ScheduleEvent(
                        id = initial?.id ?: "event-${UUID.randomUUID()}",
                        title = title.trim(),
                        date = date.toString(),
                        startTime = start,
                        endTime = end,
                        location = location.trim(),
                        note = note.trim(),
                        colorArgb = appearance.fill,
                        colorEndArgb = appearance.fillEnd,
                        borderArgb = appearance.border,
                    ).withRepeat(repeat, repeatUntil, pickedWeeks, weekAnchor),
                )
            }) { Text(stringResource(R.string.schedule_action_save)) }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) }
        },
    )
}

internal enum class EventPick { Date, Start, End, RepeatUntil, Weeks }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduleEventEditForm(
    title: String,
    onTitleChange: (String) -> Unit,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    start: String,
    onStartChange: (String) -> Unit,
    end: String,
    onEndChange: (String) -> Unit,
    location: String,
    onLocationChange: (String) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    repeat: EventRepeat,
    onRepeatChange: (EventRepeat) -> Unit,
    repeatUntil: String?,
    onRepeatUntilChange: (String?) -> Unit,
    pickedWeeks: List<Int>,
    onPickedWeeksChange: (List<Int>) -> Unit,
    appearance: ItemAppearance,
    onAppearanceChange: (ItemAppearance) -> Unit,
    submitted: Boolean,
    weekAnchor: LocalDate,
    weekLimit: Int,
    locationOptions: List<CourseReuseOption> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf<EventPick?>(null) }
    val startMinute = clockMinute(start)
    val endMinute = clockMinute(end)
    val titleMissing = title.isBlank()
    val timeInvalid = startMinute == null || endMinute == null || endMinute <= startMinute

    when (val pick = picking) {
        EventPick.Date -> AlarmDatePickerDialog(
            initial = date,
            onDismiss = { picking = null },
            onPick = {
                onDateChange(it)
                picking = null
            },
        )
        EventPick.RepeatUntil -> AlarmDatePickerDialog(
            initial = repeatUntil?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: date.plusWeeks(8),
            onDismiss = { picking = null },
            onPick = {
                onRepeatUntilChange(it.toString())
                picking = null
            },
        )
        EventPick.Weeks -> WeekMatrixDialog(
            maxWeekCount = weekLimit,
            selected = pickedWeeks.toSet(),
            onConfirm = {
                onPickedWeeksChange(it.sorted())
                picking = null
            },
            onDismiss = { picking = null },
        )
        EventPick.Start, EventPick.End -> EventTimePickerDialog(
            initialMinute = (if (pick == EventPick.Start) startMinute else endMinute) ?: 8 * 60,
            onDismiss = { picking = null },
            onPick = { minute ->
                if (pick == EventPick.Start) {
                    val duration = if (startMinute != null && endMinute != null && endMinute > startMinute) {
                        endMinute - startMinute
                    } else {
                        60
                    }
                    onStartChange(minuteText(minute))
                    onEndChange(minuteText((minute + duration).coerceAtMost(23 * 60 + 59)))
                } else {
                    onEndChange(minuteText(minute))
                }
                picking = null
            },
        )
        null -> Unit
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth().testTag("event-title-field"),
            singleLine = true,
            label = { Text(stringResource(R.string.schedule_event_field_title)) },
            isError = submitted && titleMissing,
            supportingText = if (submitted && titleMissing) {
                { Text(stringResource(R.string.schedule_event_title_required)) }
            } else {
                null
            },
        )
        AlarmEditorSection(stringResource(R.string.schedule_event_section_when)) {
            AlarmValueRow(
                label = stringResource(R.string.schedule_event_field_date),
                value = formatPickerDate(date),
                onClick = { picking = EventPick.Date },
            )
            AlarmValueRow(
                label = stringResource(R.string.schedule_event_field_start),
                value = start,
                onClick = { picking = EventPick.Start },
            )
            AlarmValueRow(
                label = stringResource(R.string.schedule_event_field_end),
                value = end,
                onClick = { picking = EventPick.End },
            )
            if (timeInvalid) {
                Text(
                    text = stringResource(R.string.schedule_event_time_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        AlarmEditorSection(stringResource(R.string.schedule_event_repeat_label)) {
            EventRepeatChips(repeat) { mode ->
                if (mode == EventRepeat.Weeks && pickedWeeks.isEmpty()) {
                    onPickedWeeksChange(listOf(resolveTermWeekNumber(weekAnchor, date)).filter { it in 1..weekLimit })
                }
                onRepeatChange(mode)
            }
            when (repeat) {
                EventRepeat.Once -> Unit
                EventRepeat.Weekly -> {
                    Text(
                        stringResource(R.string.schedule_event_repeat_weekly_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AlarmValueRow(
                        label = stringResource(R.string.schedule_event_repeat_until_label),
                        value = repeatUntil
                            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                            ?.let { formatPickerDate(it) }
                            ?: stringResource(R.string.schedule_event_repeat_until_none),
                        onClick = { picking = EventPick.RepeatUntil },
                    )
                    if (repeatUntil != null) {
                        Text(
                            text = stringResource(R.string.schedule_event_repeat_until_clear),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onRepeatUntilChange(null) },
                        )
                    }
                }
                EventRepeat.Weeks -> {
                    Text(
                        stringResource(
                            R.string.schedule_event_weeks_hint,
                            stringResource(shortWeekdayRes(date.dayOfWeek)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SelectedWeeksRow(weeks = pickedWeeks, onEdit = { picking = EventPick.Weeks })
                }
            }
        }
        CourseReuseTextField(
            value = location,
            onChange = onLocationChange,
            label = stringResource(R.string.schedule_event_field_location),
            options = locationOptions,
            tag = "event-location",
        )
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            label = { Text(stringResource(R.string.schedule_event_field_note)) },
        )
        ItemAppearanceSection(
            appearance = appearance,
            onChange = onAppearanceChange,
            autoColor = courseColor("event:$title", LocalScheduleAccents.current.coursePalette).container,
            previewTitle = title,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventRepeatChips(selected: EventRepeat, onSelect: (EventRepeat) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            EventRepeat.Once to R.string.schedule_event_repeat_once,
            EventRepeat.Weekly to R.string.schedule_event_repeat_mode_weekly,
            EventRepeat.Weeks to R.string.schedule_event_repeat_mode_weeks,
        ).forEach { (mode, label) ->
            AppFilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.testTag("event-repeat-${mode.name.lowercase()}"),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventTimePickerDialog(
    initialMinute: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimeInput(state = state) },
        confirmButton = {
            AppOutlinedButton(onClick = { onPick(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.schedule_action_save))
            }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DayEventRow(
    event: ScheduleEvent,
    cornerRadius: Dp,
    titleSizeSp: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = eventColors(event)
    val shape = RoundedCornerShape(cornerRadius)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(
            modifier = Modifier.widthIn(min = 38.dp, max = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = event.startTime,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = event.endTime,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
                .height(IntrinsicSize.Min)
                .clip(shape)
                .background(colors.container.copy(alpha = 0.94f))
                .border(BorderStroke(1.dp, colors.onContainer.copy(alpha = 0.28f)), shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(colors.onContainer.copy(alpha = 0.9f)),
            )
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = event.title,
                    fontSize = titleSizeSp.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(event.timeRangeText(), event.location).filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 11.sp,
                    color = colors.onContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Group overlapping events into one expandable count with color markers. */
@Composable
internal fun EventGroupBlock(
    events: List<ScheduleEvent>,
    width: Dp,
    height: Dp,
    offsetX: Dp,
    offsetY: Dp,
    cornerRadius: Dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius.coerceAtMost(height / 2))
    val container = MaterialTheme.colorScheme.secondaryContainer
    val onContainer = MaterialTheme.colorScheme.onSecondaryContainer
    val dotColors = events.take(4).map { eventColors(it).container }
    Box(
        modifier = Modifier
            .offset(offsetX, offsetY)
            .width(width)
            .height(height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .background(container.copy(alpha = 0.94f))
            .border(BorderStroke(1.dp, onContainer.copy(alpha = 0.28f)), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "⋯",
                fontSize = 16.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                color = onContainer,
            )
            if (height >= 40.dp) {
                Text(
                    text = androidx.compose.ui.res.pluralStringResource(
                        R.plurals.schedule_event_group_count,
                        events.size,
                        events.size,
                    ),
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    color = onContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                )
            }
            if (height >= 56.dp) {
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    dotColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(0.5.dp, onContainer.copy(alpha = 0.4f), CircleShape),
                        )
                    }
                }
            }
        }
    }
}

/** Expander under the top course of a crowded cell; neutral colors keep it apart from event groups. */
@Composable
internal fun CourseMoreBlock(
    count: Int,
    width: Dp,
    height: Dp,
    offsetX: Dp,
    offsetY: Dp,
    cornerRadius: Dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius.coerceAtMost(height / 2))
    val onContainer = MaterialTheme.colorScheme.onSurfaceVariant
    val description = androidx.compose.ui.res.pluralStringResource(
        R.plurals.schedule_course_more_description,
        count,
        count,
    )
    Row(
        modifier = Modifier
            .testTag("schedule-course-more")
            .offset(offsetX, offsetY)
            .width(width)
            .height(height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "⋯",
            fontSize = 14.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            color = onContainer,
        )
        if (width >= 36.dp) {
            Text(
                text = count.toString(),
                fontSize = 10.sp,
                lineHeight = 10.sp,
                color = onContainer.copy(alpha = 0.8f),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun ScheduleEventGroupDialog(
    events: List<ScheduleEvent>,
    date: LocalDate,
    onPick: (ScheduleEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val sorted = remember(events) { events.sortedWith(compareBy({ it.startMinute }, { it.endMinute })) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    androidx.compose.ui.res.pluralStringResource(
                        R.plurals.schedule_event_group_title,
                        events.size,
                        events.size,
                    ),
                )
                Text(
                    text = formatPickerDate(date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sorted.forEach { event -> EventGroupRow(event = event, onClick = { onPick(event) }) }
            }
        },
        confirmButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_event_group_close)) }
        },
    )
}

@Composable
internal fun EventGroupRow(event: ScheduleEvent, onClick: () -> Unit) {
    val colors = eventColors(event)
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(colors.container.copy(alpha = 0.6f))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(colors.onContainer.copy(alpha = 0.9f)),
        )
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = event.timeRangeText(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onContainer.copy(alpha = 0.85f),
            )
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.onContainer,
            )
            val extra = listOf(event.location, event.note.lineSequence().firstOrNull().orEmpty())
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (extra.isNotEmpty()) {
                Text(
                    text = extra,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
