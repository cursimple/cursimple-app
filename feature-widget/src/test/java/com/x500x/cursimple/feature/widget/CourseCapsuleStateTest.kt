package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CourseCapsuleStateTest {
    @Test
    fun `tomorrow at a similar clock time is not starting in ten minutes`() {
        val row = row("tomorrow", "2026-09-08T08:00", "2026-09-08T09:00")
        val state = resolveCourseCapsuleState(listOf(row), at("2026-09-07T07:55"))!!
        assertFalse(state.urgent)
        assertFalse(state.inProgress)
    }

    @Test
    fun `warning uses exact positive duration including seconds`() {
        val rows = listOf(row("today", "2026-09-07T08:00", "2026-09-07T09:00"))
        assertFalse(resolveCourseCapsuleState(rows, at("2026-09-07T07:49:59"))!!.urgent)
        assertTrue(resolveCourseCapsuleState(rows, at("2026-09-07T07:50"))!!.urgent)
        assertTrue(resolveCourseCapsuleState(rows, at("2026-09-07T07:59:59"))!!.urgent)
        for (now in listOf("2026-09-07T08:00", "2026-09-07T08:00:30", "2026-09-07T08:30")) {
            val state = resolveCourseCapsuleState(rows, at(now))!!
            assertFalse(state.urgent)
            assertTrue(state.inProgress)
        }
        assertNull(resolveCourseCapsuleState(rows, at("2026-09-07T09:00")))
    }

    @Test
    fun `selection uses real start times and skips an ended focused row`() {
        val rows = listOf(
            row("ended", "2026-09-07T06:00", "2026-09-07T07:00").copy(isFocus = true),
            row("later", "2026-09-07T10:00", "2026-09-07T11:00"),
            row("next", "2026-09-07T08:00", "2026-09-07T09:00"),
        )
        val state = resolveCourseCapsuleState(rows, at("2026-09-07T07:55"))!!
        assertEquals("next", state.course.id)
        assertTrue(state.urgent)
    }

    @Test
    fun `live course has a live state instead of a next-course warning`() {
        val rows = listOf(
            row("next", "2026-09-07T08:10", "2026-09-07T09:00"),
            row("live", "2026-09-07T08:00", "2026-09-07T08:10"),
        )
        val state = resolveCourseCapsuleState(rows, at("2026-09-07T08:05"))!!
        assertEquals("live", state.course.id)
        assertTrue(state.inProgress)
        assertFalse(state.urgent)
    }

    @Test
    fun `undated rows do not infer urgency from display text`() {
        val undated = row("unknown", "2026-09-07T08:00", "2026-09-07T09:00")
            .copy(startAt = null, endAt = null)
        val state = resolveCourseCapsuleState(listOf(undated), at("2026-09-07T07:55"))!!
        assertFalse(state.urgent)
        assertFalse(state.inProgress)
        assertNull(resolveCourseCapsuleState(listOf(undated.copy(isPast = true)), at("2026-09-07T07:55")))
        assertNull(resolveCourseCapsuleState(emptyList(), at("2026-09-07T07:55")))
    }

    @Test
    fun `typed clocks use actual display date including moved courses without a profile`() {
        val course = CourseItem(
            id = "moved", title = "Math",
            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
            reminderStartTime = "08:00", reminderEndTime = "09:00",
        )
        val targetDate = LocalDate.parse("2026-09-08")
        val times = nextCourseDateTimes(course, null, targetDate)!!
        assertEquals(at("2026-09-08T08:00"), times.first)
        assertEquals(at("2026-09-08T09:00"), times.second)
    }

    @Test
    fun `period clocks provide dated times and missing timing stays unknown`() {
        val course = CourseItem(
            id = "period", title = "Math",
            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
        )
        val profile = TermTimingProfile(
            termStartDate = "2026-09-07",
            slotTimes = listOf(ClassSlotTime(1, 2, "08:00", "09:35")),
        )
        val date = LocalDate.parse("2026-09-07")
        val times = nextCourseDateTimes(course, profile, date)!!
        assertEquals(at("2026-09-07T08:00"), times.first)
        assertEquals(at("2026-09-07T09:35"), times.second)
        assertNull(nextCourseDateTimes(course, null, date))
    }

    private fun row(id: String, start: String, end: String) = NextCourseRow(
        id = id, label = "即将上课", period = "1–2", title = id,
        time = "08:00 – 09:00", sub = "A101", isFocus = false, isPast = false,
        startAt = at(start), endAt = at(end),
    )

    private fun at(value: String): LocalDateTime = LocalDateTime.parse(value)
}
