package com.x500x.cursimple.feature.schedule

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.core.content.FileProvider
import com.x500x.cursimple.core.data.memo.MemoTransferImage
import java.io.File
import java.io.IOException
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.x500x.cursimple.core.data.memo.MemoImageStore
import com.x500x.cursimple.core.kernel.model.MemoImageRef
import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import com.x500x.cursimple.feature.plugin.ui.AppConfirmationDialog
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

private data class MemoImageState(val image: ImageBitmap? = null, val failed: Boolean = false)

@Composable
private fun memoImage(id: String, edge: Int, retry: Int): MemoImageState {
    val context = LocalContext.current
    return produceState(MemoImageState(), id, edge, retry) {
        value = try {
            withContext(Dispatchers.IO) {
                val file = requireNotNull(MemoImageStore(context).file(id))
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                require(bounds.outWidth in 1..32768 && bounds.outHeight in 1..32768)
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > edge) sample *= 2
                val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }))
                MemoImageState(bitmap.asImageBitmap())
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { MemoImageState(failed = true) }
    }.value
}

/** Share, save or copy a stored note image through a cache copy that the FileProvider can serve. */
internal object MemoImageActions {
    private class Exported(val file: File, val mime: String)

    private suspend fun export(context: Context, id: String): Exported? = withContext(Dispatchers.IO) {
        val source = MemoImageStore(context).file(id) ?: return@withContext null
        val head = ByteArray(16)
        val read = source.inputStream().use { it.read(head) }.coerceAtLeast(0)
        val mime = MemoTransferImage.imageMime(head.copyOf(read)) ?: "image/jpeg"
        val dir = File(context.cacheDir, "memos/image-${System.currentTimeMillis()}").apply { mkdirs() }
        val target = File(dir, "memo-image.${if (mime == "image/png") "png" else "jpg"}")
        source.copyTo(target, overwrite = true)
        Exported(target, mime)
    }

