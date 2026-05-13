package com.x500x.cursimple.feature.widget

import com.x500x.cursimple.core.reminder.model.AlarmDispatchChannel
import com.x500x.cursimple.core.reminder.model.AlarmDispatchResult
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
import com.x500x.cursimple.core.reminder.model.SystemAlarmSyncSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetAlarmRegistrationReadinessTest {
    @Test
    fun `existing app alarms are ready`() {
        val readiness = summarizeWidgetAlarmRegistration(
            summary = summary(skippedExistingCount = 2),
            backend = ReminderAlarmBackend.AppAlarmClock,
        )

        assertEquals(WidgetAlarmRegistrationStatus.Ready, readiness.status)
        assertEquals("App 自管闹钟", readiness.backendLabel)
    }

    @Test
    fun `newly created system clock alarms are repaired`() {
        val readiness = summarizeWidgetAlarmRegistration(
            summary = summary(
                submittedCount = 1,
                createdCount = 1,
                results = listOf(
                    AlarmDispatchResult(
                        channel = AlarmDispatchChannel.SystemClockApp,
                        succeeded = true,
                        message = "已创建",
                    ),
                ),
            ),
            backend = ReminderAlarmBackend.SystemClockApp,
        )

        assertEquals(WidgetAlarmRegistrationStatus.Repaired, readiness.status)
        assertEquals("系统时钟 App 闹钟", readiness.backendLabel)
    }

    @Test
    fun `dispatch or registry failures are failed`() {
        val readiness = summarizeWidgetAlarmRegistration(
            summary = summary(
                submittedCount = 1,
                createdCount = 1,
                registryWriteFailedCount = 1,
                results = listOf(
                    AlarmDispatchResult(
                        channel = AlarmDispatchChannel.AppAlarmClock,
                        succeeded = true,
                        message = "已设置",
                    ),
                ),
            ),
            backend = ReminderAlarmBackend.AppAlarmClock,
        )

        assertEquals(WidgetAlarmRegistrationStatus.Failed, readiness.status)
    }

    @Test
    fun `empty window does not need registration`() {
        val readiness = summarizeWidgetAlarmRegistration(
            summary = summary(),
            backend = ReminderAlarmBackend.AppAlarmClock,
        )

        assertEquals(WidgetAlarmRegistrationStatus.NotNeeded, readiness.status)
    }

    private fun summary(
        submittedCount: Int = 0,
        createdCount: Int = 0,
        skippedExistingCount: Int = 0,
        skippedUnrepresentableCount: Int = 0,
        registryWriteFailedCount: Int = 0,
        results: List<AlarmDispatchResult> = emptyList(),
    ): SystemAlarmSyncSummary = SystemAlarmSyncSummary(
        submittedCount = submittedCount,
        createdCount = createdCount,
        skippedExistingCount = skippedExistingCount,
        skippedUnrepresentableCount = skippedUnrepresentableCount,
        results = results,
        registryWriteFailedCount = registryWriteFailedCount,
    )
}
