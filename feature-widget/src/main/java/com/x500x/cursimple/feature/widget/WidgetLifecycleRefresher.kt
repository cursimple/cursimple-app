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
        // 全部放到后台协程：WorkManager/AlarmManager 的入库与排程都线程安全，
        // 而缩放小组件会高频触发 onAppWidgetOptionsChanged，放在广播主线程同步跑会卡顿
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
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
            runCatching {
                WidgetAlarmGuardScheduler.ensureScheduled(appContext)
            }.onFailure { error ->
                ReminderLogger.warn(
                    "widget.lifecycle.alarm_guard_schedule.failure",
                    mapOf("reason" to reason),
                    error,
                )
            }
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
