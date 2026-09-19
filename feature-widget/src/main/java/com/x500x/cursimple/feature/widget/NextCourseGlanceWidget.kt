package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

open class NextCourseGlanceWidgetReceiver : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        // 厂商启动器的刷新广播不会变成 onUpdate，这里单独接一次
        if (handleVendorWidgetUpdate(context, intent) { updateWidgets(it) }) return
        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // 有的启动器加完小组件不发 onUpdate，会一直停在「加载中」
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                updateWidgets(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        reconcileSystemAlarmsFromWidget(context)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                updateWidgets(context.applicationContext, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        reconcileSystemAlarmsFromWidget(context)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                updateWidgets(context.applicationContext, intArrayOf(appWidgetId))
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetLifecycleRefresher.onWidgetSetChanged(context.applicationContext, reason = "next_widget_deleted")
    }

    companion object {
        @Suppress("DEPRECATION")
        suspend fun updateWidgets(context: Context, appWidgetIds: IntArray? = null) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = appWidgetIds ?: collectIds(appContext, manager)
            if (ids.isEmpty()) return
            val data = NextCourseDataSource.load(appContext)
            ids.forEach { appWidgetId ->
                manager.updateAppWidget(appWidgetId, buildViews(appContext, appWidgetId, data))
                manager.notifyAppWidgetViewDataChanged(intArrayOf(appWidgetId), R.id.next_course_list)
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
            views.applyWidgetBackground(context, R.id.next_course_root, data.widgetTheme)
            views.applyOpenAppClick(context, R.id.next_course_root, appWidgetId, data.widgetTheme)
            views.setTextViewText(R.id.next_course_title, data.headerLabel)
            if (data.badgeText != null) {
                views.setViewVisibility(R.id.next_course_badge, View.VISIBLE)
                views.setTextViewText(R.id.next_course_badge, data.badgeText)
            } else {
                views.setViewVisibility(R.id.next_course_badge, View.GONE)
            }
            // 行数按当前尺寸裁剪，与列表服务那条路取同一份
            val visibleRows = visibleNextCourseRows(
                data.rows,
                widgetSizeClass(AppWidgetManager.getInstance(context), appWidgetId),
            )
            val hasRows = visibleRows.isNotEmpty()
            views.setWidgetRows(
                listId = R.id.next_course_list,
                rows = visibleRows,
                stableId = { it.stableId },
                buildRow = { buildNextCourseRow(context, it, data.themeAccent, data.widgetTheme) },
                fallbackAdapter = {
                    views.setRemoteAdapter(
                        R.id.next_course_list,
                        listIntent(
                            context = context,
                            appWidgetId = appWidgetId,
                            revision = widgetListRevision(data.headerLabel, data.badgeText, visibleRows),
                        ),
                    )
                },
            )
            views.applyOpenAppListTemplate(context, R.id.next_course_list, appWidgetId, data.widgetTheme)
            views.setEmptyView(R.id.next_course_list, R.id.next_course_empty)
            views.setViewVisibility(R.id.next_course_list, if (hasRows) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.next_course_empty, if (hasRows) View.GONE else View.VISIBLE)
            views.applyOpenAppClick(context, R.id.next_course_empty, appWidgetId, data.widgetTheme)
            views.setInt(R.id.next_course_empty, "setBackgroundResource", widgetRowVariantBackground(data.themeAccent))
            views.setTextViewText(R.id.next_course_empty, data.emptyTitle)
            return views
        }

        private fun listIntent(context: Context, appWidgetId: Int, revision: String): Intent =
            Intent(context, NextCourseRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(EXTRA_WIDGET_LIST_REVISION, revision)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
    }
}

class NextCourseGlanceWidgetReceiverMIUI : NextCourseGlanceWidgetReceiver()
