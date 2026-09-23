package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.feature.plugin.ui.AppFilterChip
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import java.util.UUID
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateListOf

internal enum class WeekParity(@param:StringRes val labelRes: Int) {
    All(R.string.schedule_week_parity_all),
    Odd(R.string.schedule_week_parity_odd),
    Even(R.string.schedule_week_parity_even),

    /** 自己在周次矩阵里逐周点选，用于 3、5、11 这种没有规律的排课。 */
    Custom(R.string.schedule_week_parity_custom),
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
            // 自选不走区间，调用方直接给周次集合，这里不该被用到
            WeekParity.Custom -> true
        }
    }.takeIf { it.isNotEmpty() }
}

/**
 * 从已有周次反推该用哪一档。
 *
 * 判据是「能不能用区间加单双周表达出来」：用 min..max 套一遍规则，结果和原周次
 * 一模一样才算那一档，否则就是自选。不这么判的话，3、5、11 这种会被归成全部周，
 * 一打开编辑器就被区间悄悄改写成 3..11 的每一周。
 */
internal fun weekParityOf(weeks: List<Int>?): WeekParity {
    if (weeks.isNullOrEmpty()) return WeekParity.All
    val sorted = weeks.distinct().sorted()
    val first = sorted.first()
    val last = sorted.last()
    for (parity in listOf(WeekParity.All, WeekParity.Odd, WeekParity.Even)) {
        val expanded = (first..last).filter { week ->
            when (parity) {
                WeekParity.All -> true
                WeekParity.Odd -> week % 2 == 1
                WeekParity.Even -> week % 2 == 0
                WeekParity.Custom -> false
            }
        }
        if (expanded == sorted) return parity
    }
    return WeekParity.Custom
}

/**
 * 周次矩阵要铺到第几周。
 *
 * 取「学期总周数」与「这门课已有的最大周次」里大的那个：课程的周次超出学期设定时
 * （导进来的数据常有），矩阵里得点得到那几周，否则用户既看不到也改不掉。
 */
internal fun customWeekLimit(maxWeekCount: Int, weeks: List<Int>?): Int =
    maxOf(maxWeekCount, weeks?.maxOrNull() ?: 0).coerceAtLeast(1)

/** 自选周次在表单状态里存成逗号串，便于 rememberSaveable 直接存取。 */
internal fun encodeCustomWeeks(weeks: Collection<Int>): String = weeks.distinct().sorted().joinToString(",")

/**
 * 解析自选周次。
 *
 * [maxWeekCount] 传的是放宽后的上限（见 [customWeekLimit]）：课程本身的周次可能
 * 超出学期设定的总周数（导进来的数据写到 24 周而学期只设了 20 周），
 * 按学期总周数硬切会把用户已有的周次悄悄丢掉。
 */
internal fun decodeCustomWeeks(raw: String, maxWeekCount: Int): List<Int> = raw
    .split(',')
    .mapNotNull { it.trim().toIntOrNull() }
    .filter { it in 1..maxWeekCount }
    .distinct()
    .sorted()

// 各周单独地点在表单状态里的编码：条目间用 ，周次与地点间用 。
// 地点里可能带任何可见字符，用不可见控制符当分隔符才不会撞上教室号。
private const val WEEK_LOC_ENTRY_SEP = "\u0002"
private const val WEEK_LOC_KV_SEP = "\u0001"

internal fun encodeWeekLocations(map: Map<Int, String>): String =
    map.entries
        .filter { it.value.isNotBlank() }
        .sortedBy { it.key }
        .joinToString(WEEK_LOC_ENTRY_SEP) { "${it.key}$WEEK_LOC_KV_SEP${it.value}" }

