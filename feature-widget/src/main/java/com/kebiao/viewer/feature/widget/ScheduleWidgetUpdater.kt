package com.kebiao.viewer.feature.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

object ScheduleWidgetUpdater {
    suspend fun refreshAll(context: Context) {
        ScheduleGlanceWidget().refreshAll(context.applicationContext)
    }
}

object ScheduleWidgetWorkScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ScheduleWidgetRefreshWorker>(
            repeatInterval = 30,
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private const val UNIQUE_WORK_NAME = "schedule_widget_refresh"
}

class ScheduleWidgetRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return runCatching {
            ScheduleWidgetUpdater.refreshAll(applicationContext)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}

