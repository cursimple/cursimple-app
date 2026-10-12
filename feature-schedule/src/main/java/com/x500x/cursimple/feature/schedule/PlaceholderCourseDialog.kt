package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.feature.plugin.ui.AppFilterChip
import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.CourseItem
import java.time.LocalTime
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PlaceholderCourseDialog(
    course: CourseItem?,
    daysOfWeek: List<Int> = course?.time?.dayOfWeek?.let(::listOf).orEmpty(),
    slotLabels: List<String>,
    maxWeekCount: Int = DEFAULT_PLACEHOLDER_WEEK_COUNT,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, String, List<Int>, List<Int>, String?) -> Unit,
) {
    val storedWeeks = course?.weeks.orEmpty()
    val weekLimit = customWeekLimit(maxWeekCount, storedWeeks)
    val defaultLabel = course?.slotLabelOverride ?: slotLabels.firstOrNull().orEmpty()
    var label by rememberSaveable(course?.id) { mutableStateOf(defaultLabel) }
    var title by rememberSaveable(course?.id) {
        // An unchanged default title mirrors the label, so keep the field empty.
        mutableStateOf(course?.title?.takeIf { it != course.slotLabelOverride }.orEmpty())
    }
    var startMinute by rememberSaveable(course?.id) {
        mutableIntStateOf(parseMinuteOfDay(course?.reminderStartTime) ?: DEFAULT_START_MINUTE)
    }
    var endMinute by rememberSaveable(course?.id) {
        mutableIntStateOf(parseMinuteOfDay(course?.reminderEndTime) ?: DEFAULT_END_MINUTE)
    }
    var daysRaw by rememberSaveable(course?.id, daysOfWeek) {
        mutableStateOf(encodeCustomWeeks(daysOfWeek.filter { it in 1..7 }.ifEmpty { (1..5).toList() }))
    }
    var parity by rememberSaveable(course?.id) { mutableStateOf(placeholderWeekParity(storedWeeks, weekLimit)) }
    var customWeeksRaw by rememberSaveable(course?.id) { mutableStateOf(encodeCustomWeeks(storedWeeks)) }
    var moreExpanded by rememberSaveable(course?.id) {
        mutableStateOf(title.isNotBlank() || parity != WeekParity.All)
    }
    var picking by rememberSaveable { mutableStateOf<PlaceholderTimePick?>(null) }

    val days = decodeCustomWeeks(daysRaw, 7)
    val customWeeks = decodeCustomWeeks(customWeeksRaw, weekLimit)
    val timeValid = endMinute > startMinute
    val weeksValid = parity != WeekParity.Custom || customWeeks.isNotEmpty()
    val canSave = label.isNotBlank() && timeValid && days.isNotEmpty() && weeksValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (course == null) R.string.schedule_placeholder_add_title else R.string.schedule_placeholder_edit_title,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                var labelExpanded by rememberSaveable(course?.id, slotLabels) { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = labelExpanded,
                    onExpandedChange = { labelExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it.take(40) },
                        label = { Text(stringResource(R.string.schedule_placeholder_label_label)) },
                        supportingText = { Text(stringResource(R.string.schedule_placeholder_label_hint)) },
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = labelExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = labelExpanded && slotLabels.isNotEmpty(),
                        onDismissRequest = { labelExpanded = false },
                    ) {
                        slotLabels.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    label = option.take(40)
                                    labelExpanded = false
                                },
                            )
                        }
                    }
                }

                PlaceholderFieldLabel(stringResource(R.string.schedule_placeholder_days_label))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    (1..7).forEach { day ->
                        val selected = day in days
                        val fullName = stringResource(scheduleWeekdayFullRes(day))
                        AppFilterChip(
                            selected = selected,
                            onClick = { daysRaw = encodeCustomWeeks(toggleDay(days, day)) },
                            label = { AppAtomicText(stringResource(WeekdayShortRes[day - 1])) },
                            modifier = Modifier.semantics { contentDescription = fullName },
                        )
                    }
                }
                if (days.isEmpty()) PlaceholderError(stringResource(R.string.schedule_placeholder_days_empty))

                PlaceholderFieldLabel(stringResource(R.string.schedule_placeholder_time_label))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 200.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlaceholderTimeButton(stringResource(R.string.schedule_placeholder_start_label), startMinute,
                                { picking = PlaceholderTimePick.Start }, Modifier.fillMaxWidth())
                            PlaceholderTimeButton(stringResource(R.string.schedule_placeholder_end_label), endMinute,
                                { picking = PlaceholderTimePick.End }, Modifier.fillMaxWidth())
                        }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlaceholderTimeButton(
                            label = stringResource(R.string.schedule_placeholder_start_label),
                            minute = startMinute,
                            onClick = { picking = PlaceholderTimePick.Start },
                            modifier = Modifier.weight(1f),
                        )
                        PlaceholderTimeButton(
                            label = stringResource(R.string.schedule_placeholder_end_label),
                            minute = endMinute,
                            onClick = { picking = PlaceholderTimePick.End },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (!timeValid) PlaceholderError(stringResource(R.string.schedule_placeholder_time_error))

                PlaceholderMoreToggle(expanded = moreExpanded, onToggle = { moreExpanded = !moreExpanded })
                if (moreExpanded) {
                    PlaceholderFieldLabel(stringResource(R.string.schedule_placeholder_weeks_label))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        WeekParity.entries.forEach { option ->
                            AppFilterChip(
                                selected = parity == option,
                                onClick = { parity = option },
                                label = { Text(stringResource(option.labelRes)) },
                            )
                        }
                    }
                    if (parity == WeekParity.Custom) {
                        PlaceholderWeekGrid(
                            weekLimit = weekLimit,
                            selected = customWeeks,
                            onToggle = { week -> customWeeksRaw = encodeCustomWeeks(toggleDay(customWeeks, week)) },
                        )
                        if (customWeeks.isEmpty()) PlaceholderError(stringResource(R.string.schedule_week_custom_empty))
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(40) },
                        label = { Text(stringResource(R.string.schedule_placeholder_name_label)) },
                        placeholder = { Text(stringResource(R.string.schedule_placeholder_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    onSave(
                        course?.id,
                        label.trim(),
                        formatMinuteOfDay(startMinute),
                        formatMinuteOfDay(endMinute),
                        placeholderWeeksFor(parity, customWeeks, weekLimit),
                        days,
                        title.trim().takeIf { it.isNotBlank() },
                    )
                },
            ) { AppAtomicText(stringResource(R.string.schedule_action_save)) }
        },
        dismissButton = { AppOutlinedButton(onClick = onDismiss) { AppAtomicText(stringResource(R.string.schedule_action_cancel)) } },
    )

    picking?.let { pick ->
        EventTimePickerDialog(
            initialMinute = if (pick == PlaceholderTimePick.Start) startMinute else endMinute,
            onDismiss = { picking = null },
            onPick = { minute ->
                if (pick == PlaceholderTimePick.Start) startMinute = minute else endMinute = minute
                picking = null
            },
        )
    }
}