internal fun decodeWeekLocations(raw: String): Map<Int, String> {
    if (raw.isBlank()) return emptyMap()
    return raw.split(WEEK_LOC_ENTRY_SEP).mapNotNull { entry ->
        val parts = entry.split(WEEK_LOC_KV_SEP, limit = 2)
        val week = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val loc = parts.getOrNull(1).orEmpty()
        if (loc.isBlank()) null else week to loc
    }.toMap()
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
    var parity by rememberSaveable(initial) { mutableStateOf(weekParityOf(initial?.weeks)) }
    // 自选的周次存成逗号串，rememberSaveable 才存得下
    var customWeeksRaw by rememberSaveable(initial) {
        mutableStateOf(encodeCustomWeeks(initial?.weeks.orEmpty()))
    }
    var pickingWeeks by rememberSaveable(initial) { mutableStateOf(false) }
    var category by rememberSaveable(initial) {
        mutableStateOf(initial?.category ?: CourseCategory.Course)
    }
    // 各周单独地点存成一串，rememberSaveable 才存得下
    var pickingWeekLocations by rememberSaveable(initial) { mutableStateOf(false) }
    var weekLocationsRaw by rememberSaveable(initial) {
        mutableStateOf(encodeWeekLocations(initial?.weekLocations.orEmpty()))
    }

    val titleTrimmed = title.trim()
    val startNode = startNodeText.toIntOrNull()
    val endNode = endNodeText.toIntOrNull()
    val startWeek = startWeekText.toIntOrNull()
    val endWeek = endWeekText.toIntOrNull()
    val rangeValid = startWeek != null && endWeek != null &&
        startWeek in 1..maxWeekCount && endWeek in startWeek..maxWeekCount
    // 课程原有的周次可能超出学期总周数，矩阵与解码都按放宽后的上限来
    val weekLimit = remember(maxWeekCount, initial) {
        customWeekLimit(maxWeekCount, initial?.weeks)
    }
    val customWeeks = remember(customWeeksRaw, weekLimit) {
        decodeCustomWeeks(customWeeksRaw, weekLimit)
    }
    val weeks = if (parity == WeekParity.Custom) {
        customWeeks.takeIf { it.isNotEmpty() }
    } else {
        manualCourseWeeksOrNull(startWeek, endWeek, parity, maxWeekCount)
    }
    val nodesValid = startNode != null && endNode != null &&
        startNode in 1..maxNodeCount && endNode in startNode..maxNodeCount
    val weekLocationsMap = remember(weekLocationsRaw) { decodeWeekLocations(weekLocationsRaw) }
    // 只保留当前选中周里、且真填了内容的地点；关掉开关就整份清空
    val effectiveWeekLocations = if (weeks != null) {
        weekLocationsMap.filterKeys { it in weeks }.mapValues { it.value.trim() }.filterValues { it.isNotBlank() }
    } else {
        emptyMap()
    }
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
            weekLocations = effectiveWeekLocations,
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
            // 每周换教室的课（物理实验之类）从这里逐周设。按钮常驻：没填默认地点、
            // 还没选周次时也能先设，周次没选就先列出整个学期的周，保存时只留选中那几周的
            trailingIcon = {
                val marked = weekLocationsMap.isNotEmpty()
                IconButton(onClick = { pickingWeekLocations = true }) {
                    Icon(
                        imageVector = Icons.Rounded.EditCalendar,
                        contentDescription = stringResource(R.string.schedule_week_location_title),
                        tint = if (marked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            },
            supportingText = weekLocationsMap.size.takeIf { it > 0 }?.let { count ->
                {
                    Text(
                        text = stringResource(R.string.schedule_week_location_summary, count),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        CourseFormLabel(stringResource(R.string.schedule_course_category_label))
        FlowChipRow {
            AppFilterChip(
                selected = category == CourseCategory.Course,
                onClick = { category = CourseCategory.Course },
                label = { Text(stringResource(R.string.schedule_category_course)) },
            )
            AppFilterChip(
                selected = category == CourseCategory.Exam,
                onClick = { category = CourseCategory.Exam },
                label = { Text(stringResource(R.string.schedule_category_exam)) },
            )
        }

        CourseFormLabel(stringResource(R.string.schedule_add_course_time_label))
        FlowChipRow {
            (1..7).forEach { day ->
                AppFilterChip(
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
        // 自选时起止周没有意义，收起来只留已选周的摘要
        if (parity == WeekParity.Custom) {
            SelectedWeeksRow(
                weeks = customWeeks,
                onEdit = { pickingWeeks = true },
            )
        } else {
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
        }

        FlowChipRow {
            WeekParity.entries.forEach { p ->
                AppFilterChip(
                    selected = parity == p,
                    onClick = {
                        parity = p
                        // 切到自选就把当前区间的结果带过去当初值，省得从零点起
                        if (p == WeekParity.Custom) {
                            if (customWeeksRaw.isBlank()) {
                                customWeeksRaw = encodeCustomWeeks(
                                    manualCourseWeeksOrNull(startWeek, endWeek, WeekParity.All, weekLimit).orEmpty(),
                                )
                            }
                            pickingWeeks = true
                        }
                    },
                    label = { Text(stringResource(p.labelRes)) },
                )
            }
        }

        if (pickingWeekLocations) {
            WeekLocationDialog(
                weeks = weeks?.takeIf { it.isNotEmpty() } ?: (1..weekLimit).toList(),
                courseTitle = titleTrimmed,
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

        if (pickingWeeks) {
            WeekMatrixDialog(
                maxWeekCount = weekLimit,
                selected = customWeeks.toSet(),
                onConfirm = { picked ->
                    customWeeksRaw = encodeCustomWeeks(picked)
                    pickingWeeks = false
                },
                onDismiss = { pickingWeeks = false },
            )
        }

        if (rangeValid && parity != WeekParity.Custom && weeks == null) {
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


/** 自选模式下的摘要行：左边列出选了哪几周，右边一直留着「修改」入口。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedWeeksRow(
    weeks: List<Int>,
    onEdit: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onEdit),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (weeks.isEmpty()) {
                        stringResource(R.string.schedule_week_custom_empty)
                    } else {
                        stringResource(R.string.schedule_week_custom_summary, weeks.size)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (weeks.isEmpty()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (weeks.isNotEmpty()) {
                    Text(
                        text = weeks.joinToString("、"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AppOutlinedButton(onClick = onEdit) {
                Text(stringResource(R.string.schedule_week_custom_edit))
            }
        }
    }
}

/**
 * 周次矩阵。
 *
 * 一格一周铺成网格，点一下切换选中；确认才写回表单，中途反悔直接关掉就行。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekMatrixDialog(
    maxWeekCount: Int,
    selected: Set<Int>,
    onConfirm: (Set<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val picked = remember(selected) { mutableStateListOf<Int>().apply { addAll(selected.sorted()) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_week_custom_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.schedule_week_custom_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (1..maxWeekCount).forEach { week ->
                        val isPicked = week in picked
                        Surface(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (isPicked) picked.remove(week) else picked.add(week)
                                },
                            color = if (isPicked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = week.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isPicked) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOutlinedButton(onClick = {
                        picked.clear()
                        picked.addAll(1..maxWeekCount)
                    }) { Text(stringResource(R.string.schedule_week_custom_all)) }
                    AppOutlinedButton(onClick = { picked.clear() }) {
                        Text(stringResource(R.string.schedule_action_clear))
                    }
                }
            }
        },
        confirmButton = {
            AppOutlinedButton(
                enabled = picked.isNotEmpty(),
                onClick = { onConfirm(picked.toSet()) },
            ) { Text(stringResource(R.string.schedule_action_save)) }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_cancel)) }
        },
    )
}

/**
 * 各周单独地点。
 *
 * 上面一片周次格，哪几周设过就点亮哪几周；点一格，下面只出一个输入框填那一周的地点。
 * 不把每周都摊成一行——周次一多就是长长一条，既难看又要一直往下滚。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekLocationDialog(
    weeks: List<Int>,
    courseTitle: String,
    baseLocation: String,
    weekLocations: Map<Int, String>,
    onChange: (Int, String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sorted = remember(weeks) { weeks.distinct().sorted() }
    var focused by rememberSaveable(sorted) { mutableStateOf(sorted.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_week_location_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 这里要让人确认「我正在给哪门课改地点」，而不是再读一遍功能说明
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = courseTitle.ifBlank {
                                stringResource(R.string.schedule_week_location_untitled_course)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (baseLocation.isBlank()) {
                                stringResource(R.string.schedule_week_location_no_default)
                            } else {
                                stringResource(R.string.schedule_week_location_default, baseLocation)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                R.string.schedule_week_location_counts,
                                weeks.size,
                                weekLocations.count { it.value.isNotBlank() },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    sorted.forEach { week ->
                        val customized = weekLocations[week]?.isNotBlank() == true
                        val isFocused = week == focused
                        Surface(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { focused = week },
                            color = when {
                                isFocused -> MaterialTheme.colorScheme.primary
                                customized -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = week.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (customized) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isFocused -> MaterialTheme.colorScheme.onPrimary
                                        customized -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }

                focused?.let { week ->
                    OutlinedTextField(
                        value = weekLocations[week].orEmpty(),
                        onValueChange = { onChange(week, it) },
                        label = { Text(stringResource(R.string.schedule_week_location_week, week)) },
                        placeholder = {
                            Text(
                                text = baseLocation.ifBlank {
                                    stringResource(R.string.schedule_week_location_placeholder_empty)
                                },
                                maxLines = 1,
                            )
                        },
                        singleLine = true,
                        trailingIcon = weekLocations[week]?.takeIf { it.isNotBlank() }?.let {
                            {
                                IconButton(onClick = { onChange(week, "") }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.schedule_action_clear),
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_action_save)) }
        },
        dismissButton = if (weekLocations.isNotEmpty()) {
            {
                AppOutlinedButton(onClick = onClearAll) { Text(stringResource(R.string.schedule_action_clear)) }
            }
        } else {
            null
        },
    )
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
    weekLocations: Map<Int, String> = emptyMap(),
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
            weekLocations = weekLocations,
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
        weekLocations = weekLocations,
    )
}

