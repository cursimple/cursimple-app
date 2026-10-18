package com.x500x.cursimple.feature.schedule

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import com.x500x.cursimple.feature.plugin.ui.AppSearchField
import com.x500x.cursimple.feature.plugin.ui.rememberAppHaptics
import com.x500x.cursimple.feature.plugin.ui.AppConfirmationDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.MemoDueState
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.model.MemoPriority
import com.x500x.cursimple.core.kernel.model.allCoursesWith
import com.x500x.cursimple.core.kernel.model.dueState
import com.x500x.cursimple.core.kernel.model.memoComparator
import com.x500x.cursimple.core.kernel.model.memoCourseKey
import com.x500x.cursimple.core.kernel.model.toggleChecklistLine
import com.x500x.cursimple.core.kernel.model.parseChecklistLine
import com.x500x.cursimple.core.kernel.model.visibleScheduleCourses
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.plugin.ui.appItemMotion
import com.x500x.cursimple.feature.plugin.ui.pressFeedback
import com.x500x.cursimple.feature.schedule.theme.CoursePaletteEntry
import com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.interaction.MutableInteractionSource

data class MemoNotebook(
    val key: String?,
    val title: String,
    val subtitle: String,
    val orphan: Boolean,
)

private sealed interface MemoSelection {
    data object All : MemoSelection
    data class Notebook(val key: String?) : MemoSelection
}

/** Overview counters filter their corresponding note category. */
private enum class MemoQuickFilter { Todo, Today, Overdue }

/**
 * One notebook per course name, including archived note associations and a general notebook.
 */
fun buildMemoNotebooks(courses: List<CourseItem>, memos: List<MemoNote>, otherTitle: String): List<MemoNotebook> {
    val fromCourses = courses
        .groupBy { memoCourseKey(it.title) }
        .map { (key, same) ->
            val first = same.first()
            MemoNotebook(
                key = key,
                title = first.title.trim(),
                subtitle = listOf(first.teacher, first.location).filter { it.isNotBlank() }.joinToString(" · "),
                orphan = false,
            )
        }
        .sortedWith(
            compareByDescending<MemoNotebook> { nb -> memos.count { it.courseKey == nb.key && !it.completed } }
                .thenByDescending { nb -> memos.count { it.courseKey == nb.key } }
                .thenBy { it.title },
        )
    val known = fromCourses.mapTo(mutableSetOf()) { it.key }
    val orphans = memos
        .filter { it.courseKey != null && it.courseKey !in known }
        .distinctBy { it.courseKey }
        .map { MemoNotebook(key = it.courseKey, title = it.courseTitle.ifBlank { it.courseKey!! }, subtitle = "", orphan = true) }
    return fromCourses + orphans + MemoNotebook(key = null, title = otherTitle, subtitle = "", orphan = false)
}

@Composable
internal fun memoNotebookColor(notebook: MemoNotebook?): CoursePaletteEntry? {
    if (notebook?.key == null) return null
    return courseColor(notebook.title, LocalScheduleAccents.current.coursePalette)
}

@Composable
internal fun memoPriorityColor(priority: MemoPriority): Color = when (priority) {
    MemoPriority.High -> MaterialTheme.colorScheme.error
    MemoPriority.Medium -> Color(0xFFE39A2D)
    MemoPriority.Low -> MaterialTheme.colorScheme.primary
    MemoPriority.None -> Color.Transparent
}

@Composable
internal fun memoPriorityLabel(priority: MemoPriority): String = stringResource(
    when (priority) {
        MemoPriority.High -> R.string.memo_priority_high
        MemoPriority.Medium -> R.string.memo_priority_medium
        MemoPriority.Low -> R.string.memo_priority_low
        MemoPriority.None -> R.string.memo_priority_none
    },
)

