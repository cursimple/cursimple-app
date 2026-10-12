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
import kotlinx.coroutines.isActive

/**
 * Persistent 15-minute JobScheduler inspection supplements less frequent WorkManager sync.
 * Force-stop still requires reopening the app.
 */
class ReminderGuardJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val running = java.util.concurrent.ConcurrentHashMap<Int, kotlinx.coroutines.Job>()
    override fun onStartJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel()
        val task = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            var retry = false
            try {
                ReminderGuardMaintenance.run(applicationContext, "guard_job")
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) {
                retry = true
                ReminderLogger.warn("reminder.guard_job.failure", emptyMap(), failure)
            } finally {
                val current = kotlinx.coroutines.currentCoroutineContext()
                if (running.remove(params.jobId, current[kotlinx.coroutines.Job]) && current.isActive) jobFinished(params, retry)
            }
        }
        running[params.jobId] = task
        task.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 0x0C1E
        private const val INTERVAL_MILLIS = 15 * 60 * 1000L

        suspend fun onAppBackground(context: Context) {
            val app = context.applicationContext
            val preferences = DataStoreUserPreferencesRepository(app).preferencesFlow.first()
            if (!preferences.alarmKeepAliveEnabled) return
            ReminderLogger.info("reminder.silent_guard.app_background", emptyMap())
            runCatching { ClassNoticeGateway.reschedule(app) }
                .onFailure { ReminderLogger.warn("reminder.silent_guard.class_notice.failure", emptyMap(), it) }
            runCatching {
                AlarmRegistrationRepair.repairIfMissing(app, reason = "app_background")
                AlarmPreNoticeScheduler.reschedule(app)
            }.onFailure { ReminderLogger.warn("reminder.silent_guard.alarm_repair.failure", emptyMap(), it) }
            schedule(app)
            ReminderWatchdogAlarm.ensureScheduled(app)
        }

        /** Schedule when enabled; avoid resubmitting an identical existing job. */
        suspend fun applyPreference(context: Context) {
            val preferences = runCatching {
                DataStoreUserPreferencesRepository(context.applicationContext).preferencesFlow.first()
            }.getOrNull() ?: return
            // Guard controls both class-notice and alarm maintenance.
            if (preferences.alarmKeepAliveEnabled) {
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
            // Resubmission resets periodic timing and can prevent execution during frequent launches.
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

        fun isScheduled(context: Context): Boolean = runCatching {
            context.getSystemService(JobScheduler::class.java)?.getPendingJob(JOB_ID) != null
        }.getOrDefault(false)

        private fun cancel(context: Context) {
            runCatching { context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID) }
        }
    }
}
