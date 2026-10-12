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

    @Synchronized
    fun ensureScheduled(context: Context) {
        val due = context.getSharedPreferences("reminder_guard_health", Context.MODE_PRIVATE).getLong("next_elapsed", 0L)
        val now = android.os.SystemClock.elapsedRealtime()
        if (guardRegistrationFresh(due, now, INTERVAL_MILLIS) && pendingIntent(context, PendingIntent.FLAG_NO_CREATE) != null) return
        scheduleNext(context)
    }

    @Synchronized
    fun scheduleNext(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return
        val operation = pendingIntent(app, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val triggerAt = android.os.SystemClock.elapsedRealtime() + INTERVAL_MILLIS
        runCatching {
            // Use inexact inspection when exact-alarm access is unavailable.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, operation)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, operation)
            }
        }.onSuccess {
            app.getSharedPreferences("reminder_guard_health", Context.MODE_PRIVATE).edit().putLong("next_elapsed", triggerAt).apply()
        }.onFailure {
            operation.cancel()
            ReminderLogger.warn("reminder.watchdog.schedule.failure", emptyMap(), it)
        }
    }

    @Synchronized
    fun cancel(context: Context) {
        context.getSharedPreferences("reminder_guard_health", Context.MODE_PRIVATE).edit().remove("next_elapsed").apply()
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

internal fun guardRegistrationFresh(due: Long, now: Long, interval: Long): Boolean = due > now && due - now <= interval
