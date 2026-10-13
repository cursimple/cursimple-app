package com.x500x.cursimple.app

import android.content.Context
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.UserPreferences
import com.x500x.cursimple.core.data.memo.*
import com.x500x.cursimple.core.kernel.model.MemoNote
import com.x500x.cursimple.core.kernel.model.MemoPriority
import com.x500x.cursimple.core.kernel.model.allCoursesWith
import com.x500x.cursimple.core.kernel.model.visibleScheduleCourses
import com.x500x.cursimple.feature.schedule.MemoNotebook
import com.x500x.cursimple.feature.schedule.MemoNotebookSelector
import com.x500x.cursimple.feature.schedule.MemoPrioritySelector
import com.x500x.cursimple.feature.schedule.MemoDeadlineFields
import com.x500x.cursimple.feature.schedule.MemoMiniEditorField
import com.x500x.cursimple.feature.schedule.MemoMiniEditorToolbar
import com.x500x.cursimple.feature.schedule.MemoMiniEditorHistory
import com.x500x.cursimple.feature.schedule.MemoMiniReader
import com.x500x.cursimple.feature.schedule.MemoCompletionPrompt
import com.x500x.cursimple.feature.schedule.MemoCompletionRequest
import com.x500x.cursimple.feature.schedule.rememberMemoRichTextState
import com.x500x.cursimple.feature.schedule.buildMemoNotebooks
import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import com.x500x.cursimple.feature.plugin.ui.AppConfirmationDialog
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import com.x500x.cursimple.feature.widget.WidgetDeepLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.util.UUID

/** A floating window with its own task; closing returns to the launcher, not the Notes page. */
class MemoTodoQuickEditActivity : ComponentActivity() {
    private var original: MemoNote? = null
    private var closing = false
    private var vanished = false

    // Save callbacks can resume off the main thread; finishing touches window attributes.
    private fun closeWindow() = runOnUiThread {
        if (closing) return@runOnUiThread
        closing = true
        finish()
    }

