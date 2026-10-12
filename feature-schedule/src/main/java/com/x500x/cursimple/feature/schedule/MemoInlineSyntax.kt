package com.x500x.cursimple.feature.schedule

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration

internal enum class MemoInlineKind { Bold, Italic, Strike, Code, Link, Url }

/**
 * One inline Markdown span with end-exclusive offsets. [markers] are syntax characters that a
 * rendered view hides; [contentStart] until [contentEnd] keeps the visible text.
 */
internal data class MemoInlineSpan(
    val kind: MemoInlineKind,
    val contentStart: Int,
    val contentEnd: Int,
    val markers: List<IntRange> = emptyList(),
    val url: String? = null,
)

private val INLINE_TOKEN = Regex(
    listOf(
        """`([^`\n]+)`""",
        """\[([^\]\n]+)]\(([^()\s]+)\)""",
        """\*\*(.+?)\*\*""",
        """~~(.+?)~~""",
        """__(?=\S)(.+?)(?<=\S)__""",
        """(?<!\*)\*(?=[^\s*])(.+?)(?<=[^\s*])\*(?!\*)""",
        """(?<![\p{L}\p{N}_])_(?=[^\s_])(.+?)(?<=[^\s_])_(?![\p{L}\p{N}_])""",
        """https?://[^\s<>()\[\]{}"'，。；！？、）】》「」]+""",
    ).joinToString("|"),
)

private const val URL_TRAILING = ".,;:!?"

/** Parse bold, italic, strikethrough, code, links and bare URLs; code spans are not nested. */
internal fun parseMemoInline(text: String): List<MemoInlineSpan> =
    mutableListOf<MemoInlineSpan>().also { collectInline(text, 0, text.length, it) }

private fun collectInline(text: String, from: Int, to: Int, out: MutableList<MemoInlineSpan>) {
    if (to - from < 2) return
    INLINE_TOKEN.findAll(text.substring(from, to)).forEach { match ->
        val start = from + match.range.first
        val end = from + match.range.last + 1
        val groups = match.groups
        fun wrapped(kind: MemoInlineKind, width: Int) {
            out += MemoInlineSpan(kind, start + width, end - width, listOf(start until start + width, end - width until end))
            collectInline(text, start + width, end - width, out)
        }
        when {
            groups[1] != null -> out += MemoInlineSpan(MemoInlineKind.Code, start + 1, end - 1, listOf(start until start + 1, end - 1 until end))
            groups[2] != null -> {
                val labelEnd = from + groups[2]!!.range.last + 1
                out += MemoInlineSpan(MemoInlineKind.Link, start + 1, labelEnd, listOf(start until start + 1, labelEnd until end), groups[3]!!.value)
                collectInline(text, start + 1, labelEnd, out)
            }
            groups[4] != null || groups[6] != null -> wrapped(MemoInlineKind.Bold, 2)
            groups[5] != null -> wrapped(MemoInlineKind.Strike, 2)
            groups[7] != null || groups[8] != null -> wrapped(MemoInlineKind.Italic, 1)
            else -> {
                val url = match.value.trimEnd { it in URL_TRAILING }
                out += MemoInlineSpan(MemoInlineKind.Url, start, start + url.length, url = url)
            }
        }
    }
}

/**
 * Maps text with hidden characters and an optional inserted [prefix]. Every input is clamped so
 * the editor never receives an out-of-range offset.
 */
internal class MemoOffsetMapping(hidden: BooleanArray, private val prefix: Int = 0) : OffsetMapping {
    private val visibleBefore = IntArray(hidden.size + 1)
    private val toOriginal: IntArray

    init {
        val positions = ArrayList<Int>(hidden.size + 1)
        hidden.forEachIndexed { index, isHidden ->
            visibleBefore[index] = positions.size
            if (!isHidden) positions += index
        }
        visibleBefore[hidden.size] = positions.size
        positions += hidden.size
        toOriginal = positions.toIntArray()
    }

    val transformedLength: Int get() = prefix + toOriginal.size - 1

    override fun originalToTransformed(offset: Int): Int =
        prefix + visibleBefore[offset.coerceIn(0, visibleBefore.lastIndex)]

    override fun transformedToOriginal(offset: Int): Int =
        toOriginal[(offset - prefix).coerceIn(0, toOriginal.lastIndex)]
}

internal data class MemoInlinePalette(val marker: Color, val codeBackground: Color, val link: Color)

internal class MemoInlineRender(val text: AnnotatedString, val mapping: MemoOffsetMapping)

private val SAFE_LINK = Regex("""^(https?://|mailto:)\S+$""", RegexOption.IGNORE_CASE)

/**
 * Styles inline Markdown. [hideMarkers] renders it Typora-style; otherwise syntax stays visible
 * and dimmed for editing. [linkify] makes http(s)/mailto links clickable.
 */
internal fun renderMemoInline(
    raw: String,
    palette: MemoInlinePalette,
    hideMarkers: Boolean,
    prefix: String = "",
    linkify: Boolean = false,
): MemoInlineRender {
    val spans = parseMemoInline(raw)
    val hidden = BooleanArray(raw.length)
    if (hideMarkers) spans.forEach { span -> span.markers.forEach { range -> range.forEach { hidden[it] = true } } }
    val mapping = MemoOffsetMapping(hidden, prefix.length)
    val linkStyle = SpanStyle(color = palette.link, textDecoration = TextDecoration.Underline)
    val text = buildAnnotatedString {
        append(prefix)
        if (prefix.isNotEmpty()) addStyle(SpanStyle(color = palette.marker), 0, prefix.length)
        raw.forEachIndexed { index, char -> if (!hidden[index]) append(char) }
        spans.forEach { span ->
            val start = mapping.originalToTransformed(span.contentStart)
            val end = mapping.originalToTransformed(span.contentEnd)
            if (start >= end) return@forEach
            val style = when (span.kind) {
                MemoInlineKind.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
                MemoInlineKind.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
                MemoInlineKind.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                MemoInlineKind.Code -> SpanStyle(fontFamily = FontFamily.Monospace, background = palette.codeBackground)
                MemoInlineKind.Link, MemoInlineKind.Url -> linkStyle
            }
            addStyle(style, start, end)
            val url = span.url
            if (linkify && url != null && SAFE_LINK.matches(url)) {
                addLink(LinkAnnotation.Url(url, TextLinkStyles(linkStyle)), start, end)
            }
            if (!hideMarkers) {
                span.markers.forEach { range ->
                    addStyle(
                        SpanStyle(color = palette.marker),
                        mapping.originalToTransformed(range.first),
                        mapping.originalToTransformed(range.last + 1),
                    )
                }
            }
        }
    }
    return MemoInlineRender(text, mapping)
}

/**
 * Typora-style editor line: unfocused lines hide inline syntax, while the caret line shows raw
 * Markdown plus a non-editable heading [prefix].
 */
internal class MemoLineTransformation(
    private val focused: Boolean,
    private val prefix: String,
    private val palette: MemoInlinePalette,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val rendered = renderMemoInline(text.text, palette, hideMarkers = !focused, prefix = if (focused) prefix else "")
        return TransformedText(rendered.text, rendered.mapping)
    }

    override fun equals(other: Any?): Boolean =
        other is MemoLineTransformation && other.focused == focused && other.prefix == prefix && other.palette == palette

    override fun hashCode(): Int = (focused.hashCode() * 31 + prefix.hashCode()) * 31 + palette.hashCode()
}
