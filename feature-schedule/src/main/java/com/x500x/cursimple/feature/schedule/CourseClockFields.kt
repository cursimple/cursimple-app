package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.typedClock
import com.x500x.cursimple.feature.plugin.ui.AppFilterChip

private data class SlotClockRange(val slot: ClassSlotTime, val start: Int, val end: Int)

private fun List<ClassSlotTime>.timed(): List<SlotClockRange> = mapNotNull { slot ->
    val start = parseMinuteOfDay(slot.startTime) ?: return@mapNotNull null
    val end = parseMinuteOfDay(slot.endTime) ?: return@mapNotNull null
    SlotClockRange(slot, start, end).takeIf { end > start && slot.endNode >= slot.startNode }
}

/**
 * Periods a typed range overlaps. Inside a break it keeps the closest period so the course
 * still has a row for conflicts and quick-add.
 */
internal fun nodesForClock(slots: List<ClassSlotTime>, startMinute: Int, endMinute: Int): Pair<Int, Int>? {
    if (endMinute <= startMinute) return null
    val timed = slots.timed()
    val overlapping = timed.filter { it.start < endMinute && it.end > startMinute }
    if (overlapping.isNotEmpty()) {
        return overlapping.minOf { it.slot.startNode } to overlapping.maxOf { it.slot.endNode }
    }
    val closest = timed.minByOrNull { if (it.end <= startMinute) startMinute - it.end else it.start - endMinute }
        ?: return null
    // A break period maps to the single node next to the typed time.
    val node = if (closest.end <= startMinute) closest.slot.endNode else closest.slot.startNode
    return node to node
}

/** The clock range [startNode]–[endNode] covers, used to prefill the typed times. */
internal fun clockForNodes(slots: List<ClassSlotTime>, startNode: Int, endNode: Int): Pair<Int, Int>? {
    val timed = slots.timed()
    val start = timed.firstOrNull { startNode in it.slot.startNode..it.slot.endNode } ?: return null
    val end = timed.firstOrNull { endNode in it.slot.startNode..it.slot.endNode } ?: return null
    return (start.start to end.end).takeIf { it.second > it.first }
}

/** Moving a course onto other periods means the user now wants those periods, not the typed time. */
internal fun CourseItem.movedTo(time: CourseTimeSlot): CourseItem {
    val samePeriods = time.startNode == this.time.startNode && time.endNode == this.time.endNode
    return if (reminderOnly || samePeriods || typedClock() == null) copy(time = time)
    else copy(time = time, reminderStartTime = null, reminderEndTime = null)
}

@Composable
internal fun CourseTimeModeChips(byClock: Boolean, onByClock: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppFilterChip(
            selected = !byClock,
            onClick = { onByClock(false) },
            label = { Text(stringResource(R.string.schedule_course_time_by_node)) },
            modifier = Modifier.testTag("course-time-by-node"),
        )
        AppFilterChip(
            selected = byClock,
            onClick = { onByClock(true) },
            label = { Text(stringResource(R.string.schedule_course_time_by_clock)) },
            modifier = Modifier.testTag("course-time-by-clock"),
        )
    }
}

/** Start and end buttons for typed course times; each opens the shared time picker. */
@Composable
internal fun CourseClockFields(
    startMinute: Int,
    endMinute: Int,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val valid = endMinute > startMinute
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val start = @Composable { modifier: Modifier ->
                TimeValueButton(stringResource(R.string.schedule_placeholder_start_label), startMinute,
                    { picking = true }, modifier.testTag("course-clock-start"))
            }
            val end = @Composable { modifier: Modifier ->
                TimeValueButton(stringResource(R.string.schedule_placeholder_end_label), endMinute,
                    { picking = false }, modifier.testTag("course-clock-end"))
            }
            if (maxWidth < 200.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    start(Modifier.fillMaxWidth())
                    end(Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    start(Modifier.weight(1f))
                    end(Modifier.weight(1f))
                }
            }
        }
        Text(
            text = stringResource(if (valid) R.string.schedule_course_clock_hint else R.string.schedule_placeholder_time_error),
            style = MaterialTheme.typography.bodySmall,
            color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
    }
    picking?.let { isStart ->
        EventTimePickerDialog(
            initialMinute = if (isStart) startMinute else endMinute,
            onDismiss = { picking = null },
            onPick = { minute ->
                if (isStart) {
                    // Keep the length when moving the start, like the event editor.
                    val length = (endMinute - startMinute).takeIf { it > 0 } ?: DEFAULT_CLOCK_LENGTH
                    onStartChange(minute)
                    onEndChange((minute + length).coerceAtMost(23 * 60 + 59))
                } else {
                    onEndChange(minute)
                }
                picking = null
            },
        )
    }
}

internal const val DEFAULT_CLOCK_START = 8 * 60
internal const val DEFAULT_CLOCK_LENGTH = 95
