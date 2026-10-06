package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.x500x.cursimple.core.data.widget.classSlotLabelText
import com.x500x.cursimple.core.data.widget.courseSlotLabelText
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.coursesScheduledOn
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import com.x500x.cursimple.core.kernel.model.visibleScheduleCourses
import java.time.LocalDate

private val CELL_HEIGHT = 64.dp
private val NODE_COLUMN_WIDTH = 44.dp

/** Drag state uses root [pointer], initial [grab] offset and original card [size]. */
private data class SwapDrag(
    val course: CourseItem,
    val fromLeft: Boolean,
    val pointer: Offset,
    val grab: Offset,
    val size: IntSize,
)

private data class PendingSwap(
    val course: CourseItem,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val startNode: Int,
    val endNode: Int,
)

/** Two-day full-screen move editor with empty slots and confirmation before applying moves. */
@Composable
fun CourseSwapScreen(
    leftDate: LocalDate,
    rightDate: LocalDate,
    timingProfile: TermTimingProfile?,
    termStartDate: LocalDate?,
    allCourses: List<CourseItem>,
    overrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings,
    onPickLeftDate: () -> Unit,
    onPickRightDate: () -> Unit,
    onMove: (course: CourseItem, from: LocalDate, to: LocalDate, startNode: Int, endNode: Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val slots = remember(timingProfile) {
        timingProfile?.slotTimes
            ?.filter { it.endNode >= it.startNode }
            ?.sortedWith(compareBy({ it.startNode }, { it.endNode }))
            .orEmpty()
    }
    val context = LocalContext.current
    val slotLabels = remember(slots, context) {
        slots.mapIndexed { index, slot -> context.classSlotLabelText(slot, index + 1) }
    }
    val visible = remember(allCourses) { allCourses.visibleScheduleCourses() }
    val leftCourses = remember(visible, leftDate, overrides, holidayCalendar, termStartDate) {
        coursesScheduledOn(leftDate, visible, overrides, holidayCalendar, termStartDate)
    }
    val rightCourses = remember(visible, rightDate, overrides, holidayCalendar, termStartDate) {
        coursesScheduledOn(rightDate, visible, overrides, holidayCalendar, termStartDate)
    }

    val cellBounds = remember { mutableStateMapOf<Pair<Boolean, Int>, Rect>() }
    var drag by remember { mutableStateOf<SwapDrag?>(null) }
    var pending by remember { mutableStateOf<PendingSwap?>(null) }
    val density = LocalDensity.current
    // Subtract container root offset before drawing the drag ghost.
    var dragLayerOrigin by remember { mutableStateOf(Offset.Zero) }
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.schedule_action_close),
                )
            }
            Text(
                text = stringResource(R.string.schedule_swap_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Text(
            text = if (slots.isEmpty()) {
                stringResource(R.string.schedule_swap_no_timing)
            } else {
                stringResource(R.string.schedule_swap_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (slots.isEmpty()) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        val leftHoliday = remember(leftDate, overrides, holidayCalendar) {
            resolveScheduleDay(leftDate, overrides, holidayCalendar).takeIf { it.isHoliday }
        }
        val rightHoliday = remember(rightDate, overrides, holidayCalendar) {
            resolveScheduleDay(rightDate, overrides, holidayCalendar).takeIf { it.isHoliday }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Spacer(Modifier.width(NODE_COLUMN_WIDTH))
            SwapDayHeader(
                date = leftDate,
                enabled = true,
                holidayName = leftHoliday?.let {
                    it.holidayName?.takeIf { n -> n.isNotBlank() }
                        ?: stringResource(R.string.schedule_holiday_unnamed)
                },
                onClick = onPickLeftDate,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            SwapDayHeader(
                date = rightDate,
                enabled = true,
                holidayName = rightHoliday?.let {
                    it.holidayName?.takeIf { n -> n.isNotBlank() }
                        ?: stringResource(R.string.schedule_holiday_unnamed)
                },
                onClick = onPickRightDate,
                modifier = Modifier.weight(1f),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { dragLayerOrigin = it.positionInRoot() },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState, enabled = drag == null)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Column(modifier = Modifier.width(NODE_COLUMN_WIDTH)) {
                    slots.forEachIndexed { index, slot ->
                        Column(
                            modifier = Modifier.fillMaxWidth().height(CELL_HEIGHT),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = slotLabels.getOrElse(index) { nodeLabelOf(slot) },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                            if (slot.startTime.isNotBlank()) {
                                Text(
                                    text = slot.startTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                SwapDayColumn(
                    isLeft = true,
                    slots = slots,
                    courses = leftCourses,
                    draggable = true,
                    dragging = drag,
                    cellBounds = cellBounds,
                    onDragStateChange = { drag = it },
                    onDrop = { d -> pending = resolveDrop(d, cellBounds, slots, leftDate, rightDate) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                SwapDayColumn(
                    isLeft = false,
                    slots = slots,
                    courses = rightCourses,
                    draggable = true,
                    dragging = drag,
                    cellBounds = cellBounds,
                    onDragStateChange = { drag = it },
                    onDrop = { d -> pending = resolveDrop(d, cellBounds, slots, leftDate, rightDate) },
                    modifier = Modifier.weight(1f),
                )
            }

            // Preserve card size and grab offset so the drag ghost follows the pointer without jumping.
            drag?.let { d ->
                SwapCourseCard(
                    course = d.course,
                    elevated = true,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (d.pointer.x - d.grab.x - dragLayerOrigin.x).toInt(),
                                (d.pointer.y - d.grab.y - dragLayerOrigin.y).toInt(),
                            )
                        }
                        .size(
                            width = with(density) { d.size.width.toDp() },
                            height = with(density) { d.size.height.toDp() },
                        ),
                )
            }
        }

    }

    pending?.let { swap ->
        val targetHoliday = remember(swap.toDate, overrides, holidayCalendar) {
            resolveScheduleDay(swap.toDate, overrides, holidayCalendar)
                .takeIf { it.isHoliday }
        }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.schedule_swap_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            R.string.schedule_swap_confirm_body,
                            swap.course.title,
                            formatSwapDate(swap.toDate),
                            nodeSpanText(swap.startNode, swap.endNode),
                        ).let { numbered ->
                            context.courseSlotLabelText(timingProfile, swap.startNode, swap.endNode)
                                ?.let { name ->
                                    stringResource(
                                        R.string.schedule_swap_confirm_body_slot,
                                        swap.course.title,
                                        formatSwapDate(swap.toDate),
                                        name,
                                    )
                                }
                                ?: numbered
                        },
                    )
                    targetHoliday?.let { holiday ->
                        Text(
                            text = stringResource(
                                R.string.schedule_swap_confirm_holiday,
                                holiday.holidayName?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.schedule_holiday_unnamed),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                AppOutlinedButton(onClick = {
                    onMove(swap.course, swap.fromDate, swap.toDate, swap.startNode, swap.endNode)
                    pending = null
                }) { Text(stringResource(R.string.schedule_swap_confirm_action)) }
            },
            dismissButton = {
                AppOutlinedButton(onClick = { pending = null }) {
                    Text(stringResource(R.string.schedule_action_cancel))
                }
            },
        )
    }
}

/** Show holiday guidance without preventing explicit moves into the date. */
@Composable
private fun SwapDayHeader(
    date: LocalDate,
    holidayName: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (holidayName != null) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
    ) {
        val onColor = if (holidayName != null) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = formatSwapDate(date),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = onColor,
            )
            Text(
                text = holidayName
                    ?: if (enabled) stringResource(R.string.schedule_swap_change_day) else "",
                style = MaterialTheme.typography.labelSmall,
                color = onColor.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SwapDayColumn(
    isLeft: Boolean,
    slots: List<ClassSlotTime>,
    courses: List<CourseItem>,
    draggable: Boolean,
    dragging: SwapDrag?,
    cellBounds: MutableMap<Pair<Boolean, Int>, Rect>,
    onDragStateChange: (SwapDrag?) -> Unit,
    onDrop: (SwapDrag) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        slots.forEachIndexed { index, slot ->
            // Draw a spanning course only at its first row.
            val starting = courses.filter { it.time.startNode in slot.startNode..slot.endNode }
            val hovered = dragging != null &&
                dragging.fromLeft != isLeft &&
                targetCell(dragging.pointer, isLeft, cellBounds) == index
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CELL_HEIGHT)
                    .padding(2.dp)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInRoot()
                        cellBounds[isLeft to index] = Rect(
                            pos.x,
                            pos.y,
                            pos.x + coords.size.width,
                            pos.y + coords.size.height,
                        )
                    }
                    .background(
                        color = if (hovered) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        shape = RoundedCornerShape(8.dp),
                    ),
            ) {
                starting.forEach { course ->
                    SwapCourseBlock(
                        course = course,
                        isLeft = isLeft,
                        draggable = draggable,
                        beingDragged = dragging?.course?.id == course.id && dragging.fromLeft == isLeft,
                        onDragStateChange = onDragStateChange,
                        onDrop = onDrop,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwapCourseBlock(
    course: CourseItem,
    isLeft: Boolean,
    draggable: Boolean,
    beingDragged: Boolean,
    onDragStateChange: (SwapDrag?) -> Unit,
    onDrop: (SwapDrag) -> Unit,
) {
    var blockRoot by remember { mutableStateOf(Offset.Zero) }
    var blockSize by remember { mutableStateOf(IntSize.Zero) }
    var current by remember { mutableStateOf<SwapDrag?>(null) }
    val gesture = if (!draggable) {
        Modifier
    } else {
        Modifier.pointerInput(course.id, isLeft) {
            detectDragGesturesAfterLongPress(
                onDragStart = { local ->
                    val state = SwapDrag(
                        course = course,
                        fromLeft = isLeft,
                        pointer = blockRoot + local,
                        grab = local,
                        size = blockSize,
                    )
                    current = state
                    onDragStateChange(state)
                },
                onDrag = { change, delta ->
                    change.consume()
                    val next = current?.let { it.copy(pointer = it.pointer + delta) }
                    current = next
                    onDragStateChange(next)
                },
                onDragEnd = {
                    current?.let(onDrop)
                    current = null
                    onDragStateChange(null)
                },
                onDragCancel = {
                    current = null
                    onDragStateChange(null)
                },
            )
        }
    }
    SwapCourseCard(
        course = course,
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (beingDragged) 0.35f else 1f)
            .onGloballyPositioned {
                blockRoot = it.positionInRoot()
                blockSize = it.size
            }
            .then(gesture),
    )
}

@Composable
private fun SwapCourseCard(
    course: CourseItem,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
) {
    val accents = com.x500x.cursimple.feature.schedule.theme.LocalScheduleAccents.current
    val palette = remember(course.title, accents) { courseColor(course.title, accents.coursePalette) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = palette.container,
        shadowElevation = if (elevated) 8.dp else 0.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = course.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.onContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (course.location.isNotBlank()) {
                Text(
                    text = course.location,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Snap near cell gaps while rejecting pointers too far horizontally from the destination
 * column.
 */
private fun targetCell(
    pointer: Offset,
    targetIsLeft: Boolean,
    cellBounds: Map<Pair<Boolean, Int>, Rect>,
): Int? {
    val candidates = cellBounds.entries.filter { it.key.first == targetIsLeft }
    if (candidates.isEmpty()) return null
    candidates.firstOrNull { it.value.contains(pointer) }?.let { return it.key.second }

    fun gap(low: Float, high: Float, value: Float) = when {
        value < low -> low - value
        value > high -> value - high
        else -> 0f
    }
    val nearest = candidates.minByOrNull { (_, rect) ->
        gap(rect.left, rect.right, pointer.x) * 2f + gap(rect.top, rect.bottom, pointer.y)
    } ?: return null
    val rect = nearest.value
    val slack = rect.width * 0.5f
    if (pointer.x < rect.left - slack || pointer.x > rect.right + slack) return null
    return nearest.key.second
}

private fun resolveDrop(
    drag: SwapDrag,
    cellBounds: Map<Pair<Boolean, Int>, Rect>,
    slots: List<ClassSlotTime>,
    leftDate: LocalDate,
    rightDate: LocalDate,
): PendingSwap? {
    val targetIsLeft = !drag.fromLeft
    val index = targetCell(drag.pointer, targetIsLeft, cellBounds) ?: return null
    val slot = slots.getOrNull(index) ?: return null
    val span = (drag.course.time.endNode - drag.course.time.startNode).coerceAtLeast(0)
    return PendingSwap(
        course = drag.course,
        fromDate = if (drag.fromLeft) leftDate else rightDate,
        toDate = if (targetIsLeft) leftDate else rightDate,
        startNode = slot.startNode,
        endNode = slot.startNode + span,
    )
}

private fun nodeLabelOf(slot: ClassSlotTime): String = nodeSpanText(slot.startNode, slot.endNode)

private fun nodeSpanText(startNode: Int, endNode: Int): String =
    if (startNode == endNode) "$startNode" else "$startNode-$endNode"

private fun formatSwapDate(date: LocalDate): String = "${date.monthValue}/${date.dayOfMonth}"
