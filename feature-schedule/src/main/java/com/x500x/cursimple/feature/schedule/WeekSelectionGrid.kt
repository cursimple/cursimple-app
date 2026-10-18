package com.x500x.cursimple.feature.schedule

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
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
    /** Second line under each number; cells widen to fill the row when labels are shown. */
    cellLabel: ((Int) -> String)? = null,
) {
    val sorted = remember(weeks) { weeks.filter { it > 0 }.distinct().sorted() }
    val latestSelected by rememberUpdatedState(selected)
    val latestChange by rememberUpdatedState(onChange)
    val bounds = remember(sorted) { mutableMapOf<Int, Rect>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val spacing = 6.dp
        val cellWidth = if (cellLabel == null) 48.dp else {
            val columns = ((maxWidth + spacing) / (52.dp + spacing)).toInt().coerceAtLeast(4)
            (maxWidth - spacing * (columns - 1)) / columns
        }
        val cellHeight = if (cellLabel == null) 48.dp else 56.dp
        FlowRow(
            modifier = Modifier.fillMaxWidth().testTag("week-selection-grid")
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
            horizontalArrangement = Arrangement.spacedBy(spacing), verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            sorted.forEach { week ->
                val picked = week in selected
                val custom = week in customized
                val label = stringResource(R.string.schedule_week_location_week, week)
                val caption = cellLabel?.invoke(week)
                val container by animateColorAsState(
                    when {
                        picked -> MaterialTheme.colorScheme.primary
                        custom -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    tween(160),
                    label = "week_cell",
                )
                val content = when {
                    picked -> MaterialTheme.colorScheme.onPrimary
                    custom -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    modifier = Modifier.size(cellWidth, cellHeight).onGloballyPositioned { bounds[week] = it.boundsInRoot() }
                        .testTag("week-select-$week")
                        .toggleable(picked, role = Role.Checkbox, onValueChange = {
                            onChange(if (it) selected + week else selected - week)
                        }).semantics { contentDescription = if (caption == null) label else "$label $caption" },
                    shape = RoundedCornerShape(10.dp),
                    color = container,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 3.dp),
                    ) {
                        Text(week.toString(), style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (custom) FontWeight.Bold else FontWeight.Normal, color = content)
                        if (caption != null) {
                            Text(
                                caption,
                                style = MaterialTheme.typography.labelSmall,
                                color = content.copy(alpha = if (custom || picked) 1f else 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Batch edits affect only selected teaching weeks and keep unrelated overrides intact. */
internal fun batchWeekValues(
    overrides: Map<Int, String>,
    available: List<Int>,
    selected: Set<Int>,
    value: String,
    defaultValue: String = "",
): Map<Int, String> =
    overrides.toMutableMap().apply {
        val trimmed = value.trim()
        val trimmedDefault = defaultValue.trim()
        selected.filter { it in available }.forEach { week ->
            if (trimmed.isBlank() || (trimmedDefault.isNotBlank() && trimmed == trimmedDefault)) {
                remove(week)
            } else {
                put(week, trimmed)
            }
        }
    }

/** What a week shows: its own override when it has one, otherwise the course default. */
internal fun effectiveWeekValue(overrides: Map<Int, String>, week: Int, defaultValue: String = ""): String =
    overrides[week]?.trim()?.takeIf(String::isNotEmpty) ?: defaultValue.trim()

/** The value every selected week shares, or null when the selection is empty or the weeks differ. */
internal fun commonWeekValue(overrides: Map<Int, String>, weeks: Set<Int>, defaultValue: String = ""): String? =
    weeks.map { effectiveWeekValue(overrides, it, defaultValue) }.distinct().singleOrNull()

/** Weeks grouped by what they show, in order of the first week that shows each value. */
internal fun groupWeeksByValue(
    overrides: Map<Int, String>,
    available: List<Int>,
    defaultValue: String = "",
): List<Pair<String, List<Int>>> =
    available.sorted().groupBy { effectiveWeekValue(overrides, it, defaultValue) }.toList()
