package com.x500x.cursimple.app.util

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SystemCalendarPlanTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val monday: LocalDate = LocalDate.of(2026, 9, 7)

    private val course = CourseItem(
        id = "c1",
        title = "高数",
        teacher = "张三",
        location = "东 13",
        time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
    )

    private fun occurrence(date: LocalDate, displaced: Boolean = false) = CourseOccurrence(
        date = date,
        start = date.atTime(8, 0),
        end = date.atTime(9, 40),
        displaced = displaced,
    )

    private fun planned(vararg occurrences: CourseOccurrence) =
        PlannedCourse(course = course, slotLabel = "第一大节", occurrences = occurrences.toList())

    @Test
    fun `a single occurrence needs no recurrence rule`() {
        val drafts = planned(occurrence(monday)).toCalendarDrafts(zone, "")

        assertEquals(1, drafts.size)
        assertNull(drafts.single().rrule)
        assertEquals(100L, drafts.single().durationMinutes)
    }

    @Test
    fun `an unbroken weekly run becomes one counted rule`() {
        val drafts = planned(
            occurrence(monday),
            occurrence(monday.plusWeeks(1)),
            occurrence(monday.plusWeeks(2)),
        ).toCalendarDrafts(zone, "")

        assertEquals(1, drafts.size)
        assertEquals("FREQ=WEEKLY;COUNT=3", drafts.single().rrule)
        assertTrue(drafts.single().exdatesUtc.isEmpty())
    }

    @Test
    fun `skipped weeks become excluded dates`() {
        val drafts = planned(
            occurrence(monday),
            occurrence(monday.plusWeeks(2)),
        ).toCalendarDrafts(zone, "")

        val draft = drafts.single()
        // 中间那一周没有课，规则跑到最后一次为止并把缺的那周排除掉
        assertEquals("FREQ=WEEKLY;UNTIL=20260921T000000Z", draft.rrule)
        assertEquals(listOf("20260914T000000Z"), draft.exdatesUtc)
    }

    @Test
    fun `a displaced occurrence becomes its own event`() {
        val drafts = planned(
            occurrence(monday),
            occurrence(monday.plusWeeks(1)),
            occurrence(monday.plusDays(3), displaced = true),
        ).toCalendarDrafts(zone, "")

        assertEquals(2, drafts.size)
        assertEquals("FREQ=WEEKLY;COUNT=2", drafts.first().rrule)
        assertNull(drafts.last().rrule)
        assertEquals(monday.plusDays(3).atTime(8, 0), drafts.last().start)
    }

    @Test
    fun `a course that only ever moved has no weekly event`() {
        val drafts = planned(occurrence(monday.plusDays(3), displaced = true)).toCalendarDrafts(zone, "")

        assertEquals(1, drafts.size)
        assertNull(drafts.single().rrule)
    }

    @Test
    fun `title location and description come from the course`() {
        val draft = planned(occurrence(monday)).toCalendarDrafts(zone, "教师：张三").single()

        assertEquals("高数", draft.title)
        assertEquals("东 13", draft.location)
        assertEquals("教师：张三", draft.description)
    }

    @Test
    fun `a class running past midnight keeps its real length`() {
        val late = PlannedCourse(
            course = course,
            slotLabel = null,
            occurrences = listOf(
                CourseOccurrence(
                    date = monday,
                    start = monday.atTime(23, 0),
                    end = monday.plusDays(1).atTime(0, 40),
                    displaced = false,
                ),
            ),
        )

        assertEquals(100L, late.toCalendarDrafts(zone, "").single().durationMinutes)
    }
}
