package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.parseChecklistLine
import com.x500x.cursimple.core.kernel.model.MemoImageRef

/** Keep source line indices for checkbox edits; unsupported Markdown renders as plain text. */
internal sealed interface MemoBlock {
    val line: Int

    data class Heading(override val line: Int, val level: Int, val text: String) : MemoBlock
    data class Check(override val line: Int, val indent: Int, val checked: Boolean, val text: String) : MemoBlock
    data class Bullet(override val line: Int, val indent: Int, val text: String) : MemoBlock
    data class Numbered(override val line: Int, val indent: Int, val number: String, val text: String) : MemoBlock
    data class Quote(override val line: Int, val text: String) : MemoBlock
    data class Paragraph(override val line: Int, val text: String) : MemoBlock
    data class Blank(override val line: Int) : MemoBlock
    data class Image(override val line: Int, val ref: MemoImageRef) : MemoBlock
    data class Divider(override val line: Int) : MemoBlock
}

private val HEADING = Regex("""^(#{1,3})\s+(.*)$""")
private val BULLET = Regex("""^(\s*)[-*•]\s+(.*)$""")
private val NUMBERED = Regex("""^(\s*)(\d{1,3})[.)]\s+(.*)$""")
private val QUOTE = Regex("""^>\s?(.*)$""")

internal fun parseMemoBlocks(body: String): List<MemoBlock> =
    body.split('\n').mapIndexed { index, raw ->
        MemoImageRef.parse(raw)?.let { return@mapIndexed MemoBlock.Image(index, it) }
        if (raw.trim() in setOf("---", "***", "___")) return@mapIndexed MemoBlock.Divider(index)
        val indent = (raw.length - raw.trimStart().length) / 2
        parseChecklistLine(raw)?.let { (checked, text) -> return@mapIndexed MemoBlock.Check(index, indent, checked, text) }
        HEADING.matchEntire(raw)?.let { return@mapIndexed MemoBlock.Heading(index, it.groupValues[1].length, it.groupValues[2]) }
        BULLET.matchEntire(raw)?.let { return@mapIndexed MemoBlock.Bullet(index, indent, it.groupValues[2]) }
        NUMBERED.matchEntire(raw)?.let {
            return@mapIndexed MemoBlock.Numbered(index, indent, it.groupValues[2], it.groupValues[3])
        }
        QUOTE.matchEntire(raw)?.let { return@mapIndexed MemoBlock.Quote(index, it.groupValues[1]) }
        if (raw.isBlank()) MemoBlock.Blank(index) else MemoBlock.Paragraph(index, raw)
    }
        // Collapse redundant blank blocks and trim outer blank lines.
        .let { blocks ->
            val out = mutableListOf<MemoBlock>()
            blocks.forEach { block ->
                if (block is MemoBlock.Blank && (out.isEmpty() || out.last() is MemoBlock.Blank)) return@forEach
                out += block
            }
            if (out.lastOrNull() is MemoBlock.Blank) out.removeAt(out.lastIndex)
            out
        }

/** Rendered inline Markdown with syntax hidden; [linkify] makes safe links clickable. */
internal fun memoInline(text: String, palette: MemoInlinePalette, linkify: Boolean = false): AnnotatedString =
    renderMemoInline(text, palette, hideMarkers = true, linkify = linkify).text

/** Card previews stay compact; Document is the full-note reading view with larger type. */
internal enum class MemoBodyStyle { Card, Document }

