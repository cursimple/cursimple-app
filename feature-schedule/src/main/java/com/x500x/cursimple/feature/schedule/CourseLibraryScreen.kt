package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.core.kernel.model.weekdayNameRes
import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import com.x500x.cursimple.feature.plugin.ui.AppFilterChip
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.plugin.ui.AppSearchField
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import com.x500x.cursimple.feature.schedule.time.today
import java.time.LocalDate

private const val COMPACT_HEADER_MAX_HEIGHT_DP = 500

/** Searchable term-wide course list with reversible manual overrides for plugin courses. */
@Composable
internal fun CourseLibraryScreen(
    entries: List<CourseLibraryEntry>,
    columnDayOfWeeks: List<Int>,
    onAddCourse: (CourseItem) -> Unit,
    onUpdateCourse: (CourseItem) -> Unit,
    onRemoveCourse: (String) -> Unit,
    maxNodeCount: Int,
    maxWeekCount: Int,
    termStartDate: LocalDate? = null,
    slotTimes: List<com.x500x.cursimple.core.kernel.model.ClassSlotTime> = emptyList(),
    /** Deleted-course tombstones enable the restoration entry point. */
    hiddenCourses: List<CourseItem> = emptyList(),
    onRestoreCourse: (String) -> Unit = {},
    onPurgeCourse: (String) -> Unit = {},
    onSetReminder: (CourseItem) -> Unit = {},
    events: List<ScheduleEvent> = emptyList(),
    onSaveEvent: (ScheduleEvent) -> Unit = {},
    onRemoveEvent: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(LibraryTab.Courses) }
    var eventEditing by remember { mutableStateOf<ScheduleEvent?>(null) }
    var addingEvent by remember { mutableStateOf(false) }
    val appZone = LocalAppZone.current
    val today = remember(appZone) { appZone.today() }
    if (addingEvent || eventEditing != null) {
        ScheduleEventEditorDialog(
            initial = eventEditing,
            defaultDate = today,
            onDismiss = {
                addingEvent = false
                eventEditing = null
            },
            onSave = { event ->
                onSaveEvent(event)
                addingEvent = false
                eventEditing = null
                tab = LibraryTab.Events
            },
            termStartDate = termStartDate,
            maxWeekCount = maxWeekCount,
        )
    }
    val matchedEvents = remember(events, query) { events.filter { matchesEventQuery(it, query) } }
    var sortMode by rememberSaveable { mutableStateOf(CourseSortMode.ByWeekday) }
    var editing by remember { mutableStateOf<CourseItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CourseLibraryEntry?>(null) }
    var pendingRestore by remember { mutableStateOf<CourseLibraryEntry?>(null) }
    var moving by remember { mutableStateOf<CourseItem?>(null) }
    var pendingMove by remember { mutableStateOf<Pair<CourseItem, CourseTimeSlot>?>(null) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var pendingPurge by remember { mutableStateOf<CourseItem?>(null) }

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
            slotTimes = slotTimes,
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
                AppOutlinedButton(onClick = {
                    onRemoveCourse(target.course.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.schedule_library_delete_confirm)) }
            },
            dismissButton = {
                AppOutlinedButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.schedule_library_delete_cancel))
                }
            },
        )
    }

    // Removing a manual override reveals the plugin original; label this as restoration.
    pendingRestore?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.schedule_library_restore_title)) },
            text = { Text(stringResource(R.string.schedule_library_restore_body, target.course.title)) },
            confirmButton = {
                AppOutlinedButton(onClick = {
                    onRemoveCourse(target.course.id)
                    pendingRestore = null
                }) { Text(stringResource(R.string.schedule_library_restore_confirm)) }
            },
            dismissButton = {
                AppOutlinedButton(onClick = { pendingRestore = null }) {
                    Text(stringResource(R.string.schedule_library_delete_cancel))
                }
            },
        )
    }

    pendingPurge?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingPurge = null },
            title = { Text(stringResource(R.string.schedule_library_purge_title)) },
            text = { Text(stringResource(R.string.schedule_library_purge_body, target.title)) },
            confirmButton = {
                Button(
                    onClick = {
                        onPurgeCourse(target.id)
                        pendingPurge = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(stringResource(R.string.schedule_library_purge)) }
            },
            dismissButton = {
                AppOutlinedButton(onClick = { pendingPurge = null }) {
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
                AppOutlinedButton(onClick = {
                    onUpdateCourse(course.copy(time = time))
                    pendingMove = null
                }) { Text(stringResource(R.string.schedule_move_dialog_confirm)) }
            },
            dismissButton = {
                AppOutlinedButton(onClick = { pendingMove = null }) {
                    Text(stringResource(R.string.schedule_action_cancel))
                }
            },
        )
    }

    // Compact landscape keeps search and tabs on one row to preserve list height.
    val compactHeader = LocalConfiguration.current.screenHeightDp < COMPACT_HEADER_MAX_HEIGHT_DP
    val searchField = @Composable { fieldModifier: Modifier ->
        AppSearchField(
            query = query,
            onQueryChange = { query = it },
            hint = stringResource(R.string.schedule_library_search),
            clearLabel = stringResource(R.string.memo_search_clear),
            modifier = fieldModifier,
            onClear = { query = "" },
        )
    }
    val tabSwitch = @Composable { switchModifier: Modifier ->
        SingleChoiceSegmentedButtonRow(modifier = switchModifier) {
            LibraryTab.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = tab == entry,
                    onClick = { tab = entry },
                    shape = SegmentedButtonDefaults.itemShape(index, LibraryTab.entries.size),
                    // Match the selected filter chips below instead of the secondary tint.
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Text(
                        stringResource(
                            if (entry == LibraryTab.Courses) R.string.schedule_library_tab_courses
                            else R.string.schedule_library_tab_events,
                            if (entry == LibraryTab.Courses) entries.size else events.size,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (compactHeader) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    searchField(Modifier.weight(1f))
                    tabSwitch(Modifier.weight(1f))
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    searchField(Modifier)
                    tabSwitch(Modifier.fillMaxWidth())
                }
            }

            if (tab == LibraryTab.Events) {
                when {
                    events.isEmpty() -> CourseLibraryHint(stringResource(R.string.schedule_library_events_empty))
                    matchedEvents.isEmpty() -> CourseLibraryHint(stringResource(R.string.schedule_library_no_match))
                    else -> EventLibraryList(
                        events = matchedEvents,
                        today = today,
                        onEdit = { eventEditing = it },
                        onDelete = onRemoveEvent,
                        contentPadding = LibraryListPadding,
                    )
                }
            } else {
                CourseLibraryList(
                    entries = entries,
                    matched = matched,
                    searching = query.isNotBlank(),
                    sortMode = sortMode,
                    onSortModeChange = { sortMode = it },
                    columnDayOfWeeks = columnDayOfWeeks,
                    hiddenCourses = hiddenCourses,
                    showHidden = showHidden,
                    onToggleHidden = { showHidden = !showHidden },
                    onRestoreHidden = onRestoreCourse,
                    onPurgeHidden = { pendingPurge = it },
                    onEdit = { editing = it },
                    onDelete = { pendingDelete = it },
                    onRestore = { pendingRestore = it },
                    onMove = { moving = it },
                    onSetReminder = onSetReminder,
                )
            }
        }
        // The add action follows the visible tab, as on the notes page.
        ExtendedFloatingActionButton(
            onClick = { if (tab == LibraryTab.Courses) adding = true else addingEvent = true },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = {
                Text(
                    stringResource(
                        if (tab == LibraryTab.Courses) R.string.schedule_library_add else R.string.schedule_library_add_event,
                    ),
                    maxLines = 1,
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        )
    }
}

/** Leaves room under the last card for the floating add button. */
private val LibraryListPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseLibraryList(
    entries: List<CourseLibraryEntry>,
    matched: List<CourseLibraryEntry>,
    searching: Boolean,
    sortMode: CourseSortMode,
    onSortModeChange: (CourseSortMode) -> Unit,
    columnDayOfWeeks: List<Int>,
    hiddenCourses: List<CourseItem>,
    showHidden: Boolean,
    onToggleHidden: () -> Unit,
    onRestoreHidden: (String) -> Unit,
    onPurgeHidden: (CourseItem) -> Unit,
    onEdit: (CourseItem) -> Unit,
    onDelete: (CourseLibraryEntry) -> Unit,
    onRestore: (CourseLibraryEntry) -> Unit,
    onMove: (CourseItem) -> Unit,
    onSetReminder: (CourseItem) -> Unit,
) {
    // Group only under weekday sorting; other modes remain flat.
    val grouped = remember(matched, sortMode, columnDayOfWeeks) {
        if (sortMode == CourseSortMode.ByWeekday) {
            groupCourseLibraryByWeekday(matched, columnDayOfWeeks)
        } else {
            emptyList()
        }
    }
    val hasPluginCourses = remember(matched) { matched.any { it.source == CourseSource.Plugin } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = LibraryListPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "controls") {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CourseSortMode.entries.forEach { mode ->
                    AppFilterChip(
                        selected = mode == sortMode,
                        onClick = { onSortModeChange(mode) },
                        label = { Text(stringResource(courseSortModeLabel(mode)), maxLines = 1) },
                    )
                }
                if (hiddenCourses.isNotEmpty()) {
                    AppFilterChip(
                        selected = showHidden,
                        onClick = onToggleHidden,
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, Modifier.size(18.dp)) },
                        label = {
                            Text(
                                stringResource(
                                    if (showHidden) R.string.schedule_library_hidden_hide else R.string.schedule_library_hidden_show,
                                    hiddenCourses.size,
                                ),
                                maxLines = 1,
                            )
                        },
                    )
                }
            }
        }
        if (showHidden && hiddenCourses.isNotEmpty()) {
            items(hiddenCourses, key = { "hidden-${it.id}" }) { course ->
                HiddenCourseRow(course = course, onRestore = onRestoreHidden, onPurge = onPurgeHidden)
            }
        }
        when {
            entries.isEmpty() -> item(key = "empty") { CourseLibraryHint(stringResource(R.string.schedule_library_empty)) }
            matched.isEmpty() -> item(key = "empty") { CourseLibraryHint(stringResource(R.string.schedule_library_no_match)) }
            else -> {
                if (searching) {
                    item(key = "summary") {
                        Text(
                            text = pluralStringResource(R.plurals.schedule_library_summary, matched.size, matched.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Said once for the list instead of on every synced card.
                if (hasPluginCourses) {
                    item(key = "plugin-hint") { PluginEditableHint() }
                }
                if (grouped.isEmpty()) {
                    items(matched, key = { it.course.id }) { entry ->
                        CourseLibraryRow(entry, onEdit, onDelete, onRestore, onMove, onSetReminder)
                    }
                } else {
                    grouped.forEach { (dayOfWeek, dayEntries) ->
                        item(key = "header-$dayOfWeek") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(weekdayNameRes(dayOfWeek)),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = pluralStringResource(R.plurals.schedule_library_group_count, dayEntries.size, dayEntries.size),
                                    style = MaterialTheme.typography.labelMedium,
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
    }
}

@Composable
private fun PluginEditableHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Rounded.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.schedule_library_plugin_editable_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = { onEdit(course) },
        enabled = entry.editable,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowRow(
                    modifier = Modifier.weight(1f).padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = course.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 2.dp),
                    )
                    CourseLibraryTag(
                        stringResource(
                            if (entry.source == CourseSource.Manual && !entry.overridesPlugin) {
                                R.string.schedule_library_source_manual
                            } else {
                                R.string.schedule_library_source_plugin
                            },
                        ),
                    )
                    if (entry.overridesPlugin) {
                        CourseLibraryTag(
                            stringResource(R.string.schedule_library_source_manual_edited),
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    if (course.category == CourseCategory.Exam) {
                        CourseLibraryTag(
                            stringResource(R.string.schedule_category_exam),
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box {
                    AppCircleIconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.memo_more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.schedule_action_edit)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = { menu = false; onEdit(course) },
                        )
                        if (entry.restorable) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.schedule_action_restore_plugin)) },
                                leadingIcon = { Icon(Icons.Rounded.Restore, contentDescription = null) },
                                onClick = { menu = false; onRestore(entry) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.schedule_action_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = { menu = false; onDelete(entry) },
                        )
                    }
                }
            }
            FlowRow(
                modifier = Modifier.padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CourseLibraryFact(
                    Icons.Rounded.Schedule,
                    stringResource(weekdayNameRes(course.time.dayOfWeek)) + " · " +
                        stringResource(R.string.schedule_node_range, course.time.startNode, course.time.endNode),
                )
                course.location.takeIf { it.isNotBlank() }?.let { CourseLibraryFact(Icons.Rounded.Place, it) }
                course.teacher.takeIf { it.isNotBlank() }?.let { CourseLibraryFact(Icons.Rounded.Person, it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppOutlinedButton(onClick = { onMove(course) }) {
                    AppAtomicText(stringResource(R.string.schedule_course_action_move))
                }
                AppOutlinedButton(onClick = { onSetReminder(course) }) {
                    AppAtomicText(stringResource(R.string.schedule_action_reminder))
                }
            }
        }
    }
}

@Composable
private fun CourseLibraryFact(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HiddenCourseRow(
    course: CourseItem,
    onRestore: (String) -> Unit,
    onPurge: (CourseItem) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = course.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = courseLibrarySubtitle(course),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                AppOutlinedButton(
                    onClick = { onPurge(course) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                ) {
                    AppAtomicText(stringResource(R.string.schedule_library_purge))
                }
                AppOutlinedButton(onClick = { onRestore(course.id) }) {
                    AppAtomicText(stringResource(R.string.schedule_action_restore))
                }
            }
        }
    }
}

@Composable
private fun CourseLibraryTag(
    text: String,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = container,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}

@Composable
private fun CourseLibraryHint(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
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
