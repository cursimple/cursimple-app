package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.CourseItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScheduleSheet(
    manualCourses: List<CourseItem>,
    onDismiss: () -> Unit,
    onAddSingleCourse: () -> Unit,
    onLoadSample: () -> Unit,
    onClearAll: () -> Unit,
    onClearEverything: () -> Unit,
    onRemoveCourse: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .heightIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "课表管理",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "在这里加载示例数据预览各种排课情况，或手动添加 / 删除自定义课程。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ActionRow(
                icon = Icons.Rounded.Add,
                title = "添加一节课",
                subtitle = "打开新建课程表单（可设单/双周）",
                onClick = onAddSingleCourse,
            )
            ActionRow(
                icon = Icons.Rounded.AutoFixHigh,
                title = "加载示例课表",
                subtitle = "覆盖现有手动课程，含单/双周、连堂、短期等多种情况",
                onClick = onLoadSample,
            )
            ActionRow(
                icon = Icons.Rounded.DeleteOutline,
                title = "清空全部课表",
                subtitle = "同时删除手动课程、插件同步的课表和所有提醒",
                onClick = onClearEverything,
                danger = true,
            )

            if (manualCourses.isNotEmpty()) {
                ActionRow(
                    icon = Icons.Rounded.DeleteOutline,
                    title = "清空手动课表",
                    subtitle = "仅清除本应用手动添加的，不影响插件同步的课表",
                    onClick = onClearAll,
                    danger = true,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "已添加 ${manualCourses.size} 门",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(manualCourses, key = { it.id }) { course ->
                        ManualCourseRow(course = course, onRemove = { onRemoveCourse(course.id) })
                    }
                }
            } else {
                Text(
                    text = "当前没有手动添加的课程",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val contentColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val container = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = container,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun ManualCourseRow(
    course: CourseItem,
    onRemove: () -> Unit,
) {
    val weekday = listOf("一", "二", "三", "四", "五", "六", "日").getOrNull(course.time.dayOfWeek - 1) ?: "?"
    val nodeText = if (course.time.startNode == course.time.endNode) {
        "第 ${course.time.startNode} 节"
    } else {
        "第 ${course.time.startNode}-${course.time.endNode} 节"
    }
    val weeksLabel = describeWeeks(course.weeks)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildString {
                        append("周$weekday · $nodeText · $weeksLabel")
                        if (course.location.isNotBlank()) append(" · ${course.location}")
                        if (course.teacher.isNotBlank()) append(" · ${course.teacher}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun describeWeeks(weeks: List<Int>): String {
    if (weeks.isEmpty()) return "全周"
    val sorted = weeks.sorted()
    val first = sorted.first()
    val last = sorted.last()
    val full = (first..last).toList()
    val odd = full.filter { it % 2 == 1 }
    val even = full.filter { it % 2 == 0 }
    return when {
        sorted == full -> "$first-$last 周"
        sorted == odd -> "$first-$last 周(单)"
        sorted == even -> "$first-$last 周(双)"
        else -> "${sorted.size} 周"
    }
}
