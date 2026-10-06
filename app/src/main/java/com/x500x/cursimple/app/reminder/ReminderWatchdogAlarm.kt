package com.x500x.cursimple.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.x500x.cursimple.core.reminder.logging.ReminderLogger

/**
 * Alarm-based guard supplements deferrable JobScheduler checks; either path can recover lost
 * registrations.
 */
object ReminderWatchdogAlarm {
    const val ACTION_WATCHDOG = "com.x500x.cursimple.action.REMINDER_WATCHDOG"
    private const val REQUEST_CODE = 0x0C1F
    private const val INTERVAL_MILLIS = 15 * 60 * 1000L

    fun ensureScheduled(context: Context) {
        if (pendingIntent(context, PendingIntent.FLAG_NO_CREATE) != null) return
        scheduleNext(context)
    }

    fun scheduleNext(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return
        val operation = pendingIntent(app, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val triggerAt = System.currentTimeMillis() + INTERVAL_MILLIS
        runCatching {
            // Use inexact inspection when exact-alarm access is unavailable.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
        }.onFailure { ReminderLogger.warn("reminder.watchdog.schedule.failure", emptyMap(), it) }
    }

    fun cancel(context: Context) {
        val operation = pendingIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
        runCatching {
            context.applicationContext.getSystemService(AlarmManager::class.java)?.cancel(operation)
            operation.cancel()
        }
    }

    private fun pendingIntent(context: Context, extraFlags: Int): PendingIntent? {
        val app = context.applicationContext
        return PendingIntent.getBroadcast(
            app,
            REQUEST_CODE,
            Intent(app, AlarmKeepAliveRestartReceiver::class.java)
                .setAction(ACTION_WATCHDOG)
                .setPackage(app.packageName)
                // Foreground-priority broadcasts reduce delays in the background queue.
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
            PendingIntent.FLAG_IMMUTABLE or extraFlags,
        )
    }
}
