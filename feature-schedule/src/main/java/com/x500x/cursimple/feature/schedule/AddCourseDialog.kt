package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.annotation.StringRes
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
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

internal enum class WeekParity(@param:StringRes val labelRes: Int) {
    All(R.string.schedule_week_parity_all),
    Odd(R.string.schedule_week_parity_odd),
    Even(R.string.schedule_week_parity_even),
}

/**
 * 按单双周筛选出周次列表，输入区间非法或筛完为空时返回 null。
 * 空列表在课程模型里表示"每周都有"，直接建课会让课程出现在所有周，
 * 所以这里用 null 与之区分，调用方拿到 null 必须禁止保存。
 */
internal fun manualCourseWeeksOrNull(
    startWeek: Int?,
    endWeek: Int?,
    parity: WeekParity,
    maxWeekCount: Int,
): List<Int>? {
    if (startWeek == null || endWeek == null) return null
    if (startWeek !in 1..maxWeekCount) return null
    if (endWeek !in startWeek..maxWeekCount) return null
    return (startWeek..endWeek).filter { week ->
        when (parity) {
            WeekParity.All -> true
            WeekParity.Odd -> week % 2 == 1
            WeekParity.Even -> week % 2 == 0
        }
    }.takeIf { it.isNotEmpty() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCourseDialog(
    onDismiss: () -> Unit,
    onConfirm: (CourseItem) -> Unit,
    existingCourses: List<CourseItem> = emptyList(),
    maxNodeCount: Int = 12,
    maxWeekCount: Int = 30,
    /** 不为 null 时是编辑已有课程，保存会保留它的 id 与提醒相关字段。 */
    initial: CourseItem? = null,
) {
    var title by rememberSaveable(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var teacher by rememberSaveable(initial) { mutableStateOf(initial?.teacher.orEmpty()) }
    var location by rememberSaveable(initial) { mutableStateOf(initial?.location.orEmpty()) }
    var dayOfWeek by rememberSaveable(initial) { mutableStateOf(initial?.time?.dayOfWeek ?: 1) }
    var startNodeText by rememberSaveable(initial) {
        mutableStateOf(initial?.time?.startNode?.toString() ?: "1")
    }
    var endNodeText by rememberSaveable(initial) {
        mutableStateOf(initial?.time?.endNode?.toString() ?: "2")
    }
    var startWeekText by rememberSaveable(initial) {
        mutableStateOf(initial?.weeks?.minOrNull()?.toString() ?: "1")
    }
    var endWeekText by rememberSaveable(initial) {
        mutableStateOf(initial?.weeks?.maxOrNull()?.toString() ?: "16")
    }
    var parity by rememberSaveable(initial) { mutableStateOf(initialWeekParity(initial?.weeks)) }
    var category by rememberSaveable(initial) {
        mutableStateOf(initial?.category ?: CourseCategory.Course)
    }

    val titleTrimmed = title.trim()
    val startNode = startNodeText.toIntOrNull()
    val endNode = endNodeText.toIntOrNull()
    val startWeek = startWeekText.toIntOrNull()
    val endWeek = endWeekText.toIntOrNull()
    val rangeValid = startWeek != null && endWeek != null &&
        startWeek in 1..maxWeekCount && endWeek in startWeek..maxWeekCount
    val weeks = manualCourseWeeksOrNull(startWeek, endWeek, parity, maxWeekCount)
    val canSave = titleTrimmed.isNotBlank() &&
        startNode != null && endNode != null && startNode in 1..maxNodeCount && endNode in startNode..maxNodeCount &&
        weeks != null
    val conflictWarning = remember(
        existingCourses, dayOfWeek, startNode, endNode, weeks, category, maxNodeCount, maxWeekCount,
    ) {
        addCourseConflictWarning(
            draftCourseConflicts(
                existingCourses = existingCourses,
                dayOfWeek = dayOfWeek,
                startNode = startNode,
                endNode = endNode,
                weeks = weeks,
                category = category,
                maxNodeCount = maxNodeCount,
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
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(
                        if (initial == null) {
                            R.string.schedule_add_course_title
                        } else {
                            R.string.schedule_edit_course_title
                        },
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.schedule_course_name_label)) },
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

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.schedule_course_location_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.schedule_course_category_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowChipRow {
                    FilterChip(
                        selected = category == CourseCategory.Course,
                        onClick = { category = CourseCategory.Course },
                        label = { Text(stringResource(R.string.schedule_category_course)) },
                    )
                    FilterChip(
                        selected = category == CourseCategory.Exam,
                        onClick = { category = CourseCategory.Exam },
                        label = { Text(stringResource(R.string.schedule_category_exam)) },
                    )
                }

                Text(
                    text = stringResource(R.string.schedule_add_course_time_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowChipRow {
                    (1..7).forEach { day ->
                        FilterChip(
                            selected = dayOfWeek == day,
                            onClick = { dayOfWeek = day },
                            label = { Text(stringResource(scheduleWeekdayFullRes(day))) },
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
                        label = { Text(stringResource(R.string.schedule_node_start_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endNodeText,
                        onValueChange = { endNodeText = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.schedule_node_end_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = stringResource(R.string.schedule_weeks_label),
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

                FlowChipRow {
                    WeekParity.entries.forEach { p ->
                        FilterChip(
                            selected = parity == p,
                            onClick = { parity = p },
                            label = { Text(stringResource(p.labelRes)) },
                        )
                    }
                }

                if (rangeValid && weeks == null) {
                    Text(
                        text = stringResource(R.string.schedule_add_course_parity_empty, stringResource(parity.labelRes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                conflictWarning?.let { CourseConflictWarning(warning = it) }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) }
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
                                weeks = weeks!!,
                                category = category,
                                existing = initial,
                            )
                            onConfirm(course)
                        },
                        enabled = canSave,
                    ) { Text(stringResource(R.string.schedule_action_save)) }
                }
            }
        }
    }
}

/**
 * 加课表单里的时间冲突提示。
 * 用 tertiaryContainer 而不是 errorContainer：这是提醒而非阻止，保存按钮仍然可用。
 */
@Composable
internal fun CourseConflictWarning(warning: AddCourseConflictWarning) {
    val text = LocalContext.current.addCourseConflictWarningText(warning)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
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
    weeks: List<Int>,
    category: CourseCategory,
    existing: CourseItem? = null,
): CourseItem {
    val time = CourseTimeSlot(dayOfWeek = dayOfWeek, startNode = startNode, endNode = endNode)
    // 编辑时只覆盖表单里的字段，id 与提醒占位字段原样保留
    if (existing != null) {
        return existing.copy(
            title = title,
            teacher = teacher,
            location = location,
            weeks = weeks,
            category = category,
            time = time,
        )
    }
    return CourseItem(
        id = "manual-" + UUID.randomUUID().toString().take(12),
        title = title,
        teacher = teacher,
        location = location,
        weeks = weeks,
        category = category,
        time = time,
    )
}

/** 从已有周次反推单双周选项，无法归类时按全部处理。 */
private fun initialWeekParity(weeks: List<Int>?): WeekParity {
    if (weeks.isNullOrEmpty()) return WeekParity.All
    return when {
        weeks.all { it % 2 == 1 } -> WeekParity.Odd
        weeks.all { it % 2 == 0 } -> WeekParity.Even
        else -> WeekParity.All
    }
}