/** Timetable courses and independently persisted notes feed the drawer Notes page. */
@Composable
fun MemoRoute(
    viewModel: ScheduleViewModel,
    modifier: Modifier = Modifier,
    searchOpen: Boolean = false,
    onCloseSearch: () -> Unit = {},
    openNoteId: String? = null,
    onNoteOpened: () -> Unit = {},
    shareOpen: Boolean = false,
    onCloseShare: () -> Unit = {},
    importUri: Uri? = null,
    importCode: String? = null,
    onCloseImport: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val courses = remember(state.schedule, state.manualCourses) {
        state.schedule.allCoursesWith(state.manualCourses).visibleScheduleCourses()
    }
    MemoScreen(
        courses = courses,
        memos = state.memos,
        onSave = viewModel::saveMemo,
        onRemove = viewModel::removeMemo,
        modifier = modifier,
        searchOpen = searchOpen,
        onCloseSearch = onCloseSearch,
        openNoteId = openNoteId,
        onNoteOpened = onNoteOpened,
        shareOpen = shareOpen,
        onCloseShare = onCloseShare,
        importUri = importUri,
        importCode = importCode,
        onCloseImport = onCloseImport,
        onImportNotes = viewModel::importMemos,
    )
}

@Composable
internal fun MemoScreen(
    courses: List<CourseItem>,
    memos: List<MemoNote>,
    onSave: (MemoNote) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    searchOpen: Boolean = false,
    onCloseSearch: () -> Unit = {},
    openNoteId: String? = null,
    onNoteOpened: () -> Unit = {},
    shareOpen: Boolean = false,
    onCloseShare: () -> Unit = {},
    importUri: Uri? = null,
    importCode: String? = null,
    onCloseImport: () -> Unit = {},
    onImportNotes: suspend (List<MemoNote>) -> Int = { notes -> notes.forEach(onSave); notes.size },
) {
    val zone = LocalAppZone.current
    val now = remember(memos, zone) { BeijingTime.nowDateTimeIn(zone) }
    val otherTitle = stringResource(R.string.memo_notebook_other)
    val notebooks = remember(courses, memos, otherTitle) { buildMemoNotebooks(courses, memos, otherTitle) }
    var selection by remember { mutableStateOf<MemoSelection>(MemoSelection.All) }
    var quickFilter by rememberSaveable { mutableStateOf<MemoQuickFilter?>(null) }
    val haptics = rememberAppHaptics()
    var searchQuery by rememberSaveable(searchOpen) { mutableStateOf("") }
    val searching = searchOpen && searchQuery.isNotBlank()
    // Editable note or new draft initialized with its course association.
    var editing by remember { mutableStateOf<MemoNote?>(null) }
    var pendingDelete by remember { mutableStateOf<MemoNote?>(null) }
    var pendingCompletion by remember { mutableStateOf<MemoCompletionRequest?>(null) }
    var sharingNote by remember { mutableStateOf<MemoNote?>(null) }
    var pickedImport by remember { mutableStateOf<Uri?>(null) }
    var importOptions by rememberSaveable { mutableStateOf(false) }
    var pickedCode by rememberSaveable { mutableStateOf<String?>(null) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pickedImport = it }
    val activeImport = importUri ?: pickedImport
    val activeCode = importCode ?: pickedCode
    if (importOptions) MemoImportOptionsSheet(
        onCode = { pickedCode = it; importOptions = false },
        onFile = {
            importOptions = false
            importPicker.launch(arrayOf(com.x500x.cursimple.core.data.memo.MemoTransferCodec.MIME, "application/json", "application/octet-stream", "text/plain"))
        }, onDismiss = { importOptions = false },
    )
    if (activeImport != null || activeCode != null) MemoImportSheet(
        uri = activeImport,
        code = activeCode,
        courses = courses,
        existing = memos,
        onImport = { notes ->
            val added = onImportNotes(notes)
            if (added > 0) {
                val groups = notes.map { it.courseKey }.distinct()
                selection = if (groups.size == 1) MemoSelection.Notebook(groups.single()) else MemoSelection.All
                quickFilter = null
                onCloseSearch()
            }
            added
        },
        onDismiss = { pickedImport = null; pickedCode = null; onCloseImport() },
    )
    LaunchedEffect(openNoteId, memos) {
        val id = openNoteId ?: return@LaunchedEffect
        memos.firstOrNull { it.id == id }?.let { editing = it; onNoteOpened() }
    }

    val selectedKey = (selection as? MemoSelection.Notebook)?.key
    val inAll = selection == MemoSelection.All
    val notebookOf = remember(notebooks) { notebooks.associateBy { it.key } }
    val currentMemos = remember(memos, selection, quickFilter, now) {
        memos
            .filter { inAll || it.courseKey == selectedKey }
            .filter { note ->
                when (quickFilter.takeIf { inAll }) {
                    null -> true
                    MemoQuickFilter.Todo -> !note.completed
                    MemoQuickFilter.Today -> note.dueState(now) == MemoDueState.Today
                    MemoQuickFilter.Overdue -> note.dueState(now) == MemoDueState.Overdue
                }
            }
    }
    val courseSearchText = remember(notebooks) {
        notebooks.associate { it.key to "${it.title} · ${it.subtitle}" }
    }
    val visible = remember(memos, currentMemos, searchOpen, searchQuery, courseSearchText, now) {
        searchMemoNotes(
            memos = memos,
            query = searchQuery.takeIf { searchOpen }.orEmpty(),
            currentMemos = currentMemos,
            courseSearchText = courseSearchText,
        ).sortedWith(memoComparator(now))
    }
    val (active, done) = visible.partition { !it.completed }

    // The global action always includes all notebooks and completed notes.
    if (shareOpen) {
        MemoShareSheet(
            notes = memos.sortedWith(memoComparator(now)),
            notebookOf = notebookOf,
            scopeLabel = stringResource(R.string.memo_share_scope_all),
            onDismiss = onCloseShare,
            onImport = { importOptions = true },
        )
    }
    sharingNote?.let { note ->
        MemoShareSheet(
            notes = listOf(memos.firstOrNull { it.id == note.id } ?: note),
            notebookOf = notebookOf,
            scopeLabel = null,
            onDismiss = { sharingNote = null },
        )
    }

    editing?.let { draft ->
        MemoEditorDialog(
            initial = draft,
            isNew = memos.none { it.id == draft.id },
            notebooks = notebooks,
            openCounts = remember(memos) { memos.filter { !it.completed }.groupingBy { it.courseKey }.eachCount() },
            onDismiss = { editing = null },
            onSave = { saved ->
                onSave(saved)
                editing = null
            },
            onDelete = {
                editing = null
                pendingDelete = draft
            },
            onUpdate = onSave,
        )
    }
    pendingDelete?.let { target ->
        AppConfirmationDialog(
            title = stringResource(R.string.memo_delete_title),
            message = stringResource(R.string.memo_delete_body, target.title.ifBlank { stringResource(R.string.memo_untitled) }),
            confirmLabel = stringResource(R.string.schedule_action_delete),
            cancelLabel = stringResource(R.string.schedule_action_cancel),
            onConfirm = {
                onRemove(target.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    fun newDraft(): MemoNote {
        val notebook = notebookOf[selectedKey]?.takeIf { !inAll }
        return MemoNote(
            id = "memo-${java.util.UUID.randomUUID()}",
            courseKey = notebook?.key,
            courseTitle = notebook?.takeIf { it.key != null }?.title.orEmpty(),
        )
    }

    fun update(note: MemoNote) = onSave(note.copy(updatedAt = System.currentTimeMillis()))

    pendingCompletion?.let { request ->
        MemoCompletionDialog(request.label, onConfirm = {
            pendingCompletion = null
            memos.firstOrNull { it.id == request.noteId }?.let(request::completedNote)?.let(::update)
        }, onDismiss = { pendingCompletion = null })
    }

    MemoTapShieldHost(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (searchOpen) {
                MemoSearchRow(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        searchQuery = ""
                        onCloseSearch()
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!searching) {
                    item(key = "selector") {
                        MemoNotebookSelector(
                            notebooks = notebooks,
                            memos = memos,
                            selection = selection,
                            onSelect = {
                                selection = it
                                quickFilter = null
                            },
                        )
                    }
                    if (inAll) {
                        item(key = "stats") {
                            MemoStatsRow(memos = memos, now = now, selected = quickFilter, onSelect = { quickFilter = it })
                        }
                    } else {
                        item(key = "header") {
                            MemoNotebookHeader(
                                notebook = notebookOf[selectedKey],
                                memos = memos.filter { it.courseKey == selectedKey },
                            )
                        }
                    }
                }
                if (visible.isEmpty()) {
                    item(key = "empty") {
                        Box(appItemMotion()) {
                            Box(Modifier.fillParentMaxWidth().fillParentMaxHeight(0.6f), contentAlignment = Alignment.Center) {
                                MemoEmptyHint(
                                    text = stringResource(
                                        when {
                                            searching -> R.string.memo_search_empty
                                            quickFilter != null && inAll -> R.string.memo_empty_filter
                                            inAll -> R.string.memo_empty_all
                                            else -> R.string.memo_empty_notebook
                                        },
                                    ),
                                    modifier = if (searching) Modifier.testTag("memo-search-empty") else Modifier,
                                )
                            }
                        }
                    }
                }
                items(active, key = { it.id }) { note ->
                    Box(appItemMotion()) {
                        MemoCard(
                            note = note,
                            now = now,
                            notebook = notebookOf[note.courseKey],
                            showNotebook = inAll || searching,
                            onOpen = { editing = note },
                            onToggleLine = { line ->
                                if (note.body.lines().getOrNull(line)?.let(::parseChecklistLine)?.first == true) {
                                    update(note.copy(body = toggleChecklistLine(note.body, line)))
                                } else pendingCompletion = MemoCompletionRequest.from(note, line)
                            },
                            onToggleCompleted = { pendingCompletion = MemoCompletionRequest.from(note) },
                            onTogglePinned = { update(note.copy(pinned = !note.pinned)) },
                            onShare = { sharingNote = note },
                            onDelete = { pendingDelete = note },
                        )
                    }
                }
                if (done.isNotEmpty()) {
                    item(key = "done-header") {
                        Box(appItemMotion()) {
                            Text(
                                text = pluralStringResource(R.plurals.memo_done_header, done.size, done.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                            )
                        }
                    }
                    items(done, key = { it.id }) { note ->
                        Box(appItemMotion()) {
                            MemoCard(
                                note = note,
                                now = now,
                                notebook = notebookOf[note.courseKey],
                                showNotebook = inAll || searching,
                                onOpen = { editing = note },
                                onToggleLine = null,
                                onToggleCompleted = { update(note.copy(completed = !note.completed)) },
                                onTogglePinned = { update(note.copy(pinned = !note.pinned)) },
                                onShare = { sharingNote = note },
                                onDelete = { pendingDelete = note },
                            )
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { haptics.click(); editing = newDraft() },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.memo_new)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        )
    }
}

@Composable
private fun MemoSearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppSearchField(
            query = query,
            onQueryChange = onQueryChange,
            hint = stringResource(R.string.memo_search_hint),
            clearLabel = stringResource(R.string.memo_search_clear),
            modifier = Modifier.weight(1f),
            fieldModifier = Modifier.testTag("memo-search-field"),
            clearModifier = Modifier.testTag("memo-search-clear"),
            onSearch = { keyboard?.hide() },
        )
        AppOutlinedButton(
            onClick = {
                keyboard?.hide()
                focusManager.clearFocus()
                onClose()
            },
            modifier = Modifier.testTag("memo-search-close"),
        ) {
            Text(stringResource(R.string.schedule_action_cancel))
        }
    }
}

@Composable
private fun MemoNotebookSelector(
    notebooks: List<MemoNotebook>,
    memos: List<MemoNote>,
    selection: MemoSelection,
    onSelect: (MemoSelection) -> Unit,
) {
    val openCounts = remember(memos) { memos.filter { !it.completed }.groupingBy { it.courseKey }.eachCount() }
    MemoNotebookDropdown(
        notebooks = notebooks,
        openCounts = openCounts,
        selectedKey = (selection as? MemoSelection.Notebook)?.key,
        includeAll = true,
        allSelected = selection == MemoSelection.All,
        allCount = memos.count { !it.completed },
        onSelect = { key ->
            onSelect(if (key == ALL_NOTEBOOKS) MemoSelection.All else MemoSelection.Notebook(key))
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MemoStatsRow(
    memos: List<MemoNote>,
    now: LocalDateTime,
    selected: MemoQuickFilter?,
    onSelect: (MemoQuickFilter?) -> Unit,
) {
    val haptics = rememberAppHaptics()
    val todo = memos.count { !it.completed }
    val today = memos.count { it.dueState(now) == MemoDueState.Today }
    val overdue = memos.count { it.dueState(now) == MemoDueState.Overdue }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MemoStatCard(
            label = stringResource(R.string.memo_stat_todo),
            value = todo,
            accent = MaterialTheme.colorScheme.primary,
            selected = selected == MemoQuickFilter.Todo,
            onClick = { haptics.click(); onSelect(if (selected == MemoQuickFilter.Todo) null else MemoQuickFilter.Todo) },
            modifier = Modifier.weight(1f),
        )
        MemoStatCard(
            label = stringResource(R.string.memo_stat_today),
            value = today,
            accent = Color(0xFFE39A2D),
            selected = selected == MemoQuickFilter.Today,
            onClick = { haptics.click(); onSelect(if (selected == MemoQuickFilter.Today) null else MemoQuickFilter.Today) },
            modifier = Modifier.weight(1f),
        )
        MemoStatCard(
            label = stringResource(R.string.memo_stat_overdue),
            value = overdue,
            accent = MaterialTheme.colorScheme.error,
            selected = selected == MemoQuickFilter.Overdue,
            onClick = { haptics.click(); onSelect(if (selected == MemoQuickFilter.Overdue) null else MemoQuickFilter.Overdue) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MemoStatCard(
    label: String,
    value: Int,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            AppAtomicText(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppAtomicText(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (value > 0) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MemoNotebookHeader(notebook: MemoNotebook?, memos: List<MemoNote>) {
    notebook ?: return
    val colors = memoNotebookColor(notebook)
    val open = memos.count { !it.completed }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors?.container ?: MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = colors?.onContainer ?: MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = notebook.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val subtitle = when {
                notebook.orphan -> stringResource(R.string.memo_notebook_orphan)
                notebook.key == null -> stringResource(R.string.memo_notebook_other_desc)
                else -> notebook.subtitle
            }
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alpha(0.8f))
            }
            Text(
                text = stringResource(R.string.memo_notebook_counts, memos.size, open),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.alpha(0.75f),
            )
        }
    }
}

@Composable
private fun MemoEmptyHint(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.EditNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                // Balanced breaks keep a lone trailing character off the last line.
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph,
                ),
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MemoCard(
    note: MemoNote,
    now: LocalDateTime,
    notebook: MemoNotebook?,
    showNotebook: Boolean,
    onOpen: () -> Unit,
    onToggleLine: ((Int) -> Unit)?,
    onToggleCompleted: () -> Unit,
    onTogglePinned: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val priorityColor = memoPriorityColor(note.priority)
    var menu by remember { mutableStateOf(false) }
    val press = remember { MutableInteractionSource() }
    Surface(
        interactionSource = press,
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (note.pinned && !note.completed) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (note.completed) 0.6f else 1f)
            .pressFeedback(press),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Show a priority stripe only when priority is assigned.
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(priorityColor),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MemoDoneToggle(done = note.completed, onClick = onToggleCompleted)
                    Spacer(Modifier.width(10.dp))
                    if (note.pinned && !note.completed) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = stringResource(R.string.memo_pinned),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = note.title.ifBlank { stringResource(R.string.memo_untitled) },
                        style = MaterialTheme.typography.titleMedium.copy(
                            textDecoration = if (note.completed) TextDecoration.LineThrough else null,
                        ),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.memo_more))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (note.pinned) R.string.memo_unpin else R.string.memo_pin)) },
                                leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onTogglePinned()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(if (note.completed) R.string.memo_mark_undone else R.string.memo_mark_done))
                                },
                                leadingIcon = { Icon(Icons.Rounded.Check, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onToggleCompleted()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.memo_share)) },
                                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onShare()
                                },
                                modifier = Modifier.testTag("memo-share-note"),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.schedule_action_delete)) },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
                val showTags = (showNotebook && notebook != null) ||
                    note.priority != MemoPriority.None ||
                    note.dueDateTime != null
                if (showTags) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        if (showNotebook && notebook != null) MemoNotebookTag(notebook)
                        if (note.priority != MemoPriority.None) {
                            MemoTag(text = memoPriorityLabel(note.priority), color = priorityColor)
                        }
                        MemoDueTag(note = note, now = now)
                    }
                }
                if (note.body.isNotBlank()) {
                    MemoBody(
                        body = note.body,
                        onToggle = onToggleLine,
                        maxBlocks = 8,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                val progress = note.checklist
                if (progress.hasItems) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            drawStopIndicator = {},
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "${progress.done}/${progress.total}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoDoneToggle(done: Boolean, onClick: () -> Unit) {
    val haptics = rememberAppHaptics()
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (done) primary else Color.Transparent)
            .border(2.dp, if (done) primary else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable {
                if (!done) haptics.confirm() else haptics.click()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.memo_mark_undone),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
internal fun MemoTag(text: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MemoNotebookTag(notebook: MemoNotebook) {
    val colors = memoNotebookColor(notebook)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors?.container ?: MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = notebook.title,
            style = MaterialTheme.typography.labelSmall,
            color = colors?.onContainer ?: MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MemoDueTag(note: MemoNote, now: LocalDateTime) {
    val due = note.dueDateTime ?: return
    val clock = memoDueText(due, now)
    when (note.dueState(now)) {
        MemoDueState.Overdue -> MemoTag(
            text = stringResource(R.string.memo_due_overdue, memoDurationText(Duration.between(due, now)), clock),
            color = MaterialTheme.colorScheme.error,
            icon = Icons.Rounded.Schedule,
        )
        MemoDueState.Today -> MemoTag(
            text = stringResource(R.string.memo_due_today, clock),
            color = Color(0xFFE39A2D),
            icon = Icons.Rounded.Schedule,
        )
        MemoDueState.Soon -> MemoTag(
            text = stringResource(R.string.memo_due_soon, memoDurationText(Duration.between(now, due)), clock),
            color = Color(0xFFE39A2D),
            icon = Icons.Rounded.Schedule,
        )
        MemoDueState.Later, MemoDueState.None -> MemoTag(
            text = stringResource(R.string.memo_due_later, clock),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = Icons.Rounded.Schedule,
        )
    }
}

@Composable
internal fun memoDueText(due: LocalDateTime, now: LocalDateTime): String {
    val pattern = if (due.year == now.year) {
        stringResource(R.string.memo_due_pattern)
    } else {
        stringResource(R.string.memo_due_pattern_year)
    }
    return due.format(DateTimeFormatter.ofPattern(pattern, LocalConfiguration.current.locales[0]))
}

@Composable
private fun memoDurationText(duration: Duration): String {
    val minutes = duration.toMinutes().coerceAtLeast(1)
    return when {
        minutes >= 24 * 60 -> pluralStringResource(R.plurals.memo_days, (minutes / (24 * 60)).toInt(), minutes / (24 * 60))
        minutes >= 60 -> pluralStringResource(R.plurals.memo_hours, (minutes / 60).toInt(), minutes / 60)
        else -> pluralStringResource(R.plurals.memo_minutes, minutes.toInt(), minutes)
    }
}
