package com.x500x.cursimple.app.update

/** 发布说明里的一段行内文字。 */
data class ReleaseNoteSpan(
    val text: String,
    val bold: Boolean = false,
    val code: Boolean = false,
    val link: String? = null,
)

/** 发布说明解析后的一个块。 */
sealed interface ReleaseNoteBlock {
    data class Heading(val level: Int, val spans: List<ReleaseNoteSpan>) : ReleaseNoteBlock

    data class Paragraph(val spans: List<ReleaseNoteSpan>) : ReleaseNoteBlock

    data class BulletItem(val indent: Int, val spans: List<ReleaseNoteSpan>) : ReleaseNoteBlock

    data object Divider : ReleaseNoteBlock
}

/**
 * 把 Release 正文的 Markdown 解析成块。
 *
 * 支持标题、无序列表、分隔线，行内支持粗体、行内代码、链接与裸链接。
 * 段落与列表项里的换行按续行合并，两侧都不是中日韩文字时补一个空格。
 */
fun parseReleaseNotes(markdown: String): List<ReleaseNoteBlock> {
    val blocks = mutableListOf<ReleaseNoteBlock>()
    val pending = StringBuilder()
    var pendingKind: PendingKind? = null
    var pendingIndent = 0

    fun flush() {
        val kind = pendingKind ?: return
        val text = pending.toString().trim()
        pending.setLength(0)
        pendingKind = null
        if (text.isEmpty()) return
        blocks += when (kind) {
            PendingKind.Paragraph -> ReleaseNoteBlock.Paragraph(parseInline(text))
            PendingKind.Bullet -> ReleaseNoteBlock.BulletItem(pendingIndent, parseInline(text))
        }
    }

    fun append(text: String) {
        if (pending.isEmpty()) {
            pending.append(text)
            return
        }
        val left = pending.last()
        val right = text.first()
        if (!left.isCjk() && !right.isCjk()) pending.append(' ')
        pending.append(text)
    }

    for (rawLine in markdown.lines()) {
        val line = rawLine.trimEnd()
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> flush()

            HORIZONTAL_RULE.matches(trimmed) -> {
                flush()
                blocks += ReleaseNoteBlock.Divider
            }

            trimmed.startsWith("#") -> {
                flush()
                val level = trimmed.takeWhile { it == '#' }.length
                val text = trimmed.drop(level).trim()
                if (text.isNotEmpty()) {
                    blocks += ReleaseNoteBlock.Heading(level.coerceIn(1, 6), parseInline(text))
                }
            }

            BULLET_PREFIX.containsMatchIn(trimmed) -> {
                flush()
                pendingKind = PendingKind.Bullet
                pendingIndent = (line.length - line.trimStart().length) / 2
                append(BULLET_PREFIX.replaceFirst(trimmed, ""))
            }

            else -> {
                if (pendingKind == null) pendingKind = PendingKind.Paragraph
                append(trimmed)
            }
        }
    }
    flush()
    return blocks
}

/** 解析行内标记，返回顺序排列的片段。 */
internal fun parseInline(text: String): List<ReleaseNoteSpan> {
    val spans = mutableListOf<ReleaseNoteSpan>()
    var index = 0
    val plain = StringBuilder()

    fun flushPlain() {
        if (plain.isEmpty()) return
        spans += ReleaseNoteSpan(plain.toString())
        plain.setLength(0)
    }

    while (index < text.length) {
        val rest = text.substring(index)
        val bold = BOLD.find(rest)?.takeIf { it.range.first == 0 }
        val code = CODE.find(rest)?.takeIf { it.range.first == 0 }
        val link = LINK.find(rest)?.takeIf { it.range.first == 0 }
        val bare = BARE_URL.find(rest)?.takeIf { it.range.first == 0 }
        when {
            bold != null -> {
                flushPlain()
                spans += ReleaseNoteSpan(bold.groupValues[1], bold = true)
                index += bold.value.length
            }
            code != null -> {
                flushPlain()
                spans += ReleaseNoteSpan(code.groupValues[1], code = true)
                index += code.value.length
            }
            link != null -> {
                flushPlain()
                spans += ReleaseNoteSpan(link.groupValues[1], link = link.groupValues[2])
                index += link.value.length
            }
            bare != null -> {
                flushPlain()
                spans += ReleaseNoteSpan(bare.value, link = bare.value)
                index += bare.value.length
            }
            else -> {
                plain.append(text[index])
                index++
            }
        }
    }
    flushPlain()
    return spans
}

private enum class PendingKind { Paragraph, Bullet }

private fun Char.isCjk(): Boolean = this in '⺀'..'鿿' || this in '＀'..'￯'

private val HORIZONTAL_RULE = Regex("^(-{3,}|\\*{3,}|_{3,})$")
private val BULLET_PREFIX = Regex("^([-*+]|\\d+\\.)\\s+")
private val BOLD = Regex("\\*\\*(.+?)\\*\\*")
private val CODE = Regex("`([^`]+)`")
private val LINK = Regex("\\[([^\\]]+)]\\(([^)\\s]+)\\)")
private val BARE_URL = Regex("https?://\\S+")
