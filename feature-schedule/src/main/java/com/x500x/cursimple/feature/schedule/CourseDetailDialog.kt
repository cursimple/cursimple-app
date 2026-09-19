package com.x500x.cursimple.feature.schedule

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight

@OptIn(ExperimentalLayoutApi::class)

/**
 * 同一格里叠着的几门课，左右翻页挑一门。
 *
 * 原先是一排筹码并列，课名长的时候（「高级可编程逻辑程序设计与应用」这种）挤成两行、
 * 还被省略号切掉，反而看不出是哪门。改成一次只显示一门、整条替换，配圆点指示当前是第几门。
 */
@Composable
private fun SameSlotPager(
    courses: List<CourseItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.schedule_course_detail_same_slot, courses.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // 页码用圆点：几门课一眼看出来，也知道现在停在第几门
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                courses.indices.forEach { index ->
                    val active = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .size(if (active) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            )
                            .clickable { onSelect(index) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onSelect((selectedIndex - 1 + courses.size) % courses.size) },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.schedule_same_slot_prev),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = courses[selectedIndex].title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(
                            R.string.schedule_same_slot_position,
                            selectedIndex + 1,
                            courses.size,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
            IconButton(
                onClick = { onSelect((selectedIndex + 1) % courses.size) },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.schedule_same_slot_next),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun CourseDetailDialog(
    courses: List<CourseItem>,
    timingProfile: TermTimingProfile?,
    visibleWeekNumber: Int?,
    /** 真实的当前教学周；只有正在看的就是它时，标记才写「本周」。 */
    currentWeekNumber: Int? = null,
    isManual: (CourseItem) -> Boolean,
    examReminderEnabled: Boolean = false,
    mutedExamCourseIds: Set<String> = emptySet(),
    targetDate: LocalDate? = null,
    isTemporarilyCancelled: (CourseItem) -> Boolean = { false },
    noteTextOf: (CourseItem) -> String = { "" },
    noteMaxLength: Int = COURSE_NOTE_MAX_LENGTH,
    /** 这门课原件来自插件，眼下显示的是用户改过的那一份。 */
    isPluginOverride: (CourseItem) -> Boolean = { false },
    /** 编辑时用来提示时间冲突，正在编辑的那门课会自动排除。 */
    existingCourses: List<CourseItem> = emptyList(),
    maxNodeCount: Int = 12,
    maxWeekCount: Int = 30,
    onSaveNote: (CourseItem, String) -> Unit = { _, _ -> },
    onSaveCourse: (CourseItem) -> Unit = {},
    onRestorePluginCourse: (CourseItem) -> Unit = {},
    onDismiss: () -> Unit,
    onTemporaryCancel: (CourseItem) -> Unit = {},
    onRestoreTemporaryCancel: (CourseItem) -> Unit = {},
    onSetReminder: (CourseItem) -> Unit,
    /** 这门课眼下有没有能在这里直接撤掉的提醒。 */
    hasCancellableReminder: (CourseItem) -> Boolean = { false },
    onCancelReminder: (CourseItem) -> Unit = {},
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
    // 切到同格的另一门课时退出编辑，免得把这门课的输入按到那门课上
    var editing by remember(course.id) { mutableStateOf(false) }
    var confirmRestore by remember(course.id) { mutableStateOf(false) }
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
    val pluginOverride = manual && isPluginOverride(course)
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
    val classTime = remember(course, timingProfile) { resolveClassTime(course, timingProfile) }
    val classTimeText = remember(classTime, context) { context.classTimeText(classTime) }
    // 没有节次时间表时，上课时间只能按节次描述，跟副标题里的节次是同一句话，
    // 这时不重复挂到副标题上，免得出现"第 5-6 节 · 第 5-6 大节"
    val headerTimeText = (classTime as? ClassTimeInfo.Range)?.let { classTimeText }
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

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.schedule_course_detail_restore_plugin_title)) },
            text = { Text(stringResource(R.string.schedule_course_detail_restore_plugin_body, course.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    onRestorePluginCourse(course)
                }) { Text(stringResource(R.string.schedule_action_restore_plugin)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) {
                    Text(stringResource(R.string.schedule_action_cancel))
                }
            },
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
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // 顶部彩色头条：标题、状态、关闭挤在一行，副标题把星期节次时间合并成一句
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerContainer)
                        .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = course.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = headerContent,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            StatusChip(
                                activeInVisibleWeek = isThisWeek,
                                visibleWeekNumber = visibleWeekNumber,
                                viewingCurrentWeek = visibleWeekNumber != null &&
                                    visibleWeekNumber == currentWeekNumber,
                                manual = manual,
                            )
                            IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.schedule_action_close),
                                    tint = headerContent,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Text(
                            text = stringResource(
                                R.string.schedule_course_detail_header,
                                if (course.category == CourseCategory.Exam) {
                                    stringResource(R.string.schedule_course_detail_exam_prefix)
                                } else {
                                    ""
                                },
                                weekday,
                                nodeRange,
                            ) + headerTimeText.orEmpty().let { if (it.isBlank()) "" else " · $it" },
                            style = MaterialTheme.typography.bodySmall,
                            color = headerContent.copy(alpha = 0.85f),
                        )
                    }
                }

                if (courses.size > 1) {
                    SameSlotPager(
                        courses = courses,
                        selectedIndex = selectedIndex,
                        onSelect = {
                            selectedIndex = it
                            editing = false
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (editing) {
                        CourseEditSection(
                            course = course,
                            // 自己跟自己不算冲突
                            existingCourses = remember(existingCourses, course.id) {
                                existingCourses.filterNot { it.id == course.id }
                            },
                            maxNodeCount = maxNodeCount,
                            maxWeekCount = maxWeekCount,
                            convertsToManual = !manual,
                            onCancel = { editing = false },
                            onSave = {
                                onSaveCourse(it)
                                editing = false
                            },
                        )
                    } else {
                        CourseFactsCard(
                            classTimeText = classTimeText,
                            weeksText = weeksText,
                            examCountdown = examCountdown,
                            location = course.location,
                            teacher = course.teacher,
                            manual = manual,
                            pluginOverride = pluginOverride,
                        )

                        CourseNoteSection(
                            courseKey = course.id,
                            savedNote = noteTextOf(course),
                            maxLength = noteMaxLength,
                            onSave = { text -> onSaveNote(course, text) },
                        )

                        if (course.category == CourseCategory.Exam) {
                            ExamReminderMuteRow(
                                enabled = examReminderEnabled,
                                muted = course.id in mutedExamCourseIds,
                                onMute = { onMuteExamReminder(course) },
                                onRestore = { onRestoreExamReminder(course) },
                            )
                        }

                        CourseActionBar(
                            temporarilyCancelled = targetDate != null && isTemporarilyCancelled(course),
                            showTemporaryCancel = targetDate != null,
                            showDelete = manual && !pluginOverride,
                            showRestorePlugin = pluginOverride,
                            onEdit = { editing = true },
                            hasReminder = hasCancellableReminder(course),
                            onSetReminder = { onSetReminder(course) },
                            onCancelReminder = { onCancelReminder(course) },
                            onTemporaryCancel = { onTemporaryCancel(course) },
                            onRestoreTemporaryCancel = { onRestoreTemporaryCancel(course) },
                            onDelete = { onDelete(course) },
                            onRestorePlugin = { confirmRestore = true },
                        )
                    }
                }
            }
        }
    }
}

