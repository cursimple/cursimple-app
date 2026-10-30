package com.x500x.cursimple.feature.plugin.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Hand-drawn slider: a rounded track, a filled part and a round thumb, with the value shown as a ratio at the right.
 * [valueLabel] turns the position into the right-hand text; by default it is the share of [valueRange] in percent.
 * [modifier] applies to the track (where the slider semantics live). [steps] > 0 snaps to that many equal intervals without drawing tick marks.
 */
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
    valueLabel: (Float) -> String = { v ->
        val span = valueRange.endInclusive - valueRange.start
        "${if (span <= 0f) 0 else (((v - valueRange.start) / span) * 100f).roundToInt()}%"
    },
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val haptics = rememberAppHaptics()
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span <= 0f) 0f else ((value - valueRange.start) / span).coerceIn(0f, 1f)
    var pressed by remember { mutableStateOf(false) }
    val thumbRadius by animateDpAsState(if (pressed) 12.dp else 9.dp, tween(120), label = "slider-thumb")
    val track = MaterialTheme.colorScheme.outlineVariant
    val active = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val thumbRing = MaterialTheme.colorScheme.surface
    val label = valueLabel(value)

    fun snap(raw: Float): Float {
        val clamped = raw.coerceIn(valueRange.start, valueRange.endInclusive)
        if (steps <= 0 || span <= 0f) return clamped
        val interval = span / (steps + 1)
        return (valueRange.start + ((clamped - valueRange.start) / interval).roundToInt() * interval)
            .coerceIn(valueRange.start, valueRange.endInclusive)
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = modifier
                .weight(1f)
                .height(48.dp)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange.start, valueRange.endInclusive), valueRange, steps)
                    stateDescription = label
                    if (!enabled) disabled()
                    setProgress { target ->
                        if (!enabled) return@setProgress false
                        val next = snap(target)
                        currentOnChange(next)
                        currentOnFinished?.invoke()
                        true
                    }
                }
                .pointerInput(enabled, valueRange, steps) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        pressed = true
                        val inset = 12.dp.toPx()
                        fun valueAt(x: Float): Float {
                            val usable = (size.width - 2 * inset).coerceAtLeast(1f)
                            return snap(valueRange.start + ((x - inset) / usable).coerceIn(0f, 1f) * span)
                        }
                        fun emit(x: Float) {
                            val next = valueAt(x)
                            if (next != currentValue) {
                                currentOnChange(next)
                                haptics.tick()
                            }
                        }
                        emit(down.position.x)
                        horizontalDrag(down.id) { change ->
                            change.consume()
                            emit(change.position.x)
                        }
                        pressed = false
                        currentOnFinished?.invoke()
                    }
                },
        ) {
            Canvas(Modifier.fillMaxWidth().height(48.dp)) {
                val inset = 12.dp.toPx()
                val trackHeight = 6.dp.toPx()
                val left = inset
                val width = size.width - 2 * inset
                val centerY = size.height / 2f
                val radius = CornerRadius(trackHeight / 2f)
                drawRoundRect(track, Offset(left, centerY - trackHeight / 2f), Size(width, trackHeight), radius)
                val thumbX = left + width * fraction
                drawRoundRect(active, Offset(left, centerY - trackHeight / 2f), Size(thumbX - left, trackHeight), radius)
                val r = thumbRadius.toPx()
                drawCircle(thumbRing, r + 2.dp.toPx(), Offset(thumbX, centerY))
                drawCircle(active, r, Offset(thumbX, centerY))
                drawCircle(thumbRing, r * 0.36f, Offset(thumbX, centerY))
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 52.dp).testTag("app-slider-value"),
        )
    }
}
