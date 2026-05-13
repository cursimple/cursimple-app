package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.x500x.cursimple.core.data.ThemeAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class NextCourseRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        return NextCourseListFactory(applicationContext, appWidgetId)
    }
}

private class NextCourseListFactory(
    private val context: Context,
    @Suppress("unused") private val appWidgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {
    private var rows: List<NextCourseRow> = emptyList()
    private var themeAccent: ThemeAccent = ThemeAccent.Green

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        runCatching {
            runBlocking(Dispatchers.IO) {
                NextCourseDataSource.load(context)
            }
        }.onSuccess { data ->
            rows = data.rows
            themeAccent = data.themeAccent
        }
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_next_course_row)
        val data = rows.getOrNull(position) ?: return row
        val backgroundRes = if (data.isPast) {
            widgetRowVariantBackground(themeAccent)
        } else {
            widgetRowBackground(themeAccent)
        }
        row.setInt(R.id.next_course_row_root, "setBackgroundResource", backgroundRes)
        row.setTextViewText(R.id.next_course_label, data.label)
        row.setTextViewText(R.id.next_course_period, data.period)
        row.setTextViewText(R.id.next_course_name, data.title)
        row.setTextViewText(R.id.next_course_time, data.time)
        row.setTextViewText(R.id.next_course_sub, data.sub)
        return row
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        rows.getOrNull(position)?.stableId ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
