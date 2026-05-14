package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.CourseItem
import java.time.LocalTime

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
        title = { Text(if (course == null) "新增占位课" else "编辑占位课") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(40) },
                    label = { Text("Label") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (slotLabels.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val index = slotLabels.indexOf(label)
                                label = slotLabels[(index + 1).floorMod(slotLabels.size)]
                            },
                        ) { Text("切换已有 label") }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(40) },
                    label = { Text("名称") },
                    placeholder = { Text("可空，默认使用 label") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it.filter { c -> c.isDigit() || c == ':' }.take(5) },
                        label = { Text("开始") },
                        modifier = Modifier.weight(1f),
                        isError = startTime.isNotBlank() && start == null,
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it.filter { c -> c.isDigit() || c == ':' }.take(5) },
                        label = { Text("结束") },
                        modifier = Modifier.weight(1f),
                        isError = endTime.isNotBlank() && (end == null || (start != null && !end.isAfter(start))),
                    )
                }
                OutlinedTextField(
                    value = weeksText,
                    onValueChange = { weeksText = it.filter { c -> c.isDigit() || c == ',' || c == '，' || c == ' ' } },
                    label = { Text("生效周次") },
                    placeholder = { Text("可空表示全部周") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it.filter { c -> c.isDigit() || c == ',' || c == '，' || c == ' ' } },
                    label = { Text("生效星期") },
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
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
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

private fun Int.floorMod(size: Int): Int = if (size <= 0) 0 else ((this % size) + size) % size
