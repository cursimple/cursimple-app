package com.x500x.cursimple.feature.schedule

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

/**
 * 课程的编辑表单本体，只负责字段与校验，按钮由调用方自己摆。
 *
 * 新增课程与在详情页里改课共用这一份，两处的字段、校验与冲突提示才不会走散。
 * 每次输入变动都通过 [onDraftChange] 吐出当前草稿，输入不合法时给 null，
 * 调用方据此决定保存按钮是否可用。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CourseEditFormFields(
    initial: CourseItem?,
    existingCourses: List<CourseItem>,
    maxNodeCount: Int,
    maxWeekCount: Int,
    onDraftChange: (CourseItem?) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
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
    // 新建课程时周次留空由用户自己填：预填的 1-16 多半不是这门课的真实周次，
    // 填了反而容易被当成已经填好而直接保存。
    var startWeekText by rememberSaveable(initial) {
        mutableStateOf(initial?.weeks?.minOrNull()?.toString().orEmpty())
    }
    var endWeekText by rememberSaveable(initial) {
        mutableStateOf(initial?.weeks?.maxOrNull()?.toString().orEmpty())
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
    val nodesValid = startNode != null && endNode != null &&
        startNode in 1..maxNodeCount && endNode in startNode..maxNodeCount
    val draft = if (titleTrimmed.isNotBlank() && nodesValid && weeks != null) {
        buildCourse(
            title = titleTrimmed,
            teacher = teacher.trim(),
            location = location.trim(),
            dayOfWeek = dayOfWeek,
            startNode = startNode!!,
            endNode = endNode!!,
            weeks = weeks,
            category = category,
            existing = initial,
        )
    } else {
        null
    }
    // 回调可能每次重组都是新 lambda，锁一层再在草稿真的变了时才上报
    val currentOnDraftChange by rememberUpdatedState(onDraftChange)
    LaunchedEffect(draft) { currentOnDraftChange(draft) }

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

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing),
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

        CourseFormLabel(stringResource(R.string.schedule_course_category_label))
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

        CourseFormLabel(stringResource(R.string.schedule_add_course_time_label))
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

        CourseFormLabel(stringResource(R.string.schedule_weeks_label))
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
                text = stringResource(
                    R.string.schedule_add_course_parity_empty,
                    stringResource(parity.labelRes),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        conflictWarning?.let { CourseConflictWarning(warning = it) }
    }
}

@Composable
private fun CourseFormLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChipRow(content: @Composable () -> Unit) {
    FlowRow(
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
    // 编辑时只覆盖表单里的字段，id 与提醒占位字段原样保留。
    // 改的若是插件课，保持 id 不变正是覆盖的关键：保存后它以同 id 的手动课盖掉插件原件。
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
