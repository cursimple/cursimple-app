package com.x500x.cursimple.feature.plugin.extension

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.x500x.cursimple.feature.plugin.R
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal data class PdfPageImage(val bitmap: Bitmap, val pageCount: Int)

internal fun isExtensionPdf(file: File, mime: String): Boolean =
    mime.substringBefore(';').trim().equals("application/pdf", ignoreCase = true) || file.extension.equals("pdf", ignoreCase = true)

/** Decode one bounded page. File descriptors never outlive a render request. */
internal suspend fun renderExtensionPdfPage(file: File, index: Int, width: Int): PdfPageImage = withContext(Dispatchers.IO) {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            require(renderer.pageCount > 0 && index in 0 until renderer.pageCount)
            renderer.openPage(index).use { page ->
                val scale = min(width.coerceIn(1, 2400).toFloat() / page.width,
                    sqrt(4_000_000f / (page.width.toFloat() * page.height)))
                val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1),
                    (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    coroutineContext.ensureActive()
                    PdfPageImage(bitmap, renderer.pageCount)
                } catch (failure: Throwable) { bitmap.recycle(); throw failure }
            }
        }
    }
}

@Composable
internal fun ExtensionPdfPreview(file: File, onDismiss: () -> Unit) {
    var index by rememberSaveable(file.path) { mutableIntStateOf(0) }
    var pageCount by remember(file.path) { mutableIntStateOf(0) }
    var page by remember(file.path) { mutableStateOf<PdfPageImage?>(null) }
    var error by remember(file.path) { mutableStateOf(false) }
    var retry by remember(file.path) { mutableIntStateOf(0) }
    var transform by remember(file.path, index) { mutableStateOf(PdfViewportTransform()) }
    var viewportSize by remember { mutableStateOf(Size.Zero) }
    val pageSize = page?.bitmap?.let { Size(it.width.toFloat(), it.height.toFloat()) } ?: Size.Zero
    fun zoomBy(factor: Float) {
        transform = transform.transform(Offset(viewportSize.width / 2, viewportSize.height / 2), Offset.Zero, factor, viewportSize, pageSize)
    }
    val density = LocalDensity.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.extension_pdf_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.extension_pdf_gesture_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AppCircleIconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.extension_pdf_close)) }
                }
                HorizontalDivider()
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
                    val viewport = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
                    val width = viewport.width.toInt()
                    LaunchedEffect(viewport, pageSize) { viewportSize = viewport; transform = transform.bounded(viewport, pageSize) }
                    LaunchedEffect(file.path, index, width, retry) {
                        page = null; error = false
                        try {
                            val rendered = renderExtensionPdfPage(file, index, width * 2)
                            pageCount = rendered.pageCount
                            page = rendered
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { error = true }
                    }
                    val current = page
                    when {
                        error -> Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(R.string.extension_pdf_error), color = MaterialTheme.colorScheme.error)
                            AppOutlinedButton(onClick = { retry++ }) { Text(stringResource(R.string.extension_pdf_retry)) }
                        }
                        current == null -> CircularProgressIndicator()
                        else -> {
                            val contentSize = Size(current.bitmap.width.toFloat(), current.bitmap.height.toFloat())
                            val zoomDescription = stringResource(R.string.extension_pdf_zoom_level, (transform.zoom * 100).roundToInt())
                            Box(Modifier.fillMaxSize().testTag("pdf-viewport").semantics { stateDescription = zoomDescription }
                                .pointerInput(index, viewport, contentSize) {
                                    detectTransformGestures { centroid, delta, factor, _ ->
                                        transform = transform.transform(centroid, delta, factor, viewport, contentSize)
                                    }
                                }
                                .pointerInput(index, viewport, contentSize) {
                                    detectTapGestures(onDoubleTap = { focus ->
                                        transform = if (transform.zoom > 1f) PdfViewportTransform()
                                        else transform.transform(focus, Offset.Zero, 2f, viewport, contentSize)
                                    })
                                }) {
                                Image(current.bitmap.asImageBitmap(), stringResource(R.string.extension_pdf_page, index + 1, pageCount),
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().testTag("pdf-page-$index").graphicsLayer {
                                        scaleX = transform.zoom; scaleY = transform.zoom
                                        translationX = transform.pan.x; translationY = transform.pan.y
                                    })
                            }
                        }
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppCircleIconButton(onClick = { index = (index - 1).coerceAtLeast(0) }, enabled = page != null && index > 0) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.extension_pdf_previous))
                    }
                    Text(if (pageCount > 0) "${index + 1} / $pageCount" else "…", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AppCircleIconButton(onClick = { index = (index + 1).coerceAtMost((pageCount - 1).coerceAtLeast(0)) }, enabled = page != null && index + 1 < pageCount) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.extension_pdf_next))
                    }
                    AppCircleIconButton(onClick = { zoomBy((transform.zoom - .5f).coerceAtLeast(1f) / transform.zoom) }, enabled = page != null && transform.zoom > 1f) {
                        Icon(Icons.Rounded.ZoomOut, stringResource(R.string.extension_pdf_zoom_out))
                    }
                    AppCircleIconButton(onClick = { zoomBy((transform.zoom + .5f).coerceAtMost(4f) / transform.zoom) }, enabled = page != null && transform.zoom < 4f) {
                        Icon(Icons.Rounded.ZoomIn, stringResource(R.string.extension_pdf_zoom_in))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExtensionImagePreview(bitmap: Bitmap, name: String, onDismiss: () -> Unit) {
    var zoom by remember(bitmap) { mutableFloatStateOf(1f) }
    var pan by remember(bitmap) { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AppCircleIconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.extension_image_close)) }
                }
                Box(Modifier.weight(1f).clipToBounds()) {
                    Image(bitmap.asImageBitmap(), name, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().pointerInput(bitmap) {
                            detectTransformGestures { _, delta, factor, _ ->
                                zoom = (zoom * factor).coerceIn(1f, 5f)
                                pan = if (zoom == 1f) Offset.Zero else Offset(
                                    (pan.x + delta.x).coerceIn(-size.width * (zoom - 1) / 2, size.width * (zoom - 1) / 2),
                                    (pan.y + delta.y).coerceIn(-size.height * (zoom - 1) / 2, size.height * (zoom - 1) / 2))
                            }
                        }.graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y))
                }
            }
        }
    }
}
