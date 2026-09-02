package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.weekdayNameRes

/**
 * 本学期全部课程的平铺列表。
 * 课多时不必在网格里逐格点开找，可直接搜索、编辑、删除。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CourseLibraryScreen(
    entries: List<CourseLibraryEntry>,
    columnDayOfWeeks: List<Int>,
    onAddCourse: (CourseItem) -> Unit,
    onUpdateCourse: (CourseItem) -> Unit,
    onRemoveCourse: (String) -> Unit,
    maxNodeCount: Int,
    maxWeekCount: Int,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(CourseSortMode.ByWeekday) }
    var editing by remember { mutableStateOf<CourseItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CourseLibraryEntry?>(null) }

    val allCourses = remember(entries) { entries.map { it.course } }
    val matched = remember(entries, query, sortMode) {
        sortCourseLibrary(entries.filter { matchesCourseQuery(it.course, query) }, sortMode)
    }

    if (adding || editing != null) {
        val initial = editing
        AddCourseDialog(
            onDismiss = { adding = false; editing = null },
            onConfirm = { course ->
                if (initial == null) onAddCourse(course) else onUpdateCourse(course)
                adding = false
                editing = null
            },
            existingCourses = allCourses.filterNot { it.id == initial?.id },
            maxNodeCount = maxNodeCount,
            maxWeekCount = maxWeekCount,
            initial = initial,
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.schedule_library_delete_title)) },
            text = { Text(stringResource(R.string.schedule_library_delete_body, target.course.title)) },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveCourse(target.course.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.schedule_library_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.schedule_library_delete_cancel))
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            singleLine = true,
            label = { Text(stringResource(R.string.schedule_library_search)) },
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CourseSortMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == sortMode,
                    onClick = { sortMode = mode },
                    label = { Text(stringResource(courseSortModeLabel(mode)), maxLines = 2) },
                )
            }
            Button(onClick = { adding = true }) {
                Text(stringResource(R.string.schedule_library_add), maxLines = 2)
            }
        }

        Text(
            text = stringResource(R.string.schedule_library_summary, matched.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            entries.isEmpty() -> CourseLibraryHint(stringResource(R.string.schedule_library_empty))
            matched.isEmpty() -> CourseLibraryHint(stringResource(R.string.schedule_library_no_match))
            else -> CourseLibraryList(
                matched = matched,
                sortMode = sortMode,
                columnDayOfWeeks = columnDayOfWeeks,
                onEdit = { editing = it },
                onDelete = { pendingDelete = it },
            )
        }
    }
}

@Composable
private fun CourseLibraryList(
    matched: List<CourseLibraryEntry>,
    sortMode: CourseSortMode,
    columnDayOfWeeks: List<Int>,
    onEdit: (CourseItem) -> Unit,
    onDelete: (CourseLibraryEntry) -> Unit,
) {
    // 只有按星期排序时分组才有意义，其余两种保持平铺
    val grouped = remember(matched, sortMode, columnDayOfWeeks) {
        if (sortMode == CourseSortMode.ByWeekday) {
            groupCourseLibraryByWeekday(matched, columnDayOfWeeks)
        } else {
            emptyList()
        }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (grouped.isEmpty()) {
            items(matched, key = { it.course.id }) { entry ->
                CourseLibraryRow(entry, onEdit, onDelete)
            }
        } else {
            grouped.forEach { (dayOfWeek, dayEntries) ->
                item(key = "header-$dayOfWeek") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(weekdayNameRes(dayOfWeek)),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.schedule_library_group_count, dayEntries.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(dayEntries, key = { it.course.id }) { entry ->
                    CourseLibraryRow(entry, onEdit, onDelete)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseLibraryRow(
    entry: CourseLibraryEntry,
    onEdit: (CourseItem) -> Unit,
    onDelete: (CourseLibraryEntry) -> Unit,
) {
    val course = entry.course
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.editable) { onEdit(course) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = course.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                CourseLibraryTag(
                    stringResource(
                        if (entry.source == CourseSource.Manual) {
                            R.string.schedule_library_source_manual
                        } else {
                            R.string.schedule_library_source_plugin
                        },
                    ),
                )
                if (course.category == CourseCategory.Exam) {
                    CourseLibraryTag(stringResource(R.string.schedule_category_exam))
                }
            }
            Text(
                text = courseLibrarySubtitle(course),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!entry.editable) {
                Text(
                    text = stringResource(R.string.schedule_library_plugin_readonly),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onEdit(course) }) {
                        Text(stringResource(R.string.schedule_action_edit), maxLines = 2)
                    }
                    OutlinedButton(onClick = { onDelete(entry) }) {
                        Text(stringResource(R.string.schedule_action_delete), maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseLibraryTag(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CourseLibraryHint(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun courseLibrarySubtitle(course: CourseItem): String {
    val weekday = stringResource(weekdayNameRes(course.time.dayOfWeek))
    val nodes = stringResource(
        R.string.schedule_node_range,
        course.time.startNode,
        course.time.endNode,
    )
    val parts = buildList {
        add("$weekday · $nodes")
        course.location.takeIf { it.isNotBlank() }?.let(::add)
        course.teacher.takeIf { it.isNotBlank() }?.let(::add)
    }
    return parts.joinToString(" · ")
}

private fun courseSortModeLabel(mode: CourseSortMode): Int = when (mode) {
    CourseSortMode.ByWeekday -> R.string.schedule_library_sort_weekday
    CourseSortMode.ByTitle -> R.string.schedule_library_sort_title
    CourseSortMode.BySource -> R.string.schedule_library_sort_source
}
