package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedTextField
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.rounded.Close
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.Role
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
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.pluralStringResource
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
import com.x500x.cursimple.feature.plugin.ui.AppModalBottomSheet
import com.x500x.cursimple.feature.schedule.theme.CoursePaletteEntry
import com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import com.x500x.cursimple.feature.plugin.ui.AppDialogTone
import com.x500x.cursimple.feature.plugin.ui.AppDialogDangerButton
import com.x500x.cursimple.feature.plugin.ui.AppAlertDialog
import com.x500x.cursimple.feature.plugin.ui.AppDialogPrimaryButton
import com.x500x.cursimple.feature.plugin.ui.AppDialogSecondaryButton
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Add

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

internal data class EventAddDefaults(val date: String, val startTime: String, val endTime: String)

internal fun ScheduleEvent.addDefaults(): EventAddDefaults = EventAddDefaults(date, startTime, endTime)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun EventActionSheet(
    event: ScheduleEvent,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onAddAtPosition: () -> Unit,
    onDelete: () -> Unit,
) {
    AppModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "${formatPickerDate(event.localDate ?: LocalDate.now())}  ${event.timeRangeText()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            EventActionRow(Icons.Rounded.Edit, stringResource(R.string.schedule_action_edit), onEdit)
            EventActionRow(Icons.Rounded.Add, stringResource(R.string.schedule_event_add_title), onAddAtPosition)
            EventActionRow(Icons.Rounded.DeleteOutline, stringResource(R.string.schedule_action_delete), onDelete, MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EventActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tint)
        }
    }
}

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
                    ?: BorderStroke(1.dp, colors.onContainer.copy(alpha = 0.45f)),
                shape,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        val slim = width < 44.dp
        // A round corner cuts into the first line of text; inset by what a circle of that radius takes off the corner.
        val cornerInset = (cornerRadius.coerceAtMost(height / 2).coerceAtMost(width / 2).value * 0.3f).dp
        // An event is told apart by its outlined box rather than a stripe, so the text owns the full width.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = maxOf(if (slim) 2.dp else 3.dp, cornerInset),
                    vertical = maxOf(3.dp, cornerInset),
                ),
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
                val timeStyle = baseStyle.merge(TextStyle(fontSize = 8.5.sp, lineHeight = 9.5.sp))
                val titleStyle = baseStyle.merge(
                    TextStyle(
                        fontSize = fittedSize.sp,
                        lineHeight = (fittedSize + 1f).sp,
                        fontWeight = FontWeight.Bold,
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
                            fontSize = 8.5.sp,
                            lineHeight = 9.5.sp,
                            color = colors.onContainer.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
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
        AppAlertDialog(
            onDismissRequest = { confirmDelete = false },
            tone = AppDialogTone.Danger,
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(22.dp)) },
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
                AppDialogDangerButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.schedule_action_delete)) }
            },
            dismissButton = {
                AppDialogSecondaryButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.schedule_action_cancel))
                }
            },
        )
        return
    }
    val colors = eventColors(event)
    AppAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = true),
        icon = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(22.dp)) },
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
            AppDialogPrimaryButton(onClick = onEdit) { Text(stringResource(R.string.schedule_event_edit)) }
        },
        dismissButton = {
            AppDialogSecondaryButton(onClick = { confirmDelete = true }) {
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
    copyMode: Boolean = false,
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

    fun submit() {
        submitted = true
        if (titleMissing || timeInvalid || weeksMissing) return
        onSave(
            ScheduleEvent(
                id = initial?.id?.takeIf { it.isNotBlank() && !copyMode } ?: "event-${UUID.randomUUID()}",
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
    }

    // A full page like the course editor: the save button stays in the top bar while the form scrolls,
    // and the page lifts above the keyboard.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize().testTag("event-editor-page"), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppCircleIconButton(onClick = onDismiss, modifier = Modifier.testTag("event-editor-close")) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.schedule_action_cancel))
                    }
                    Text(
                        text = stringResource(
                            if (initial == null || copyMode) R.string.schedule_event_add_title else R.string.schedule_event_edit_title,
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = ::submit, modifier = Modifier.testTag("event-editor-save")) {
                        Text(stringResource(R.string.schedule_action_save), maxLines = 1)
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
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
            modifier = Modifier.fillMaxWidth(),
                        )
                }
            }
        }
    }
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
            titleRes = if (pick == EventPick.Start) R.string.schedule_time_select_start else R.string.schedule_time_select_end,
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
        AppOutlinedTextField(
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
        AppOutlinedTextField(
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

@Composable
internal fun EventTimePickerDialog(
    initialMinute: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
    @androidx.annotation.StringRes titleRes: Int = R.string.schedule_time_select,
) {
    val state = rememberAppTimeInputState(initialMinute)
    val minutesOfDay = state.minutesOfDay
    AppAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(22.dp)) },
        title = { Text(stringResource(titleRes)) },
        text = { AppTimeInput(state = state) },
        confirmButton = {
            AppDialogPrimaryButton(
                enabled = minutesOfDay != null,
                modifier = Modifier.testTag("time-input-save"),
                onClick = { minutesOfDay?.let(onPick) },
            ) {
                Text(stringResource(R.string.schedule_action_save))
            }
        },
        dismissButton = {
            AppDialogSecondaryButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) }
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
    val radius = cornerRadius.coerceAtMost(height / 2)
    val shape = RoundedCornerShape(radius)
    val highlight = eventColors(events.first()).onContainer
    val base = MaterialTheme.colorScheme.surfaceContainerLow
    val fill = lerp(base, highlight, 0.16f)
    val onContainer = MaterialTheme.colorScheme.onSurfaceVariant
    // Same language as the folded-course chip: a dashed hint, not a card of its own.
    Column(
        modifier = Modifier
            .offset(offsetX, offsetY)
            .width(width)
            .height(height)
            .clip(shape)
            .background(fill)
            .innerGlow(radius, highlight)
            .dashedChipBorder(radius, highlight.copy(alpha = 0.9f))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        val countLabel = pluralStringResource(R.plurals.schedule_event_group_count, events.size, events.size)
        if (height < 26.dp) {
            // One line only: the count sits beside the dots rather than a bare "···".
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (height >= 16.dp) ChipEllipsis(width, onContainer)
                if (width >= 26.dp) ChipNumber(events.size, onContainer)
            }
        } else {
            if (height >= 16.dp) ChipEllipsis(width, onContainer)
            ChipCount(countLabel, events.size, onContainer)
            ChipDots(
                dots = events.take(MAX_CHIP_DOTS).map { ChipDot(eventColors(it).onContainer, active = true) },
                width = width,
                height = height,
            )
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
    /** One dot per folded item, so the chip shows what hides behind it and which of it runs this week. */
    dots: List<ChipDot> = emptyList(),
    /** True when the chip holds only courses; with events inside it counts "items" instead of "courses". */
    coursesOnly: Boolean = true,
) {
    val radius = cornerRadius.coerceAtMost(height / 2)
    val shape = RoundedCornerShape(radius)
    // The chip stays neutral; only its dots carry the folded courses' own colours.
    val fill = MaterialTheme.colorScheme.surfaceContainerLow
    val dashColor = MaterialTheme.colorScheme.outline
    val onChip = MaterialTheme.colorScheme.onSurfaceVariant
    val ellipsisColor = onChip
    val description = if (coursesOnly) {
        pluralStringResource(R.plurals.schedule_course_more_description, count, count)
    } else {
        stringResource(R.string.schedule_overflow_title, count)
    }
    Column(
        modifier = Modifier
            .testTag("schedule-course-more")
            .offset(offsetX, offsetY)
            .width(width)
            .height(height)
            .clip(shape)
            .background(fill)
            .dashedChipBorder(radius, dashColor)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        // A run of dots: several items share this time, none of them extra lessons on the timetable.
        val countLabel = pluralStringResource(
            if (coursesOnly) R.plurals.schedule_course_more_count else R.plurals.schedule_event_group_count,
            count,
            count,
        )
        if (height < 26.dp) {
            // A lane chip is only one line tall, so the count rides beside the dots; a bare "···" would
            // say that something is hidden but not how much. The number stands alone because a lane can
            // be as narrow as a thumbnail and any word would be cut off.
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (height >= 16.dp) ChipEllipsis(width, ellipsisColor)
                if (width >= 26.dp) ChipNumber(count, onChip)
            }
        } else {
            if (height >= 16.dp) ChipEllipsis(width, ellipsisColor)
            ChipCount(countLabel, count, onChip)
            ChipDots(dots, width, height)
        }
    }
}

