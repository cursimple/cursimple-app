package com.x500x.cursimple.app.notice

import com.x500x.cursimple.core.data.EventReminderPreferences
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleEventReminderPlannerTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 9, 29, 9, 0).atZone(zone)
    private val event = ScheduleEvent("e", "组会", "2026-09-29", "10:30", "11:20")

    @Test
    fun oneOffUsesGlobalAdvanceMinutes() {
        val plan = ScheduleEventReminderPlanner.nextReminder(event, now, EventReminderPreferences(true, 20), zone)
        assertEquals(LocalDateTime.of(2026, 9, 29, 10, 10).atZone(zone).toInstant().toEpochMilli(), plan?.triggerAtMillis)
    }

    @Test
    fun eventOverrideWinsOverGlobalAndZeroMeansOnTime() {
        val override = event.copy(reminderMinutes = 0)
        val plan = ScheduleEventReminderPlanner.nextReminder(override, now, EventReminderPreferences(true, 20), zone)
        assertEquals(LocalDateTime.of(2026, 9, 29, 10, 30).atZone(zone).toInstant().toEpochMilli(), plan?.triggerAtMillis)
    }

    @Test
    fun globalOffAndEventOffProduceNoPlan() {
        assertNull(ScheduleEventReminderPlanner.nextReminder(event, now, EventReminderPreferences(false, 20), zone))
        assertNull(ScheduleEventReminderPlanner.nextReminder(event.copy(reminderMinutes = -1), now, EventReminderPreferences(true, 20), zone))
    }

    @Test
    fun weeklyEventUsesNextFutureOccurrence() {
        val weekly = event.copy(repeatWeekly = true, repeatUntil = "2026-10-20")
        val plan = ScheduleEventReminderPlanner.nextReminder(weekly, eventTime("2026-10-06", "11:00"), EventReminderPreferences(true, 20), zone)
        assertEquals(LocalDateTime.of(2026, 10, 13, 10, 10).atZone(zone).toInstant().toEpochMilli(), plan?.triggerAtMillis)
    }

    @Test
    fun occurrencePlanCanResolveTheAlarmThatIsCurrentlyFiring() {
        val plan = ScheduleEventReminderPlanner.planForOccurrence(
            event,
            "2026-09-29",
            EventReminderPreferences(true, 20),
            zone,
        )
        assertEquals("2026-09-29", plan?.occurrenceDate)
        assertEquals(20, plan?.advanceMinutes)
    }

    private fun eventTime(date: String, time: String) =
        LocalDateTime.parse("${date}T$time").atZone(zone)
}
