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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.x500x.cursimple.R
import com.x500x.cursimple.app.update.ReleaseImageLoader
import com.x500x.cursimple.app.update.ReleaseNoteImage
import com.x500x.cursimple.app.update.rememberReleaseImageLoader
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
    ReleaseNotesBlocks(blocks)
}

/**
 * 逐块渲染已经解析好的发布说明；翻页公告也用它。
 * [large] 给全屏公告用：正文大一号，弹窗里的小方框用默认的小字。
 */
@Composable
fun ReleaseNotesBlocks(blocks: List<ReleaseNoteBlock>, large: Boolean = false) {
    val imageLoader = rememberReleaseImageLoader()
    val bodyStyle = if (large) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall
    blocks.forEach { block ->
        when (block) {
            is ReleaseNoteBlock.Heading -> Text(
                text = block.spans.toAnnotatedString(),
                style = when (block.level) {
                    1 -> MaterialTheme.typography.titleMedium
                    2 -> if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall
                    else -> if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge
                },
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = if (block.level >= 3) 4.dp else 0.dp),
            )

            is ReleaseNoteBlock.Paragraph -> Text(
                text = block.spans.toAnnotatedString(),
                style = bodyStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is ReleaseNoteBlock.BulletItem -> Row(
                modifier = Modifier.padding(start = (block.indent * 12).dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (block.indent == 0) "•" else "◦",
                    style = bodyStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = block.spans.toAnnotatedString(),
                    style = bodyStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ReleaseNoteBlock.Divider -> HorizontalDivider(
                modifier = Modifier.padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // 不翻页的地方（更新提示、更新历史）图就按顺序往下排
            is ReleaseNoteBlock.Gallery -> block.images.forEach { image ->
                ReleaseImage(
                    image = image,
                    loader = imageLoader,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                )
            }
        }
    }
}

/**
 * 公告里的一张图：加载中转圈，失败时给一行说明，点一下重试。
 * 图按原比例缩进给定的框里，不裁，截图的边角都看得到。
 */
@Composable
fun ReleaseImage(
    image: ReleaseNoteImage,
    loader: ReleaseImageLoader,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    /** 加载好之后点图做什么，比如全屏放大；为空时点图没有反应。 */
    onClick: (() -> Unit)? = null,
) {
    var attempt by remember(image.url) { mutableIntStateOf(0) }
    val state by produceState<ReleaseImageState>(ReleaseImageState.Loading, image.url, attempt) {
        value = ReleaseImageState.Loading
        value = loader.load(image.url)?.let(ReleaseImageState::Loaded) ?: ReleaseImageState.Failed
    }
    val shape = RoundedCornerShape(cornerRadius)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (val current = state) {
            // 按图自己的比例摆，框多出来的地方留白而不是垫一块灰底，截图四周才不会有灰边
            is ReleaseImageState.Loaded -> Image(
                bitmap = current.bitmap,
                contentDescription = image.caption.ifBlank { null },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .aspectRatio(
                        current.bitmap.width.toFloat() / current.bitmap.height.coerceAtLeast(1),
                        matchHeightConstraintsFirst = true,
                    )
                    .clip(shape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            )

            ReleaseImageState.Failed -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { attempt++ },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.update_announcement_image_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp),
                )
            }

            ReleaseImageState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        }
    }
}

private sealed interface ReleaseImageState {
    data object Loading : ReleaseImageState

    data object Failed : ReleaseImageState

    data class Loaded(val bitmap: ImageBitmap) : ReleaseImageState
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
