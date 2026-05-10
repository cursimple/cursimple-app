package com.kebiao.viewer.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.kebiao.viewer.core.data.ThemeAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class ScheduleWidgetRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        return ScheduleCourseListFactory(applicationContext, appWidgetId)
    }
}

private class ScheduleCourseListFactory(
    private val context: Context,
    private val appWidgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {
    private var rows: List<ScheduleWidgetCourseRow> = emptyList()
    private var themeAccent: ThemeAccent = ThemeAccent.Green

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        runCatching {
            runBlocking(Dispatchers.IO) {
                ScheduleWidgetDataSource.loadDay(context, appWidgetId)
            }
        }.onSuccess { day ->
            rows = day.rows
            themeAccent = day.themeAccent
        }
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val rowData = rows.getOrNull(position)
        val row = RemoteViews(context.packageName, R.layout.widget_schedule_course_row)
        if (rowData == null) return row

        row.setInt(R.id.course_row_root, "setBackgroundResource", widgetRowBackground(themeAccent))
        row.setTextViewText(R.id.course_nodes, rowData.nodeRange)
        row.setTextViewText(R.id.course_time, rowData.timeRange)
        row.setTextViewText(R.id.course_title, rowData.title)
        row.setTextViewText(R.id.course_subtitle, rowData.subtitle)
        row.setViewVisibility(R.id.course_badge, if (rowData.hasReminder) View.VISIBLE else View.GONE)
        row.setTextViewText(R.id.course_badge, if (rowData.hasReminder) "提醒" else "")
        return row
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        rows.getOrNull(position)?.stableId ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}

internal fun widgetRowBackground(accent: ThemeAccent): Int = when (accent) {
    ThemeAccent.Green -> R.drawable.widget_bg_surface_green
    ThemeAccent.Blue -> R.drawable.widget_bg_surface_blue
    ThemeAccent.Purple -> R.drawable.widget_bg_surface_purple
    ThemeAccent.Orange -> R.drawable.widget_bg_surface_orange
    ThemeAccent.Pink -> R.drawable.widget_bg_surface_pink
}

internal fun widgetRowVariantBackground(accent: ThemeAccent): Int = when (accent) {
    ThemeAccent.Green -> R.drawable.widget_bg_surface_variant_green
    ThemeAccent.Blue -> R.drawable.widget_bg_surface_variant_blue
    ThemeAccent.Purple -> R.drawable.widget_bg_surface_variant_purple
    ThemeAccent.Orange -> R.drawable.widget_bg_surface_variant_orange
    ThemeAccent.Pink -> R.drawable.widget_bg_surface_variant_pink
}
