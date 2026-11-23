package com.x500x.cursimple.core.kernel.model

import com.x500x.cursimple.core.kernel.time.WeekStartDay
import com.x500x.cursimple.core.kernel.time.displayWeekStartOf
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class CourseWeekPreview internal constructor(val firstCourseWeek: Int) {
    fun sourceWeek(displayWeek: Int): Int = firstCourseWeek + displayWeek - 1

    fun displayWeek(sourceWeek: Int): Int = sourceWeek - firstCourseWeek + 1

    fun sourceWeekForPageDate(reference: LocalDate, pageDate: LocalDate, weekStartDay: WeekStartDay): Int =
        firstCourseWeek + ChronoUnit.WEEKS.between(
            displayWeekStartOf(reference, weekStartDay),
            displayWeekStartOf(pageDate, weekStartDay),
        ).toInt()
}

fun courseWeekPreview(courses: List<CourseItem>): CourseWeekPreview {
    val first = courses.asSequence()
        .filterNot { it.hidden || it.purged || it.reminderOnly }
        .mapNotNull { course ->
            if (course.weeks.isEmpty()) 1 else course.weeks.filter { it > 0 }.minOrNull()
        }
        .minOrNull()
    return CourseWeekPreview(first ?: 1)
}
