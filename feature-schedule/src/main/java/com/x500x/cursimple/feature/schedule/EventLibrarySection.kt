package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.feature.plugin.ui.appItemMotion
import com.x500x.cursimple.feature.plugin.ui.pressFeedback
import com.x500x.cursimple.feature.plugin.ui.rememberAppHaptics
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.x500x.cursimple.feature.plugin.ui.AppDialogTone
import com.x500x.cursimple.feature.plugin.ui.AppDialogDangerButton
import com.x500x.cursimple.feature.plugin.ui.AppAlertDialog
import com.x500x.cursimple.feature.plugin.ui.AppDialogSecondaryButton
import kotlin.math.abs

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

internal enum class EventSourceKind {
    Plugin,
    UserCreated,
}

internal fun ScheduleEvent.eventSourceKind(): EventSourceKind =
    if (source == null) EventSourceKind.UserCreated else EventSourceKind.Plugin

internal fun filterLibraryEvents(
    events: List<ScheduleEvent>,
    selectedSources: Set<EventSourceKind>,
): List<ScheduleEvent> = events.filter { it.eventSourceKind() in selectedSources }

internal fun matchesEventQuery(event: ScheduleEvent, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return event.title.contains(q, ignoreCase = true) ||
        event.location.contains(q, ignoreCase = true) ||
        event.note.contains(q, ignoreCase = true)
}

internal fun chronologicalLibraryEvents(events: List<ScheduleEvent>, today: LocalDate): List<ScheduleEvent> {
    val (upcoming, past) = events.partition { it.nextOccurrence(today) != null }
    return upcoming.sortedWith(compareBy({ it.nextOccurrence(today) }, { it.startMinute })) +
        past.sortedWith(compareByDescending<ScheduleEvent> { it.localDate }.thenBy { it.startMinute })
}

/** Reorder matching events in their existing slots in the full list. */
internal fun moveLibraryEvent(
    events: List<ScheduleEvent>,
    matching: List<ScheduleEvent>,
    sourceId: String,
    targetId: String,
): List<String> {
    val sourceIndex = matching.indexOfFirst { it.id == sourceId }
    val targetIndex = matching.indexOfFirst { it.id == targetId }
    if (sourceIndex == targetIndex || sourceIndex < 0 || targetIndex < 0) return emptyList()
    val matchingIds = matching.map { it.id }
    val moved = matchingIds.toMutableList().apply { add(targetIndex, removeAt(sourceIndex)) }.iterator()
    val matchingSet = matchingIds.toHashSet()
    return events.map { if (it.id in matchingSet) moved.next() else it.id }
}

private data class EventLibraryDrag(
    val id: String,
    val index: Int,
    val size: Int,
    val grabY: Float,
    val fingerY: Float,
) {
    val top: Float get() = fingerY - grabY
    val center: Float get() = top + size / 2f
}

private fun LazyListLayoutInfo.dropTarget(drag: EventLibraryDrag): String? {
    val above = visibleItemsInfo.firstOrNull { item ->
        item.index < drag.index && drag.center < item.offset + item.size / 2f
    }
    val below = visibleItemsInfo.lastOrNull { item ->
        item.index > drag.index && drag.center > item.offset + item.size / 2f
    }
    return (above ?: below)?.key as? String
}

