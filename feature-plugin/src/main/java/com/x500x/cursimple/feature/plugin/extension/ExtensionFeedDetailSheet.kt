package com.x500x.cursimple.feature.plugin.extension

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

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
    ActionableFeedItemDetailSheet(record, item, actions, palette, BeijingTime.nowMillis(BeijingTime.zone), onDismiss, scheduleLabel)
}

@Composable
internal fun ActionableFeedItemDetailSheet(
    record: InstalledPluginRecord, item: ExtensionFeedItem, actions: ExtensionHostActions,
    palette: FeedPalette, now: Long, onDismiss: () -> Unit, scheduleLabel: String? = null,
) {
    val context = LocalContext.current
    val store = remember { ExtensionStore.get(context) }
    val data by store.flow(record.pluginId).collectAsState(initial = ExtensionData(record.pluginId))
    val current = data.items.firstOrNull { it.id == item.id } ?: item
    val scope = rememberCoroutineScope()
    var busy by remember(item.id) { mutableStateOf(false) }
    var result by remember(item.id) { mutableStateOf<String?>(null) }
    var failed by remember(item.id) { mutableStateOf(false) }
    val itemUpdated = stringResource(R.string.extension_item_updated)
    fun toggleIgnored() {
        if (busy) return
        busy = true
        result = null
        scope.launch {
            try {
                actions.setItemIgnored(record, item.id, !data.isIgnored(current, now))
                failed = false
                result = itemUpdated
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failed = true
                result = error.message
            } finally { busy = false }
        }
    }
    FeedItemDetailSheet(current, record.name, palette, now, onDismiss, scheduleLabel,
        ignored = data.isIgnored(current, now), busy = busy, result = result, failed = failed,
        onIgnore = ::toggleIgnored)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedItemDetailSheet(
    item: ExtensionFeedItem,
    sourceName: String,
    palette: FeedPalette,
    now: Long,
    onDismiss: () -> Unit,
    scheduleLabel: String? = null,
    ignored: Boolean = false,
    busy: Boolean = false,
    result: String? = null,
    failed: Boolean = false,
    onIgnore: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val attachmentScope = rememberCoroutineScope()
    val attachmentErrorText = stringResource(R.string.extension_attachment_error)
    var attachmentBusy by remember(item.id) { mutableStateOf<String?>(null) }
    var attachmentError by remember(item.id) { mutableStateOf<String?>(null) }
    var pdfPreview by remember(item.id) { mutableStateOf<java.io.File?>(null) }
    var imagePreview by remember(item.id) { mutableStateOf<LoadedFeedImage?>(null) }
    pdfPreview?.let { ExtensionPdfPreview(it, onDismiss = { pdfPreview = null }) }
    imagePreview?.let { ExtensionImagePreview(it.bitmap, it.source.name, onDismiss = { imagePreview = null }) }
    var media by remember(item.id) { mutableStateOf<LoadedFeedMedia?>(null) }
    LaunchedEffect(item.id, item.images) {
        val loader = ExtensionMediaLoader(context)
        val loadedImages = item.images.mapNotNull { image ->
            try {
                val file = loader.fetch(image.url, image.name, hint = "image/*", referer = item.url)
                LoadedFeedImage(image, loader.bitmap(file.file))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }
        media = LoadedFeedMedia(loadedImages)

    }
    val contentScroll = rememberScrollState()
    // Allow sheet dismissal only after content reaches the top to prevent competing vertical scroll movement.
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
                if (busy) CircularProgressIndicator(modifier = Modifier.padding(8.dp).size(20.dp), strokeWidth = 2.dp)
                onIgnore?.let {
                    AppOutlinedButton(onClick = it, enabled = !busy) {
                        Text(stringResource(if (ignored) R.string.extension_restore_item else R.string.extension_ignore_item))
                    }
                }
                AppCircleIconButton(onClick = onDismiss) {
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
                    result?.let { Text(it, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
                    if (ignored) Text(stringResource(R.string.extension_ignored_hint), style = MaterialTheme.typography.bodySmall)
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
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                loaded.images.forEach { image ->
                                    Image(bitmap = image.bitmap.asImageBitmap(),
                                        contentDescription = stringResource(R.string.extension_image_enlarge), contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(132.dp, 104.dp).clip(RoundedCornerShape(16.dp)).clickable { imagePreview = image })
                                }
                            }

                        }
                        item.attachments.forEach { attachment ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(attachment.name.ifBlank { stringResource(R.string.extension_pdf_title) }, modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                AppOutlinedButton(enabled = attachmentBusy == null, onClick = {
                                    attachmentScope.launch {
                                        attachmentBusy = attachment.url; attachmentError = null
                                        try {
                                            val file = ExtensionMediaLoader(context).fetch(attachment.url, attachment.name, attachment.type, referer = item.url)
                                            if (isExtensionPdf(file.file, file.mime)) pdfPreview = file.file else openExtensionFile(context, file.file, file.mime)
                                        } catch (cancelled: CancellationException) { throw cancelled }
                                        catch (_: Exception) { attachmentError = attachmentErrorText }
                                        finally { attachmentBusy = null }
                                    }
                                }) {
                                    if (attachmentBusy == attachment.url) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    else Text(stringResource(R.string.extension_attachment_preview))
                                }
                            }
                        }
                        attachmentError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                    }
                }
            }
        }
    }
}

private data class LoadedFeedImage(val source: ExtensionImage, val bitmap: Bitmap)
private data class LoadedFeedMedia(
    val images: List<LoadedFeedImage>,
)

@Composable
private fun FeedDetailMetadata(text: String, urgent: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
