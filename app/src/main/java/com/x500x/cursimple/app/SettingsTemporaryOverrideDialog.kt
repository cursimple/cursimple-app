package com.x500x.cursimple.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverrideType
import com.x500x.cursimple.core.kernel.model.makeUpNodeRange
import com.x500x.cursimple.core.kernel.model.resolveTemporaryScheduleSourceDate
import com.x500x.cursimple.core.kernel.model.weekdayLabel
import java.time.LocalDate
import java.util.UUID
import com.x500x.cursimple.feature.schedule.CalendarMonthPicker

/** 临时调课的查看与编辑。 */

/** 两种规则各占一个 tab：表单和下面的规则列表都只属于当前 tab。 */
private enum class TemporaryOverrideTab { MakeUp, CancelCourse }

/** 调课覆盖整天，还是只覆盖指定的几节。 */
private enum class MakeUpScope { WholeDay, Nodes }

/** 需要二次确认的动作。 */
private sealed interface OverrideConfirm {
    data class Remove(val id: String) : OverrideConfirm

    data object Clear : OverrideConfirm

    /** 表单里还有没添加的草稿就想关闭。 */
    data object DiscardDraft : OverrideConfirm
}

private const val MAX_NODE = 32

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TemporaryScheduleOverridesDialog(
    overrides: List<TemporaryScheduleOverride>,
    onAdd: (TemporaryScheduleOverride) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(TemporaryOverrideTab.MakeUp) }

    // 日期一律从「未选择」起步：默认填今天会让人一点添加就落下一条自己没挑过的规则
    var makeUpTargetDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var makeUpSourceDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var makeUpScope by rememberSaveable { mutableStateOf(MakeUpScope.WholeDay) }
    var makeUpStartText by rememberSaveable { mutableStateOf("") }
    var makeUpEndText by rememberSaveable { mutableStateOf("") }

    var cancelTargetDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var cancelStartText by rememberSaveable { mutableStateOf("") }
    var cancelEndText by rememberSaveable { mutableStateOf("") }

    var pickDateFor by rememberSaveable { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf<OverrideConfirm?>(null) }

    val makeUpNodes = nodeRangeOf(makeUpStartText, makeUpEndText)
    val cancelNodes = nodeRangeOf(cancelStartText, cancelEndText)

    fun resetMakeUpDraft() {
        makeUpTargetDate = null
        makeUpSourceDate = null
        makeUpScope = MakeUpScope.WholeDay
        makeUpStartText = ""
        makeUpEndText = ""
    }

    fun resetCancelDraft() {
        cancelTargetDate = null
        cancelStartText = ""
        cancelEndText = ""
    }

    val makeUpDraftDirty = makeUpTargetDate != null || makeUpSourceDate != null ||
        makeUpStartText.isNotBlank() || makeUpEndText.isNotBlank()
    val cancelDraftDirty = cancelTargetDate != null ||
        cancelStartText.isNotBlank() || cancelEndText.isNotBlank()
    val draftDirty = makeUpDraftDirty || cancelDraftDirty

    // 调课日和来源日相同等于什么都没调，挡在添加之前，免得列表里堆出一条没有效果的规则
    val makeUpDatesUsable = makeUpTargetDate != null &&
        makeUpSourceDate != null &&
        makeUpTargetDate != makeUpSourceDate
    val makeUpNodesUsable = makeUpScope == MakeUpScope.WholeDay || makeUpNodes != null
    val canAddMakeUp = makeUpDatesUsable && makeUpNodesUsable
    val canAddCancel = cancelTargetDate != null && cancelNodes != null

    val visibleRules = overrides.filter {
        when (tab) {
            TemporaryOverrideTab.MakeUp -> it.type == TemporaryScheduleOverrideType.MakeUp
            TemporaryOverrideTab.CancelCourse -> it.type == TemporaryScheduleOverrideType.CancelCourse
        }
    }

    fun requestDismiss() {
        if (draftDirty) confirming = OverrideConfirm.DiscardDraft else onDismiss()
    }

    AlertDialog(
        onDismissRequest = { requestDismiss() },
        title = { Text(stringResource(R.string.settings_dest_temporary_overrides)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TabRow(selectedTabIndex = tab.ordinal) {
                    Tab(
                        selected = tab == TemporaryOverrideTab.MakeUp,
                        onClick = { tab = TemporaryOverrideTab.MakeUp },
                        text = { Text(stringResource(R.string.settings_override_mode_makeup)) },
                    )
                    Tab(
                        selected = tab == TemporaryOverrideTab.CancelCourse,
                        onClick = { tab = TemporaryOverrideTab.CancelCourse },
                        text = { Text(stringResource(R.string.settings_override_mode_cancel)) },
                    )
                }

                when (tab) {
                    TemporaryOverrideTab.MakeUp -> {
                        OverrideFormSection(title = stringResource(R.string.settings_override_form_makeup)) {
                            DateChoiceButton(
                                label = stringResource(R.string.settings_override_target_makeup),
                                date = makeUpTargetDate,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { pickDateFor = PICK_MAKEUP_TARGET },
                            )
                            DateChoiceButton(
                                label = stringResource(R.string.settings_override_source_day),
                                date = makeUpSourceDate,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { pickDateFor = PICK_MAKEUP_SOURCE },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = makeUpScope == MakeUpScope.WholeDay,
                                    onClick = { makeUpScope = MakeUpScope.WholeDay },
                                    label = { Text(stringResource(R.string.settings_override_scope_whole_day)) },
                                )
                                FilterChip(
                                    selected = makeUpScope == MakeUpScope.Nodes,
                                    onClick = { makeUpScope = MakeUpScope.Nodes },
                                    label = { Text(stringResource(R.string.settings_override_scope_nodes)) },
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
                                text = makeUpHintText(
                                    targetDate = makeUpTargetDate,
                                    sourceDate = makeUpSourceDate,
                                    scope = makeUpScope,
                                    nodes = makeUpNodes,
                                ),
                                isError = !canAddMakeUp,
                            )
                            Button(
                                onClick = {
                                    val target = makeUpTargetDate ?: return@Button
                                    val source = makeUpSourceDate ?: return@Button
                                    onAdd(
                                        TemporaryScheduleOverride(
                                            id = UUID.randomUUID().toString(),
                                            type = TemporaryScheduleOverrideType.MakeUp,
                                            targetDate = target.toString(),
                                            sourceDate = source.toString(),
                                            makeUpStartNode = makeUpNodes
                                                ?.first
                                                ?.takeIf { makeUpScope == MakeUpScope.Nodes },
                                            makeUpEndNode = makeUpNodes
                                                ?.last
                                                ?.takeIf { makeUpScope == MakeUpScope.Nodes },
                                        ),
                                    )
                                    resetMakeUpDraft()
                                },
                                enabled = canAddMakeUp,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.settings_override_add))
                            }
                        }
                    }

                    TemporaryOverrideTab.CancelCourse -> {
                        OverrideFormSection(title = stringResource(R.string.settings_override_form_cancel)) {
                            DateChoiceButton(
                                label = stringResource(R.string.settings_override_target_cancel),
                                date = cancelTargetDate,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { pickDateFor = PICK_CANCEL_TARGET },
                            )
                            NodeRangeFields(
                                startText = cancelStartText,
                                endText = cancelEndText,
                                startLabel = stringResource(R.string.settings_override_cancel_start),
                                endLabel = stringResource(R.string.settings_override_cancel_end),
                                valid = cancelNodes != null,
                                onStartChange = { cancelStartText = it },
                                onEndChange = { cancelEndText = it },
                            )
                            FormHint(
                                text = cancelHintText(cancelTargetDate, cancelNodes),
                                isError = !canAddCancel,
                            )
                            Button(
                                onClick = {
                                    val target = cancelTargetDate ?: return@Button
                                    val nodes = cancelNodes ?: return@Button
                                    onAdd(
                                        TemporaryScheduleOverride(
                                            id = UUID.randomUUID().toString(),
                                            type = TemporaryScheduleOverrideType.CancelCourse,
                                            targetDate = target.toString(),
                                            cancelStartNode = nodes.first,
                                            cancelEndNode = nodes.last,
                                        ),
                                    )
                                    resetCancelDraft()
                                },
                                enabled = canAddCancel,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.settings_override_add_cancel))
                            }
                        }
                    }
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
                        TemporaryOverrideRuleRow(
                            rule = rule,
                            onRemove = { confirming = OverrideConfirm.Remove(rule.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { requestDismiss() }) { Text(stringResource(R.string.settings_done)) }
        },
        dismissButton = if (overrides.isNotEmpty()) {
            {
                TextButton(onClick = { confirming = OverrideConfirm.Clear }) {
                    Text(stringResource(R.string.settings_clear_all))
                }
            }
        } else null,
    )

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
                    is OverrideConfirm.Remove -> onRemove(pending.id)
                    OverrideConfirm.Clear -> onClear()
                    OverrideConfirm.DiscardDraft -> {
                        resetMakeUpDraft()
                        resetCancelDraft()
                        onDismiss()
                    }
                }
                confirming = null
            },
        )
    }
}

