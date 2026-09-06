package com.x500x.cursimple.core.reminder

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.reminder.model.ReminderDayPeriod
import com.x500x.cursimple.core.reminder.model.ReminderLabelAction
import com.x500x.cursimple.core.reminder.model.ReminderLabelActionType
import com.x500x.cursimple.core.reminder.model.ReminderLabelCondition
import com.x500x.cursimple.core.reminder.model.ReminderLabelPresence
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderScopeType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 提醒时刻要跟随设置里的应用时区。
 *
 * 人在别的时区、课表按学校时区走时，8 点的课必须在学校时区的 8 点响，
 * 而不是设备时区的 8 点。
 */
class ReminderPlannerZoneTest {
    private val planner = ReminderPlanner()

    @After
    fun tearDown() {
        BeijingTime.setOverrideZone(null)
    }

    private fun triggerMillisUnderZone(zone: ZoneId, rule: ReminderRule = labelRule()): Long {
        BeijingTime.setOverrideZone(zone)
        val plans = planner.expandRule(
            rule = rule,
            schedule = schedule(),
            timingProfile = profile(),
            fromDate = LocalDate.of(2026, 2, 23),
        )
        return plans.single().triggerAtMillis
    }

    @Test
    fun `the trigger follows the app time zone, not the device`() {
        val shanghai = triggerMillisUnderZone(ZoneId.of("Asia/Shanghai"))
        val london = triggerMillisUnderZone(ZoneId.of("Europe/London"))

        // 2026-02-23 提前 15 分钟，即当地 07:45
        val expectedShanghai = LocalDateTime.of(2026, 2, 23, 7, 45)
            .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        val expectedLondon = LocalDateTime.of(2026, 2, 23, 7, 45)
            .atZone(ZoneId.of("Europe/London")).toInstant().toEpochMilli()

        assertEquals(expectedShanghai, shanghai)
        assertEquals(expectedLondon, london)
    }

    @Test
    fun `two zones eight hours apart place the same class eight hours apart`() {
        val shanghai = triggerMillisUnderZone(ZoneId.of("Asia/Shanghai"))
        val london = triggerMillisUnderZone(ZoneId.of("Europe/London"))

        assertEquals(8 * 60 * 60 * 1000L, london - shanghai)
    }

    @Test
    fun `the period rule follows the app time zone too`() {
        val rule = periodRule()
        val shanghai = triggerMillisUnderZone(ZoneId.of("Asia/Shanghai"), rule)
        val london = triggerMillisUnderZone(ZoneId.of("Europe/London"), rule)

        assertEquals(
            LocalDateTime.of(2026, 2, 23, 7, 45)
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli(),
            shanghai,
        )
        assertEquals(8 * 60 * 60 * 1000L, london - shanghai)
    }

    private fun periodRule(): ReminderRule = ReminderRule(
        ruleId = "zone-period-rule",
        pluginId = "demo",
        scopeType = ReminderScopeType.FirstCourseOfPeriod,
        displayName = "Zone period rule",
        period = ReminderDayPeriod.Morning,
        advanceMinutes = 15,
        createdAt = "2026-02-23T00:00:00+08:00",
        updatedAt = "2026-02-23T00:00:00+08:00",
    )

    private fun labelRule(): ReminderRule = ReminderRule(
        ruleId = "zone-rule",
        pluginId = "demo",
        scopeType = ReminderScopeType.LabelRule,
        displayName = "Zone rule",
        labelConditions = listOf(ReminderLabelCondition("第一节课", ReminderLabelPresence.Exists)),
        labelActions = listOf(ReminderLabelAction("第一节课", ReminderLabelActionType.Remind)),
        advanceMinutes = 15,
        createdAt = "2026-02-23T00:00:00+08:00",
        updatedAt = "2026-02-23T00:00:00+08:00",
    )

    private fun schedule(): TermSchedule = TermSchedule(
        termId = "2026-spring",
        updatedAt = "2026-02-23T00:00:00+08:00",
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

    private fun profile(): TermTimingProfile = TermTimingProfile(
        termStartDate = "2026-02-23",
        slotTimes = listOf(ClassSlotTime(1, 2, "08:00", "09:35", "第一节课")),
    )
}
