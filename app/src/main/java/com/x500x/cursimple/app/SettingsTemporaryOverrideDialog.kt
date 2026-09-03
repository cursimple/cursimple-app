package com.x500x.cursimple.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverrideType
import com.x500x.cursimple.core.kernel.model.resolveTemporaryScheduleSourceDate
import com.x500x.cursimple.core.kernel.model.weekdayLabel
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.UUID
import com.x500x.cursimple.core.kernel.time.datePickerMillisToLocalDate
import com.x500x.cursimple.core.kernel.time.toDatePickerMillis

/** 临时调课的查看与编辑。 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TemporaryScheduleOverridesDialog(
    overrides: List<TemporaryScheduleOverride>,
    onAdd: (TemporaryScheduleOverride) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    var mode by rememberSaveable { mutableStateOf(TemporaryOverrideDialogMode.MakeUp) }
    var targetDate by rememberSaveable { mutableStateOf(today) }
    var sourceDate by rememberSaveable { mutableStateOf(today) }
    var cancelStartNodeText by rememberSaveable { mutableStateOf("1") }
    var cancelEndNodeText by rememberSaveable { mutableStateOf("1") }
    var pickTargetDate by rememberSaveable { mutableStateOf(false) }
    var pickSourceDate by rememberSaveable { mutableStateOf(false) }
    val cancelStartNode = cancelStartNodeText.toIntOrNull()
    val cancelEndNode = cancelEndNodeText.toIntOrNull()
    val canAddCancellation = cancelStartNode != null &&
        cancelEndNode != null &&
        cancelStartNode in 1..32 &&
        cancelEndNode in cancelStartNode..32

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_dest_temporary_overrides)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverrideModeButton(
                        label = stringResource(R.string.settings_override_mode_makeup),
                        selected = mode == TemporaryOverrideDialogMode.MakeUp,
                        modifier = Modifier.weight(1f),
                        onClick = { mode = TemporaryOverrideDialogMode.MakeUp },
                    )
                    OverrideModeButton(
                        label = stringResource(R.string.settings_override_mode_cancel),
                        selected = mode == TemporaryOverrideDialogMode.CancelCourse,
                        modifier = Modifier.weight(1f),
                        onClick = { mode = TemporaryOverrideDialogMode.CancelCourse },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateChoiceButton(
                        label = if (mode == TemporaryOverrideDialogMode.MakeUp) {
                            stringResource(R.string.settings_override_target_makeup)
                        } else {
                            stringResource(R.string.settings_override_target_cancel)
                        },
                        date = targetDate,
                        modifier = Modifier.weight(1f),
                        onClick = { pickTargetDate = true },
                    )
                    if (mode == TemporaryOverrideDialogMode.MakeUp) {
                        DateChoiceButton(
                            label = stringResource(R.string.settings_override_source_day),
                            date = sourceDate,
                            modifier = Modifier.weight(1f),
                            onClick = { pickSourceDate = true },
                        )
                    }
                }
                if (mode == TemporaryOverrideDialogMode.MakeUp) {
                    Text(
                        text = stringResource(
                            R.string.settings_override_makeup_hint,
                            formatLongDate(targetDate),
                            formatLongDate(sourceDate),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cancelStartNodeText,
                            onValueChange = { cancelStartNodeText = it.filter(Char::isDigit).take(2) },
                            label = { Text(stringResource(R.string.settings_override_cancel_start)) },
                            singleLine = true,
                            isError = cancelStartNodeText.isNotBlank() && !canAddCancellation,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = cancelEndNodeText,
                            onValueChange = { cancelEndNodeText = it.filter(Char::isDigit).take(2) },
                            label = { Text(stringResource(R.string.settings_override_cancel_end)) },
                            singleLine = true,
                            isError = cancelEndNodeText.isNotBlank() && !canAddCancellation,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = if (canAddCancellation) {
                            stringResource(
                                R.string.settings_override_cancel_hint,
                                formatLongDate(targetDate),
                                cancelStartNode.toString(),
                                cancelEndNode.toString(),
                            )
                        } else {
                            stringResource(R.string.settings_override_cancel_invalid)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (canAddCancellation) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = {
                        if (mode == TemporaryOverrideDialogMode.MakeUp) {
                            onAdd(
                                TemporaryScheduleOverride(
                                    id = UUID.randomUUID().toString(),
                                    type = TemporaryScheduleOverrideType.MakeUp,
                                    targetDate = targetDate.toString(),
                                    sourceDate = sourceDate.toString(),
                                ),
                            )
                        } else if (canAddCancellation) {
                            onAdd(
                                TemporaryScheduleOverride(
                                    id = UUID.randomUUID().toString(),
                                    type = TemporaryScheduleOverrideType.CancelCourse,
                                    targetDate = targetDate.toString(),
                                    cancelStartNode = cancelStartNode,
                                    cancelEndNode = cancelEndNode,
                                ),
                            )
                        }
                    },
                    enabled = mode == TemporaryOverrideDialogMode.MakeUp || canAddCancellation,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (mode == TemporaryOverrideDialogMode.MakeUp) {
                            stringResource(R.string.settings_override_add)
                        } else {
                            stringResource(R.string.settings_override_add_cancel)
                        },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (overrides.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_override_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    overrides.forEach { rule ->
                        TemporaryOverrideRuleRow(
                            rule = rule,
                            onRemove = { onRemove(rule.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) }
        },
        dismissButton = if (overrides.isNotEmpty()) {
            {
                TextButton(onClick = onClear) { Text(stringResource(R.string.settings_clear_all)) }
            }
        } else null,
    )

    if (pickTargetDate) {
        SettingsDatePickerDialog(
            initial = targetDate,
            onConfirm = {
                targetDate = it
                pickTargetDate = false
            },
            onDismiss = { pickTargetDate = false },
        )
    }
    if (pickSourceDate) {
        SettingsDatePickerDialog(
            initial = sourceDate,
            onConfirm = {
                sourceDate = it
                pickSourceDate = false
            },
            onDismiss = { pickSourceDate = false },
        )
    }
}

@Composable
internal fun OverrideModeButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(label)
    }
}

@Composable
internal fun DateChoiceButton(
    label: String,
    date: LocalDate,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onClick,
    ) {
        Text("$label ${formatShortDate(date)}")
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
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsDatePickerDialog(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toDatePickerMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(datePickerMillisToLocalDate(millis))
                    }
                },
            ) {
                Text(stringResource(R.string.settings_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
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
    return if (source != null) {
        stringResource(R.string.settings_override_source_makeup, formatLongDate(source))
    } else {
        stringResource(R.string.settings_override_source_invalid)
    }
}

internal fun formatShortDate(date: LocalDate): String =
    "${date.monthValue}/${date.dayOfMonth}"

internal fun formatLongDate(date: LocalDate): String =
    "${formatShortDate(date)} ${weekdayLabel(date.dayOfWeek.value)}"

private enum class TemporaryOverrideDialogMode { MakeUp, CancelCourse }
