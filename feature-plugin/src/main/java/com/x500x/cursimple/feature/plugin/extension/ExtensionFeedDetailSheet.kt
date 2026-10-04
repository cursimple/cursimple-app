package com.x500x.cursimple.feature.plugin.extension

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.feature.plugin.R
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.manifest.PluginFeedTypeSpec
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.kernel.model.ScheduleEvent

/** 供宿主从课表事务打开同一份内容详情，不依赖组件日历是否被启用。 */
@Composable
fun ExtensionContentDetailSheet(
    record: InstalledPluginRecord,
    item: ExtensionFeedItem,
    actions: ExtensionHostActions,
    onDismiss: () -> Unit,
    scheduleEvent: ScheduleEvent? = null,
) {
    var types by remember(record.installKey) { mutableStateOf<List<PluginFeedTypeSpec>>(emptyList()) }
    LaunchedEffect(record.installKey) {
        runCatching { actions.loadPackage(record) }.onSuccess { types = it.first.extension?.feedTypes.orEmpty() }
    }
    val colors = MaterialTheme.colorScheme
    val palette = remember(types, colors) { FeedPalette(types, listOf(colors.primary, colors.secondary, colors.tertiary)) }
    val scheduleLabel = scheduleEvent?.let { stringResource(R.string.extension_content_schedule_slot, it.date, it.startTime, it.endTime) }
    FeedItemDetailSheet(item, record.name, palette, BeijingTime.nowMillis(BeijingTime.zone), onDismiss, scheduleLabel)
}

/** 内容始终在宿主内阅读：正文、图片和附件都由组件带回后在这里展示。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedItemDetailSheet(
    item: ExtensionFeedItem,
    sourceName: String,
    palette: FeedPalette,
    now: Long,
    onDismiss: () -> Unit,
    scheduleLabel: String? = null,
) {
    val context = LocalContext.current
    var media by remember(item.id) { mutableStateOf<LoadedFeedMedia?>(null) }
    LaunchedEffect(item.id, item.images, item.attachments) {
        val loader = ExtensionMediaLoader(context)
        val loadedImages = item.images.mapNotNull { image ->
            runCatching {
                val file = loader.fetch(image.url, image.name, hint = "image/*", referer = item.url)
                LoadedFeedImage(image, loader.bitmap(file.file))
            }.getOrNull()
        }
        val loadedAttachments = item.attachments.mapNotNull { attachment ->
            runCatching {
                val file = loader.fetch(attachment.url, attachment.name, attachment.type, referer = item.url)
                LoadedFeedAttachment(attachment, file)
            }.getOrNull()
        }
        media = LoadedFeedMedia(loadedImages, loadedAttachments)
    }
    val contentScroll = rememberScrollState()
    // 弹层和内部滚动条抢同一个向下拖动时，滚到底部后弹层会反复位移，看起来一直上下跳。
    // 只在内容已经滚到顶时才允许把弹层关掉：内容没到顶时弹层不动，循环就断了。
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value -> value != SheetValue.Hidden || contentScroll.value <= 0 },
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = Dp.Unspecified,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    sourceName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.extension_item_close))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            SelectionContainer(
                modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp).weight(1f, fill = false),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(contentScroll)
                        .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        FeedTypeBadge(item, palette)
                        Text(
                            stringResource(feedStatusRes(item)),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    SettingsCard {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            scheduleLabel?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                Text(stringResource(R.string.extension_content_schedule_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                            Text(
                                stringResource(R.string.plugin_detail_field_source),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                listOfNotNull(
                                    sourceName.ifBlank { null },
                                    item.course.ifBlank { null },
                                    item.category.ifBlank { null }?.takeIf { it != palette.labelOf(item) },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            item.author.ifBlank { null }?.let {
                                FeedDetailMetadata(stringResource(R.string.extension_item_author, it))
                            }
                            item.dueAt?.let {
                                FeedDetailMetadata(
                                    stringResource(
                                        R.string.extension_item_due,
                                        feedDateTime(it),
                                        if (item.done) stringResource(feedStatusRes(item)) else whenText(item, now).orEmpty(),
                                    ),
                                    urgent = !item.done && it < now,
                                )
                            }
                            item.startAt?.let { FeedDetailMetadata(stringResource(R.string.extension_item_start, feedDateTime(it))) }
                            item.publishAt?.let { FeedDetailMetadata(stringResource(R.string.extension_item_publish, feedDateTime(it))) }
                        }
                    }
                    val body = item.content.ifBlank { item.summary }
                    if (body.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.extension_feed_detail_body),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(text = body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (item.images.isNotEmpty() || item.attachments.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.extension_feed_media_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { heading() },
                        )
                        val loaded = media
                        if (loaded == null) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else {
                            loaded.images.forEach { image ->
                                Image(
                                    bitmap = image.bitmap.asImageBitmap(),
                                    contentDescription = image.source.name.ifBlank { null },
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                                )
                            }
                            loaded.attachments.forEach { attachment ->
                                Text(
                                    text = "📎 ${attachment.source.name.ifBlank { attachment.file.name }} · ${attachment.file.file.length() / 1024} KB",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class LoadedFeedImage(val source: ExtensionImage, val bitmap: Bitmap)
private data class LoadedFeedAttachment(val source: ExtensionAttachment, val file: ExtensionMediaFile)
private data class LoadedFeedMedia(
    val images: List<LoadedFeedImage>,
    val attachments: List<LoadedFeedAttachment>,
)

@Composable
private fun FeedDetailMetadata(text: String, urgent: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
