package com.x500x.cursimple.core.kernel.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * 以开学日所在周的周一为基准换算 [date] 的教学周，不做任何钳制。
 * 开学之前得到 0 或负数。
 */
fun resolveTermWeekNumber(termStart: LocalDate, date: LocalDate): Int {
    val termStartMonday = termStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val dateMonday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return ChronoUnit.WEEKS.between(termStartMonday, dateMonday).toInt() + 1
}

/** 周次小于 1 表示尚未开学。 */
fun isTermWeekNumberStarted(weekNumber: Int): Boolean = weekNumber >= 1

/**
 * 周次列表为空表示每周都有，但开学前一律不生效。
 */
fun isTermWeekNumberActive(weekNumber: Int, weeks: List<Int>): Boolean {
    if (!isTermWeekNumberStarted(weekNumber)) return false
    return weeks.isEmpty() || weekNumber in weeks
}

/** 课程在第 [weekNumber] 教学周是否上课。 */
fun CourseItem.isActiveInTermWeekNumber(weekNumber: Int): Boolean =
    isTermWeekNumberActive(weekNumber, weeks)

/**
 * 正在看的周是否就是当前教学周。
 * 未设置开学日期时没有当前周可言，开学之前同样没有。
 */
fun isCurrentTermWeek(termStart: LocalDate?, displayedWeekIndex: Int, currentWeekIndex: Int): Boolean =
    termStart != null &&
        isTermWeekNumberStarted(currentWeekIndex) &&
        displayedWeekIndex == currentWeekIndex
