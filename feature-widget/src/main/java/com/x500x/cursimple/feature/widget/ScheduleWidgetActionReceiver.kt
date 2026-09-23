package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionSendBroadcast
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScheduleWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                handleAction(
                    context = context.applicationContext,
                    action = intent.getStringExtra(EXTRA_ACTION),
                    appWidgetId = intent.getIntExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        AppWidgetManager.INVALID_APPWIDGET_ID,
                    ),
                    renderedOffset = if (intent.hasExtra(EXTRA_CURRENT_OFFSET)) {
                        intent.getIntExtra(EXTRA_CURRENT_OFFSET, 0)
                    } else {
                        null
                    },
                    renderedDateIso = intent.getStringExtra(EXTRA_RENDERED_DATE),
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAction(
        context: Context,
        action: String?,
        appWidgetId: Int,
        renderedOffset: Int?,
        renderedDateIso: String? = null,
    ) {
        val repository = DataStoreWidgetPreferencesRepository(context)
        // 偏移记的是「今天往后数几天」，把按下的那一天一并存下，跨过零点后它才会自己作废
        val todayIso = widgetTodayIso(context)
        // 按钮上带的相对偏移是渲染那天算的。零点那次重画被系统吞掉时，屏幕上的「明天」
        // 其实已经是今天，再拿旧偏移加减就会翻错一天——有渲染日期就按它和今天重新算
        val currentOffset = renderedDateIso
            ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            ?.let { rendered ->
                java.time.temporal.ChronoUnit.DAYS
                    .between(java.time.LocalDate.parse(todayIso), rendered)
                    .toInt()
            }
            ?: renderedOffset
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            when (action) {
                ACTION_PREV -> {
                    if (currentOffset != null) {
                        repository.setWidgetDayOffset(currentOffset - 1, todayIso)
                    } else {
                        repository.shiftWidgetDayOffset(-1, todayIso)
                    }
                }
                ACTION_NEXT -> {
                    if (currentOffset != null) {
                        repository.setWidgetDayOffset(currentOffset + 1, todayIso)
                    } else {
                        repository.shiftWidgetDayOffset(1, todayIso)
                    }
                }
                ACTION_RESET -> repository.setWidgetDayOffset(0, todayIso)
                else -> return
            }
            ScheduleWidgetDataSource.invalidate()
            ScheduleGlanceWidgetReceiver.updateWidgets(context)
            WidgetSystemAlarmSynchronizer.reconcileToday(context)
            return
        }
        when (action) {
            ACTION_PREV -> {
                if (currentOffset != null) {
                    repository.setWidgetDayOffset(appWidgetId, currentOffset - 1, todayIso)
                } else {
                    repository.shiftWidgetDayOffset(appWidgetId, -1, todayIso)
                }
            }
            ACTION_NEXT -> {
                if (currentOffset != null) {
                    repository.setWidgetDayOffset(appWidgetId, currentOffset + 1, todayIso)
                } else {
                    repository.shiftWidgetDayOffset(appWidgetId, 1, todayIso)
                }
            }
            ACTION_RESET -> repository.setWidgetDayOffset(appWidgetId, 0, todayIso)
            else -> return
        }
        ScheduleWidgetDataSource.invalidate()
        ScheduleGlanceWidgetReceiver.updateWidgets(context, intArrayOf(appWidgetId))
        WidgetSystemAlarmSynchronizer.reconcileToday(context)
    }

    companion object {
        const val ACTION_PREV = "prev"
        const val ACTION_NEXT = "next"
        const val ACTION_RESET = "reset"

        const val EXTRA_ACTION = "schedule_widget_action"
        const val EXTRA_CURRENT_OFFSET = "schedule_widget_current_offset"
        const val EXTRA_RENDERED_DATE = "schedule_widget_rendered_date"

        fun action(context: Context, action: String): Action {
            val intent = Intent(context, ScheduleWidgetActionReceiver::class.java).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_ACTION, action)
            }
            return actionSendBroadcast(intent)
        }
    }
}
