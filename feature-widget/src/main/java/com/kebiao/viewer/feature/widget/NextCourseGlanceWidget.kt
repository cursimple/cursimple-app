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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

open class NextCourseGlanceWidgetReceiver : AppWidgetProvider() {
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
        WidgetLifecycleRefresher.onWidgetSetChanged(context.applicationContext, reason = "next_widget_deleted")
    }

    companion object {
        @Suppress("DEPRECATION")
        fun updateWidgets(context: Context, appWidgetIds: IntArray? = null) {
            val appContext = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                val manager = AppWidgetManager.getInstance(appContext)
                val ids = appWidgetIds ?: collectIds(appContext, manager)
                if (ids.isEmpty()) return@launch
                val data = NextCourseDataSource.load(appContext)
                ids.forEach { appWidgetId ->
                    manager.updateAppWidget(appWidgetId, buildViews(appContext, appWidgetId, data))
                    manager.notifyAppWidgetViewDataChanged(intArrayOf(appWidgetId), R.id.next_course_list)
                }
            }
        }

        private fun collectIds(context: Context, manager: AppWidgetManager): IntArray {
            val ids = WidgetCatalog.entries(context)
                .firstOrNull { it.id == "next" }
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
            data: NextCourseWidgetData,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_next_course)
            views.setInt(R.id.next_course_root, "setBackgroundResource", widgetCardBackground(data.themeAccent))
            views.setTextViewText(R.id.next_course_title, data.headerLabel)
            if (data.badgeText != null) {
                views.setViewVisibility(R.id.next_course_badge, View.VISIBLE)
                views.setTextViewText(R.id.next_course_badge, data.badgeText)
            } else {
                views.setViewVisibility(R.id.next_course_badge, View.GONE)
            }
            val hasRows = data.rows.isNotEmpty()
            views.setRemoteAdapter(R.id.next_course_list, listIntent(context, appWidgetId))
            views.setEmptyView(R.id.next_course_list, R.id.next_course_empty)
            views.setViewVisibility(R.id.next_course_list, if (hasRows) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.next_course_empty, if (hasRows) View.GONE else View.VISIBLE)
            views.setInt(R.id.next_course_empty, "setBackgroundResource", widgetRowVariantBackground(data.themeAccent))
            views.setTextViewText(R.id.next_course_empty, data.emptyTitle)
            return views
        }

        private fun listIntent(context: Context, appWidgetId: Int): Intent =
            Intent(context, NextCourseRemoteViewsService::class.java).apply {
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

class NextCourseGlanceWidgetReceiverMIUI : NextCourseGlanceWidgetReceiver()
