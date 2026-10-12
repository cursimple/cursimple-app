package com.x500x.cursimple.feature.schedule

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.testTag

/** An invisible prefix makes line-start Backspace observable to the soft keyboard. */
private const val LINE_START = '\u200B'

private const val UNDO_LIMIT = 100
private const val TYPING_PAUSE_MS = 1_000L

private fun lineField(content: String, cursor: Int = content.length, end: Int = cursor): TextFieldValue =
    TextFieldValue(LINE_START + content, TextRange(cursor + 1, end + 1))

private data class MemoEditorSnapshot(val lines: List<MemoLine>, val focusId: Long?, val cursor: Int, val body: String)

/**
 * Markdown-backed block editor state. Enter only breaks lines; formatting comes from typed
 * prefixes or the toolbar. Structural edits and typing bursts are undoable.
 */
@Stable
internal class MemoBlockEditorState(initialBody: String, private val clock: () -> Long = SystemClock::uptimeMillis) {
    private var idSource = 0L
    private fun newId(): Long = ++idSource

    val lines = mutableStateListOf<MemoLine>().apply {
        addAll(parseMemoLines(initialBody, ::newId))
        if (last().kind == MemoLineKind.Image || last().kind == MemoLineKind.Divider) add(MemoLine(newId()))
    }
    internal val fields = mutableStateMapOf<Long, TextFieldValue>().apply {
        lines.forEach { put(it.id, lineField(it.content)) }
    }
    internal val requesters = mutableMapOf<Long, FocusRequester>()
    var focusedId by mutableStateOf<Long?>(null)
        private set
    private var lastFocusedId by mutableStateOf<Long?>(null)
    internal var pendingFocus by mutableStateOf<Long?>(null)

    /** Serialized Markdown; untouched notes keep their original text. */
    var body by mutableStateOf(initialBody)
        private set

    private val undoStack = ArrayDeque<MemoEditorSnapshot>()
    private val redoStack = ArrayDeque<MemoEditorSnapshot>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var importingImage by mutableStateOf(false)
    private var typingLine: Long? = null
    private var typingAt = 0L

    /** Line the toolbar acts on: focused, last focused, or the final line. */
    val targetLine: MemoLine
        get() = (focusedId ?: lastFocusedId)?.let { id -> lines.firstOrNull { it.id == id } } ?: lines.last()

    private fun cursorOf(id: Long): Int = ((fields[id]?.selection?.start ?: 1) - 1).coerceAtLeast(0)

    private fun snapshot(): MemoEditorSnapshot {
        val id = targetLine.id
        return MemoEditorSnapshot(lines.toList(), id, cursorOf(id), body)
    }

