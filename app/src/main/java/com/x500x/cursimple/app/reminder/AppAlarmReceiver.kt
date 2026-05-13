package com.x500x.cursimple.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.x500x.cursimple.core.reminder.dispatch.AppAlarmClockIntents
import com.x500x.cursimple.core.reminder.logging.ReminderLogger

class AppAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AppAlarmClockIntents.ACTION_TRIGGER) return
        val pendingResult = goAsync()
        runCatching {
            val serviceIntent = Intent(context.applicationContext, AlarmRingingService::class.java).apply {
                action = AlarmRingingService.ACTION_RING
                putExtras(intent)
            }
            ContextCompat.startForegroundService(context.applicationContext, serviceIntent)
        }.onFailure { error ->
            ReminderLogger.warn(
                "reminder.app_alarm_clock.receiver.start_service.failure",
                mapOf(
                    "alarmKey" to intent.getStringExtra(AppAlarmClockIntents.EXTRA_ALARM_KEY).orEmpty(),
                    "planId" to intent.getStringExtra(AppAlarmClockIntents.EXTRA_PLAN_ID).orEmpty(),
                ),
                error,
            )
        }
        pendingResult.finish()
    }
}
