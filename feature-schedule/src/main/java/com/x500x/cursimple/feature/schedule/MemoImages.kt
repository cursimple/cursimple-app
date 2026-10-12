package com.x500x.cursimple.feature.schedule

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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

@Composable
internal fun MemoImageThumbnail(ref: MemoImageRef, modifier: Modifier = Modifier, onRemove: (() -> Unit)? = null) {
    var preview by remember(ref.id) { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val state = memoImage(ref.id, 512, retry)
    if (preview) MemoImageViewer(ref, onDismiss = { preview = false })
    if (remove) AppConfirmationDialog(title = stringResource(R.string.memo_image_remove_title),
        message = stringResource(R.string.memo_image_remove_body), confirmLabel = stringResource(R.string.schedule_action_delete),
        cancelLabel = stringResource(R.string.schedule_action_cancel), onConfirm = { remove = false; onRemove?.invoke() },
        onDismiss = { remove = false })
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(onClick = { if (state.failed) retry++ else if (state.image != null) preview = true },
            color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(132.dp, 104.dp).testTag("memo-image-thumbnail")) {
            Box(contentAlignment = Alignment.Center) {
                when {
                    state.image != null -> Image(state.image, ref.alt.ifBlank { stringResource(R.string.memo_tool_image) },
                        modifier = Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                    state.failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Image, null)
                        Text(stringResource(R.string.memo_image_retry), style = MaterialTheme.typography.labelSmall)
                    }
                    else -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
        if (onRemove != null) AppCircleIconButton(onClick = { remove = true }) {
            Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.memo_image_remove_title), tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun MemoImageViewer(ref: MemoImageRef, onDismiss: () -> Unit) {
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
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().testTag("memo-image-viewer")) {
            Box(Modifier.fillMaxSize().padding(top = 64.dp).clipToBounds().onSizeChanged { viewport = it }
                .pointerInput(ref.id, state.image) {
                    detectTransformGestures { centroid, delta, factor, _ ->
                        val next = (zoom * factor).coerceIn(1f, 5f)
                        val pivot = centroid - Offset(viewport.width / 2f, viewport.height / 2f)
                        pan = clamp(pan * (next / zoom) + pivot * (1 - next / zoom) + delta, next)
                        zoom = next
                    }
                }.pointerInput(ref.id) {
                    detectTapGestures(onDoubleTap = { point ->
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
            }
            AppCircleIconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.schedule_action_close))
            }
        }
    }
}
