package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.DEFAULT_TERM_WEEK_COUNT
import com.x500x.cursimple.feature.plugin.ui.AppAssistChip
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
 * Quick-add fixes the tapped day and period span while asking for course metadata and weeks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddCourseDialog(
    dayOfWeek: Int,
    startNode: Int,
    endNode: Int,
    existingCourses: List<CourseItem> = emptyList(),
    maxWeekCount: Int = DEFAULT_TERM_WEEK_COUNT,
    slotLabel: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (CourseItem) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var weekLocationsRaw by rememberSaveable { mutableStateOf("") }
    var pickingWeekLocations by rememberSaveable { mutableStateOf(false) }
    var teacher by rememberSaveable { mutableStateOf("") }
    // Leave new-course weeks unset rather than assuming current-to-term-end coverage.
    var startWeekText by rememberSaveable { mutableStateOf("") }
    var endWeekText by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(CourseCategory.Course) }

    val titleTrimmed = title.trim()
    val weekLocationsMap = remember(weekLocationsRaw) { decodeWeekLocations(weekLocationsRaw) }
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

                // Show fixed day and periods as read-only destination chips.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppAssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(stringResource(scheduleWeekdayFullRes(dayOfWeek))) },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                    AppAssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                slotLabel
                                    ?: if (startNode == endNode) stringResource(R.string.schedule_node_single, startNode)
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
                    CourseLocationField(
                        location = location,
                        onLocationChange = { location = it },
                        weekLocations = weekLocationsMap,
                        onPickWeekLocations = { pickingWeekLocations = true },
                    )
                    OutlinedTextField(
                        value = teacher,
                        onValueChange = { teacher = it },
                        label = { Text(stringResource(R.string.schedule_course_teacher_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppAssistChip(
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
                        AppAssistChip(
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
                    AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) }
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
                                // Retain nonblank locations for selected weeks only.
                                weekLocations = weekLocationsMap
                                    .filterKeys { it in weeks }
                                    .mapValues { it.value.trim() }
                                    .filterValues { it.isNotBlank() },
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

    if (pickingWeekLocations) {
        val startWeek = startWeekText.toIntOrNull()
        val endWeek = endWeekText.toIntOrNull()
        WeekLocationDialog(
            weeks = if (startWeek != null && endWeek != null && startWeek in 1..endWeek) {
                (startWeek..endWeek).toList()
            } else {
                (1..maxWeekCount).toList()
            },
            courseTitle = title.trim(),
            baseLocation = location.trim(),
            weekLocations = weekLocationsMap,
            onChange = { week, value ->
                weekLocationsRaw = encodeWeekLocations(
                    weekLocationsMap.toMutableMap().apply {
                        if (value.isBlank()) remove(week) else put(week, value)
                    },
                )
            },
            onClearAll = { weekLocationsRaw = "" },
            onDismiss = { pickingWeekLocations = false },
        )
    }
}