/** Cards limit [maxBlocks]; null [onToggle] makes checkboxes read-only. */
@Composable
internal fun MemoBody(
    body: String,
    onToggle: ((line: Int) -> Unit)?,
    modifier: Modifier = Modifier,
    maxBlocks: Int = Int.MAX_VALUE,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    style: MemoBodyStyle = MemoBodyStyle.Card,
) {
    val blocks = remember(body) { parseMemoBlocks(body) }
    if (blocks.isEmpty()) return
    val shown = blocks.take(maxBlocks)
    val document = style == MemoBodyStyle.Document
    val palette = MemoInlinePalette(
        marker = contentColor.copy(alpha = 0.35f),
        codeBackground = MaterialTheme.colorScheme.surfaceVariant,
        link = MaterialTheme.colorScheme.primary,
    )
    val typography = MaterialTheme.typography
    val textStyle = if (document) typography.bodyLarge else typography.bodyMedium
    val muted = contentColor.copy(alpha = 0.62f)
    fun inline(text: String) = memoInline(text, palette, linkify = document)
    val gallery = remember(blocks) { blocks.filterIsInstance<MemoBlock.Image>().map { it.ref }.distinctBy { it.id } }
    androidx.compose.runtime.CompositionLocalProvider(LocalMemoImageGallery provides gallery) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(if (document) 6.dp else 4.dp)) {
        shown.forEach { block ->
            when (block) {
                is MemoBlock.Heading -> Text(
                    text = inline(block.text),
                    style = when (block.level) {
                        1 -> if (document) typography.titleLarge else typography.titleMedium
                        2 -> if (document) typography.titleMedium else typography.titleSmall
                        else -> if (document) typography.titleSmall else typography.labelLarge
                    },
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    modifier = Modifier.padding(top = if (block.line == 0) 0.dp else 4.dp),
                )
                is MemoBlock.Check -> MemoCheckRow(
                    checked = block.checked,
                    indent = block.indent,
                    text = inline(block.text),
                    textStyle = textStyle,
                    contentColor = contentColor,
                    boxOnly = document,
                    onClick = onToggle?.let { toggle -> { toggle(block.line) } },
                )
                is MemoBlock.Bullet -> MemoListRow(
                    marker = if (block.indent > 0) "◦" else "•",
                    indent = block.indent,
                    text = inline(block.text),
                    textStyle = textStyle,
                    contentColor = contentColor,
                    markerWidth = if (document) 20.dp else 14.dp,
                )
                is MemoBlock.Numbered -> MemoListRow(
                    marker = "${block.number}.",
                    indent = block.indent,
                    text = inline(block.text),
                    textStyle = textStyle,
                    contentColor = contentColor,
                    markerWidth = if (document) 20.dp else 14.dp,
                )
                is MemoBlock.Quote -> Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = inline(block.text), style = textStyle, color = muted)
                }
                is MemoBlock.Paragraph -> Text(text = inline(block.text), style = textStyle, color = contentColor)
                is MemoBlock.Blank -> Spacer(Modifier.height(if (document) 6.dp else 2.dp))
                is MemoBlock.Image -> MemoImageThumbnail(block.ref, Modifier.padding(vertical = 4.dp))
                is MemoBlock.Divider -> androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
        }
        if (blocks.size > shown.size) {
            Text(
                text = pluralStringResource(R.plurals.memo_more_lines, blocks.size - shown.size, blocks.size - shown.size),
                style = MaterialTheme.typography.labelSmall,
                color = muted,
            )
        }
    }
    }
}

/** [boxOnly] limits toggling to a 48dp checkbox target so reading taps never change state. */
@Composable
private fun MemoCheckRow(
    checked: Boolean,
    indent: Int,
    text: AnnotatedString,
    textStyle: TextStyle,
    contentColor: Color,
    boxOnly: Boolean,
    onClick: (() -> Unit)?,
) {
    val decorated = textStyle.copy(textDecoration = if (checked) TextDecoration.LineThrough else null)
    if (boxOnly) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (indent * 16).dp),
            verticalAlignment = Alignment.Top,
        ) {
            val label = text.text
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .then(
                        if (onClick != null) {
                            Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onClick() })
                        } else {
                            Modifier
                        },
                    )
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                MemoCheckBox(
                    checked = checked,
                    contentColor = contentColor,
                    modifier = Modifier.alpha(if (onClick != null) 1f else 0.6f),
                )
            }
            Text(
                text = text,
                style = decorated,
                color = contentColor,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .alpha(if (checked) 0.5f else 1f),
            )
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indent * 16).dp)
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        MemoCheckBox(checked = checked, contentColor = contentColor, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = decorated,
            color = contentColor,
            modifier = Modifier.alpha(if (checked) 0.5f else 1f),
        )
    }
}

@Composable
internal fun MemoCheckBox(checked: Boolean, contentColor: Color, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (checked) primary else Color.Transparent)
            .border(1.5.dp, if (checked) primary else MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun MemoListRow(
    marker: String,
    indent: Int,
    text: AnnotatedString,
    textStyle: TextStyle,
    contentColor: Color,
    markerWidth: Dp,
) {
    Row(modifier = Modifier.padding(start = (indent * 16).dp), verticalAlignment = Alignment.Top) {
        Text(
            text = marker,
            style = textStyle,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = markerWidth).padding(end = 4.dp),
        )
        Text(text = text, style = textStyle, color = contentColor)
    }
}
