package com.x500x.cursimple.app.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Periodic state reconciliation supplements lost boundary alarms and process restarts. */
class AutoSilenceGuardWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            AutoSilenceController.evaluate(applicationContext, reason = WORKER_NAME)
            Result.success()
        } catch (error: Exception) {
            ReminderLogger.warn(
                "reminder.auto_silence.worker.failure",
                mapOf("worker" to WORKER_NAME),
                error,
            )
            Result.retry()
        }
    }

    companion object {
        const val WORKER_NAME = "AutoSilenceGuardWorker"
    }
}
