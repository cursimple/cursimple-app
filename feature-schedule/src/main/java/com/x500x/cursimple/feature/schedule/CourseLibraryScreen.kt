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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import androidx.compose.ui.platform.LocalConfiguration
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.weekdayNameRes

/** 屏幕高度低于这条线就把页头收成两行；横屏手机大多在 400dp 上下。 */
private const val COMPACT_HEADER_MAX_HEIGHT_DP = 500

/**
 * 本学期全部课程的平铺列表。
 * 课多时不必在网格里逐格点开找，可直接搜索、编辑、删除。
 * 插件同步的课也能改，改完会转成手动课程，随时能还原回插件那一份。
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
    /** 被删掉、只剩墓碑的课；不为空时列表上方给出恢复入口。 */
    hiddenCourses: List<CourseItem> = emptyList(),
    onRestoreCourse: (String) -> Unit = {},
    onSetReminder: (CourseItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(CourseSortMode.ByWeekday) }
    var editing by remember { mutableStateOf<CourseItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CourseLibraryEntry?>(null) }
    var pendingRestore by remember { mutableStateOf<CourseLibraryEntry?>(null) }
    var moving by remember { mutableStateOf<CourseItem?>(null) }
    var pendingMove by remember { mutableStateOf<Pair<CourseItem, CourseTimeSlot>?>(null) }
    var showHidden by rememberSaveable { mutableStateOf(false) }

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

    // 改过的插件课删掉的只是那份手动覆盖，插件原件会重新露出来，所以叫"还原"而不是"删除"
    pendingRestore?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.schedule_library_restore_title)) },
            text = { Text(stringResource(R.string.schedule_library_restore_body, target.course.title)) },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveCourse(target.course.id)
                    pendingRestore = null
                }) { Text(stringResource(R.string.schedule_library_restore_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) {
                    Text(stringResource(R.string.schedule_library_delete_cancel))
                }
            },
        )
    }

    moving?.let { course ->
        MoveCourseDialog(
            course = course,
            maxNodeCount = maxNodeCount,
            existingCourses = allCourses,
            onDismiss = { moving = null },
            onConfirm = { time ->
                moving = null
                pendingMove = course to time
            },
        )
    }

    // 移动是易误触的改动，落库前再确认一次
    pendingMove?.let { (course, time) ->
        AlertDialog(
            onDismissRequest = { pendingMove = null },
            title = { Text(stringResource(R.string.schedule_drag_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.schedule_drag_confirm_move,
                        course.title,
                        stringResource(weekdayNameRes(time.dayOfWeek)),
                        time.startNode,
                        time.endNode,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateCourse(course.copy(time = time))
                    pendingMove = null
                }) { Text(stringResource(R.string.schedule_move_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingMove = null }) {
                    Text(stringResource(R.string.schedule_action_cancel))
                }
            },
        )
    }

    // 横屏高度只有几百 dp，搜索框、筛选、统计各占一行的话列表就没地方了：横屏时并排收成两行
    val compactHeader = LocalConfiguration.current.screenHeightDp < COMPACT_HEADER_MAX_HEIGHT_DP
    val searchField = @Composable { fieldModifier: Modifier ->
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = fieldModifier,
            singleLine = true,
            label = { Text(stringResource(R.string.schedule_library_search)) },
        )
    }
    val addButton = @Composable {
        Button(onClick = { adding = true }) {
            Text(stringResource(R.string.schedule_library_add), maxLines = 2)
        }
    }
    val summaryText = @Composable { textModifier: Modifier ->
        Text(
            text = pluralStringResource(R.plurals.schedule_library_summary, matched.size, matched.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = textModifier,
        )
    }
    val hiddenToggle = @Composable {
        if (hiddenCourses.isNotEmpty()) {
            TextButton(onClick = { showHidden = !showHidden }) {
                Text(
                    stringResource(
                        if (showHidden) {
                            R.string.schedule_library_hidden_hide
                        } else {
                            R.string.schedule_library_hidden_show
                        },
                        hiddenCourses.size,
                    ),
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (compactHeader) 6.dp else 10.dp),
    ) {
        if (compactHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                searchField(Modifier.weight(1f))
                addButton()
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CourseSortMode.entries.forEach { mode ->
                    FilterChip(
                        selected = mode == sortMode,
                        onClick = { sortMode = mode },
                        label = { Text(stringResource(courseSortModeLabel(mode)), maxLines = 1) },
                    )
                }
                summaryText(Modifier.align(Alignment.CenterVertically))
                hiddenToggle()
            }
        } else {
            searchField(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
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
                addButton()
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                summaryText(Modifier.weight(1f))
                hiddenToggle()
            }
        }

        if (showHidden && hiddenCourses.isNotEmpty()) {
            HiddenCourseList(hiddenCourses = hiddenCourses, onRestore = onRestoreCourse)
        }

        when {
            entries.isEmpty() -> CourseLibraryHint(stringResource(R.string.schedule_library_empty))
            matched.isEmpty() -> CourseLibraryHint(stringResource(R.string.schedule_library_no_match))
            else -> CourseLibraryList(
                matched = matched,
                sortMode = sortMode,
                columnDayOfWeeks = columnDayOfWeeks,
                onEdit = { editing = it },
                onDelete = { pendingDelete = it },
                onRestore = { pendingRestore = it },
                onMove = { moving = it },
                onSetReminder = onSetReminder,
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
    onRestore: (CourseLibraryEntry) -> Unit,
    onMove: (CourseItem) -> Unit,
    onSetReminder: (CourseItem) -> Unit,
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
                CourseLibraryRow(entry, onEdit, onDelete, onRestore, onMove, onSetReminder)
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
                            text = pluralStringResource(R.plurals.schedule_library_group_count, dayEntries.size, dayEntries.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(dayEntries, key = { it.course.id }) { entry ->
                    CourseLibraryRow(entry, onEdit, onDelete, onRestore, onMove, onSetReminder)
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
    onRestore: (CourseLibraryEntry) -> Unit,
    onMove: (CourseItem) -> Unit,
    onSetReminder: (CourseItem) -> Unit,
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
                        when {
                            entry.overridesPlugin -> R.string.schedule_library_source_plugin
                            entry.source == CourseSource.Manual -> R.string.schedule_library_source_manual
                            else -> R.string.schedule_library_source_plugin
                        },
                    ),
                )
                // 改过的插件课两个标都挂上：来源仍是插件，但眼下这份是用户自己的
                if (entry.overridesPlugin) {
                    CourseLibraryTag(stringResource(R.string.schedule_library_source_manual_edited))
                }
                if (course.category == CourseCategory.Exam) {
                    CourseLibraryTag(stringResource(R.string.schedule_category_exam))
                }
            }
            Text(
                text = courseLibrarySubtitle(course),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.source == CourseSource.Plugin) {
                Text(
                    text = stringResource(R.string.schedule_library_plugin_editable_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedButton(onClick = { onEdit(course) }) {
                    Text(stringResource(R.string.schedule_action_edit), maxLines = 2)
                }
                OutlinedButton(onClick = { onMove(course) }) {
                    Text(stringResource(R.string.schedule_course_action_move), maxLines = 2)
                }
                OutlinedButton(onClick = { onSetReminder(course) }) {
                    Text(stringResource(R.string.schedule_action_reminder), maxLines = 2)
                }
                if (entry.restorable) {
                    OutlinedButton(onClick = { onRestore(entry) }) {
                        Text(stringResource(R.string.schedule_action_restore_plugin), maxLines = 2)
                    }
                }
                // 插件课删不掉原件，但可以把它盖住；两种情况用户看到的都是「删除」
                OutlinedButton(onClick = { onDelete(entry) }) {
                    Text(stringResource(R.string.schedule_action_delete), maxLines = 2)
                }
            }
        }
    }
}

/** 已删除课程的墓碑列表，给一条回头路。 */
@Composable
private fun HiddenCourseList(
    hiddenCourses: List<CourseItem>,
    onRestore: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        hiddenCourses.forEach { course ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = course.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Text(
                            text = courseLibrarySubtitle(course),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    TextButton(onClick = { onRestore(course.id) }) {
                        Text(stringResource(R.string.schedule_action_restore))
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
