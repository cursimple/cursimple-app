package com.x500x.cursimple.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class NextCourseHeaderTest {
    private val today: LocalDate = LocalDate.of(2026, 9, 12)

    @Test
    fun `header keeps the plain day label without a temporary override`() {
        assertEquals(
            NextCourseDayHeader.Plain(tomorrow = false),
            nextCourseDayHeader(today, today, today),
        )
        val tomorrow = today.plusDays(1)
        assertEquals(
            NextCourseDayHeader.Plain(tomorrow = true),
            nextCourseDayHeader(tomorrow, tomorrow, today),
        )
    }

    @Test
    fun `header shows the source date when today follows another day's schedule`() {
        val sourceDate = LocalDate.of(2026, 9, 7)
        assertEquals(
            NextCourseDayHeader.TemporarySource(tomorrow = false, sourceDate = sourceDate),
            nextCourseDayHeader(today, sourceDate, today),
        )
    }

    @Test
    fun `header shows the source date for tomorrow as well`() {
        val tomorrow = today.plusDays(1)
        val sourceDate = LocalDate.of(2026, 9, 10)
        assertEquals(
            NextCourseDayHeader.TemporarySource(tomorrow = true, sourceDate = sourceDate),
            nextCourseDayHeader(tomorrow, sourceDate, today),
        )
    }

    @Test
    fun `header names the holiday instead of the source date`() {
        val nationalDay = WidgetHolidayLabel.Named("国庆节")
        assertEquals(
            NextCourseDayHeader.Holiday(tomorrow = false, label = nationalDay),
            nextCourseDayHeader(today, today, today, holidayLabel = nationalDay),
        )
        val tomorrow = today.plusDays(1)
        assertEquals(
            NextCourseDayHeader.Holiday(tomorrow = true, label = nationalDay),
            nextCourseDayHeader(tomorrow, tomorrow, today, holidayLabel = nationalDay),
        )
    }
}