    override fun finish() {
        if (!vanished) { vanished = true; vanishThen(::finish); return }
        window.setWindowAnimations(0)
        if (Build.VERSION.SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        if (isTaskRoot) super.finishAndRemoveTask() else super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocale.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        setFinishOnTouchOutside(false)
        window.setWindowAnimations(0)
        window.setDimAmount(0.25f)
        val completing = intent.getBooleanExtra(WidgetDeepLinks.EXTRA_CONFIRM_MEMO_COMPLETION, false)
        val maxWidth = (440 * resources.displayMetrics.density).toInt()
        @Suppress("DEPRECATION")
        val windowWidth = if (Build.VERSION.SDK_INT >= 30) windowManager.currentWindowMetrics.bounds.width()
            else android.graphics.Point().also { windowManager.defaultDisplay.getSize(it) }.x
        val width = (windowWidth - 32 * resources.displayMetrics.density).toInt()
        // Loading, editing and settings share one window size. Never resize the launcher widget.
        @Suppress("DEPRECATION")
        val windowHeight = if (Build.VERSION.SDK_INT >= 30) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars())
            metrics.bounds.height() - insets.top - insets.bottom
        } else resources.displayMetrics.heightPixels
        val height = minOf(QUICK_EDIT_MAX_HEIGHT_DP * resources.displayMetrics.density, windowHeight - 48 * resources.displayMetrics.density).toInt()
        val container = (application as ClassScheduleApplication).appContainer
        val repo = DataStoreMemoRepository(this)
        val noteId = intent.getStringExtra(WidgetDeepLinks.EXTRA_OPEN_MEMO)
        val isNew = intent.getBooleanExtra(WidgetDeepLinks.EXTRA_CREATE_MEMO, false)
        val line = intent.getIntExtra(WidgetDeepLinks.EXTRA_MEMO_LINE, -1).takeIf { it >= 0 }
        val revision = intent.getStringExtra(WidgetDeepLinks.EXTRA_MEMO_REVISION)
        original = savedInstanceState?.getString("original")?.let {
            runCatching { Json.decodeFromString<MemoNote>(it) }.getOrNull()
        }
        setContent {
            val prefs by container.userPreferencesRepository.preferencesFlow.collectAsState(initial = UserPreferences())
            ClassScheduleTheme(prefs.themeMode, prefs.themeAccent, prefs.themeCustomColorArgb) {
                var note by remember { mutableStateOf<MemoNote?>(original) }
                var notebooks by remember { mutableStateOf<List<MemoNotebook>>(emptyList()) }
                var openCounts by remember { mutableStateOf<Map<String?, Int>>(emptyMap()) }
                var loading by remember { mutableStateOf(note == null) }
                var error by remember { mutableStateOf(false) }
                var retry by remember { mutableIntStateOf(0) }
                LaunchedEffect(retry) {
                    loading = true
                    error = false
                    val loaded = runCatching { withContext(Dispatchers.IO) {
                        container.bootstrapJob.join()
                        val notes = repo.notesFlow.first()
                        if (completing) {
                            return@withContext Triple(notes.firstOrNull { it.id == noteId }, emptyList<MemoNotebook>(), emptyMap<String?, Int>())
                        }
                        val courses = container.scheduleRepository.scheduleFlow.first()
                            .allCoursesWith(container.manualCourseRepository.manualCoursesFlow.first()).visibleScheduleCourses()
                        val choices = buildMemoNotebooks(courses, notes,
                            getString(com.x500x.cursimple.feature.schedule.R.string.memo_notebook_other))
                        val selected = note ?: if (isNew) MemoNote(UUID.randomUUID().toString(), createdAt = System.currentTimeMillis())
                            else notes.firstOrNull { it.id == noteId }
                        Triple(selected, choices, notes.filter { !it.completed && !it.isBlank &&
                            (!it.checklist.hasItems || it.checklist.done < it.checklist.total) }.groupingBy { it.courseKey }.eachCount())
                    } }.getOrNull()
                    note = loaded?.first
                    notebooks = loaded?.second.orEmpty()
                    openCounts = loaded?.third.orEmpty()
                    original = note
                    error = note == null
                    loading = false
                }
                Box((if (completing) Modifier.fillMaxWidth() else Modifier.fillMaxSize()).clipToBounds()) {
                    Surface(modifier = if (completing) Modifier.fillMaxWidth() else Modifier.fillMaxSize(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                        val current = note
                        when {
                            completing && !loading -> MemoTodoCompletionWindow(
                                note = current, line = line, revision = revision, onClose = ::closeWindow,
                                onComplete = {
                                    val changed = withContext(Dispatchers.IO) {
                                        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                                        val manager = AppWidgetManager.getInstance(applicationContext)
                                        val info = manager.getAppWidgetInfo(widgetId)
                                        if (current == null || revision == null || info?.provider?.packageName != packageName) false
                                        else repo.completeTodo(current.id, line, revision)
                                    }
                                    // The application observes note commits and refreshes the widget once.
                                    changed
                                },
                            )
                            current != null && !loading -> MemoTodoQuickEditor(current, line.takeIf { memoTodoRevision(current.body) == revision }, isNew, notebooks, openCounts, onClose = ::closeWindow,
                                onSave = { draft ->
                                    val result = withContext(Dispatchers.IO) {
                                        if (isNew) {
                                            val created = current.copy(title = draft.text.trim(), body = draft.body,
                                                priority = draft.priority, pinned = draft.pinned, dueAt = draft.dueAt,
                                                courseKey = draft.courseKey, courseTitle = draft.courseTitle, updatedAt = System.currentTimeMillis())
                                            if (repo.importNotes(listOf(created)) == 1) MemoTodoEditResult.Saved else MemoTodoEditResult.Conflict
                                        } else repo.saveTodoEdit(current, null, draft)
                                    }
                                    result
                                })
                            else -> Column((if (completing) Modifier.fillMaxWidth() else Modifier.fillMaxSize()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                BackHandler(onBack = ::closeWindow)
                                Text(stringResource(if (completing) com.x500x.cursimple.feature.schedule.R.string.memo_complete_confirm_title
                                    else if (isNew) R.string.memo_quick_add_title else R.string.memo_quick_title), style = MaterialTheme.typography.titleMedium)
                                if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                                if (error) {
                                    Text(stringResource(R.string.memo_quick_unavailable))
                                    AppOutlinedButton(onClick = { retry++ }) { AppAtomicText(stringResource(R.string.memo_quick_retry)) }
                                }
                                AppOutlinedButton(onClick = ::closeWindow) { AppAtomicText(stringResource(R.string.memo_quick_close)) }
                            }
                        }
                    }
                }
            }
        }
        // A floating window resets its layout to wrap content when setContent installs the decor.
        window.setLayout(minOf(width, maxWidth), if (completing) WindowManager.LayoutParams.WRAP_CONTENT else height.coerceAtLeast(1))
    }


    override fun onSaveInstanceState(outState: Bundle) {
        original?.let { outState.putString("original", Json.encodeToString(MemoNote.serializer(), it)) }
        super.onSaveInstanceState(outState)
    }
}

@Composable
internal fun MemoTodoCompletionWindow(
    note: MemoNote?,
    line: Int?,
    revision: String?,
    onClose: () -> Unit,
    onComplete: suspend () -> Boolean,
) {
    val target = remember(note, line, revision) {
        note?.takeIf { memoTodoRevision(it.body) == revision && (line != null || !it.checklist.hasItems) }
            ?.let { MemoCompletionRequest.from(it, line) }
    }
    var busy by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf(target == null) }
    var error by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    BackHandler { if (!busy) onClose() }
    MemoCompletionPrompt(label = target?.label.orEmpty(), busy = busy, canConfirm = !unavailable,
        error = if (unavailable) stringResource(com.x500x.cursimple.feature.widget.R.string.widget_memo_changed)
            else error?.let { stringResource(it) },
        onCancel = { if (!busy) onClose() },
        onConfirm = {
            if (!busy && !unavailable) {
                busy = true
                error = null
                scope.launch {
                    val result = runCatching { onComplete() }
                    if (result.getOrNull() == true) onClose()
                    else if (result.isSuccess) unavailable = true
                    else error = com.x500x.cursimple.feature.widget.R.string.widget_memo_save_failed
                    busy = false
                }
            }
        },
    )
}

