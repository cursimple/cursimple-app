package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A stroke selects or clears the available weeks between its endpoints. */
internal fun dragSelectedWeeks(weeks: List<Int>, initial: Set<Int>, start: Int, end: Int): Set<Int> {
    val from = weeks.indexOf(start)
    val to = weeks.indexOf(end)
    if (from < 0 || to < 0) return initial
    val range = weeks.subList(minOf(from, to), maxOf(from, to) + 1).toSet()
    return if (start in initial) initial - range else initial + range
}

/** Horizontal strokes paint a range; vertical drags remain available to the surrounding scroll. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeekSelectionGrid(
    weeks: List<Int>,
    selected: Set<Int>,
    onChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    customized: Set<Int> = emptySet(),
) {
    val sorted = remember(weeks) { weeks.filter { it > 0 }.distinct().sorted() }
    val latestSelected by rememberUpdatedState(selected)
    val latestChange by rememberUpdatedState(onChange)
    val bounds = remember(sorted) { mutableMapOf<Int, Rect>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    FlowRow(
        modifier = modifier.fillMaxWidth().testTag("week-selection-grid")
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(sorted) {
                fun weekAt(position: Offset) = sorted.firstOrNull { bounds[it]?.contains(position + origin) == true }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = weekAt(down.position) ?: return@awaitEachGesture
                    val initial = latestSelected.toSet()
                    val change = awaitHorizontalTouchSlopOrCancellation(down.id) { pointer, _ -> pointer.consume() }
                        ?: return@awaitEachGesture
                    latestChange(dragSelectedWeeks(sorted, initial, start, weekAt(change.position) ?: start))
                    val completed = drag(change.id) { pointer ->
                        weekAt(pointer.position)?.let { end -> latestChange(dragSelectedWeeks(sorted, initial, start, end)) }
                        pointer.consume()
                    }
                    if (!completed) latestChange(initial)
                }
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        sorted.forEach { week ->
            val picked = week in selected
            val custom = week in customized
            val label = stringResource(R.string.schedule_week_location_week, week)
            Surface(
                modifier = Modifier.size(48.dp).onGloballyPositioned { bounds[week] = it.boundsInRoot() }
                    .testTag("week-select-$week")
                    .toggleable(picked, role = Role.Checkbox, onValueChange = {
                        onChange(if (it) selected + week else selected - week)
                    }).semantics { contentDescription = label },
                shape = RoundedCornerShape(10.dp),
                color = when { picked -> MaterialTheme.colorScheme.primary
                    custom -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(week.toString(), style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (custom) FontWeight.Bold else FontWeight.Normal,
                        color = when { picked -> MaterialTheme.colorScheme.onPrimary
                            custom -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant })
                }
            }
        }
    }
}

/** Batch edits affect only selected teaching weeks and keep unrelated overrides intact. */
internal fun batchWeekValues(overrides: Map<Int, String>, available: List<Int>, selected: Set<Int>, value: String): Map<Int, String> =
    overrides.toMutableMap().apply {
        selected.filter { it in available }.forEach { week ->
            if (value.isBlank()) remove(week) else put(week, value.trim())
        }
    }
