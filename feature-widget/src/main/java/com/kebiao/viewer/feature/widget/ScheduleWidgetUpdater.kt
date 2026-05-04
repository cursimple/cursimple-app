package com.kebiao.viewer.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

object ScheduleWidgetUpdater {
    suspend fun refreshAll(context: Context) {
        val app = context.applicationContext
        // Glance refreshes by GlanceAppWidget class; this covers the AOSP receivers.
        ScheduleGlanceWidget().refreshAll(app)
        NextCourseGlanceWidget().refreshAll(app)
        ReminderGlanceWidget().refreshAll(app)
        // The vendor-aware receivers are separate ComponentNames that Glance won't visit
        // automatically — nudge them via the standard AppWidgetManager update broadcast so
        // any pinned MIUI / vivo / HONOR instances get re-rendered.
        broadcastVendorUpdate(app)
    }

    suspend fun refreshSchedule(context: Context, glanceId: GlanceId? = null) {
        val app = context.applicationContext
        val widget = ScheduleGlanceWidget()
        if (glanceId != null) {
            widget.update(app, glanceId)
        }
        widget.refreshAll(app)
        broadcastVendorUpdate(
            context = app,
            receiverClassNames = listOf(SCHEDULE_VENDOR_RECEIVER),
        )
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

    private const val SCHEDULE_VENDOR_RECEIVER =
        "com.kebiao.viewer.feature.widget.ScheduleGlanceWidgetReceiverMIUI"

    private val VENDOR_RECEIVERS = listOf(
        SCHEDULE_VENDOR_RECEIVER,
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

