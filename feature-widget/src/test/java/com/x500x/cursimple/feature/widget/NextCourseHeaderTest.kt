package com.x500x.cursimple.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class NextCourseHeaderTest {
    private val today: LocalDate = LocalDate.of(2026, 9, 12)

    @Test
    fun `header keeps the plain day label without a temporary override`() {
        assertEquals("今日课程", nextCourseDayHeader(today, today, today))
        val tomorrow = today.plusDays(1)
        assertEquals("明日课程", nextCourseDayHeader(tomorrow, tomorrow, today))
    }

    @Test
    fun `header shows the source date when today follows another day's schedule`() {
        assertEquals(
            "今日课程 · 按9月7日周一",
            nextCourseDayHeader(today, LocalDate.of(2026, 9, 7), today),
        )
    }

    @Test
    fun `header shows the source date for tomorrow as well`() {
        val tomorrow = today.plusDays(1)
        assertEquals(
            "明日课程 · 按9月10日周四",
            nextCourseDayHeader(tomorrow, LocalDate.of(2026, 9, 10), today),
        )
    }
}
