package com.x500x.cursimple.feature.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal data class WidgetAlarmGuardSlot(
    val index: Int,
    val triggerElapsedRealtime: Long,
    val requestCode: Int,
)

/**
 * Maintains a small chain of silent guard alarms.
 *
 * These alarms are intentionally separate from user-facing class reminders: when they fire they
 * only wake the app process, re-arm the guard chain, verify reminder alarm registration, and refresh
 * widget RemoteViews with the latest data. They must never ring, vibrate, or show alarm UI.
 *
 * Future background jobs that need the same five-minute cadence can be attached in
 * [WidgetAlarmGuardRunner.run] so there is one documented place for this silent wakeup path.
 */
internal object WidgetAlarmGuardScheduler {
    fun ensureScheduled(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        schedulePlan(SystemClock.elapsedRealtime()).forEach { slot ->
            runCatching {
                scheduleSlot(
                    alarmManager = alarmManager,
                    triggerElapsedRealtime = slot.triggerElapsedRealtime,
                    operation = guardPendingIntent(app, slot),
                )
            }.onFailure { error ->
                ReminderLogger.warn(
                    "widget.alarm_guard.schedule.failure",
                    mapOf(
                        "index" to slot.index,
                        "triggerElapsedRealtime" to slot.triggerElapsedRealtime,
                        "requestCode" to slot.requestCode,
                    ),
                    error,
                )
            }
        }
    }

    internal fun schedulePlan(nowElapsedRealtime: Long): List<WidgetAlarmGuardSlot> {
        return (0 until PREARM_COUNT).map { index ->
            WidgetAlarmGuardSlot(
                index = index,
                triggerElapsedRealtime = nowElapsedRealtime + GUARD_INTERVAL_MILLIS * (index + 1L),
                requestCode = requestCodeForIndex(index),
            )
        }
    }

    internal fun requestCodeForIndex(index: Int): Int =
        REQUEST_CODE_BASE + index

    private fun scheduleSlot(
        alarmManager: AlarmManager,
        triggerElapsedRealtime: Long,
        operation: PendingIntent,
    ) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms() -> {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsedRealtime, operation)
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerElapsedRealtime,
                    operation,
                )
            }

            else -> {
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsedRealtime, operation)
            }
        }
    }

    private fun guardPendingIntent(context: Context, slot: WidgetAlarmGuardSlot): PendingIntent {
        val intent = Intent(context, WidgetAlarmGuardReceiver::class.java).apply {
            action = ACTION_GUARD_TICK
            setPackage(context.packageName)
            putExtra(EXTRA_INDEX, slot.index)
            putExtra(EXTRA_TRIGGER_ELAPSED_REALTIME, slot.triggerElapsedRealtime)
        }
        return PendingIntent.getBroadcast(
            context,
            slot.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    const val ACTION_GUARD_TICK = "com.x500x.cursimple.feature.widget.action.ALARM_GUARD_TICK"

    internal const val GUARD_INTERVAL_MILLIS = 5L * 60L * 1000L
    internal const val PREARM_COUNT = 3
    internal const val REQUEST_CODE_BASE = 6405

    private const val EXTRA_INDEX = "com.x500x.cursimple.feature.widget.extra.ALARM_GUARD_INDEX"
    private const val EXTRA_TRIGGER_ELAPSED_REALTIME =
        "com.x500x.cursimple.feature.widget.extra.ALARM_GUARD_TRIGGER_ELAPSED_REALTIME"
}

internal object WidgetAlarmGuardRunner {
    suspend fun run(context: Context, reason: String) {
        val app = context.applicationContext

        // Re-arm first so a later sync or widget refresh failure does not stop the silent guard chain.
        WidgetAlarmGuardScheduler.ensureScheduled(app)

        runCatching {
            WidgetSystemAlarmSynchronizer.reconcileToday(app)
        }.onFailure { error ->
            ReminderLogger.warn(
                "widget.alarm_guard.reconcile.failure",
                mapOf("reason" to reason),
                error,
            )
        }

        // RemoteViews updates are silent when rendered data is unchanged; no notification or user UI is shown.
        runCatching {
            ScheduleWidgetUpdater.refreshAll(app)
        }.onFailure { error ->
            ReminderLogger.warn(
                "widget.alarm_guard.refresh_widgets.failure",
                mapOf("reason" to reason),
                error,
            )
        }
    }
}

class WidgetAlarmGuardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetAlarmGuardScheduler.ACTION_GUARD_TICK) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                WidgetAlarmGuardRunner.run(
                    context = context.applicationContext,
                    reason = "alarm_guard_tick",
                )
            } finally {
                pending.finish()
            }
        }
    }
}
