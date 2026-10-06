package com.x500x.cursimple.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.x500x.cursimple.app.notice.AlarmPreNoticeScheduler
import com.x500x.cursimple.app.notice.AlarmRegistrationRepair
import com.x500x.cursimple.app.notice.ClassNoticeGateway
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Run silent maintenance and continue the guard chain. Legacy restart actions trigger
 * inspection without reviving the removed service.
 */
class AlarmKeepAliveRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val watchdog = intent.action == ReminderWatchdogAlarm.ACTION_WATCHDOG
        if (intent.action != ACTION_RESTART && !watchdog) return
        val appContext = context.applicationContext
        if (watchdog) ReminderWatchdogAlarm.scheduleNext(appContext)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                ReminderLogger.info(if (watchdog) "reminder.watchdog.fire" else "reminder.keep_alive.restart", emptyMap())
                runCatching { ClassNoticeGateway.reschedule(appContext) }
                    .onFailure { ReminderLogger.warn("reminder.keep_alive.class_notice.failure", emptyMap(), it) }
                runCatching {
                    AlarmRegistrationRepair.repairIfMissing(appContext, reason = if (watchdog) "watchdog" else "restart")
                    AlarmPreNoticeScheduler.reschedule(appContext)
                }.onFailure { ReminderLogger.warn("reminder.keep_alive.alarm_repair.failure", emptyMap(), it) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_RESTART = "com.x500x.cursimple.action.KEEP_ALIVE_RESTART"
    }
}
