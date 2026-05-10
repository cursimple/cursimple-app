package com.kebiao.viewer.core.kernel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Serializable
data class TemporaryScheduleOverride(
    @SerialName("id") val id: String,
    @SerialName("type") val type: TemporaryScheduleOverrideType = TemporaryScheduleOverrideType.MakeUp,
    @SerialName("targetDate") val targetDate: String = "",
    @SerialName("sourceDate") val sourceDate: String = "",
    @SerialName("startDate") val startDate: String = "",
    @SerialName("endDate") val endDate: String = "",
    @SerialName("sourceDayOfWeek") val sourceDayOfWeek: Int? = null,
    @SerialName("cancelStartNode") val cancelStartNode: Int? = null,
    @SerialName("cancelEndNode") val cancelEndNode: Int? = null,
    @SerialName("cancelCourseId") val cancelCourseId: String? = null,
)

@Serializable
enum class TemporaryScheduleOverrideType {
    @SerialName("make_up")
    MakeUp,

    @SerialName("cancel_course")
    CancelCourse,
}

fun TemporaryScheduleOverride.containsDate(date: LocalDate): Boolean {
    return sourceDateFor(date) != null
}

fun TemporaryScheduleOverride.sourceDateFor(date: LocalDate): LocalDate? {
    if (type != TemporaryScheduleOverrideType.MakeUp) return null

    val explicitTarget = parseOverrideDate(targetDate)
    val explicitSource = parseOverrideDate(sourceDate)
    if (explicitTarget != null || explicitSource != null) {
        return if (explicitTarget == date) explicitSource else null
    }

    val legacySourceDayOfWeek = sourceDayOfWeek?.takeIf { it in 1..7 } ?: return null
    val start = parseOverrideDate(startDate) ?: return null
    val end = parseOverrideDate(endDate) ?: return null
    val normalizedStart = minOf(start, end)
    val normalizedEnd = maxOf(start, end)
    if (date.isBefore(normalizedStart) || date.isAfter(normalizedEnd)) return null
    return date
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .plusDays((legacySourceDayOfWeek - 1).toLong())
}

fun TemporaryScheduleOverride.targetDates(): List<LocalDate> {
    val explicitTarget = parseOverrideDate(targetDate)
    if (explicitTarget != null) return listOf(explicitTarget)

    val start = parseOverrideDate(startDate) ?: return emptyList()
    val end = parseOverrideDate(endDate) ?: return emptyList()
    val normalizedStart = minOf(start, end)
    val normalizedEnd = maxOf(start, end)
    val days = ChronoUnit.DAYS.between(normalizedStart, normalizedEnd).toInt()
    return (0..days).map { offset -> normalizedStart.plusDays(offset.toLong()) }
}

fun TemporaryScheduleOverride.cancelsCourseOn(date: LocalDate, course: CourseItem): Boolean {
    if (type != TemporaryScheduleOverrideType.CancelCourse) return false
    if (date !in targetDates()) return false
    val requiredCourseId = cancelCourseId?.takeIf { it.isNotBlank() }
    if (requiredCourseId != null && requiredCourseId != course.id) return false
    val start = cancelStartNode ?: return false
    val end = cancelEndNode ?: start
    val normalizedStart = minOf(start, end)
    val normalizedEnd = maxOf(start, end)
    return course.time.startNode <= normalizedEnd && course.time.endNode >= normalizedStart
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
    return resolveTemporaryScheduleSourceDate(date, overrides).dayOfWeek.value
}

fun resolveTemporaryScheduleSourceDate(
    date: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
): LocalDate {
    return matchingTemporaryScheduleOverride(date, overrides)?.sourceDateFor(date) ?: date
}

fun isTemporaryScheduleOverridden(
    date: LocalDate,
    overrides: List<TemporaryScheduleOverride>,
): Boolean {
    return resolveTemporaryScheduleSourceDate(date, overrides) != date
}

fun isCourseTemporarilyCancelled(
    date: LocalDate,
    course: CourseItem,
    overrides: List<TemporaryScheduleOverride>,
): Boolean {
    return overrides.any { it.cancelsCourseOn(date, course) }
}

fun filterTemporaryCancelledCourses(
    date: LocalDate,
    courses: List<CourseItem>,
    overrides: List<TemporaryScheduleOverride>,
): List<CourseItem> {
    if (overrides.none { it.type == TemporaryScheduleOverrideType.CancelCourse }) return courses
    return courses.filterNot { course -> isCourseTemporarilyCancelled(date, course, overrides) }
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
