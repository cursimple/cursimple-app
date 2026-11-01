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
    appContext: Context,
    private val appWidgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {
    private var context: Context = appContext.widgetLocaleContext()
    private var rows: List<ScheduleWidgetCourseRow> = emptyList()
    private var themeAccent: ThemeAccent = ThemeAccent.Green
    private var widgetTheme: WidgetThemePreferences = WidgetThemePreferences()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        context = context.widgetLocaleContext()
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
            ?: return RemoteViews(context.packageName, R.layout.widget_schedule_course_row_surface)
        return buildScheduleCourseRow(context, rowData, themeAccent, widgetTheme)
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        rows.getOrNull(position)?.stableId ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}

/** Share row rendering between service adapters and API 31+ inline collections. */
internal fun buildScheduleCourseRow(
    context: Context,
    rowData: ScheduleWidgetCourseRow,
    themeAccent: ThemeAccent,
    widgetTheme: WidgetThemePreferences,
): RemoteViews {
    val row = RemoteViews(context.packageName, R.layout.widget_schedule_course_row_surface)
    row.applyAccentBackground(
        widgetTheme,
        if (rowData.status == CourseStatus.Live) WidgetSurfaceTone.RowVariant else WidgetSurfaceTone.Row,
        isDark = resolveWidgetIsDark(context, widgetTheme.widgetThemeMode),
    )
    row.applyOpenAppFillInIntent(R.id.course_row_root, widgetTheme)
    row.setTextViewText(
        R.id.course_nodes,
        widgetSlotCellText(rowData.slotLabel, rowData.nodeNumbers, fallback = rowData.nodeRange),
    )
    row.setTextViewText(R.id.course_time, rowData.timeRange)
    row.setTextViewText(R.id.course_title, rowData.title)
    row.setTextViewText(R.id.course_subtitle, rowData.subtitle)
    // Events may have neither place nor note; launchers reuse rows, so set both states.
    row.setViewVisibility(R.id.course_subtitle, if (rowData.subtitle.isBlank()) View.GONE else View.VISIBLE)
    // Apply colors in every state because launchers reuse previously greyed rows.
    if (rowData.onHoliday) {
        val primary = ContextCompat.getColor(context, R.color.widget_row_holiday_primary)
        val secondary = ContextCompat.getColor(context, R.color.widget_row_holiday_secondary)
        row.setTextColor(R.id.course_title, primary)
        row.setTextColor(R.id.course_nodes, secondary)
        row.setTextColor(R.id.course_time, secondary)
        row.setTextColor(R.id.course_subtitle, secondary)
    } else {
        row.setTextColor(R.id.course_title, ContextCompat.getColor(context, R.color.widget_row_title))
        row.setTextColor(R.id.course_nodes, ContextCompat.getColor(context, R.color.widget_row_nodes))
        row.setTextColor(R.id.course_time, ContextCompat.getColor(context, R.color.widget_row_time))
        row.setTextColor(R.id.course_subtitle, ContextCompat.getColor(context, R.color.widget_row_subtitle))
    }
    // In-progress and starting-soon status takes priority over reminder markers.
    val badgeText = when (rowData.status) {
        CourseStatus.Live, CourseStatus.Soon -> context.getString(
            if (rowData.isEvent && rowData.status == CourseStatus.Live) {
                R.string.widget_status_event_live
            } else {
                widgetCourseStatusRes(rowData.status, rowData.isExam)
            },
        )
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
    } else {
        row.setInt(R.id.course_badge, "setBackgroundResource", R.drawable.widget_bg_badge)
        row.setTextColor(R.id.course_badge, ContextCompat.getColor(context, R.color.widget_badge_text))
    }
    if (!rowData.onHoliday && resolveWidgetIsDark(context, widgetTheme.widgetThemeMode)) {
        row.setTextColor(R.id.course_title, 0xFFE6E1E5.toInt())
        listOf(R.id.course_nodes, R.id.course_time, R.id.course_subtitle).forEach { row.setTextColor(it, 0xFFCAC4D0.toInt()) }
    }
    return row
}

internal fun widgetRowBackground(accent: ThemeAccent): Int = when (accent) {
    ThemeAccent.Green, ThemeAccent.Custom -> R.drawable.widget_bg_surface_green_clickable
    ThemeAccent.Blue -> R.drawable.widget_bg_surface_blue_clickable
    ThemeAccent.Purple -> R.drawable.widget_bg_surface_purple_clickable
    ThemeAccent.Orange -> R.drawable.widget_bg_surface_orange_clickable
    ThemeAccent.Pink -> R.drawable.widget_bg_surface_pink_clickable
}

internal fun widgetRowVariantBackground(accent: ThemeAccent): Int = when (accent) {
    ThemeAccent.Green, ThemeAccent.Custom -> R.drawable.widget_bg_surface_variant_green_clickable
    ThemeAccent.Blue -> R.drawable.widget_bg_surface_variant_blue_clickable
    ThemeAccent.Purple -> R.drawable.widget_bg_surface_variant_purple_clickable
    ThemeAccent.Orange -> R.drawable.widget_bg_surface_variant_orange_clickable
    ThemeAccent.Pink -> R.drawable.widget_bg_surface_variant_pink_clickable
}
