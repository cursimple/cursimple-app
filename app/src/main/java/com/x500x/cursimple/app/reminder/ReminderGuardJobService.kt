package com.x500x.cursimple.app.reminder

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.x500x.cursimple.app.notice.AlarmPreNoticeScheduler
import com.x500x.cursimple.app.notice.AlarmRegistrationRepair
import com.x500x.cursimple.app.notice.ClassNoticeGateway
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 提醒的巡检任务：每 15 分钟由系统拉起一次，把上课提醒的闹钟重挂一遍，守护服务不在了就拉回来。
 *
 * 为什么另起一个 JobScheduler 任务，而不只靠 WorkManager 那条两小时一次的巡检：
 * 上课提醒一次只挂一个闹钟，被厂商系统清掉之后，下一次补回来之前的提醒全都丢了，
 * 两小时的空窗太长。任务由系统统一调度，应用被划掉、进程被回收都不影响它按时来；
 * setPersisted 让它重启后也还在。只有「强制停止」会清掉它，那种情况只能等用户再打开应用。
 */
class ReminderGuardJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            try {
                runCatching { ClassNoticeGateway.reschedule(applicationContext) }
                    .onFailure { ReminderLogger.warn("reminder.guard_job.class_notice.failure", emptyMap(), it) }
                runCatching {
                    AlarmRegistrationRepair.repairIfMissing(applicationContext, reason = "guard_job")
                    AlarmPreNoticeScheduler.reschedule(applicationContext)
                }.onFailure { ReminderLogger.warn("reminder.guard_job.alarm_repair.failure", emptyMap(), it) }
                // 后台拉起前台服务在 Android 12 起受限，只有忽略电池优化等豁免情况下才放行；
                // 起不来也没关系，上面的闹钟已经重挂好了
                runCatching { AlarmKeepAliveService.applyPreference(applicationContext) }
                    .onFailure { ReminderLogger.warn("reminder.guard_job.keep_alive.failure", emptyMap(), it) }
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 0x0C1E
        private const val INTERVAL_MILLIS = 15 * 60 * 1000L

        /** 上课提醒或守护开着就挂上巡检，都关了就撤掉；已经挂着同样的任务时不重复提交。 */
        suspend fun applyPreference(context: Context) {
            val preferences = runCatching {
                DataStoreUserPreferencesRepository(context.applicationContext).preferencesFlow.first()
            }.getOrNull() ?: return
            // 闹钟的体检也挂在这两条上，所以只要守护开着就留着，不看有没有开上课通知
            if (preferences.classNotice.enabled || preferences.alarmKeepAliveEnabled) {
                schedule(context)
                ReminderWatchdogAlarm.ensureScheduled(context)
            } else {
                cancel(context)
                ReminderWatchdogAlarm.cancel(context)
            }
        }

        private fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val existing = runCatching { scheduler.getPendingJob(JOB_ID) }.getOrNull()
            // 重新 schedule 会把周期计时清零，每次启动都提交的话，常开应用的人就永远等不到它跑
            if (existing != null && existing.intervalMillis == INTERVAL_MILLIS && existing.isPersisted) return
            val job = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, ReminderGuardJobService::class.java),
            )
                .setPeriodic(INTERVAL_MILLIS)
                .setPersisted(true)
                .build()
            runCatching { scheduler.schedule(job) }
                .onFailure { ReminderLogger.warn("reminder.guard_job.schedule.failure", emptyMap(), it) }
        }

        private fun cancel(context: Context) {
            runCatching { context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID) }
        }
    }
}