private enum class PlaceholderTimePick { Start, End }

private const val DEFAULT_PLACEHOLDER_WEEK_COUNT = 20
private const val DEFAULT_START_MINUTE = 7 * 60 + 10
private const val DEFAULT_END_MINUTE = 7 * 60 + 50

private val WeekdayShortRes = listOf(
    R.string.schedule_weekday_short_monday,
    R.string.schedule_weekday_short_tuesday,
    R.string.schedule_weekday_short_wednesday,
    R.string.schedule_weekday_short_thursday,
    R.string.schedule_weekday_short_friday,
    R.string.schedule_weekday_short_saturday,
    R.string.schedule_weekday_short_sunday,
)

@Composable
private fun PlaceholderFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun PlaceholderError(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun PlaceholderTimeButton(
    label: String,
    minute: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppOutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 64.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
            AppAtomicText(
                text = formatMinuteOfDay(minute),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun PlaceholderMoreToggle(expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.schedule_placeholder_more),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceholderWeekGrid(
    weekLimit: Int,
    selected: List<Int>,
    onToggle: (Int) -> Unit,
) {
    val selectedState = stringResource(R.string.schedule_placeholder_week_selected)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (1..weekLimit).forEach { week ->
            val isPicked = week in selected
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Checkbox) { onToggle(week) }
                    .semantics { if (isPicked) stateDescription = selectedState },
                color = if (isPicked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = week.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isPicked) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

private fun toggleDay(values: List<Int>, value: Int): List<Int> =
    if (value in values) values - value else (values + value).sorted()

/**
 * Presets apply only when they reproduce the stored weeks exactly; anything else stays custom so
 * saving without edits never changes the stored week set. Empty means every week.
 */
internal fun placeholderWeekParity(weeks: List<Int>, weekLimit: Int): WeekParity {
    val sorted = weeks.distinct().sorted()
    return when {
        sorted.isEmpty() -> WeekParity.All
        sorted == placeholderWeeksFor(WeekParity.Odd, emptyList(), weekLimit) -> WeekParity.Odd
        sorted == placeholderWeeksFor(WeekParity.Even, emptyList(), weekLimit) -> WeekParity.Even
        else -> WeekParity.Custom
    }
}

internal fun placeholderWeeksFor(parity: WeekParity, customWeeks: List<Int>, weekLimit: Int): List<Int> =
    when (parity) {
        WeekParity.All -> emptyList()
        WeekParity.Odd -> (1..weekLimit).filter { it % 2 == 1 }
        WeekParity.Even -> (1..weekLimit).filter { it % 2 == 0 }
        WeekParity.Custom -> customWeeks.distinct().sorted()
    }

internal fun parseMinuteOfDay(value: String?): Int? =
    value?.let { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }?.let { it.hour * 60 + it.minute }

internal fun formatMinuteOfDay(minute: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)
