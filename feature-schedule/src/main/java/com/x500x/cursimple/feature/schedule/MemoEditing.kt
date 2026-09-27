package com.x500x.cursimple.feature.schedule

/**
 * 笔记编辑器背后的纯逻辑，全是对「一行行的内容 + 光标」的变换，方便单测。
 *
 * 编辑时一行就是一块：复选框、列表、编号、标题、引用各画成自己的样子，
 * 输入框里只放这一行的文字，不放 `- [ ] ` 这类记号；存的时候再拼回 Markdown，
 * 卡片、备份和以前的数据都还是同一种格式。
 */
internal data class MemoTextState(val text: String, val selectionStart: Int, val selectionEnd: Int = selectionStart)

/** 选中的文字两边加上 [marker]（比如 `**`）；没选中就插一对，光标放中间。 */
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

/** 编辑器里的一行。[indent] 是行首空格数，两个空格算缩进一级。 */
internal data class MemoLine(
    val id: Long,
    val kind: MemoLineKind = MemoLineKind.Plain,
    val content: String = "",
    val checked: Boolean = false,
    val indent: Int = 0,
    val headingLevel: Int = 1,
)

/** 一次编辑之后的样子，外加光标该落在哪一行的第几个字 */
internal data class MemoEditResult(val lines: List<MemoLine>, val focusId: Long, val cursor: Int)

private val BULLET_TO_CHECK = Regex("""^\[([ xX])] """)

private val MEMO_LINE_PREFIX = Regex("""^( *)(?:- \[([ xX])] |([-*•]) |(\d{1,3})[.)] |(#{1,3}) |(>) ?)""")

/** 认出一行开头的记号，拆成「什么格式 + 纯文字」 */
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

/** 每一行显示的编号：连着的编号行按 1、2、3 往下数，中间隔了别的行就重新数；不是编号行是 0 */
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
 * 一行里出现了换行：按回车，或者粘贴了好几行。
 *
 * 按回车（只多了一个换行）：新的一行接着用这一行的格式——复选框续一个没勾的，列表续列表，标题后面是普通文字；
 * 在空的列表项上按回车就是不要列表了，这一行变回普通文字，不再多出一行。
 * 粘贴好几行：每行按自己开头的记号认格式，粘进来的 `- [ ] ` 也会变成复选框。
 *
 * [content] 是这一行编辑后的全部文字（带换行），[cursor] 是光标在其中的位置。
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
 * 光标在一行最前面按了删除：带格式的先去掉格式（复选框变回普通文字），
 * 已经是普通文字就接到上一行后面。第一行的普通文字没有可删的，返回 null。
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

/** 工具栏按钮：已经是这个格式就去掉，是别的格式就换成这个 */
internal fun memoToggleKind(line: MemoLine, kind: MemoLineKind): MemoLine =
    if (line.kind == kind) {
        line.copy(kind = MemoLineKind.Plain, checked = false)
    } else {
        line.copy(kind = kind, checked = false, headingLevel = if (kind == MemoLineKind.Heading) 1 else line.headingLevel)
    }

/**
 * 普通文字行里手敲了 `- [ ] `、`- `、`1. `、`# `、`> ` 这样的开头：直接变成对应格式，记号从文字里拿掉。
 * 返回变好的行和拿掉了几个字（光标要往前挪这么多），没敲这种开头就返回 null。
 */
internal fun memoApplyShortcut(line: MemoLine): Pair<MemoLine, Int>? {
    // 敲「- 」时已经先变成列表了，接着再敲「[ ] 」就是想要复选框
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
    // 「>」后面还没打空格就先别变，不然想打「>=」都打不出来
    if (parsed.kind == MemoLineKind.Quote && !line.content.startsWith("> ")) return null
    val removed = line.content.length - parsed.content.length
    return parsed to removed
}

private fun List<MemoLine>.replaced(index: Int, with: List<MemoLine>): List<MemoLine> =
    subList(0, index) + with + subList(index + 1, size)
