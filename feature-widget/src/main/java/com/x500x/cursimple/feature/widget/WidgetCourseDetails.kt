package com.x500x.cursimple.feature.widget

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.x500x.cursimple.core.kernel.model.*
import java.time.LocalDate

data class WidgetCourseDetailSelection(val courses: List<CourseItem>, val sourceWeeks: Map<CourseItem, Int?>,
    val week: Int?, val onHoliday: Boolean)

/** Read the same rescheduled, holiday and week-specific information rendered on the widget. */
fun widgetCourseDetailsForSlot(date: LocalDate, startNode: Int, endNode: Int, courses: List<CourseItem>,
    termStart: LocalDate?, overrides: List<TemporaryScheduleOverride>, holidays: HolidayCalendarSettings): WidgetCourseDetailSelection {
    val day = resolveWidgetScheduleDay(date, termStart, overrides, holidays) { weekday -> courses.filter { it.time.dayOfWeek == weekday } }
    val selected = day.courses.filter { it.time.startNode <= endNode && it.time.endNode >= startNode }
    val moved = coursesMovedToWithOrigin(date, overrides, { id -> courses.firstOrNull { it.id == id } },
        isOriginallyActive = { course, from -> course.activeOnWeek(resolveWeekIndex(from, termStart)) })
    val sourceWeeks = selected.associateWith { course ->
        val source = moved.firstOrNull { (item, _) -> item.id == course.id && item.time == course.time }?.second
            ?: temporaryScheduleCourseSourceDate(date, course, day.sourceDate, overrides) ?: day.sourceDate
        resolveWeekIndex(source, termStart)
    }
    return WidgetCourseDetailSelection(selected, sourceWeeks, day.weekIndex, day.onHoliday)
}

internal fun widgetCourseDetailPendingIntent(context: Context, widgetId: Int, termId: String, date: LocalDate,
    startNode: Int, endNode: Int): PendingIntent {
    val intent = Intent().setClassName(context, WidgetDeepLinks.COURSE_DETAIL_ACTIVITY)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        .setData(Uri.Builder().scheme("cursimple-widget").authority("course").appendPath(widgetId.toString()).appendPath(termId)
            .appendPath(date.toString()).appendPath("$startNode-$endNode").build())
        .putExtra(WidgetDeepLinks.EXTRA_OPEN_SCHEDULE_DATE, date.toString())
        .putExtra(WidgetDeepLinks.EXTRA_COURSE_START_NODE, startNode)
        .putExtra(WidgetDeepLinks.EXTRA_COURSE_END_NODE, endNode)
        .putExtra(WidgetDeepLinks.EXTRA_COURSE_TERM_ID, termId)
    return PendingIntent.getActivity(context, widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle())
}
