package com.kebiao.viewer.core.reminder

import com.kebiao.viewer.core.kernel.model.ClassSlotTime
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.CourseTimeSlot
import com.kebiao.viewer.core.kernel.model.DailySchedule
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
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
}
