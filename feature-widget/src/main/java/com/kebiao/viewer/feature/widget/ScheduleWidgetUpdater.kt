package com.kebiao.viewer.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

object ScheduleWidgetUpdater {
    suspend fun refreshAll(context: Context) {
        val app = context.applicationContext
        ScheduleGlanceWidgetReceiver.updateWidgets(app)
        NextCourseGlanceWidget().refreshAll(app)
        ReminderGlanceWidget().refreshAll(app)
        // Next-course and reminder vendor-aware receivers are separate ComponentNames
        // that Glance won't visit automatically.
        broadcastVendorUpdate(app)
    }

    fun refreshSchedule(context: Context, appWidgetIds: IntArray? = null) {
        val app = context.applicationContext
        ScheduleGlanceWidgetReceiver.updateWidgets(app, appWidgetIds)
    }

    private fun broadcastVendorUpdate(
        context: Context,
        receiverClassNames: List<String> = VENDOR_RECEIVERS,
    ) {
        val manager = AppWidgetManager.getInstance(context)
        val pkg = context.packageName
        receiverClassNames.forEach { className ->
            val component = ComponentName(pkg, className)
            val ids = runCatching { manager.getAppWidgetIds(component) }.getOrNull() ?: return@forEach
            if (ids.isEmpty()) return@forEach
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                this.component = component
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }

    private val VENDOR_RECEIVERS = listOf(
        "com.kebiao.viewer.feature.widget.NextCourseGlanceWidgetReceiverMIUI",
        "com.kebiao.viewer.feature.widget.ReminderGlanceWidgetReceiverMIUI",
    )
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

