package com.x500x.cursimple.feature.widget

import android.content.Context
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal object WidgetLifecycleRefresher {
    fun onWidgetUpdated(context: Context, reason: String) {
        refresh(
            context = context,
            reason = reason,
            refreshWidgets = false,
        )
    }

    fun onWidgetSetChanged(context: Context, reason: String) {
        refresh(
            context = context,
            reason = reason,
            refreshWidgets = true,
        )
    }

    private fun refresh(
        context: Context,
        reason: String,
        refreshWidgets: Boolean,
    ) {
        val appContext = context.applicationContext
        runCatching {
            WidgetCatalog.notifyInstalledChanged(appContext)
        }.onFailure { error ->
            ReminderLogger.warn(
                "widget.lifecycle.installed_changed.failure",
                mapOf("reason" to reason),
                error,
            )
        }
        runCatching {
            ScheduleWidgetWorkScheduler.schedule(appContext)
        }.onFailure { error ->
            ReminderLogger.warn(
                "widget.lifecycle.worker_schedule.failure",
                mapOf("reason" to reason),
                error,
            )
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            if (refreshWidgets) {
                runCatching {
                    ScheduleWidgetUpdater.refreshAll(appContext)
                }.onFailure { error ->
                    ReminderLogger.warn(
                        "widget.lifecycle.refresh_all.failure",
                        mapOf("reason" to reason),
                        error,
                    )
                }
            }
            runCatching {
                WidgetSystemAlarmSynchronizer.reconcileToday(appContext)
            }.onFailure { error ->
                ReminderLogger.warn(
                    "widget.lifecycle.alarm_reconcile.failure",
                    mapOf("reason" to reason),
                    error,
                )
            }
        }
    }
}
