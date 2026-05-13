package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.reminder.model.AppAlarmOperationMode
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
import com.x500x.cursimple.core.reminder.model.ReminderPlan
import com.x500x.cursimple.core.reminder.model.SystemAlarmRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderWidgetRecordsTest {
    @Test
    fun `snoozed app alarm record is included in reminder entries`() {
        val nowMillis = 1_000L
        val plan = reminderPlan(
            planId = "plan-normal",
            triggerAtMillis = 5_000L,
            title = "线性代数",
            message = "线性代数即将开始",
        )
        val snoozeRecord = systemAlarmRecord(
            alarmKey = "snooze",
            triggerAtMillis = 2_000L,
            operationMode = AppAlarmOperationMode.SnoozeForegroundService,
            displayTitle = "高等数学",
            displayMessage = "已延后 5 分钟",
        )
        val normalRecord = systemAlarmRecord(
            alarmKey = "normal",
            triggerAtMillis = 3_000L,
            operationMode = AppAlarmOperationMode.ForegroundService,
        )

        val entries = buildReminderWidgetEntries(
            plans = listOf(plan),
            records = listOf(snoozeRecord, normalRecord),
            nowMillis = nowMillis,
        )

        assertEquals(2, entries.size)
        assertEquals("高等数学", entries[0].title)
        assertEquals("已延后 5 分钟", entries[0].message)
        assertEquals("线性代数", entries[1].title)
    }

    @Test
    fun `expired snoozed app alarm record is hidden from reminder entries`() {
        val entries = buildReminderWidgetEntries(
            plans = emptyList(),
            records = listOf(
                systemAlarmRecord(
                    alarmKey = "expired",
                    triggerAtMillis = 900L,
                    operationMode = AppAlarmOperationMode.SnoozeForegroundService,
                ),
            ),
            nowMillis = 1_000L,
        )

        assertEquals(emptyList<ReminderWidgetEntry>(), entries)
    }

    @Test
    fun `snoozed app alarm record falls back to alarm label for title`() {
        val entries = buildReminderWidgetEntries(
            plans = emptyList(),
            records = listOf(
                systemAlarmRecord(
                    alarmKey = "snooze",
                    triggerAtMillis = 2_000L,
                    operationMode = AppAlarmOperationMode.SnoozeForegroundService,
                    displayTitle = null,
                    displayMessage = "",
                    alarmLabel = "课表提醒 · 大学英语 · 08:00",
                ),
            ),
            nowMillis = 1_000L,
        )

        assertEquals("课表提醒 · 大学英语 · 08:00", entries.single().title)
        assertEquals("已延后 5 分钟", entries.single().message)
    }

    private fun reminderPlan(
        planId: String,
        triggerAtMillis: Long,
        title: String,
        message: String,
    ): ReminderPlan = ReminderPlan(
        planId = planId,
        ruleId = "rule",
        pluginId = "demo",
        title = title,
        message = message,
        triggerAtMillis = triggerAtMillis,
        ringtoneUri = null,
        courseId = "course",
    )

    private fun systemAlarmRecord(
        alarmKey: String,
        triggerAtMillis: Long,
        operationMode: AppAlarmOperationMode,
        displayTitle: String? = "课程提醒",
        displayMessage: String? = "课程即将开始",
        alarmLabel: String? = null,
    ): SystemAlarmRecord = SystemAlarmRecord(
        alarmKey = alarmKey,
        ruleId = "rule",
        pluginId = "demo",
        planId = "plan-$alarmKey",
        courseId = "course",
        triggerAtMillis = triggerAtMillis,
        message = alarmLabel ?: "课表提醒",
        alarmLabel = alarmLabel,
        backend = ReminderAlarmBackend.AppAlarmClock,
        requestCode = alarmKey.hashCode() and Int.MAX_VALUE,
        operationMode = operationMode,
        displayTitle = displayTitle,
        displayMessage = displayMessage,
        createdAtMillis = 500L,
    )
}