/** 详情页里的就地编辑：字段与加课表单同源，保存后插件课会转成手动课。 */
@Composable
private fun CourseEditSection(
    course: CourseItem,
    existingCourses: List<CourseItem>,
    maxNodeCount: Int,
    maxWeekCount: Int,
    convertsToManual: Boolean,
    onCancel: () -> Unit,
    onSave: (CourseItem) -> Unit,
) {
    var draft by remember(course.id) { mutableStateOf<CourseItem?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.schedule_course_detail_edit_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )

        if (convertsToManual) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.schedule_course_detail_converts_to_manual),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        CourseEditFormFields(
            initial = course,
            existingCourses = existingCourses,
            maxNodeCount = maxNodeCount,
            maxWeekCount = maxWeekCount,
            onDraftChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.schedule_action_cancel)) }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { draft?.let(onSave) },
                enabled = draft != null,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(stringResource(R.string.schedule_action_save), maxLines = 1, softWrap = false) }
        }
    }
}

/**
 * 关键信息卡。
 * 名称与取值同行、图标轻量内联：一门课四五条信息各占一行就够，
 * 不必每条都堆成"图标 + 标题 + 大字取值"三段，同格两门课时也才放得下。
 */
@Composable
private fun CourseFactsCard(
    classTimeText: String,
    weeksText: String,
    examCountdown: ExamCountdown?,
    location: String,
    teacher: String,
    manual: Boolean,
    pluginOverride: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
            if (location.isNotBlank()) {
                DetailRow(
                    icon = Icons.Rounded.LocationOn,
                    title = stringResource(R.string.schedule_course_detail_location),
                    body = location,
                )
            }
            if (teacher.isNotBlank()) {
                DetailRow(
                    icon = Icons.Rounded.Person,
                    title = stringResource(R.string.schedule_course_detail_teacher),
                    body = teacher,
                )
            }
            // 数据来源信息量低，降为浅色脚注一行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Source,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(R.string.schedule_course_detail_source) + " · " +
                        stringResource(
                            when {
                                pluginOverride -> R.string.schedule_source_plugin_edited
                                manual -> R.string.schedule_source_manual
                                else -> R.string.schedule_source_plugin
                            },
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 底部操作条。
 * 每个动作等宽平分一行，图标在上、短标签在下：按钮个数随课程来源变化，
 * 用等宽格子就不会像并排的文字按钮那样一溢出就每行只剩一个。
 */
@Composable
private fun CourseActionBar(
    temporarilyCancelled: Boolean,
    showTemporaryCancel: Boolean,
    showDelete: Boolean,
    showRestorePlugin: Boolean,
    hasReminder: Boolean,
    onEdit: () -> Unit,
    onSetReminder: () -> Unit,
    onCancelReminder: () -> Unit,
    onTemporaryCancel: () -> Unit,
    onRestoreTemporaryCancel: () -> Unit,
    onDelete: () -> Unit,
    onRestorePlugin: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            DetailAction(
                icon = Icons.Rounded.Edit,
                label = stringResource(R.string.schedule_action_edit),
                onClick = onEdit,
            )
            // 已经有提醒时这里变成撤掉它，免得详情页永远只给「提醒」一个方向
            DetailAction(
                icon = if (hasReminder) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive,
                label = stringResource(
                    if (hasReminder) R.string.schedule_action_cancel_reminder else R.string.schedule_action_reminder,
                ),
                onClick = if (hasReminder) onCancelReminder else onSetReminder,
            )
            if (showTemporaryCancel) {
                DetailAction(
                    icon = if (temporarilyCancelled) Icons.Rounded.Restore else Icons.Rounded.Close,
                    label = stringResource(
                        if (temporarilyCancelled) {
                            R.string.schedule_course_detail_restore_cancel_short
                        } else {
                            R.string.schedule_course_detail_temp_cancel
                        },
                    ),
                    onClick = if (temporarilyCancelled) onRestoreTemporaryCancel else onTemporaryCancel,
                )
            }
            if (showRestorePlugin) {
                DetailAction(
                    icon = Icons.Rounded.SettingsBackupRestore,
                    label = stringResource(R.string.schedule_action_restore_plugin),
                    onClick = onRestorePlugin,
                )
            }
            if (showDelete) {
                DetailAction(
                    icon = Icons.Rounded.DeleteOutline,
                    label = stringResource(R.string.schedule_action_delete),
                    onClick = onDelete,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RowScope.DetailAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
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

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.schedule_note_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (!editing) {
                TextButton(
                    onClick = { editing = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                ) {
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
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = savedNote.ifBlank { stringResource(R.string.schedule_note_empty) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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

/**
 * 右上角那枚状态标记。
 *
 * 它说的是「这门课在你正在看的这一周上不上」，而不是「在真实的本周上不上」。
 * 两者混为一谈就会出现：翻到第 12 周点开一门课，标记却写着「本周」。
 * 所以只有正在看的恰好就是当前教学周时才写「本周」，否则把周次写出来。
 */
@Composable
private fun StatusChip(
    activeInVisibleWeek: Boolean?,
    visibleWeekNumber: Int?,
    viewingCurrentWeek: Boolean,
    manual: Boolean,
) {
    val (label, container, content) = when {
        activeInVisibleWeek == null -> Triple(
            stringResource(R.string.schedule_status_week_unknown),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        !activeInVisibleWeek -> Triple(
            if (viewingCurrentWeek || visibleWeekNumber == null) {
                stringResource(R.string.schedule_status_other_week)
            } else {
                stringResource(R.string.schedule_status_week_inactive, visibleWeekNumber)
            },
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        manual -> Triple(
            stringResource(R.string.schedule_status_manual),
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        else -> Triple(
            if (viewingCurrentWeek || visibleWeekNumber == null) {
                stringResource(R.string.schedule_status_this_week)
            } else {
                stringResource(R.string.schedule_status_week_active, visibleWeekNumber)
            },
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
            maxLines = 1,
        )
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp),
        )
        // 名称给个下限宽度让各行取值大致对齐，超出时照常撑开，不裁字
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 1.dp)
                .widthIn(min = 56.dp),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun examCountdownText(countdown: ExamCountdown): String = when (countdown.daysRemaining) {
    0L -> stringResource(R.string.schedule_exam_countdown_today)
    1L -> stringResource(R.string.schedule_exam_countdown_tomorrow)
    else -> pluralStringResource(
        R.plurals.schedule_exam_countdown_days,
        countdown.daysRemaining.toInt(),
        countdown.daysRemaining,
    )
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
    // 连续 / 单双周本身已说清是哪些周，不再把每一周都罗列一遍
    is WeeksDetail.Consecutive ->
        resources.getQuantityString(
            R.plurals.schedule_weeks_detail_consecutive,
            detail.count,
            detail.first,
            detail.last,
            detail.count,
        )
    is WeeksDetail.Odd ->
        getString(R.string.schedule_weeks_detail_odd, detail.first, detail.last, detail.count)
    is WeeksDetail.Even ->
        getString(R.string.schedule_weeks_detail_even, detail.first, detail.last, detail.count)
    // 不规则周次没有简洁概括，仍把具体周次列出来
    is WeeksDetail.Count ->
        resources.getQuantityString(R.plurals.schedule_weeks_detail_count, detail.count, detail.count) +
            "\n" + detail.weeksList
}
