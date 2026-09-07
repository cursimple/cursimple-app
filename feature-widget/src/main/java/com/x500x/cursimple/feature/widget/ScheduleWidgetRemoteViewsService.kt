package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
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
    private var widgetTheme: WidgetThemePreferences = WidgetThemePreferences()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        runCatching {
            runBlocking(Dispatchers.IO) {
                ScheduleWidgetDataSource.loadDay(context, appWidgetId, reuseRecent = true)
            }
        }.onSuccess { day ->
            rows = day.rows
            themeAccent = day.themeAccent
            widgetTheme = day.widgetTheme
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

        // 正在上的那一节用更深的同色底，一眼能从一列课里挑出来
        val background = if (rowData.status == CourseStatus.Live) {
            widgetRowVariantBackground(themeAccent)
        } else {
            widgetRowBackground(themeAccent)
        }
        row.setInt(R.id.course_row_root, "setBackgroundResource", background)
        row.applyOpenAppFillInIntent(R.id.course_row_root, widgetTheme)
        row.setTextViewText(R.id.course_nodes, rowData.nodeRange)
        row.setTextViewText(R.id.course_time, rowData.timeRange)
        row.setTextViewText(R.id.course_title, rowData.title)
        row.setTextViewText(R.id.course_subtitle, rowData.subtitle)
        if (rowData.onHoliday) {
            // 放假当天的行整体调灰，与课表里的不可用态保持一致
            val primary = ContextCompat.getColor(context, R.color.widget_row_holiday_primary)
            val secondary = ContextCompat.getColor(context, R.color.widget_row_holiday_secondary)
            row.setTextColor(R.id.course_title, primary)
            row.setTextColor(R.id.course_nodes, secondary)
            row.setTextColor(R.id.course_time, secondary)
            row.setTextColor(R.id.course_subtitle, secondary)
        }
        // 上课中与即将开始比提醒标记更该被看到，同一个位置上让状态优先
        val badgeText = when (rowData.status) {
            CourseStatus.Live, CourseStatus.Soon ->
                context.getString(widgetCourseStatusRes(rowData.status, rowData.isExam))
            else -> if (rowData.hasReminder) context.getString(R.string.widget_course_reminder_badge) else null
        }
        row.setViewVisibility(R.id.course_badge, if (badgeText == null) View.GONE else View.VISIBLE)
        row.setTextViewText(R.id.course_badge, badgeText.orEmpty())
        if (rowData.status == CourseStatus.Live) {
            row.setInt(R.id.course_badge, "setBackgroundResource", R.drawable.widget_bg_badge_live)
            row.setTextColor(
                R.id.course_badge,
                ContextCompat.getColor(context, R.color.widget_badge_live_text),
            )
        }
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