    private fun uriOf(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    suspend fun share(context: Context, id: String): Boolean {
        val image = export(context, id) ?: return false
        val uri = uriOf(context, image.file)
        val send = Intent(Intent.ACTION_SEND).setType(image.mime).putExtra(Intent.EXTRA_STREAM, uri).apply {
            clipData = ClipData.newUri(context.contentResolver, image.file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.memo_image_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /** Gallery on Android 10+; older systems hand the file to the share sheet instead of needing storage permission. */
    suspend fun save(context: Context, id: String): Boolean {
        val image = export(context, id) ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return share(context, id)
        return withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "cursimple-note-${System.currentTimeMillis()}.${image.file.extension}")
                put(MediaStore.Images.Media.MIME_TYPE, image.mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/CurSimple")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }) ?: throw IOException("gallery_unavailable")
            try {
                (resolver.openOutputStream(uri) ?: throw IOException("gallery_unavailable")).use { out ->
                    image.file.inputStream().use { it.copyTo(out) }
                }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
            true
        }
    }

    suspend fun copy(context: Context, id: String): Boolean {
        val image = export(context, id) ?: return false
        val clip = ClipData.newUri(context.contentResolver, "image", uriOf(context, image.file))
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        return true
    }
}

private enum class MemoImageAction { Share, Save, Copy }

/** Runs [action] off the main thread and reports the outcome with a toast. */
@Composable
private fun rememberMemoImageRunner(ref: MemoImageRef): (MemoImageAction) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return { action ->
        // Main thread for the share sheet and toast; the file work switches to IO inside the actions.
        scope.launch(Dispatchers.Main.immediate) {
            val ok = try {
                when (action) {
                    MemoImageAction.Share -> MemoImageActions.share(context, ref.id)
                    MemoImageAction.Save -> MemoImageActions.save(context, ref.id)
                    MemoImageAction.Copy -> MemoImageActions.copy(context, ref.id)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { false }
            // Android 13+ shows its own clipboard confirmation; the share sheet needs none.
            val message = when {
                !ok -> R.string.memo_image_action_failed
                action == MemoImageAction.Save && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> R.string.memo_image_saved
                action == MemoImageAction.Copy && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> R.string.memo_image_copied
                else -> null
            }
            message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        }
    }
}

@Composable
private fun MemoImageMenu(expanded: Boolean, onDismiss: () -> Unit, run: (MemoImageAction) -> Unit, onRemove: (() -> Unit)? = null) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = Modifier.testTag("memo-image-menu")) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.memo_image_share)) },
            leadingIcon = { Icon(Icons.Rounded.Share, null) },
            onClick = { onDismiss(); run(MemoImageAction.Share) },
            modifier = Modifier.testTag("memo-image-share"),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.memo_image_save)) },
            leadingIcon = { Icon(Icons.Rounded.Download, null) },
            onClick = { onDismiss(); run(MemoImageAction.Save) },
            modifier = Modifier.testTag("memo-image-save"),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.memo_image_copy)) },
            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
            onClick = { onDismiss(); run(MemoImageAction.Copy) },
            modifier = Modifier.testTag("memo-image-copy"),
        )
        if (onRemove != null) DropdownMenuItem(
            text = { Text(stringResource(R.string.memo_image_remove_title), color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
            onClick = { onDismiss(); onRemove() },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MemoImageThumbnail(ref: MemoImageRef, modifier: Modifier = Modifier, onRemove: (() -> Unit)? = null) {
    var preview by remember(ref.id) { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val state = memoImage(ref.id, 512, retry)
    val gallery = LocalMemoImageGallery.current
    if (preview) MemoImageViewer(ref, gallery, onDismiss = { preview = false })
    if (remove) AppConfirmationDialog(title = stringResource(R.string.memo_image_remove_title),
        message = stringResource(R.string.memo_image_remove_body), confirmLabel = stringResource(R.string.schedule_action_delete),
        cancelLabel = stringResource(R.string.schedule_action_cancel), onConfirm = { remove = false; onRemove?.invoke() },
        onDismiss = { remove = false })
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        var menu by remember { mutableStateOf(false) }
        val run = rememberMemoImageRunner(ref)
        val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(132.dp, 104.dp).testTag("memo-image-thumbnail")) {
            Box(Modifier.fillMaxSize().combinedClickable(
                onClick = { if (state.failed) retry++ else if (state.image != null) preview = true },
                onLongClick = if (state.image != null) {
                    { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); menu = true }
                } else null,
            ), contentAlignment = Alignment.Center) {
                when {
                    state.image != null -> Image(state.image, ref.alt.ifBlank { stringResource(R.string.memo_tool_image) },
                        modifier = Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                    state.failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Image, null)
                        Text(stringResource(R.string.memo_image_retry), style = MaterialTheme.typography.labelSmall)
                    }
                    else -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                MemoImageMenu(menu, onDismiss = { menu = false }, run = run)
            }
        }
        if (onRemove != null) AppCircleIconButton(onClick = { remove = true }) {
            Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.memo_image_remove_title), tint = MaterialTheme.colorScheme.error)
        }
    }
}

