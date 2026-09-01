package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExamCountdownTest {

    private val termStart: LocalDate = LocalDate.parse("2026-02-23")

    private fun exam(
        id: String,
        title: String = id,
        dayOfWeek: Int = 3,
        startNode: Int = 1,
        weeks: List<Int> = listOf(17),
        category: CourseCategory = CourseCategory.Exam,
        reminderOnly: Boolean = false,
    ) = CourseItem(
        id = id,
        title = title,
        weeks = weeks,
        category = category,
        reminderOnly = reminderOnly,
        time = CourseTimeSlot(dayOfWeek = dayOfWeek, startNode = startNode, endNode = startNode + 1),
    )

    @Test
    fun `a course lands on the weekday of the requested term week`() {
        val course = exam("e", dayOfWeek = 3, weeks = listOf(17))

        assertEquals(LocalDate.parse("2026-06-17"), courseDateInTermWeek(course, termStart, 17))
        assertEquals(LocalDate.parse("2026-02-25"), courseDateInTermWeek(course, termStart, 1))
    }

    @Test
    fun `term week resolution round trips with the occurrence date`() {
        val course = exam("e", dayOfWeek = 5)
        for (week in 1..20) {
            val date = courseDateInTermWeek(course, termStart, week)
            assertEquals(week, resolveTermWeekNumber(termStart, date))
        }
    }

    @Test
    fun `days remaining counts calendar days ahead`() {
        val course = exam("e")
        val countdown = examCountdownOrNull(course, LocalDate.parse("2026-06-17"), LocalDate.parse("2026-06-10"))!!

        assertEquals(7L, countdown.daysRemaining)
        assertEquals(LocalDate.parse("2026-06-17"), countdown.date)
    }

    @Test
    fun `an exam happening today has zero days remaining`() {
        val today = LocalDate.parse("2026-06-17")

        assertEquals(0L, examCountdownOrNull(exam("e"), today, today)!!.daysRemaining)
    }

    @Test
    fun `an exam already past is not counted down`() {
        assertNull(
            examCountdownOrNull(exam("e"), LocalDate.parse("2026-06-16"), LocalDate.parse("2026-06-17")),
        )
    }

    @Test
    fun `upcoming exams are sorted by date and exclude past ones`() {
        val courses = listOf(
            exam("late", title = "线性代数", dayOfWeek = 3, weeks = listOf(18)),
            exam("soon", title = "高等数学", dayOfWeek = 3, weeks = listOf(17)),
            exam("gone", title = "已考完", dayOfWeek = 3, weeks = listOf(10)),
            exam("class", title = "普通课", dayOfWeek = 3, weeks = listOf(17), category = CourseCategory.Course),
        )

        val upcoming = upcomingExamCountdowns(courses, termStart, LocalDate.parse("2026-06-10"))

        assertEquals(listOf("高等数学", "线性代数"), upcoming.map { it.course.title })
        assertEquals(listOf(7L, 14L), upcoming.map { it.daysRemaining })
    }

    @Test
    fun `only the nearest remaining session of a repeated exam is kept`() {
        val course = exam("repeat", weeks = listOf(10, 17, 18))

        val upcoming = upcomingExamCountdowns(listOf(course), termStart, LocalDate.parse("2026-06-10"))

        assertEquals(1, upcoming.size)
        assertEquals(LocalDate.parse("2026-06-17"), upcoming.single().date)
    }

    @Test
    fun `an exam without a week list is treated as every week`() {
        val course = exam("everyWeek", weeks = emptyList())

        val upcoming = upcomingExamCountdowns(listOf(course), termStart, LocalDate.parse("2026-06-10"))

        assertEquals(LocalDate.parse("2026-06-10"), upcoming.single().date)
        assertEquals(0L, upcoming.single().daysRemaining)
    }

    @Test
    fun `reminder only placeholders are not counted down`() {
        val course = exam("placeholder", reminderOnly = true)

        assertTrue(upcomingExamCountdowns(listOf(course), termStart, LocalDate.parse("2026-06-10")).isEmpty())
    }

    @Test
    fun `next exam picks the closest one and returns null without any`() {
        val courses = listOf(
            exam("late", title = "线性代数", weeks = listOf(18)),
            exam("soon", title = "高等数学", weeks = listOf(17)),
        )

        assertEquals("高等数学", nextExamCountdown(courses, termStart, LocalDate.parse("2026-06-10"))?.course?.title)
        assertNull(nextExamCountdown(courses, termStart, LocalDate.parse("2026-07-20")))
    }

    @Test
    fun `limit caps how many exams are returned`() {
        val courses = (17..20).map { exam("e$it", title = "考试$it", weeks = listOf(it)) }

        assertEquals(2, upcomingExamCountdowns(courses, termStart, LocalDate.parse("2026-06-10"), limit = 2).size)
        assertTrue(upcomingExamCountdowns(courses, termStart, LocalDate.parse("2026-06-10"), limit = 0).isEmpty())
    }
}
