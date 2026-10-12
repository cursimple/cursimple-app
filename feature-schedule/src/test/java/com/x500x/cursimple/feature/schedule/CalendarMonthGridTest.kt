package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Verify custom month-grid weekday placement across display start days. */
class CalendarMonthGridTest {

    @Test fun pagerMonthMappingRoundTripsAcrossYearsWithoutChangingDates() {
        val origin = YearMonth.of(2026, 9)
        for (month in listOf(origin, YearMonth.of(2027, 1), YearMonth.of(2025, 12), YearMonth.of(2036, 9))) {
            assertEquals(month, calendarMonthAtPage(origin, calendarPageOfMonth(origin, month)))
        }
    }

    @Test fun `left and right drags cross year boundaries`() {
        val selected = LocalDate.of(2026, 12, 31)
        val initial = YearMonth.from(selected)
        assertEquals(YearMonth.of(2027, 1), initial.plusMonths(calendarMonthSwipeDelta(-120f, 48f).toLong()))
        assertEquals(YearMonth.of(2026, 11), initial.plusMonths(calendarMonthSwipeDelta(120f, 48f).toLong()))
    }

    @Test fun `small drags and invalid distances do not page`() {
        assertEquals(0, calendarMonthSwipeDelta(0f, 48f))
        assertEquals(0, calendarMonthSwipeDelta(-47f, 48f))
        assertEquals(0, calendarMonthSwipeDelta(Float.NaN, 48f))
        assertEquals(0, calendarMonthSwipeDelta(120f, 0f))
        assertEquals(1, calendarMonthSwipeDelta(-48f, 48f))
    }

    @Test fun `right to left layout reverses swipe navigation`() {
        assertEquals(-1, calendarMonthSwipeDelta(-100f, 48f, rtl = true))
        assertEquals(1, calendarMonthSwipeDelta(100f, 48f, rtl = true))
    }

    @Test
    fun `week starting on monday puts the first day under its own weekday`() {
        val cells = monthGridCells(YearMonth.of(2026, 9), weekStartDayOfWeek = 1)

        assertNull(cells[0])
        assertEquals(LocalDate.of(2026, 9, 1), cells[1])
        val index = cells.indexOf(LocalDate.of(2026, 9, 19))
        assertEquals(5, index % 7)
    }

    @Test
    fun `week starting on sunday shifts every column by one`() {
        val cells = monthGridCells(YearMonth.of(2026, 9), weekStartDayOfWeek = 7)

        assertNull(cells[0])
        assertNull(cells[1])
        assertEquals(LocalDate.of(2026, 9, 1), cells[2])
        assertEquals(6, cells.indexOf(LocalDate.of(2026, 9, 19)) % 7)
    }

    @Test
    fun `grid keeps whole weeks and covers every day of the month`() {
        val month = YearMonth.of(2026, 2)
        val cells = monthGridCells(month, weekStartDayOfWeek = 1)

        assertEquals(0, cells.size % 7)
        assertEquals(month.lengthOfMonth(), cells.count { it != null })
    }
}
