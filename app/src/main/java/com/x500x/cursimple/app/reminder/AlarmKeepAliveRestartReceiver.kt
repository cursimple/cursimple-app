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
 * 静默守护的看门狗闹钟落到这里：重挂上课提醒、体检闹钟，再把下一环续上。
 *
 * [ACTION_RESTART] 是早先常驻守护服务的自启动闹钟，服务已经拿掉了；
 * 从旧版升级上来时可能还有一条挂在系统里，响了就当一次巡检，不再拉服务。
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
