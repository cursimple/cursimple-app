package com.x500x.cursimple.app

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.x500x.cursimple.R
import com.x500x.cursimple.app.util.CropSourceRect
import com.x500x.cursimple.app.util.ScheduleBackgroundImageStore
import com.x500x.cursimple.app.util.cropSourceRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 背景图裁切。
 *
 * 预览框按课表比例显示，拖动与双指缩放决定取图范围，确认后裁好另存，
 * 用户在应用到课表前就能看到大致效果。
 */
@Composable
internal fun ScheduleBackgroundCropDialog(
    source: Uri,
    frameAspect: Float,
    onDismiss: () -> Unit,
    onCropped: (Uri) -> Unit,
) {
    val context = LocalContext.current
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var working by remember { mutableStateOf(false) }

    val preview by produceState<ImageBitmap?>(initialValue = null, source) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(source).use { input ->
                    BitmapFactory.decodeStream(requireNotNull(input))?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

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
                        .pointerInput(source) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                zoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                                // 平移量按预览框尺寸归一化，缩放越大可移动范围越大
                                offsetX = (offsetX - pan.x / size.width * 2f).coerceIn(-1f, 1f)
                                offsetY = (offsetY - pan.y / size.height * 2f).coerceIn(-1f, 1f)
                            }
                        },
                ) {
                    preview?.let { bitmap ->
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    // 预览里的位移按框宽高折算，与落盘时的偏移口径一致
                                    translationX = -offsetX * this.size.width * 0.25f
                                    translationY = -offsetY * this.size.height * 0.25f
                                },
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
                    TextButton(
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
            TextButton(
                enabled = preview != null && !working,
                onClick = {
                    working = true
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
                    val saved = rect?.let { ScheduleBackgroundImageStore.saveCropped(context, source, it) }
                    working = false
                    if (saved != null) onCropped(saved) else onDismiss()
                },
            ) { Text(stringResource(R.string.settings_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

/** 取景框边线与三分辅助线，让用户看清哪一块会落到课表上。 */
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
