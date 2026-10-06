package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.schedule.theme.CoursePaletteEntry
import com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** Use an explicit event color, otherwise choose from the course palette by title. */
@Composable
internal fun eventColors(event: ScheduleEvent): CoursePaletteEntry {
    val argb = event.colorArgb
    if (argb != null) {
        val container = Color(argb.toInt())
        val on = if (container.luminance() > 0.5f) Color(0xFF1F2A24) else Color.White
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
            .background(colors.container.copy(alpha = 0.94f))
            .border(BorderStroke(1.dp, colors.onContainer.copy(alpha = 0.28f)), shape)
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
                val titleLines = (((maxHeight.value - timeLineDp) / ((titleSize + 1f) * 1.15f)).toInt())
                    .coerceIn(1, 4)
                // Shrink short one-line titles when width is limited.
                val fittedSize = if (titleLines == 1) {
                    fitSingleLineSp(event.title, maxWidth.value, titleSize)
                } else {
                    titleSize
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    if (!tight) {
                        // Show only start time when a full time range cannot fit.
                        Text(
                            text = if (narrow) event.startTime else event.timeRangeText(),
                            fontSize = 9.sp,
                            lineHeight = 10.sp,
                            color = colors.onContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                    Text(
                        text = event.title,
                        fontSize = fittedSize.sp,
                        lineHeight = (fittedSize + 1f).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onContainer,
                        maxLines = titleLines,
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
                        if (event.repeatWeekly) {
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
                if (event.repeatWeekly) {
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
    val until = event.repeatUntil?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    return if (until == null) {
        stringResource(R.string.schedule_event_repeat_forever)
    } else {
        stringResource(R.string.schedule_event_repeat_until, formatPickerDate(until))
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
) {
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
    var repeatWeekly by rememberSaveable { mutableStateOf(initial?.repeatWeekly ?: false) }
    var repeatUntil by rememberSaveable { mutableStateOf(initial?.repeatUntil) }
    var colorArgb by rememberSaveable { mutableStateOf(initial?.colorArgb) }

    var picking by remember { mutableStateOf<EventPick?>(null) }
    var submitted by remember { mutableStateOf(false) }

    val startMinute = clockMinute(start)
    val endMinute = clockMinute(end)
    val titleMissing = title.isBlank()
    val timeInvalid = startMinute == null || endMinute == null || endMinute <= startMinute

    when (val pick = picking) {
        EventPick.Date -> AlarmDatePickerDialog(
            initial = date,
            onDismiss = { picking = null },
            onPick = {
                date = it
                picking = null
            },
        )
        EventPick.RepeatUntil -> AlarmDatePickerDialog(
            initial = repeatUntil?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: date.plusWeeks(8),
            onDismiss = { picking = null },
            onPick = {
                repeatUntil = it.toString()
                picking = null
            },
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
                    start = minuteText(minute)
                    end = minuteText((minute + duration).coerceAtMost(23 * 60 + 59))
                } else {
                    end = minuteText(minute)
                }
                picking = null
            },
        )
        null -> Unit
    }

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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.schedule_event_repeat_weekly),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(R.string.schedule_event_repeat_weekly_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = repeatWeekly, onCheckedChange = { repeatWeekly = it })
                    }
                    if (repeatWeekly) {
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
                                modifier = Modifier.clickable { repeatUntil = null },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.schedule_event_field_location)) },
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    label = { Text(stringResource(R.string.schedule_event_field_note)) },
                )
                AlarmEditorSection(stringResource(R.string.schedule_event_section_color)) {
                    EventColorRow(
                        selected = colorArgb,
                        previewTitle = title,
                        onSelect = { colorArgb = it },
                    )
                }
            }
        },
        confirmButton = {
            AppOutlinedButton(onClick = {
                submitted = true
                if (titleMissing || timeInvalid) return@AppOutlinedButton
                onSave(
                    ScheduleEvent(
                        id = initial?.id ?: "event-${UUID.randomUUID()}",
                        title = title.trim(),
                        date = date.toString(),
                        startTime = start,
                        endTime = end,
                        location = location.trim(),
                        note = note.trim(),
                        repeatWeekly = repeatWeekly,
                        repeatUntil = repeatUntil.takeIf { repeatWeekly },
                        colorArgb = colorArgb,
                    ),
                )
            }) { Text(stringResource(R.string.schedule_action_save)) }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) }
        },
    )
}

private enum class EventPick { Date, Start, End, RepeatUntil }

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventColorRow(selected: Long?, previewTitle: String, onSelect: (Long?) -> Unit) {
    val palette = LocalScheduleAccents.current.coursePalette.map { it.container }
    val vivid = listOf(Color(0xFFFFB74D), Color(0xFFE57373), Color(0xFF64B5F6), Color(0xFF81C784), Color(0xFFBA68C8))
    val autoColor = courseColor("event:$previewTitle", LocalScheduleAccents.current.coursePalette).container
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ColorDot(
            color = autoColor,
            selected = selected == null,
            label = stringResource(R.string.schedule_event_color_auto),
            onClick = { onSelect(null) },
        )
        (palette + vivid).distinct().forEach { color ->
            val argb = color.toArgb().toLong() and 0xFFFFFFFFL
            ColorDot(color = color, selected = selected == argb, label = null, onClick = { onSelect(argb) })
        }
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, label: String?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = color,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                selected -> Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = label,
                    tint = if (color.luminance() > 0.5f) Color(0xFF1F2A24) else Color.White,
                    modifier = Modifier.size(18.dp),
                )
                label != null -> Text("A", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2A24))
            }
        }
    }
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
private fun EventGroupRow(event: ScheduleEvent, onClick: () -> Unit) {
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
