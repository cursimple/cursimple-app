package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class MemoTodoRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = MemoTodoListFactory(applicationContext, intent)
}

private class MemoTodoListFactory(appContext: Context, intent: Intent) : RemoteViewsService.RemoteViewsFactory {
    private var context = appContext.widgetLocaleContext()
    private val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    private val layout = MemoWidgetLayout(0, intent.getIntExtra("rowHeightDp", 64).coerceIn(48, 64),
        intent.getBooleanExtra("compact", false), intent.getBooleanExtra("minimal", false))
    private var snapshot: MemoTodoData? = null

    override fun onCreate() = Unit
    override fun onDataSetChanged() {
        context = context.widgetLocaleContext()
        runCatching { runBlocking(Dispatchers.IO) { loadMemoTodos(context) } }
            .onSuccess { snapshot = it }
            .onFailure { com.x500x.cursimple.core.reminder.logging.ReminderLogger.warn("widget.memo.rows.failure", emptyMap(), it) }
    }
    override fun onDestroy() { snapshot = null }
    override fun getCount() = snapshot?.rows?.size ?: 0
    override fun getViewAt(position: Int): RemoteViews {
        val data = snapshot
        val row = data?.rows?.getOrNull(position)
        return if (row == null) getLoadingView()
        else memoTodoView(context, row, data, layout, widgetId, fillIn = true)
    }
    override fun getLoadingView(): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_memo_loading_row).apply {
            applyWidgetTextColors(context, snapshot?.theme ?: WidgetThemePreferences())
            val density = context.resources.displayMetrics.density
            setInt(R.id.memo_row_root, "setMinimumHeight", (layout.rowHeightDp * density).toInt())
            val padding = ((if (layout.compact) 0 else 5) * density).toInt()
            setViewPadding(R.id.memo_row_content, 0, padding, 0, padding)
            setTextViewText(R.id.memo_row_title, context.getString(R.string.widget_loading_text))
            setInt(R.id.memo_row_title, "setMaxLines", 1)
            setContentDescription(R.id.memo_row_root, context.getString(R.string.widget_loading_text))
            listOf(
                R.id.memo_checkbox, R.id.memo_row_source_row, R.id.memo_row_due,
                R.id.memo_row_pin, R.id.memo_row_dot_inline, R.id.memo_row_priority_stripe,
                R.id.memo_row_rule,
            ).forEach { setViewVisibility(it, View.GONE) }
        }
    override fun getViewTypeCount() = 1
    // IDs are unique within the current sorted snapshot, including colliding note hashes.
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = false
}
