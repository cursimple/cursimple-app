package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BuiltInHolidayResolutionTest {

    private val nationalDay = LocalDate.of(2026, 10, 1)
    private val ordinaryDay = LocalDate.of(2026, 10, 20)

    private fun resolve(date: LocalDate, calendar: HolidayCalendarSettings) =
        resolveScheduleDay(date, overrides = emptyList(), holidayCalendar = calendar)

    @Test
    fun `a built-in holiday carries a text resource so it can follow the app language`() {
        val result = resolve(nationalDay, HolidayCalendarSettings(builtInEnabled = true))

        assertNotNull(result.holidayNameRes)
        assertEquals(builtInHolidayNameResOn(nationalDay), result.holidayNameRes)
    }

    @Test
    fun `an ordinary day carries no holiday resource`() {
        val result = resolve(ordinaryDay, HolidayCalendarSettings(builtInEnabled = true))

        assertNull(result.holidayNameRes)
    }

    @Test
    fun `a user written holiday keeps its own name and gets no resource`() {
        val calendar = HolidayCalendarSettings(
            builtInEnabled = true,
            entries = listOf(
                HolidayCalendarEntry(nationalDay.toString(), HolidayEntryKind.Holiday, "校庆"),
            ),
        )

        val result = resolve(nationalDay, calendar)

        assertEquals("校庆", result.holidayName)
        assertNull(result.holidayNameRes)
    }

    @Test
    fun `every built-in holiday name maps to a distinct resource per name`() {
        val byName = builtInHolidayYears
            .flatMap(::builtInHolidayEntriesOfYear)
            .associate { entry ->
                val date = LocalDate.parse(entry.date)
                entry.name to builtInHolidayNameResOn(date)
            }

        assertEquals(byName.size, byName.values.distinct().size)
        byName.values.forEach(::assertNotNull)
    }
}
