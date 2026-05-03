package com.kebiao.viewer.feature.schedule

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.CourseTimeSlot
import java.util.UUID

/**
 * A streamlined add-course dialog used when the user taps an empty grid cell.
 * The day-of-week and start/end nodes are locked to where they tapped, so the
 * dialog only asks for the bits that actually vary: title, location, week range.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddCourseDialog(
    dayOfWeek: Int,
    startNode: Int,
    endNode: Int,
    initialWeek: Int,
    maxWeekCount: Int = 30,
    onDismiss: () -> Unit,
    onConfirm: (CourseItem) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var teacher by rememberSaveable { mutableStateOf("") }
    var startWeekText by rememberSaveable { mutableStateOf("1") }
    var endWeekText by rememberSaveable { mutableStateOf("2") }

    val titleTrimmed = title.trim()
    val startWeek = startWeekText.toIntOrNull()
    val endWeek = endWeekText.toIntOrNull()
    val weeksValid = startWeek != null && endWeek != null &&
        startWeek in 1..maxWeekCount && endWeek in startWeek..maxWeekCount
    val canSave = titleTrimmed.isNotBlank() && weeksValid

    val dayLabel = listOf("一", "二", "三", "四", "五", "六", "日").getOrNull(dayOfWeek - 1) ?: "?"

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
                    text = "在此处添加课程",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Locked time chips: day + node range, read-only so the user knows
                // exactly where the new course will land.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("周$dayLabel") },
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
                                if (startNode == endNode) "第 $startNode 节"
                                else "第 $startNode-$endNode 节"
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
                        label = { Text("课程名 *") },
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
                    OutlinedTextField(
                        value = teacher,
                        onValueChange = { teacher = it },
                        label = { Text("授课教师（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
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
                                time = CourseTimeSlot(
                                    dayOfWeek = dayOfWeek,
                                    startNode = startNode,
                                    endNode = endNode,
                                ),
                            )
                            onConfirm(course)
                        },
                    ) { Text("保存") }
                }
            }
        }
    }
}
