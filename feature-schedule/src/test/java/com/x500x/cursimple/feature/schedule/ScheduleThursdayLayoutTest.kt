package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.unit.dp
import org.junit.Test

/** Same-slot courses stay grouped in one cell, so the stack button shows them instead of two narrow lanes. */
class ScheduleThursdayLayoutTest {
    private fun m(clock: String) = clockMinute(clock)!!

    private val slots = listOf(
        SlotClock(m("08:00"), m("08:45")),
        SlotClock(m("08:50"), m("09:35")),
        SlotClock(m("09:55"), m("10:40")),
        SlotClock(m("10:45"), m("11:30")),
        SlotClock(m("11:35"), m("12:20")),
    )

    private fun entry(title: String, thursday: Int, rowIndex: Int, inactive: Boolean) = CourseRenderEntry(
        course = CourseItem(
            id = "$title-$rowIndex",
            title = title,
            time = CourseTimeSlot(dayOfWeek = 4, startNode = rowIndex + 1, endNode = rowIndex + 1),
        ),
        placement = CoursePlacement(dayIndex = thursday, rowIndex = rowIndex, rowSpan = 1),
        inactive = inactive,
    )

    @Test fun sameSlotCoursesShareOneCellAndFoldIntoTheStackButton() {
        val timeline = GridTimeline.build(slots, emptyList())
        val entries = listOf(
            entry("学术英语阅读与写作", thursday = 3, rowIndex = 3, inactive = false),
            entry("高级可编程逻辑程序设计与应用", thursday = 3, rowIndex = 3, inactive = true),
            entry("高级可编程逻辑程序设计与应用", thursday = 3, rowIndex = 4, inactive = true),
        )
        val cells = groupCourseCells(entries, timeline)
        // The two courses of the 4th period share one cell (the stack button shows the other course);
        // the 5th-period course is its own cell.
        assertEquals(2, cells.size)
        assertEquals(2, cells.single { it.main.course.title.startsWith("学术") }.entries.size)
    }

    @Test fun twoCoursesInOnePeriodSplitTopAndBottom() {
        val stack = stackCellItems(listOf("learning", "advanced"), 52.dp)
        assertEquals(2, stack.pieces.size)
        assertEquals(0.dp, stack.moreHeight)
    }
}
