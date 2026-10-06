package com.x500x.cursimple.app

import com.x500x.cursimple.core.kernel.model.moveToNodeRange
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import com.x500x.cursimple.feature.plugin.ui.AppFilterChip
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.core.kernel.model.CancelCoursePlan
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverrideType
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.slotsCovering
import com.x500x.cursimple.core.data.widget.slotLabelText
import androidx.compose.ui.platform.LocalContext
import com.x500x.cursimple.core.kernel.model.coursesScheduledOn
import com.x500x.cursimple.core.kernel.model.isCourseTemporarilyCancelled
import com.x500x.cursimple.core.kernel.model.planCancelCourse
import com.x500x.cursimple.core.kernel.model.planRestoreCourse
import com.x500x.cursimple.core.kernel.model.visibleScheduleCourses
import com.x500x.cursimple.core.kernel.model.makeUpNodeRange
import com.x500x.cursimple.core.kernel.model.resolveTemporaryScheduleSourceDate
import com.x500x.cursimple.core.kernel.model.weekdayLabel
import java.time.LocalDate
import java.util.UUID
import com.x500x.cursimple.feature.schedule.CalendarMonthPicker

/** Each rule tab owns its form and list. */
private enum class TemporaryOverrideTab { MakeUp, CancelCourse }

/** Whether a swap covers a whole day or selected periods. */
private enum class MakeUpScope { WholeDay, Nodes }

private sealed interface OverrideConfirm {
    data class Remove(val id: String) : OverrideConfirm

    data object Clear : OverrideConfirm

    data object DiscardDraft : OverrideConfirm
}

private const val MAX_NODE = 32

/**
 * Inline override editor; selecting a rule loads it into the form, while removal affects only
 * that rule.
 */
