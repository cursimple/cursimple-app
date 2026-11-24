package com.x500x.cursimple.app.notice

import android.app.AlarmManager
import android.content.Context
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.event.DataStoreScheduleEventRepository
import com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.flow.first
import java.time.ZoneId

object ScheduleEventReminderScheduler {
    const val ACTION_EVENT_REMINDER = "com.x500x.cursimple.action.EVENT_REMINDER"
    const val EXTRA_EVENT_ID = "eventId"
    const val EXTRA_OCCURRENCE_DATE = "occurrenceDate"

    suspend fun reschedule(context: Context) {
        val app = context.applicationContext
        val terms = DataStoreTermProfileRepository(app)
        val events = DataStoreScheduleEventRepository(app, terms).eventsFlow.first()
        val prefs = DataStoreUserPreferencesRepository(app).preferencesFlow.first()
        val alarm = app.getSystemService(AlarmManager::class.java) ?: return
        events.forEach { event ->
            ClassNoticeScheduler.eventIntent(app, event.id, noCreate = true).let(alarm::cancel)
            if (!prefs.scheduleEventReminder.enabled) return@forEach
            val plan = ScheduleEventReminderPlanner.nextReminder(event, java.time.ZonedDateTime.now(ZoneId.systemDefault()), prefs.scheduleEventReminder)
                ?: return@forEach
            val pending = ClassNoticeScheduler.eventIntent(app, event.id, plan.occurrenceDate)
            runCatching { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.triggerAtMillis, pending) }
                .onFailure { ReminderLogger.warn("event_reminder.schedule.failure", mapOf("eventId" to event.id), it) }
        }
    }

    fun cancel(context: Context, eventId: String) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        ClassNoticeScheduler.eventIntent(context, eventId, noCreate = true).let(alarm::cancel)
    }
}

