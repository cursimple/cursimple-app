package com.x500x.cursimple.core.kernel.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Nonnegative days to an upcoming exam; zero means today. */
data class ExamCountdown(
    val course: CourseItem,
    val date: LocalDate,
    val daysRemaining: Long,
)

/** Return null for past [examDate]; the caller selects eligible course categories. */
fun examCountdownOrNull(course: CourseItem, examDate: LocalDate, today: LocalDate): ExamCountdown? {
    val days = ChronoUnit.DAYS.between(today, examDate)
    if (days < 0) return null
    return ExamCountdown(course = course, date = examDate, daysRemaining = days)
}
