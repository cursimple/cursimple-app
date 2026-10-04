package com.x500x.cursimple.core.reminder

import com.x500x.cursimple.core.kernel.model.HolidayCalendarEntry
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.HolidayEntryKind
import com.x500x.cursimple.core.reminder.model.SystemAlarmRecord
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AlarmDayPolicyTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val holiday = LocalDate.of(2026, 10, 1)
    private fun at(day: LocalDate) = day.atTime(7, 30).atZone(zone).toInstant().toEpochMilli()
    private fun suppression(
        date: LocalDate = holiday,
        allow: Boolean = false,
        policy: ReminderDayPolicy = ReminderDayPolicy(),
        calendar: HolidayCalendarSettings = HolidayCalendarSettings(),
    ) = alarmDaySuppression(at(date), allow, zone, policy, calendar)

    @Test fun `holidays skip automatic and manual alarms by default`() {
        assertEquals(AlarmDaySuppression.Holiday, suppression())
        assertTrue(ReminderDayPolicy().skipOnHoliday)
    }

    @Test fun `explicit alarm exception and explicit global opt out allow ringing`() {
        assertNull(suppression(allow = true))
        assertNull(suppression(policy = ReminderDayPolicy.ALWAYS))
    }

    @Test fun `muted date takes priority even over holiday exception`() {
        assertEquals(AlarmDaySuppression.MutedDate, suppression(allow = true, policy = ReminderDayPolicy(mutedDates = setOf(holiday))))
    }

    @Test fun `ordinary weekdays and weekends are not automatically muted`() {
        assertNull(suppression(date = LocalDate.of(2026, 11, 2)))
        assertNull(suppression(date = LocalDate.of(2026, 11, 7)))
    }

    @Test fun `user workday overrides legal holiday and school holiday applies to ordinary day`() {
        assertNull(suppression(calendar = HolidayCalendarSettings(entries = listOf(HolidayCalendarEntry(holiday.toString(), HolidayEntryKind.Workday)))))
        val schoolHoliday = LocalDate.of(2026, 11, 2)
        assertEquals(AlarmDaySuppression.Holiday, suppression(date = schoolHoliday, calendar = HolidayCalendarSettings(
            builtInEnabled = false, entries = listOf(HolidayCalendarEntry(schoolHoliday.toString(), HolidayEntryKind.Holiday, "校庆")),
        )))
    }

    @Test fun `ringing date follows app timezone around midnight`() {
        val epoch = holiday.atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(AlarmDaySuppression.Holiday, alarmDaySuppression(epoch, false, zone, ReminderDayPolicy(), HolidayCalendarSettings()))
        assertNull(alarmDaySuppression(epoch - 1, false, zone, ReminderDayPolicy(), HolidayCalendarSettings()))
    }

    @Test fun `old alarm records do not implicitly allow holiday ringing`() {
        val record = Json { ignoreUnknownKeys = true }.decodeFromString<SystemAlarmRecord>(
            """{"alarmKey":"old","ruleId":"r","pluginId":"p","planId":"x","triggerAtMillis":1,"message":"alarm","createdAtMillis":1,"manualAlarm":true}""",
        )
        assertFalse(record.allowOnHoliday)
    }
}
