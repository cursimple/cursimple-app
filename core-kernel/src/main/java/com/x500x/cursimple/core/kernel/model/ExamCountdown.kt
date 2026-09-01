package com.x500x.cursimple.core.kernel.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 一场考试与它距今的天数。
 * [daysRemaining] 为 0 表示就在今天，负数不会出现——已过去的场次不参与统计。
 */
data class ExamCountdown(
    val course: CourseItem,
    val date: LocalDate,
    val daysRemaining: Long,
)

/**
 * [today] 到 [examDate] 的倒计时，考试已经过去返回 null。
 * 不检查课程类别，调用方自行决定哪些课程算考试。
 */
fun examCountdownOrNull(course: CourseItem, examDate: LocalDate, today: LocalDate): ExamCountdown? {
    val days = ChronoUnit.DAYS.between(today, examDate)
    if (days < 0) return null
    return ExamCountdown(course = course, date = examDate, daysRemaining = days)
}
