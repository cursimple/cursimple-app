package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseDragTargetTest {

    private val columnWidth = 100f
    private val slotHeight = 60f

    private fun target(
        startDayIndex: Int = 1,
        startRowIndex: Int = 2,
        rowSpan: Int = 2,
        dragOffsetX: Float = 0f,
        dragOffsetY: Float = 0f,
        dayColumnCount: Int = 7,
        slotCount: Int = 10,
        occupiedByOthers: Set<Pair<Int, Int>> = emptySet(),
    ) = resolveCourseDragTarget(
        startDayIndex = startDayIndex,
        startRowIndex = startRowIndex,
        rowSpan = rowSpan,
        dragOffsetX = dragOffsetX,
        dragOffsetY = dragOffsetY,
        dayColumnWidthPx = columnWidth,
        slotHeightPx = slotHeight,
        dayColumnCount = dayColumnCount,
        slotCount = slotCount,
        occupiedByOthers = occupiedByOthers,
    )

    @Test
    fun `no drag keeps the original cell and is not a move`() {
        val result = target()

        assertEquals(1, result.dayIndex)
        assertEquals(2, result.rowIndex)
        assertTrue(result.isValid)
        assertFalse(result.isMoveFrom(startDayIndex = 1, startRowIndex = 2))
    }

    @Test
    fun `dragging less than half a column stays in the same column`() {
        val result = target(dragOffsetX = 49f)

        assertEquals(1, result.dayIndex)
    }

    @Test
    fun `dragging past half a column moves one column over`() {
        val result = target(dragOffsetX = 51f)

        assertEquals(2, result.dayIndex)
        assertTrue(result.isMoveFrom(startDayIndex = 1, startRowIndex = 2))
    }

    @Test
    fun `dragging backwards moves to an earlier column and row`() {
        val result = target(dragOffsetX = -110f, dragOffsetY = -70f)

        assertEquals(0, result.dayIndex)
        assertEquals(1, result.rowIndex)
    }

    @Test
    fun `dragging past the last column clamps to it`() {
        val result = target(dragOffsetX = 1000f)

        assertEquals(6, result.dayIndex)
    }

    @Test
    fun `dragging before the first column clamps to it`() {
        val result = target(dragOffsetX = -1000f)

        assertEquals(0, result.dayIndex)
    }

    @Test
    fun `a multi row course cannot be dropped so that it overflows the last row`() {
        val result = target(startRowIndex = 2, rowSpan = 3, dragOffsetY = 1000f, slotCount = 10)

        assertEquals(7, result.rowIndex)
        assertTrue(result.isValid)
    }

    @Test
    fun `dropping onto a cell another course occupies is rejected`() {
        val result = target(
            dragOffsetX = 100f,
            occupiedByOthers = setOf(2 to 3),
        )

        assertEquals(2, result.dayIndex)
        assertEquals(2, result.rowIndex)
        assertFalse(result.isValid)
        assertFalse(result.isMoveFrom(startDayIndex = 1, startRowIndex = 2))
    }

    @Test
    fun `only the rows the course spans are checked for occupancy`() {
        val result = target(
            rowSpan = 2,
            dragOffsetX = 100f,
            occupiedByOthers = setOf(2 to 4),
        )

        assertEquals(2, result.dayIndex)
        assertEquals(2, result.rowIndex)
        assertTrue(result.isValid)
    }

    @Test
    fun `a zero sized grid does not divide by zero`() {
        val result = resolveCourseDragTarget(
            startDayIndex = 0,
            startRowIndex = 0,
            rowSpan = 1,
            dragOffsetX = 120f,
            dragOffsetY = 80f,
            dayColumnWidthPx = 0f,
            slotHeightPx = 0f,
            dayColumnCount = 7,
            slotCount = 10,
            occupiedByOthers = emptySet(),
        )

        assertEquals(0, result.dayIndex)
        assertEquals(0, result.rowIndex)
    }

    @Test
    fun `the dragged course does not block its own cells`() {
        val dragged = renderEntry("dragged", dayIndex = 1, rowIndex = 2, rowSpan = 2)
        val other = renderEntry("other", dayIndex = 3, rowIndex = 0, rowSpan = 1)

        val occupied = occupiedCellsExcluding(listOf(dragged, other), excludedCourseId = "dragged")

        assertEquals(setOf(3 to 0), occupied)
    }

    @Test
    fun `every row a remaining course spans counts as occupied`() {
        val other = renderEntry("other", dayIndex = 2, rowIndex = 1, rowSpan = 3)

        val occupied = occupiedCellsExcluding(listOf(other), excludedCourseId = "dragged")

        assertEquals(setOf(2 to 1, 2 to 2, 2 to 3), occupied)
    }

    private fun slot(startNode: Int, endNode: Int) =
        DisplaySlot(
            startNode = startNode,
            endNode = endNode,
            label = "第${startNode}节",
            startTime = "08:00",
            endTime = "08:45",
        )

    @Test
    fun `a hidden weekend column does not shift the resolved weekday`() {
        val target = CourseDragTarget(dayIndex = 4, rowIndex = 1, isValid = true)

        val time = movedCourseTime(
            target = target,
            rowSpan = 1,
            columnDayOfWeeks = listOf(1, 2, 3, 4, 5),
            slots = listOf(slot(1, 2), slot(3, 4), slot(5, 6)),
        )

        assertEquals(5, time?.dayOfWeek)
        assertEquals(3, time?.startNode)
        assertEquals(4, time?.endNode)
    }

    @Test
    fun `a multi row course takes the first and last node it spans`() {
        val target = CourseDragTarget(dayIndex = 0, rowIndex = 0, isValid = true)

        val time = movedCourseTime(
            target = target,
            rowSpan = 3,
            columnDayOfWeeks = listOf(1, 2, 3, 4, 5, 6, 7),
            slots = listOf(slot(1, 2), slot(3, 4), slot(5, 6), slot(7, 8)),
        )

        assertEquals(1, time?.dayOfWeek)
        assertEquals(1, time?.startNode)
        assertEquals(6, time?.endNode)
    }

    @Test
    fun `a span reaching past the last slot resolves to nothing`() {
        val target = CourseDragTarget(dayIndex = 0, rowIndex = 2, isValid = true)

        val time = movedCourseTime(
            target = target,
            rowSpan = 3,
            columnDayOfWeeks = listOf(1, 2, 3, 4, 5, 6, 7),
            slots = listOf(slot(1, 2), slot(3, 4), slot(5, 6)),
        )

        assertNull(time)
    }

    @Test
    fun `a column with no matching visible day resolves to nothing`() {
        val target = CourseDragTarget(dayIndex = 5, rowIndex = 0, isValid = true)

        val time = movedCourseTime(
            target = target,
            rowSpan = 1,
            columnDayOfWeeks = listOf(1, 2, 3, 4, 5),
            slots = listOf(slot(1, 2)),
        )

        assertNull(time)
    }

    private fun renderEntry(
        id: String,
        dayIndex: Int,
        rowIndex: Int,
        rowSpan: Int,
    ) = CourseRenderEntry(
        course = com.x500x.cursimple.core.kernel.model.CourseItem(
            id = id,
            title = id,
            time = com.x500x.cursimple.core.kernel.model.CourseTimeSlot(
                dayOfWeek = dayIndex + 1,
                startNode = rowIndex + 1,
                endNode = rowIndex + rowSpan,
            ),
        ),
        placement = CoursePlacement(dayIndex = dayIndex, rowIndex = rowIndex, rowSpan = rowSpan),
        inactive = false,
    )
}
