package com.x500x.cursimple.feature.schedule

import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.serialization.json.Json
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.x500x.cursimple.core.kernel.model.MemoDueState
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.model.MemoPriority
import com.x500x.cursimple.core.kernel.model.dueState
import com.x500x.cursimple.core.kernel.model.toggleChecklistLine
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import androidx.compose.foundation.layout.PaddingValues
import com.x500x.cursimple.feature.plugin.ui.AppConfirmationDialog
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import java.time.LocalDateTime
import java.time.LocalTime

private enum class MemoDuePick { Date, Time }

/** Editable note fields besides the body, which lives in [MemoBlockEditorState]. */
internal data class MemoDraft(
    val courseKey: String?,
    val title: String,
    val priority: MemoPriority,
    val pinned: Boolean,
    val due: LocalDateTime?,
) {
    companion object {
        fun of(note: MemoNote) = MemoDraft(note.courseKey, note.title, note.priority, note.pinned, note.dueDateTime)
    }
}

private val MemoDraftSaver = listSaver<MemoDraft, Any?>(
    save = { listOf(it.courseKey, it.title, it.priority.ordinal, it.pinned, it.due?.toString()) },
    restore = { MemoDraft(it[0] as String?, it[1] as String, MemoPriority.entries[it[2] as Int],
        it[3] as Boolean, (it[4] as String?)?.let(LocalDateTime::parse)) },
)
private val MemoNoteSaver = Saver<MemoNote, String>(
    save = { Json.encodeToString(MemoNote.serializer(), it) },
    restore = { Json.decodeFromString(MemoNote.serializer(), it) },
)
private val MemoBlockStateSaver = Saver<MemoBlockEditorState, String>(
    save = { it.body }, restore = { MemoBlockEditorState(it) },
)

private fun LocalDateTime?.toMinute(): LocalDateTime? = this?.withSecond(0)?.withNano(0)

/** Whether saving [draft] and [body] would change [note]; trailing whitespace is ignored. */
internal fun memoDraftChanged(note: MemoNote, draft: MemoDraft, body: String): Boolean =
    draft.courseKey != note.courseKey ||
        draft.title.trim() != note.title.trim() ||
        body.trimEnd() != note.body.trimEnd() ||
        draft.priority != note.priority ||
        draft.pinned != note.pinned ||
        draft.due.toMinute() != note.dueDateTime.toMinute()

