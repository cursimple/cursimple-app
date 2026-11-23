package com.x500x.cursimple.feature.schedule

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.core.kernel.model.weekdayNameRes
import com.x500x.cursimple.feature.plugin.ui.AppAssistChip
import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import com.x500x.cursimple.feature.plugin.ui.AppFilterChip
import com.x500x.cursimple.feature.plugin.ui.rememberAppHaptics
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.plugin.ui.AppSearchField
import com.x500x.cursimple.feature.plugin.ui.appItemMotion
import com.x500x.cursimple.feature.plugin.ui.pressFeedback
import com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import com.x500x.cursimple.feature.schedule.time.today
import java.time.LocalDate
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.x500x.cursimple.feature.plugin.ui.AppAlertDialog
import com.x500x.cursimple.feature.plugin.ui.AppDialogPrimaryButton
import com.x500x.cursimple.feature.plugin.ui.AppDialogSecondaryButton
import com.x500x.cursimple.feature.plugin.ui.AppDialogDangerButton
import com.x500x.cursimple.feature.plugin.ui.AppDialogTone
import androidx.compose.material.icons.rounded.OpenWith

private const val COMPACT_HEADER_MAX_HEIGHT_DP = 500

internal fun libraryTabMovesForward(initial: LibraryTab, target: LibraryTab): Boolean =
    target == LibraryTab.Events && initial == LibraryTab.Courses

