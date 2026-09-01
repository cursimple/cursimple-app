package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.CourseItem
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaceholderCourseDialog(
    course: CourseItem?,
    daysOfWeek: List<Int> = course?.time?.dayOfWeek?.let(::listOf).orEmpty(),
    slotLabels: List<String>,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, String, List<Int>, List<Int>, String?) -> Unit,
) {
    val defaultLabel = course?.slotLabelOverride ?: slotLabels.firstOrNull().orEmpty()
    var label by rememberSaveable(course?.id) { mutableStateOf(defaultLabel) }
    var title by rememberSaveable(course?.id) { mutableStateOf(course?.title.orEmpty()) }
    var startTime by rememberSaveable(course?.id) { mutableStateOf(course?.reminderStartTime ?: "07:10") }
    var endTime by rememberSaveable(course?.id) { mutableStateOf(course?.reminderEndTime ?: "07:50") }
    var weeksText by rememberSaveable(course?.id) { mutableStateOf(course?.weeks.orEmpty().joinToString(",")) }
    var daysText by rememberSaveable(course?.id, daysOfWeek) {
        mutableStateOf(daysOfWeek.ifEmpty { listOf(1, 2, 3, 4, 5) }.joinToString(","))
    }
    val start = startTime.parseTimeOrNull()
    val end = endTime.parseTimeOrNull()
    val weeks = parseIntList(weeksText)
    val days = parseIntList(daysText).filter { it in 1..7 }
    val canSave = label.isNotBlank() && start != null && end != null && end.isAfter(start) && days.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (course == null) R.string.schedule_placeholder_add_title else R.string.schedule_placeholder_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                var labelExpanded by rememberSaveable(course?.id, slotLabels) { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = labelExpanded,
                    onExpandedChange = { labelExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it.take(40) },
                        label = { Text("Label") },
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
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(40) },
                    label = { Text(stringResource(R.string.schedule_placeholder_name_label)) },
                    placeholder = { Text(stringResource(R.string.schedule_placeholder_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it.filter { c -> c.isDigit() || c == ':' }.take(5) },
                        label = { Text(stringResource(R.string.schedule_placeholder_start_label)) },
                        modifier = Modifier.weight(1f),
                        isError = startTime.isNotBlank() && start == null,
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it.filter { c -> c.isDigit() || c == ':' }.take(5) },
                        label = { Text(stringResource(R.string.schedule_placeholder_end_label)) },
                        modifier = Modifier.weight(1f),
                        isError = endTime.isNotBlank() && (end == null || (start != null && !end.isAfter(start))),
                    )
                }
                OutlinedTextField(
                    value = weeksText,
                    onValueChange = { weeksText = it.filter { c -> c.isDigit() || c == ',' || c == '，' || c == ' ' } },
                    label = { Text(stringResource(R.string.schedule_placeholder_weeks_label)) },
                    placeholder = { Text(stringResource(R.string.schedule_placeholder_weeks_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it.filter { c -> c.isDigit() || c == ',' || c == '，' || c == ' ' } },
                    label = { Text(stringResource(R.string.schedule_placeholder_days_label)) },
                    placeholder = { Text("1,2,3,4,5") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    onSave(
                        course?.id,
                        label.trim(),
                        startTime,
                        endTime,
                        weeks,
                        days,
                        title.takeIf { it.isNotBlank() },
                    )
                },
            ) { Text(stringResource(R.string.schedule_action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) } },
    )
}

private fun String.parseTimeOrNull(): LocalTime? =
    runCatching { LocalTime.parse(this) }.getOrNull()

internal fun parseIntList(value: String): List<Int> =
    value.split(',', '，', ' ')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it > 0 }
        .distinct()
        .sorted()
