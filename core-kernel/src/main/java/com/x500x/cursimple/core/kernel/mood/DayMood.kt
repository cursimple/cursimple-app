package com.x500x.cursimple.core.kernel.mood

import android.content.Context
import com.x500x.cursimple.core.kernel.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.random.Random

/**
 * Date-seeded empty-state messages stay consistent between app and widgets throughout the day.
 */
enum class DayMood(val lines: Int) {
    LateNight(R.array.mood_late_night),
    HolidayNewYear(R.array.mood_holiday_new_year),
    HolidaySpringEve(R.array.mood_holiday_spring_eve),
    HolidaySpring(R.array.mood_holiday_spring),
    HolidayQingming(R.array.mood_holiday_qingming),
    HolidayLabour(R.array.mood_holiday_labour),
    HolidayDragonBoat(R.array.mood_holiday_dragon_boat),
    HolidayMidAutumn(R.array.mood_holiday_mid_autumn),
    HolidayNational(R.array.mood_holiday_national),
    HolidayOther(R.array.mood_holiday_other),
    HolidayLastDay(R.array.mood_holiday_last_day),
    WeekendFree(R.array.mood_weekend_free),
    SundayNight(R.array.mood_sunday_night),
    WeekdayFree(R.array.mood_weekday_free),
    DoneTomorrowHoliday(R.array.mood_done_tomorrow_holiday),
    DoneFriday(R.array.mood_done_friday),
    DoneEvening(R.array.mood_done_evening),
    Done(R.array.mood_done),

    TomorrowFree(R.array.mood_tomorrow_free),
    WeekFree(R.array.mood_week_free),
    NoAlarms(R.array.mood_no_alarms),
    TasksClear(R.array.mood_tasks_clear),
}

/**
 * Return null while courses remain. holidayNameRes selects holiday-specific text; unknown
 * tomorrowHoliday omits related cases. Enable timeSensitive only for promptly refreshed app
 * surfaces.
 */
fun dayMood(
    now: LocalDateTime,
    isHoliday: Boolean,
    holidayNameRes: Int?,
    tomorrowHoliday: Boolean?,
    totalCourses: Int,
    hasMoreToday: Boolean,
    timeSensitive: Boolean,
): DayMood? {
    if (hasMoreToday) return null
    val time = now.toLocalTime()
    val dayOfWeek = now.dayOfWeek
    if (timeSensitive && (time >= LATE_NIGHT_START || time < LATE_NIGHT_END)) return DayMood.LateNight
    if (isHoliday) {
        return if (tomorrowHoliday == false) DayMood.HolidayLastDay else holidayMoodOf(holidayNameRes)
    }
    if (totalCourses == 0) {
        return when (dayOfWeek) {
            DayOfWeek.SATURDAY -> DayMood.WeekendFree
            DayOfWeek.SUNDAY ->
                if (timeSensitive && time >= EVENING_START && tomorrowHoliday == false) DayMood.SundayNight
                else DayMood.WeekendFree
            else -> DayMood.WeekdayFree
        }
    }
    return when {
        tomorrowHoliday == true -> DayMood.DoneTomorrowHoliday
        dayOfWeek == DayOfWeek.FRIDAY -> DayMood.DoneFriday
        timeSensitive && time >= EVENING_START -> DayMood.DoneEvening
        else -> DayMood.Done
    }
}

private fun holidayMoodOf(nameRes: Int?): DayMood = when (nameRes) {
    R.string.kernel_holiday_new_year -> DayMood.HolidayNewYear
    R.string.kernel_holiday_spring_festival_eve -> DayMood.HolidaySpringEve
    R.string.kernel_holiday_spring_festival -> DayMood.HolidaySpring
    R.string.kernel_holiday_qingming -> DayMood.HolidayQingming
    R.string.kernel_holiday_labour_day -> DayMood.HolidayLabour
    R.string.kernel_holiday_dragon_boat -> DayMood.HolidayDragonBoat
    R.string.kernel_holiday_mid_autumn -> DayMood.HolidayMidAutumn
    R.string.kernel_holiday_national_day -> DayMood.HolidayNational
    else -> DayMood.HolidayOther
}

fun dayMoodLineIndex(date: LocalDate, mood: DayMood, size: Int): Int =
    if (size <= 1) 0 else Random(date.toEpochDay() * 31 + mood.ordinal).nextInt(size)

fun Context.dayMoodLine(date: LocalDate, mood: DayMood): String? {
    val lines = resources.getStringArray(mood.lines)
    if (lines.isEmpty()) return null
    return lines[dayMoodLineIndex(date, mood, lines.size)]
}

private val LATE_NIGHT_START: LocalTime = LocalTime.of(23, 30)
private val LATE_NIGHT_END: LocalTime = LocalTime.of(5, 0)
private val EVENING_START: LocalTime = LocalTime.of(18, 0)
