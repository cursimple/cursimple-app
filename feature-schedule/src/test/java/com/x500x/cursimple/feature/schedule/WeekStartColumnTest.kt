package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekStartColumnTest {
    private val timing = TermTimingProfile("2026-09-07", listOf(
        ClassSlotTime(1, 2, "08:00", "09:35"), ClassSlotTime(3, 4, "10:05", "11:40")))

    private fun course(id: String, day: Int) =
        CourseItem(id, "课$id", weeks = (1..16).toList(), time = CourseTimeSlot(day, 1, 2))

    private fun columns(columnDayOfWeeks: List<Int>): Map<String, Int> {
        val slots = displaySlotsForTest(timing)
        return buildWeekRenderEntries(
            allCourses = listOf(course("mon", 1), course("wed", 3), course("sun", 7)),
            slots = slots,
            weekIndex = 2,
            columnDayOfWeeks = columnDayOfWeeks,
        ).associate { it.course.id to it.placement.dayIndex }
    }

    @Test fun mondayFirstPutsEachCourseInItsWeekdayColumn() {
        assertEquals(mapOf("mon" to 0, "wed" to 2, "sun" to 6), columns((1..7).toList()))
    }

    @Test fun sundayFirstKeepsCoursesUnderTheirOwnWeekday() {
        assertEquals(mapOf("mon" to 1, "wed" to 3, "sun" to 0), columns(listOf(7) + (1..6).toList()))
    }

    @Test fun hidingWeekendKeepsCoursesInVisibleColumns() {
        assertEquals(mapOf("mon" to 0, "wed" to 2), columns((1..5).toList()))
    }

    private fun displaySlotsForTest(profile: TermTimingProfile) = profile.slotTimes.map {
        DisplaySlot(it.startNode, it.endNode, "第${it.startNode}节", it.startTime, it.endTime)
    }
}