/** Searchable term-wide course list with reversible manual overrides for plugin courses. */
@Composable
internal fun CourseLibraryScreen(
    entries: List<CourseLibraryEntry>,
    columnDayOfWeeks: List<Int>,
    onAddCourses: (List<CourseItem>) -> Unit,
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
    /** Courses deleted by hand and kept for restore. */
    deletedCourses: List<com.x500x.cursimple.core.data.DeletedCourse> = emptyList(),
    onRestoreDeleted: (String) -> Unit = {},
    onClearRecycleBin: () -> Unit = {},
    onSetReminder: (CourseItem) -> Unit = {},
    events: List<ScheduleEvent> = emptyList(),
    onSaveEvent: (ScheduleEvent) -> Unit = {},
    onRemoveEvent: (String) -> Unit = {},
    onRemoveCourses: (Set<String>) -> Unit,
    onRemoveEvents: (Set<String>) -> Unit,
    onReorderEvents: (List<String>) -> Unit,
    eventCustomOrder: Boolean,
    eventOrderSaving: Boolean,
    libraryTermId: String,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val haptics = rememberAppHaptics()
    var tab by rememberSaveable { mutableStateOf(LibraryTab.Courses) }
    var selection by remember(libraryTermId) { mutableStateOf(emptySet<String>()) }
    var batchDelete by remember(libraryTermId) { mutableStateOf<Set<String>?>(null) }
    var selectedEventSourceNames by rememberSaveable(libraryTermId) {
        mutableStateOf(EventSourceKind.entries.map { it.name })
    }
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
    val orderedEvents = remember(events, today, eventCustomOrder) {
        if (eventCustomOrder) events else chronologicalLibraryEvents(events, today)
    }
    val showEventSourceFilters = remember(events) {
        events.any { it.eventSourceKind() == EventSourceKind.Plugin }
    }
    val selectedEventSources = remember(selectedEventSourceNames) {
        selectedEventSourceNames.mapNotNull { name -> EventSourceKind.entries.firstOrNull { it.name == name } }.toSet()
    }
    val sourceMatchedEvents = remember(orderedEvents, selectedEventSources, showEventSourceFilters) {
        if (showEventSourceFilters) filterLibraryEvents(orderedEvents, selectedEventSources) else orderedEvents
    }
    val matchedEvents = remember(sourceMatchedEvents, query) {
        sourceMatchedEvents.filter { matchesEventQuery(it, query) }
    }
    var sortMode by rememberSaveable { mutableStateOf(CourseSortMode.ByWeekday) }
    var editing by remember { mutableStateOf<CourseItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CourseLibraryEntry?>(null) }
    var pendingRestore by remember { mutableStateOf<CourseLibraryEntry?>(null) }
    var moving by remember { mutableStateOf<CourseItem?>(null) }
    var pendingMove by remember { mutableStateOf<Pair<CourseItem, CourseTimeSlot>?>(null) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var pendingPurge by remember { mutableStateOf<CourseItem?>(null) }
    var showBin by remember { mutableStateOf(false) }
    var pendingBinRestore by remember { mutableStateOf<Pair<String, CourseItem>?>(null) }
    var pendingClearBin by remember { mutableStateOf(false) }
    var binDetail by remember { mutableStateOf<Pair<String, CourseItem>?>(null) }

    val allCourses = remember(entries) { entries.map { it.course } }
    val matched = remember(entries, query, sortMode) {
        sortCourseLibrary(entries.filter { matchesCourseQuery(it.course, query) }, sortMode)
    }
    val matchingIds = remember(tab, matched, matchedEvents) {
        if (tab == LibraryTab.Courses) matched.mapTo(hashSetOf()) { it.course.id }
        else matchedEvents.mapTo(hashSetOf()) { it.id }
    }
    val selectedIds = selection.intersect(matchingIds)
    val selecting = selectedIds.isNotEmpty()
    LaunchedEffect(matchingIds) { selection = selection.intersect(matchingIds) }

    // Register on entry so this handler takes precedence over the host page's Back action.
    if (selecting) {
        BackHandler { selection = emptySet() }
    }
    val toggleSelection = { ids: Set<String> ->
        selection = if (selectedIds.containsAll(ids)) selectedIds - ids else selectedIds + ids
    }
    batchDelete?.let { ids ->
        val courses = tab == LibraryTab.Courses
        AppAlertDialog(
            onDismissRequest = { batchDelete = null },
            tone = AppDialogTone.Danger,
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(22.dp)) },
            title = { Text(stringResource(R.string.library_management_delete_selected)) },
            text = {
                Text(
                    pluralStringResource(
                        if (courses) R.plurals.library_management_delete_courses else R.plurals.library_management_delete_events,
                        ids.size,
                        ids.size,
                    ),
                )
            },
            confirmButton = {
                AppDialogDangerButton(
                    modifier = Modifier.testTag("library-confirm-delete"),
                    onClick = {
                        if (courses) onRemoveCourses(ids) else onRemoveEvents(ids)
                        batchDelete = null
                        selection = emptySet()
                    },
                ) { Text(stringResource(R.string.schedule_action_delete)) }
            },
            dismissButton = {
                AppDialogSecondaryButton(
                    onClick = { batchDelete = null },
                    modifier = Modifier.testTag("library-cancel-delete"),
                ) {
                    Text(stringResource(R.string.schedule_action_cancel))
                }
            },
        )
    }

    if (adding || editing != null) {
        val initial = editing
        AddCourseDialog(
            onDismiss = { adding = false; editing = null },
            onConfirm = onUpdateCourse,
            onConfirmBatch = { courses ->
                if (initial == null) onAddCourses(courses) else onUpdateCourse(courses.single())
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
        AppAlertDialog(
            onDismissRequest = { pendingDelete = null },
            tone = AppDialogTone.Danger,
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(22.dp)) },
            title = { Text(stringResource(R.string.schedule_library_delete_title)) },
            text = { Text(stringResource(R.string.schedule_library_delete_body, target.course.title)) },
            confirmButton = {
                AppDialogDangerButton(onClick = {
                    onRemoveCourse(target.course.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.schedule_library_delete_confirm)) }
            },
            dismissButton = {
                AppDialogSecondaryButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.schedule_library_delete_cancel))
                }
            },
        )
    }

    // Removing a manual override reveals the plugin original; label this as restoration.
    pendingRestore?.let { target ->
        AppAlertDialog(
            onDismissRequest = { pendingRestore = null },
            icon = { Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(22.dp)) },
            title = { Text(stringResource(R.string.schedule_library_restore_title)) },
            text = { Text(stringResource(R.string.schedule_library_restore_body, target.course.title)) },
            confirmButton = {
                AppDialogPrimaryButton(onClick = {
                    onRemoveCourse(target.course.id)
                    pendingRestore = null
                }) { Text(stringResource(R.string.schedule_library_restore_confirm)) }
            },
            dismissButton = {
                AppDialogSecondaryButton(onClick = { pendingRestore = null }) {
                    Text(stringResource(R.string.schedule_library_delete_cancel))
                }
            },
        )
    }

    if (showBin) {
        val binCount = deletedCourses.size + hiddenCourses.size
        AppAlertDialog(
            onDismissRequest = { showBin = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(22.dp)) },
            title = { Text(stringResource(R.string.schedule_library_bin_title, binCount)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (binCount == 0) {
                        Text(stringResource(R.string.schedule_library_bin_empty), style = MaterialTheme.typography.bodyMedium)
                    }
                    deletedCourses.forEach { deleted ->
                        BinRow(
                            course = deleted.course,
                            onOpen = { binDetail = "deleted" to deleted.course },
                            onRestore = { pendingBinRestore = "deleted" to deleted.course },
                        )
                    }
                    hiddenCourses.forEach { course ->
                        BinRow(
                            course = course,
                            onOpen = { binDetail = "hidden" to course },
                            onRestore = { pendingBinRestore = "hidden" to course },
                        )
                    }
                }
            },
            confirmButton = {
                AppDialogPrimaryButton(onClick = { showBin = false }) { Text(stringResource(R.string.schedule_action_close)) }
            },
            dismissButton = if (binCount > 0) {
                {
                    AppDialogSecondaryButton(onClick = { pendingClearBin = true }) {
                        Text(stringResource(R.string.schedule_library_bin_clear))
                    }
                }
            } else null,
        )
    }

    binDetail?.let { (_, course) ->
        AppAlertDialog(
            onDismissRequest = { binDetail = null },
            icon = { Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(22.dp)) },
            title = { Text(course.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.schedule_library_bin_detail_weekday, binWeekdayText(course)))
                    Text(stringResource(R.string.schedule_library_bin_detail_period_line, binPeriodText(course)))
                    binClockText(course)?.let { clock ->
                        Text(stringResource(R.string.schedule_library_bin_detail_clock, clock))
                    }
                    Text(stringResource(R.string.schedule_library_bin_detail_weeks, binWeeksText(course)))
                    if (course.location.isNotBlank()) {
                        Text(stringResource(R.string.schedule_library_bin_detail_location, course.location))
                    }
                    if (course.teacher.isNotBlank()) {
                        Text(stringResource(R.string.schedule_library_bin_detail_teacher, course.teacher))
                    }
                }
            },
            confirmButton = {
                AppDialogPrimaryButton(onClick = { binDetail = null }) { Text(stringResource(R.string.schedule_action_close)) }
            },
        )
    }

    pendingBinRestore?.let { (kind, course) ->
        AppAlertDialog(
            onDismissRequest = { pendingBinRestore = null },
            icon = { Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(22.dp)) },
            title = { Text(stringResource(R.string.schedule_library_bin_restore_title)) },
            text = { Text(stringResource(R.string.schedule_library_bin_restore_body, course.title)) },
            confirmButton = {
                AppDialogPrimaryButton(onClick = {
                    pendingBinRestore = null
                    if (kind == "deleted") onRestoreDeleted(course.id) else onRestoreCourse(course.id)
                }) { Text(stringResource(R.string.schedule_library_bin_restore)) }
            },
            dismissButton = {
                AppDialogSecondaryButton(onClick = { pendingBinRestore = null }) { Text(stringResource(R.string.schedule_action_cancel)) }
            },
        )
    }

    if (pendingClearBin) {
        AppAlertDialog(
            onDismissRequest = { pendingClearBin = false },
            icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(22.dp)) },
            title = { Text(stringResource(R.string.schedule_library_bin_clear_title)) },
            text = { Text(stringResource(R.string.schedule_library_bin_clear_body)) },
            confirmButton = {
                AppDialogPrimaryButton(onClick = {
                    pendingClearBin = false
                    onClearRecycleBin()
                    hiddenCourses.forEach { onPurgeCourse(it.id) }
                }) { Text(stringResource(R.string.schedule_library_bin_clear_confirm)) }
            },
            dismissButton = {
                AppDialogSecondaryButton(onClick = { pendingClearBin = false }) { Text(stringResource(R.string.schedule_action_cancel)) }
            },
        )
    }

    pendingPurge?.let { target ->
        AppAlertDialog(
            onDismissRequest = { pendingPurge = null },
            tone = AppDialogTone.Danger,
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(22.dp)) },
            title = { Text(stringResource(R.string.schedule_library_purge_title)) },
            text = { Text(stringResource(R.string.schedule_library_purge_body, target.title)) },
            confirmButton = {
                AppDialogDangerButton(
                    onClick = {
                        onPurgeCourse(target.id)
                        pendingPurge = null
                    },
                    ) { Text(stringResource(R.string.schedule_library_purge)) }
            },
            dismissButton = {
                AppDialogSecondaryButton(onClick = { pendingPurge = null }) {
                    Text(stringResource(R.string.schedule_library_delete_cancel))
                }
            },
        )
    }

    moving?.let { course ->
        MoveCourseDialog(
            course = course,
            maxNodeCount = maxNodeCount,
            slotTimes = slotTimes,
            existingCourses = allCourses,
            onDismiss = { moving = null },
            onConfirm = { time ->
                moving = null
                pendingMove = course to time
            },
        )
    }

    pendingMove?.let { (course, time) ->
        AppAlertDialog(
            onDismissRequest = { pendingMove = null },
            icon = { Icon(Icons.Rounded.OpenWith, contentDescription = null, modifier = Modifier.size(22.dp)) },
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
                AppDialogPrimaryButton(onClick = {
                    onUpdateCourse(course.copy(time = time))
                    pendingMove = null
                }) { Text(stringResource(R.string.schedule_move_dialog_confirm)) }
            },
            dismissButton = {
                AppDialogSecondaryButton(onClick = { pendingMove = null }) {
                    Text(stringResource(R.string.schedule_action_cancel))
                }
            },
        )
    }

    // Compact landscape puts search above the tabs to preserve list height.
    val compactHeader = LocalConfiguration.current.screenHeightDp < COMPACT_HEADER_MAX_HEIGHT_DP
    val searchField = @Composable { fieldModifier: Modifier ->
        AppSearchField(
            query = query,
            onQueryChange = { query = it },
            hint = stringResource(R.string.schedule_library_search),
            clearLabel = stringResource(R.string.memo_search_clear),
            modifier = fieldModifier,
            onClear = { query = "" },
            cornerRadius = 18.dp,
            minHeight = 52.dp,
            borderWidth = 1.5.dp,
        )
    }
    val tabSwitch = @Composable { switchModifier: Modifier ->
        Row(
            modifier = switchModifier,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LibraryTab.entries.forEach { entry ->
                // Same pill as the sort filters below, so the two chip rows read as one system.
                AppFilterChip(
                    selected = tab == entry,
                    onClick = {
                        haptics.click()
                        if (tab != entry) selection = emptySet()
                        tab = entry
                    },
                    label = {
                        Text(
                            stringResource(
                                if (entry == LibraryTab.Courses) R.string.schedule_library_tab_courses
                                else R.string.schedule_library_tab_events,
                                if (entry == LibraryTab.Courses) entries.size else events.size,
                            ),
                            maxLines = 1,
                        )
                    },
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            val binCount = deletedCourses.size + hiddenCourses.size
            val binButton = @Composable {
                if (binCount > 0) {
                    AppCircleIconButton(onClick = { showBin = true }) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.schedule_library_bin_open, binCount),
                        )
                    }
                }
            }
            if (compactHeader) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        searchField(Modifier.weight(1f))
                        binButton()
                    }
                    tabSwitch(Modifier.fillMaxWidth())
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tabSwitch(Modifier.weight(1f))
                        binButton()
                    }
                    searchField(Modifier)
                }
            }

            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    AppMotion.sharedAxisX(libraryTabMovesForward(initialState, targetState))
                },
                label = "library_tab_transition",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { currentTab ->
                Column(Modifier.fillMaxSize()) {
                    if (currentTab == LibraryTab.Events && showEventSourceFilters) {
                        EventSourceFilters(
                            selectedSources = selectedEventSources,
                            onSelectionChange = { sources ->
                                haptics.click()
                                selectedEventSourceNames = sources.map { it.name }
                                selection = emptySet()
                            },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        )
                    }

                    if (selecting && currentTab == tab) {
                        LibrarySelectionBar(
                            count = selectedIds.size,
                            allSelected = selectedIds.size == matchingIds.size,
                            onClear = { selection = emptySet() },
                            onSelectAll = { selection = matchingIds },
                            onDelete = { batchDelete = selectedIds },
                        )
                    }

                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (currentTab == LibraryTab.Events) {
                            when {
                                events.isEmpty() -> CenteredLibraryHint(stringResource(R.string.schedule_library_events_empty))
                                matchedEvents.isEmpty() && sourceMatchedEvents.isEmpty() ->
                                    CenteredLibraryHint(stringResource(R.string.schedule_library_event_source_no_match))
                                matchedEvents.isEmpty() -> CenteredLibraryHint(stringResource(R.string.schedule_library_event_no_match))
                                else -> EventLibraryList(
                                    events = matchedEvents,
                                    today = today,
                                    selectedIds = if (tab == currentTab) selectedIds else emptySet(),
                                    reorderEnabled = tab == currentTab && !eventOrderSaving,
                                    onSelect = { id -> if (tab == currentTab) selection = selectedIds + id },
                                    onClearDragSelection = { id -> if (selection == setOf(id)) selection = emptySet() },
                                    onToggleSelection = { id -> if (tab == currentTab) toggleSelection(setOf(id)) },
                                    onMove = { source, target ->
                                        val order = moveLibraryEvent(orderedEvents, matchedEvents, source, target)
                                        if (order.isNotEmpty()) onReorderEvents(order)
                                    },
                                    onEdit = { eventEditing = it },
                                    onDelete = onRemoveEvent,
                                    contentPadding = LibraryListPadding,
                                )
                            }
                        } else {
                            CourseLibraryList(
                                entries = entries,
                                matched = matched,
                                selectedIds = if (tab == currentTab) selectedIds else emptySet(),
                                onSelect = { ids -> if (tab == currentTab) selection = selectedIds + ids },
                                onToggleSelection = { ids -> if (tab == currentTab) toggleSelection(ids) },
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
            }
        }
        // The add action follows the visible tab, as on the notes page.
        if (!selecting) ExtendedFloatingActionButton(
            onClick = { haptics.click(); if (tab == LibraryTab.Courses) adding = true else addingEvent = true },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = {
                Text(
                    stringResource(
                        if (tab == LibraryTab.Courses) R.string.schedule_library_add else R.string.schedule_library_add_event,
                    ),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            },
            shape = RoundedCornerShape(21.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 2.dp,
                pressedElevation = 2.dp,
                focusedElevation = 2.dp,
                hoveredElevation = 2.dp,
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventSourceFilters(
    selectedSources: Set<EventSourceKind>,
    onSelectionChange: (Set<EventSourceKind>) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        EventSourceKind.entries.forEach { source ->
            val labelRes = when (source) {
                EventSourceKind.Plugin -> R.string.library_management_event_source_plugin
                EventSourceKind.UserCreated -> R.string.library_management_event_source_user
            }
            val selected = source in selectedSources
            AppFilterChip(
                selected = selected,
                onClick = {
                    onSelectionChange(if (selected) selectedSources - source else selectedSources + source)
                },
                label = { Text(stringResource(labelRes), maxLines = 1) },
                modifier = Modifier.testTag("event-source-${source.name}"),
            )
        }
        val clearDescription = stringResource(R.string.library_management_event_source_clear_content_description)
        AppAssistChip(
            onClick = { onSelectionChange(EventSourceKind.entries.toSet()) },
            label = { Text(stringResource(R.string.library_management_event_source_all), maxLines = 1) },
            modifier = Modifier.testTag("event-source-all").height(36.dp),
            enabled = selectedSources.size < EventSourceKind.entries.size,
        )
        AppAssistChip(
            onClick = { onSelectionChange(emptySet()) },
            label = { Text(stringResource(R.string.library_management_event_source_clear), maxLines = 1) },
            leadingIcon = {
                Icon(
                    Icons.Rounded.ClearAll,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            modifier = Modifier
                .testTag("event-source-clear")
                .semantics {
                    contentDescription = clearDescription
                }
                .height(36.dp),
            enabled = selectedSources.isNotEmpty(),
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                disabledLeadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            ),
            border = AssistChipDefaults.assistChipBorder(
                enabled = selectedSources.isNotEmpty(),
                borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.48f),
                disabledBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f),
            ),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibrarySelectionBar(
    count: Int,
    allSelected: Boolean,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
) {
    val error = MaterialTheme.colorScheme.error
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        AppCircleIconButton(onClick = onClear) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.library_management_clear))
        }
        Text(
            stringResource(R.string.library_management_count, count),
            style = MaterialTheme.typography.labelLarge,
        )
        AppOutlinedButton(onClick = onSelectAll, enabled = !allSelected, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.library_management_select_all))
        }
        AppOutlinedButton(
            onClick = onDelete,
            modifier = Modifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = error),
            border = BorderStroke(1.dp, error),
        ) {
            Text(stringResource(R.string.library_management_delete_selected))
        }
    }
}

