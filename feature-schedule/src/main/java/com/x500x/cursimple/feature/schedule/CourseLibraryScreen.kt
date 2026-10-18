package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.feature.plugin.ui.AppMotion
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import com.x500x.cursimple.feature.plugin.ui.rememberAppHaptics
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.plugin.ui.AppSearchField
import com.x500x.cursimple.feature.plugin.ui.appItemMotion
import com.x500x.cursimple.feature.plugin.ui.pressFeedback
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import com.x500x.cursimple.feature.schedule.time.today
import java.time.LocalDate
import androidx.compose.foundation.interaction.MutableInteractionSource

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
    val haptics = rememberAppHaptics()
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
            onConfirmEvent = if (initial == null) { event ->
                onSaveEvent(event)
                adding = false
                tab = LibraryTab.Events
            } else null,
            existingCourses = allCourses.filterNot { it.id == initial?.id },
            maxNodeCount = maxNodeCount,
            slotTimes = slotTimes,
            maxWeekCount = maxWeekCount,
            initial = initial,
            termStartDate = termStartDate,
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
                    onClick = { haptics.click(); tab = entry },
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

            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = targetState == LibraryTab.Events
                    AppMotion.sharedAxisX(forward)
                },
                label = "library_tab_transition",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { currentTab ->
                if (currentTab == LibraryTab.Events) {
                    when {
                        events.isEmpty() -> CenteredLibraryHint(stringResource(R.string.schedule_library_events_empty))
                        matchedEvents.isEmpty() -> CenteredLibraryHint(stringResource(R.string.schedule_library_no_match))
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
        }
        // The add action follows the visible tab, as on the notes page.
        ExtendedFloatingActionButton(
            onClick = { haptics.click(); if (tab == LibraryTab.Courses) adding = true else addingEvent = true },
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
    // Weekday sorting adds day headers; every mode folds same-name courses into one card.
    val grouped = remember(matched, sortMode, columnDayOfWeeks) {
        if (sortMode == CourseSortMode.ByWeekday) {
            groupCourseLibraryByWeekday(matched, columnDayOfWeeks)
                .map { (day, dayEntries) -> day to groupCourseLibrary(dayEntries, keyPrefix = "d$day|") }
        } else {
            emptyList()
        }
    }
    val flat = remember(matched, sortMode) {
        if (sortMode == CourseSortMode.ByWeekday) emptyList() else groupCourseLibrary(matched)
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
                Box(appItemMotion()) {
                    HiddenCourseRow(course = course, onRestore = onRestoreHidden, onPurge = onPurgeHidden)
                }
            }
        }
        when {
            entries.isEmpty() -> item(key = "empty") {
                Box(Modifier.fillParentMaxWidth().fillParentMaxHeight(0.7f), contentAlignment = Alignment.Center) {
                    CourseLibraryHint(stringResource(R.string.schedule_library_empty))
                }
            }
            matched.isEmpty() -> item(key = "empty") {
                Box(Modifier.fillParentMaxWidth().fillParentMaxHeight(0.7f), contentAlignment = Alignment.Center) {
                    CourseLibraryHint(stringResource(R.string.schedule_library_no_match))
                }
            }
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
                    items(flat, key = { it.key }) { group ->
                        Box(appItemMotion()) {
                            CourseLibraryRow(group, onEdit, onDelete, onRestore, onMove, onSetReminder)
                        }
                    }
                } else {
                    grouped.forEach { (dayOfWeek, dayGroups) ->
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
                                    text = pluralStringResource(R.plurals.schedule_library_group_count, dayGroups.size, dayGroups.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(dayGroups, key = { it.key }) { group ->
                            Box(appItemMotion()) {
                                CourseLibraryRow(group, onEdit, onDelete, onRestore, onMove, onSetReminder)
                            }
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
    group: CourseLibraryGroup,
    onEdit: (CourseItem) -> Unit,
    onDelete: (CourseLibraryEntry) -> Unit,
    onRestore: (CourseLibraryEntry) -> Unit,
    onMove: (CourseItem) -> Unit,
    onSetReminder: (CourseItem) -> Unit,
) {
    val entry = group.main
    val course = entry.course
    // Several courses share this card, so their actions move onto the variant rows.
    val shared = group.entries.size > 1
    var expanded by rememberSaveable(group.key) { mutableStateOf(false) }
    val press = remember { MutableInteractionSource() }
    Surface(
        interactionSource = press,
        onClick = { if (shared) expanded = !expanded else onEdit(course) },
        enabled = entry.editable,
        modifier = Modifier.fillMaxWidth().pressFeedback(press).testTag("course-library-card"),
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
                    if (group.entries.any { it.overridesPlugin }) {
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
                    if (group.hasVariants) {
                        CourseLibraryTag(
                            pluralStringResource(R.plurals.schedule_library_variant_count, group.variants.size, group.variants.size),
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (!shared) CourseEntryMenu(entry, onEdit, onDelete, onRestore)
            }
            FlowRow(
                modifier = Modifier.padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CourseLibraryFact(Icons.Rounded.Schedule, courseTimeText(course.time))
                course.location.takeIf { it.isNotBlank() }?.let { CourseLibraryFact(Icons.Rounded.Place, it) }
                course.teacher.takeIf { it.isNotBlank() }?.let { CourseLibraryFact(Icons.Rounded.Person, it) }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!shared) {
                    AppOutlinedButton(onClick = { onMove(course) }) {
                        AppAtomicText(stringResource(R.string.schedule_course_action_move))
                    }
                    AppOutlinedButton(onClick = { onSetReminder(course) }) {
                        AppAtomicText(stringResource(R.string.schedule_action_reminder))
                    }
                }
                if (group.hasVariants) {
                    AppOutlinedButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.testTag("course-library-variants-toggle"),
                    ) {
                        Icon(
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        AppAtomicText(
                            stringResource(
                                if (expanded) R.string.schedule_library_variants_collapse else R.string.schedule_library_variants_expand,
                            ),
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = expanded && group.hasVariants,
                enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(200)),
            ) {
                Column(
                    modifier = Modifier.padding(end = 8.dp).testTag("course-library-variants"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.schedule_library_variants_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    group.variants.forEach { variant ->
                        val siblings = group.variants.count { it.entry === variant.entry }
                        CourseVariantRow(
                            variant = variant,
                            otherWeeks = siblings > 1,
                            showActions = shared,
                            onEdit = onEdit,
                            onDelete = onDelete,
                            onRestore = onRestore,
                            onMove = onMove,
                            onSetReminder = onSetReminder,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseEntryMenu(
    entry: CourseLibraryEntry,
    onEdit: (CourseItem) -> Unit,
    onDelete: (CourseLibraryEntry) -> Unit,
    onRestore: (CourseLibraryEntry) -> Unit,
    onMove: ((CourseItem) -> Unit)? = null,
    onSetReminder: ((CourseItem) -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        AppCircleIconButton(onClick = { menu = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.memo_more))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.schedule_action_edit)) },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = { menu = false; onEdit(entry.course) },
            )
            onMove?.let { move ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.schedule_course_action_move)) },
                    leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
                    onClick = { menu = false; move(entry.course) },
                )
            }
            onSetReminder?.let { remind ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.schedule_action_reminder)) },
                    leadingIcon = { Icon(Icons.Rounded.Notifications, contentDescription = null) },
                    onClick = { menu = false; remind(entry.course) },
                )
            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseVariantRow(
    variant: CourseVariant,
    otherWeeks: Boolean,
    showActions: Boolean,
    onEdit: (CourseItem) -> Unit,
    onDelete: (CourseLibraryEntry) -> Unit,
    onRestore: (CourseLibraryEntry) -> Unit,
    onMove: (CourseItem) -> Unit,
    onSetReminder: (CourseItem) -> Unit,
) {
    val context = LocalContext.current
    val weeksText = when {
        variant.weeks.isNotEmpty() -> context.weekRangesText(compactWeekRanges(variant.weeks))
        otherWeeks -> stringResource(R.string.schedule_library_variant_other_weeks)
        else -> stringResource(R.string.schedule_library_variant_all_weeks)
    }
    val unset = stringResource(R.string.schedule_library_variant_unset)
    val press = remember { MutableInteractionSource() }
    Surface(
        interactionSource = press,
        onClick = { onEdit(variant.entry.course) },
        modifier = Modifier.fillMaxWidth().pressFeedback(press).testTag("course-library-variant"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(weeksText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    // The other rows are compared against this one, so name it instead of leaving it blank.
                    if (variant.changed.isEmpty()) {
                        CourseLibraryTag(
                            stringResource(R.string.schedule_library_variant_main),
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (VariantField.Title in variant.changed) {
                        VariantFact(Icons.Rounded.Edit, variant.title, changed = true)
                    }
                    VariantFact(Icons.Rounded.Schedule, courseTimeText(variant.time), VariantField.Time in variant.changed)
                    val locationChanged = VariantField.Location in variant.changed
                    if (variant.location.isNotBlank() || locationChanged) {
                        VariantFact(Icons.Rounded.Place, variant.location.ifBlank { unset }, locationChanged)
                    }
                    val teacherChanged = VariantField.Teacher in variant.changed
                    if (variant.teacher.isNotBlank() || teacherChanged) {
                        VariantFact(Icons.Rounded.Person, variant.teacher.ifBlank { unset }, teacherChanged)
                    }
                }
            }
            if (showActions) {
                CourseEntryMenu(variant.entry, onEdit, onDelete, onRestore, onMove, onSetReminder)
            }
        }
    }
}

/** Facts that differ from the main arrangement get the accent container so they stand out. */
@Composable
private fun VariantFact(icon: ImageVector, text: String, changed: Boolean) {
    val content = if (changed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (changed) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(horizontal = if (changed) 8.dp else 0.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (changed) FontWeight.SemiBold else FontWeight.Normal,
            color = content,
        )
    }
}

@Composable
private fun courseTimeText(time: CourseTimeSlot): String =
    stringResource(weekdayNameRes(time.dayOfWeek)) + " · " +
        stringResource(R.string.schedule_node_range, time.startNode, time.endNode)

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

/** Empty states sit in the middle of the free space instead of hugging the top. */
@Composable
private fun CenteredLibraryHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CourseLibraryHint(text) }
}

@Composable
private fun CourseLibraryHint(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
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
