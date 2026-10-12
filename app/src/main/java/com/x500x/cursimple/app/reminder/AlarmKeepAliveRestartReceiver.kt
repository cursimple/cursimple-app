package com.x500x.cursimple.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
        val pendingResult = goAsync()
        val wakeLock = (appContext.getSystemService(android.os.PowerManager::class.java))
            ?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "cursimple:guard_repair")
        runCatching { wakeLock?.acquire(10_000L) }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                kotlinx.coroutines.withTimeout(8_000L) {
                    ReminderGuardMaintenance.run(appContext, if (watchdog) "watchdog" else "restart")
                }
            } catch (failure: Exception) {
                ReminderLogger.warn("reminder.guard.broadcast.deferred", emptyMap(), failure)
                runCatching { ReminderGuardMaintenance.enqueue(appContext) }
            } finally {
                if (wakeLock?.isHeld == true) runCatching { wakeLock.release() }
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_RESTART = "com.x500x.cursimple.action.KEEP_ALIVE_RESTART"
    }
}
