package com.kebiao.viewer.feature.schedule

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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kebiao.viewer.core.kernel.model.ClassSlotTime
import com.kebiao.viewer.core.kernel.model.CourseCategory
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.TermTimingProfile

@Composable
fun CourseDetailDialog(
    courses: List<CourseItem>,
    timingProfile: TermTimingProfile?,
    visibleWeekNumber: Int,
    isManual: (CourseItem) -> Boolean,
    onDismiss: () -> Unit,
    onSetReminder: (CourseItem) -> Unit,
    onDelete: (CourseItem) -> Unit,
) {
    if (courses.isEmpty()) return
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(courses) {
        if (selectedIndex >= courses.size) selectedIndex = 0
    }
    val course = courses[selectedIndex.coerceIn(0, courses.size - 1)]
    val accents = com.kebiao.viewer.feature.schedule.theme.LocalScheduleAccents.current
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
    val isThisWeek = course.weeks.isEmpty() || course.weeks.contains(visibleWeekNumber)
    val manual = isManual(course)
    val weekday = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        .getOrNull(course.time.dayOfWeek - 1) ?: "?"
    val nodeRange = if (course.time.startNode == course.time.endNode) {
        "第 ${course.time.startNode} 节"
    } else {
        "第 ${course.time.startNode}-${course.time.endNode} 节"
    }
    val classTimeText = remember(course, timingProfile) { resolveClassTime(course, timingProfile) }
    val weeksText = remember(course.weeks) { describeWeeksDetail(course.weeks) }

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
                            text = "${if (course.category == CourseCategory.Exam) "考试 · " else ""}$weekday · $nodeRange",
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
                            text = "同位置 ${courses.size} 门：",
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
                        title = "上课时间",
                        body = classTimeText,
                    )
                    DetailRow(
                        icon = Icons.Rounded.CalendarMonth,
                        title = "上课周次",
                        body = weeksText,
                    )
                    if (course.location.isNotBlank()) {
                        DetailRow(
                            icon = Icons.Rounded.LocationOn,
                            title = "地点",
                            body = course.location,
                        )
                    }
                    if (course.teacher.isNotBlank()) {
                        DetailRow(
                            icon = Icons.Rounded.Person,
                            title = "授课教师",
                            body = course.teacher,
                        )
                    }
                    DetailRow(
                        icon = Icons.Rounded.Source,
                        title = "数据来源",
                        body = if (manual) "手动添加" else "插件同步",
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
                                Text("删除", maxLines = 1, softWrap = false)
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
                            Text("设为提醒", maxLines = 1, softWrap = false)
                        }
                        Button(
                            onClick = onDismiss,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text("关闭", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(thisWeek: Boolean, manual: Boolean) {
    val (label, container, content) = when {
        !thisWeek -> Triple(
            "非本周",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        manual -> Triple(
            "手动",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        else -> Triple(
            "本周",
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

private fun resolveClassTime(course: CourseItem, timingProfile: TermTimingProfile?): String {
    val slots: List<ClassSlotTime> = timingProfile?.slotTimes.orEmpty().sortedBy { it.startNode }
    val matchStart = slots.firstOrNull { course.time.startNode in it.startNode..it.endNode }
    val matchEnd = slots.firstOrNull { course.time.endNode in it.startNode..it.endNode }
    if (matchStart != null && matchEnd != null) {
        return "${matchStart.startTime} - ${matchEnd.endTime}"
    }
    // 超出 timing 配置的节次：按 profile 行数 + 顺次给"第 N 大节"
    val baseCount = slots.size
    val extraStart = course.time.startNode - (slots.lastOrNull()?.endNode ?: 0)
    val extraEnd = course.time.endNode - (slots.lastOrNull()?.endNode ?: 0)
    return if (extraStart >= 1 && extraEnd >= 1) {
        if (extraStart == extraEnd) "第 ${baseCount + extraStart} 大节"
        else "第 ${baseCount + extraStart}-${baseCount + extraEnd} 大节"
    } else {
        "第 ${course.time.startNode}-${course.time.endNode} 节"
    }
}

private fun describeWeeksDetail(weeks: List<Int>): String {
    if (weeks.isEmpty()) return "全部周（未指定具体周次）"
    val sorted = weeks.sorted().distinct()
    val first = sorted.first()
    val last = sorted.last()
    val full = (first..last).toList()
    val odd = full.filter { it % 2 == 1 }
    val even = full.filter { it % 2 == 0 }
    val pattern = when {
        sorted == full -> "$first-$last 周（连续 ${sorted.size} 周）"
        sorted == odd -> "$first-$last 周（单周，共 ${sorted.size} 周）"
        sorted == even -> "$first-$last 周（双周，共 ${sorted.size} 周）"
        else -> "${sorted.size} 个周次"
    }
    val list = sorted.joinToString(", ")
    return "$pattern\n$list"
}
