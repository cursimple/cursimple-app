package com.x500x.cursimple.core.kernel.mood

import android.content.Context
import com.x500x.cursimple.core.kernel.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.random.Random

/**
 * 今天没课、放假、或者课都上完了的时候，用一句带点人味的话代替干巴巴的「今日没有课程」。
 *
 * 每种情形各有一组句子，按日期挑一句：同一天里 App 和小组件说的是同一句，不会一刷新就换，
 * 第二天再换一句。不进设置、不写进更新公告，留着当彩蛋。
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

    // 下面几类不经 [dayMood] 判断，由各个小组件在对应的空状态下直接取用
    TomorrowFree(R.array.mood_tomorrow_free),
    WeekFree(R.array.mood_week_free),
    NoAlarms(R.array.mood_no_alarms),
    TasksClear(R.array.mood_tasks_clear),
}

/**
 * 挑出今天该说哪类话；还有课在上或没上的时候返回 null，那会儿该看的是课。
 *
 * @param holidayNameRes 内置假日名称资源（[com.x500x.cursimple.core.kernel.model.ScheduleDayResolution.holidayNameRes]），
 *   认得出是哪个节就说那个节的话，认不出按普通假期说。
 * @param tomorrowHoliday 明天是否放假；不知道时传 null，相关的几类就不说。
 * @param timeSensitive 是否按钟点说话（深夜、晚上、周日晚上）。小组件不会准点刷新，
 *   挂在桌面上的「早点睡」到第二天中午还在就尴尬了，所以只有 App 里传 true。
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

/** 同一天、同一类话总是同一句；换一天或换一类就重新抽。 */
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
