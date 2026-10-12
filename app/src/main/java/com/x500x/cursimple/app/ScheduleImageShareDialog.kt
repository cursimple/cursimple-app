@file:Suppress("LocalContextGetResourceValueCall")
package com.x500x.cursimple.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.x500x.cursimple.R
import com.x500x.cursimple.app.util.ScheduleImageExportOutcome
import com.x500x.cursimple.feature.plugin.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PreparedScheduleImage(val bitmap: Bitmap, val intent: Intent)

@Composable
internal fun ScheduleImageDialog(
    weekNumber: Int,
    maxWeekNumber: Int,
    allWeeks: Boolean,
    generationKey: Any,
    generateImage: suspend () -> ScheduleImageExportOutcome,
    onAllWeeksChange: (Boolean) -> Unit,
    onWeekChange: (Int) -> Unit,
    onShare: (Intent) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val requestKey = listOf(weekNumber, allWeeks, generationKey)
    var image by remember(requestKey) { mutableStateOf<PreparedScheduleImage?>(null) }
    var error by remember(requestKey) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var showPreview by remember(requestKey) { mutableStateOf(false) }
    LaunchedEffect(requestKey, retry) {
        image = null; error = null
        try {
            val result = generateImage()
            val intent = result.intent
            if (intent == null) { error = result.failureReason ?: context.getString(R.string.ie_export_failed_retry); return@LaunchedEffect }
            @Suppress("DEPRECATION") val uri = checkNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            val bitmap = withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 2048) inSampleSize *= 2
                }
                checkNotNull(context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) })
            }
            image = PreparedScheduleImage(bitmap, intent)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = context.getString(R.string.ie_export_failed_retry) }
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ie_image_panel_title)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppFilterChip(selected = allWeeks, onClick = { onAllWeeksChange(true) },
                        label = { AppAtomicText(stringResource(R.string.ie_image_scope_all_weeks)) }, modifier = Modifier.weight(1f))
                    AppFilterChip(selected = !allWeeks, onClick = { onAllWeeksChange(false) },
                        label = { AppAtomicText(stringResource(R.string.ie_image_scope_single_week)) }, modifier = Modifier.weight(1f))
                }
                if (!allWeeks) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppCircleIconButton(onClick = { onWeekChange(weekNumber - 1) }, enabled = weekNumber > 1) {
                        Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.ie_previous_week))
                    }
                    AppAtomicText(stringResource(R.string.ie_week_label, weekNumber), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center))
                    AppCircleIconButton(onClick = { onWeekChange(weekNumber + 1) }, enabled = weekNumber < maxWeekNumber) {
                        Icon(Icons.Rounded.ChevronRight, stringResource(R.string.ie_next_week))
                    }
                }
                Text(stringResource(if (allWeeks) R.string.ie_image_panel_body_all_weeks else R.string.ie_image_panel_body),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val prepared = image
                when {
                    prepared != null -> {
                        Image(prepared.bitmap.asImageBitmap(), stringResource(R.string.ie_image_preview),
                            Modifier.fillMaxWidth().height(180.dp).clickable { showPreview = true }.testTag("schedule-image-thumbnail"),
                            contentScale = ContentScale.Fit)
                        Text(stringResource(R.string.ie_image_preview_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth())
                    }
                    error != null -> {
                        Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("schedule-image-error"))
                        AppOutlinedButton(onClick = { retry++ }, modifier = Modifier.fillMaxWidth()) {
                            AppAtomicText(stringResource(R.string.ie_retry))
                        }
                    }
                    else -> Column(Modifier.fillMaxWidth().heightIn(min = 180.dp), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(32.dp))
                        Text(stringResource(R.string.ie_generating), Modifier.padding(top = 12.dp))
                    }
                }
            }
        },
        confirmButton = { Button(enabled = image != null, onClick = { image?.let { onShare(it.intent) } },
            modifier = Modifier.heightIn(min = 48.dp).testTag("schedule-image-share")) {
            AppAtomicText(stringResource(R.string.ie_share_image))
        } },
        dismissButton = { AppOutlinedButton(onClick = onDismiss) { AppAtomicText(stringResource(R.string.ie_cancel)) } },
    )
    if (showPreview) image?.let { prepared ->
        ScheduleImagePreview(bitmap = prepared.bitmap, onShare = { onShare(prepared.intent) }, onDismiss = { showPreview = false })
    }
}

@Composable
private fun ScheduleImagePreview(bitmap: Bitmap, onShare: () -> Unit, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("schedule-image-preview"), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppCircleIconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.ie_back)) }
                    AppAtomicText(stringResource(R.string.ie_image_preview), Modifier.weight(1f).padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.titleMedium)
                    AppCircleIconButton(onClick = onShare) { Icon(Icons.Rounded.Share, stringResource(R.string.ie_share_image)) }
                }
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { viewport = it }
                    .pointerInput(bitmap, viewport) {
                        detectTransformGestures { centroid, movement, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 6f)
                            val centre = Offset(viewport.width / 2f, viewport.height / 2f)
                            val adjusted = (pan + centre - centroid) * (newScale / scale) + centroid - centre + movement
                            val fit = minOf(viewport.width.toFloat() / bitmap.width, viewport.height.toFloat() / bitmap.height)
                            val maxX = ((bitmap.width * fit * newScale - viewport.width) / 2f).coerceAtLeast(0f)
                            val maxY = ((bitmap.height * fit * newScale - viewport.height) / 2f).coerceAtLeast(0f)
                            pan = Offset(adjusted.x.coerceIn(-maxX, maxX), adjusted.y.coerceIn(-maxY, maxY))
                            scale = newScale
                        }
                    }.pointerInput(bitmap) { detectTapGestures(onDoubleTap = { scale = 1f; pan = Offset.Zero }) },
                    contentAlignment = Alignment.Center) {
                    Image(bitmap.asImageBitmap(), stringResource(R.string.ie_image_preview), Modifier.fillMaxSize()
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y },
                        contentScale = ContentScale.Fit)
                }
                Text(stringResource(R.string.ie_image_zoom_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(12.dp))
            }
        }
    }
}
