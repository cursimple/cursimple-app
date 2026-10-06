package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/** An invisible prefix makes line-start Backspace observable to the soft keyboard. */
private const val LINE_START = '\u200B'

private fun lineField(content: String, cursor: Int = content.length, end: Int = cursor): TextFieldValue =
    TextFieldValue(LINE_START + content, TextRange(cursor + 1, end + 1))

/**
 * Block editor stores Markdown while rendering checkboxes, lists, headings and quotes. Enter
 * continues or ends formatting; Backspace removes formatting before merging lines.
 */
@Composable
internal fun MemoBlockEditor(
    initialBody: String,
    onBodyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val idSource = remember { longArrayOf(0L) }
    val newId: () -> Long = { ++idSource[0] }
    val lines = remember { mutableStateListOf<MemoLine>().apply { addAll(parseMemoLines(initialBody, newId)) } }
    val fields = remember {
        mutableStateMapOf<Long, TextFieldValue>().apply { lines.forEach { put(it.id, lineField(it.content)) } }
    }
    val requesters = remember { mutableMapOf<Long, FocusRequester>() }
    var focusedId by remember { mutableStateOf<Long?>(null) }
    var pendingFocus by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(pendingFocus) {
        val id = pendingFocus ?: return@LaunchedEffect
        runCatching { requesters[id]?.requestFocus() }
        pendingFocus = null
    }

    fun commit(next: List<MemoLine>, focusId: Long?, cursor: Int = 0, selectionEnd: Int = cursor) {
        val ids = next.map { it.id }.toSet()
        (fields.keys - ids).forEach { fields.remove(it) }
        (requesters.keys - ids).forEach { requesters.remove(it) }
        next.forEach { line ->
            val field = fields[line.id]
            if (field == null || field.text.drop(1) != line.content) fields[line.id] = lineField(line.content)
        }
        if (focusId != null) {
            val content = next.first { it.id == focusId }.content
            fields[focusId] = lineField(content, cursor.coerceIn(0, content.length), selectionEnd.coerceIn(0, content.length))
            if (focusedId != focusId) pendingFocus = focusId
        }
        lines.clear()
        lines.addAll(next)
        onBodyChange(serializeMemoLines(next))
    }

    fun onFieldChange(id: Long, value: TextFieldValue) {
        val index = lines.indexOfFirst { it.id == id }
        if (index < 0) return
        val line = lines[index]
        val raw = value.text
        val content = raw.replace(LINE_START.toString(), "")
        if (!raw.startsWith(LINE_START) && content == line.content) {
            // Removing only the invisible prefix denotes line-start Backspace.
            val result = memoBackspaceAtStart(lines, index)
            if (result != null) commit(result.lines, result.focusId, result.cursor) else fields[id] = lineField(line.content, 0)
            return
        }
        val shift = if (raw.startsWith(LINE_START)) 1 else 0
        val cursor = (value.selection.start - shift).coerceIn(0, content.length)
        if ('\n' in content) {
            val result = memoInsertLineBreaks(lines, index, content, cursor, newId)
            commit(result.lines, result.focusId, result.cursor)
            return
        }
        val updated = line.copy(content = content)
        val shortcut = memoApplyShortcut(updated)
        if (shortcut != null) {
            val (converted, removed) = shortcut
            commit(lines.toMutableList().also { it[index] = converted }, id, (cursor - removed).coerceAtLeast(0))
            return
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
            onBodyChange(serializeMemoLines(lines))
        }
    }

    fun targetLineId(): Long = focusedId?.takeIf { id -> lines.any { it.id == id } } ?: lines.last().id

    fun cursorOf(id: Long): Int = ((fields[id]?.selection?.start ?: 1) - 1).coerceAtLeast(0)

    fun toggle(kind: MemoLineKind) {
        val id = targetLineId()
        val index = lines.indexOfFirst { it.id == id }
        commit(lines.toMutableList().also { it[index] = memoToggleKind(lines[index], kind) }, id, cursorOf(id))
    }

    fun bold() {
        val id = targetLineId()
        val index = lines.indexOfFirst { it.id == id }
        val field = fields[id] ?: lineField(lines[index].content)
        val state = MemoTextState(
            text = lines[index].content,
            selectionStart = (field.selection.start - 1).coerceAtLeast(0),
            selectionEnd = (field.selection.end - 1).coerceAtLeast(0),
        )
        val wrapped = wrapSelection(state, "**")
        commit(
            lines.toMutableList().also { it[index] = lines[index].copy(content = wrapped.text) },
            id,
            wrapped.selectionStart,
            wrapped.selectionEnd,
        )
    }

    val numbers = memoLineNumbers(lines)
    val contentColor = MaterialTheme.colorScheme.onSurface
    val inlineStyle = remember(contentColor) {
        MemoInlineStyle(markerColor = contentColor.copy(alpha = 0.35f))
    }
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MemoToolButton(Icons.Rounded.CheckBox, R.string.memo_tool_check) { toggle(MemoLineKind.Check) }
            MemoToolButton(Icons.AutoMirrored.Rounded.FormatListBulleted, R.string.memo_tool_bullet) { toggle(MemoLineKind.Bullet) }
            MemoToolButton(Icons.Rounded.FormatListNumbered, R.string.memo_tool_numbered) { toggle(MemoLineKind.Numbered) }
            MemoToolButton(Icons.Rounded.Title, R.string.memo_tool_heading) { toggle(MemoLineKind.Heading) }
            MemoToolButton(Icons.Rounded.FormatBold, R.string.memo_tool_bold, ::bold)
            MemoToolButton(Icons.Rounded.FormatQuote, R.string.memo_tool_quote) { toggle(MemoLineKind.Quote) }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    1.dp,
                    if (focusedId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(14.dp),
                )
                .pointerInput(Unit) {
                    detectTapGestures {
                        val last = lines.last()
                        commit(lines.toList(), last.id, last.content.length)
                        pendingFocus = last.id
                    }
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            lines.forEachIndexed { index, line ->
                key(line.id) {
                    MemoEditorLine(
                        line = line,
                        number = numbers[index],
                        value = fields[line.id] ?: lineField(line.content),
                        placeholder = if (lines.size == 1 && line.content.isEmpty()) {
                            stringResource(R.string.memo_editor_body_hint)
                        } else {
                            null
                        },
                        requester = remember(line.id) { requesters.getOrPut(line.id) { FocusRequester() } },
                        contentColor = contentColor,
                        inlineStyle = inlineStyle,
                        codeBackground = codeBackground,
                        onValueChange = { onFieldChange(line.id, it) },
                        onToggleCheck = {
                            val at = lines.indexOfFirst { it.id == line.id }
                            if (at >= 0) {
                                lines[at] = lines[at].copy(checked = !lines[at].checked)
                                onBodyChange(serializeMemoLines(lines))
                            }
                        },
                        onFocused = { focused ->
                            if (focused) {
                                focusedId = line.id
                            } else if (focusedId == line.id) {
                                focusedId = null
                            }
                        },
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
    placeholder: String?,
    requester: FocusRequester,
    contentColor: Color,
    inlineStyle: MemoInlineStyle,
    codeBackground: Color,
    onValueChange: (TextFieldValue) -> Unit,
    onToggleCheck: () -> Unit,
    onFocused: (Boolean) -> Unit,
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
    val transformation = remember(inlineStyle, codeBackground) { MemoInlineTransformation(inlineStyle, codeBackground) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(start = (line.indent / 2 * 16).dp),
        verticalAlignment = Alignment.Top,
    ) {
        when (line.kind) {
            MemoLineKind.Check -> {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggleCheck),
                    contentAlignment = Alignment.Center,
                ) {
                    MemoCheckBox(checked = line.checked, contentColor = contentColor)
                }
                Spacer(Modifier.width(4.dp))
            }
            MemoLineKind.Bullet, MemoLineKind.Numbered -> Text(
                text = if (line.kind == MemoLineKind.Bullet) (if (line.indent > 0) "◦" else "•") else "$number.",
                style = body,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .width(if (line.kind == MemoLineKind.Bullet) 20.dp else 28.dp)
                    .padding(top = 4.dp),
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
            MemoLineKind.Heading, MemoLineKind.Plain -> Unit
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = transformation,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
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

private data class MemoInlineStyle(val markerColor: Color)

private val INLINE_MARKS = Regex("""\*\*(.+?)\*\*|~~(.+?)~~|`([^`]+)`""")

/** Apply inline styling without changing text so cursor offsets remain exact. */
private class MemoInlineTransformation(
    private val style: MemoInlineStyle,
    private val codeBackground: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val marker = SpanStyle(color = style.markerColor)
        val styled = buildAnnotatedString {
            append(raw)
            INLINE_MARKS.findAll(raw).forEach { match ->
                val first = match.range.first
                val last = match.range.last + 1
                val (bold, strike, _) = match.destructured
                val markLength = if (bold.isNotEmpty() || strike.isNotEmpty()) 2 else 1
                val inner = when {
                    bold.isNotEmpty() -> SpanStyle(fontWeight = FontWeight.Bold)
                    strike.isNotEmpty() -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    else -> SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
                }
                addStyle(marker, first, first + markLength)
                addStyle(inner, first + markLength, last - markLength)
                addStyle(marker, last - markLength, last)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    override fun equals(other: Any?): Boolean =
        other is MemoInlineTransformation && other.style == style && other.codeBackground == codeBackground

    override fun hashCode(): Int = 31 * style.hashCode() + codeBackground.hashCode()
}

@Composable
private fun MemoToolButton(icon: ImageVector, labelRes: Int, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(),
    ) {
        Icon(icon, contentDescription = stringResource(labelRes), modifier = Modifier.size(20.dp))
    }
}
