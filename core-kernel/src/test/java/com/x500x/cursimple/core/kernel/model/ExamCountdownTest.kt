package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
