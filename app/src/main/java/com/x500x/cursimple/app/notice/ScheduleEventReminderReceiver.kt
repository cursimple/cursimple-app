package com.x500x.cursimple.app.notice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

class ScheduleEventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(ScheduleEventReminderScheduler.EXTRA_EVENT_ID) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val app = context.applicationContext
                val terms = com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository(app)
                val events = com.x500x.cursimple.core.data.event.DataStoreScheduleEventRepository(app, terms).eventsFlow.first()
                val event = events.firstOrNull { it.id == eventId } ?: return@launch
                val prefs = com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository(app).preferencesFlow.first()
                val occurrenceDate = intent.getStringExtra(ScheduleEventReminderScheduler.EXTRA_OCCURRENCE_DATE)
                    ?: return@launch
                val plan = ScheduleEventReminderPlanner.planForOccurrence(
                    event,
                    occurrenceDate,
                    prefs.scheduleEventReminder,
                    ZoneId.systemDefault(),
                )
                if (plan != null) {
                    ClassNoticeNotifier.notify(
                        app,
                        ClassNoticeNotifier.eventContent(event, plan.advanceMinutes),
                        prefs.classNotice,
                        ClassNoticeGateway.theme(app),
                        plain = true,
                    )
                }
                ScheduleEventReminderScheduler.reschedule(app)
            } finally {
                pending.finish()
            }
        }
    }
}
