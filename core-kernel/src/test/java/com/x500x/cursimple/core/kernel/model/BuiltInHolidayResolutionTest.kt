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
    fun `built-in holidays cover the announced 2026 ranges`() {
        val expected = mapOf(
            "元旦" to listOf("2026-01-01".."2026-01-03"),
            "春节" to listOf("2026-02-15".."2026-02-23"),
            "清明节" to listOf("2026-04-04".."2026-04-06"),
            "劳动节" to listOf("2026-05-01".."2026-05-05"),
            "端午节" to listOf("2026-06-19".."2026-06-21"),
            "中秋节" to listOf("2026-09-25".."2026-09-27"),
            "国庆节" to listOf("2026-10-01".."2026-10-07"),
        )
        val holidayDates = expected.values
            .flatten()
            .flatMap { range -> datesBetween(range.start, range.endInclusive) }

        holidayDates.forEach { date ->
            assertNotNull("$date 应当是内置假日", builtInHolidayEntryOn(date))
        }
        assertEquals(holidayDates.size, builtInHolidayEntriesOfYear(2026).size)
    }

    @Test
    fun `the days around each holiday range are not holidays`() {
        listOf("2025-12-31", "2026-01-04", "2026-02-14", "2026-02-24", "2026-09-24", "2026-10-08")
            .map(LocalDate::parse)
            .forEach { date ->
                assertNull("$date 不应当是内置假日", builtInHolidayEntryOn(date))
            }
    }

    private fun datesBetween(start: String, end: String): List<LocalDate> {
        var cursor = LocalDate.parse(start)
        val last = LocalDate.parse(end)
        val dates = mutableListOf<LocalDate>()
        while (!cursor.isAfter(last)) {
            dates += cursor
            cursor = cursor.plusDays(1)
        }
        return dates
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
