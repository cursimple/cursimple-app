package com.x500x.cursimple.app.reminder

import android.content.Context
import com.x500x.cursimple.app.ClassScheduleApplication
import com.x500x.cursimple.app.notice.ClassNoticeGateway
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import com.x500x.cursimple.core.reminder.model.ReminderSyncReason

object AlarmRuntimeMaintenance {
    suspend fun onAlarmStarted(context: Context) {
        runCatching { AutoSilenceController.evaluate(context, reason = "alarm_started") }
        runCatching { ClassNoticeGateway.reschedule(context) }
            .onFailure { ReminderLogger.warn("class_notice.runtime_maintenance.failure", emptyMap(), it) }
        val app = context.applicationContext as? ClassScheduleApplication
        if (app == null) {
            ReminderLogger.warn("reminder.app_alarm_clock.runtime_maintenance.no_application", emptyMap())
            return
        }
        runCatching {
            app.appContainer.ensureAlarmRuntimeHealth()
            app.appContainer.runSharedAlarmIntegrityCheck(
                reason = ReminderSyncReason.AlarmRuntime,
                includeTomorrow = true,
                clearExpiredRecords = false,
            )
        }.onFailure { error ->
            ReminderLogger.warn(
                "reminder.app_alarm_clock.runtime_maintenance.started.failure",
                emptyMap(),
                error,
            )
        }
    }

    suspend fun onAlarmFinished(context: Context) {
        runCatching { AutoSilenceController.evaluate(context, reason = "alarm_finished") }
        val app = context.applicationContext as? ClassScheduleApplication
        if (app == null) {
            ReminderLogger.warn("reminder.app_alarm_clock.runtime_maintenance.no_application", emptyMap())
            return
        }
        runCatching {
            app.appContainer.runSharedAlarmIntegrityCheck(
                reason = ReminderSyncReason.AlarmRuntime,
                includeTomorrow = true,
            )
            app.appContainer.refreshWidgets()
        }.onFailure { error ->
            ReminderLogger.warn(
                "reminder.app_alarm_clock.runtime_maintenance.finished.failure",
                emptyMap(),
                error,
            )
        }
    }
}