@Composable
internal fun MemoTodoQuickEditor(note: MemoNote, line: Int?, isNew: Boolean, notebooks: List<MemoNotebook>,
    openCounts: Map<String?, Int>, onClose: () -> Unit, onSave: suspend (MemoTodoEditDraft) -> MemoTodoEditResult) {
    val initial = remember(note) { requireNotNull(MemoTodoEditDraft.from(note, null)) }
    val editor = rememberMemoRichTextState(initial.body, note.id)
    var text by rememberSaveable(note.id) { mutableStateOf(initial.text) }
    var priority by rememberSaveable(note.id) { mutableStateOf(initial.priority) }
    var pinned by rememberSaveable(note.id) { mutableStateOf(initial.pinned) }
    var due by rememberSaveable(note.id) { mutableStateOf(initial.dueAt) }
    var courseKey by rememberSaveable(note.id) { mutableStateOf(initial.courseKey) }
    var courseTitle by rememberSaveable(note.id) { mutableStateOf(initial.courseTitle) }
    var options by rememberSaveable { mutableStateOf(false) }
    // Existing notes open for reading; only the header's edit button turns on editing.
    var editing by rememberSaveable(note.id) { mutableStateOf(isNew) }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val draft = MemoTodoEditDraft(text, editor.body, priority, pinned, due, courseKey, courseTitle)
    val changed = draft != initial
    val valid = (text.isNotBlank() || editor.hasContent) && !editor.importingImage
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val contentScroll = rememberScrollState()
    val optionsScroll = rememberScrollState()
    fun leaveInput() { focus.clearFocus(); keyboard?.hide() }
    fun close() { if (!busy) { if (changed) { leaveInput(); discard = true } else onClose() } }
    BackHandler {
        if (options && !busy) { leaveInput(); options = false } else close()
    }
    // Keep the keyboard closed on entry; the user focuses a field after the window settles.
    LaunchedEffect(options) { if (options) optionsScroll.scrollTo(0) }
    if (discard) AppConfirmationDialog(title = stringResource(R.string.memo_quick_discard_title),
        message = stringResource(R.string.memo_quick_discard_body),
        confirmLabel = stringResource(R.string.memo_quick_discard), cancelLabel = stringResource(R.string.memo_quick_keep),
        onConfirm = onClose, onDismiss = { discard = false })
    if (confirm) AppConfirmationDialog(title = stringResource(R.string.memo_quick_confirm_title),
        message = stringResource(R.string.memo_quick_confirm_body),
        confirmLabel = stringResource(R.string.memo_quick_save), cancelLabel = stringResource(R.string.memo_quick_cancel),
        onDismiss = { confirm = false }, onConfirm = {
            confirm = false; busy = true; error = null
            scope.launch {
                val result = runCatching { onSave(draft) }.getOrNull()
                busy = false
                if (result == MemoTodoEditResult.Saved) onClose()
                else error = if (result == MemoTodoEditResult.Conflict || result == MemoTodoEditResult.Missing)
                    R.string.memo_quick_conflict else R.string.memo_quick_save_failed
            }
        })
    val maxHeight = minOf(QUICK_EDIT_MAX_HEIGHT_DP, (LocalConfiguration.current.screenHeightDp - 48).coerceAtLeast(200)).dp
    if (!editing) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(16.dp).testTag("memo-quick-editor")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppAtomicText(stringResource(R.string.memo_quick_view_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                AppCircleIconButton(onClick = { editing = true }, modifier = Modifier.testTag("memo-quick-edit")) {
                    Icon(Icons.Rounded.Edit, stringResource(R.string.memo_quick_edit))
                }
                AppCircleIconButton(onClick = onClose, modifier = Modifier.testTag("memo-quick-close")) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.memo_quick_close))
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) { MemoMiniReader(note, notebooks) }
        }
        return
    }
    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(16.dp).testTag("memo-quick-editor")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppAtomicText(stringResource(if (options) R.string.memo_quick_settings_title else if (isNew) R.string.memo_quick_add_title else R.string.memo_quick_title),
                Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            AppCircleIconButton(onClick = { leaveInput(); options = !options }, enabled = !busy,
                modifier = Modifier.testTag(if (options) "memo-quick-back" else "memo-quick-more")) {
                Icon(if (options) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Tune,
                    stringResource(if (options) R.string.memo_quick_back else R.string.memo_quick_settings_title))
            }
            AppCircleIconButton(onClick = ::close, enabled = !busy, modifier = Modifier.testTag("memo-quick-close")) {
                Icon(Icons.Rounded.Close, stringResource(R.string.memo_quick_close))
            }
        }
        // History shares the course label row so it does not cost a row of its own.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.memo_quick_course), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            MemoMiniEditorHistory(editor, enabled = !options && !busy && !editor.importingImage)
        }
        Spacer(Modifier.height(4.dp))
        error?.let {
            Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("memo-quick-error"))
            Spacer(Modifier.height(8.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val settings = options
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(if (settings) optionsScroll else contentScroll),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MemoNotebookSelector(notebooks, courseKey, modifier = Modifier.fillMaxWidth().testTag("memo-quick-course"),
                        enabled = !busy, openCounts = openCounts) { selected ->
                        courseKey = selected.key; courseTitle = if (selected.key == null) "" else selected.title
                    }
                    if (!settings) {
                        OutlinedTextField(text, { text = it; error = null }, enabled = !busy, singleLine = true,
                            placeholder = { Text(stringResource(com.x500x.cursimple.feature.schedule.R.string.memo_editor_title_hint)) },
                            textStyle = MaterialTheme.typography.titleMedium, shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().testTag("memo-quick-text"))
                        MemoMiniEditorField(editor, enabled = !busy, lineTagPrefix = "memo-quick-body", modifier = Modifier.fillMaxWidth())
                    } else {
                        Text(stringResource(R.string.memo_quick_priority), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        MemoPrioritySelector(priority, onChange = { priority = it }, enabled = !busy, compact = true)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.memo_quick_pin), Modifier.weight(1f))
                            Switch(pinned, onCheckedChange = { pinned = it }, enabled = !busy)
                        }
                        Text(stringResource(R.string.memo_quick_due), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        MemoDeadlineFields(due?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
                            onChange = { due = it?.toString() }, enabled = !busy)
                    }
                }
                if (!settings) {
                    Spacer(Modifier.height(8.dp))
                    MemoMiniEditorToolbar(editor, enabled = !busy, modifier = Modifier.testTag("memo-quick-toolbar"))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppOutlinedButton(onClick = ::close, enabled = !busy, modifier = Modifier.weight(1f)) {
                AppAtomicText(stringResource(R.string.memo_quick_cancel))
            }
            Button(onClick = { leaveInput(); confirm = true }, enabled = changed && valid && !busy,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("memo-quick-save")) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else AppAtomicText(stringResource(R.string.memo_quick_save))
            }
        }
    }
}

/** Editing and settings share this fixed window height so switching panels never resizes it. */
private const val QUICK_EDIT_MAX_HEIGHT_DP = 460
