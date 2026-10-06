package com.x500x.cursimple.app.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.x500x.cursimple.app.AppContainer
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import com.x500x.cursimple.core.reminder.model.ReminderSyncReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Daily rebuilding reconciles alarm registrations, prunes expired records and refreshes the
 * reminder window.
 */
class DailyGuardWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            ReminderLogger.info(
                "reminder.worker.daily_guard.start",
                mapOf("worker" to WORKER_NAME),
            )

            val appContainer = getAppContainer()

            appContainer.refreshScheduleOutputs(recreateAppManagedAlarms = true)

            // Bypass polling cooldown after prior refresh has marked the current poll time.
            val summaries = appContainer.runSharedAlarmIntegrityCheck(
                reason = ReminderSyncReason.DailyNextDay,
                includeTomorrow = true,
                bypassPollClaim = true,
            )

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            val totalCreated = summaries.sumOf { it.createdCount }
            val totalSubmitted = summaries.sumOf { it.submittedCount }
            val totalFailed = summaries.sumOf { it.failedCount }
            val expiredCleared = summaries.sumOf { it.expiredRecordClearedCount }

            ReminderLogger.info(
                "reminder.worker.daily_guard.finish",
                mapOf(
                    "worker" to WORKER_NAME,
                    "durationMs" to duration,
                    "totalSubmitted" to totalSubmitted,
                    "totalCreated" to totalCreated,
                    "totalFailed" to totalFailed,
                    "expiredCleared" to expiredCleared,
                    "summaryCount" to summaries.size,
                ),
            )

            AutoSilenceController.evaluate(applicationContext, reason = WORKER_NAME)

            // Report this attempt complete; later periodic runs perform another inspection.
            Result.success()
        } catch (e: Exception) {
            ReminderLogger.warn(
                "reminder.worker.daily_guard.failure",
                mapOf("worker" to WORKER_NAME),
                e,
            )
            // Retry failed daily maintenance.
            Result.retry()
        }
    }

    private fun getAppContainer(): AppContainer {
        val application = applicationContext.applicationContext
        val appField = application.javaClass.getDeclaredField("appContainer")
        appField.isAccessible = true
        return appField.get(application) as AppContainer
    }

    companion object {
        const val WORKER_NAME = "DailyGuardWorker"
    }
}