    private fun syncHistory() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    private fun record() {
        undoStack.addLast(snapshot())
        if (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
        redoStack.clear()
        typingLine = null
        syncHistory()
    }

    private fun publish() {
        body = serializeMemoLines(lines)
    }

    private fun commit(next: List<MemoLine>, focusId: Long?, cursor: Int = 0, selectionEnd: Int = cursor) {
        val ids = next.map { it.id }.toSet()
        (fields.keys - ids).forEach { fields.remove(it) }
        (requesters.keys - ids).forEach { requesters.remove(it) }
        next.forEach { line ->
            val field = fields[line.id]
            if (field == null || field.text.drop(1) != line.content) fields[line.id] = lineField(line.content)
        }
        val focusLine = focusId?.let { id -> next.firstOrNull { it.id == id } }
        if (focusLine != null) {
            val length = focusLine.content.length
            fields[focusLine.id] = lineField(focusLine.content, cursor.coerceIn(0, length), selectionEnd.coerceIn(0, length))
            if (focusedId != focusLine.id) pendingFocus = focusLine.id
        }
        lines.clear()
        lines.addAll(next)
        publish()
    }

    private fun restore(target: MemoEditorSnapshot) {
        typingLine = null
        commit(target.lines, target.focusId, target.cursor)
        body = target.body
        syncHistory()
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snapshot())
        restore(previous)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snapshot())
        restore(next)
    }

    /** Insert a standalone block at the cursor without dropping the following text. */
    fun insertBlock(markdown: String) {
        val line = targetLine
        val index = lines.indexOfFirst { it.id == line.id }
        val cursor = cursorOf(line.id).coerceIn(0, line.content.length)
        val before = line.content.take(cursor)
        val after = line.content.drop(cursor)
        record()
        val tail = MemoLine(newId(), content = after)
        val insert = buildList {
            if (before.isNotEmpty()) add(line.copy(content = before))
            addAll(parseMemoLines(markdown, ::newId))
            add(tail)
        }
        commit(lines.take(index) + insert + lines.drop(index + 1), tail.id, 0)
    }

    fun removeBlock(id: Long) {
        val index = lines.indexOfFirst { it.id == id }
        if (index < 0) return
        record()
        val next = lines.filter { it.id != id }.ifEmpty { listOf(MemoLine(newId())) }
        commit(next, null)
    }

    fun selectedText(): String {
        val line = targetLine
        val selection = fields[line.id]?.selection ?: TextRange(0)
        val start = (selection.min - 1).coerceIn(0, line.content.length)
        val end = (selection.max - 1).coerceIn(start, line.content.length)
        return line.content.substring(start, end)
    }

    fun insertLink(label: String, url: String) {
        val line = targetLine
        val index = lines.indexOfFirst { it.id == line.id }
        val selection = fields[line.id]?.selection ?: TextRange(line.content.length + 1)
        val start = (selection.min - 1).coerceIn(0, line.content.length)
        val end = (selection.max - 1).coerceIn(start, line.content.length)
        val safeLabel = label.ifBlank { url }.replace(Regex("[\\[\\]\\r\\n]"), " ")
        val link = "[$safeLabel]($url)"
        record()
        val next = lines.toMutableList()
        next[index] = line.copy(content = line.content.take(start) + link + line.content.drop(end))
        commit(next, line.id, start + link.length)
    }

    fun clearFormatting() {
        val line = targetLine
        if (line.kind == MemoLineKind.Image || line.kind == MemoLineKind.Divider) return
        val hidden = BooleanArray(line.content.length)
        parseMemoInline(line.content).filter { it.kind != MemoInlineKind.Link && it.kind != MemoInlineKind.Url }
            .flatMap { it.markers }.forEach { range -> range.forEach { if (it in hidden.indices) hidden[it] = true } }
        val content = line.content.filterIndexed { index, _ -> !hidden[index] }
        record()
        val next = lines.toMutableList()
        next[lines.indexOfFirst { it.id == line.id }] = line.copy(kind = MemoLineKind.Plain, content = content, indent = 0, checked = false)
        commit(next, line.id, content.length)
    }

    internal fun onFieldChange(id: Long, value: TextFieldValue) {
        val index = lines.indexOfFirst { it.id == id }
        if (index < 0) return
        val line = lines[index]
        val raw = value.text
        val content = raw.replace(LINE_START.toString(), "")
        if (!raw.startsWith(LINE_START) && content == line.content) {
            // Removing only the invisible prefix denotes line-start Backspace.
            val result = memoBackspaceAtStart(lines, index)
            if (result != null) {
                record()
                commit(result.lines, result.focusId, result.cursor)
            } else {
                fields[id] = lineField(line.content, 0)
            }
            return
        }
        val shift = if (raw.startsWith(LINE_START)) 1 else 0
        val cursor = (value.selection.start - shift).coerceIn(0, content.length)
        if ('\n' in content) {
            record()
            val result = memoInsertLineBreaks(lines, index, content, cursor, ::newId)
            commit(result.lines, result.focusId, result.cursor)
            return
        }
        val updated = line.copy(content = content)
        val shortcut = memoApplyShortcut(updated)
        if (shortcut != null) {
            val (converted, removed) = shortcut
            record()
            commit(lines.toMutableList().also { it[index] = converted }, id, (cursor - removed).coerceAtLeast(0))
            return
        }
        if (content != line.content) {
            // Group a typing burst into one undo step; never snapshot mid IME composition.
            val now = clock()
            val settled = fields[id]?.composition == null
            if (settled && (typingLine != id || now - typingAt > TYPING_PAUSE_MS)) record()
            typingLine = id
            typingAt = now
        }
        // Preserve IME composition and prevent the cursor moving before the sentinel.
        fields[id] = if (shift == 1) {
            val start = value.selection.start.coerceAtLeast(1)
            val end = value.selection.end.coerceAtLeast(1)
            if (start == value.selection.start && end == value.selection.end) value else value.copy(selection = TextRange(start, end))
        } else {
            lineField(content, cursor)
        }
        if (content != line.content) {
            lines[index] = updated
            publish()
        }
    }

    internal fun onFocusChanged(id: Long, focused: Boolean) {
        if (focused) {
            focusedId = id
            lastFocusedId = id
        } else if (focusedId == id) {
            focusedId = null
        }
    }

    internal fun toggleChecked(id: Long) {
        val at = lines.indexOfFirst { it.id == id }
        if (at < 0) return
        record()
        lines[at] = lines[at].copy(checked = !lines[at].checked)
        publish()
    }

    internal fun confirmChecked(target: MemoLine) {
        val current = lines.firstOrNull { it.id == target.id } ?: return
        if (current != target || current.kind != MemoLineKind.Check || current.checked) return
        toggleChecked(current.id)
    }

    fun applyKind(kind: MemoLineKind) {
        val id = targetLine.id
        val index = lines.indexOfFirst { it.id == id }
        val cursor = cursorOf(id)
        record()
        commit(lines.toMutableList().also { it[index] = memoToolbarKind(lines, index, kind) }, id, cursor)
    }

    fun wrap(marker: String) {
        val line = targetLine
        val index = lines.indexOfFirst { it.id == line.id }
        val field = fields[line.id] ?: lineField(line.content)
        val wrapped = wrapSelection(
            MemoTextState(
                text = line.content,
                selectionStart = (field.selection.start - 1).coerceAtLeast(0),
                selectionEnd = (field.selection.end - 1).coerceAtLeast(0),
            ),
            marker,
        )
        record()
        commit(
            lines.toMutableList().also { it[index] = line.copy(content = wrapped.text) },
            line.id,
            wrapped.selectionStart,
            wrapped.selectionEnd,
        )
    }

    /** Focus [id], or the first line, with the caret at its end. */
    fun focusLine(id: Long? = null) {
        val line = id?.let { target -> lines.firstOrNull { it.id == target } } ?: lines.first()
        fields[line.id] = lineField(line.content)
        pendingFocus = line.id
    }

    internal fun focusEnd() {
        val last = lines.last()
        fields[last.id] = lineField(last.content)
        pendingFocus = last.id
    }
}

