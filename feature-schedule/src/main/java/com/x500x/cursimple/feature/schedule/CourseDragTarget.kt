package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import kotlin.math.roundToInt

internal data class CourseDragTarget(
    val dayIndex: Int,
    val rowIndex: Int,
    val isValid: Boolean,
)

/** Round drag displacement into cells and clamp the entire course span within the grid. */
internal fun resolveCourseDragTarget(
    startDayIndex: Int,
    startRowIndex: Int,
    rowSpan: Int,
    dragOffsetX: Float,
    dragOffsetY: Float,
    dayColumnWidthPx: Float,
    slotHeightPx: Float,
    dayColumnCount: Int,
    slotCount: Int,
    occupiedByOthers: Set<Pair<Int, Int>>,
): CourseDragTarget {
    val maxRowIndex = (slotCount - rowSpan).coerceAtLeast(0)
    val dayShift = if (dayColumnWidthPx > 0f) (dragOffsetX / dayColumnWidthPx).roundToInt() else 0
    val rowShift = if (slotHeightPx > 0f) (dragOffsetY / slotHeightPx).roundToInt() else 0
    val dayIndex = (startDayIndex + dayShift).coerceIn(0, (dayColumnCount - 1).coerceAtLeast(0))
    val rowIndex = (startRowIndex + rowShift).coerceIn(0, maxRowIndex)
    val fits = rowIndex + rowSpan <= slotCount
    val clear = (0 until rowSpan).none { (dayIndex to (rowIndex + it)) in occupiedByOthers }
    return CourseDragTarget(dayIndex = dayIndex, rowIndex = rowIndex, isValid = fits && clear)
}

internal fun CourseDragTarget.isMoveFrom(startDayIndex: Int, startRowIndex: Int): Boolean =
    isValid && (dayIndex != startDayIndex || rowIndex != startRowIndex)

/** Map cells through [columnDayOfWeeks] and [slots]; return null outside the grid. */
internal fun movedCourseTime(
    target: CourseDragTarget,
    rowSpan: Int,
    columnDayOfWeeks: List<Int>,
    slots: List<DisplaySlot>,
): CourseTimeSlot? {
    val dayOfWeek = columnDayOfWeeks.getOrNull(target.dayIndex) ?: return null
    return courseTimeAt(dayOfWeek, target.rowIndex, rowSpan, slots)
}

internal fun courseTimeAt(
    dayOfWeek: Int,
    rowIndex: Int,
    rowSpan: Int,
    slots: List<DisplaySlot>,
): CourseTimeSlot? {
    val first = slots.getOrNull(rowIndex) ?: return null
    val last = slots.getOrNull(rowIndex + rowSpan - 1) ?: return null
    return CourseTimeSlot(
        dayOfWeek = dayOfWeek,
        startNode = first.startNode,
        endNode = last.endNode,
    )
}

internal enum class CourseResizeEdge { Top, Bottom }

internal data class CourseResizeTarget(
    val rowIndex: Int,
    val rowSpan: Int,
    val isValid: Boolean,
)

/** Resize within grid bounds, retain at least one row and reject newly overlapping rows. */
internal fun resolveCourseResizeTarget(
    startRowIndex: Int,
    rowSpan: Int,
    edge: CourseResizeEdge,
    dragOffsetY: Float,
    slotHeightPx: Float,
    slotCount: Int,
    dayIndex: Int,
    occupiedByOthers: Set<Pair<Int, Int>>,
): CourseResizeTarget {
    val rowShift = if (slotHeightPx > 0f) (dragOffsetY / slotHeightPx).roundToInt() else 0
    val bottomExclusive = startRowIndex + rowSpan
    val (newStart, newEnd) = when (edge) {
        CourseResizeEdge.Top -> {
            val start = (startRowIndex + rowShift).coerceIn(0, bottomExclusive - 1)
            start to bottomExclusive
        }

        CourseResizeEdge.Bottom -> {
            val end = (bottomExclusive + rowShift).coerceIn(startRowIndex + 1, slotCount)
            startRowIndex to end
        }
    }
    val newSpan = (newEnd - newStart).coerceAtLeast(1)
    val fits = newStart >= 0 && newStart + newSpan <= slotCount
    // Check only newly occupied rows.
    val added = (newStart until newStart + newSpan).filter { it !in startRowIndex until bottomExclusive }
    val clear = added.none { (dayIndex to it) in occupiedByOthers }
    return CourseResizeTarget(rowIndex = newStart, rowSpan = newSpan, isValid = fits && clear)
}

internal fun CourseResizeTarget.isResizeFrom(startRowIndex: Int, startRowSpan: Int): Boolean =
    isValid && (rowIndex != startRowIndex || rowSpan != startRowSpan)

internal fun resizedCourseTime(
    target: CourseResizeTarget,
    dayOfWeek: Int,
    slots: List<DisplaySlot>,
): CourseTimeSlot? = courseTimeAt(dayOfWeek, target.rowIndex, target.rowSpan, slots)

/** Exclude the dragged course from occupancy so it cannot block its own position. */
internal fun occupiedCellsExcluding(
    entries: List<CourseRenderEntry>,
    excludedCourseId: String,
): Set<Pair<Int, Int>> = buildSet {
    entries
        .filterNot { it.course.id == excludedCourseId }
        .forEach { entry ->
            val placement = entry.placement
            for (row in 0 until placement.rowSpan) {
                add(placement.dayIndex to (placement.rowIndex + row))
            }
        }
}
