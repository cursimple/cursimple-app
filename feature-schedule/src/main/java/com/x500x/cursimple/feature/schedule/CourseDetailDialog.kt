package com.x500x.cursimple.feature.schedule

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.x500x.cursimple.core.data.note.COURSE_NOTE_MAX_LENGTH
import com.x500x.cursimple.core.data.note.CourseNoteInput
import com.x500x.cursimple.core.data.note.courseNoteLength
import com.x500x.cursimple.core.data.note.validateCourseNote
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.ExamCountdown
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.examCountdownOrNull
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import com.x500x.cursimple.feature.schedule.time.today
import java.time.LocalDate

@Composable
fun CourseDetailDialog(
    courses: List<CourseItem>,
    timingProfile: TermTimingProfile?,
    visibleWeekNumber: Int?,
    isManual: (CourseItem) -> Boolean,
    examReminderEnabled: Boolean = false,
    mutedExamCourseIds: Set<String> = emptySet(),
    targetDate: LocalDate? = null,
    isTemporarilyCancelled: (CourseItem) -> Boolean = { false },
    noteTextOf: (CourseItem) -> String = { "" },
    noteMaxLength: Int = COURSE_NOTE_MAX_LENGTH,
    onSaveNote: (CourseItem, String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
    onTemporaryCancel: (CourseItem) -> Unit = {},
    onRestoreTemporaryCancel: (CourseItem) -> Unit = {},
    onSetReminder: (CourseItem) -> Unit,
    onMuteExamReminder: (CourseItem) -> Unit = {},
    onRestoreExamReminder: (CourseItem) -> Unit = {},
    onDelete: (CourseItem) -> Unit,
) {
    if (courses.isEmpty()) return
    // 以这一格的课程 id 列表为 key：换格子或删掉其中一门后，chip 选中项回到第一门，
    // 不会沿用上一格的下标而串到别的课上。
    val courseIdsKey = remember(courses) { courses.joinToString("|") { it.id } }
    var selectedIndex by rememberSaveable(courseIdsKey) { mutableIntStateOf(0) }
    val course = courses[selectedIndex.coerceIn(0, courses.size - 1)]
    val accents = com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents.current
    val palette = remember(course.title, course.category, accents) { courseColor(course.title, accents.coursePalette) }
    val headerContainer = if (course.category == CourseCategory.Exam) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        palette.container
    }
    val headerContent = if (course.category == CourseCategory.Exam) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        palette.onContainer
    }
    // 周次未知时不判断本周与否，徽章另行标注
    val isThisWeek = visibleWeekNumber?.let { course.isActiveInWeek(it) }
    val manual = isManual(course)
    val weekday = if (course.time.dayOfWeek in 1..7) {
        stringResource(scheduleWeekdayFullRes(course.time.dayOfWeek))
    } else {
        "?"
    }
    val nodeRange = if (course.time.startNode == course.time.endNode) {
        stringResource(R.string.schedule_node_single, course.time.startNode)
    } else {
        stringResource(R.string.schedule_node_range, course.time.startNode, course.time.endNode)
    }
    val context = LocalContext.current
    val classTimeText = remember(course, timingProfile, context) {
        context.classTimeText(resolveClassTime(course, timingProfile))
    }
    val weeksText = remember(course.weeks, context) {
        context.weeksDetailText(describeWeeksDetail(course.weeks))
    }
    val today = LocalAppZone.current.today()
    // targetDate 是点开这一格时对应的日期，对考试来说就是这场考试的日期。
    val examCountdown = remember(course, targetDate, today) {
        if (course.category == CourseCategory.Exam && targetDate != null) {
            examCountdownOrNull(course, targetDate, today)
        } else {
            null
        }
    }

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
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 680.dp),
            ) {
                // 顶部彩色头条
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerContainer)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = course.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = headerContent,
                                modifier = Modifier.weight(1f),
                            )
                            StatusChip(thisWeek = isThisWeek, manual = manual)
                        }
                        Text(
                            text = stringResource(
                                R.string.schedule_course_detail_header,
                                if (course.category == CourseCategory.Exam) stringResource(R.string.schedule_course_detail_exam_prefix) else "",
                                weekday,
                                nodeRange,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = headerContent.copy(alpha = 0.85f),
                        )
                    }
                }

                if (courses.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.schedule_course_detail_same_slot, courses.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                        courses.forEachIndexed { index, c ->
                            FilterChip(
                                selected = index == selectedIndex,
                                onClick = { selectedIndex = index },
                                label = {
                                    Text(
                                        text = c.title,
                                        maxLines = 1,
                                    )
                                },
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    DetailRow(
                        icon = Icons.Rounded.AccessTime,
                        title = stringResource(R.string.schedule_course_detail_class_time),
                        body = classTimeText,
                    )
                    DetailRow(
                        icon = Icons.Rounded.CalendarMonth,
                        title = stringResource(R.string.schedule_course_detail_weeks),
                        body = weeksText,
                    )
                    examCountdown?.let { countdown ->
                        DetailRow(
                            icon = Icons.Rounded.AccessTime,
                            title = stringResource(R.string.schedule_course_detail_countdown),
                            body = stringResource(
                                R.string.schedule_course_detail_countdown_body,
                                countdown.date.monthValue,
                                countdown.date.dayOfMonth,
                                examCountdownText(countdown),
                            ),
                        )
                    }
                    if (course.location.isNotBlank()) {
                        DetailRow(
                            icon = Icons.Rounded.LocationOn,
                            title = stringResource(R.string.schedule_course_detail_location),
                            body = course.location,
                        )
                    }
                    if (course.teacher.isNotBlank()) {
                        DetailRow(
                            icon = Icons.Rounded.Person,
                            title = stringResource(R.string.schedule_course_detail_teacher),
                            body = course.teacher,
                        )
                    }
                    DetailRow(
                        icon = Icons.Rounded.Source,
                        title = stringResource(R.string.schedule_course_detail_source),
                        body = stringResource(if (manual) R.string.schedule_source_manual else R.string.schedule_source_plugin),
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    CourseNoteSection(
                        courseKey = course.id,
                        savedNote = noteTextOf(course),
                        maxLength = noteMaxLength,
                        onSave = { text -> onSaveNote(course, text) },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    if (course.category == CourseCategory.Exam) {
                        ExamReminderMuteRow(
                            enabled = examReminderEnabled,
                            muted = course.id in mutedExamCourseIds,
                            onMute = { onMuteExamReminder(course) },
                            onRestore = { onRestoreExamReminder(course) },
                        )
                    }

                    if (targetDate != null) {
                        val temporarilyCancelled = isTemporarilyCancelled(course)
                        OutlinedButton(
                            onClick = {
                                if (temporarilyCancelled) {
                                    onRestoreTemporaryCancel(course)
                                } else {
                                    onTemporaryCancel(course)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                imageVector = if (temporarilyCancelled) Icons.Rounded.Restore else Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(if (temporarilyCancelled) R.string.schedule_course_detail_restore_cancel else R.string.schedule_course_detail_temp_cancel),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (manual) {
                            OutlinedButton(
                                onClick = { onDelete(course) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.schedule_action_delete), maxLines = 1, softWrap = false)
                            }
                        }
                        OutlinedButton(
                            onClick = { onSetReminder(course) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.schedule_course_detail_set_reminder), maxLines = 1, softWrap = false)
                        }
                        Button(
                            onClick = onDismiss,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(stringResource(R.string.schedule_action_close), maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseNoteSection(
    courseKey: String,
    savedNote: String,
    maxLength: Int,
    onSave: (String) -> Unit,
) {
    // 切换到同格的另一门课时，草稿与编辑态一起重置，不会把上一门课的输入带过来。
    var editing by remember(courseKey) { mutableStateOf(false) }
    var draft by remember(courseKey, savedNote) { mutableStateOf(savedNote) }
    val draftLength = courseNoteLength(draft)
    val tooLong = draftLength > maxLength

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.schedule_note_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (!editing) {
                TextButton(onClick = { editing = true }) {
                    Text(stringResource(if (savedNote.isBlank()) R.string.schedule_action_add else R.string.schedule_action_edit))
                }
            }
        }

        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
                placeholder = { Text(stringResource(R.string.schedule_note_hint)) },
                isError = tooLong,
                supportingText = {
                    Text(
                        text = if (tooLong) {
                            stringResource(R.string.schedule_note_over_limit, draftLength - maxLength, maxLength)
                        } else {
                            "$draftLength / $maxLength"
                        },
                    )
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (savedNote.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            draft = ""
                            onSave("")
                            editing = false
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(stringResource(R.string.schedule_action_clear), maxLines = 1, softWrap = false)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        draft = savedNote
                        editing = false
                    },
                ) {
                    Text(stringResource(R.string.schedule_action_cancel))
                }
                Button(
                    onClick = {
                        when (val result = validateCourseNote(draft, maxLength)) {
                            is CourseNoteInput.Accepted -> {
                                draft = result.text
                                onSave(result.text)
                                editing = false
                            }

                            is CourseNoteInput.TooLong -> Unit
                        }
                    },
                    enabled = !tooLong,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.schedule_action_save), maxLines = 1, softWrap = false)
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = savedNote.ifBlank { stringResource(R.string.schedule_note_empty) },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (savedNote.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun ExamReminderMuteRow(
    enabled: Boolean,
    muted: Boolean,
    onMute: () -> Unit,
    onRestore: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.schedule_exam_mute_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        !enabled -> stringResource(R.string.schedule_exam_mute_disabled)
                        muted -> stringResource(R.string.schedule_exam_mute_muted)
                        else -> stringResource(R.string.schedule_exam_mute_active)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (enabled) {
                TextButton(onClick = if (muted) onRestore else onMute) {
                    Text(stringResource(if (muted) R.string.schedule_action_restore else R.string.schedule_exam_mute_this))
                }
            }
        }
    }
}

@Composable
private fun StatusChip(thisWeek: Boolean?, manual: Boolean) {
    val (label, container, content) = when {
        thisWeek == null -> Triple(
            stringResource(R.string.schedule_status_week_unknown),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        !thisWeek -> Triple(
            stringResource(R.string.schedule_status_other_week),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        manual -> Triple(
            stringResource(R.string.schedule_status_manual),
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        else -> Triple(
            stringResource(R.string.schedule_status_this_week),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = container,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun examCountdownText(countdown: ExamCountdown): String = when (countdown.daysRemaining) {
    0L -> stringResource(R.string.schedule_exam_countdown_today)
    1L -> stringResource(R.string.schedule_exam_countdown_tomorrow)
    else -> stringResource(R.string.schedule_exam_countdown_days, countdown.daysRemaining)
}

/** 上课时间的呈现形态：命中节次时间表给区间，否则按大节或节次编号。 */
internal sealed interface ClassTimeInfo {
    data class Range(val startTime: String, val endTime: String) : ClassTimeInfo
    data class MajorPeriod(val index: Int) : ClassTimeInfo
    data class MajorPeriodRange(val start: Int, val end: Int) : ClassTimeInfo
    data class NodeRange(val start: Int, val end: Int) : ClassTimeInfo
}

private fun resolveClassTime(course: CourseItem, timingProfile: TermTimingProfile?): ClassTimeInfo {
    val slots: List<ClassSlotTime> = timingProfile?.slotTimes.orEmpty().sortedBy { it.startNode }
    val matchStart = slots.firstOrNull { course.time.startNode in it.startNode..it.endNode }
    val matchEnd = slots.firstOrNull { course.time.endNode in it.startNode..it.endNode }
    if (matchStart != null && matchEnd != null) {
        return ClassTimeInfo.Range(matchStart.startTime, matchEnd.endTime)
    }
    // 超出 timing 配置的节次：按 profile 行数 + 顺次给"第 N 大节"
    val baseCount = slots.size
    val extraStart = course.time.startNode - (slots.lastOrNull()?.endNode ?: 0)
    val extraEnd = course.time.endNode - (slots.lastOrNull()?.endNode ?: 0)
    return if (extraStart >= 1 && extraEnd >= 1) {
        if (extraStart == extraEnd) ClassTimeInfo.MajorPeriod(baseCount + extraStart)
        else ClassTimeInfo.MajorPeriodRange(baseCount + extraStart, baseCount + extraEnd)
    } else {
        ClassTimeInfo.NodeRange(course.time.startNode, course.time.endNode)
    }
}

internal fun Context.classTimeText(info: ClassTimeInfo): String = when (info) {
    is ClassTimeInfo.Range -> getString(R.string.schedule_class_time_range, info.startTime, info.endTime)
    is ClassTimeInfo.MajorPeriod -> getString(R.string.schedule_major_period_single, info.index)
    is ClassTimeInfo.MajorPeriodRange -> getString(R.string.schedule_major_period_range, info.start, info.end)
    is ClassTimeInfo.NodeRange -> getString(R.string.schedule_node_range, info.start, info.end)
}

/** 上课周次的呈现形态。[weeksList] 为空表示未指定周次，其余附上逐个周次列表。 */
internal sealed interface WeeksDetail {
    object Unspecified : WeeksDetail
    data class Consecutive(val first: Int, val last: Int, val count: Int, val weeksList: String) : WeeksDetail
    data class Odd(val first: Int, val last: Int, val count: Int, val weeksList: String) : WeeksDetail
    data class Even(val first: Int, val last: Int, val count: Int, val weeksList: String) : WeeksDetail
    data class Count(val count: Int, val weeksList: String) : WeeksDetail
}

private fun describeWeeksDetail(weeks: List<Int>): WeeksDetail {
    if (weeks.isEmpty()) return WeeksDetail.Unspecified
    val sorted = weeks.sorted().distinct()
    val first = sorted.first()
    val last = sorted.last()
    val full = (first..last).toList()
    val odd = full.filter { it % 2 == 1 }
    val even = full.filter { it % 2 == 0 }
    val list = sorted.joinToString(", ")
    return when {
        sorted == full -> WeeksDetail.Consecutive(first, last, sorted.size, list)
        sorted == odd -> WeeksDetail.Odd(first, last, sorted.size, list)
        sorted == even -> WeeksDetail.Even(first, last, sorted.size, list)
        else -> WeeksDetail.Count(sorted.size, list)
    }
}

internal fun Context.weeksDetailText(detail: WeeksDetail): String = when (detail) {
    WeeksDetail.Unspecified -> getString(R.string.schedule_weeks_detail_unspecified)
    is WeeksDetail.Consecutive ->
        getString(R.string.schedule_weeks_detail_consecutive, detail.first, detail.last, detail.count) + "\n" + detail.weeksList
    is WeeksDetail.Odd ->
        getString(R.string.schedule_weeks_detail_odd, detail.first, detail.last, detail.count) + "\n" + detail.weeksList
    is WeeksDetail.Even ->
        getString(R.string.schedule_weeks_detail_even, detail.first, detail.last, detail.count) + "\n" + detail.weeksList
    is WeeksDetail.Count ->
        getString(R.string.schedule_weeks_detail_count, detail.count) + "\n" + detail.weeksList
}