/** Renders [state] lines; only the caret line shows raw inline Markdown. */
@Composable
internal fun MemoBlockEditor(
    state: MemoBlockEditorState,
    modifier: Modifier = Modifier,
    minimumHeight: Dp = 220.dp,
    enabled: Boolean = true,
    lineTagPrefix: String? = null,
) {
    var pendingCompletion by remember(state) { mutableStateOf<MemoLine?>(null) }
    pendingCompletion?.let { target ->
        MemoCompletionDialog(target.content, onConfirm = {
            pendingCompletion = null
            if (enabled) state.confirmChecked(target)
        }, onDismiss = { pendingCompletion = null })
    }
    LaunchedEffect(state.pendingFocus) {
        val id = state.pendingFocus ?: return@LaunchedEffect
        runCatching { state.requesters[id]?.requestFocus() }
        state.pendingFocus = null
    }
    val lines = state.lines
    val numbers = memoLineNumbers(lines)
    val contentColor = MaterialTheme.colorScheme.onSurface
    val palette = MemoInlinePalette(
        marker = contentColor.copy(alpha = 0.35f),
        codeBackground = MaterialTheme.colorScheme.surfaceVariant,
        link = MaterialTheme.colorScheme.primary,
    )
    val anyFocused = state.focusedId != null

    Column(
        modifier = modifier
            .heightIn(min = minimumHeight)
            .clip(RoundedCornerShape(16.dp))
            .border(
                if (anyFocused) 2.dp else 1.dp,
                if (anyFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(16.dp),
            )
            .pointerInput(state, enabled) { detectTapGestures { if (enabled) state.focusEnd() } }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        lines.forEachIndexed { index, line ->
            key(line.id) {
                if (line.kind == MemoLineKind.Image) {
                    com.x500x.cursimple.core.kernel.model.MemoImageRef.parse(line.content)?.let { ref ->
                        MemoImageThumbnail(ref, Modifier.padding(vertical = 4.dp),
                            onRemove = if (enabled) ({ state.removeBlock(line.id) }) else null)
                    }
                } else if (line.kind == MemoLineKind.Divider) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.HorizontalDivider(Modifier.weight(1f).padding(vertical = 12.dp))
                        if (enabled) androidx.compose.material3.IconButton(onClick = { state.removeBlock(line.id) }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.memo_tool_divider_remove))
                        }
                    }
                } else {
                MemoEditorLine(
                    line = line,
                    number = numbers[index],
                    value = state.fields[line.id] ?: lineField(line.content),
                    focused = state.focusedId == line.id,
                    placeholder = if (lines.size == 1 && line.content.isEmpty() && line.kind == MemoLineKind.Plain) {
                        stringResource(R.string.memo_editor_body_hint)
                    } else {
                        null
                    },
                    requester = remember(line.id) { state.requesters.getOrPut(line.id) { FocusRequester() } },
                    contentColor = contentColor,
                    palette = palette,
                    onValueChange = { state.onFieldChange(line.id, it) },
                    onToggleCheck = {
                        if (line.checked) state.toggleChecked(line.id) else pendingCompletion = line
                    },
                    onFocused = { state.onFocusChanged(line.id, it) },
                    enabled = enabled,
                    testTag = lineTagPrefix?.let { if (index == 0) it else "$it-$index" },
                )
                }
            }
        }
    }
}

