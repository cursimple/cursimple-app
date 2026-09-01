package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import java.util.UUID

/**
 * 空格子点击后弹出的快速加课对话框的默认周次区间。
 * 起始周取当前显示的周次，结束周取学期总周数，保证新课在当前周立刻可见。
 */
internal fun quickAddDefaultWeekRange(initialWeek: Int, maxWeekCount: Int): Pair<Int, Int> {
    val safeMax = maxWeekCount.coerceAtLeast(1)
    val start = initialWeek.coerceIn(1, safeMax)
    return start to safeMax
}

/**
 * 点击课表空白格时使用的精简版添加课程对话框。
 * 星期与起止节次固定为点击的位置，对话框只询问会变化的部分：课程名、地点、周次范围。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddCourseDialog(
    dayOfWeek: Int,
    startNode: Int,
    endNode: Int,
    initialWeek: Int,
    existingCourses: List<CourseItem> = emptyList(),
    maxWeekCount: Int = 30,
    onDismiss: () -> Unit,
    onConfirm: (CourseItem) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var teacher by rememberSaveable { mutableStateOf("") }
    val defaultWeekRange = quickAddDefaultWeekRange(initialWeek, maxWeekCount)
    var startWeekText by rememberSaveable { mutableStateOf(defaultWeekRange.first.toString()) }
    var endWeekText by rememberSaveable { mutableStateOf(defaultWeekRange.second.toString()) }
    var category by rememberSaveable { mutableStateOf(CourseCategory.Course) }

    val titleTrimmed = title.trim()
    val startWeek = startWeekText.toIntOrNull()
    val endWeek = endWeekText.toIntOrNull()
    val weeksValid = startWeek != null && endWeek != null &&
        startWeek in 1..maxWeekCount && endWeek in startWeek..maxWeekCount
    val canSave = titleTrimmed.isNotBlank() && weeksValid
    val conflictWarning = remember(
        existingCourses, dayOfWeek, startNode, endNode, startWeek, endWeek, weeksValid, category, maxWeekCount,
    ) {
        addCourseConflictWarning(
            draftCourseConflicts(
                existingCourses = existingCourses,
                dayOfWeek = dayOfWeek,
                startNode = startNode,
                endNode = endNode,
                weeks = if (weeksValid) (startWeek!!..endWeek!!).toList() else null,
                category = category,
                maxWeekCount = maxWeekCount,
            ),
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.schedule_quick_add_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 星期与节次范围以只读 chip 固定展示，标明新课程会落在哪个位置。
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(stringResource(scheduleWeekdayFullRes(dayOfWeek))) },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                if (startNode == endNode) stringResource(R.string.schedule_node_single, startNode)
                                else stringResource(R.string.schedule_node_range, startNode, endNode)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.schedule_course_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text(stringResource(R.string.schedule_course_location_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = teacher,
                        onValueChange = { teacher = it },
                        label = { Text(stringResource(R.string.schedule_course_teacher_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { category = CourseCategory.Course },
                            label = { Text(stringResource(R.string.schedule_category_course)) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (category == CourseCategory.Course) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                labelColor = if (category == CourseCategory.Course) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            ),
                        )
                        AssistChip(
                            onClick = { category = CourseCategory.Exam },
                            label = { Text(stringResource(R.string.schedule_category_exam)) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (category == CourseCategory.Exam) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                labelColor = if (category == CourseCategory.Exam) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            ),
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = startWeekText,
                            onValueChange = { startWeekText = it.filter(Char::isDigit).take(2) },
                            label = { Text(stringResource(R.string.schedule_week_start_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = endWeekText,
                            onValueChange = { endWeekText = it.filter(Char::isDigit).take(2) },
                            label = { Text(stringResource(R.string.schedule_week_end_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    conflictWarning?.let { CourseConflictWarning(warning = it) }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = canSave,
                        onClick = {
                            val weeks = (startWeek!!..endWeek!!).toList()
                            val course = CourseItem(
                                id = "manual-" + UUID.randomUUID().toString().take(12),
                                title = titleTrimmed,
                                teacher = teacher.trim(),
                                location = location.trim(),
                                weeks = weeks,
                                category = category,
                                time = CourseTimeSlot(
                                    dayOfWeek = dayOfWeek,
                                    startNode = startNode,
                                    endNode = endNode,
                                ),
                            )
                            onConfirm(course)
                        },
                    ) { Text(stringResource(R.string.schedule_action_save)) }
                }
            }
        }
    }
}
