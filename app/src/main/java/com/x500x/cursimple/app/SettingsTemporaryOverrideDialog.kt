package com.x500x.cursimple.app

import com.x500x.cursimple.core.kernel.model.moveToNodeRange
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import com.x500x.cursimple.feature.plugin.ui.AppFilterChip
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.core.kernel.model.CancelCoursePlan
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverrideType
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.slotsCovering
import com.x500x.cursimple.core.data.widget.slotLabelText
import androidx.compose.ui.platform.LocalContext
import com.x500x.cursimple.core.kernel.model.coursesScheduledOn
import com.x500x.cursimple.core.kernel.model.isCourseTemporarilyCancelled
import com.x500x.cursimple.core.kernel.model.planCancelCourse
import com.x500x.cursimple.core.kernel.model.planRestoreCourse
import com.x500x.cursimple.core.kernel.model.visibleScheduleCourses
import com.x500x.cursimple.core.kernel.model.makeUpNodeRange
import com.x500x.cursimple.core.kernel.model.resolveTemporaryScheduleSourceDate
import com.x500x.cursimple.core.kernel.model.weekdayLabel
import java.time.LocalDate
import java.util.UUID
import com.x500x.cursimple.feature.schedule.CalendarMonthPicker

/** 临时调课的查看与编辑。 */

/** 两种规则各占一个 tab：表单和下面的规则列表都只属于当前 tab。 */
private enum class TemporaryOverrideTab { MakeUp, CancelCourse }

/** 调课覆盖整天，还是只覆盖指定的几节。 */
private enum class MakeUpScope { WholeDay, Nodes }

/** 需要二次确认的动作。 */
private sealed interface OverrideConfirm {
    data class Remove(val id: String) : OverrideConfirm

    data object Clear : OverrideConfirm

    /** 表单里还有没添加的草稿就想关闭。 */
    data object DiscardDraft : OverrideConfirm
}

private const val MAX_NODE = 32

/**
 * 临时调课的设置页区块。
 *
 * 直接内联在设置页里而不是弹窗：弹窗自带一层和设置页不同的底色与表头，插在设置页上很突兀；
 * 内联后配色、圆角、行高都跟着设置页走。点已有规则会把它读回上面的表单里单条修改，
 * 行尾的叉只删这一条。
 */
