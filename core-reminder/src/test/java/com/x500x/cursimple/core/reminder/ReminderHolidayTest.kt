package com.x500x.cursimple.core.reminder

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.HolidayCalendarEntry
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.HolidayEntryKind
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverrideType
import com.x500x.cursimple.core.kernel.model.withEntry
import com.x500x.cursimple.core.reminder.model.ReminderDayPeriod
import com.x500x.cursimple.core.reminder.model.ReminderLabelAction
import com.x500x.cursimple.core.reminder.model.ReminderLabelActionType
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderScopeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.time.LocalDate
import org.junit.Test

class ReminderHolidayTest {
    private val planner = ReminderPlanner()
    private val fromDate = LocalDate.of(2026, 9, 7)

    @Test
    fun builtInHolidayRemovesCourseReminder() {
        val dates = planDates(holidayCalendar = HolidayCalendarSettings())

        assertFalse(dates.contains("10月5日"))
        assertTrue(dates.contains("9月28日"))
        assertTrue(dates.contains("10月12日"))
    }

    @Test
    fun disabledCalendarKeepsReminderOnHoliday() {
        val dates = planDates(holidayCalendar = HolidayCalendarSettings.NONE)

        assertTrue(dates.contains("10月5日"))
    }

    @Test
    fun userWorkdayEntryRestoresReminderOnBuiltInHoliday() {
        val calendar = HolidayCalendarSettings().withEntry(
            HolidayCalendarEntry("2026-10-05", HolidayEntryKind.Workday, "学校照常上课"),
        )

        assertTrue(planDates(holidayCalendar = calendar).contains("10月5日"))
    }

    @Test
    fun userHolidayEntryRemovesReminderOnOrdinaryDay() {
        val calendar = HolidayCalendarSettings().withEntry(
            HolidayCalendarEntry("2026-09-14", HolidayEntryKind.Holiday, "校运会"),
        )

        val dates = planDates(holidayCalendar = calendar)

        assertFalse(dates.contains("9月14日"))
        assertTrue(dates.contains("9月21日"))
    }

    @Test
    fun makeUpOverrideOnHolidayStillProducesReminder() {
        val dates = planDates(
            holidayCalendar = HolidayCalendarSettings(),
            overrides = listOf(mondayMakeUpOn("2026-10-03")),
        )

        assertTrue(dates.contains("10月3日"))
    }

    @Test
    fun userHolidayEntryBeatsMakeUpOverride() {
        val calendar = HolidayCalendarSettings().withEntry(
            HolidayCalendarEntry("2026-10-03", HolidayEntryKind.Holiday, "国庆节"),
        )

        val dates = planDates(
            holidayCalendar = calendar,
            overrides = listOf(mondayMakeUpOn("2026-10-03")),
        )

        assertFalse(dates.contains("10月3日"))
    }

    @Test
    fun labelRuleSkipsHolidayDates() {
        val plans = planner.expandRule(
            rule = labelRule(),
            schedule = schedule(),
            timingProfile = profile(),
            fromDate = fromDate,
            holidayCalendar = HolidayCalendarSettings(),
        )
        val dates = plans.map { it.message.substringBefore(' ') }

        assertFalse(dates.contains("10月5日"))
        assertTrue(dates.contains("9月28日"))
    }

    @Test
    fun firstCourseRuleSkipsHolidayDates() {
        val plans = planner.expandRule(
            rule = firstCourseRule(),
            schedule = schedule(),
            timingProfile = profile(),
            fromDate = fromDate,
            holidayCalendar = HolidayCalendarSettings(),
        )
        val dates = plans.map { it.message.substringBefore(' ') }

        assertFalse(dates.contains("10月5日"))
        assertTrue(dates.contains("9月28日"))
    }

    @Test
    fun holidayFilteringLeavesOtherOccurrencesUntouched() {
        val dates = planDates(holidayCalendar = HolidayCalendarSettings())

        assertEquals(listOf("9月7日", "9月14日", "9月21日", "9月28日", "10月12日"), dates)
    }

    private fun planDates(
        holidayCalendar: HolidayCalendarSettings,
        overrides: List<TemporaryScheduleOverride> = emptyList(),
    ): List<String> = planner.expandRule(
        rule = singleCourseRule(),
        schedule = schedule(),
        timingProfile = profile(),
        fromDate = fromDate,
        temporaryScheduleOverrides = overrides,
        holidayCalendar = holidayCalendar,
    ).map { it.message.substringBefore(' ') }

    /** 目标日按开学第一周的周一课表上课。 */
    private fun mondayMakeUpOn(targetDate: String): TemporaryScheduleOverride = TemporaryScheduleOverride(
        id = "makeup-$targetDate",
        type = TemporaryScheduleOverrideType.MakeUp,
        targetDate = targetDate,
        sourceDate = "2026-09-07",
    )

    private fun schedule(): TermSchedule = TermSchedule(
        termId = "2026-autumn",
        updatedAt = "2026-09-07T00:00:00+08:00",
        dailySchedules = listOf(
            DailySchedule(
                dayOfWeek = 1,
                courses = listOf(
                    CourseItem(
                        id = "math",
                        title = "高等数学",
                        weeks = listOf(1, 2, 3, 4, 5, 6),
                        time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
                    ),
                ),
            ),
        ),
    )

    private fun profile(): TermTimingProfile = TermTimingProfile(
        termStartDate = "2026-09-07",
        slotTimes = listOf(ClassSlotTime(1, 2, "08:00", "09:35", "第一节课")),
    )

    private fun singleCourseRule(): ReminderRule = ReminderRule(
        ruleId = "single",
        pluginId = "demo",
        scopeType = ReminderScopeType.SingleCourse,
        courseId = "math",
        advanceMinutes = 20,
        createdAt = "2026-09-07T00:00:00+08:00",
        updatedAt = "2026-09-07T00:00:00+08:00",
    )

    private fun labelRule(): ReminderRule = ReminderRule(
        ruleId = "label",
        pluginId = "demo",
        scopeType = ReminderScopeType.LabelRule,
        advanceMinutes = 20,
        labelActions = listOf(ReminderLabelAction("第一节课", ReminderLabelActionType.Remind)),
        createdAt = "2026-09-07T00:00:00+08:00",
        updatedAt = "2026-09-07T00:00:00+08:00",
    )

    private fun firstCourseRule(): ReminderRule = ReminderRule(
        ruleId = "first-course",
        pluginId = "demo",
        scopeType = ReminderScopeType.FirstCourseOfPeriod,
        period = ReminderDayPeriod.Morning,
        advanceMinutes = 20,
        periodStartNode = 1,
        periodEndNode = 4,
        createdAt = "2026-09-07T00:00:00+08:00",
        updatedAt = "2026-09-07T00:00:00+08:00",
    )
}
