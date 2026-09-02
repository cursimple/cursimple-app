package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import kotlin.math.roundToInt

/** 拖动落点所在的格子，以及该落点是否可以放下。 */
internal data class CourseDragTarget(
    val dayIndex: Int,
    val rowIndex: Int,
    val isValid: Boolean,
)

/**
 * 把累计的拖动位移换算成落点格子。位移按列宽和行高四舍五入取整，
 * 落点被夹在网格范围内，使课程块整体不会越出边界。
 */
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

/** 落点与起点相同时不构成移动。 */
internal fun CourseDragTarget.isMoveFrom(startDayIndex: Int, startRowIndex: Int): Boolean =
    isValid && (dayIndex != startDayIndex || rowIndex != startRowIndex)

/**
 * 把落点格子换算成课程的星期与节次。
 * [columnDayOfWeeks] 按列序给出每列的星期值，[slots] 决定跨行课程的首尾节次。
 * 落点超出可用范围时返回 null。
 */
internal fun movedCourseTime(
    target: CourseDragTarget,
    rowSpan: Int,
    columnDayOfWeeks: List<Int>,
    slots: List<DisplaySlot>,
): CourseTimeSlot? {
    val dayOfWeek = columnDayOfWeeks.getOrNull(target.dayIndex) ?: return null
    val first = slots.getOrNull(target.rowIndex) ?: return null
    val last = slots.getOrNull(target.rowIndex + rowSpan - 1) ?: return null
    return CourseTimeSlot(
        dayOfWeek = dayOfWeek,
        startNode = first.startNode,
        endNode = last.endNode,
    )
}

/**
 * 网格中除 [excludedCourseId] 之外的课程占用的格子。
 * 被拖动的课程本身要排除掉，否则它会挡住自己原来的位置。
 */
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
