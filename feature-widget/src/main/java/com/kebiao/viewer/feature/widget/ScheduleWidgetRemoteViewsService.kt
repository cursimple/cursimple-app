package com.kebiao.viewer.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
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

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        rows = runBlocking {
            ScheduleWidgetDataSource.loadDay(context, appWidgetId).rows
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
