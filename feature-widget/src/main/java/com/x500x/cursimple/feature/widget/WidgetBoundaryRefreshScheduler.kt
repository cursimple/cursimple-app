package com.x500x.cursimple.feature.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import java.time.LocalDateTime
import java.time.ZoneId

/** Refresh at advance, start and end boundaries, supplementing the periodic guard. */
internal object WidgetBoundaryRefreshScheduler {

    fun reschedule(context: Context, slots: List<ClassSlotTime>, zone: ZoneId = BeijingTime.zone) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val boundaries = widgetRefreshBoundaries(slots, LocalDateTime.now(zone), limit = SLOT_COUNT)
        // Cancel unused boundary slots after the schedule shrinks.
        for (index in boundaries.size until SLOT_COUNT) {
            cancelSlot(app, alarmManager, index)
        }
        boundaries.forEachIndexed { index, at ->
            val triggerAtMillis = at.atZone(zone).toInstant().toEpochMilli()
            runCatching {
                scheduleSlot(alarmManager, triggerAtMillis, pendingIntent(app, index, PendingIntent.FLAG_UPDATE_CURRENT))
            }.onFailure { error ->
                ReminderLogger.warn(
                    "widget.boundary_refresh.schedule.failure",
                    mapOf("index" to index, "triggerAtMillis" to triggerAtMillis),
                    error,
                )
            }
        }
    }

    private fun cancelSlot(context: Context, alarmManager: AlarmManager, index: Int) {
        val operation = pendingIntent(context, index, PendingIntent.FLAG_NO_CREATE) ?: return
        runCatching {
            alarmManager.cancel(operation)
            operation.cancel()
        }
    }

    private fun scheduleSlot(alarmManager: AlarmManager, triggerAtMillis: Long, operation: PendingIntent?) {
        if (operation == null) return
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
        if (exactAllowed) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        } else {
            // Use inexact refresh when exact-alarm access is unavailable.
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    private fun pendingIntent(context: Context, index: Int, flags: Int): PendingIntent? {
        val intent = Intent(context, WidgetAlarmGuardReceiver::class.java).apply {
            action = WidgetAlarmGuardScheduler.ACTION_GUARD_TICK
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + index,
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    internal const val SLOT_COUNT = 6
    private const val REQUEST_CODE_BASE = 6420
}