@Composable
internal fun TemporaryOverrideSettingsSection(
    overrides: List<TemporaryScheduleOverride>,
    onUpsert: (TemporaryScheduleOverride) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onOpenCourseSwap: (LocalDate, LocalDate) -> Unit = { _, _ -> },
    courseTitleOf: (String) -> String? = { null },
    /** All imported and manual courses for the selected-day cancellation list. */
    courses: List<CourseItem> = emptyList(),
    /** Format periods using timing-profile labels, matching the timetable header. */
    timingProfile: TermTimingProfile? = null,
    holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
    termStartDate: LocalDate? = null,
    onApplyCancelPlan: (CancelCoursePlan) -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(TemporaryOverrideTab.MakeUp) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    var makeUpTargetDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var makeUpSourceDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var makeUpScope by rememberSaveable { mutableStateOf(MakeUpScope.WholeDay) }
    var makeUpStartText by rememberSaveable { mutableStateOf("") }
    var makeUpEndText by rememberSaveable { mutableStateOf("") }

    var cancelTargetDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }

    var pickDateFor by rememberSaveable { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf<OverrideConfirm?>(null) }

    val makeUpNodes = nodeRangeOf(makeUpStartText, makeUpEndText)

    fun resetDraft() {
        editingId = null
        makeUpTargetDate = null
        makeUpSourceDate = null
        makeUpScope = MakeUpScope.WholeDay
        makeUpStartText = ""
        makeUpEndText = ""
        cancelTargetDate = null
    }

    fun loadForEdit(rule: TemporaryScheduleOverride) {
        resetDraft()
        val target = parseIsoDate(rule.targetDate) ?: parseIsoDate(rule.startDate)
        if (rule.type == TemporaryScheduleOverrideType.CancelCourse) {
            tab = TemporaryOverrideTab.CancelCourse
            cancelTargetDate = target
        } else {
            editingId = rule.id
            tab = TemporaryOverrideTab.MakeUp
            makeUpTargetDate = target
            makeUpSourceDate = target?.let { resolveTemporaryScheduleSourceDate(it, listOf(rule)) }
            val nodes = rule.makeUpNodeRange()
            if (nodes != null) {
                makeUpScope = MakeUpScope.Nodes
                makeUpStartText = nodes.first.toString()
                makeUpEndText = nodes.last.toString()
            }
        }
    }

    val makeUpUsable = makeUpTargetDate != null &&
        makeUpSourceDate != null &&
        makeUpTargetDate != makeUpSourceDate &&
        (makeUpScope == MakeUpScope.WholeDay || makeUpNodes != null)

    val visibleRules = overrides.filter {
        when (tab) {
            TemporaryOverrideTab.MakeUp -> it.type == TemporaryScheduleOverrideType.MakeUp
            TemporaryOverrideTab.CancelCourse -> it.type == TemporaryScheduleOverrideType.CancelCourse
        }
    }

    // Switch the form and rule list together using shared settings styles.
    OverrideModeSwitch(
        selected = tab,
        onSelect = { picked ->
            if (tab != picked) {
                tab = picked
                editingId = null
            }
        },
    )

    Text(
        text = if (editingId != null) {
            stringResource(R.string.settings_override_form_editing)
        } else if (tab == TemporaryOverrideTab.MakeUp) {
            stringResource(R.string.settings_override_form_makeup)
        } else {
            stringResource(R.string.settings_override_form_cancel)
        },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (tab == TemporaryOverrideTab.MakeUp) {
        SettingsActionRow(
            icon = Icons.Rounded.EventRepeat,
            title = stringResource(R.string.settings_override_target_makeup),
            subtitle = makeUpTargetDate?.let(::formatLongDate)
                ?: stringResource(R.string.settings_override_date_unset),
            onClick = { pickDateFor = PICK_MAKEUP_TARGET },
        )
        SettingsActionRow(
            icon = Icons.Rounded.Schedule,
            title = stringResource(R.string.settings_override_source_day),
            subtitle = makeUpSourceDate?.let(::formatLongDate)
                ?: stringResource(R.string.settings_override_date_unset),
            onClick = { pickDateFor = PICK_MAKEUP_SOURCE },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppFilterChip(
                selected = makeUpScope == MakeUpScope.WholeDay,
                onClick = { makeUpScope = MakeUpScope.WholeDay },
                label = { Text(stringResource(R.string.settings_override_scope_whole_day)) },
                modifier = Modifier.weight(1f),
            )
            AppFilterChip(
                selected = makeUpScope == MakeUpScope.Nodes,
                onClick = { makeUpScope = MakeUpScope.Nodes },
                label = { Text(stringResource(R.string.settings_override_scope_nodes)) },
                modifier = Modifier.weight(1f),
            )
        }
        if (makeUpScope == MakeUpScope.Nodes) {
            NodeRangeFields(
                startText = makeUpStartText,
                endText = makeUpEndText,
                startLabel = stringResource(R.string.settings_override_makeup_start),
                endLabel = stringResource(R.string.settings_override_makeup_end),
                valid = makeUpNodes != null,
                onStartChange = { makeUpStartText = it },
                onEndChange = { makeUpEndText = it },
            )
        }
        FormHint(
            text = makeUpHintText(makeUpTargetDate, makeUpSourceDate, makeUpScope, makeUpNodes),
            isError = makeUpTargetDate != null && makeUpSourceDate != null && !makeUpUsable,
        )
        if (makeUpTargetDate != null && makeUpSourceDate != null && makeUpTargetDate != makeUpSourceDate) {
            SettingsActionRow(
                icon = Icons.Rounded.SwapHoriz,
                title = stringResource(R.string.settings_override_open_swap_title),
                subtitle = stringResource(R.string.settings_override_open_swap_subtitle),
                onClick = { onOpenCourseSwap(makeUpTargetDate!!, makeUpSourceDate!!) },
            )
        }
        OverrideSubmitRow(
            editing = editingId != null,
            enabled = makeUpUsable,
            addLabel = stringResource(R.string.settings_override_add),
            onSubmit = {
                val target = makeUpTargetDate ?: return@OverrideSubmitRow
                val source = makeUpSourceDate ?: return@OverrideSubmitRow
                onUpsert(
                    TemporaryScheduleOverride(
                        id = editingId ?: UUID.randomUUID().toString(),
                        type = TemporaryScheduleOverrideType.MakeUp,
                        targetDate = target.toString(),
                        sourceDate = source.toString(),
                        makeUpStartNode = makeUpNodes?.first?.takeIf { makeUpScope == MakeUpScope.Nodes },
                        makeUpEndNode = makeUpNodes?.last?.takeIf { makeUpScope == MakeUpScope.Nodes },
                    ),
                )
                resetDraft()
            },
            onCancelEdit = { resetDraft() },
        )
    } else {
        SettingsActionRow(
            icon = Icons.Rounded.EventBusy,
            title = stringResource(R.string.settings_override_target_cancel),
            subtitle = cancelTargetDate?.let(::formatLongDate)
                ?: stringResource(R.string.settings_override_date_unset),
            onClick = { pickDateFor = PICK_CANCEL_TARGET },
        )
        CancelDayCourseList(
            date = cancelTargetDate,
            courses = courses,
            timingProfile = timingProfile,
            overrides = overrides,
            holidayCalendar = holidayCalendar,
            termStartDate = termStartDate,
            onApply = onApplyCancelPlan,
        )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Text(
        text = stringResource(R.string.settings_override_list_title, visibleRules.size),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (visibleRules.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_override_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        visibleRules.forEach { rule ->
            SettingsActionRow(
                icon = Icons.Rounded.Schedule,
                title = formatOverrideRange(rule),
                subtitle = formatOverrideSource(rule, courseTitleOf),
                onClick = { loadForEdit(rule) },
                trailing = {
                    IconButton(onClick = { confirming = OverrideConfirm.Remove(rule.id) }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.settings_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        }
        SettingsActionRow(
            icon = Icons.Rounded.DeleteSweep,
            title = stringResource(R.string.settings_clear_all),
            subtitle = stringResource(R.string.settings_override_clear_body),
            onClick = { confirming = OverrideConfirm.Clear },
        )
    }

    // List individual drag-based moves separately from day and period swaps.
    if (tab == TemporaryOverrideTab.MakeUp) {
        MovedCoursesList(
            overrides = overrides,
            courseTitleOf = courseTitleOf,
            onRemove = { id -> confirming = OverrideConfirm.Remove(id) },
        )
    }

    pickDateFor?.let { target ->
        val initial = when (target) {
            PICK_MAKEUP_TARGET -> makeUpTargetDate
            PICK_MAKEUP_SOURCE -> makeUpSourceDate
            else -> cancelTargetDate
        }
        SettingsDatePickerDialog(
            initial = initial,
            onConfirm = { picked ->
                when (target) {
                    PICK_MAKEUP_TARGET -> makeUpTargetDate = picked
                    PICK_MAKEUP_SOURCE -> makeUpSourceDate = picked
                    else -> cancelTargetDate = picked
                }
                pickDateFor = null
            },
            onDismiss = { pickDateFor = null },
        )
    }

    confirming?.let { pending ->
        OverrideConfirmDialog(
            pending = pending,
            onDismiss = { confirming = null },
            onConfirm = {
                when (pending) {
                    is OverrideConfirm.Remove -> {
                        if (editingId == pending.id) resetDraft()
                        onRemove(pending.id)
                    }
                    OverrideConfirm.Clear -> {
                        resetDraft()
                        onClear()
                    }
                    OverrideConfirm.DiscardDraft -> resetDraft()
                }
                confirming = null
            },
        )
    }
}

/** Cancel or restore courses by ID so simultaneous courses remain independent. */
@Composable
private fun CancelDayCourseList(
    date: LocalDate?,
    courses: List<CourseItem>,
    timingProfile: TermTimingProfile?,
    overrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings,
    termStartDate: LocalDate?,
    onApply: (CancelCoursePlan) -> Unit,
) {
    val context = LocalContext.current
    if (date == null) {
        FormHint(text = stringResource(R.string.settings_override_cancel_pick_date), isError = false)
        return
    }
    val dayCourses = remember(date, courses, overrides, holidayCalendar, termStartDate) {
        coursesScheduledOn(
            date = date,
            courses = courses.visibleScheduleCourses(),
            overrides = overrides,
            holidayCalendar = holidayCalendar,
            termStartDate = termStartDate,
            includeCancelled = true,
        )
    }
    if (dayCourses.isEmpty()) {
        FormHint(text = stringResource(R.string.settings_override_cancel_no_course, formatLongDate(date)), isError = false)
        return
    }
    dayCourses.forEach { course ->
        val cancelled = isCourseTemporarilyCancelled(date, course, overrides)
        // Legacy multi-day period rules must be removed as a whole.
        val restorePlan = if (cancelled) {
            planRestoreCourse(date, course, dayCourses, overrides, newId = { UUID.randomUUID().toString() })
        } else {
            null
        }
        // Prefer timing-profile names; internal period numbers may differ from visible labels.
        val covering = timingProfile?.slotsCovering(course.time.startNode, course.time.endNode).orEmpty()
        val period = context.slotLabelText(covering) ?: stringResource(
            R.string.settings_override_cancel_course_nodes,
            nodeSpanText(course.time.startNode, course.time.endNode),
        )
        val time = covering.takeIf { it.isNotEmpty() }
            ?.let { "${it.first().value.startTime}–${it.last().value.endTime}" }
        val detail = listOfNotNull(
            period,
            time,
            course.location.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        val toggle: () -> Unit = {
            when {
                !cancelled -> onApply(planCancelCourse(date, course, newId = { UUID.randomUUID().toString() }))
                restorePlan != null -> onApply(restorePlan)
            }
        }
        SettingsActionRow(
            icon = if (cancelled) Icons.Rounded.EventBusy else Icons.Rounded.Schedule,
            title = course.title,
            subtitle = when {
                !cancelled -> detail
                restorePlan == null -> stringResource(R.string.settings_override_cancel_state_legacy, detail)
                else -> stringResource(R.string.settings_override_cancel_state, detail)
            },
            onClick = toggle,
            trailing = {
                AppOutlinedButton(onClick = toggle, enabled = !cancelled || restorePlan != null) {
                    Text(
                        stringResource(
                            if (cancelled) R.string.settings_override_cancel_restore else R.string.settings_override_cancel_action,
                        ),
                    )
                }
            },
        )
    }
}

/** Group individual moves by destination date and allow per-course reversal. */
@Composable
private fun MovedCoursesList(
    overrides: List<TemporaryScheduleOverride>,
    courseTitleOf: (String) -> String?,
    onRemove: (String) -> Unit,
) {
    val moves = overrides.filter { it.type == TemporaryScheduleOverrideType.MoveCourse }
    if (moves.isEmpty()) return
    val byDay = moves
        .groupBy { parseIsoDate(it.targetDate) }
        .toSortedMap(compareBy(nullsLast()) { it })
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        text = stringResource(R.string.settings_override_moves_title, moves.size),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    byDay.forEach { (day, rules) ->
        val key = day?.toString() ?: "invalid"
        val open = expanded == key
        SettingsActionRow(
            icon = Icons.Rounded.SwapHoriz,
            title = day?.let(::formatShortDate) ?: stringResource(R.string.settings_invalid_date),
            subtitle = stringResource(R.string.settings_override_moves_day, rules.size),
            onClick = { expanded = if (open) null else key },
            trailing = {
                Icon(
                    imageVector = if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )
        if (open) {
            rules.sortedBy { it.moveToStartNode ?: Int.MAX_VALUE }.forEach { rule ->
                MovedCourseRow(rule = rule, courseTitleOf = courseTitleOf, onRemove = { onRemove(rule.id) })
            }
        }
    }
}

@Composable
private fun MovedCourseRow(
    rule: TemporaryScheduleOverride,
    courseTitleOf: (String) -> String?,
    onRemove: () -> Unit,
) {
    val title = rule.moveCourseId?.let(courseTitleOf)
        ?: stringResource(R.string.settings_override_move_unknown_course)
    val from = parseIsoDate(rule.sourceDate)
    val nodes = rule.moveToNodeRange()
    val detail = if (from != null && nodes != null) {
        stringResource(R.string.settings_override_move_item, formatShortDate(from), nodes.first, nodes.last)
    } else {
        stringResource(R.string.settings_override_source_invalid)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.settings_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Use the settings row's shared colors and rounding for rule tabs. */
@Composable
private fun OverrideModeSwitch(
    selected: TemporaryOverrideTab,
    onSelect: (TemporaryOverrideTab) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TemporaryOverrideTab.entries.forEach { mode ->
                val active = mode == selected
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(mode) },
                    color = if (active) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = stringResource(
                            when (mode) {
                                TemporaryOverrideTab.MakeUp -> R.string.settings_override_mode_makeup
                                TemporaryOverrideTab.CancelCourse -> R.string.settings_override_mode_cancel
                            },
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/** Offer cancellation while editing an existing rule. */
@Composable
private fun OverrideSubmitRow(
    editing: Boolean,
    enabled: Boolean,
    addLabel: String,
    onSubmit: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onSubmit,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                if (editing) stringResource(R.string.settings_override_save_edit) else addLabel,
            )
        }
        if (editing) {
            AppOutlinedButton(onClick = onCancelEdit) {
                Text(stringResource(R.string.settings_override_cancel_edit))
            }
        }
    }
}

private const val PICK_MAKEUP_TARGET = "makeup_target"
private const val PICK_MAKEUP_SOURCE = "makeup_source"
private const val PICK_CANCEL_TARGET = "cancel_target"

@Composable
private fun NodeRangeFields(
    startText: String,
    endText: String,
    startLabel: String,
    endLabel: String,
    valid: Boolean,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
) {
    val filled = startText.isNotBlank() || endText.isNotBlank()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = startText,
            onValueChange = { onStartChange(it.filter(Char::isDigit).take(2)) },
            label = { Text(startLabel) },
            singleLine = true,
            isError = filled && !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = endText,
            onValueChange = { onEndChange(it.filter(Char::isDigit).take(2)) },
            label = { Text(endLabel) },
            singleLine = true,
            isError = filled && !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FormHint(text: String?, isError: Boolean) {
    if (text.isNullOrBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun makeUpHintText(
    targetDate: LocalDate?,
    sourceDate: LocalDate?,
    scope: MakeUpScope,
    nodes: NodeRange?,
): String? = when {
    // Incomplete fields are not validation errors.
    targetDate == null || sourceDate == null -> null
    targetDate == sourceDate -> stringResource(R.string.settings_override_same_day)
    scope == MakeUpScope.Nodes && nodes == null -> stringResource(R.string.settings_override_node_invalid)
    scope == MakeUpScope.Nodes -> stringResource(
        R.string.settings_override_makeup_hint_nodes,
        formatLongDate(targetDate),
        nodes!!.first,
        nodes.last,
        formatLongDate(sourceDate),
    )

    else -> stringResource(
        R.string.settings_override_makeup_hint,
        formatLongDate(targetDate),
        formatLongDate(sourceDate),
    )
}

@Composable
private fun OverrideConfirmDialog(
    pending: OverrideConfirm,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val (titleRes, bodyRes, actionRes) = when (pending) {
        is OverrideConfirm.Remove -> Triple(
            R.string.settings_override_remove_title,
            R.string.settings_override_remove_body,
            R.string.settings_delete,
        )

        OverrideConfirm.Clear -> Triple(
            R.string.settings_override_clear_title,
            R.string.settings_override_clear_body,
            R.string.settings_clear_all,
        )

        OverrideConfirm.DiscardDraft -> Triple(
            R.string.settings_override_discard_title,
            R.string.settings_override_discard_body,
            R.string.settings_override_discard_action,
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(bodyRes)) },
        confirmButton = {
            AppOutlinedButton(onClick = onConfirm) { Text(stringResource(actionRes)) }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

@Composable
internal fun DateChoiceButton(
    label: String,
    date: LocalDate?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AppOutlinedButton(
        modifier = modifier,
        onClick = onClick,
    ) {
        Text("$label  ${date?.let(::formatShortDate) ?: stringResource(R.string.settings_override_date_unset)}")
    }
}

@Composable
internal fun SettingsDatePickerDialog(
    initial: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // Use custom weekday labels; narrow system names can be ambiguous in some locales.
    val fallback = com.x500x.cursimple.core.kernel.time.BeijingTime.today()
    var selected by remember(initial) { mutableStateOf(initial ?: fallback) }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            CalendarMonthPicker(
                selected = selected,
                onSelect = { selected = it },
            )
        },
        confirmButton = {
            AppOutlinedButton(onClick = { onConfirm(selected) }) {
                Text(stringResource(R.string.settings_confirm))
            }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

@Composable
internal fun temporaryOverridesSubtitle(overrides: List<TemporaryScheduleOverride>): String {
    return when {
        overrides.isEmpty() -> stringResource(R.string.settings_not_set)
        overrides.size == 1 -> formatOverrideSummary(overrides.first())
        else -> stringResource(
            R.string.settings_override_subtitle_multi,
            overrides.size,
            formatOverrideSummary(overrides.last()),
        )
    }
}

@Composable
internal fun formatOverrideSummary(rule: TemporaryScheduleOverride): String {
    return "${formatOverrideRange(rule)} · ${formatOverrideSource(rule)}"
}

@Composable
internal fun formatOverrideRange(rule: TemporaryScheduleOverride): String {
    val target = parseIsoDate(rule.targetDate) ?: parseIsoDate(rule.startDate)
    return target?.let(::formatShortDate) ?: stringResource(R.string.settings_invalid_date)
}

@Composable
internal fun formatOverrideSource(
    rule: TemporaryScheduleOverride,
    courseTitleOf: (String) -> String? = { null },
): String {
    if (rule.type == TemporaryScheduleOverrideType.CancelCourse) {
        val start = rule.cancelStartNode
        val end = rule.cancelEndNode ?: start
        val title = rule.cancelCourseId?.let(courseTitleOf)
        return if (start != null && end != null && title != null) {
            stringResource(R.string.settings_override_source_cancel_course, title, nodeSpanText(start, end))
        } else if (start != null && end != null) {
            stringResource(R.string.settings_override_source_cancel, nodeSpanText(start, end))
        } else {
            stringResource(R.string.settings_override_source_cancel_invalid)
        }
    }
    if (rule.type == TemporaryScheduleOverrideType.MoveCourse) {
        val from = parseIsoDate(rule.sourceDate)
            ?: return stringResource(R.string.settings_override_source_invalid)
        return stringResource(R.string.settings_override_source_move, formatShortDate(from))
    }
    val target = parseIsoDate(rule.targetDate) ?: parseIsoDate(rule.startDate)
    val source = target?.let { resolveTemporaryScheduleSourceDate(it, listOf(rule)) }
        ?: return stringResource(R.string.settings_override_source_invalid)
    val nodes = rule.makeUpNodeRange()
        ?: return stringResource(R.string.settings_override_source_makeup, formatLongDate(source))
    return stringResource(
        R.string.settings_override_source_makeup_nodes,
        formatLongDate(source),
        nodes.first,
        nodes.last,
    )
}

internal data class NodeRange(val first: Int, val last: Int)

internal fun nodeRangeOf(startText: String, endText: String): NodeRange? {
    val start = startText.toIntOrNull() ?: return null
    val end = endText.toIntOrNull() ?: return null
    if (start !in 1..MAX_NODE || end !in 1..MAX_NODE) return null
    return NodeRange(minOf(start, end), maxOf(start, end))
}

internal fun nodeSpanText(start: Int, end: Int): String =
    if (start == end) "$start" else "$start-$end"

internal fun formatShortDate(date: LocalDate): String =
    "${date.monthValue}/${date.dayOfMonth}"

internal fun formatLongDate(date: LocalDate): String =
    "${formatShortDate(date)} ${weekdayLabel(date.dayOfWeek.value)}"