/** An item folded into a "more" chip: lit in its own [color] when it runs this week, grey otherwise. */
internal data class ChipDot(val color: Color, val active: Boolean)

/** A day shows at most this many folded dots; with the card on its left that is ten items in one spot. */
internal const val MAX_CHIP_DOTS = 9

private val ChipDotSlot = 8.dp

/** Dots wrap into rows of up to three, as many rows as the chip's height leaves room for. */
@Composable
private fun ChipDots(dots: List<ChipDot>, width: Dp, height: Dp) {
    if (dots.isEmpty()) return
    // The ellipsis, the count and the padding around them take about 36dp of the chip.
    val rows = ((height - 36.dp) / ChipDotSlot).toInt().coerceIn(0, 3)
    val perRow = ((width - 8.dp) / ChipDotSlot).toInt().coerceIn(1, 3)
    if (rows == 0 || height < 46.dp) return
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        dots.take(MAX_CHIP_DOTS).take(rows * perRow).chunked(perRow).forEach { row ->
            Row {
                row.forEach { dot -> ChipDotView(if (dot.active) dot.color else inactive) }
            }
        }
    }
}

/** A plain dot in the item's own colour, with no halo. */
@Composable
private fun ChipDotView(color: Color) {
    Box(
        modifier = Modifier
            .size(ChipDotSlot)
            .drawBehind { drawCircle(color = color, radius = 2.5.dp.toPx(), center = size.center) },
    )
}

