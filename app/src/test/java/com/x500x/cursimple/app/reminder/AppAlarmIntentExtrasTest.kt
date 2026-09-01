package com.x500x.cursimple.app.reminder

import com.x500x.cursimple.core.reminder.dispatch.AppAlarmClockIntents
import com.x500x.cursimple.core.reminder.model.AlarmAlertMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppAlarmIntentExtrasTest {
    @Test
    fun `alarm extras round trip keeps every field`() {
        val alarm = sampleAlarm()

        val restored = activeAlarmFromExtras(alarm.toAlarmExtras()::get)

        assertEquals(alarm, restored)
    }

    @Test
    fun `forwarded alarm extras keep alert mode and repeat settings`() {
        val source = sampleAlarm().toAlarmExtras()

        val forwarded = activeAlarmFromExtras(source::get).toAlarmExtras()

        assertEquals(source, forwarded)
        assertEquals(AlarmAlertMode.VibrateOnly.name, forwarded[AppAlarmClockIntents.EXTRA_ALERT_MODE])
        assertEquals(30, forwarded[AppAlarmClockIntents.EXTRA_RING_DURATION_SECONDS])
        assertEquals(90, forwarded[AppAlarmClockIntents.EXTRA_REPEAT_INTERVAL_SECONDS])
        assertEquals(3, forwarded[AppAlarmClockIntents.EXTRA_REPEAT_COUNT])
    }

    @Test
    fun `alarm extras cover every declared extra key`() {
        val declaredKeys = AppAlarmClockIntents::class.java.declaredFields
            .filter { it.name.startsWith("EXTRA_") }
            .map { field ->
                field.isAccessible = true
                field.get(null) as String
            }
            .toSet()

        assertEquals(declaredKeys, sampleAlarm().toAlarmExtras().keys)
    }

    @Test
    fun `missing optional extras fall back to null`() {
        val minimal = mapOf(
            AppAlarmClockIntents.EXTRA_ALARM_KEY to "alarm-1",
            AppAlarmClockIntents.EXTRA_RULE_ID to "rule",
        )

        val alarm = activeAlarmFromExtras(minimal::get)

        assertEquals("alarm-1", alarm.alarmKey)
        assertEquals(0L, alarm.triggerAtMillis)
        assertNull(alarm.courseId)
        assertNull(alarm.alertMode)
        assertNull(alarm.ringDurationSeconds)
        assertNull(alarm.repeatIntervalSeconds)
        assertNull(alarm.repeatCount)
    }

    private fun sampleAlarm(): ActiveAlarm = ActiveAlarm(
        alarmKey = "alarm-1",
        ruleId = "rule",
        pluginId = "demo",
        planId = "plan",
        courseId = "math",
        title = "高等数学",
        message = "课程即将开始",
        ringtoneUri = "content://alarm/custom",
        alertMode = AlarmAlertMode.VibrateOnly,
        triggerAtMillis = 1_772_000_000_000L,
        ringDurationSeconds = 30,
        repeatIntervalSeconds = 90,
        repeatCount = 3,
    )
}