private const val PICK_MAKEUP_TARGET = "makeup_target"
private const val PICK_MAKEUP_SOURCE = "makeup_source"
private const val PICK_CANCEL_TARGET = "cancel_target"

/** 表单区块：给当前 tab 的输入项一个明确的边框，和下面的规则列表分开。 */
@Composable
private fun OverrideFormSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

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
private fun FormHint(text: String, isError: Boolean) {
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
): String = when {
    targetDate == null || sourceDate == null -> stringResource(R.string.settings_override_pick_dates)
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
private fun cancelHintText(targetDate: LocalDate?, nodes: NodeRange?): String = when {
    targetDate == null -> stringResource(R.string.settings_override_pick_cancel_date)
    nodes == null -> stringResource(R.string.settings_override_cancel_invalid)
    else -> stringResource(
        R.string.settings_override_cancel_hint,
        formatLongDate(targetDate),
        nodes.first.toString(),
        nodes.last.toString(),
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
            TextButton(onClick = onConfirm) { Text(stringResource(actionRes)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

/** 日期选择入口；[date] 为空时显示「未选择」，不拿今天冒充用户的选择。 */
@Composable
internal fun DateChoiceButton(
    label: String,
    date: LocalDate?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onClick,
    ) {
        Text("$label  ${date?.let(::formatShortDate) ?: stringResource(R.string.settings_override_date_unset)}")
    }
}

@Composable
internal fun TemporaryOverrideRuleRow(
    rule: TemporaryScheduleOverride,
    onRemove: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatOverrideRange(rule),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatOverrideSource(rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.settings_delete))
            }
        }
    }
}

