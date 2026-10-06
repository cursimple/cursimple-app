package com.x500x.cursimple.feature.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Maximum timetable zoom. */
internal const val SCHEDULE_MAX_ZOOM = 3f

internal const val SCHEDULE_ZOOM_EPSILON = 1.01f

private const val PERCENT_LINGER_MILLIS = 700L

/**
 * During pinch, transform existing layers without remeasuring; relayout at final density on
 * release. Manage two-axis panning directly, preserve ordinary gestures at unity, and expose
 * draw-time offsets for pinned headers.
 */
@Composable
internal fun ZoomableScheduleBox(
    enabled: Boolean,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (stickyOffset: () -> IntOffset, zoomed: Boolean) -> Unit,
) {
    if (!enabled) {
        Box(modifier) { content({ IntOffset.Zero }, false) }
        return
    }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val currentZoom by rememberUpdatedState(zoom)
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var flingJob by remember { mutableStateOf<Job?>(null) }
    var pinching by remember { mutableStateOf(false) }
    var liveZoom by remember { mutableFloatStateOf(zoom) }
    val shownZoom = if (pinching) liveZoom else zoom
    val zoomed = shownZoom > SCHEDULE_ZOOM_EPSILON
    var showPercent by remember { mutableStateOf(false) }
    LaunchedEffect(pinching) {
        if (pinching) {
            showPercent = true
        } else {
            delay(PERCENT_LINGER_MILLIS)
            showPercent = false
        }
    }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val viewportWidth = constraints.maxWidth.toFloat()
        val viewportHeight = constraints.maxHeight.toFloat()
        fun maxX(z: Float) = (viewportWidth * (z - 1f)).coerceAtLeast(0f)
        fun maxY(z: Float) = (viewportHeight * (z - 1f)).coerceAtLeast(0f)
        fun moveTo(x: Float, y: Float, z: Float = currentZoom) {
            offsetX = x.coerceIn(0f, maxX(z))
            offsetY = y.coerceIn(0f, maxY(z))
        }
        // Clamp offsets after zoom reduction.
        if (offsetX > maxX(shownZoom) || offsetY > maxY(shownZoom)) moveTo(offsetX, offsetY, shownZoom)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(viewportWidth, viewportHeight) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                flingJob?.cancel()
                                if (!pinching) {
                                    liveZoom = currentZoom
                                    pinching = true
                                }
                                val old = liveZoom
                                val next = (old * event.calculateZoom()).coerceIn(1f, SCHEDULE_MAX_ZOOM)
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                val ratio = next / old
                                moveTo(
                                    (offsetX + centroid.x) * ratio - centroid.x - pan.x,
                                    (offsetY + centroid.y) * ratio - centroid.y - pan.y,
                                    next,
                                )
                                liveZoom = next
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        if (pinching) {
                            if (liveZoom != currentZoom) currentOnZoomChange(liveZoom)
                            pinching = false
                        }
                    }
                }
                .pointerInput(zoomed, viewportWidth, viewportHeight) {
                    if (!zoomed) return@pointerInput
                    val tracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = {
                            flingJob?.cancel()
                            tracker.resetTracking()
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            moveTo(offsetX - drag.x, offsetY - drag.y)
                        },
                        onDragEnd = {
                            val velocity = tracker.calculateVelocity()
                            flingJob = scope.launch {
                                val decay = splineBasedDecay<Float>(density)
                                coroutineScope {
                                    launch {
                                        AnimationState(offsetX, -velocity.x).animateDecay(decay) {
                                            moveTo(value, offsetY)
                                            if (value <= 0f || value >= maxX(currentZoom)) cancelAnimation()
                                        }
                                    }
                                    launch {
                                        AnimationState(offsetY, -velocity.y).animateDecay(decay) {
                                            moveTo(offsetX, value)
                                            if (value <= 0f || value >= maxY(currentZoom)) cancelAnimation()
                                        }
                                    }
                                }
                            }
                        },
                    )
                },
        ) {
            CompositionLocalProvider(
                LocalDensity provides Density(density.density * zoom, density.fontScale),
            ) {
                Box(
                    modifier = Modifier.layout { measurable, _ ->
                        // Read offsets and live pinch scale during placement only to avoid per-frame relayout.
                        val width = (viewportWidth * zoom).roundToInt()
                        val height = (viewportHeight * zoom).roundToInt()
                        val placeable = measurable.measure(Constraints.fixed(width, height))
                        layout(viewportWidth.roundToInt(), viewportHeight.roundToInt()) {
                            val scale = if (pinching) liveZoom / zoom else 1f
                            placeable.placeWithLayer(-offsetX.roundToInt(), -offsetY.roundToInt()) {
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                        }
                    },
                ) {
                    content(
                        {
                            val scale = if (pinching) liveZoom / zoom else 1f
                            IntOffset((offsetX / scale).roundToInt(), (offsetY / scale).roundToInt())
                        },
                        zoomed,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = zoomed,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 6.dp),
        ) {
            Surface(
                onClick = {
                    flingJob?.cancel()
                    currentOnZoomChange(1f)
                    offsetX = 0f
                    offsetY = 0f
                },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ZoomOutMap,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.schedule_zoom_reset),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = showPercent,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            val percent = (shownZoom * 100).roundToInt()
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.82f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                Text(
                    text = when {
                        shownZoom >= SCHEDULE_MAX_ZOOM -> stringResource(R.string.schedule_zoom_percent_max, percent)
                        else -> stringResource(R.string.schedule_zoom_percent, percent)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}
