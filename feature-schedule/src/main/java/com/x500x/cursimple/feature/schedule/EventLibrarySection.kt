package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal enum class LibraryTab { Courses, Events }

/** Next occurrence for sorting; null after every occurrence has passed. */
internal fun ScheduleEvent.nextOccurrence(today: LocalDate): LocalDate? {
    val first = localDate ?: return null
    if (weekDates.isNotEmpty()) return pickedDates.firstOrNull { !it.isBefore(today) }
    if (!repeatWeekly) return first.takeIf { !it.isBefore(today) }
    val candidate = if (!first.isBefore(today)) {
        first
    } else {
        val weeks = (ChronoUnit.DAYS.between(first, today) + 6) / 7
        first.plusWeeks(weeks)
    }
    val until = repeatUntil?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    return candidate.takeIf { until == null || !it.isAfter(until) }
}

internal fun matchesEventQuery(event: ScheduleEvent, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return event.title.contains(q, ignoreCase = true) ||
        event.location.contains(q, ignoreCase = true) ||
        event.note.contains(q, ignoreCase = true)
}

@Composable
internal fun EventLibraryList(
    events: List<ScheduleEvent>,
    today: LocalDate,
    onEdit: (ScheduleEvent) -> Unit,
    onDelete: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var pendingDelete by remember { mutableStateOf<ScheduleEvent?>(null) }
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.schedule_event_delete_title)) },
            text = {
                Text(
                    stringResource(
                        if (target.isRecurring) {
                            R.string.schedule_event_delete_body_repeat
                        } else {
                            R.string.schedule_event_delete_body
                        },
                        target.title,
                    ),
                )
            },
            confirmButton = {
                AppOutlinedButton(onClick = {
                    onDelete(target.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.schedule_action_delete)) }
            },
            dismissButton = {
                AppOutlinedButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.schedule_action_cancel))
                }
            },
        )
    }

    val sorted = remember(events, today) {
        val (upcoming, past) = events.partition { it.nextOccurrence(today) != null }
        upcoming.sortedWith(compareBy({ it.nextOccurrence(today) }, { it.startMinute })) +
            past.sortedWith(compareByDescending<ScheduleEvent> { it.localDate }.thenBy { it.startMinute })
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(sorted, key = { it.id }) { event ->
            val next = event.nextOccurrence(today)
            EventLibraryRow(
                event = event,
                shownDate = next ?: event.localDate,
                past = next == null,
                onEdit = { onEdit(event) },
                onDelete = { pendingDelete = event },
            )
        }
    }
}

@Composable
private fun EventLibraryRow(
    event: ScheduleEvent,
    shownDate: LocalDate?,
    past: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = eventColors(event)
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (past) 0.55f else 1f),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(colors.container)
                    .border(1.dp, colors.onContainer.copy(alpha = 0.4f), CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val whenText = buildString {
                    shownDate?.let { append(formatPickerDate(it)).append("  ") }
                    append(event.timeRangeText())
                }
                Text(
                    text = whenText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val extra = listOfNotNull(
                    if (event.isRecurring) repeatText(event) else null,
                    event.location.takeIf { it.isNotBlank() },
                    if (past) stringResource(R.string.schedule_event_past) else null,
                ).joinToString(" · ")
                if (extra.isNotEmpty()) {
                    Text(
                        text = extra,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box {
                AppCircleIconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.memo_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.schedule_event_edit)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.schedule_action_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}
