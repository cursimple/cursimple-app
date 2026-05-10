package com.kebiao.viewer.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.kebiao.viewer.core.data.ThemeAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class ReminderRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        return ReminderListFactory(applicationContext, appWidgetId)
    }
}

private class ReminderListFactory(
    private val context: Context,
    @Suppress("unused") private val appWidgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {
    private var rows: List<ReminderRowData> = emptyList()
    private var themeAccent: ThemeAccent = ThemeAccent.Green

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        runCatching {
            runBlocking(Dispatchers.IO) {
                ReminderDataSource.load(context)
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
        val row = RemoteViews(context.packageName, R.layout.widget_reminder_row)
        val data = rows.getOrNull(position) ?: return row
        row.setInt(R.id.reminder_row_root, "setBackgroundResource", widgetRowBackground(themeAccent))
        row.setTextViewText(R.id.reminder_date, data.dateLabel)
        row.setTextViewText(R.id.reminder_time, data.timeLabel)
        row.setTextViewText(R.id.reminder_row_title, data.title)
        row.setTextViewText(R.id.reminder_row_message, data.message)
        row.setTextViewText(R.id.reminder_countdown, data.countdown)
        return row
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        rows.getOrNull(position)?.stableId ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
