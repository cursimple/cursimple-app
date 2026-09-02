package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidayDatasetTest {

    private val nationalDay = """
        {
          "year": 2026,
          "papers": ["国办发明电〔2025〕7号"],
          "days": [
            {"name": "国庆节", "date": "2026-10-01", "isOffDay": true},
            {"name": "国庆节", "date": "2026-10-07", "isOffDay": true},
            {"name": "国庆节", "date": "2026-10-10", "isOffDay": false}
          ]
        }
    """.trimIndent()

    @Test
    fun `off days become holidays and work days become make-up workdays`() {
        val result = parseHolidayDataset(nationalDay, expectedYear = 2026)

        val success = result as HolidayDatasetParseResult.Success
        assertEquals(2026, success.year)
        assertEquals(3, success.entries.size)
        assertEquals(HolidayEntryKind.Holiday, success.entries[0].kind)
        assertEquals(HolidayEntryKind.Holiday, success.entries[1].kind)
        assertEquals(HolidayEntryKind.Workday, success.entries[2].kind)
        assertEquals("国庆节", success.entries[0].name)
    }

    @Test
    fun `unknown fields do not break parsing`() {
        val result = parseHolidayDataset(
            """{"year":2026,"somethingNew":1,"days":[{"name":"元旦","date":"2026-01-01","isOffDay":true,"extra":true}]}""",
            expectedYear = 2026,
        )

        assertTrue(result is HolidayDatasetParseResult.Success)
    }

    @Test
    fun `a body that is not the dataset is reported as malformed`() {
        assertEquals(HolidayDatasetParseResult.Malformed, parseHolidayDataset("<html>404</html>", 2026))
        assertEquals(HolidayDatasetParseResult.Malformed, parseHolidayDataset("", 2026))
    }

    @Test
    fun `a dataset for another year is refused instead of silently used`() {
        val result = parseHolidayDataset("""{"year":2025,"days":[{"date":"2025-01-01","name":"元旦"}]}""", 2026)

        assertEquals(HolidayDatasetParseResult.YearMismatch(2026, 2025), result)
    }

    @Test
    fun `a dataset with no usable day is reported as empty`() {
        assertEquals(HolidayDatasetParseResult.Empty, parseHolidayDataset("""{"year":2026,"days":[]}""", 2026))
        assertEquals(
            HolidayDatasetParseResult.Empty,
            parseHolidayDataset("""{"year":2026,"days":[{"date":"not a date","name":"元旦"}]}""", 2026),
        )
    }

    @Test
    fun `synced data wins over the built-in snapshot for the same year`() {
        val settings = HolidayCalendarSettings(
            syncedYears = listOf(
                SyncedHolidayYear(
                    year = 2026,
                    entries = listOf(HolidayCalendarEntry("2026-10-06", HolidayEntryKind.Holiday, "国庆节")),
                ),
            ),
        )

        // 同步数据只声明了 10 月 6 日，同一年内置快照的其它日期不再参与判定
        assertNotNull(settings.entryOn(LocalDate.of(2026, 10, 6)))
        assertNull(settings.entryOn(LocalDate.of(2026, 10, 1)))
    }

    @Test
    fun `a year without synced data still falls back to the built-in snapshot`() {
        val settings = HolidayCalendarSettings(
            syncedYears = listOf(SyncedHolidayYear(year = 2027, entries = emptyList())),
        )

        assertNotNull(settings.entryOn(LocalDate.of(2026, 10, 1)))
    }

    @Test
    fun `a user entry still wins over synced data`() {
        val date = LocalDate.of(2026, 10, 6)
        val settings = HolidayCalendarSettings(
            entries = listOf(HolidayCalendarEntry(date.toString(), HolidayEntryKind.Workday, "学校照常上课")),
            syncedYears = listOf(
                SyncedHolidayYear(
                    year = 2026,
                    entries = listOf(HolidayCalendarEntry(date.toString(), HolidayEntryKind.Holiday, "国庆节")),
                ),
            ),
        )

        assertEquals(HolidayEntryKind.Workday, settings.entryOn(date)?.kind)
    }

    @Test
    fun `synced holiday names still resolve to a text resource so they follow the app language`() {
        listOf("元旦", "除夕", "春节", "清明", "清明节", "劳动节", "端午", "中秋节", "国庆节").forEach { name ->
            assertNotNull("$name 应当有对应文案", holidayNameResOfName(name))
        }
        assertNull(holidayNameResOfName("校庆"))
        assertNull(holidayNameResOfName(""))
    }

    @Test
    fun `a synced holiday resolves with a text resource on the schedule`() {
        val date = LocalDate.of(2027, 10, 1)
        val settings = HolidayCalendarSettings(
            syncedYears = listOf(
                SyncedHolidayYear(
                    year = 2027,
                    entries = listOf(HolidayCalendarEntry(date.toString(), HolidayEntryKind.Holiday, "国庆节")),
                ),
            ),
        )

        val result = resolveScheduleDay(date, overrides = emptyList(), holidayCalendar = settings)

        assertTrue(result.isHoliday)
        assertEquals("国庆节", result.holidayName)
        assertNotNull(result.holidayNameRes)
    }
}