@Composable
private fun MemoEditorLine(
    line: MemoLine,
    number: Int,
    value: TextFieldValue,
    focused: Boolean,
    placeholder: String?,
    requester: FocusRequester,
    contentColor: Color,
    palette: MemoInlinePalette,
    onValueChange: (TextFieldValue) -> Unit,
    onToggleCheck: () -> Unit,
    onFocused: (Boolean) -> Unit,
    enabled: Boolean,
    testTag: String?,
) {
    val body = MaterialTheme.typography.bodyLarge
    val textStyle: TextStyle = when (line.kind) {
        MemoLineKind.Heading -> when (line.headingLevel) {
            1 -> MaterialTheme.typography.titleLarge
            2 -> MaterialTheme.typography.titleMedium
            else -> MaterialTheme.typography.titleSmall
        }.copy(fontWeight = FontWeight.Bold, color = contentColor)
        MemoLineKind.Check -> body.copy(
            color = if (line.checked) contentColor.copy(alpha = 0.5f) else contentColor,
            textDecoration = if (line.checked) TextDecoration.LineThrough else null,
        )
        MemoLineKind.Quote -> body.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> body.copy(color = contentColor)
    }
    val prefix = if (line.kind == MemoLineKind.Heading) "#".repeat(line.headingLevel.coerceIn(1, 3)) + " " else ""
    val transformation = remember(focused, prefix, palette) { MemoLineTransformation(focused, prefix, palette) }
    val fieldPadding = if (line.kind == MemoLineKind.Check) 12.dp else 4.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(start = (line.indent / 2 * 16).dp),
        verticalAlignment = Alignment.Top,
    ) {
        when (line.kind) {
            MemoLineKind.Check -> {
                val label = stringResource(R.string.memo_tool_check)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .then(if (testTag == null) Modifier else Modifier.testTag("$testTag-check"))
                        .clip(CircleShape)
                        .toggleable(value = line.checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggleCheck() })
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    MemoCheckBox(checked = line.checked, contentColor = contentColor)
                }
            }
            MemoLineKind.Bullet, MemoLineKind.Numbered -> Text(
                text = if (line.kind == MemoLineKind.Bullet) (if (line.indent > 0) "◦" else "•") else "$number.",
                style = body,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .widthIn(min = if (line.kind == MemoLineKind.Bullet) 20.dp else 28.dp)
                    .padding(top = 4.dp, end = 4.dp),
            )
            MemoLineKind.Quote -> {
                Box(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                )
                Spacer(Modifier.width(10.dp))
            }
            MemoLineKind.Heading, MemoLineKind.Plain, MemoLineKind.Image, MemoLineKind.Divider -> Unit
        }
        BasicTextField(
            enabled = enabled,
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = transformation,
            modifier = Modifier
                .weight(1f)
                .then(if (testTag == null) Modifier else Modifier.testTag(testTag))
                .padding(vertical = fieldPadding)
                .focusRequester(requester)
                .onFocusChanged { onFocused(it.isFocused) },
            decorationBox = { inner ->
                Box {
                    if (placeholder != null) {
                        Text(text = placeholder, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    inner()
                }
            },
        )
    }
}
