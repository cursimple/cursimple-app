package com.x500x.cursimple.app.update

data class ReleaseNoteSpan(
    val text: String,
    val bold: Boolean = false,
    val code: Boolean = false,
    val link: String? = null,
)

sealed interface ReleaseNoteBlock {
    data class Heading(val level: Int, val spans: List<ReleaseNoteSpan>) : ReleaseNoteBlock

    data class Paragraph(val spans: List<ReleaseNoteSpan>) : ReleaseNoteBlock

    data class BulletItem(val indent: Int, val spans: List<ReleaseNoteSpan>) : ReleaseNoteBlock

    data object Divider : ReleaseNoteBlock

    data class Gallery(val images: List<ReleaseNoteImage>) : ReleaseNoteBlock
}

/** Image [url] and Markdown-derived [caption]. */
data class ReleaseNoteImage(val url: String, val caption: String)

/**
 * Parse headings, lists, dividers and inline formatting. Join CJK continuation lines without
 * extra spaces; group adjacent images into galleries.
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

    var afterImage = false

    for (rawLine in markdown.lines()) {
        val line = rawLine.trimEnd()
        val trimmed = line.trim()
        val image = IMAGE_LINE.matchEntire(trimmed)
        if (trimmed.isNotEmpty() && image == null) afterImage = false
        when {
            trimmed.isEmpty() -> flush()

            image != null -> {
                flush()
                val picture = ReleaseNoteImage(url = image.groupValues[2], caption = image.groupValues[1].trim())
                val last = blocks.lastOrNull()
                if (afterImage && last is ReleaseNoteBlock.Gallery) {
                    blocks[blocks.lastIndex] = last.copy(images = last.images + picture)
                } else {
                    blocks += ReleaseNoteBlock.Gallery(listOf(picture))
                }
                afterImage = true
            }

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

private val IMAGE_LINE = Regex("^!\\[([^\\]]*)]\\((\\S+?)(?:\\s+\"[^\"]*\")?\\)$")
private val HORIZONTAL_RULE = Regex("^(-{3,}|\\*{3,}|_{3,})$")
private val BULLET_PREFIX = Regex("^([-*+]|\\d+\\.)\\s+")
private val BOLD = Regex("\\*\\*(.+?)\\*\\*")
private val CODE = Regex("`([^`]+)`")
private val LINK = Regex("\\[([^\\]]+)]\\(([^)\\s]+)\\)")
private val BARE_URL = Regex("https?://\\S+")
