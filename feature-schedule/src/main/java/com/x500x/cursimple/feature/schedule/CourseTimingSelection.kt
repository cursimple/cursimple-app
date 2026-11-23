package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.data.widget.classSlotLabelText
import com.x500x.cursimple.core.data.widget.MAX_SLOT_NODE
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.resolveTermWeekNumber
import com.x500x.cursimple.core.kernel.model.startLocalTimeOrNull
import com.x500x.cursimple.feature.plugin.ui.AppAlertDialog
import com.x500x.cursimple.feature.plugin.ui.AppDialogPrimaryButton
import com.x500x.cursimple.feature.plugin.ui.AppDialogSecondaryButton
import com.x500x.cursimple.feature.plugin.ui.AppFilterChip
import java.time.LocalDate

internal fun courseWeekForDate(termStart: LocalDate?, date: LocalDate?, maxWeekCount: Int): Int? {
    if (termStart == null || date == null || date.isBefore(termStart)) return null
    return resolveTermWeekNumber(termStart, date).takeIf { it in 1..maxWeekCount }
}

internal fun courseWeeksByDate(
    termStart: LocalDate?,
    dates: List<LocalDate>,
    maxWeekCount: Int,
): Map<Int, List<Int>> {
    val byDay = sortedMapOf<Int, MutableList<Int>>()
    for (date in dates.distinct().sorted()) {
        val week = courseWeekForDate(termStart, date, maxWeekCount) ?: return emptyMap()
        byDay.getOrPut(date.dayOfWeek.value) { mutableListOf() }.add(week)
    }
    return byDay
}

