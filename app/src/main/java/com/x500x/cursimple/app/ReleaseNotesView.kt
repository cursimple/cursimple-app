package com.x500x.cursimple.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.app.update.ReleaseNoteBlock
import com.x500x.cursimple.app.update.ReleaseNoteSpan
import com.x500x.cursimple.app.update.parseReleaseNotes

/** 把发布说明按 Markdown 渲染进一个可滚动的方框里。 */
@Composable
fun ReleaseNotesCard(
    markdown: String,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ReleaseNotesBody(markdown)
        }
    }
}

/** 逐块渲染发布说明。 */
@Composable
fun ReleaseNotesBody(markdown: String) {
    val blocks = remember(markdown) { parseReleaseNotes(markdown) }
    blocks.forEach { block ->
        when (block) {
            is ReleaseNoteBlock.Heading -> Text(
                text = block.spans.toAnnotatedString(),
                style = when (block.level) {
                    1 -> MaterialTheme.typography.titleMedium
                    2 -> MaterialTheme.typography.titleSmall
                    else -> MaterialTheme.typography.labelLarge
                },
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = if (block.level >= 3) 4.dp else 0.dp),
            )

            is ReleaseNoteBlock.Paragraph -> Text(
                text = block.spans.toAnnotatedString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is ReleaseNoteBlock.BulletItem -> Row(
                modifier = Modifier.padding(start = (block.indent * 12).dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (block.indent == 0) "•" else "◦",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = block.spans.toAnnotatedString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ReleaseNoteBlock.Divider -> HorizontalDivider(
                modifier = Modifier.padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun List<ReleaseNoteSpan>.toAnnotatedString(): AnnotatedString {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
    )
    return buildAnnotatedString {
        this@toAnnotatedString.forEach { span ->
            val style = SpanStyle(
                fontWeight = if (span.bold) FontWeight.SemiBold else null,
                fontFamily = if (span.code) FontFamily.Monospace else null,
            )
            val url = span.link
            if (url == null) {
                withStyle(style) { append(span.text) }
            } else {
                withLink(LinkAnnotation.Url(url = url, styles = linkStyles)) {
                    withStyle(style) { append(span.text) }
                }
            }
        }
    }
}
