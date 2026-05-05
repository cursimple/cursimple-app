package com.kebiao.viewer.app.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

object LogCleanupScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<LogCleanupWorker>(
            repeatInterval = 3,
            repeatIntervalTimeUnit = TimeUnit.DAYS,
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private const val UNIQUE_WORK_NAME = "app_log_cleanup"
}

class LogCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return if (LogExporter.clearExpiredLogs(applicationContext)) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
