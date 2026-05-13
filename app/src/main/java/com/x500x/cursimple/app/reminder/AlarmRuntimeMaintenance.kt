package com.x500x.cursimple.app.reminder

import android.content.Context
import com.x500x.cursimple.app.ClassScheduleApplication
import com.x500x.cursimple.core.reminder.logging.ReminderLogger

object AlarmRuntimeMaintenance {
    suspend fun onAlarmStarted(context: Context) {
        val app = context.applicationContext as? ClassScheduleApplication
        if (app == null) {
            ReminderLogger.warn("reminder.app_alarm_clock.runtime_maintenance.no_application", emptyMap())
            return
        }
        runCatching {
            app.appContainer.ensureAlarmRuntimeHealth()
            app.appContainer.runAlarmFollowUpSync(clearExpiredRecords = false)
        }.onFailure { error ->
            ReminderLogger.warn(
                "reminder.app_alarm_clock.runtime_maintenance.started.failure",
                emptyMap(),
                error,
            )
        }
    }

    suspend fun onAlarmFinished(context: Context) {
        val app = context.applicationContext as? ClassScheduleApplication
        if (app == null) {
            ReminderLogger.warn("reminder.app_alarm_clock.runtime_maintenance.no_application", emptyMap())
            return
        }
        runCatching {
            app.appContainer.runAlarmFollowUpSync()
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
