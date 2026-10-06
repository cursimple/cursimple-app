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
 * Reconcile silence on class boundaries, boot, package replacement and clock changes; handles
 * manual restore actions too.
 */
class AutoSilenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val action = intent.action
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    AutoSilenceController.ACTION_RESTORE_NOW -> AutoSilenceController.restoreNow(
                        context = appContext,
                        reason = "user_restore",
                        suppressUntilBlockEnd = true,
                    )

                    else -> AutoSilenceController.evaluate(appContext, reason = action.orEmpty())
                }
            } catch (error: Throwable) {
                ReminderLogger.warn(
                    "reminder.auto_silence.receiver.failure",
                    mapOf("action" to action.orEmpty()),
                    error,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
