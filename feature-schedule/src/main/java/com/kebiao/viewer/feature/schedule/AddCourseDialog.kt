package com.kebiao.viewer.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.CourseTimeSlot
import java.util.UUID

private enum class WeekParity(val label: String) {
    All("全部周"),
    Odd("单周"),
    Even("双周"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCourseDialog(
    onDismiss: () -> Unit,
    onConfirm: (CourseItem) -> Unit,
    maxNodeCount: Int = 12,
    maxWeekCount: Int = 30,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var teacher by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var dayOfWeek by rememberSaveable { mutableStateOf(1) }
    var startNodeText by rememberSaveable { mutableStateOf("1") }
    var endNodeText by rememberSaveable { mutableStateOf("2") }
    var startWeekText by rememberSaveable { mutableStateOf("1") }
    var endWeekText by rememberSaveable { mutableStateOf("16") }
    var parity by rememberSaveable { mutableStateOf(WeekParity.All) }

    val titleTrimmed = title.trim()
    val startNode = startNodeText.toIntOrNull()
    val endNode = endNodeText.toIntOrNull()
    val startWeek = startWeekText.toIntOrNull()
    val endWeek = endWeekText.toIntOrNull()
    val canSave = titleTrimmed.isNotBlank() &&
        startNode != null && endNode != null && startNode in 1..maxNodeCount && endNode in startNode..maxNodeCount &&
        startWeek != null && endWeek != null && startWeek in 1..maxWeekCount && endWeek in startWeek..maxWeekCount

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
                    .heightIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "添加课程",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("课程名 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("授课教师（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("上课地点（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "上课时间",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowChipRow {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { idx, label ->
                        val day = idx + 1
                        FilterChip(
                            selected = dayOfWeek == day,
                            onClick = { dayOfWeek = day },
                            label = { Text("周$label") },
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = startNodeText,
                        onValueChange = { startNodeText = it.filter(Char::isDigit).take(2) },
                        label = { Text("起始节") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endNodeText,
                        onValueChange = { endNodeText = it.filter(Char::isDigit).take(2) },
                        label = { Text("结束节") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "周次",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = startWeekText,
                        onValueChange = { startWeekText = it.filter(Char::isDigit).take(2) },
                        label = { Text("起始周") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endWeekText,
                        onValueChange = { endWeekText = it.filter(Char::isDigit).take(2) },
                        label = { Text("结束周") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                FlowChipRow {
                    WeekParity.entries.forEach { p ->
                        FilterChip(
                            selected = parity == p,
                            onClick = { parity = p },
                            label = { Text(p.label) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val course = buildCourse(
                                title = titleTrimmed,
                                teacher = teacher.trim(),
                                location = location.trim(),
                                dayOfWeek = dayOfWeek,
                                startNode = startNode!!,
                                endNode = endNode!!,
                                startWeek = startWeek!!,
                                endWeek = endWeek!!,
                                parity = parity,
                            )
                            onConfirm(course)
                        },
                        enabled = canSave,
                    ) { Text("保存") }
                }
            }
        }
    }
}

@Composable
private fun FlowChipRow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
    }
}

private fun buildCourse(
    title: String,
    teacher: String,
    location: String,
    dayOfWeek: Int,
    startNode: Int,
    endNode: Int,
    startWeek: Int,
    endWeek: Int,
    parity: WeekParity,
): CourseItem {
    val weeks = (startWeek..endWeek).filter { week ->
        when (parity) {
            WeekParity.All -> true
            WeekParity.Odd -> week % 2 == 1
            WeekParity.Even -> week % 2 == 0
        }
    }
    return CourseItem(
        id = "manual-" + UUID.randomUUID().toString().take(12),
        title = title,
        teacher = teacher,
        location = location,
        weeks = weeks,
        time = CourseTimeSlot(
            dayOfWeek = dayOfWeek,
            startNode = startNode,
            endNode = endNode,
        ),
    )
}
