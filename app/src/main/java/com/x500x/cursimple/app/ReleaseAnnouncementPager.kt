package com.x500x.cursimple.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import com.x500x.cursimple.R
import com.x500x.cursimple.app.update.ReleaseAnnouncement
import com.x500x.cursimple.app.update.ReleaseHighlight
import com.x500x.cursimple.app.update.ReleaseImageLoader
import com.x500x.cursimple.app.update.ReleaseNoteImage
import com.x500x.cursimple.app.update.buildReleaseAnnouncement
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Image sections become full-screen highlights; text-only notes use a scrollable dialog. */
@Composable
fun ReleaseAnnouncementDialog(
    versionName: String,
    markdown: String,
    imageLoader: ReleaseImageLoader,
    onDismiss: () -> Unit,
) {
    val announcement = remember(markdown) { buildReleaseAnnouncement(markdown) }
    if (announcement == null || announcement.highlights.isEmpty()) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_announcement_title, versionName)) },
            text = { ReleaseNotesCard(markdown = markdown, maxHeight = 300.dp) },
            confirmButton = {
                Button(onClick = onDismiss) { Text(stringResource(R.string.update_announcement_dismiss)) }
            },
        )
        return
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Match system-bar icon contrast to the full-screen announcement background.
        val dialogView = LocalView.current
        val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, dialogView).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            ReleaseAnnouncementScreen(
                versionName = versionName,
                announcement = announcement,
                imageLoader = imageLoader,
                onDismiss = onDismiss,
            )
        }
    }
}

/** Auto-advance after idle time; pause for touch or drag and restart from the settled page. */
@Composable
private fun ReleaseAnnouncementScreen(
    versionName: String,
    announcement: ReleaseAnnouncement,
    imageLoader: ReleaseImageLoader,
    onDismiss: () -> Unit,
) {
    val highlights = announcement.highlights
    val pageCount = highlights.size + if (announcement.rest.isNotEmpty()) 1 else 0
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    // Observe touch without consuming the pager's gestures.
    var touching by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf<ReleaseNoteImage?>(null) }
    val restScroll = rememberScrollState()
    val readingRest by remember {
        derivedStateOf { pagerState.settledPage >= highlights.size && restScroll.value > 0 }
    }

    // Start idle timing only on settled pages; gesture state changes must not cancel an active page animation.
    LaunchedEffect(pagerState, pageCount) {
        if (pageCount < 2) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage }.collectLatest { page ->
            delay(if (page >= highlights.size) REST_PAGE_DWELL_MS else AUTO_ADVANCE_MS)
            snapshotFlow { touching || pagerState.isScrollInProgress || zoomed != null || readingRest }
                .first { busy -> !busy }
            try {
                pagerState.animateScrollToPage((page + 1) % pageCount)
            } catch (interrupted: CancellationException) {
                // Treat user-drag cancellation locally; propagate cancellation when the announcement closes.
                currentCoroutineContext().ensureActive()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.update_announcement_title, versionName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.update_announcement_dismiss))
            }
        }
        HorizontalPager(
            state = pagerState,
            pageSpacing = 24.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        touching = true
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                        touching = false
                    }
                },
        ) { page ->
            if (page < highlights.size) {
                HighlightPage(highlights[page], imageLoader, onZoom = { zoomed = it })
            } else {
                RestPage(announcement, restScroll)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1 + pageCount) % pageCount) }
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.update_announcement_prev),
                )
            }
            PageDots(
                count = pageCount,
                current = pagerState.currentPage,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1) % pageCount) } },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.update_announcement_next),
                )
            }
        }
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(stringResource(R.string.update_announcement_dismiss))
        }
    }

    zoomed?.let { image ->
        ReleaseImageViewer(image = image, imageLoader = imageLoader, onDismiss = { zoomed = null })
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(if (active) 20.dp else 7.dp, label = "pageDot")
            Box(
                modifier = Modifier
                    .height(7.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}

@Composable
private fun HighlightPage(
    highlight: ReleaseHighlight,
    imageLoader: ReleaseImageLoader,
    onZoom: (ReleaseNoteImage) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Keep titles to one line to preserve image space.
        Text(
            text = highlight.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (highlight.body.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ReleaseNotesBlocks(highlight.body, large = true)
            }
        }
        Spacer(Modifier.height(16.dp))
        ReleaseImage(
            image = highlight.image,
            loader = imageLoader,
            cornerRadius = 20.dp,
            onClick = { onZoom(highlight.image) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        Text(
            text = stringResource(R.string.update_announcement_tap_to_zoom),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp),
        )
    }
}

@Composable
private fun RestPage(announcement: ReleaseAnnouncement, scrollState: androidx.compose.foundation.ScrollState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.update_announcement_more_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        ReleaseNotesBlocks(announcement.rest, large = true)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ReleaseImageViewer(
    image: ReleaseNoteImage,
    imageLoader: ReleaseImageLoader,
    onDismiss: () -> Unit,
) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, image.url) {
        value = imageLoader.load(image.url)
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val shownScale by animateFloatAsState(scale, label = "zoomScale")
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onDismiss() },
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = ZOOM_ON_DOUBLE_TAP
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = image.caption.ifBlank { null },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(12.dp)
                        .graphicsLayer {
                            scaleX = shownScale
                            scaleY = shownScale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
            if (image.caption.isNotBlank()) {
                Text(
                    text = image.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .systemBarsPadding()
                        .padding(bottom = 20.dp),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .systemBarsPadding()
                    .padding(8.dp)
                    .size(44.dp),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.update_announcement_dismiss), tint = Color.White)
            }
        }
    }
}

private const val AUTO_ADVANCE_MS = 5_000L
private const val REST_PAGE_DWELL_MS = 15_000L
private const val ZOOM_ON_DOUBLE_TAP = 2.5f
private const val MAX_ZOOM = 5f
