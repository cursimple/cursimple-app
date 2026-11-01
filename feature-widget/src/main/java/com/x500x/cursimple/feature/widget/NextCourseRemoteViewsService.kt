package com.x500x.cursimple.feature.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class NextCourseRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        NextCourseListFactory(applicationContext)
}

private class NextCourseListFactory(
    appContext: Context,
) : RemoteViewsService.RemoteViewsFactory {
    private var context: Context = appContext.widgetLocaleContext()
    private var rows: List<NextCourseRow> = emptyList()
    private var themeAccent: ThemeAccent = ThemeAccent.Green
    private var widgetTheme: WidgetThemePreferences = WidgetThemePreferences()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        context = context.widgetLocaleContext()
        runCatching {
            runBlocking(Dispatchers.IO) {
                NextCourseDataSource.load(context, reuseRecent = true)
            }
        }.onSuccess { data ->
            rows = visibleNextCourseRows(data.rows)
            themeAccent = data.themeAccent
            widgetTheme = data.widgetTheme
        }
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val data = rows.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_next_course_row_surface)
        return buildNextCourseRow(context, data, themeAccent, widgetTheme)
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        rows.getOrNull(position)?.stableId ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}

internal fun buildNextCourseRow(
    context: Context,
    data: NextCourseRow,
    themeAccent: ThemeAccent,
    widgetTheme: WidgetThemePreferences,
): RemoteViews {
    val row = RemoteViews(context.packageName, R.layout.widget_next_course_row_surface)
    // The live or next course stands out like the timetable widget's live row. Launchers reuse rows, so set both states.
    row.applyAccentBackground(
        widgetTheme,
        if (data.isFocus) WidgetSurfaceTone.RowVariant else WidgetSurfaceTone.Row,
        isDark = resolveWidgetIsDark(context, widgetTheme.widgetThemeMode),
    )
    row.applyOpenAppFillInIntent(R.id.next_course_row_root, widgetTheme)
    row.setTextViewText(R.id.next_course_label, data.label)
    val density = context.resources.displayMetrics.density
    if (data.isFocus) {
        row.setInt(R.id.next_course_label, "setBackgroundResource", R.drawable.widget_bg_badge_live)
        row.setTextColor(R.id.next_course_label, ContextCompat.getColor(context, R.color.widget_badge_live_text))
        row.setViewPadding(R.id.next_course_label, (6 * density).toInt(), (1 * density).toInt(), (6 * density).toInt(), (1 * density).toInt())
    } else {
        row.setInt(R.id.next_course_label, "setBackgroundResource", 0)
        row.setTextColor(R.id.next_course_label, ContextCompat.getColor(context, R.color.widget_row_nodes))
        row.setViewPadding(R.id.next_course_label, 0, 0, 0, 0)
    }
    row.setTextViewText(
        R.id.next_course_period,
        widgetSlotCellText(data.slotLabel, data.nodeNumbers, fallback = data.period),
    )
    row.setTextColor(R.id.next_course_period, widgetAccentTextColor(widgetTheme, resolveWidgetIsDark(context, widgetTheme.widgetThemeMode)))
    row.setTextViewText(R.id.next_course_name, data.title)
    row.setTextViewText(R.id.next_course_time, data.time)
    row.setTextViewText(R.id.next_course_sub, data.sub)
    row.applyWidgetTextColors(context, widgetTheme)
    return row
}