@Composable
internal fun LibrarySelectionMark(isSelected: Boolean) {
    Icon(
        if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
        contentDescription = null,
        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(24.dp),
    )
}

/** Leaves room under the last card for the floating add button. */
private val LibraryListPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseLibraryList(
    entries: List<CourseLibraryEntry>,
    matched: List<CourseLibraryEntry>,
    selectedIds: Set<String>,
    onSelect: (Set<String>) -> Unit,
    onToggleSelection: (Set<String>) -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
            }
        }
        if (selectedIds.isEmpty() && showHidden && hiddenCourses.isNotEmpty()) {
            items(hiddenCourses, key = { "hidden-${it.id}" }) { course ->
                Box(appItemMotion()) {
                    HiddenCourseRow(
                        course = course,
                        onRestore = { onRestoreHidden(course.id) },
                        onPurge = onPurgeHidden,
                    )
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
                            CourseLibraryRow(group, selectedIds, onSelect, onToggleSelection, onEdit, onDelete, onRestore, onMove, onSetReminder)
                        }
                    }
                } else {
                    grouped.forEach { (dayOfWeek, dayGroups) ->
                        item(key = "header-$dayOfWeek") {
                            Row(
                                // Headers glide and fade with their cards, so every sort mode moves the list the same way.
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp).then(appItemMotion()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(weekdayNameRes(dayOfWeek)),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 12.5.sp,
                                        letterSpacing = 0.5.sp,
                                    ),
                                    fontWeight = FontWeight.Bold,
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
                                CourseLibraryRow(group, selectedIds, onSelect, onToggleSelection, onEdit, onDelete, onRestore, onMove, onSetReminder)
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
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Rounded.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
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
    selectedIds: Set<String>,
    onSelect: (Set<String>) -> Unit,
    onToggleSelection: (Set<String>) -> Unit,
    onEdit: (CourseItem) -> Unit,
    onDelete: (CourseLibraryEntry) -> Unit,
    onRestore: (CourseLibraryEntry) -> Unit,
    onMove: (CourseItem) -> Unit,
    onSetReminder: (CourseItem) -> Unit,
) {
    val entry = group.main
    val course = entry.course
    val shared = group.entries.size > 1
    var expanded by rememberSaveable(group.key) { mutableStateOf(false) }
    val press = remember { MutableInteractionSource() }
    val haptics = rememberAppHaptics()
    val ids = remember(group) { group.entries.mapTo(hashSetOf()) { it.course.id } }
    val selecting = selectedIds.isNotEmpty()
    val isSelected = selectedIds.containsAll(ids)
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().pressFeedback(press),
        shape = shape,
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .testTag("course-library-card")
                .then(if (selecting) Modifier.semantics { selected = isSelected } else Modifier)
                .combinedClickable(
                    interactionSource = press,
                    indication = ripple(),
                    enabled = entry.editable,
                    role = Role.Button,
                    onLongClickLabel = stringResource(R.string.library_management_select),
                    hapticFeedbackEnabled = false,
                    onLongClick = { haptics.heavy(); onSelect(ids) },
                    onClick = {
                        when {
                            selecting -> onToggleSelection(ids)
                            shared -> expanded = !expanded
                            else -> onEdit(course)
                        }
                    },
                )
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                CourseLibrarySwatch(course)
                FlowRow(
                    modifier = Modifier.weight(1f).padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = course.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
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
                if (selecting) {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        LibrarySelectionMark(isSelected)
                    }
                } else {
                    CourseEntryMenu(
                        entry, onEdit, onDelete, onRestore,
                        onSelect = { onSelect(ids) },
                        onMove = onMove.takeUnless { shared },
                        onSetReminder = onSetReminder.takeUnless { shared },
                    )
                }
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
            if (group.hasVariants) {
                AppOutlinedButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("course-library-variants-toggle"),
                ) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(
                        if (expanded) R.string.schedule_library_variants_collapse else R.string.schedule_library_variants_expand,
                    ))
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
                        val variantIds = setOf(variant.entry.course.id)
                        CourseVariantRow(
                            variant = variant,
                            otherWeeks = siblings > 1,
                            showActions = shared,
                            selecting = selecting,
                            isSelected = variant.entry.course.id in selectedIds,
                            onSelect = { onSelect(variantIds) },
                            onToggleSelection = { onToggleSelection(variantIds) },
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

/** The course colour as a rounded swatch, so rows can be scanned by colour like the grid. */
@Composable
private fun CourseLibrarySwatch(course: CourseItem) {
    val accents = LocalScheduleAccents.current
    val palette = remember(course.title, course.colorArgb, course.colorEndArgb, accents) {
        courseColor(course.title, accents.coursePalette, course.colorArgb, course.colorEndArgb)
    }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .padding(top = 4.dp, end = 12.dp)
            .size(16.dp)
            .clip(shape)
            .background(palette.container)
            .border(2.dp, palette.onContainer.copy(alpha = 0.85f), shape),
    )
}

@Composable
private fun CourseEntryMenu(
    entry: CourseLibraryEntry,
    onEdit: (CourseItem) -> Unit,
    onDelete: (CourseLibraryEntry) -> Unit,
    onRestore: (CourseLibraryEntry) -> Unit,
    onSelect: () -> Unit,
    onMove: ((CourseItem) -> Unit)? = null,
    onSetReminder: ((CourseItem) -> Unit)? = null,
) {
    LibraryCardMenu(onEdit = { onEdit(entry.course) }, onSelect = onSelect, onDelete = { onDelete(entry) }) { dismiss ->
        onMove?.let { move ->
            LibraryMenuAction(stringResource(R.string.schedule_course_action_move), Icons.Rounded.Schedule) {
                dismiss()
                move(entry.course)
            }
        }
        onSetReminder?.let { remind ->
            LibraryMenuAction(stringResource(R.string.schedule_action_reminder), Icons.Rounded.Notifications) {
                dismiss()
                remind(entry.course)
            }
        }
        if (entry.restorable) {
            LibraryMenuAction(stringResource(R.string.schedule_action_restore_plugin), Icons.Rounded.Restore) {
                dismiss()
                onRestore(entry)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseVariantRow(
    variant: CourseVariant,
    otherWeeks: Boolean,
    showActions: Boolean,
    selecting: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleSelection: () -> Unit,
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
    val shape = RoundedCornerShape(16.dp)
    val haptics = rememberAppHaptics()
    Surface(
        modifier = Modifier.fillMaxWidth().pressFeedback(press),
        shape = shape,
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .testTag("course-library-variant")
                .then(if (selecting) Modifier.semantics { selected = isSelected } else Modifier)
                .combinedClickable(
                    interactionSource = press,
                    indication = ripple(),
                    role = Role.Button,
                    onLongClickLabel = stringResource(R.string.library_management_select),
                    hapticFeedbackEnabled = false,
                    onLongClick = { haptics.heavy(); onSelect() },
                    onClick = { if (selecting) onToggleSelection() else onEdit(variant.entry.course) },
                )
                .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
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
            if (selecting) {
                Box(Modifier.padding(horizontal = 12.dp)) { LibrarySelectionMark(isSelected) }
            } else if (showActions) {
                CourseEntryMenu(variant.entry, onEdit, onDelete, onRestore, onSelect, onMove, onSetReminder)
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
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(13.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
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
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
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
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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

/** One deleted course in the recycle bin: readable summary, tap for details, restore on the side. */
@Composable
private fun BinRow(course: CourseItem, onOpen: () -> Unit, onRestore: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(course.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            // One label per fact instead of a dotted line, so each value stays readable on its own.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BinTag(binWeekdayText(course))
                BinTag(binPeriodText(course))
                binClockText(course)?.let { BinTag(it) }
                if (course.location.isNotBlank()) BinTag(stringResource(R.string.schedule_library_bin_tag_location, course.location))
                if (course.teacher.isNotBlank()) BinTag(stringResource(R.string.schedule_library_bin_tag_teacher, course.teacher))
            }
        }
        AppOutlinedButton(onClick = onRestore, modifier = Modifier.heightIn(min = 44.dp)) {
            Text(stringResource(R.string.schedule_library_bin_restore))
        }
    }
}

/** A small grey label for one fact in a bin row. */
@Composable
private fun BinTag(text: String) {
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun binWeekdayText(course: CourseItem): String =
    stringResource(scheduleWeekdayFullRes(course.time.dayOfWeek))

@Composable
private fun binPeriodText(course: CourseItem): String =
    stringResource(R.string.schedule_library_bin_detail_period, course.time.startNode, course.time.endNode)

/** Typed clock times when the course has them; the period row already covers the timetable slot. */
@Composable
private fun binClockText(course: CourseItem): String? {
    val start = course.reminderStartTime?.takeIf { it.isNotBlank() } ?: return null
    val end = course.reminderEndTime?.takeIf { it.isNotBlank() }
    return if (end != null) "$start-$end" else start
}

@Composable
private fun binWeeksText(course: CourseItem): String {
    val weeks = course.weeks.filter { it > 0 }.sorted()
    return if (weeks.isEmpty()) {
        stringResource(R.string.schedule_library_bin_detail_weeks_unknown)
    } else {
        LocalContext.current.weeksDetailText(describeWeeksDetail(weeks))
    }
}