@Composable
internal fun EventLibraryList(
    events: List<ScheduleEvent>,
    today: LocalDate,
    selectedIds: Set<String>,
    reorderEnabled: Boolean,
    onSelect: (String) -> Unit,
    onClearDragSelection: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onMove: (String, String) -> Unit,
    onEdit: (ScheduleEvent) -> Unit,
    onDelete: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val state = rememberLazyListState()
    val haptics = rememberAppHaptics()
    val selecting = selectedIds.isNotEmpty()
    val eventIds = remember(events) { events.map { it.id } }
    var drag by remember(eventIds) { mutableStateOf<EventLibraryDrag?>(null) }
    val edge = with(LocalDensity.current) { 48.dp.toPx() }
    LaunchedEffect(drag?.id) {
        if (drag == null) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val held = drag ?: break
            val layout = state.layoutInfo
            val distance = when {
                held.fingerY < layout.viewportStartOffset + edge ->
                    held.fingerY - (layout.viewportStartOffset + edge)
                held.fingerY > layout.viewportEndOffset - edge ->
                    held.fingerY - (layout.viewportEndOffset - edge)
                else -> 0f
            }
            if (distance != 0f) {
                val frameSeconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.05f)
                state.scrollBy(distance.coerceIn(-edge, edge) * 14 * frameSeconds)
            }
            previousFrame = frame
        }
    }
    var pendingDelete by remember { mutableStateOf<ScheduleEvent?>(null) }
    pendingDelete?.let { target ->
        AppAlertDialog(
            onDismissRequest = { pendingDelete = null },
            tone = AppDialogTone.Danger,
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(22.dp)) },
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
                AppDialogDangerButton(onClick = {
                    onDelete(target.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.schedule_action_delete)) }
            },
            dismissButton = {
                AppDialogSecondaryButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.schedule_action_cancel))
                }
            },
        )
    }

    val targetId = drag?.let { state.layoutInfo.dropTarget(it) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(events, key = { _, event -> event.id }) { index, event ->
            val currentDrag = drag?.takeIf { it.id == event.id }
            var rowTop by remember { mutableStateOf(0f) }
            val pinnable = LocalPinnableContainer.current
            val move = rememberUpdatedState(onMove)
            val select = rememberUpdatedState(onSelect)
            val clearDragSelection = rememberUpdatedState(onClearDragSelection)
            val canReorder = rememberUpdatedState(reorderEnabled)
            val isSelecting = rememberUpdatedState(selecting)
            val gesture = Modifier.pointerInput(event.id, eventIds, pinnable, haptics) {
                var held: EventLibraryDrag? = null
                var movement = Offset.Zero
                var pin: PinnableContainer.PinnedHandle? = null

                fun clearDrag() {
                    pin?.release()
                    pin = null
                    held = null
                    if (drag?.id == event.id) drag = null
                }

                try {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { position ->
                            val item = if (isSelecting.value || !canReorder.value) null else
                                state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == event.id }
                            held = item?.let {
                                EventLibraryDrag(event.id, it.index, it.size, position.y, it.offset + position.y)
                            }
                            if (held != null) pin = pinnable?.pin()
                            movement = Offset.Zero
                            haptics.heavy()
                            select.value(event.id)
                        },
                        onDrag = { _, delta ->
                            movement += delta
                            val start = held
                            if (start != null && canReorder.value &&
                                (drag?.id == event.id ||
                                    (abs(movement.y) > viewConfiguration.touchSlop && abs(movement.y) > abs(movement.x)))
                            ) {
                                if (drag?.id != event.id) clearDragSelection.value(event.id)
                                drag = start.copy(fingerY = start.fingerY + movement.y)
                            }
                        },
                        onDragEnd = {
                            val finished = drag?.takeIf { it.id == event.id }
                            val drop = finished?.let { state.layoutInfo.dropTarget(it) }
                            clearDrag()
                            if (drop != null && canReorder.value) {
                                move.value(event.id, drop)
                            }
                        },
                        onDragCancel = ::clearDrag,
                    )
                } finally {
                    clearDrag()
                }
            }
            val previous = eventIds.getOrNull(index - 1)
            val nextId = eventIds.getOrNull(index + 1)
            Box(
                appItemMotion()
                    .zIndex(if (currentDrag != null) 1f else 0f)
                    .onPlaced { rowTop = it.positionInParent().y }
                    .graphicsLayer {
                        translationY = currentDrag?.let { it.top - state.layoutInfo.viewportStartOffset - rowTop } ?: 0f
                    },
            ) {
                val next = event.nextOccurrence(today)
                EventLibraryRow(
                    event = event,
                    shownDate = next ?: event.localDate,
                    past = next == null,
                    selecting = selecting,
                    isSelected = event.id in selectedIds,
                    highlighted = targetId == event.id || currentDrag != null,
                    dragging = currentDrag != null,
                    gesture = gesture,
                    onClick = { if (selecting) onToggleSelection(event.id) else onEdit(event) },
                    onSelect = { onSelect(event.id) },
                    onMoveUp = if (reorderEnabled && previous != null) ({ onMove(event.id, previous) }) else null,
                    onMoveDown = if (reorderEnabled && nextId != null) ({ onMove(event.id, nextId) }) else null,
                    onDelete = { pendingDelete = event },
                )
            }
        }
    }
}

@Composable
private fun EventLibraryRow(
    event: ScheduleEvent,
    shownDate: LocalDate?,
    past: Boolean,
    selecting: Boolean,
    isSelected: Boolean,
    highlighted: Boolean,
    dragging: Boolean,
    gesture: Modifier,
    onClick: () -> Unit,
    onSelect: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    val colors = eventColors(event)
    val press = remember { MutableInteractionSource() }
    val selectionLabel = stringResource(R.string.library_management_select)
    val moveUp = stringResource(R.string.library_management_move_up)
    val moveDown = stringResource(R.string.library_management_move_down)
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (past && !highlighted && !selecting) 0.55f else 1f)
            .pressFeedback(press),
        shape = shape,
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (highlighted || isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
        shadowElevation = if (dragging) 8.dp else 0.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .testTag("event-library-card")
                    .semantics {
                        if (selecting) selected = isSelected
                        onLongClick(label = selectionLabel) { onSelect(); true }
                        customActions = buildList {
                            onMoveUp?.let { action -> add(CustomAccessibilityAction(moveUp) { action(); true }) }
                            onMoveDown?.let { action -> add(CustomAccessibilityAction(moveDown) { action(); true }) }
                        }
                    }
                    .clickable(interactionSource = press, indication = ripple(), role = Role.Button, onClick = onClick)
                    // The inner detector consumes release after a hold before clickable can handle it.
                    .then(gesture)
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
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
                if (selecting) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.padding(horizontal = 12.dp)) { LibrarySelectionMark(isSelected) }
                }
            }
            if (!selecting) {
                LibraryCardMenu(onEdit = onClick, onSelect = onSelect, onDelete = onDelete, modifier = Modifier.padding(end = 8.dp)) { dismiss ->
                    onMoveUp?.let { action ->
                        LibraryMenuAction(moveUp, Icons.Rounded.ArrowUpward) { dismiss(); action() }
                    }
                    onMoveDown?.let { action ->
                        LibraryMenuAction(moveDown, Icons.Rounded.ArrowDownward) { dismiss(); action() }
                    }
                }
            }
        }
    }
}