/** Every image of the note being read, in order, so the full-screen preview can swipe between them. */
internal val LocalMemoImageGallery = compositionLocalOf<List<MemoImageRef>> { emptyList() }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MemoImageViewer(ref: MemoImageRef, gallery: List<MemoImageRef>, onDismiss: () -> Unit) {
    val pages = remember(ref.id, gallery) { gallery.takeIf { list -> list.any { it.id == ref.id } } ?: listOf(ref) }
    val pager = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = pages.indexOfFirst { it.id == ref.id }.coerceAtLeast(0),
    ) { pages.size }
    var zoomed by remember { mutableStateOf(false) }
    val current = pages[pager.currentPage.coerceIn(pages.indices)]
    val run = rememberMemoImageRunner(current)
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().testTag("memo-image-viewer")) {
            androidx.compose.foundation.pager.HorizontalPager(
                state = pager,
                userScrollEnabled = !zoomed,
                modifier = Modifier.fillMaxSize().padding(top = 64.dp),
            ) { page ->
                MemoImagePage(pages[page], active = page == pager.currentPage, onZoomed = { zoomed = it })
            }
            if (pages.size > 1) {
                Text(
                    text = "${pager.currentPage + 1} / ${pages.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 22.dp).testTag("memo-image-counter"),
                )
            }
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppCircleIconButton(onClick = { run(MemoImageAction.Copy) }, modifier = Modifier.testTag("memo-image-viewer-copy")) {
                    Icon(Icons.Rounded.ContentCopy, stringResource(R.string.memo_image_copy))
                }
                AppCircleIconButton(onClick = { run(MemoImageAction.Share) }, modifier = Modifier.testTag("memo-image-viewer-share")) {
                    Icon(Icons.Rounded.Share, stringResource(R.string.memo_image_share))
                }
                AppCircleIconButton(onClick = { run(MemoImageAction.Save) }, modifier = Modifier.testTag("memo-image-viewer-save")) {
                    Icon(Icons.Rounded.Download, stringResource(R.string.memo_image_save))
                }
                AppCircleIconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.schedule_action_close))
                }
            }
        }
    }
}

/** One page of the preview: pinch to zoom, drag to pan once zoomed, double tap to toggle, long press for the menu. */
@Composable
private fun MemoImagePage(ref: MemoImageRef, active: Boolean, onZoomed: (Boolean) -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    val state = memoImage(ref.id, 2560, retry)
    var zoom by remember(ref.id) { mutableFloatStateOf(1f) }
    var pan by remember(ref.id) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    fun clamp(offset: Offset, scale: Float): Offset {
        val image = state.image ?: return Offset.Zero
        val fit = min(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
        val x = max(0f, (image.width * fit * scale - viewport.width) / 2)
        val y = max(0f, (image.height * fit * scale - viewport.height) / 2)
        return Offset(offset.x.coerceIn(-x, x), offset.y.coerceIn(-y, y))
    }
    LaunchedEffect(viewport, state.image) { pan = clamp(pan, zoom) }
    // Leaving a page puts it back at full view, so swiping back never lands on a stale zoom.
    LaunchedEffect(active) { if (!active) { zoom = 1f; pan = Offset.Zero } }
    LaunchedEffect(zoom, active) { if (active) onZoomed(zoom > 1.01f) }
    var menu by remember { mutableStateOf(false) }
    val run = rememberMemoImageRunner(ref)
    Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { viewport = it }
        .pointerInput(ref.id, state.image) {
            // Single-finger drags stay with the pager until the image is zoomed; two fingers always zoom.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    val multi = event.changes.count { it.pressed } > 1
                    if (multi || zoom > 1f) {
                        val factor = event.calculateZoom()
                        val delta = event.calculatePan()
                        val centroid = event.calculateCentroid()
                        if (factor != 1f || delta != Offset.Zero) {
                            val next = (zoom * factor).coerceIn(1f, 5f)
                            val pivot = centroid - Offset(viewport.width / 2f, viewport.height / 2f)
                            pan = clamp(pan * (next / zoom) + pivot * (1 - next / zoom) + delta, next)
                            zoom = next
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }.pointerInput(ref.id) {
            detectTapGestures(onLongPress = { if (state.image != null) menu = true }, onDoubleTap = { point ->
                if (zoom > 1f) { zoom = 1f; pan = Offset.Zero }
                else { zoom = 2.5f; pan = clamp((Offset(viewport.width / 2f, viewport.height / 2f) - point) * 1.5f, zoom) }
            })
        }, contentAlignment = Alignment.Center) {
        when {
            state.image != null -> Image(state.image, ref.alt, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y
                })
            state.failed -> AppOutlinedButton(onClick = { retry++ }) {
                AppAtomicText(stringResource(R.string.memo_image_retry), color = Color.White)
            }
            else -> CircularProgressIndicator()
        }
        MemoImageMenu(menu, onDismiss = { menu = false }, run = run)
    }
}
