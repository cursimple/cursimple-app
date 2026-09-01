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
 * 自动静音的广播入口。
 *
 * 上下课边界闹钟、开机、覆盖安装、系统时间变更都在这里重新体检一次；
 * 通知上的「立即恢复」也走这里，保证用户随时能一键把手机调回来。
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
