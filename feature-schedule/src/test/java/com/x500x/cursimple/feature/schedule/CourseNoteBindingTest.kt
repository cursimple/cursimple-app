package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.data.note.CourseNote
import com.x500x.cursimple.core.data.note.noteAnchor
import com.x500x.cursimple.core.data.note.resolveCourseNotes
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseNoteBindingTest {

    private fun course(
        id: String,
        title: String,
        dayOfWeek: Int = 1,
        startNode: Int = 1,
        endNode: Int = 2,
    ) = CourseItem(
        id = id,
        title = title,
        weeks = listOf(1, 2, 3),
        time = CourseTimeSlot(dayOfWeek = dayOfWeek, startNode = startNode, endNode = endNode),
    )

    private fun stateOf(schedule: TermSchedule?, manual: List<CourseItem>) =
        ScheduleUiState(schedule = schedule, manualCourses = manual)

    @Test
    fun `note matching covers both plugin schedule and manual courses`() {
        val synced = course("plugin-aaaa", "高等数学")
        val manual = course("manual-1", "自习", dayOfWeek = 5)
        val state = stateOf(
            schedule = TermSchedule(
                termId = "2026-spring",
                updatedAt = "2026-02-01T00:00:00+08:00",
                dailySchedules = listOf(DailySchedule(dayOfWeek = 1, courses = listOf(synced))),
            ),
            manual = listOf(manual),
        )

        assertEquals(listOf("plugin-aaaa", "manual-1"), state.noteMatchCourses().map { it.id })
    }

    @Test
    fun `note stays visible after a sync replaces the schedule with new ids`() {
        val before = course("plugin-aaaa", "高等数学")
        val notes = listOf(CourseNote(anchor = before.noteAnchor(), text = "带教材第二册", updatedAt = 1L))
        val after = before.copy(id = "plugin-bbbb")
        val state = stateOf(
            schedule = TermSchedule(
                termId = "2026-spring",
                updatedAt = "2026-03-01T00:00:00+08:00",
                dailySchedules = listOf(DailySchedule(dayOfWeek = 1, courses = listOf(after))),
            ),
            manual = emptyList(),
        )

        val index = resolveCourseNotes(state.noteMatchCourses(), notes)

        assertTrue(index.hasNote("plugin-bbbb"))
        assertEquals("带教材第二册", index.textOf("plugin-bbbb"))
    }
}
