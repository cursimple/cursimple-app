package com.x500x.cursimple.app.reminder

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.x500x.cursimple.core.kernel.time.BeijingTime
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Schedule periodic alarm sync, daily rebuilding and automatic-silence checks through
 * WorkManager.
 */
object AlarmSyncScheduler {

    private const val SYNC_WORK_NAME = "alarm_sync_periodic"
    private const val DAILY_GUARD_WORK_NAME = "alarm_daily_guard"
    private const val AUTO_SILENCE_GUARD_WORK_NAME = "auto_silence_guard"

    /** Reconcile alarm records and registrations every two hours. */
    fun schedulePeriodicSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<AlarmSyncWorker>(
            2, TimeUnit.HOURS,
            30, TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .addTag(SYNC_WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest,
        )
    }

    fun scheduleDailyGuard(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val now = BeijingTime.nowDateTime()
        val targetTime = if (now.toLocalTime().isBefore(DAILY_GUARD_TIME)) {
            LocalDateTime.of(now.toLocalDate(), DAILY_GUARD_TIME)
        } else {
            LocalDateTime.of(now.toLocalDate().plusDays(1), DAILY_GUARD_TIME)
        }
        val initialDelayMinutes = Duration.between(now, targetTime).toMinutes()

        val workRequest = PeriodicWorkRequestBuilder<DailyGuardWorker>(
            24, TimeUnit.HOURS,
            1, TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
            .addTag(DAILY_GUARD_WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_GUARD_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest,
        )
    }

    /** Inspect automatic silence every 15 minutes as a fallback for boundary alarms. */
    fun scheduleAutoSilenceGuard(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<AutoSilenceGuardWorker>(
            15, TimeUnit.MINUTES,
        )
            .addTag(AUTO_SILENCE_GUARD_WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AUTO_SILENCE_GUARD_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest,
        )
    }

    /** Cancel only when the feature is disabled and no previous state needs restoration. */
    fun cancelAutoSilenceGuard(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(AUTO_SILENCE_GUARD_WORK_NAME)
    }

    private val DAILY_GUARD_TIME: LocalTime = LocalTime.of(2, 0)
}
