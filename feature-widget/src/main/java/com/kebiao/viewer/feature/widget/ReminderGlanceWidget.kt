package com.kebiao.viewer.feature.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.kebiao.viewer.core.data.ThemeAccent
import com.kebiao.viewer.core.reminder.model.AppAlarmOperationMode
import com.kebiao.viewer.core.reminder.model.ReminderAlarmBackend
import com.kebiao.viewer.core.reminder.model.ReminderPlan
import com.kebiao.viewer.core.reminder.model.SystemAlarmRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal data class ReminderWidgetEntry(
    val id: String,
    val triggerAtMillis: Long,
    val title: String,
    val message: String,
)

internal fun buildReminderWidgetEntries(
    plans: List<ReminderPlan>,
    records: List<SystemAlarmRecord>,
    nowMillis: Long,
): List<ReminderWidgetEntry> {
    val planEntries = plans
        .asSequence()
        .filter { it.triggerAtMillis >= nowMillis }
        .map { plan ->
            ReminderWidgetEntry(
                id = "plan:${plan.planId}:${plan.triggerAtMillis}",
                triggerAtMillis = plan.triggerAtMillis,
                title = plan.title.ifBlank { "课程提醒" },
                message = plan.message.ifBlank { "课程即将开始" },
            )
        }
    val snoozeEntries = records
        .asSequence()
        .filter { it.backend == ReminderAlarmBackend.AppAlarmClock }
        .filter { it.operationMode == AppAlarmOperationMode.SnoozeForegroundService }
        .filter { it.triggerAtMillis >= nowMillis }
        .map { record ->
            ReminderWidgetEntry(
                id = "record:${record.backend.name}:${record.alarmKey}",
                triggerAtMillis = record.triggerAtMillis,
                title = firstNotBlank(record.displayTitle, record.alarmLabel, record.message)
                    ?: "课程提醒",
                message = firstNotBlank(record.displayMessage) ?: "已延后 5 分钟",
            )
        }
    return (planEntries + snoozeEntries)
        .distinctBy { it.id }
        .sortedWith(
            compareBy<ReminderWidgetEntry> { it.triggerAtMillis }
                .thenBy { it.title }
                .thenBy { it.id },
        )
        .toList()
}

private fun firstNotBlank(vararg values: String?): String? =
    values.firstOrNull { !it.isNullOrBlank() }

open class ReminderGlanceWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        reconcileSystemAlarmsFromWidget(context)
        updateWidgets(context.applicationContext, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        reconcileSystemAlarmsFromWidget(context)
        updateWidgets(context.applicationContext, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetLifecycleRefresher.onWidgetSetChanged(context.applicationContext, reason = "reminder_widget_deleted")
    }

    companion object {
        @Suppress("DEPRECATION")
        fun updateWidgets(context: Context, appWidgetIds: IntArray? = null) {
            val appContext = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                val manager = AppWidgetManager.getInstance(appContext)
                val ids = appWidgetIds ?: collectIds(appContext, manager)
                if (ids.isEmpty()) return@launch
                val data = ReminderDataSource.load(appContext)
                ids.forEach { appWidgetId ->
                    manager.updateAppWidget(appWidgetId, buildViews(appContext, appWidgetId, data))
                    manager.notifyAppWidgetViewDataChanged(intArrayOf(appWidgetId), R.id.reminder_list)
                }
            }
        }

        private fun collectIds(context: Context, manager: AppWidgetManager): IntArray {
            val ids = WidgetCatalog.entries(context)
                .firstOrNull { it.id == "reminder" }
                ?.let { entry ->
                    (listOf(entry.provider) + entry.vendorProviders)
                        .flatMap { component ->
                            runCatching { manager.getAppWidgetIds(component).toList() }.getOrDefault(emptyList())
                        }
                }
                .orEmpty()
            return ids.toIntArray()
        }

        @Suppress("DEPRECATION")
        private fun buildViews(
            context: Context,
            appWidgetId: Int,
            data: ReminderWidgetData,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_reminder)
            views.setInt(R.id.reminder_root, "setBackgroundResource", widgetCardBackground(data.themeAccent))
            if (data.totalCount > 0) {
                views.setViewVisibility(R.id.reminder_badge, View.VISIBLE)
                views.setTextViewText(R.id.reminder_badge, "${data.totalCount} 条")
            } else {
                views.setViewVisibility(R.id.reminder_badge, View.GONE)
            }
            val hasRows = data.rows.isNotEmpty()
            views.setRemoteAdapter(R.id.reminder_list, listIntent(context, appWidgetId))
            views.setEmptyView(R.id.reminder_list, R.id.reminder_empty)
            views.setViewVisibility(R.id.reminder_list, if (hasRows) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.reminder_empty, if (hasRows) View.GONE else View.VISIBLE)
            views.setInt(R.id.reminder_empty, "setBackgroundResource", widgetRowVariantBackground(data.themeAccent))
            val emptyText = data.emptySubtitle?.let { "${data.emptyTitle}\n$it" } ?: data.emptyTitle
            views.setTextViewText(R.id.reminder_empty, emptyText)
            return views
        }

        private fun listIntent(context: Context, appWidgetId: Int): Intent =
            Intent(context, ReminderRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }

        private fun widgetCardBackground(accent: ThemeAccent): Int = when (accent) {
            ThemeAccent.Green -> R.drawable.widget_bg_card_green
            ThemeAccent.Blue -> R.drawable.widget_bg_card_blue
            ThemeAccent.Purple -> R.drawable.widget_bg_card_purple
            ThemeAccent.Orange -> R.drawable.widget_bg_card_orange
            ThemeAccent.Pink -> R.drawable.widget_bg_card_pink
        }
    }
}

class ReminderGlanceWidgetReceiverMIUI : ReminderGlanceWidgetReceiver()
