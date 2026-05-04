package com.kebiao.viewer.core.kernel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate

@Serializable
data class TemporaryScheduleOverride(
    @SerialName("id") val id: String,
    @SerialName("startDate") val startDate: String,
    @SerialName("endDate") val endDate: String,
    @SerialName("sourceDayOfWeek") val sourceDayOfWeek: Int,
)

fun TemporaryScheduleOverride.containsDate(date: LocalDate): Boolean {
    if (sourceDayOfWeek !in 1..7) return false
    val start = parseOverrideDate(startDate) ?: return false
    val end = parseOverrideDate(endDate) ?: return false
    val normalizedStart = minOf(start, end)
    val normalizedEnd = maxOf(start, end)
    return !date.isBefore(normalizedStart) && !date.isAfter(normalizedEnd)
}

fun matchingTemporaryScheduleOverride(
    date: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
): TemporaryScheduleOverride? {
    return overrides.asReversed().firstOrNull { it.containsDate(date) }
}

fun resolveTemporaryScheduleDayOfWeek(
    date: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
): Int {
    return matchingTemporaryScheduleOverride(date, overrides)?.sourceDayOfWeek
        ?: date.dayOfWeek.value
}

fun isTemporaryScheduleOverridden(
    date: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
): Boolean {
    return resolveTemporaryScheduleDayOfWeek(date, overrides) != date.dayOfWeek.value
}

fun weekdayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    7 -> "周日"
    else -> "周$dayOfWeek"
}

fun shortWeekdayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
    DayOfWeek.MONDAY.value -> "一"
    DayOfWeek.TUESDAY.value -> "二"
    DayOfWeek.WEDNESDAY.value -> "三"
    DayOfWeek.THURSDAY.value -> "四"
    DayOfWeek.FRIDAY.value -> "五"
    DayOfWeek.SATURDAY.value -> "六"
    DayOfWeek.SUNDAY.value -> "日"
    else -> dayOfWeek.toString()
}

private fun parseOverrideDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()
