package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverrideType
import com.x500x.cursimple.core.kernel.model.coursesMovedTo
import com.x500x.cursimple.core.kernel.model.resolveScheduleDay
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.coursesScheduledOn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleInteractionTest {

    private val termStart = LocalDate.of(2026, 3, 2)

    private fun course(id: String): CourseItem = CourseItem(
        id = id,
        title = id,
        time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
    )

    @Test
    fun `tapping a stacked cell selects every course in it`() {
        val cell = listOf(course("a"), course("b"))

        val afterFirstTap = toggleCellSelection(emptySet(), cell)
        assertEquals(setOf("a", "b"), afterFirstTap)

        val afterSecondTap = toggleCellSelection(afterFirstTap, cell)
        assertTrue(afterSecondTap.isEmpty())
    }

    @Test
    fun `tapping a partially selected cell completes the selection`() {
        val cell = listOf(course("a"), course("b"))

        assertEquals(setOf("a", "b"), toggleCellSelection(setOf("a"), cell))
    }

    @Test
    fun `tapping a single course cell keeps toggling that course`() {
        val cell = listOf(course("a"))

        assertEquals(setOf("a"), toggleCellSelection(emptySet(), cell))
        assertEquals(emptySet<String>(), toggleCellSelection(setOf("a"), cell))
        assertEquals(setOf("b"), toggleCellSelection(setOf("a", "b"), cell))
    }

    @Test
    fun `tapping an empty cell changes nothing`() {
        assertEquals(setOf("a"), toggleCellSelection(setOf("a"), emptyList()))
    }

    @Test
    fun `detail dialog week number follows the tapped date`() {
        assertEquals(5, detailWeekNumber(LocalDate.of(2026, 4, 1), termStart, emptyList()))
        assertEquals(6, detailWeekNumber(LocalDate.of(2026, 4, 8), termStart, emptyList()))
    }

    @Test
    fun `detail dialog week number follows the borrowed source date on a make-up day`() {
        val overrides = listOf(
            TemporaryScheduleOverride(
                id = "makeup-1",
                type = TemporaryScheduleOverrideType.MakeUp,
                targetDate = "2026-04-08",
                sourceDate = "2026-04-01",
            ),
        )

        assertEquals(5, detailWeekNumber(LocalDate.of(2026, 4, 8), termStart, overrides))
    }

    @Test
    fun `adding at a make-up class remains in the same visible cell under the day swap`() {
        val source = course("original").copy(time = CourseTimeSlot(2, 3, 5))
        val sunday = LocalDate.of(2026, 4, 12)
        val override = TemporaryScheduleOverride(id = "makeup", targetDate = sunday.toString(), sourceDate = "2026-04-07")
        assertEquals(LocalDate.of(2026, 4, 7), resolveScheduleDay(sunday, listOf(override), HolidayCalendarSettings.NONE).sourceDate)

        val position = quickAddPosition(source, sunday, listOf(override))
        val added = CourseItem("new", "New", time = position)
        assertEquals(CourseTimeSlot(2, 3, 5), position)
        assertEquals(setOf(source.id, added.id), coursesScheduledOn(sunday, listOf(source, added), listOf(override),
            HolidayCalendarSettings.NONE, null).map { it.id }.toSet())
        assertEquals(CourseTimeSlot(2, 3, 5), source.time)
    }

    @Test
    fun `adding beside a moved class retains destination periods rather than original ones`() {
        val source = course("original")
        val destination = LocalDate.of(2026, 4, 10)
        val move = TemporaryScheduleOverride(id = "move", type = TemporaryScheduleOverrideType.MoveCourse,
            sourceDate = "2026-04-06", targetDate = destination.toString(), moveCourseId = source.id,
            moveToStartNode = 5, moveToEndNode = 7)
        val displayed = coursesMovedTo(destination, listOf(move), { source }).single()

        assertEquals(CourseTimeSlot(5, 5, 7), quickAddPosition(displayed, destination))
        val added = CourseItem("new", "New", time = quickAddPosition(displayed, destination, listOf(move)))
        assertEquals(setOf(source.id, added.id), coursesScheduledOn(destination, listOf(source, added), listOf(move),
            HolidayCalendarSettings.NONE, null).map { it.id }.toSet())
        assertEquals(CourseTimeSlot(1, 1, 2), source.time)
    }

    @Test
    fun `partial day swaps use the source weekday inside the interval and actual weekday outside`() {
        val date = LocalDate.of(2026, 4, 10)
        val swap = TemporaryScheduleOverride(id = "partial", sourceDate = "2026-04-07", targetDate = date.toString(),
            makeUpStartNode = 1, makeUpEndNode = 2)
        val early = course("early").copy(time = CourseTimeSlot(2, 1, 2))
        val late = course("late").copy(time = CourseTimeSlot(5, 5, 7))
        assertEquals(CourseTimeSlot(2, 1, 2), quickAddPosition(early, date, listOf(swap)))
        assertEquals(CourseTimeSlot(5, 5, 7), quickAddPosition(late, date, listOf(swap)))
    }

    @Test
    fun `overlapping courses keep their individual full spans when choosing an add position`() {
        val date = LocalDate.of(2026, 4, 7)
        val short = course("short").copy(time = CourseTimeSlot(2, 1, 2))
        val long = course("long").copy(time = CourseTimeSlot(2, 1, 4))
        assertEquals(CourseTimeSlot(2, 1, 2), quickAddPosition(short, date))
        assertEquals(CourseTimeSlot(2, 1, 4), quickAddPosition(long, date))
    }

    @Test
    fun `empty schedule keeps a hint while the grid stays usable`() {
        assertNull(emptyScheduleHint(hasSchedule = true, hasAnyCourse = true, hasCoursesThisWeek = true))
        assertEquals(
            EmptyScheduleHint.NeedsSync,
            emptyScheduleHint(hasSchedule = false, hasAnyCourse = false, hasCoursesThisWeek = false),
        )
        assertEquals(
            EmptyScheduleHint.NoCourses,
            emptyScheduleHint(hasSchedule = true, hasAnyCourse = false, hasCoursesThisWeek = false),
        )
        assertEquals(
            EmptyScheduleHint.NoCourseThisWeek,
            emptyScheduleHint(hasSchedule = true, hasAnyCourse = true, hasCoursesThisWeek = false),
        )
    }

    @Test
    fun `bulk reminder message reports failures instead of claiming success`() {
        assertEquals(
            BulkReminderStatus.AllCreated(successCount = 3),
            bulkReminderStatusMessage(3, emptyList(), true),
        )

        assertEquals(
            BulkReminderStatus.PartiallyCreated(
                successCount = 2,
                failed = ReminderTitlePreview(titles = listOf("体育"), totalCount = 1),
                hasTimingProfile = true,
            ),
            bulkReminderStatusMessage(2, listOf("体育"), true),
        )

        assertEquals(
            BulkReminderStatus.NoneCreated(
                failed = ReminderTitlePreview(titles = listOf("体育", "军训"), totalCount = 2),
                hasTimingProfile = true,
            ),
            bulkReminderStatusMessage(0, listOf("体育", "军训"), true),
        )

        assertEquals(
            BulkReminderStatus.NoneCreated(
                failed = ReminderTitlePreview(titles = listOf("体育"), totalCount = 1),
                hasTimingProfile = false,
            ),
            bulkReminderStatusMessage(0, listOf("体育"), false),
        )

        assertEquals(
            BulkReminderStatus.NoneCreated(
                failed = ReminderTitlePreview(titles = listOf("a", "b", "c"), totalCount = 4),
                hasTimingProfile = true,
            ),
            bulkReminderStatusMessage(0, listOf("a", "b", "c", "d"), true),
        )
    }
}
