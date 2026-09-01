package com.x500x.cursimple.core.reminder

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.reminder.model.ReminderLabelAction
import com.x500x.cursimple.core.reminder.model.ReminderLabelActionType
import com.x500x.cursimple.core.reminder.model.ReminderLabelCondition
import com.x500x.cursimple.core.reminder.model.ReminderLabelPresence
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderScopeType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** 开学日为周三（2026-09-09），其所在周的周一是 2026-09-07。 */
class TermWeekRulesTest {
    private val planner = ReminderPlanner()
    private val wednesdayTermStart = LocalDate.of(2026, 9, 9)
    private val mondayTermStart = LocalDate.of(2026, 9, 7)
    private val earlyFromDate = LocalDate.of(2026, 9, 1)

    @Test
    fun wednesdayTermStartKeepsCourseWeekdayForMondayCourse() {
        val dates = courseOccurrenceDates(
            course = course(id = "math", dayOfWeek = 1, weeks = listOf(1, 2, 3)),
            termStart = wednesdayTermStart,
            fromDate = earlyFromDate,
            temporaryScheduleOverrides = emptyList(),
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 21),
            ),
            dates,
        )
        assertEquals(listOf(1, 1, 1), dates.map { it.dayOfWeek.value })
    }

    @Test
    fun wednesdayTermStartKeepsCourseWeekdayForWednesdayCourse() {
        val dates = courseOccurrenceDates(
            course = course(id = "physics", dayOfWeek = 3, weeks = listOf(1, 2, 3)),
            termStart = wednesdayTermStart,
            fromDate = earlyFromDate,
            temporaryScheduleOverrides = emptyList(),
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 9),
                LocalDate.of(2026, 9, 16),
                LocalDate.of(2026, 9, 23),
            ),
            dates,
        )
        assertEquals(listOf(3, 3, 3), dates.map { it.dayOfWeek.value })
    }

    @Test
    fun wednesdayTermStartKeepsCourseWeekdayForFridayCourse() {
        val dates = courseOccurrenceDates(
            course = course(id = "english", dayOfWeek = 5, weeks = listOf(1, 2, 3)),
            termStart = wednesdayTermStart,
            fromDate = earlyFromDate,
            temporaryScheduleOverrides = emptyList(),
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 9, 18),
                LocalDate.of(2026, 9, 25),
            ),
            dates,
        )
        assertEquals(listOf(5, 5, 5), dates.map { it.dayOfWeek.value })
    }

    @Test
    fun firstWeekMondayCourseFallsOnMondayBeforeWednesdayTermStart() {
        val dates = courseOccurrenceDates(
            course = course(id = "math", dayOfWeek = 1, weeks = listOf(1)),
            termStart = wednesdayTermStart,
            fromDate = earlyFromDate,
            temporaryScheduleOverrides = emptyList(),
        )

        assertEquals(listOf(LocalDate.of(2026, 9, 7)), dates)
        assertEquals(true, dates.single().isBefore(wednesdayTermStart))
    }

    @Test
    fun occurrencesBeforeFromDateAreDropped() {
        val dates = courseOccurrenceDates(
            course = course(id = "math", dayOfWeek = 1, weeks = listOf(1, 2)),
            termStart = wednesdayTermStart,
            fromDate = wednesdayTermStart,
            temporaryScheduleOverrides = emptyList(),
        )

        assertEquals(listOf(LocalDate.of(2026, 9, 14)), dates)
    }

    @Test
    fun mondayTermStartKeepsCourseWeekday() {
        val monday = courseOccurrenceDates(
            course = course(id = "math", dayOfWeek = 1, weeks = listOf(1, 2, 3)),
            termStart = mondayTermStart,
            fromDate = earlyFromDate,
            temporaryScheduleOverrides = emptyList(),
        )
        val friday = courseOccurrenceDates(
            course = course(id = "english", dayOfWeek = 5, weeks = listOf(1, 2, 3)),
            termStart = mondayTermStart,
            fromDate = earlyFromDate,
            temporaryScheduleOverrides = emptyList(),
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 21),
            ),
            monday,
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 9, 18),
                LocalDate.of(2026, 9, 25),
            ),
            friday,
        )
    }

    @Test
    fun singleCourseRuleProducesPlansWhenTermStartIsWednesday() {
        val plans = planner.expandRule(
            rule = singleCourseRule("math"),
            schedule = weekdaySchedule(),
            timingProfile = wednesdayProfile(),
            fromDate = earlyFromDate,
        )

        assertEquals(
            listOf("9月7日", "9月14日", "9月21日"),
            plans.map { it.message.substringBefore(' ') },
        )
    }

    @Test
    fun labelRuleProducesPlansWhenTermStartIsWednesday() {
        val plans = planner.expandRule(
            rule = labelRule(),
            schedule = weekdaySchedule(),
            timingProfile = wednesdayProfile(),
            fromDate = earlyFromDate,
        )

        assertEquals(listOf("math", "math", "math"), plans.map { it.courseId })
        assertEquals(
            listOf("9月7日", "9月14日", "9月21日"),
            plans.map { it.message.substringBefore(' ') },
        )
    }

    private fun course(id: String, dayOfWeek: Int, weeks: List<Int>): CourseItem = CourseItem(
        id = id,
        title = id,
        weeks = weeks,
        time = CourseTimeSlot(dayOfWeek = dayOfWeek, startNode = 1, endNode = 2),
    )

    private fun weekdaySchedule(): TermSchedule = TermSchedule(
        termId = "2026-autumn",
        updatedAt = "2026-09-01T00:00:00+08:00",
        dailySchedules = listOf(
            DailySchedule(dayOfWeek = 1, courses = listOf(course("math", 1, listOf(1, 2, 3)))),
        ),
    )

    private fun wednesdayProfile(): TermTimingProfile = TermTimingProfile(
        termStartDate = "2026-09-09",
        slotTimes = listOf(
            ClassSlotTime(1, 2, "08:00", "09:35", "第一节课"),
        ),
    )

    private fun singleCourseRule(courseId: String): ReminderRule = ReminderRule(
        ruleId = "single-course",
        pluginId = "demo",
        scopeType = ReminderScopeType.SingleCourse,
        courseId = courseId,
        advanceMinutes = 15,
        createdAt = "2026-09-01T00:00:00+08:00",
        updatedAt = "2026-09-01T00:00:00+08:00",
    )

    private fun labelRule(): ReminderRule = ReminderRule(
        ruleId = "label-rule",
        pluginId = "demo",
        scopeType = ReminderScopeType.LabelRule,
        displayName = "第一节课提醒",
        labelConditions = listOf(ReminderLabelCondition("第一节课", ReminderLabelPresence.Exists)),
        labelActions = listOf(ReminderLabelAction("第一节课", ReminderLabelActionType.Remind)),
        advanceMinutes = 15,
        createdAt = "2026-09-01T00:00:00+08:00",
        updatedAt = "2026-09-01T00:00:00+08:00",
    )
}
