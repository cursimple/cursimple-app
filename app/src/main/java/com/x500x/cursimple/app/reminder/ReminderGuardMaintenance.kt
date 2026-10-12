package com.x500x.cursimple.app.reminder

import android.content.Context
import androidx.work.*
import com.x500x.cursimple.app.notice.AlarmPreNoticeScheduler
import com.x500x.cursimple.app.notice.AlarmRegistrationRepair
import com.x500x.cursimple.app.notice.ClassNoticeGateway
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** One repair pipeline for alarm, job and deferred recovery entry points. */
object ReminderGuardMaintenance {
    private val mutex = Mutex()
    suspend fun run(context: Context, reason: String): Boolean = mutex.withLock {
        val app = context.applicationContext
        if (!DataStoreUserPreferencesRepository(app).preferencesFlow.first().alarmKeepAliveEnabled) {
            ReminderWatchdogAlarm.cancel(app)
            return@withLock false
        }
        ReminderWatchdogAlarm.scheduleNext(app)
        ClassNoticeGateway.catchUpIfMissed(app)
        ClassNoticeGateway.reschedule(app)
        AlarmRegistrationRepair.repairIfMissing(app, reason = reason)
        AlarmPreNoticeScheduler.reschedule(app)
        app.getSharedPreferences("reminder_guard_health", Context.MODE_PRIVATE).edit()
            .putLong("last_success", System.currentTimeMillis()).putString("source", reason).apply()
        true
    }

    fun enqueue(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("reminder-guard-recovery", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ReminderGuardRecoveryWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
}

class ReminderGuardRecoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        ReminderGuardMaintenance.run(applicationContext, "deferred_recovery")
        Result.success()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: Exception) {
        ReminderLogger.warn("reminder.guard.recovery.failure", emptyMap(), failure)
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
