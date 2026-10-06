package com.x500x.cursimple.app.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.x500x.cursimple.app.AppContainer
import com.x500x.cursimple.app.notice.ClassNoticeGateway
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import com.x500x.cursimple.core.reminder.model.ReminderSyncReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodic inspection rebuilds missing registrations and refreshes the upcoming reminder
 * window.
 */
class AlarmSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            ReminderLogger.info(
                "reminder.worker.sync.start",
                mapOf("worker" to WORKER_NAME),
            )

            val appContainer = getAppContainer()

            val summaries = appContainer.runSharedAlarmIntegrityCheck(
                reason = ReminderSyncReason.WorkerBackgroundSync,
                includeTomorrow = true,
            )

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            val totalCreated = summaries.sumOf { it.createdCount }
            val totalSubmitted = summaries.sumOf { it.submittedCount }
            val totalFailed = summaries.sumOf { it.failedCount }

            ReminderLogger.info(
                "reminder.worker.sync.finish",
                mapOf(
                    "worker" to WORKER_NAME,
                    "durationMs" to duration,
                    "totalSubmitted" to totalSubmitted,
                    "totalCreated" to totalCreated,
                    "totalFailed" to totalFailed,
                    "summaryCount" to summaries.size,
                ),
            )

            AutoSilenceController.evaluate(applicationContext, reason = WORKER_NAME)

            // Reschedule class notices during maintenance so a missing wake-up does not break the chain indefinitely.
            runCatching { ClassNoticeGateway.reschedule(applicationContext) }
                .onFailure { ReminderLogger.warn("class_notice.worker.reschedule.failure", emptyMap(), it) }

            // Retry failures once.
            if (totalFailed > 0) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            ReminderLogger.warn(
                "reminder.worker.sync.failure",
                mapOf("worker" to WORKER_NAME),
                e,
            )
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
        const val WORKER_NAME = "AlarmSyncWorker"
    }
}
