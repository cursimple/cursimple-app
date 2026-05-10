package com.kebiao.viewer.feature.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kebiao.viewer.core.reminder.logging.ReminderLogger
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
        ScheduleWidgetRefreshAlarmScheduler.scheduleNext(context)
    }

    private const val UNIQUE_WORK_NAME = "schedule_widget_refresh"
}

object ScheduleWidgetRefreshAlarmScheduler {
    fun scheduleNext(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = refreshPendingIntent(app)
        val triggerAtMillis = System.currentTimeMillis() + REFRESH_INTERVAL_MILLIS
        runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms() -> {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
                else -> {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            }
        }.onFailure { error ->
            ReminderLogger.warn("widget.refresh_alarm.schedule.failure", emptyMap(), error)
        }
    }

    private fun refreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScheduleWidgetRefreshReceiver::class.java).apply {
            action = ACTION_REFRESH
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    const val ACTION_REFRESH = "com.kebiao.viewer.feature.widget.action.REFRESH_WIDGETS"
    private const val REQUEST_CODE = 5305
    private const val REFRESH_INTERVAL_MILLIS = 5L * 60L * 1000L
}

class ScheduleWidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ScheduleWidgetRefreshAlarmScheduler.ACTION_REFRESH) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val app = context.applicationContext
                runCatching {
                    ScheduleWidgetUpdater.refreshAll(app)
                    WidgetSystemAlarmSynchronizer.reconcileToday(app)
                }.onFailure { error ->
                    ReminderLogger.warn("widget.refresh_alarm.run.failure", emptyMap(), error)
                }
                ScheduleWidgetRefreshAlarmScheduler.scheduleNext(app)
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
            ScheduleWidgetUpdater.refreshAll(applicationContext)
            WidgetSystemAlarmSynchronizer.reconcileToday(applicationContext)
            ScheduleWidgetRefreshAlarmScheduler.scheduleNext(applicationContext)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}

