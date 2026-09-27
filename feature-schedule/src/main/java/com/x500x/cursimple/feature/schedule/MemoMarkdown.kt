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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.parseChecklistLine

/**
 * 笔记正文的一块：一行对应一块，记下它在正文里是第几行，勾复选框时才改得回去。
 *
 * 只认几种最常用的写法，够记课堂笔记、待办就行，写不对也只是按普通文字显示：
 * `# 标题`、`- 列表`、`1. 编号`、`> 引用`、`- [ ] 复选框`，行内 `**粗体**`、`~~删除线~~`、`` `代码` ``。
 */
internal sealed interface MemoBlock {
    val line: Int

    data class Heading(override val line: Int, val level: Int, val text: String) : MemoBlock
    data class Check(override val line: Int, val indent: Int, val checked: Boolean, val text: String) : MemoBlock
    data class Bullet(override val line: Int, val indent: Int, val text: String) : MemoBlock
    data class Numbered(override val line: Int, val indent: Int, val number: String, val text: String) : MemoBlock
    data class Quote(override val line: Int, val text: String) : MemoBlock
    data class Paragraph(override val line: Int, val text: String) : MemoBlock
    data class Blank(override val line: Int) : MemoBlock
}

private val HEADING = Regex("""^(#{1,3})\s+(.*)$""")
private val BULLET = Regex("""^(\s*)[-*•]\s+(.*)$""")
private val NUMBERED = Regex("""^(\s*)(\d{1,3})[.)]\s+(.*)$""")
private val QUOTE = Regex("""^>\s?(.*)$""")

internal fun parseMemoBlocks(body: String): List<MemoBlock> =
    body.split('\n').mapIndexed { index, raw ->
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
        // 开头结尾的空行、连着的空行都没有意义，只留一个
        .let { blocks ->
            val out = mutableListOf<MemoBlock>()
            blocks.forEach { block ->
                if (block is MemoBlock.Blank && (out.isEmpty() || out.last() is MemoBlock.Blank)) return@forEach
                out += block
            }
            if (out.lastOrNull() is MemoBlock.Blank) out.removeAt(out.lastIndex)
            out
        }

private val INLINE = Regex("""\*\*(.+?)\*\*|~~(.+?)~~|`([^`]+)`""")

/** 行内的粗体、删除线、代码。 */
internal fun memoInline(text: String, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    INLINE.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        val (bold, strike, code) = match.destructured
        when {
            bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            strike.isNotEmpty() -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(strike) }
            else -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                append(" $code ")
            }
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}

/**
 * 渲染笔记正文。
 *
 * [maxBlocks] 给卡片用：只露前几块，剩下的写一句「还有 N 行」，点进去再看全文。
 * [onToggle] 为空时复选框只看不能点（比如已经整条完成了）。
 */
@Composable
internal fun MemoBody(
    body: String,
    onToggle: ((line: Int) -> Unit)?,
    modifier: Modifier = Modifier,
    maxBlocks: Int = Int.MAX_VALUE,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val blocks = remember(body) { parseMemoBlocks(body) }
    if (blocks.isEmpty()) return
    val shown = blocks.take(maxBlocks)
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val muted = contentColor.copy(alpha = 0.62f)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.forEach { block ->
            when (block) {
                is MemoBlock.Heading -> Text(
                    text = memoInline(block.text, codeBackground),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleMedium
                        2 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.labelLarge
                    },
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    modifier = Modifier.padding(top = if (block.line == 0) 0.dp else 4.dp),
                )
                is MemoBlock.Check -> MemoCheckRow(
                    checked = block.checked,
                    indent = block.indent,
                    text = memoInline(block.text, codeBackground),
                    contentColor = contentColor,
                    onClick = onToggle?.let { toggle -> { toggle(block.line) } },
                )
                is MemoBlock.Bullet -> MemoListRow(
                    marker = if (block.indent > 0) "◦" else "•",
                    indent = block.indent,
                    text = memoInline(block.text, codeBackground),
                    contentColor = contentColor,
                )
                is MemoBlock.Numbered -> MemoListRow(
                    marker = "${block.number}.",
                    indent = block.indent,
                    text = memoInline(block.text, codeBackground),
                    contentColor = contentColor,
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
                    Text(
                        text = memoInline(block.text, codeBackground),
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                    )
                }
                is MemoBlock.Paragraph -> Text(
                    text = memoInline(block.text, codeBackground),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
                is MemoBlock.Blank -> Spacer(Modifier.height(2.dp))
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

@Composable
private fun MemoCheckRow(
    checked: Boolean,
    indent: Int,
    text: AnnotatedString,
    contentColor: Color,
    onClick: (() -> Unit)?,
) {
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
        // 勾完的划掉变淡，一眼看出还剩哪些
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                textDecoration = if (checked) TextDecoration.LineThrough else null,
            ),
            color = contentColor,
            modifier = Modifier.alpha(if (checked) 0.5f else 1f),
        )
    }
}

/** 笔记里的复选框：卡片上和编辑器里用同一个样子 */
@Composable
internal fun MemoCheckBox(checked: Boolean, contentColor: Color, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (checked) primary else Color.Transparent)
            .border(1.5.dp, if (checked) primary else contentColor.copy(alpha = 0.45f), RoundedCornerShape(5.dp)),
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
private fun MemoListRow(marker: String, indent: Int, text: AnnotatedString, contentColor: Color) {
    Row(modifier = Modifier.padding(start = (indent * 16).dp), verticalAlignment = Alignment.Top) {
        Text(
            text = marker,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(18.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = contentColor)
    }
}
