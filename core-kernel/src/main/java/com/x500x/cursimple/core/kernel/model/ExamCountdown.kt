package com.x500x.cursimple.core.kernel.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * 一场考试与它距今的天数。
 * [daysRemaining] 为 0 表示就在今天，负数不会出现——已过去的场次不参与统计。
 */
data class ExamCountdown(
    val course: CourseItem,
    val date: LocalDate,
    val daysRemaining: Long,
)

/** 第 [weekNumber] 教学周的周一。 */
private fun termWeekMonday(termStart: LocalDate, weekNumber: Int): LocalDate =
    termStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .plusWeeks((weekNumber - 1).toLong())

/** 课程在第 [weekNumber] 教学周落在哪一天。 */
fun courseDateInTermWeek(course: CourseItem, termStart: LocalDate, weekNumber: Int): LocalDate =
    termWeekMonday(termStart, weekNumber)
        .plusDays((course.time.dayOfWeek - 1).coerceIn(0, 6).toLong())

/**
 * [today] 到 [examDate] 的倒计时，考试已经过去返回 null。
 * 不检查课程类别，调用方自行决定哪些课程算考试。
 */
fun examCountdownOrNull(course: CourseItem, examDate: LocalDate, today: LocalDate): ExamCountdown? {
    val days = ChronoUnit.DAYS.between(today, examDate)
    if (days < 0) return null
    return ExamCountdown(course = course, date = examDate, daysRemaining = days)
}

/**
 * [courses] 里尚未结束的考试，按日期升序，最多取 [limit] 场。
 *
 * 周次列表为空的考试按每周都考处理，展开成 1 到 [maxWeekCount] 周；
 * 同一门考试只保留最近的一场。只用于提醒的占位课程不计入。
 */
fun upcomingExamCountdowns(
    courses: List<CourseItem>,
    termStart: LocalDate,
    today: LocalDate,
    maxWeekCount: Int = DEFAULT_TERM_WEEK_COUNT,
    limit: Int = Int.MAX_VALUE,
): List<ExamCountdown> {
    if (limit <= 0) return emptyList()
    val bound = maxWeekCount.coerceAtLeast(0)
    return courses.asSequence()
        .distinctBy { it.id }
        .filter { it.category == CourseCategory.Exam && !it.reminderOnly }
        .mapNotNull { course ->
            val weeks = if (course.weeks.isEmpty()) {
                (1..bound).toList()
            } else {
                course.weeks.filter(::isTermWeekNumberStarted)
            }
            weeks.asSequence()
                .map { courseDateInTermWeek(course, termStart, it) }
                .filterNot { it.isBefore(today) }
                .minByOrNull { it.toEpochDay() }
                ?.let { examCountdownOrNull(course, it, today) }
        }
        .sortedWith(
            compareBy({ it.date.toEpochDay() }, { it.course.time.startNode }, { it.course.title }),
        )
        .take(limit)
        .toList()
}

/** 距今最近的一场考试，没有则返回 null。 */
fun nextExamCountdown(
    courses: List<CourseItem>,
    termStart: LocalDate,
    today: LocalDate,
    maxWeekCount: Int = DEFAULT_TERM_WEEK_COUNT,
): ExamCountdown? =
    upcomingExamCountdowns(courses, termStart, today, maxWeekCount, limit = 1).firstOrNull()
