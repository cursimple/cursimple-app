package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TodayOverviewTest {
    private val termStart = LocalDate.of(2026, 9, 7)
    private val profile = TermTimingProfile(termStart.toString(), listOf(
        ClassSlotTime(1, 2, "08:00", "09:00"),
        ClassSlotTime(3, 4, "09:30", "10:30"),
        ClassSlotTime(5, 6, "14:00", "15:00"),
    ))
    private fun course(id: String, node: Int = 1, weeks: List<Int> = emptyList()) = CourseItem(
        id, id, teacher = "老师", location = "默认教室", weeks = weeks, time = CourseTimeSlot(1, node, node + 1),
    )
    private fun overview(
        at: String = "2026-09-07T07:00",
        courses: List<CourseItem> = listOf(course("a"), course("b", 3)),
        timing: TermTimingProfile? = profile,
        start: LocalDate? = termStart,
        overrides: List<TemporaryScheduleOverride> = emptyList(),
        holidays: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
    ) = buildTodayOverview(LocalDateTime.parse(at), courses, timing, start, overrides, holidays)

    @Test fun `current class progress and remaining exclude the class already started`() {
        val state = overview(at = "2026-09-07T08:30")
        assertEquals("a", state.current?.course?.id)
        assertEquals("b", state.next?.course?.id)
        assertEquals(0.5f, state.currentProgress, 0.001f)
        assertEquals(30L, state.currentMinutesLeft)
        assertEquals(1, state.remaining)
        assertEquals(2, state.total)
    }

    @Test fun `countdown rounds remaining seconds up and exact boundaries change state`() {
        assertEquals(1L, overview(at = "2026-09-07T07:59:59").minutesUntilNext)
        assertEquals("a", overview(at = "2026-09-07T08:00").current?.course?.id)
        val after = overview(at = "2026-09-07T09:00")
        assertNull(after.current)
        assertEquals(30L, after.minutesUntilNext)
    }

    @Test fun `classes without timing are retained rather than reported as no classes`() {
        val state = overview(timing = null)
        assertEquals(2, state.total)
        assertEquals(2, state.unknownTimeCount)
        assertNull(state.current)
        assertNull(state.next)
        assertNull(state.minutesUntilNext)
    }

    @Test fun `invalid and reversed times remain untimed without corrupting other classes`() {
        val bad = profile.copy(slotTimes = listOf(ClassSlotTime(1, 2, "broken", "07:00"), ClassSlotTime(3, 4, "10:30", "09:30")))
        assertEquals(2, overview(timing = bad).unknownTimeCount)
    }

    @Test fun `explicit course times provide fallback when slots are absent`() {
        val direct = course("direct").copy(reminderStartTime = "08:00", reminderEndTime = "09:00")
        val state = overview(courses = listOf(direct), timing = null)
        assertEquals(0, state.unknownTimeCount)
        assertEquals(60L, state.minutesUntilNext)
    }

    @Test fun `imported slot and explicit times tolerate surrounding whitespace`() {
        val spaced = profile.copy(slotTimes = listOf(ClassSlotTime(1, 2, " 08:00 ", " 09:00 ")))
        assertEquals(60L, overview(courses = listOf(course("slot")), timing = spaced).minutesUntilNext)
        val direct = course("direct").copy(reminderStartTime = " 08:00 ", reminderEndTime = " 09:00 ")
        assertEquals(60L, overview(courses = listOf(direct), timing = null).minutesUntilNext)
    }

    @Test fun `hidden and reminder only courses never leak into overview`() {
        val state = overview(courses = listOf(course("real"), course("hidden").copy(hidden = true), course("placeholder").copy(reminderOnly = true)))
        assertEquals(listOf("real"), state.courses.map { it.course.id })
    }

    @Test fun `only actual teaching week appears and locations follow that week`() {
        val odd = course("odd", weeks = listOf(1, 3)).copy(weekLocations = mapOf(1 to "第一周教室"))
        val even = course("even", weeks = listOf(2, 4))
        val state = overview(courses = listOf(odd, even))
        assertEquals(1, state.total)
        assertEquals("第一周教室", state.next?.location)
        assertTrue(state.conflicts.isEmpty())
        assertEquals("even", overview(at = "2026-09-14T07:00", courses = listOf(odd, even)).next?.course?.id)
    }

    @Test fun `moving from another week uses original week's location and creates real day conflict`() {
        val old = course("moved", weeks = listOf(1)).copy(weekLocations = mapOf(1 to "原周教室", 2 to "目标周教室"))
        val move = TemporaryScheduleOverride(
            "move", TemporaryScheduleOverrideType.MoveCourse, sourceDate = "2026-09-07", targetDate = "2026-09-14",
            moveCourseId = "moved", moveToStartNode = 3, moveToEndNode = 4,
        )
        val state = overview(at = "2026-09-14T07:00", courses = listOf(old, course("target", 3, listOf(2))), overrides = listOf(move))
        assertEquals(2, state.total)
        assertEquals("原周教室", state.courses.first { it.course.id == "moved" }.location)
        assertEquals(1, state.conflicts.size)
    }

    @Test fun `actual overlapping clock times conflict even when node numbers differ`() {
        val overlap = profile.copy(slotTimes = listOf(ClassSlotTime(1, 2, "08:00", "09:00"), ClassSlotTime(3, 4, "08:30", "09:30")))
        assertEquals(1, overview(timing = overlap).conflicts.size)
        assertTrue(overview().conflicts.isEmpty())
    }

    @Test fun `adjacent clock times do not create a conflict`() {
        val adjacent = profile.copy(slotTimes = listOf(ClassSlotTime(1, 2, "08:00", "09:00"), ClassSlotTime(3, 4, "09:00", "10:00")))
        assertTrue(overview(timing = adjacent).conflicts.isEmpty())
    }

    @Test fun `holiday and cancellation remove courses but workday override restores them`() {
        val holiday = HolidayCalendarSettings(false, listOf(HolidayCalendarEntry(termStart.toString(), HolidayEntryKind.Holiday)))
        assertEquals(0, overview(holidays = holiday).total)
        assertTrue(overview(holidays = holiday).holiday)
        val workday = HolidayCalendarSettings(false, listOf(HolidayCalendarEntry(termStart.toString(), HolidayEntryKind.Workday)))
        assertEquals(2, overview(holidays = workday).total)
        val cancel = TemporaryScheduleOverride("cancel", TemporaryScheduleOverrideType.CancelCourse, targetDate = termStart.toString(), cancelCourseId = "a", cancelStartNode = 1, cancelEndNode = 2)
        assertEquals(listOf("b"), overview(overrides = listOf(cancel)).courses.map { it.course.id })
    }

    @Test fun `missing term start explicitly reports unknown week`() {
        assertFalse(overview(start = null).weekKnown)
        assertTrue(overview().weekKnown)
    }

    @Test fun `completed courses retain their daily total without reporting upcoming classes`() {
        val state = overview(at = "2026-09-07T16:00")
        assertEquals(2, state.total)
        assertEquals(0, state.remaining)
        assertNull(state.next)
        assertNull(state.current)
    }
}
