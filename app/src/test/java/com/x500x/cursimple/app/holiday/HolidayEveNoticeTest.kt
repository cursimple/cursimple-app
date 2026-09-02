package com.x500x.cursimple.app.holiday

import com.x500x.cursimple.core.kernel.model.HolidayCalendarEntry
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.HolidayEntryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidayEveNoticeTest {

    private val holidayEve = LocalDate.of(2026, 9, 30)
    private val nationalDay = LocalDate.of(2026, 10, 1)

    private fun notice(
        today: LocalDate = holidayEve,
        holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings(),
        skipRemindersOnHoliday: Boolean = false,
        mutedDates: Set<LocalDate> = emptySet(),
        reminderCount: Int = 2,
    ) = holidayEveNotice(
        today = today,
        holidayCalendar = holidayCalendar,
        temporaryScheduleOverrides = emptyList(),
        skipRemindersOnHoliday = skipRemindersOnHoliday,
        mutedDates = mutedDates,
        reminderCountOn = { reminderCount },
    )

    @Test
    fun `the evening before a holiday suggests muting the reminders that are still set`() {
        val result = notice()

        val suggestion = result as HolidayEveNotice.SuggestMute
        assertEquals(nationalDay, suggestion.date)
        assertEquals(2, suggestion.reminderCount)
        assertTrue(suggestion.holidayNameRes != null || suggestion.holidayName != null)
    }

    @Test
    fun `an ordinary evening says nothing`() {
        assertEquals(HolidayEveNotice.None, notice(today = LocalDate.of(2026, 10, 20)))
    }

    @Test
    fun `nothing is suggested when the holiday has no reminders left`() {
        assertEquals(HolidayEveNotice.None, notice(reminderCount = 0))
    }

    @Test
    fun `nothing is suggested when reminders already skip holidays`() {
        assertEquals(HolidayEveNotice.None, notice(skipRemindersOnHoliday = true))
    }

    @Test
    fun `the same day is not suggested twice`() {
        assertEquals(HolidayEveNotice.None, notice(mutedDates = setOf(nationalDay)))
    }

    @Test
    fun `a user workday entry on the holiday means there is nothing to suggest`() {
        val calendar = HolidayCalendarSettings(
            entries = listOf(
                HolidayCalendarEntry(nationalDay.toString(), HolidayEntryKind.Workday, "学校照常上课"),
            ),
        )

        assertEquals(HolidayEveNotice.None, notice(holidayCalendar = calendar))
    }

    @Test
    fun `a user written holiday is suggested with the name the user typed`() {
        val date = LocalDate.of(2026, 11, 11)
        val calendar = HolidayCalendarSettings(
            builtInEnabled = false,
            entries = listOf(HolidayCalendarEntry(date.toString(), HolidayEntryKind.Holiday, "校庆")),
        )

        val suggestion = notice(today = date.minusDays(1), holidayCalendar = calendar)

        assertEquals("校庆", (suggestion as HolidayEveNotice.SuggestMute).holidayName)
    }
}
