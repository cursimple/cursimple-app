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
import androidx.compose.material.icons.rounded.Warning
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.CourseConflict
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.findCourseConflicts

/** 冲突列表最多直接列出几条，其余折成一行计数，避免把底部弹窗撑爆。 */
private const val CONFLICT_ROW_LIMIT = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScheduleSheet(
    manualCourses: List<CourseItem>,
    importedCourses: List<CourseItem> = emptyList(),
    maxWeekCount: Int = 30,
    onDismiss: () -> Unit,
    onAddSingleCourse: () -> Unit,
    onLoadSample: () -> Unit,
    onClearAll: () -> Unit,
    onClearEverything: () -> Unit,
    onRemoveCourse: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val conflicts = remember(manualCourses, importedCourses, maxWeekCount) {
        findCourseConflicts(manualCourses + importedCourses, maxWeekCount)
    }
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
                text = stringResource(R.string.schedule_manage_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.schedule_manage_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (conflicts.isNotEmpty()) {
                ConflictSection(conflicts = conflicts)
            }

            ActionRow(
                icon = Icons.Rounded.Add,
                title = stringResource(R.string.schedule_manage_add_title),
                subtitle = stringResource(R.string.schedule_manage_add_subtitle),
                onClick = onAddSingleCourse,
            )
            ActionRow(
                icon = Icons.Rounded.AutoFixHigh,
                title = stringResource(R.string.schedule_manage_sample_title),
                subtitle = stringResource(R.string.schedule_manage_sample_subtitle),
                onClick = onLoadSample,
            )
            ActionRow(
                icon = Icons.Rounded.DeleteOutline,
                title = stringResource(R.string.schedule_manage_clear_all_title),
                subtitle = stringResource(R.string.schedule_manage_clear_all_subtitle),
                onClick = onClearEverything,
                danger = true,
            )

            if (manualCourses.isNotEmpty()) {
                ActionRow(
                    icon = Icons.Rounded.DeleteOutline,
                    title = stringResource(R.string.schedule_manage_clear_manual_title),
                    subtitle = stringResource(R.string.schedule_manage_clear_manual_subtitle),
                    onClick = onClearAll,
                    danger = true,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = stringResource(R.string.schedule_manage_added_count, manualCourses.size),
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
                    text = stringResource(R.string.schedule_manage_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.size(12.dp))
        }
    }
}

/**
 * 当前课表里所有时间冲突。
 * 只列出同一天、节次重叠且教学周有交集的课程对，单双周交替不在其中。
 */
@Composable
private fun ConflictSection(conflicts: List<CourseConflict>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.schedule_conflict_count, conflicts.size),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
        )
        conflicts.take(CONFLICT_ROW_LIMIT).forEach { conflict ->
            ConflictRow(conflict = conflict)
        }
        val rest = conflicts.size - CONFLICT_ROW_LIMIT
        if (rest > 0) {
            Text(
                text = stringResource(R.string.schedule_conflict_more, rest),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.schedule_conflict_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConflictRow(conflict: CourseConflict) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = context.conflictPairTitleText(conflictPairTitle(conflict)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(
                        R.string.schedule_conflict_kind_and_scope,
                        stringResource(conflictKindNameRes(conflict.kind)),
                        context.conflictScopeText(conflictScope(conflict)),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.78f),
                )
            }
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
    val weekday = if (course.time.dayOfWeek in 1..7) {
        stringResource(scheduleWeekdayFullRes(course.time.dayOfWeek))
    } else {
        "?"
    }
    val nodeText = if (course.time.startNode == course.time.endNode) {
        stringResource(R.string.schedule_node_single, course.time.startNode)
    } else {
        stringResource(R.string.schedule_node_range, course.time.startNode, course.time.endNode)
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
                        append("$weekday · $nodeText · $weeksLabel")
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
                    contentDescription = stringResource(R.string.schedule_action_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun describeWeeks(weeks: List<Int>): String {
    if (weeks.isEmpty()) return stringResource(R.string.schedule_weeks_all_short)
    val sorted = weeks.sorted()
    val first = sorted.first()
    val last = sorted.last()
    val full = (first..last).toList()
    val odd = full.filter { it % 2 == 1 }
    val even = full.filter { it % 2 == 0 }
    return when {
        sorted == full -> stringResource(R.string.schedule_weeks_range, first, last)
        sorted == odd -> stringResource(R.string.schedule_weeks_range_odd, first, last)
        sorted == even -> stringResource(R.string.schedule_weeks_range_even, first, last)
        else -> stringResource(R.string.schedule_weeks_count, sorted.size)
    }
}
