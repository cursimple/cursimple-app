package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    onDismiss: () -> Unit,
    onSave: (String?, String, Boolean, Int, String?, List<ReminderLabelCondition>, List<ReminderLabelAction>) -> Unit,
) {
    val labels = slotLabels.ifEmpty { listOf("") }
    var name by rememberSaveable(rule?.ruleId) {
        mutableStateOf(rule?.displayName ?: "新提醒规则 ${(System.currentTimeMillis() % 1000).toInt()}")
    }
    var enabled by rememberSaveable(rule?.ruleId) { mutableStateOf(rule?.enabled ?: true) }
    var advanceText by rememberSaveable(rule?.ruleId) { mutableStateOf((rule?.advanceMinutes ?: 20).toString()) }
    var ringtoneText by rememberSaveable(rule?.ruleId) { mutableStateOf(rule?.ringtoneUri.orEmpty()) }
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
    val canSave = name.isNotBlank() &&
        advance != null &&
        advance in 0..720 &&
        conditions.isNotEmpty() &&
        actions.isNotEmpty() &&
        conditions.all { it.slotLabel.isNotBlank() } &&
        actions.all { it.slotLabel.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule == null) "新建规则" else "编辑规则") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("规则名称") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                OutlinedTextField(
                    value = advanceText,
                    onValueChange = { advanceText = it.filter(Char::isDigit).take(4) },
                    label = { Text("提前分钟数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = advanceText.isNotBlank() && (advance == null || advance !in 0..720),
                )
                OutlinedTextField(
                    value = ringtoneText,
                    onValueChange = { ringtoneText = it.take(240) },
                    label = { Text("铃声 URI") },
                    placeholder = { Text("可空，使用默认铃声") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("如果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                OutlinedButton(
                    onClick = {
                        conditions += ReminderLabelCondition(labels.first(), ReminderLabelPresence.Exists)
                    },
                ) {
                    Text("+ 添加触发条件")
                }

                Spacer(Modifier.height(4.dp))
                Text("就", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                OutlinedButton(
                    onClick = {
                        actions += ReminderLabelAction(labels.first(), ReminderLabelActionType.Remind)
                    },
                ) {
                    Text("+ 添加执行动作")
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
                        ringtoneText.takeIf { it.isNotBlank() },
                        conditions,
                        actions,
                    )
                },
                enabled = canSave,
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionRow(
    condition: ReminderLabelCondition,
    labels: List<String>,
    onChange: (ReminderLabelCondition) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LabelDropdown(
            label = condition.slotLabel,
            labels = labels,
            modifier = Modifier.weight(1f),
            onSelected = { onChange(condition.copy(slotLabel = it)) },
        )
        EnumDropdown(
            value = condition.presence,
            options = ReminderLabelPresence.entries,
            label = "条件",
            optionLabel = { it.conditionLabel() },
            modifier = Modifier.weight(0.8f),
            onSelected = { onChange(condition.copy(presence = it)) },
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, contentDescription = "删除条件")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionRow(
    action: ReminderLabelAction,
    labels: List<String>,
    onChange: (ReminderLabelAction) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LabelDropdown(
            label = action.slotLabel,
            labels = labels,
            modifier = Modifier.weight(1f),
            onSelected = { onChange(action.copy(slotLabel = it)) },
        )
        EnumDropdown(
            value = action.action,
            options = ReminderLabelActionType.entries,
            label = "动作",
            optionLabel = { it.actionLabel() },
            modifier = Modifier.weight(0.8f),
            onSelected = { onChange(action.copy(action = it)) },
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, contentDescription = "删除动作")
        }
    }
}

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
            value = label.ifBlank { "无可选 label" },
            onValueChange = {},
            readOnly = true,
            label = { Text("课程 label") },
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
                    text = { Text(option.ifBlank { "无可选 label" }) },
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
    optionLabel: (T) -> String,
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

private fun ReminderLabelPresence.conditionLabel(): String = when (this) {
    ReminderLabelPresence.Exists -> "存在"
    ReminderLabelPresence.Absent -> "不存在"
}

private fun ReminderLabelActionType.actionLabel(): String = when (this) {
    ReminderLabelActionType.Remind -> "提醒"
    ReminderLabelActionType.Skip -> "跳过"
}