/**
 * Full-screen note view. Existing notes open read-only and switch to editing via the edit
 * button; saving changes and leaving with unsaved edits both ask first. New notes start editing.
 * [onUpdate] persists confirmed edits without closing; when absent, saving falls back to [onSave].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoEditorDialog(
    initial: MemoNote,
    isNew: Boolean,
    notebooks: List<MemoNotebook>,
    openCounts: Map<String?, Int>,
    onDismiss: () -> Unit,
    onSave: (MemoNote) -> Unit,
    onDelete: () -> Unit,
    onUpdate: ((MemoNote) -> Unit)? = null,
) {
    val zone = LocalAppZone.current
    var base by rememberSaveable(initial.id, stateSaver = MemoNoteSaver) { mutableStateOf(initial) }
    var editing by rememberSaveable(initial.id) { mutableStateOf(isNew) }
    var draft by rememberSaveable(initial.id, stateSaver = MemoDraftSaver) { mutableStateOf(MemoDraft.of(initial)) }
    var editor by rememberSaveable(initial.id, stateSaver = MemoBlockStateSaver) { mutableStateOf(MemoBlockEditorState(initial.body)) }
    var duePick by remember { mutableStateOf<MemoDuePick?>(null) }
    var confirmSave by remember { mutableStateOf(false) }
    var unsavedPrompt by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }

    when (duePick) {
        MemoDuePick.Date -> AlarmDatePickerDialog(
            initial = (draft.due ?: BeijingTime.nowDateTimeIn(zone)).toLocalDate(),
            onDismiss = { duePick = null },
            onPick = { date ->
                draft = draft.copy(due = date.atTime(draft.due?.toLocalTime() ?: LocalTime.of(23, 59)))
                duePick = null
            },
        )
        MemoDuePick.Time -> EventTimePickerDialog(
            initialMinute = (draft.due ?: LocalDateTime.now()).let { it.hour * 60 + it.minute },
            onDismiss = { duePick = null },
            onPick = { minute ->
                val date = draft.due?.toLocalDate() ?: BeijingTime.nowDateTimeIn(zone).toLocalDate()
                draft = draft.copy(due = date.atTime(minute / 60, minute % 60))
                duePick = null
            },
        )
        null -> Unit
    }

    val canSave = (draft.title.isNotBlank() || editor.body.isNotBlank()) && !editor.importingImage
    val changed = editing && if (isNew) canSave else memoDraftChanged(base, draft, editor.body)

    fun buildNote(): MemoNote {
        val notebook = notebooks.firstOrNull { it.key == draft.courseKey }
        val now = System.currentTimeMillis()
        return base.copy(
            courseKey = draft.courseKey,
            courseTitle = notebook?.takeIf { it.key != null }?.title
                ?: base.courseTitle.takeIf { draft.courseKey != null }.orEmpty(),
            title = draft.title.trim(),
            body = editor.body.trimEnd(),
            priority = draft.priority,
            pinned = draft.pinned,
            dueAt = draft.due.toMinute()?.toString(),
            createdAt = base.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now,
        )
    }

    fun persist() {
        val note = buildNote()
        val update = onUpdate
        if (isNew || update == null) {
            onSave(note)
        } else {
            update(note)
            base = note
            editing = false
        }
    }

    fun startEditing() {
        draft = MemoDraft.of(base)
        editor = MemoBlockEditorState(base.body)
        editing = true
    }

    fun leaveEditing() {
        if (isNew) onDismiss() else editing = false
    }

    fun requestExit() {
        when {
            !editing -> onDismiss()
            changed -> unsavedPrompt = true
            else -> leaveEditing()
        }
    }

    fun requestSave() {
        when {
            isNew -> persist()
            !changed -> editing = false
            else -> confirmSave = true
        }
    }

    if (confirmSave) {
        AppConfirmationDialog(
            title = stringResource(R.string.memo_editor_save_confirm_title),
            message = stringResource(R.string.memo_editor_save_confirm_body),
            confirmLabel = stringResource(R.string.schedule_action_save),
            cancelLabel = stringResource(R.string.schedule_action_cancel),
            onConfirm = {
                confirmSave = false
                persist()
            },
            onDismiss = { confirmSave = false },
        )
    }
    if (unsavedPrompt) {
        MemoUnsavedDialog(
            canSave = canSave,
            onSave = {
                unsavedPrompt = false
                persist()
            },
            onDiscard = {
                unsavedPrompt = false
                leaveEditing()
            },
            onKeepEditing = { unsavedPrompt = false },
        )
    }

    Dialog(
        onDismissRequest = ::requestExit,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        MemoTapShieldHost(Modifier.fillMaxSize()) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppCircleIconButton(onClick = ::requestExit) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(
                                    if (editing) R.string.schedule_action_cancel else R.string.schedule_action_close,
                                ),
                            )
                        }
                        AppAtomicText(
                            text = stringResource(
                                when {
                                    isNew -> R.string.memo_editor_new
                                    editing -> R.string.memo_editor_edit
                                    else -> R.string.memo_editor_view
                                },
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (editing) {
                            MemoEditorHistoryControls(editor, enabled = !editor.importingImage)
                            Button(onClick = ::requestSave, enabled = canSave, contentPadding = PaddingValues(horizontal = 12.dp)) {
                                AppAtomicText(stringResource(R.string.schedule_action_save))
                            }
                        } else {
                            var copyMenu by remember { mutableStateOf(false) }
                            val context = LocalContext.current
                            fun copyNote(markdown: Boolean) {
                                val doc = buildMemoShareDocument(
                                    context, listOf(base), notebooks.associateBy { it.key }, null, BeijingTime.nowDateTimeIn(zone),
                                )
                                copyMemoText(context, if (markdown) memoShareMarkdown(doc) else memoSharePlainText(doc))
                            }
                            Box {
                                AppCircleIconButton(onClick = { copyMenu = true }, modifier = Modifier.testTag("memo-copy-note")) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.memo_note_copy))
                                }
                                DropdownMenu(expanded = copyMenu, onDismissRequest = { copyMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.memo_note_copy_plain)) },
                                        onClick = { copyMenu = false; copyNote(markdown = false) },
                                        modifier = Modifier.testTag("memo-copy-plain"),
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.memo_note_copy_markdown)) },
                                        onClick = { copyMenu = false; copyNote(markdown = true) },
                                        modifier = Modifier.testTag("memo-copy-markdown"),
                                    )
                                }
                            }
                            AppCircleIconButton(onClick = ::startEditing) {
                                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.schedule_action_edit))
                            }
                        }
                    }
                    Crossfade(
                        targetState = editing,
                        animationSpec = tween(220),
                        modifier = Modifier.weight(1f),
                        label = "memo-mode",
                    ) { isEditing ->
                        if (isEditing) {
                            MemoEditContent(
                                draft = draft,
                                onDraftChange = { draft = it },
                                editor = editor,
                                isNew = isNew,
                                notebooks = notebooks,
                                openCounts = openCounts,
                                titleFocus = titleFocus,
                                onPickDue = { duePick = it },
                                onDelete = onDelete,
                            )
                        } else {
                            MemoReadContent(
                                note = base,
                                notebooks = notebooks,
                                onToggle = null,
                            )
                        }
                    }
                    if (editing) MemoEditorToolbar(editor)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoEditContent(
    draft: MemoDraft,
    onDraftChange: (MemoDraft) -> Unit,
    editor: MemoBlockEditorState,
    isNew: Boolean,
    notebooks: List<MemoNotebook>,
    openCounts: Map<String?, Int>,
    titleFocus: FocusRequester,
    onPickDue: (MemoDuePick) -> Unit,
    onDelete: () -> Unit,
) {
    val zone = LocalAppZone.current
    // New notes open with the keyboard on the title.
    LaunchedEffect(Unit) {
        if (isNew) runCatching { titleFocus.requestFocus() }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MemoEditorSection(stringResource(R.string.memo_editor_notebook)) {
            MemoNotebookDropdown(
                notebooks = notebooks,
                openCounts = openCounts,
                selectedKey = draft.courseKey,
                onSelect = { onDraftChange(draft.copy(courseKey = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = draft.title,
            onValueChange = { onDraftChange(draft.copy(title = it)) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(titleFocus),
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium,
            placeholder = { Text(stringResource(R.string.memo_editor_title_hint)) },
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { editor.focusLine() }),
        )

        MemoBlockEditor(state = editor, modifier = Modifier.fillMaxWidth())

        MemoEditorSection(stringResource(R.string.memo_editor_priority)) {
            MemoPrioritySelector(draft.priority, onChange = { onDraftChange(draft.copy(priority = it)) })
        }

        MemoEditorSection(stringResource(R.string.memo_editor_due)) {
            val current = draft.due
            if (current == null) {
                AppOutlinedButton(onClick = {
                    onDraftChange(draft.copy(due = BeijingTime.nowDateTimeIn(zone).toLocalDate().atTime(23, 59)))
                    onPickDue(MemoDuePick.Date)
                }) { Text(stringResource(R.string.memo_editor_due_add)) }
            } else {
                AlarmValueRow(
                    label = stringResource(R.string.memo_editor_due_date),
                    value = formatPickerDate(current.toLocalDate()),
                    onClick = { onPickDue(MemoDuePick.Date) },
                )
                AlarmValueRow(
                    label = stringResource(R.string.memo_editor_due_time),
                    value = "%02d:%02d".format(current.hour, current.minute),
                    onClick = { onPickDue(MemoDuePick.Time) },
                )
                AppOutlinedButton(onClick = { onDraftChange(draft.copy(due = null)) }) {
                    Text(stringResource(R.string.memo_editor_due_clear))
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.PushPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.memo_pin), fontWeight = FontWeight.SemiBold)
                Text(
                    text = stringResource(R.string.memo_editor_pin_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = draft.pinned, onCheckedChange = { onDraftChange(draft.copy(pinned = it)) })
        }

        if (!isNew) {
            val error = MaterialTheme.colorScheme.error
            AppOutlinedButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = error),
                border = BorderStroke(1.dp, error.copy(alpha = 0.6f)),
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.memo_editor_delete))
            }
        }
    }
}

/** Rendered, read-only note; only round checklist controls can change it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MemoReadContent(
    note: MemoNote,
    notebooks: List<MemoNotebook>,
    onToggle: ((Int) -> Unit)?,
    horizontalPadding: Dp = 16.dp,
) {
    val zone = LocalAppZone.current
    val now = BeijingTime.nowDateTimeIn(zone)
    // Long-press selects text with the system copy handles.
    androidx.compose.foundation.text.selection.SelectionContainer {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding)
            .padding(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = note.title.ifBlank { stringResource(R.string.memo_untitled) },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (note.title.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        val notebook = note.courseKey?.let { key -> notebooks.firstOrNull { it.key == key } }
        val due = note.dueDateTime
        if (note.courseKey != null || note.priority != MemoPriority.None || due != null || note.pinned) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (note.courseKey != null) {
                    val colors = memoNotebookColor(notebook)
                    MemoTag(
                        text = notebook?.title ?: note.courseTitle.ifBlank { note.courseKey.orEmpty() },
                        color = colors?.onContainer ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (note.priority != MemoPriority.None) {
                    MemoTag(text = memoPriorityLabel(note.priority), color = memoPriorityColor(note.priority))
                }
                if (due != null) {
                    MemoTag(
                        text = stringResource(R.string.memo_due_later, memoDueText(due, now)),
                        color = if (note.dueState(now) == MemoDueState.Overdue) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        icon = Icons.Rounded.Schedule,
                    )
                }
                if (note.pinned) {
                    MemoTag(
                        text = stringResource(R.string.memo_pinned),
                        color = MaterialTheme.colorScheme.primary,
                        icon = Icons.Rounded.PushPin,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (note.body.isBlank()) {
            Text(
                text = stringResource(R.string.memo_editor_preview_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            MemoBody(
                body = note.body,
                onToggle = onToggle,
                style = MemoBodyStyle.Document,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    }
}

/** Put [text] on the clipboard; Android 13+ confirms by itself, older versions get a toast. */
internal fun copyMemoText(context: android.content.Context, text: String) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("note", text))
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        android.widget.Toast.makeText(context, R.string.memo_note_copied, android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** Save, discard or keep editing; stacked full-width so all three fit narrow screens. */
@Composable
private fun MemoUnsavedDialog(
    canSave: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    val error = MaterialTheme.colorScheme.error
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = {
            Text(
                stringResource(R.string.memo_editor_unsaved_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = { Text(stringResource(R.string.memo_editor_unsaved_body), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.schedule_action_save))
                }
                AppOutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = error),
                    border = BorderStroke(1.dp, error.copy(alpha = 0.6f)),
                ) { Text(stringResource(R.string.memo_editor_discard)) }
                AppOutlinedButton(onClick = onKeepEditing, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.memo_editor_keep_editing))
                }
            }
        },
    )
}

@Composable
private fun MemoEditorSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}