@Composable
internal fun SettingsDatePickerDialog(
    initial: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // 不用 M3 的 DatePicker：它的星期表头取自系统 narrow 名字，中文环境下七列全是「星」
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
            TextButton(onClick = { onConfirm(selected) }) {
                Text(stringResource(R.string.settings_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
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
internal fun formatOverrideSource(rule: TemporaryScheduleOverride): String {
    if (rule.type == TemporaryScheduleOverrideType.CancelCourse) {
        val start = rule.cancelStartNode
        val end = rule.cancelEndNode ?: start
        return if (start != null && end != null) {
            stringResource(R.string.settings_override_source_cancel, start, end)
        } else {
            stringResource(R.string.settings_override_source_cancel_invalid)
        }
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

/** 起止节次；两端都填了且落在 1..[MAX_NODE] 内才成立，顺序写反时自动归位。 */
internal data class NodeRange(val first: Int, val last: Int)

internal fun nodeRangeOf(startText: String, endText: String): NodeRange? {
    val start = startText.toIntOrNull() ?: return null
    val end = endText.toIntOrNull() ?: return null
    if (start !in 1..MAX_NODE || end !in 1..MAX_NODE) return null
    return NodeRange(minOf(start, end), maxOf(start, end))
}

internal fun formatShortDate(date: LocalDate): String =
    "${date.monthValue}/${date.dayOfMonth}"

internal fun formatLongDate(date: LocalDate): String =
    "${formatShortDate(date)} ${weekdayLabel(date.dayOfWeek.value)}"
