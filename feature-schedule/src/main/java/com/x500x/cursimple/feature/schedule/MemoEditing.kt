package com.x500x.cursimple.feature.schedule

/** Pure block and cursor transformations serialize back to the existing Markdown format. */
internal data class MemoTextState(val text: String, val selectionStart: Int, val selectionEnd: Int = selectionStart)

internal fun wrapSelection(state: MemoTextState, marker: String): MemoTextState {
    val start = minOf(state.selectionStart, state.selectionEnd).coerceIn(0, state.text.length)
    val end = maxOf(state.selectionStart, state.selectionEnd).coerceIn(0, state.text.length)
    val selected = state.text.substring(start, end)
    val newText = state.text.substring(0, start) + marker + selected + marker + state.text.substring(end)
    return if (start == end) {
        MemoTextState(newText, start + marker.length)
    } else {
        MemoTextState(newText, start + marker.length, end + marker.length)
    }
}

internal enum class MemoLineKind { Plain, Check, Bullet, Numbered, Heading, Quote }

internal data class MemoLine(
    val id: Long,
    val kind: MemoLineKind = MemoLineKind.Plain,
    val content: String = "",
    val checked: Boolean = false,
    val indent: Int = 0,
    val headingLevel: Int = 1,
)

internal data class MemoEditResult(val lines: List<MemoLine>, val focusId: Long, val cursor: Int)

private val BULLET_TO_CHECK = Regex("""^\[([ xX])] """)

private val MEMO_LINE_PREFIX = Regex("""^( *)(?:- \[([ xX])] |([-*•]) |(\d{1,3})[.)] |(#{1,3}) |(>) ?)""")

internal fun parseMemoLine(raw: String, id: Long): MemoLine {
    val match = MEMO_LINE_PREFIX.find(raw) ?: return MemoLine(id, content = raw)
    val indent = match.groupValues[1].length
    val content = raw.substring(match.value.length)
    val g = match.groupValues
    return when {
        g[2].isNotEmpty() -> MemoLine(id, MemoLineKind.Check, content, checked = g[2] != " ", indent = indent)
        g[3].isNotEmpty() -> MemoLine(id, MemoLineKind.Bullet, content, indent = indent)
        g[4].isNotEmpty() -> MemoLine(id, MemoLineKind.Numbered, content, indent = indent)
        g[5].isNotEmpty() -> MemoLine(id, MemoLineKind.Heading, content, headingLevel = g[5].length)
        else -> MemoLine(id, MemoLineKind.Quote, content)
    }
}

internal fun parseMemoLines(body: String, newId: () -> Long): List<MemoLine> =
    body.split('\n').map { parseMemoLine(it, newId()) }

/** Number contiguous ordered items from one; nonordered blocks receive zero. */
internal fun memoLineNumbers(lines: List<MemoLine>): IntArray {
    val numbers = IntArray(lines.size)
    lines.forEachIndexed { index, line ->
        if (line.kind != MemoLineKind.Numbered) return@forEachIndexed
        val previous = lines.getOrNull(index - 1)
        numbers[index] = if (previous?.kind == MemoLineKind.Numbered && previous.indent == line.indent) {
            numbers[index - 1] + 1
        } else {
            1
        }
    }
    return numbers
}

internal fun serializeMemoLines(lines: List<MemoLine>): String {
    val numbers = memoLineNumbers(lines)
    return lines.mapIndexed { index, line ->
        val pad = " ".repeat(line.indent)
        when (line.kind) {
            MemoLineKind.Plain -> pad + line.content
            MemoLineKind.Check -> pad + (if (line.checked) "- [x] " else "- [ ] ") + line.content
            MemoLineKind.Bullet -> "$pad- ${line.content}"
            MemoLineKind.Numbered -> "$pad${numbers[index]}. ${line.content}"
            MemoLineKind.Heading -> "#".repeat(line.headingLevel.coerceIn(1, 3)) + " " + line.content
            MemoLineKind.Quote -> "> ${line.content}"
        }
    }.joinToString("\n")
}

/**
 * Enter continues the current format, ends empty lists or leaves headings. Multiline paste
 * recognizes each line's markers; [content] and [cursor] describe edited input.
 */
internal fun memoInsertLineBreaks(
    lines: List<MemoLine>,
    index: Int,
    content: String,
    cursor: Int,
    newId: () -> Long,
): MemoEditResult {
    val line = lines[index]
    val parts = content.split('\n')
    if (parts.size == 2 && line.kind != MemoLineKind.Plain && parts[0].isBlank() && parts[1].isEmpty()) {
        val plain = line.copy(kind = MemoLineKind.Plain, content = "", checked = false, indent = 0)
        return MemoEditResult(lines.replaced(index, listOf(plain)), plain.id, 0)
    }
    val first = line.copy(content = parts[0])
    val rest = if (parts.size == 2) {
        listOf(continuedLine(line, newId()).copy(content = parts[1]))
    } else {
        parts.drop(1).map { parseMemoLine(it, newId()) }
    }
    val lastBreak = content.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0))
    val cursorInLast = (cursor - lastBreak - 1).coerceIn(0, rest.last().content.length)
    return MemoEditResult(lines.replaced(index, listOf(first) + rest), rest.last().id, cursorInLast)
}

private fun continuedLine(line: MemoLine, id: Long): MemoLine = when (line.kind) {
    MemoLineKind.Heading -> MemoLine(id)
    else -> MemoLine(id, line.kind, indent = line.indent)
}

/**
 * Line-start Backspace removes formatting, then joins plain text upward; first plain line
 * returns null.
 */
internal fun memoBackspaceAtStart(lines: List<MemoLine>, index: Int): MemoEditResult? {
    val line = lines[index]
    if (line.kind != MemoLineKind.Plain || line.indent > 0) {
        val plain = line.copy(kind = MemoLineKind.Plain, checked = false, indent = 0)
        return MemoEditResult(lines.replaced(index, listOf(plain)), line.id, 0)
    }
    if (index == 0) return null
    val previous = lines[index - 1]
    val merged = previous.copy(content = previous.content + line.content)
    val out = lines.toMutableList()
    out[index - 1] = merged
    out.removeAt(index)
    return MemoEditResult(out, previous.id, previous.content.length)
}

internal fun memoToggleKind(line: MemoLine, kind: MemoLineKind): MemoLine =
    if (line.kind == kind) {
        line.copy(kind = MemoLineKind.Plain, checked = false)
    } else {
        line.copy(kind = kind, checked = false, headingLevel = if (kind == MemoLineKind.Heading) 1 else line.headingLevel)
    }

/** Recognize typed Markdown prefixes and report removed characters for cursor adjustment. */
internal fun memoApplyShortcut(line: MemoLine): Pair<MemoLine, Int>? {
    if (line.kind == MemoLineKind.Bullet) {
        val box = BULLET_TO_CHECK.find(line.content) ?: return null
        val check = line.copy(
            kind = MemoLineKind.Check,
            content = line.content.substring(box.value.length),
            checked = box.groupValues[1] != " ",
        )
        return check to box.value.length
    }
    if (line.kind != MemoLineKind.Plain) return null
    val parsed = parseMemoLine(" ".repeat(line.indent) + line.content, line.id)
    if (parsed.kind == MemoLineKind.Plain) return null
    if (parsed.kind == MemoLineKind.Quote && !line.content.startsWith("> ")) return null
    val removed = line.content.length - parsed.content.length
    return parsed to removed
}

private fun List<MemoLine>.replaced(index: Int, with: List<MemoLine>): List<MemoLine> =
    subList(0, index) + with + subList(index + 1, size)
