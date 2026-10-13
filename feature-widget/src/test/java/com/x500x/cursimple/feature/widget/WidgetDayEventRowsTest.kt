package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** Events share the day list with courses: placed by clock, with their own live status. */
class WidgetDayEventRowsTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 7)

    private fun event(start: String = "12:10", end: String = "13:00", weekly: Boolean = false) = ScheduleEvent(
        id = "e1",
        title = "社团例会",
        date = today.toString(),
        startTime = start,
        endTime = end,
        repeatWeekly = weekly,
    )

    @Test
    fun `events slot between courses by start time`() {
        val merged = mergeWidgetDayRows(
            courses = listOf(8 * 60 to "高数", 14 * 60 to "英语"),
            events = listOf(12 * 60 + 10 to "例会", 19 * 60 to "跑步"),
        )

        assertEquals(listOf("高数", "例会", "英语", "跑步"), merged)
    }

    @Test
    fun `a course wins the earlier place on a tie`() {
        val merged = mergeWidgetDayRows(courses = listOf(8 * 60 to "高数"), events = listOf(8 * 60 to "例会"))

        assertEquals(listOf("高数", "例会"), merged)
    }

    @Test
    fun `courses without clock times stay in period order ahead of events`() {
        val merged = mergeWidgetDayRows(
            courses = listOf<Pair<Int?, String>>(null to "高数", null to "英语"),
            events = listOf(7 * 60 to "晨跑"),
        )

        assertEquals(listOf("高数", "英语", "晨跑"), merged)
    }

    @Test
    fun `a day with only events still lists them`() {
        assertEquals(listOf("例会"), mergeWidgetDayRows(emptyList(), listOf(12 * 60 to "例会")))
    }

    @Test
    fun `event status follows its own clock`() {
        fun at(hour: Int, minute: Int) = widgetEventStatus(event(), today, today, LocalTime.of(hour, minute))

        assertEquals(CourseStatus.Upcoming, at(11, 39))
        assertEquals(CourseStatus.Soon, at(11, 40))
        assertEquals(CourseStatus.Live, at(12, 10))
        assertEquals(CourseStatus.Live, at(12, 59))
        assertEquals(CourseStatus.Past, at(13, 0))
    }

    @Test
    fun `other days carry no live status`() {
        assertNull(widgetEventStatus(event(), today, today.plusDays(1), LocalTime.of(12, 30)))
        assertNull(widgetEventStatus(event(), today, today.minusDays(1), LocalTime.of(12, 30)))
    }

    @Test
    fun `event start and end refresh the widget outside class hours`() {
        val result = widgetRefreshBoundaries(
            slots = listOf(ClassSlotTime(1, 1, "08:00", "09:40")),
            now = today.atTime(10, 0),
            limit = 10,
            eventSpans = widgetEventSpans(listOf(event()), today),
        )

        assertTrue(today.atTime(11, 40) in result)
        assertTrue(today.atTime(12, 10) in result)
        assertTrue(today.atTime(13, 0) in result)
    }

    @Test
    fun `events alone still schedule their boundaries`() {
        val result = widgetRefreshBoundaries(
            slots = emptyList(),
            now = today.atTime(10, 0),
            eventSpans = widgetEventSpans(listOf(event()), today),
        )

        assertEquals(
            listOf(today.atTime(11, 40), today.atTime(12, 10), today.atTime(13, 0), today.plusDays(1).atStartOfDay()),
            result,
        )
    }

    @Test
    fun `spans cover today and tomorrow only`() {
        val weekly = event(weekly = true)
        val tomorrowOnly = event().copy(id = "e2", date = today.plusDays(1).toString())
        val later = event().copy(id = "e3", date = today.plusDays(2).toString())

        assertEquals(
            listOf(
                today.atTime(12, 10) to today.atTime(13, 0),
                today.plusDays(1).atTime(12, 10) to today.plusDays(1).atTime(13, 0),
            ),
            widgetEventSpans(listOf(weekly, tomorrowOnly, later), today),
        )
    }

    @Test
    fun `empty day wording covers events only when they are shown`() {
        assertEquals(
            ScheduleWidgetEmptyLabel.NoCourses(0, agenda = true),
            scheduleWidgetEmptyLabel(
                termStartMissing = false,
                beforeTermStart = false,
                termStartDate = null,
                offset = 0,
                showEvents = true,
            ),
        )
        assertEquals(
            ScheduleWidgetEmptyLabel.NoCourses(0),
            scheduleWidgetEmptyLabel(
                termStartMissing = false,
                beforeTermStart = false,
                termStartDate = null,
                offset = 0,
            ),
        )
    }
}
