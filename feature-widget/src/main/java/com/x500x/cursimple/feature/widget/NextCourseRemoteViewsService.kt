package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
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
    appContext: Context,
    private val appWidgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {
    // 工厂会被系统长期复用，改完语言进程又不重启，所以每次取数都重新按当前语言包一层
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
            val sizeClass = widgetSizeClass(AppWidgetManager.getInstance(context), appWidgetId)
            rows = visibleNextCourseRows(data.rows, sizeClass)
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
            ?: return RemoteViews(context.packageName, R.layout.widget_next_course_row)
        return buildNextCourseRow(context, data, themeAccent, widgetTheme)
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        rows.getOrNull(position)?.stableId ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}

/** 下一节课那一行的 RemoteViews，列表服务与内联行共用。 */
internal fun buildNextCourseRow(
    context: Context,
    data: NextCourseRow,
    themeAccent: ThemeAccent,
    widgetTheme: WidgetThemePreferences,
): RemoteViews {
    val row = RemoteViews(context.packageName, R.layout.widget_next_course_row)
    row.applyAccentBackground(
        R.id.next_course_row_root,
        widgetTheme,
        if (data.isPast) WidgetSurfaceTone.RowVariant else WidgetSurfaceTone.Row,
    )
    row.applyOpenAppFillInIntent(R.id.next_course_row_root, widgetTheme)
    row.setTextViewText(R.id.next_course_label, data.label)
    row.setTextViewText(
        R.id.next_course_period,
        widgetSlotCellText(data.slotLabel, data.nodeNumbers, fallback = data.period),
    )
    row.setTextColor(R.id.next_course_period, widgetAccentTextColor(widgetTheme))
    row.setTextViewText(R.id.next_course_name, data.title)
    row.setTextViewText(R.id.next_course_time, data.time)
    row.setTextViewText(R.id.next_course_sub, data.sub)
    return row
}
