package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.widget.FrameLayout
import android.view.View
import android.view.ViewGroup
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

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
    appContext: Context,
    private val appWidgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {
    private var context: Context = appContext.widgetLocaleContext()
    private var rows: List<ReminderRowData> = emptyList()
    private var widgetTheme: WidgetThemePreferences = WidgetThemePreferences()
    private var compact = false

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        context = context.widgetLocaleContext()
        runCatching {
            runBlocking(Dispatchers.IO) {
                ReminderDataSource.load(context, reuseRecent = true)
            }
        }.onSuccess { data ->
            val layout = runBlocking { reminderWidgetLayout(context, appWidgetId, data) }
            rows = layout.rows
            compact = layout.compact
            widgetTheme = data.widgetTheme
        }
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val data = rows.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_reminder_row)
        return buildReminderRow(context, data, widgetTheme, compact)
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        rows.getOrNull(position)?.stableId ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}

internal fun buildReminderRow(
    context: Context,
    data: ReminderRowData,
    widgetTheme: WidgetThemePreferences,
    compact: Boolean = false,
): RemoteViews {
    val row = RemoteViews(context.packageName, if (compact) R.layout.widget_reminder_compact_row else R.layout.widget_reminder_row)
    row.applyAccentBackground(R.id.reminder_row_root, widgetTheme, WidgetSurfaceTone.Row)
    row.applyOpenAppFillInIntent(R.id.reminder_row_root, widgetTheme)
    row.setTextViewText(R.id.reminder_time, if (compact) "${data.dateLabel} ${data.timeLabel}" else data.timeLabel)
    row.setTextColor(R.id.reminder_time, widgetAccentTextColor(widgetTheme))
    row.setTextViewText(R.id.reminder_row_title, data.title)
    row.setContentDescription(R.id.reminder_row_root, "${data.title}, ${data.dateLabel} ${data.timeLabel}, ${data.message}")
    if (!compact) {
        row.setTextViewText(R.id.reminder_date, data.dateLabel)
        row.setTextViewText(R.id.reminder_row_message, data.message)
        row.setTextViewText(R.id.reminder_countdown, data.countdown)
    }
    return row
}

internal data class ReminderWidgetLayout(val rows: List<ReminderRowData>, val compact: Boolean)

internal fun reminderWidgetHeader(context: Context, data: ReminderWidgetData, compact: Boolean): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_reminder).apply {
        setTextViewText(R.id.reminder_title, context.getString(R.string.widget_label_reminder))
        setViewVisibility(R.id.reminder_badge, if (data.totalCount > 0) View.VISIBLE else View.GONE)
        setTextViewText(R.id.reminder_badge, context.getString(R.string.widget_reminder_badge_count, data.totalCount))
        if (compact) {
            val scale = context.resources.displayMetrics.density
            setViewPadding(R.id.reminder_content, (8 * scale).toInt(), (6 * scale).toInt(), (8 * scale).toInt(), (6 * scale).toInt())
        }
    }

/** Measure localized rows against the actual launcher viewport, including large fonts. */
internal suspend fun reminderWidgetLayout(context: Context, id: Int, data: ReminderWidgetData): ReminderWidgetLayout = withContext(Dispatchers.Main) {
    val manager = AppWidgetManager.getInstance(context)
    val options = manager.getAppWidgetOptions(id)
    val scale = context.resources.displayMetrics.density
    val width = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 330) * scale).toInt()
    val height = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 200) * scale).toInt()
    val sizeClass = widgetSizeClass(manager, id)
    val compact = sizeClass == WidgetSizeClass.Compact
    val parent = FrameLayout(context)
    val header = reminderWidgetHeader(context, data, compact).apply(context, parent)
    header.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
    var remaining = height - header.measuredHeight
    val rows = mutableListOf<ReminderRowData>()
    for (row in visibleReminderRows(data.rows, sizeClass)) {
        val view = buildReminderRow(context, row, data.widgetTheme, compact).apply(context, parent)
        view.measure(View.MeasureSpec.makeMeasureSpec((width - (if (compact) 16 else 24) * scale).toInt().coerceAtLeast(1), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val margins = view.layoutParams as? ViewGroup.MarginLayoutParams
        val rowHeight = view.measuredHeight + (margins?.topMargin ?: 0) + (margins?.bottomMargin ?: 0)
        if (rowHeight > remaining) break
        remaining -= rowHeight
        rows += row
    }
    ReminderWidgetLayout(rows, compact)
}