internal fun coursePeriodSlots(slots: List<ClassSlotTime>, fallbackCount: Int): List<ClassSlotTime> =
    if (slots.isNotEmpty()) {
        slots.sortedWith(compareBy<ClassSlotTime> { it.startLocalTimeOrNull() }.thenBy { it.startNode }.thenBy { it.endNode })
    } else {
        (1..fallbackCount.coerceIn(1, 32)).map { node -> ClassSlotTime(node, node, "", "") }
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CourseDateMode(
    byDate: Boolean,
    onModeChange: (Boolean) -> Unit,
    dates: List<LocalDate>,
    defaultDate: LocalDate,
    termStart: LocalDate?,
    maxWeekCount: Int,
    onDatesChange: (List<LocalDate>) -> Unit,
) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppFilterChip(
            selected = !byDate,
            onClick = { onModeChange(false) },
            label = { Text(stringResource(R.string.schedule_course_by_weekday)) },
            modifier = Modifier.testTag("course-by-weekday"),
        )
        AppFilterChip(
            selected = byDate,
            onClick = { onModeChange(true) },
            label = { Text(stringResource(R.string.schedule_course_by_date)) },
            modifier = Modifier.testTag("course-by-date"),
        )
    }
    if (!byDate) return
    val validDates = dates.isEmpty() || courseWeeksByDate(termStart, dates, maxWeekCount).isNotEmpty()
    CourseFormSection(title = stringResource(R.string.schedule_event_field_date)) {
        AlarmValueRow(
            label = stringResource(R.string.schedule_course_pick_dates),
            value = if (dates.size == 1) formatPickerDate(dates.single())
                else pluralStringResource(R.plurals.schedule_course_selected_dates, dates.size, dates.size),
            onClick = { if (termStart != null) pickerOpen = true },
        )
        when {
            termStart == null -> Text(
                stringResource(R.string.schedule_course_date_needs_term),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            !validDates -> Text(
                stringResource(R.string.schedule_course_date_outside_term),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            dates.size > 1 -> Text(
                dates.take(3).map { formatPickerDate(it) }.joinToString(" · ") + if (dates.size > 3) " …" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (pickerOpen && termStart != null) {
        val allowed: (LocalDate) -> Boolean = { courseWeekForDate(termStart, it, maxWeekCount) != null }
        val initialDate = dates.firstOrNull(allowed) ?: defaultDate.takeIf(allowed) ?: termStart
        var pending by rememberSaveable {
            mutableStateOf(dates.filter(allowed).map { it.toEpochDay() })
        }
        AppAlertDialog(
            onDismissRequest = { pickerOpen = false },
            icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(22.dp)) },
            title = { Text(stringResource(R.string.schedule_course_pick_dates)) },
            text = {
                CalendarMonthPicker(
                    selected = initialDate,
                    onSelect = { date ->
                        val epochDay = date.toEpochDay()
                        pending = if (epochDay in pending) pending - epochDay else pending + epochDay
                    },
                    headline = {
                        Text(pluralStringResource(R.plurals.schedule_course_selected_dates, pending.size, pending.size))
                    },
                    isDateSelected = { it.toEpochDay() in pending },
                    isDateEnabled = allowed,
                )
            },
            confirmButton = {
                AppDialogPrimaryButton(onClick = {
                    onDatesChange(pending.sorted().map(LocalDate::ofEpochDay))
                    pickerOpen = false
                }) { Text(stringResource(R.string.schedule_action_save)) }
            },
            dismissButton = {
                AppDialogSecondaryButton(onClick = { pickerOpen = false }) {
                    Text(stringResource(R.string.schedule_action_cancel))
                }
            },
        )
    }
}

@Composable
internal fun CoursePeriodRangeFields(
    slots: List<ClassSlotTime>,
    startNode: Int,
    endNode: Int,
    onRangeChange: (Int, Int) -> Unit,
) {
    CoursePeriodField(
        label = stringResource(R.string.schedule_node_start_label),
        slots = slots,
        choices = slots,
        node = startNode,
        onSelect = { onRangeChange(it.startNode, maxOf(endNode, it.endNode)) },
    )
    CoursePeriodField(
        label = stringResource(R.string.schedule_node_end_label),
        slots = slots,
        choices = slots.filter { it.endNode >= startNode },
        node = endNode,
        onSelect = { onRangeChange(startNode, it.endNode) },
    )
}

@Composable
fun TimingProfilePeriodNumberField(
    label: String,
    value: Int?,
    minimum: Int = 1,
    onSelect: (Int) -> Unit,
) {
    val slots = remember { coursePeriodSlots(emptyList(), MAX_SLOT_NODE) }
    CoursePeriodField(label, slots, slots.filter { it.startNode >= minimum }, value) { onSelect(it.startNode) }
}

@Composable
internal fun CoursePeriodField(
    label: String,
    slots: List<ClassSlotTime>,
    choices: List<ClassSlotTime>,
    node: Int?,
    onSelect: (ClassSlotTime) -> Unit,
) {
    val context = LocalContext.current
    val names = slots.mapIndexed { index, slot -> slot to context.classSlotLabelText(slot, index + 1) }.toMap()
    val selected = node?.let { current -> slots.firstOrNull { current in it.startNode..it.endNode } }
    var open by remember { mutableStateOf(false) }
    AlarmValueRow(
        label = label,
        value = names[selected] ?: node?.let { stringResource(R.string.schedule_node_single, it) }.orEmpty(),
        onClick = { if (choices.isNotEmpty()) open = true },
    )
    if (open) {
        AppAlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    choices.forEach { slot ->
                        AppFilterChip(
                            selected = slot == selected,
                            onClick = { onSelect(slot); open = false },
                            label = {
                                Column {
                                    Text(names.getValue(slot))
                                    if (slot.startTime.isNotBlank() && slot.endTime.isNotBlank()) {
                                        Text("${slot.startTime} – ${slot.endTime}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        )
                    }
                }
            },
            confirmButton = {
                AppDialogSecondaryButton(onClick = { open = false }) {
                    Text(stringResource(R.string.schedule_action_cancel))
                }
            },
        )
    }
}
