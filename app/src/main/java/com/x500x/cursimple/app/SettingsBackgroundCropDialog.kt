package com.x500x.cursimple.app

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.x500x.cursimple.R
import com.x500x.cursimple.app.util.CropPanBounds
import com.x500x.cursimple.app.util.PREVIEW_MAX_EDGE_PX
import com.x500x.cursimple.app.util.CropSourceRect
import com.x500x.cursimple.app.util.ScheduleBackgroundImageStore
import com.x500x.cursimple.app.util.cropOffsetFraction
import com.x500x.cursimple.app.util.cropPanBounds
import com.x500x.cursimple.app.util.cropSourceRect
import com.x500x.cursimple.app.util.decodeSampledImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Preview the timetable-shaped crop before saving the transformed image. */
@Composable
internal fun ScheduleBackgroundCropDialog(
    source: Uri,
    frameAspect: Float,
    onDismiss: () -> Unit,
    onCropped: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var frameSize by remember { mutableStateOf(IntSize.Zero) }
    var working by remember { mutableStateOf(false) }

    val preview by produceState<ImageBitmap?>(initialValue = null, source) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeSampledImage(context, source, PREVIEW_MAX_EDGE_PX) }.getOrNull()
        }
    }

    val panBounds = preview?.let { bitmap ->
        cropPanBounds(
            frameWidth = frameSize.width.toFloat(),
            frameHeight = frameSize.height.toFloat(),
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            zoom = zoom,
        )
    } ?: CropPanBounds(0f, 0f)
    // Read current pan limits inside the gesture closure; initial limits become stale after zooming.
    val currentPanBounds = rememberUpdatedState(panBounds)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_background_crop_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_background_crop_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.settings_background_crop_frame_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(frameAspect.coerceIn(0.2f, 3f))
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .onSizeChanged { frameSize = it }
                        .pointerInput(source) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                zoom = (zoom * gestureZoom).coerceIn(1f, 6f)
                                val bounds = currentPanBounds.value
                                offsetX = (offsetX + cropOffsetFraction(pan.x, bounds.maxX))
                                    .coerceIn(-1f, 1f)
                                offsetY = (offsetY + cropOffsetFraction(pan.y, bounds.maxY))
                                    .coerceIn(-1f, 1f)
                            }
                        },
                ) {
                    preview?.let { bitmap ->
                        CropPreviewImage(
                            bitmap = bitmap,
                            frameAspect = frameAspect,
                            zoom = zoom,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    CropFrameOverlay(modifier = Modifier.fillMaxSize())
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_background_crop_zoom, zoom),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    AppOutlinedButton(
                        enabled = zoom != 1f || offsetX != 0f || offsetY != 0f,
                        onClick = {
                            zoom = 1f
                            offsetX = 0f
                            offsetY = 0f
                        },
                    ) { Text(stringResource(R.string.settings_background_crop_reset)) }
                }
            }
        },
        confirmButton = {
            AppOutlinedButton(
                enabled = preview != null && !working,
                onClick = {
                    working = true
                    scope.launch {
                        // Decode, crop and encode images off the UI thread.
                        val saved = withContext(Dispatchers.IO) {
                            val size = ScheduleBackgroundImageStore.readSize(context, source)
                            val rect: CropSourceRect? = size?.let { (width, height) ->
                                cropSourceRect(
                                    imageWidth = width,
                                    imageHeight = height,
                                    frameAspect = frameAspect,
                                    zoom = zoom,
                                    offsetXFraction = offsetX,
                                    offsetYFraction = offsetY,
                                )
                            }
                            rect?.let { ScheduleBackgroundImageStore.saveCropped(context, source, it) }
                        }
                        working = false
                        if (saved != null) onCropped(saved) else onDismiss()
                    }
                },
            ) { Text(stringResource(R.string.settings_confirm)) }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

/** Use [cropSourceRect] for both preview and saved crop. */
@Composable
private fun CropPreviewImage(
    bitmap: ImageBitmap,
    frameAspect: Float,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val rect = cropSourceRect(
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            frameAspect = frameAspect,
            zoom = zoom,
            offsetXFraction = offsetX,
            offsetYFraction = offsetY,
        ) ?: return@Canvas
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(rect.left, rect.top),
            srcSize = IntSize(rect.width, rect.height),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        )
    }
}

@Composable
private fun CropFrameOverlay(modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val borderColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val stroke = 1.dp.toPx()
        for (index in 1..2) {
            val x = size.width * index / 3f
            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), stroke)
            val y = size.height * index / 3f
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), stroke)
        }
        drawRect(
            color = borderColor,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

