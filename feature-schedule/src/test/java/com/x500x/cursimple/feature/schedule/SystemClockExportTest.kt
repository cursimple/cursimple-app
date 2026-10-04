package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.reminder.AlarmDaySuppression
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
import com.x500x.cursimple.core.reminder.model.SystemAlarmRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class SystemClockExportTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 3)
    private val tomorrow = today.plusDays(1)

    private fun millis(date: LocalDate, hour: Int, minute: Int) =
        LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun record(
        key: String,
        triggerAtMillis: Long,
        backend: ReminderAlarmBackend = ReminderAlarmBackend.AppAlarmClock,
        enabled: Boolean = true,
    ) = SystemAlarmRecord(
        alarmKey = key,
        ruleId = "rule",
        pluginId = "plugin",
        planId = key,
        triggerAtMillis = triggerAtMillis,
        message = key,
        backend = backend,
        enabled = enabled,
        createdAtMillis = 0L,
    )

    private fun plan(
        records: List<SystemAlarmRecord>,
        now: Long,
        suppression: (SystemAlarmRecord) -> AlarmDaySuppression? = { null },
    ) = systemClockExportPlan(records, now, zone, zone, suppression)

    @Test
    fun eveningExportTakesAllOfTomorrowMorning() {
        val result = plan(
            listOf(
                record("a", millis(tomorrow, 7, 30)),
                record("b", millis(tomorrow, 9, 50)),
                record("today", millis(today, 22, 0)),
                record("dayAfter", millis(tomorrow.plusDays(1), 7, 30)),
            ),
            now = millis(today, 21, 0),
        )
        assertEquals(tomorrow, result.date)
        assertEquals(listOf(LocalTime.of(7, 30), LocalTime.of(9, 50)), result.writable.map { it.time })
        assertTrue(result.notYet.isEmpty())
        assertFalse(result.alreadyMuted)
    }

    @Test
    fun alarmsLaterThanNowWaitUntilTheirTimePasses() {
        val result = plan(
            listOf(
                record("a", millis(tomorrow, 7, 30)),
                record("b", millis(tomorrow, 14, 0)),
                record("c", millis(tomorrow, 16, 0)),
            ),
            now = millis(today, 10, 0),
        )
        assertEquals(listOf(LocalTime.of(7, 30)), result.writable.map { it.time })
        assertEquals(listOf(LocalTime.of(14, 0), LocalTime.of(16, 0)), result.notYet.map { it.time })
        assertEquals(LocalTime.of(16, 0), result.allWritableAfter)
    }

    @Test
    fun sameMinuteIsNotWritableYet() {
        val result = plan(
            listOf(record("a", millis(tomorrow, 10, 0))),
            now = millis(today, 10, 0) - 30_000L,
        )
        assertTrue(result.writable.isEmpty())
        assertEquals(1, result.notYet.size)
    }

    @Test
    fun alarmsInTheSameMinuteBecomeOne() {
        val result = plan(
            listOf(
                record("a", millis(tomorrow, 7, 30)),
                record("b", millis(tomorrow, 7, 30) + 20_000L),
            ),
            now = millis(today, 21, 0),
        )
        assertEquals(1, result.writable.size)
        assertEquals(listOf("a", "b"), result.writable.single().records.map { it.alarmKey })
    }

    @Test
    fun skipsDisabledLegacyAndHolidayAlarms() {
        val holiday = record("holiday", millis(tomorrow, 8, 0))
        val result = plan(
            listOf(
                record("off", millis(tomorrow, 7, 0), enabled = false),
                record("legacy", millis(tomorrow, 7, 10), backend = ReminderAlarmBackend.SystemClockApp),
                holiday,
                record("ok", millis(tomorrow, 9, 0)),
            ),
            now = millis(today, 21, 0),
            suppression = { if (it == holiday) AlarmDaySuppression.Holiday else null },
        )
        assertEquals(listOf("ok"), result.writable.flatMap { it.records }.map { it.alarmKey })
        assertFalse(result.alreadyMuted)
    }

    @Test
    fun mutedTomorrowHasNothingToWrite() {
        val result = plan(
            listOf(record("a", millis(tomorrow, 7, 30))),
            now = millis(today, 21, 0),
            suppression = { AlarmDaySuppression.MutedDate },
        )
        assertTrue(result.alreadyMuted)
        assertTrue(result.writable.isEmpty())
    }
}
