package com.x500x.cursimple.core.kernel.mood

import com.x500x.cursimple.core.kernel.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DayMoodTest {

    // 2026-10-02 是周五，10-03 周六，10-04 周日
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)

    private fun mood(
        now: LocalDateTime,
        isHoliday: Boolean = false,
        holidayNameRes: Int? = null,
        tomorrowHoliday: Boolean? = false,
        totalCourses: Int = 0,
        hasMoreToday: Boolean = false,
        timeSensitive: Boolean = true,
    ) = dayMood(now, isHoliday, holidayNameRes, tomorrowHoliday, totalCourses, hasMoreToday, timeSensitive)

    @Test
    fun staysQuietWhileClassesRemain() {
        assertNull(mood(at(2, 10), totalCourses = 4, hasMoreToday = true))
    }

    @Test
    fun namedHolidayGetsItsOwnLines() {
        assertEquals(
            DayMood.HolidayNational,
            mood(at(3, 10), isHoliday = true, holidayNameRes = R.string.kernel_holiday_national_day, tomorrowHoliday = true),
        )
        assertEquals(DayMood.HolidayOther, mood(at(3, 10), isHoliday = true, tomorrowHoliday = true))
    }

    @Test
    fun lastHolidayDayWarnsAboutTomorrow() {
        assertEquals(
            DayMood.HolidayLastDay,
            mood(at(7, 10), isHoliday = true, holidayNameRes = R.string.kernel_holiday_national_day, tomorrowHoliday = false),
        )
        // 不知道明天放不放假时不贸然说「假期最后一天」
        assertEquals(
            DayMood.HolidayNational,
            mood(at(7, 10), isHoliday = true, holidayNameRes = R.string.kernel_holiday_national_day, tomorrowHoliday = null),
        )
    }

    @Test
    fun freeDaysSplitByWeekend() {
        assertEquals(DayMood.WeekendFree, mood(at(3, 10)))
        assertEquals(DayMood.WeekdayFree, mood(at(1, 10)))
        assertEquals(DayMood.SundayNight, mood(at(4, 20)))
        assertEquals(DayMood.WeekendFree, mood(at(4, 20), timeSensitive = false))
    }

    @Test
    fun finishedDays() {
        assertEquals(DayMood.DoneFriday, mood(at(2, 17), totalCourses = 3))
        assertEquals(
            DayMood.DoneTomorrowHoliday,
            mood(LocalDateTime.of(2026, 9, 30, 17, 0), totalCourses = 3, tomorrowHoliday = true),
        )
        assertEquals(DayMood.DoneEvening, mood(LocalDateTime.of(2026, 9, 29, 19, 0), totalCourses = 3))
        assertEquals(DayMood.Done, mood(LocalDateTime.of(2026, 9, 29, 15, 0), totalCourses = 3))
        assertEquals(DayMood.Done, mood(LocalDateTime.of(2026, 9, 29, 19, 0), totalCourses = 3, timeSensitive = false))
    }

    @Test
    fun lateNightOnlyWhenTimeSensitive() {
        assertEquals(DayMood.LateNight, mood(at(3, 23, 45)))
        assertEquals(DayMood.LateNight, mood(at(3, 2)))
        assertEquals(DayMood.WeekendFree, mood(at(3, 2), timeSensitive = false))
    }

    @Test
    fun lineStaysPutWithinADayAndMovesAcrossDays() {
        val date = LocalDate.of(2026, 10, 3)
        val first = dayMoodLineIndex(date, DayMood.WeekendFree, 6)
        repeat(5) { assertEquals(first, dayMoodLineIndex(date, DayMood.WeekendFree, 6)) }
        val week = (0L until 30L).map { dayMoodLineIndex(date.plusDays(it), DayMood.WeekendFree, 6) }.toSet()
        assertTrue("一个月里应该轮到好几句", week.size >= 4)
        assertEquals(0, dayMoodLineIndex(date, DayMood.Done, 1))
    }
}
