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
 * 守护服务被划掉任务带走之后，由它把服务重新拉起来。
 * 顺带按开关确认一次：用户已经关掉守护时不再复活。
 */
class AlarmKeepAliveRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val watchdog = intent.action == ReminderWatchdogAlarm.ACTION_WATCHDOG
        if (intent.action != ACTION_RESTART && !watchdog) return
        val appContext = context.applicationContext
        // 先续上下一环再干活：后面哪一步出岔子，链条也不会断
        if (watchdog) ReminderWatchdogAlarm.scheduleNext(appContext)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                ReminderLogger.info(if (watchdog) "reminder.watchdog.fire" else "reminder.keep_alive.restart", emptyMap())
                AlarmKeepAliveService.applyPreference(appContext)
                // 进程被带走时挂着的上课提醒可能一起没了，拉起来顺手补上
                runCatching { ClassNoticeGateway.reschedule(appContext) }
                    .onFailure { ReminderLogger.warn("reminder.keep_alive.class_notice.failure", emptyMap(), it) }
                // 闹钟也在这里体检：被系统清掉的立刻重挂，预告跟着重排
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
