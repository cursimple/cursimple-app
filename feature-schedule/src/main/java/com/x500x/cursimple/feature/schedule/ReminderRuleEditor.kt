package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.annotation.StringRes
import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.reminder.model.ReminderLabelAction
import com.x500x.cursimple.core.reminder.model.ReminderLabelActionType
import com.x500x.cursimple.core.reminder.model.ReminderLabelCondition
import com.x500x.cursimple.core.reminder.model.ReminderLabelPresence
import com.x500x.cursimple.core.reminder.model.ReminderRule

@Composable
internal fun ReminderRuleEditorDialog(
    rule: ReminderRule?,
    slotLabels: List<String>,
    onPickSystemRingtone: ((String?) -> Unit) -> Unit,
    onPickLocalAudio: ((String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String?, String, Boolean, Int, String?, List<ReminderLabelCondition>, List<ReminderLabelAction>) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val labels = slotLabels.ifEmpty { listOf("") }
    val defaultRuleName = stringResource(
        R.string.schedule_reminder_rule_default_name,
        (System.currentTimeMillis() % 1000).toInt(),
    )
    var name by rememberSaveable(rule?.ruleId) {
        mutableStateOf(rule?.displayName ?: defaultRuleName)
    }
    // Enablement is toggled from the list row; the editor keeps the current value.
    val enabled = rule?.enabled ?: true
    var confirmDelete by rememberSaveable(rule?.ruleId) { mutableStateOf(false) }
    var advanceText by rememberSaveable(rule?.ruleId) { mutableStateOf((rule?.advanceMinutes ?: 20).toString()) }
    var ringtoneUri by rememberSaveable(rule?.ruleId) { mutableStateOf(rule?.ringtoneUri) }
    var conditions by remember(rule?.ruleId) {
        mutableStateOf(
            rule?.labelConditions?.takeIf { it.isNotEmpty() }
                ?: listOf(ReminderLabelCondition(labels.first(), ReminderLabelPresence.Exists)),
        )
    }
    var actions by remember(rule?.ruleId) {
        mutableStateOf(
            rule?.labelActions?.takeIf { it.isNotEmpty() }
                ?: listOf(ReminderLabelAction(labels.first(), ReminderLabelActionType.Remind)),
        )
    }
    val advance = advanceText.toIntOrNull()
    val canSave = canSaveReminderLabelRule(
        displayName = name,
        advanceMinutes = advance,
        conditions = conditions,
        actions = actions,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (rule == null) R.string.schedule_reminder_rule_new_title else R.string.schedule_reminder_rule_edit_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.schedule_reminder_rule_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                AlarmEditorSection(stringResource(R.string.schedule_reminder_rule_section_when)) {
                    conditions.forEachIndexed { index, condition ->
                        ConditionRow(
                            condition = condition,
                            labels = labels,
                            onChange = { next ->
                                conditions = conditions.toMutableList().also { it[index] = next }
                            },
                            onDelete = { conditions = conditions.filterIndexed { i, _ -> i != index } },
                        )
                    }
                    AppOutlinedButton(
                        onClick = {
                            conditions += ReminderLabelCondition(labels.first(), ReminderLabelPresence.Exists)
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.schedule_reminder_rule_add_condition))
                    }
                }

                AlarmEditorSection(stringResource(R.string.schedule_reminder_rule_section_then)) {
                    actions.forEachIndexed { index, action ->
                        ActionRow(
                            action = action,
                            labels = labels,
                            onChange = { next ->
                                actions = actions.toMutableList().also { it[index] = next }
                            },
                            onDelete = { actions = actions.filterIndexed { i, _ -> i != index } },
                        )
                    }
                    AppOutlinedButton(
                        onClick = {
                            actions += ReminderLabelAction(labels.first(), ReminderLabelActionType.Remind)
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.schedule_reminder_rule_add_action))
                    }
                }

                AlarmEditorSection(stringResource(R.string.schedule_alarm_section_ring)) {
                    val advanceInvalid = advanceText.isNotBlank() && (advance == null || advance !in 0..720)
                    OutlinedTextField(
                        value = advanceText,
                        onValueChange = { advanceText = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.schedule_advance_minutes_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        isError = advanceInvalid,
                        supportingText = if (advanceInvalid) {
                            { Text(stringResource(R.string.schedule_advance_minutes_error)) }
                        } else {
                            null
                        },
                    )
                    AlarmRingtoneSelector(
                        ringtoneUri = ringtoneUri,
                        onUseDefault = { ringtoneUri = null },
                        onPickSystem = { onPickSystemRingtone { ringtoneUri = it } },
                        onPickLocal = { onPickLocalAudio { ringtoneUri = it } },
                    )
                }

                if (onDelete != null) {
                    ReminderDeleteButton(
                        label = stringResource(R.string.schedule_cd_delete_rule),
                        onClick = { confirmDelete = true },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        rule?.ruleId,
                        name.trim(),
                        enabled,
                        advance ?: 20,
                        ringtoneUri?.takeIf { it.isNotBlank() },
                        conditions,
                        actions,
                    )
                },
                enabled = canSave,
            ) {
                AppAtomicText(stringResource(R.string.schedule_action_save))
            }
        },
        dismissButton = { AppOutlinedButton(onClick = onDismiss) { AppAtomicText(stringResource(R.string.schedule_action_cancel)) } },
    )

    if (confirmDelete && onDelete != null) {
        ReminderDeleteConfirmDialog(
            name = name.ifBlank { rule?.displayName.orEmpty() },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

internal fun canSaveReminderLabelRule(
    displayName: String,
    advanceMinutes: Int?,
    conditions: List<ReminderLabelCondition>,
    actions: List<ReminderLabelAction>,
): Boolean =
    displayName.isNotBlank() &&
        advanceMinutes != null &&
        advanceMinutes in 0..720 &&
        conditions.all { it.slotLabel.isNotBlank() } &&
        actions.all { it.slotLabel.isNotBlank() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionRow(
    condition: ReminderLabelCondition,
    labels: List<String>,
    onChange: (ReminderLabelCondition) -> Unit,
    onDelete: () -> Unit,
) {
    RuleClauseLayout(
        deleteDescription = stringResource(R.string.schedule_reminder_rule_delete_condition),
        onDelete = onDelete,
        labelDropdown = { modifier ->
            LabelDropdown(
                label = condition.slotLabel,
                labels = labels,
                modifier = modifier,
                onSelected = { onChange(condition.copy(slotLabel = it)) },
            )
        },
        enumDropdown = { modifier ->
            EnumDropdown(
                value = condition.presence,
                options = ReminderLabelPresence.entries,
                label = stringResource(R.string.schedule_reminder_rule_condition_label),
                optionLabel = { stringResource(it.conditionLabelRes()) },
                modifier = modifier,
                onSelected = { onChange(condition.copy(presence = it)) },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionRow(
    action: ReminderLabelAction,
    labels: List<String>,
    onChange: (ReminderLabelAction) -> Unit,
    onDelete: () -> Unit,
) {
    RuleClauseLayout(
        deleteDescription = stringResource(R.string.schedule_reminder_rule_delete_action),
        onDelete = onDelete,
        labelDropdown = { modifier ->
            LabelDropdown(
                label = action.slotLabel,
                labels = labels,
                modifier = modifier,
                onSelected = { onChange(action.copy(slotLabel = it)) },
            )
        },
        enumDropdown = { modifier ->
            EnumDropdown(
                value = action.action,
                options = ReminderLabelActionType.entries,
                label = stringResource(R.string.schedule_reminder_rule_action_label),
                optionLabel = { stringResource(it.actionLabelRes()) },
                modifier = modifier,
                onSelected = { onChange(action.copy(action = it)) },
            )
        },
    )
}

/**
 * Stack selectors on narrow screens or large fonts when parallel layout would compress labels.
 */
@Composable
private fun RuleClauseLayout(
    deleteDescription: String,
    onDelete: () -> Unit,
    labelDropdown: @Composable (Modifier) -> Unit,
    enumDropdown: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        if (maxWidth >= CLAUSE_SIDE_BY_SIDE_MIN_WIDTH) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                labelDropdown(Modifier.weight(1f))
                enumDropdown(Modifier.weight(1f))
                ClauseDeleteButton(deleteDescription, onDelete)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                labelDropdown(Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    enumDropdown(Modifier.weight(1f))
                    ClauseDeleteButton(deleteDescription, onDelete)
                }
            }
        }
    }
}

@Composable
private fun ClauseDeleteButton(description: String, onDelete: () -> Unit) {
    AppCircleIconButton(onClick = onDelete) {
        Icon(
            Icons.Rounded.Delete,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp),
        )
    }
}

private val CLAUSE_SIDE_BY_SIDE_MIN_WIDTH = 300.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabelDropdown(
    label: String,
    labels: List<String>,
    modifier: Modifier,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = label.ifBlank { stringResource(R.string.schedule_reminder_rule_no_label) },
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.schedule_reminder_rule_course_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            labels.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.ifBlank { stringResource(R.string.schedule_reminder_rule_no_label) }) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    value: T,
    options: List<T>,
    label: String,
    optionLabel: @Composable (T) -> String,
    modifier: Modifier,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = optionLabel(value),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@StringRes
private fun ReminderLabelPresence.conditionLabelRes(): Int = when (this) {
    ReminderLabelPresence.Exists -> R.string.schedule_reminder_presence_exists
    ReminderLabelPresence.Absent -> R.string.schedule_reminder_presence_absent
}

@StringRes
private fun ReminderLabelActionType.actionLabelRes(): Int = when (this) {
    ReminderLabelActionType.Remind -> R.string.schedule_reminder_action_remind
    ReminderLabelActionType.Skip -> R.string.schedule_reminder_action_skip
}
