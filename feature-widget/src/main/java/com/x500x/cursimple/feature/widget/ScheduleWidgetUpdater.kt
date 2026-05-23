package com.x500x.cursimple.feature.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object ScheduleWidgetUpdater {
    suspend fun refreshAll(context: Context) {
        val app = context.applicationContext
        ScheduleGlanceWidgetReceiver.updateWidgets(app)
        NextCourseGlanceWidgetReceiver.updateWidgets(app)
        ReminderGlanceWidgetReceiver.updateWidgets(app)
    }

    fun refreshSchedule(context: Context, appWidgetIds: IntArray? = null) {
        val app = context.applicationContext
        ScheduleGlanceWidgetReceiver.updateWidgets(app, appWidgetIds)
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
        ScheduleWidgetRefreshAlarmScheduler.cancel(context)
        WidgetAlarmGuardScheduler.ensureScheduled(context)
    }

    private const val UNIQUE_WORK_NAME = "schedule_widget_refresh"
}

object ScheduleWidgetRefreshAlarmScheduler {
    fun cancel(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = refreshPendingIntent(app, PendingIntent.FLAG_NO_CREATE) ?: return
        runCatching {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }.onFailure { error ->
            ReminderLogger.warn("widget.refresh_alarm.cancel.failure", emptyMap(), error)
        }
    }

    private fun refreshPendingIntent(context: Context, extraFlags: Int = 0): PendingIntent? {
        val intent = Intent(context, ScheduleWidgetRefreshReceiver::class.java).apply {
            action = ACTION_REFRESH
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or extraFlags,
        )
    }

    const val ACTION_REFRESH = "com.x500x.cursimple.feature.widget.action.REFRESH_WIDGETS"
    private const val REQUEST_CODE = 5305
}

class ScheduleWidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ScheduleWidgetRefreshAlarmScheduler.ACTION_REFRESH) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val app = context.applicationContext
                runCatching {
                    WidgetAlarmGuardRunner.run(
                        context = app,
                        reason = "legacy_refresh_alarm",
                    )
                }.onFailure { error ->
                    ReminderLogger.warn("widget.refresh_alarm.run.failure", emptyMap(), error)
                }
                ScheduleWidgetRefreshAlarmScheduler.cancel(app)
            } finally {
                pending.finish()
            }
        }
    }
}

class ScheduleWidgetRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return runCatching {
            ScheduleWidgetRefreshAlarmScheduler.cancel(applicationContext)
            WidgetAlarmGuardRunner.run(
                context = applicationContext,
                reason = "periodic_worker",
            )
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}

