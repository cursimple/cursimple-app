package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

private val TODAY: LocalDate = LocalDate.of(2026, 9, 7)

private val PROFILE = TermTimingProfile(
    termStartDate = "2026-09-07",
    slotTimes = listOf(
        ClassSlotTime(1, 2, "08:00", "09:40", "第一大节"),
        ClassSlotTime(3, 4, "10:00", "11:40", "第二大节"),
    ),
)

private fun course(startNode: Int = 1, endNode: Int = 2) = CourseItem(
    id = "c$startNode",
    title = "高等数学",
    weeks = listOf(1),
    time = CourseTimeSlot(dayOfWeek = 1, startNode = startNode, endNode = endNode),
)

private fun statusAt(hour: Int, minute: Int, date: LocalDate = TODAY): CourseStatus? =
    widgetRowStatus(
        course = course(),
        today = TODAY,
        targetDate = date,
        now = LocalTime.of(hour, minute),
        timingProfile = PROFILE,
        onHoliday = false,
    )

class WidgetRowStatusTest {
    @Test
    fun `a class in progress reads as live`() {
        assertEquals(CourseStatus.Live, statusAt(8, 0))
        assertEquals(CourseStatus.Live, statusAt(9, 0))
        assertEquals(CourseStatus.Live, statusAt(9, 39))
    }

    @Test
    fun `the last half hour before it starts reads as soon`() {
        assertEquals(CourseStatus.Soon, statusAt(7, 30))
        assertEquals(CourseStatus.Soon, statusAt(7, 59))
    }

    @Test
    fun `earlier than that is just an upcoming class`() {
        assertEquals(CourseStatus.Upcoming, statusAt(7, 29))
        assertEquals(CourseStatus.Upcoming, statusAt(6, 0))
    }

    @Test
    fun `once it ends it is past`() {
        assertEquals(CourseStatus.Past, statusAt(9, 40))
        assertEquals(CourseStatus.Past, statusAt(12, 0))
    }

    @Test
    fun `another day carries no status`() {
        assertNull(statusAt(8, 0, date = TODAY.plusDays(1)))
        assertNull(statusAt(8, 0, date = TODAY.minusDays(1)))
    }

    @Test
    fun `a holiday carries no status even for today`() {
        val status = widgetRowStatus(
            course = course(),
            today = TODAY,
            targetDate = TODAY,
            now = LocalTime.of(8, 30),
            timingProfile = PROFILE,
            onHoliday = true,
        )

        assertNull(status)
    }

    @Test
    fun `without period times there is nothing to compare against`() {
        val status = widgetRowStatus(
            course = course(),
            today = TODAY,
            targetDate = TODAY,
            now = LocalTime.of(8, 30),
            timingProfile = null,
            onHoliday = false,
        )

        assertNull(status)
    }
}
