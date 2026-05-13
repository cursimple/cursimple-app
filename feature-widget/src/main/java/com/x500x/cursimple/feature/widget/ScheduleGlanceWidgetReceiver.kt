package com.x500x.cursimple.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.kernel.model.weekdayLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

open class ScheduleGlanceWidgetReceiver : AppWidgetProvider() {
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
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val repository = DataStoreWidgetPreferencesRepository(appContext)
                appWidgetIds.forEach { repository.clearWidgetDayOffset(it) }
                WidgetLifecycleRefresher.onWidgetSetChanged(appContext, reason = "today_widget_deleted")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        @Suppress("DEPRECATION")
        fun updateWidgets(context: Context, appWidgetIds: IntArray? = null) {
            val appContext = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                val manager = AppWidgetManager.getInstance(appContext)
                val ids = appWidgetIds ?: scheduleWidgetIds(appContext, manager)
                if (ids.isEmpty()) return@launch
                ids.forEach { appWidgetId ->
                    val sizeClass = sizeClassForWidget(manager, appWidgetId)
                    val dayData = ScheduleWidgetDataSource.loadDay(appContext, appWidgetId)
                    manager.updateAppWidget(
                        appWidgetId,
                        buildScheduleViews(appContext, appWidgetId, sizeClass, dayData),
                    )
                    manager.notifyAppWidgetViewDataChanged(intArrayOf(appWidgetId), R.id.widget_course_list)
                }
            }
        }

        private fun sizeClassForWidget(manager: AppWidgetManager, appWidgetId: Int): WidgetSizeClass {
            val options = manager.getAppWidgetOptions(appWidgetId)
            return WidgetSizeClass.fromDp(
                widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_MIN_WIDTH_DP),
                heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_MIN_HEIGHT_DP),
            )
        }

        private fun scheduleWidgetIds(context: Context, manager: AppWidgetManager): IntArray {
            val ids = WidgetCatalog.entries(context)
                .firstOrNull { it.id == "today" }
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
        private fun buildScheduleViews(
            context: Context,
            appWidgetId: Int,
            sizeClass: WidgetSizeClass,
            dayData: ScheduleWidgetDayData,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_schedule_today)

            views.applyWidgetBackground(context, R.id.widget_root, dayData.widgetTheme)
            views.applyOpenAppOnDoubleClick(context, R.id.widget_root, appWidgetId, dayData.widgetTheme)

            views.setTextViewText(
                R.id.widget_title,
                "${dateFormatter.format(dayData.targetDate)} · ${WidgetDayLabels.tag(dayData.offset)}",
            )
            val subtitle = if (dayData.sourceDate != dayData.targetDate) {
                "${dayData.weekdayLabel} · 按${sourceDateLabel(dayData.sourceDate)}课"
            } else {
                dayData.weekdayLabel
            }
            views.setTextViewText(R.id.widget_subtitle, subtitle)
            views.setViewVisibility(
                R.id.widget_subtitle,
                if (sizeClass == WidgetSizeClass.Compact) View.GONE else View.VISIBLE,
            )
            views.setOnClickPendingIntent(
                R.id.widget_prev,
                actionPendingIntent(
                    context,
                    appWidgetId,
                    ScheduleWidgetActionReceiver.ACTION_PREV,
                    dayData.offset,
                ),
            )
            views.setOnClickPendingIntent(
                R.id.widget_next,
                actionPendingIntent(
                    context,
                    appWidgetId,
                    ScheduleWidgetActionReceiver.ACTION_NEXT,
                    dayData.offset,
                ),
            )
            views.setViewVisibility(R.id.widget_prev, View.VISIBLE)
            views.setViewVisibility(R.id.widget_next, View.VISIBLE)
            views.setViewVisibility(R.id.widget_reset, if (dayData.manualOffset == 0) View.GONE else View.VISIBLE)
            views.setOnClickPendingIntent(
                R.id.widget_reset,
                actionPendingIntent(
                    context,
                    appWidgetId,
                    ScheduleWidgetActionReceiver.ACTION_RESET,
                    dayData.offset,
                ),
            )

            views.setRemoteAdapter(R.id.widget_course_list, courseListIntent(context, appWidgetId))
            views.setEmptyView(R.id.widget_course_list, R.id.widget_empty)
            val hasRows = dayData.rows.isNotEmpty()
            views.setViewVisibility(R.id.widget_course_list, if (hasRows) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_empty, if (hasRows) View.GONE else View.VISIBLE)
            views.setInt(R.id.widget_empty, "setBackgroundResource", widgetRowVariantBackground(dayData.themeAccent))
            views.setTextViewText(R.id.widget_empty, WidgetDayLabels.empty(dayData.offset))
            return views
        }

        private fun actionPendingIntent(
            context: Context,
            appWidgetId: Int,
            action: String,
            currentOffset: Int,
        ): PendingIntent {
            val intent = Intent(context, ScheduleWidgetActionReceiver::class.java).apply {
                setPackage(context.packageName)
                putExtra(ScheduleWidgetActionReceiver.EXTRA_ACTION, action)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(ScheduleWidgetActionReceiver.EXTRA_CURRENT_OFFSET, currentOffset)
            }
            return PendingIntent.getBroadcast(
                context,
                appWidgetId * 31 + action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun courseListIntent(context: Context, appWidgetId: Int): Intent =
            Intent(context, ScheduleWidgetRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }

        private val dateFormatter = DateTimeFormatter.ofPattern("M月d日")
        private fun sourceDateLabel(date: java.time.LocalDate): String =
            "${dateFormatter.format(date)}${weekdayLabel(date.dayOfWeek.value)}"

        private const val DEFAULT_MIN_WIDTH_DP = 220
        private const val DEFAULT_MIN_HEIGHT_DP = 180
    }
}

/**
 * Vendor-aware twin of [ScheduleGlanceWidgetReceiver]. Registered separately so MIUI / vivo /
 * HONOR launchers can pick it up via their custom widget actions and meta-data.
 */
class ScheduleGlanceWidgetReceiverMIUI : ScheduleGlanceWidgetReceiver()