@Composable
internal fun TemporaryOverrideSettingsSection(
    overrides: List<TemporaryScheduleOverride>,
    onUpsert: (TemporaryScheduleOverride) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onOpenCourseSwap: (LocalDate, LocalDate) -> Unit = { _, _ -> },
    courseTitleOf: (String) -> String? = { null },
    /** 课表里的全部课程（导入的加手动的），临时取消页按它列出选中那天的课。 */
    courses: List<CourseItem> = emptyList(),
    /** 用来把节号换成「第三节 14:00–15:35」这种给人看的写法，和课表左侧的节次栏一致。 */
    timingProfile: TermTimingProfile? = null,
    holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
    termStartDate: LocalDate? = null,
    onApplyCancelPlan: (CancelCoursePlan) -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(TemporaryOverrideTab.MakeUp) }
    // 非空表示正在修改这一条已有规则，保存时沿用它的 id 覆盖回去
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    var makeUpTargetDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var makeUpSourceDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var makeUpScope by rememberSaveable { mutableStateOf(MakeUpScope.WholeDay) }
    var makeUpStartText by rememberSaveable { mutableStateOf("") }
    var makeUpEndText by rememberSaveable { mutableStateOf("") }

    var cancelTargetDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }

    var pickDateFor by rememberSaveable { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf<OverrideConfirm?>(null) }

    val makeUpNodes = nodeRangeOf(makeUpStartText, makeUpEndText)

    fun resetDraft() {
        editingId = null
        makeUpTargetDate = null
        makeUpSourceDate = null
        makeUpScope = MakeUpScope.WholeDay
        makeUpStartText = ""
        makeUpEndText = ""
        cancelTargetDate = null
    }

    /** 把已有规则读回表单，单条修改。 */
    fun loadForEdit(rule: TemporaryScheduleOverride) {
        resetDraft()
        val target = parseIsoDate(rule.targetDate) ?: parseIsoDate(rule.startDate)
        if (rule.type == TemporaryScheduleOverrideType.CancelCourse) {
            // 停课没有表单可改，点开就是把那天的课列出来，在列表里逐门停或恢复
            tab = TemporaryOverrideTab.CancelCourse
            cancelTargetDate = target
        } else {
            editingId = rule.id
            tab = TemporaryOverrideTab.MakeUp
            makeUpTargetDate = target
            makeUpSourceDate = target?.let { resolveTemporaryScheduleSourceDate(it, listOf(rule)) }
            val nodes = rule.makeUpNodeRange()
            if (nodes != null) {
                makeUpScope = MakeUpScope.Nodes
                makeUpStartText = nodes.first.toString()
                makeUpEndText = nodes.last.toString()
            }
        }
    }

    val makeUpUsable = makeUpTargetDate != null &&
        makeUpSourceDate != null &&
        makeUpTargetDate != makeUpSourceDate &&
        (makeUpScope == MakeUpScope.WholeDay || makeUpNodes != null)

    val visibleRules = overrides.filter {
        when (tab) {
            TemporaryOverrideTab.MakeUp -> it.type == TemporaryScheduleOverrideType.MakeUp
            TemporaryOverrideTab.CancelCourse -> it.type == TemporaryScheduleOverrideType.CancelCourse
        }
    }

    // 两类规则各占一个分段，切换时下面的表单与规则列表一起换。
    // 用和设置行同一套底色与圆角，不用 Material 自带的 chip/tab，免得一眼看出是两种控件
    OverrideModeSwitch(
        selected = tab,
        onSelect = { picked ->
            if (tab != picked) {
                tab = picked
                editingId = null
            }
        },
    )

    Text(
        text = if (editingId != null) {
            stringResource(R.string.settings_override_form_editing)
        } else if (tab == TemporaryOverrideTab.MakeUp) {
            stringResource(R.string.settings_override_form_makeup)
        } else {
            stringResource(R.string.settings_override_form_cancel)
        },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (tab == TemporaryOverrideTab.MakeUp) {
        SettingsActionRow(
            icon = Icons.Rounded.EventRepeat,
            title = stringResource(R.string.settings_override_target_makeup),
            subtitle = makeUpTargetDate?.let(::formatLongDate)
                ?: stringResource(R.string.settings_override_date_unset),
            onClick = { pickDateFor = PICK_MAKEUP_TARGET },
        )
        SettingsActionRow(
            icon = Icons.Rounded.Schedule,
            title = stringResource(R.string.settings_override_source_day),
            subtitle = makeUpSourceDate?.let(::formatLongDate)
                ?: stringResource(R.string.settings_override_date_unset),
            onClick = { pickDateFor = PICK_MAKEUP_SOURCE },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppFilterChip(
                selected = makeUpScope == MakeUpScope.WholeDay,
                onClick = { makeUpScope = MakeUpScope.WholeDay },
                label = { Text(stringResource(R.string.settings_override_scope_whole_day)) },
                modifier = Modifier.weight(1f),
            )
            AppFilterChip(
                selected = makeUpScope == MakeUpScope.Nodes,
                onClick = { makeUpScope = MakeUpScope.Nodes },
                label = { Text(stringResource(R.string.settings_override_scope_nodes)) },
                modifier = Modifier.weight(1f),
            )
        }
        if (makeUpScope == MakeUpScope.Nodes) {
            NodeRangeFields(
                startText = makeUpStartText,
                endText = makeUpEndText,
                startLabel = stringResource(R.string.settings_override_makeup_start),
                endLabel = stringResource(R.string.settings_override_makeup_end),
                valid = makeUpNodes != null,
                onStartChange = { makeUpStartText = it },
                onEndChange = { makeUpEndText = it },
            )
        }
        FormHint(
            text = makeUpHintText(makeUpTargetDate, makeUpSourceDate, makeUpScope, makeUpNodes),
            isError = makeUpTargetDate != null && makeUpSourceDate != null && !makeUpUsable,
        )
        // 两天都选好了才谈得上「摆开看」，所以这个入口跟着日期出现，而不是常驻在页首
        if (makeUpTargetDate != null && makeUpSourceDate != null && makeUpTargetDate != makeUpSourceDate) {
            SettingsActionRow(
                icon = Icons.Rounded.SwapHoriz,
                title = stringResource(R.string.settings_override_open_swap_title),
                subtitle = stringResource(R.string.settings_override_open_swap_subtitle),
                onClick = { onOpenCourseSwap(makeUpTargetDate!!, makeUpSourceDate!!) },
            )
        }
        OverrideSubmitRow(
            editing = editingId != null,
            enabled = makeUpUsable,
            addLabel = stringResource(R.string.settings_override_add),
            onSubmit = {
                val target = makeUpTargetDate ?: return@OverrideSubmitRow
                val source = makeUpSourceDate ?: return@OverrideSubmitRow
                onUpsert(
                    TemporaryScheduleOverride(
                        id = editingId ?: UUID.randomUUID().toString(),
                        type = TemporaryScheduleOverrideType.MakeUp,
                        targetDate = target.toString(),
                        sourceDate = source.toString(),
                        makeUpStartNode = makeUpNodes?.first?.takeIf { makeUpScope == MakeUpScope.Nodes },
                        makeUpEndNode = makeUpNodes?.last?.takeIf { makeUpScope == MakeUpScope.Nodes },
                    ),
                )
                resetDraft()
            },
            onCancelEdit = { resetDraft() },
        )
    } else {
        SettingsActionRow(
            icon = Icons.Rounded.EventBusy,
            title = stringResource(R.string.settings_override_target_cancel),
            subtitle = cancelTargetDate?.let(::formatLongDate)
                ?: stringResource(R.string.settings_override_date_unset),
            onClick = { pickDateFor = PICK_CANCEL_TARGET },
        )
        CancelDayCourseList(
            date = cancelTargetDate,
            courses = courses,
            timingProfile = timingProfile,
            overrides = overrides,
            holidayCalendar = holidayCalendar,
            termStartDate = termStartDate,
            onApply = onApplyCancelPlan,
        )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Text(
        text = stringResource(R.string.settings_override_list_title, visibleRules.size),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (visibleRules.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_override_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        visibleRules.forEach { rule ->
            SettingsActionRow(
                icon = Icons.Rounded.Schedule,
                title = formatOverrideRange(rule),
                subtitle = formatOverrideSource(rule, courseTitleOf),
                onClick = { loadForEdit(rule) },
                trailing = {
                    IconButton(onClick = { confirming = OverrideConfirm.Remove(rule.id) }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.settings_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        }
        SettingsActionRow(
            icon = Icons.Rounded.DeleteSweep,
            title = stringResource(R.string.settings_clear_all),
            subtitle = stringResource(R.string.settings_override_clear_body),
            onClick = { confirming = OverrideConfirm.Clear },
        )
    }

    // 拖动调课挪过的单门课。它们不属于「整天/节次换课」那套规则，以前根本不列出来，
    // 挪了几门、挪到哪天都看不到，也没法单独撤回
    if (tab == TemporaryOverrideTab.MakeUp) {
        MovedCoursesList(
            overrides = overrides,
            courseTitleOf = courseTitleOf,
            onRemove = { id -> confirming = OverrideConfirm.Remove(id) },
        )
    }

    pickDateFor?.let { target ->
        val initial = when (target) {
            PICK_MAKEUP_TARGET -> makeUpTargetDate
            PICK_MAKEUP_SOURCE -> makeUpSourceDate
            else -> cancelTargetDate
        }
        SettingsDatePickerDialog(
            initial = initial,
            onConfirm = { picked ->
                when (target) {
                    PICK_MAKEUP_TARGET -> makeUpTargetDate = picked
                    PICK_MAKEUP_SOURCE -> makeUpSourceDate = picked
                    else -> cancelTargetDate = picked
                }
                pickDateFor = null
            },
            onDismiss = { pickDateFor = null },
        )
    }

    confirming?.let { pending ->
        OverrideConfirmDialog(
            pending = pending,
            onDismiss = { confirming = null },
            onConfirm = {
                when (pending) {
                    is OverrideConfirm.Remove -> {
                        // 删掉的正是在改的那条，表单要退回新建态，免得保存时又把它写回来
                        if (editingId == pending.id) resetDraft()
                        onRemove(pending.id)
                    }
                    OverrideConfirm.Clear -> {
                        resetDraft()
                        onClear()
                    }
                    OverrideConfirm.DiscardDraft -> resetDraft()
                }
                confirming = null
            },
        )
    }
}

/**
 * 临时取消：选中那天实际要上的课逐门列出，点「停课」就记下，已停的点「恢复」。
 *
 * 以前要自己填起止节次，得先去课表里查那门课是第几节；而且按节次停会把同一时段的
 * 别的课一起停掉。现在每条规则都记下课程 id，只停点中的那一门。
 */
@Composable
private fun CancelDayCourseList(
    date: LocalDate?,
    courses: List<CourseItem>,
    timingProfile: TermTimingProfile?,
    overrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings,
    termStartDate: LocalDate?,
    onApply: (CancelCoursePlan) -> Unit,
) {
    val context = LocalContext.current
    if (date == null) {
        FormHint(text = stringResource(R.string.settings_override_cancel_pick_date), isError = false)
        return
    }
    val dayCourses = remember(date, courses, overrides, holidayCalendar, termStartDate) {
        coursesScheduledOn(
            date = date,
            courses = courses.visibleScheduleCourses(),
            overrides = overrides,
            holidayCalendar = holidayCalendar,
            termStartDate = termStartDate,
            includeCancelled = true,
        )
    }
    if (dayCourses.isEmpty()) {
        FormHint(text = stringResource(R.string.settings_override_cancel_no_course, formatLongDate(date)), isError = false)
        return
    }
    dayCourses.forEach { course ->
        val cancelled = isCourseTemporarilyCancelled(date, course, overrides)
        // 旧版跨几天的按节次规则拆不开，只能去下面的规则列表整条删
        val restorePlan = if (cancelled) {
            planRestoreCourse(date, course, dayCourses, overrides, newId = { UUID.randomUUID().toString() })
        } else {
            null
        }
        // 节号是内部编号（午间课占了第 3 号，「第三节」其实是 4 号），直接写给人看会对不上课表，
        // 所以优先用作息表里的时段名；跨了几个时段的课没有单一名字，才退回节号
        val covering = timingProfile?.slotsCovering(course.time.startNode, course.time.endNode).orEmpty()
        val period = context.slotLabelText(covering) ?: stringResource(
            R.string.settings_override_cancel_course_nodes,
            nodeSpanText(course.time.startNode, course.time.endNode),
        )
        val time = covering.takeIf { it.isNotEmpty() }
            ?.let { "${it.first().value.startTime}–${it.last().value.endTime}" }
        val detail = listOfNotNull(
            period,
            time,
            course.location.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        val toggle: () -> Unit = {
            when {
                !cancelled -> onApply(planCancelCourse(date, course, newId = { UUID.randomUUID().toString() }))
                restorePlan != null -> onApply(restorePlan)
            }
        }
        SettingsActionRow(
            icon = if (cancelled) Icons.Rounded.EventBusy else Icons.Rounded.Schedule,
            title = course.title,
            subtitle = when {
                !cancelled -> detail
                restorePlan == null -> stringResource(R.string.settings_override_cancel_state_legacy, detail)
                else -> stringResource(R.string.settings_override_cancel_state, detail)
            },
            onClick = toggle,
            trailing = {
                AppOutlinedButton(onClick = toggle, enabled = !cancelled || restorePlan != null) {
                    Text(
                        stringResource(
                            if (cancelled) R.string.settings_override_cancel_restore else R.string.settings_override_cancel_action,
                        ),
                    )
                }
            },
        )
    }
}

/**
 * 拖动调课挪过的课，按「挪到哪天」分组。
 *
 * 每天一行写清调入了几门，点开才列出具体是哪几门、各自从哪天调来、落在第几节，
 * 行尾的叉只撤回那一门。天数多的时候不至于一打开就铺满整页。
 */
@Composable
private fun MovedCoursesList(
    overrides: List<TemporaryScheduleOverride>,
    courseTitleOf: (String) -> String?,
    onRemove: (String) -> Unit,
) {
    val moves = overrides.filter { it.type == TemporaryScheduleOverrideType.MoveCourse }
    if (moves.isEmpty()) return
    val byDay = moves
        .groupBy { parseIsoDate(it.targetDate) }
        .toSortedMap(compareBy(nullsLast()) { it })
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        text = stringResource(R.string.settings_override_moves_title, moves.size),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    byDay.forEach { (day, rules) ->
        val key = day?.toString() ?: "invalid"
        val open = expanded == key
        SettingsActionRow(
            icon = Icons.Rounded.SwapHoriz,
            title = day?.let(::formatShortDate) ?: stringResource(R.string.settings_invalid_date),
            subtitle = stringResource(R.string.settings_override_moves_day, rules.size),
            onClick = { expanded = if (open) null else key },
            trailing = {
                Icon(
                    imageVector = if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )
        if (open) {
            rules.sortedBy { it.moveToStartNode ?: Int.MAX_VALUE }.forEach { rule ->
                MovedCourseRow(rule = rule, courseTitleOf = courseTitleOf, onRemove = { onRemove(rule.id) })
            }
        }
    }
}

@Composable
private fun MovedCourseRow(
    rule: TemporaryScheduleOverride,
    courseTitleOf: (String) -> String?,
    onRemove: () -> Unit,
) {
    val title = rule.moveCourseId?.let(courseTitleOf)
        ?: stringResource(R.string.settings_override_move_unknown_course)
    val from = parseIsoDate(rule.sourceDate)
    val nodes = rule.moveToNodeRange()
    val detail = if (from != null && nodes != null) {
        stringResource(R.string.settings_override_move_item, formatShortDate(from), nodes.first, nodes.last)
    } else {
        stringResource(R.string.settings_override_source_invalid)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.settings_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** 补课/调课 与 临时取消 的分段切换，外形对齐设置页的行：同样的圆角与底色。 */
@Composable
private fun OverrideModeSwitch(
    selected: TemporaryOverrideTab,
    onSelect: (TemporaryOverrideTab) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TemporaryOverrideTab.entries.forEach { mode ->
                val active = mode == selected
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(mode) },
                    color = if (active) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = stringResource(
                            when (mode) {
                                TemporaryOverrideTab.MakeUp -> R.string.settings_override_mode_makeup
                                TemporaryOverrideTab.CancelCourse -> R.string.settings_override_mode_cancel
                            },
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/** 添加/保存按钮；正在改已有规则时多给一个「取消修改」。 */
@Composable
private fun OverrideSubmitRow(
    editing: Boolean,
    enabled: Boolean,
    addLabel: String,
    onSubmit: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onSubmit,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                if (editing) stringResource(R.string.settings_override_save_edit) else addLabel,
            )
        }
        if (editing) {
            AppOutlinedButton(onClick = onCancelEdit) {
                Text(stringResource(R.string.settings_override_cancel_edit))
            }
        }
    }
}

private const val PICK_MAKEUP_TARGET = "makeup_target"
private const val PICK_MAKEUP_SOURCE = "makeup_source"
private const val PICK_CANCEL_TARGET = "cancel_target"

@Composable
private fun NodeRangeFields(
    startText: String,
    endText: String,
    startLabel: String,
    endLabel: String,
    valid: Boolean,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
) {
    val filled = startText.isNotBlank() || endText.isNotBlank()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = startText,
            onValueChange = { onStartChange(it.filter(Char::isDigit).take(2)) },
            label = { Text(startLabel) },
            singleLine = true,
            isError = filled && !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = endText,
            onValueChange = { onEndChange(it.filter(Char::isDigit).take(2)) },
            label = { Text(endLabel) },
            singleLine = true,
            isError = filled && !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FormHint(text: String?, isError: Boolean) {
    if (text.isNullOrBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun makeUpHintText(
    targetDate: LocalDate?,
    sourceDate: LocalDate?,
    scope: MakeUpScope,
    nodes: NodeRange?,
): String? = when {
    // 只是还没填完，不是填错了：这时什么都不说，别拿报错色吓人
    targetDate == null || sourceDate == null -> null
    targetDate == sourceDate -> stringResource(R.string.settings_override_same_day)
    scope == MakeUpScope.Nodes && nodes == null -> stringResource(R.string.settings_override_node_invalid)
    scope == MakeUpScope.Nodes -> stringResource(
        R.string.settings_override_makeup_hint_nodes,
        formatLongDate(targetDate),
        nodes!!.first,
        nodes.last,
        formatLongDate(sourceDate),
    )

    else -> stringResource(
        R.string.settings_override_makeup_hint,
        formatLongDate(targetDate),
        formatLongDate(sourceDate),
    )
}

@Composable
private fun OverrideConfirmDialog(
    pending: OverrideConfirm,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val (titleRes, bodyRes, actionRes) = when (pending) {
        is OverrideConfirm.Remove -> Triple(
            R.string.settings_override_remove_title,
            R.string.settings_override_remove_body,
            R.string.settings_delete,
        )

        OverrideConfirm.Clear -> Triple(
            R.string.settings_override_clear_title,
            R.string.settings_override_clear_body,
            R.string.settings_clear_all,
        )

        OverrideConfirm.DiscardDraft -> Triple(
            R.string.settings_override_discard_title,
            R.string.settings_override_discard_body,
            R.string.settings_override_discard_action,
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(bodyRes)) },
        confirmButton = {
            AppOutlinedButton(onClick = onConfirm) { Text(stringResource(actionRes)) }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

/** 日期选择入口；[date] 为空时显示「未选择」，不拿今天冒充用户的选择。 */
@Composable
internal fun DateChoiceButton(
    label: String,
    date: LocalDate?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AppOutlinedButton(
        modifier = modifier,
        onClick = onClick,
    ) {
        Text("$label  ${date?.let(::formatShortDate) ?: stringResource(R.string.settings_override_date_unset)}")
    }
}

@Composable
internal fun TemporaryOverrideRuleRow(
    rule: TemporaryScheduleOverride,
    onRemove: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatOverrideRange(rule),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatOverrideSource(rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppOutlinedButton(onClick = onRemove) {
                Text(stringResource(R.string.settings_delete))
            }
        }
    }
}

@Composable
internal fun SettingsDatePickerDialog(
    initial: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // 不用 M3 的 DatePicker：它的星期表头取自系统 narrow 名字，中文环境下七列全是「星」
    val fallback = com.x500x.cursimple.core.kernel.time.BeijingTime.today()
    var selected by remember(initial) { mutableStateOf(initial ?: fallback) }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            CalendarMonthPicker(
                selected = selected,
                onSelect = { selected = it },
            )
        },
        confirmButton = {
            AppOutlinedButton(onClick = { onConfirm(selected) }) {
                Text(stringResource(R.string.settings_confirm))
            }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

@Composable
internal fun temporaryOverridesSubtitle(overrides: List<TemporaryScheduleOverride>): String {
    return when {
        overrides.isEmpty() -> stringResource(R.string.settings_not_set)
        overrides.size == 1 -> formatOverrideSummary(overrides.first())
        else -> stringResource(
            R.string.settings_override_subtitle_multi,
            overrides.size,
            formatOverrideSummary(overrides.last()),
        )
    }
}

@Composable
internal fun formatOverrideSummary(rule: TemporaryScheduleOverride): String {
    return "${formatOverrideRange(rule)} · ${formatOverrideSource(rule)}"
}

@Composable
internal fun formatOverrideRange(rule: TemporaryScheduleOverride): String {
    val target = parseIsoDate(rule.targetDate) ?: parseIsoDate(rule.startDate)
    return target?.let(::formatShortDate) ?: stringResource(R.string.settings_invalid_date)
}

@Composable
internal fun formatOverrideSource(
    rule: TemporaryScheduleOverride,
    courseTitleOf: (String) -> String? = { null },
): String {
    if (rule.type == TemporaryScheduleOverrideType.CancelCourse) {
        val start = rule.cancelStartNode
        val end = rule.cancelEndNode ?: start
        val title = rule.cancelCourseId?.let(courseTitleOf)
        return if (start != null && end != null && title != null) {
            stringResource(R.string.settings_override_source_cancel_course, title, nodeSpanText(start, end))
        } else if (start != null && end != null) {
            stringResource(R.string.settings_override_source_cancel, nodeSpanText(start, end))
        } else {
            stringResource(R.string.settings_override_source_cancel_invalid)
        }
    }
    if (rule.type == TemporaryScheduleOverrideType.MoveCourse) {
        val from = parseIsoDate(rule.sourceDate)
            ?: return stringResource(R.string.settings_override_source_invalid)
        return stringResource(R.string.settings_override_source_move, formatShortDate(from))
    }
    val target = parseIsoDate(rule.targetDate) ?: parseIsoDate(rule.startDate)
    val source = target?.let { resolveTemporaryScheduleSourceDate(it, listOf(rule)) }
        ?: return stringResource(R.string.settings_override_source_invalid)
    val nodes = rule.makeUpNodeRange()
        ?: return stringResource(R.string.settings_override_source_makeup, formatLongDate(source))
    return stringResource(
        R.string.settings_override_source_makeup_nodes,
        formatLongDate(source),
        nodes.first,
        nodes.last,
    )
}

/** 起止节次；两端都填了且落在 1..[MAX_NODE] 内才成立，顺序写反时自动归位。 */
internal data class NodeRange(val first: Int, val last: Int)

internal fun nodeRangeOf(startText: String, endText: String): NodeRange? {
    val start = startText.toIntOrNull() ?: return null
    val end = endText.toIntOrNull() ?: return null
    if (start !in 1..MAX_NODE || end !in 1..MAX_NODE) return null
    return NodeRange(minOf(start, end), maxOf(start, end))
}

/** 单节写「3」，跨节写「3-4」。 */
internal fun nodeSpanText(start: Int, end: Int): String =
    if (start == end) "$start" else "$start-$end"

internal fun formatShortDate(date: LocalDate): String =
    "${date.monthValue}/${date.dayOfMonth}"

internal fun formatLongDate(date: LocalDate): String =
    "${formatShortDate(date)} ${weekdayLabel(date.dayOfWeek.value)}"
