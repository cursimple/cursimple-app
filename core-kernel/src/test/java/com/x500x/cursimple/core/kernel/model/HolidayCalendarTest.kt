package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.time.LocalDate
import org.junit.Test

class HolidayCalendarTest {
    private val defaults = HolidayCalendarSettings()

    @Test
    fun builtInCalendarMarksStatutoryHolidays() {
        assertTrue(isScheduleHoliday(LocalDate.of(2026, 1, 1), emptyList(), defaults))
        assertTrue(isScheduleHoliday(LocalDate.of(2026, 2, 17), emptyList(), defaults))
        assertTrue(isScheduleHoliday(LocalDate.of(2026, 10, 1), emptyList(), defaults))
        assertEquals("国庆节", scheduleHolidayName(LocalDate.of(2026, 10, 1), emptyList(), defaults))
    }

    @Test
    fun builtInCalendarLeavesOrdinaryDaysAlone() {
        assertFalse(isScheduleHoliday(LocalDate.of(2026, 9, 28), emptyList(), defaults))
        assertNull(scheduleHolidayName(LocalDate.of(2026, 9, 28), emptyList(), defaults))
    }

    @Test
    fun builtInCalendarCanBeDisabled() {
        val settings = HolidayCalendarSettings.NONE

        assertFalse(isScheduleHoliday(LocalDate.of(2026, 10, 1), emptyList(), settings))
    }

    @Test
    fun userWorkdayEntryOverridesBuiltInHoliday() {
        val settings = defaults.withEntry(
            HolidayCalendarEntry("2026-10-05", HolidayEntryKind.Workday, "学校照常上课"),
        )

        assertFalse(isScheduleHoliday(LocalDate.of(2026, 10, 5), emptyList(), settings))
    }

    @Test
    fun userHolidayEntryAddsDayMissingFromBuiltInData() {
        val settings = defaults.withEntry(
            HolidayCalendarEntry("2026-10-06", HolidayEntryKind.Holiday, "国庆调休"),
        )

        assertTrue(isScheduleHoliday(LocalDate.of(2026, 10, 6), emptyList(), settings))
        assertEquals("国庆调休", scheduleHolidayName(LocalDate.of(2026, 10, 6), emptyList(), settings))
    }

    @Test
    fun laterUserEntryReplacesEarlierOneOnSameDate() {
        val settings = defaults
            .withEntry(HolidayCalendarEntry("2026-10-06", HolidayEntryKind.Holiday))
            .withEntry(HolidayCalendarEntry("2026-10-06", HolidayEntryKind.Workday))

        assertEquals(1, settings.entries.size)
        assertFalse(isScheduleHoliday(LocalDate.of(2026, 10, 6), emptyList(), settings))
    }

    @Test
    fun removingUserEntryFallsBackToBuiltInData() {
        val settings = defaults
            .withEntry(HolidayCalendarEntry("2026-10-05", HolidayEntryKind.Workday))
            .withoutEntryOn(LocalDate.of(2026, 10, 5))

        assertTrue(settings.entries.isEmpty())
        assertTrue(isScheduleHoliday(LocalDate.of(2026, 10, 5), emptyList(), settings))
    }

    @Test
    fun makeUpOverrideOutranksBuiltInHoliday() {
        val overrides = listOf(
            TemporaryScheduleOverride(
                id = "makeup",
                type = TemporaryScheduleOverrideType.MakeUp,
                targetDate = "2026-10-03",
                sourceDate = "2026-09-07",
            ),
        )

        val resolution = resolveScheduleDay(LocalDate.of(2026, 10, 3), overrides, defaults)

        assertFalse(resolution.isHoliday)
        assertEquals(LocalDate.of(2026, 9, 7), resolution.sourceDate)
    }

    @Test
    fun userHolidayEntryOutranksMakeUpOverride() {
        val overrides = listOf(
            TemporaryScheduleOverride(
                id = "makeup",
                type = TemporaryScheduleOverrideType.MakeUp,
                targetDate = "2026-10-03",
                sourceDate = "2026-09-07",
            ),
        )
        val settings = defaults.withEntry(
            HolidayCalendarEntry("2026-10-03", HolidayEntryKind.Holiday, "国庆节"),
        )

        val resolution = resolveScheduleDay(LocalDate.of(2026, 10, 3), overrides, settings)

        assertTrue(resolution.isHoliday)
        assertEquals(LocalDate.of(2026, 10, 3), resolution.sourceDate)
    }

    @Test
    fun cancelCourseOverrideDoesNotTurnHolidayIntoClassDay() {
        val overrides = listOf(
            TemporaryScheduleOverride(
                id = "cancel",
                type = TemporaryScheduleOverrideType.CancelCourse,
                targetDate = "2026-10-01",
                cancelStartNode = 1,
                cancelEndNode = 2,
            ),
        )

        assertTrue(isScheduleHoliday(LocalDate.of(2026, 10, 1), overrides, defaults))
    }

    @Test
    fun userWorkdayEntryKeepsMakeUpSourceDate() {
        val overrides = listOf(
            TemporaryScheduleOverride(
                id = "makeup",
                type = TemporaryScheduleOverrideType.MakeUp,
                targetDate = "2026-10-11",
                sourceDate = "2026-10-09",
            ),
        )
        val settings = defaults.withEntry(
            HolidayCalendarEntry("2026-10-11", HolidayEntryKind.Workday),
        )

        val resolution = resolveScheduleDay(LocalDate.of(2026, 10, 11), overrides, settings)

        assertFalse(resolution.isHoliday)
        assertEquals(LocalDate.of(2026, 10, 9), resolution.sourceDate)
    }

    @Test
    fun invalidEntryDatesAreIgnored() {
        val settings = defaults.withEntry(HolidayCalendarEntry("2026-13-40", HolidayEntryKind.Holiday))

        assertTrue(settings.entries.isEmpty())
    }

    @Test
    fun mergedYearListingPrefersUserEntries() {
        val settings = defaults
            .withEntry(HolidayCalendarEntry("2026-10-01", HolidayEntryKind.Workday, "补课"))
            .withEntry(HolidayCalendarEntry("2026-10-06", HolidayEntryKind.Holiday, "国庆调休"))

        val entries = settings.entriesOfYear(2026)
        val octoberFirst = entries.filter { it.date == "2026-10-01" }

        assertEquals(1, octoberFirst.size)
        assertEquals(HolidayEntryKind.Workday, octoberFirst.first().kind)
        assertTrue(entries.any { it.date == "2026-10-06" })
    }

    @Test
    fun builtInDataCoversTwentyTwentySix() {
        assertEquals(listOf(2026), builtInHolidayYears)
        assertEquals(18, builtInHolidayEntriesOfYear(2026).size)
    }
}
