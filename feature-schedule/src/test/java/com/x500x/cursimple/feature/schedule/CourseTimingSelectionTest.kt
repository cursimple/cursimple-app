package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CourseTimingSelectionTest {
    @Test
    fun specificDatesAreGroupedByWeekdayWithoutAddingUnselectedDates() {
        val start = LocalDate.of(2026, 9, 7)
        val monday = start.plusDays(7)
        val wednesday = start.plusDays(16)
        val nextMonthMonday = start.plusDays(28)
        assertEquals(
            mapOf(1 to listOf(2, 5), 3 to listOf(3)),
            courseWeeksByDate(start, listOf(wednesday, nextMonthMonday, monday, monday), 18),
        )
        assertEquals(emptyMap<Int, List<Int>>(), courseWeeksByDate(start, listOf(monday, start.minusDays(1)), 18))
    }

    @Test
    fun dateUsesTheTermsWeekAndAllowsOnlyDatesInsideIt() {
        val start = LocalDate.of(2026, 9, 2)
        assertEquals(1, courseWeekForDate(start, start.plusDays(3), 18))
        assertEquals(2, courseWeekForDate(start, start.plusDays(5), 18))
        assertNull(courseWeekForDate(start, start.minusDays(1), 18))
        assertNull(courseWeekForDate(start, start.plusWeeks(18), 18))
        assertNull(courseWeekForDate(null, start, 18))
    }

    @Test
    fun namedBlocksStayWholeAndMatchTimetableOrder() {
        val first = ClassSlotTime(1, 2, "08:00", "09:35", "第一大节")
        val second = ClassSlotTime(3, 4, "10:00", "11:35", "第二大节")
        assertEquals(listOf(first, second), coursePeriodSlots(listOf(second, first), 12))
        assertEquals(listOf(1..1, 2..2, 3..3), coursePeriodSlots(emptyList(), 3).map { it.startNode..it.endNode })
    }
}
