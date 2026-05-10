package com.kebiao.viewer.core.reminder

import com.kebiao.viewer.core.kernel.model.ClassSlotTime
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.CourseTimeSlot
import com.kebiao.viewer.core.kernel.model.DailySchedule
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.kernel.model.TemporaryScheduleOverride
import com.kebiao.viewer.core.reminder.model.ReminderDayPeriod
import com.kebiao.viewer.core.reminder.model.ReminderNodeRange
import com.kebiao.viewer.core.reminder.model.ReminderRule
import com.kebiao.viewer.core.reminder.model.ReminderScopeType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderPlannerTest {
    private val planner = ReminderPlanner()

    @Test
    fun expandsTimeSlotRuleAcrossWeek() {
        val schedule = TermSchedule(
            termId = "2026-spring",
            updatedAt = "2026-04-27T08:00:00+08:00",
            dailySchedules = listOf(
                DailySchedule(
                    dayOfWeek = 1,
                    courses = listOf(
                        CourseItem(
                            id = "math",
                            title = "高等数学",
                            weeks = listOf(1, 2),
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
                        ),
                    ),
                ),
            ),
        )
        val profile = TermTimingProfile(
            termStartDate = "2026-02-23",
            slotTimes = listOf(
                ClassSlotTime(1, 2, "08:00", "09:35"),
            ),
        )
        val rule = ReminderRule(
            ruleId = "r1",
            pluginId = "demo",
            scopeType = ReminderScopeType.TimeSlot,
            dayOfWeek = 1,
            startNode = 1,
            endNode = 2,
            advanceMinutes = 15,
            createdAt = "2026-02-23T00:00:00+08:00",
            updatedAt = "2026-02-23T00:00:00+08:00",
        )

        val plans = planner.expandRule(rule, schedule, profile, fromDate = java.time.LocalDate.of(2026, 2, 23))

        assertEquals(2, plans.size)
    }

    @Test
    fun temporaryOverrideMovesReminderToActualMakeupDate() {
        val schedule = TermSchedule(
            termId = "2026-spring",
            updatedAt = "2026-04-27T08:00:00+08:00",
            dailySchedules = listOf(
                DailySchedule(
                    dayOfWeek = 1,
                    courses = listOf(
                        CourseItem(
                            id = "math",
                            title = "高等数学",
                            weeks = listOf(1),
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
                        ),
                    ),
                ),
            ),
        )
        val profile = TermTimingProfile(
            termStartDate = "2026-02-23",
            slotTimes = listOf(
                ClassSlotTime(1, 2, "08:00", "09:35"),
            ),
        )
        val rule = ReminderRule(
            ruleId = "r1",
            pluginId = "demo",
            scopeType = ReminderScopeType.SingleCourse,
            courseId = "math",
            advanceMinutes = 15,
            createdAt = "2026-02-23T00:00:00+08:00",
            updatedAt = "2026-02-23T00:00:00+08:00",
        )

        val plans = planner.expandRule(
            rule = rule,
            schedule = schedule,
            timingProfile = profile,
            fromDate = java.time.LocalDate.of(2026, 2, 23),
            temporaryScheduleOverrides = listOf(
                TemporaryScheduleOverride(
                    id = "holiday",
                    startDate = "2026-02-23",
                    endDate = "2026-02-23",
                    sourceDayOfWeek = 7,
                ),
                TemporaryScheduleOverride(
                    id = "makeup",
                    startDate = "2026-02-28",
                    endDate = "2026-02-28",
                    sourceDayOfWeek = 1,
                ),
            ),
        )

        assertEquals(1, plans.size)
        assertEquals(true, plans.single().message.startsWith("2月28日"))
    }

    @Test
    fun firstCourseOfPeriodRuleKeepsOnlyEarliestCourseInPeriod() {
        val schedule = TermSchedule(
            termId = "2026-spring",
            updatedAt = "2026-04-27T08:00:00+08:00",
            dailySchedules = listOf(
                DailySchedule(
                    dayOfWeek = 1,
                    courses = listOf(
                        CourseItem(
                            id = "late",
                            title = "线性代数",
                            weeks = listOf(1),
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 3, endNode = 4),
                        ),
                        CourseItem(
                            id = "early",
                            title = "高等数学",
                            weeks = listOf(1),
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
                        ),
                    ),
                ),
            ),
        )
        val profile = TermTimingProfile(
            termStartDate = "2026-02-23",
            slotTimes = listOf(
                ClassSlotTime(1, 2, "08:00", "09:35"),
                ClassSlotTime(3, 4, "10:00", "11:35"),
            ),
        )
        val rule = ReminderRule(
            ruleId = "morning",
            pluginId = "demo",
            scopeType = ReminderScopeType.FirstCourseOfPeriod,
            period = ReminderDayPeriod.Morning,
            advanceMinutes = 15,
            createdAt = "2026-02-23T00:00:00+08:00",
            updatedAt = "2026-02-23T00:00:00+08:00",
        )

        val plans = planner.expandRule(rule, schedule, profile, fromDate = java.time.LocalDate.of(2026, 2, 23))

        assertEquals(1, plans.size)
        assertEquals("early", plans.single().courseId)
    }

    @Test
    fun firstCourseMutedPreludeSuppressesPeriodWhenPreludeCourseExists() {
        val schedule = TermSchedule(
            termId = "2026-spring",
            updatedAt = "2026-04-27T08:00:00+08:00",
            dailySchedules = listOf(
                DailySchedule(
                    dayOfWeek = 1,
                    courses = listOf(
                        CourseItem(
                            id = "study",
                            title = "早自习",
                            weeks = listOf(1),
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 1),
                        ),
                        CourseItem(
                            id = "math",
                            title = "高等数学",
                            weeks = listOf(1),
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 2, endNode = 2),
                        ),
                    ),
                ),
            ),
        )
        val profile = TermTimingProfile(
            termStartDate = "2026-02-23",
            slotTimes = listOf(
                ClassSlotTime(1, 1, "07:20", "07:50"),
                ClassSlotTime(2, 2, "08:00", "08:45"),
            ),
        )
        val rule = ReminderRule(
            ruleId = "morning",
            pluginId = "demo",
            scopeType = ReminderScopeType.FirstCourseOfPeriod,
            period = ReminderDayPeriod.Morning,
            periodStartNode = 1,
            periodEndNode = 4,
            mutedNodeRanges = listOf(ReminderNodeRange(1, 1)),
            advanceMinutes = 15,
            createdAt = "2026-02-23T00:00:00+08:00",
            updatedAt = "2026-02-23T00:00:00+08:00",
        )

        val plans = planner.expandRule(rule, schedule, profile, fromDate = java.time.LocalDate.of(2026, 2, 23))

        assertEquals(0, plans.size)
    }

    @Test
    fun firstCourseMutedPreludeAllowsLaterCourseWhenPreludeIsEmpty() {
        val schedule = TermSchedule(
            termId = "2026-spring",
            updatedAt = "2026-04-27T08:00:00+08:00",
            dailySchedules = listOf(
                DailySchedule(
                    dayOfWeek = 1,
                    courses = listOf(
                        CourseItem(
                            id = "math",
                            title = "高等数学",
                            weeks = listOf(1),
                            time = CourseTimeSlot(dayOfWeek = 1, startNode = 2, endNode = 2),
                        ),
                    ),
                ),
            ),
        )
        val profile = TermTimingProfile(
            termStartDate = "2026-02-23",
            slotTimes = listOf(
                ClassSlotTime(1, 1, "07:20", "07:50"),
                ClassSlotTime(2, 2, "08:00", "08:45"),
            ),
        )
        val rule = ReminderRule(
            ruleId = "morning",
            pluginId = "demo",
            scopeType = ReminderScopeType.FirstCourseOfPeriod,
            period = ReminderDayPeriod.Morning,
            periodStartNode = 1,
            periodEndNode = 4,
            mutedNodeRanges = listOf(ReminderNodeRange(1, 1)),
            advanceMinutes = 15,
            createdAt = "2026-02-23T00:00:00+08:00",
            updatedAt = "2026-02-23T00:00:00+08:00",
        )

        val plans = planner.expandRule(rule, schedule, profile, fromDate = java.time.LocalDate.of(2026, 2, 23))

        assertEquals(1, plans.size)
        assertEquals("math", plans.single().courseId)
    }
}