/** The bare count for a one-line chip, where the words of [ChipCount] would not fit. */
@Composable
private fun ChipNumber(count: Int, color: Color) {
    Text(
        text = count.toString(),
        fontSize = 9.5.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}

/** The run of dots that says "several items share this time", shared by every folded chip. */
@Composable
private fun ChipEllipsis(width: Dp, color: Color) {
    Text(
        text = "···",
        fontSize = if (width < 30.dp) 12.sp else 13.5.sp,
        lineHeight = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}

/** How many items the chip hides; smaller than the dots so the dots keep the chip's centre. */
@Composable
private fun ChipCount(label: String, count: Int, color: Color) {
    // A clipped label would sit against the chip's left edge, so a lane too narrow for the words shows the number, centred.
    var tooWide by remember(label) { mutableStateOf(false) }
    if (tooWide) {
        ChipNumber(count, color)
        return
    }
    Text(
        text = label,
        fontSize = 9.5.sp,
        lineHeight = 11.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { if (it.hasVisualOverflow) tooWide = true },
    )
}

/** A soft halo just inside the chip's edge, drawn as a few fading strokes; the chip clips the outer half. */
private fun Modifier.innerGlow(radius: Dp, color: Color): Modifier = drawBehind {
    val steps = 4
    for (i in 0 until steps) {
        drawRoundRect(
            color = color.copy(alpha = 0.32f * (1f - i / steps.toFloat())),
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(width = (2.5f * (i + 1)).dp.toPx()),
        )
    }
}

/** A dashed edge says "there is more behind this", where a solid border would read as another card. */
private fun Modifier.dashedChipBorder(radius: Dp, color: Color): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
        ),
    )
}

@Composable
internal fun ScheduleEventGroupDialog(
    events: List<ScheduleEvent>,
    date: LocalDate,
    onPick: (ScheduleEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val sorted = remember(events) { events.sortedWith(compareBy({ it.startMinute }, { it.endMinute })) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.EventNote, contentDescription = null, modifier = Modifier.size(22.dp)) },
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
            AppDialogPrimaryButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_event_group_close)) }
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
