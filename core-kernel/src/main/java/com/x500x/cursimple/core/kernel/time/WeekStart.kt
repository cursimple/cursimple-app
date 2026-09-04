package com.x500x.cursimple.core.kernel.time

import com.x500x.cursimple.core.kernel.model.resolveTermWeekNumber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 课表一周从哪天开始显示。
 *
 * 只影响显示窗口的排列，不影响教学周编号：第几周永远以周一为界，
 * 否则已存课程的周次含义会整体漂移。
 */
enum class WeekStartDay(val dayOfWeek: DayOfWeek) {
    Monday(DayOfWeek.MONDAY),
    Sunday(DayOfWeek.SUNDAY),
}

/** [date] 所在显示窗口的第一天。 */
fun displayWeekStartOf(date: LocalDate, weekStart: WeekStartDay): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(weekStart.dayOfWeek))

/**
 * 显示窗口内的周一。
 * 周日起时窗口第一天是周日，它属于上一个教学周，取窗口内的周一才是这一页代表的教学周。
 */
fun displayWeekAnchorMonday(displayWeekStart: LocalDate): LocalDate =
    displayWeekStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))

/** 显示窗口对应的教学周编号。 */
fun displayWeekTermIndex(termStart: LocalDate, displayWeekStart: LocalDate): Int =
    resolveTermWeekNumber(termStart, displayWeekAnchorMonday(displayWeekStart))

/**
 * 按列序排列的星期值（1 为周一，7 为周日）。
 *
 * 返回星期本身而不是列下标，因为起始日一变「下标 + 1 = 星期」就不再成立。
 * 隐藏周末时不论起始日都从周一排起：周日既然不显示，就没有把它放在最前的余地。
 */
fun columnDayOfWeeks(
    weekStart: WeekStartDay,
    weekendVisible: Boolean,
    saturdayVisible: Boolean,
): List<Int> = when {
    !weekendVisible && !saturdayVisible -> (1..5).toList()
    !weekendVisible -> (1..6).toList()
    weekStart == WeekStartDay.Sunday -> listOf(7) + (1..6).toList()
    else -> (1..7).toList()
}

/** 星期值在显示窗口里对应的日期。 */
fun columnDate(displayWeekStart: LocalDate, dayOfWeek: Int): LocalDate {
    val offset = (dayOfWeek - displayWeekStart.dayOfWeek.value + 7) % 7
    return displayWeekStart.plusDays(offset.toLong())
}

/** 课表纵向排布：整屏平铺全部节次，或固定行高纵向滚动。 */
enum class ScheduleRowFitMode {
    Fit,
    Scroll,
}
