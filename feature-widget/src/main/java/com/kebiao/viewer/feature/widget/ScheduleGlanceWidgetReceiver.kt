package com.kebiao.viewer.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.kebiao.viewer.core.data.widget.DataStoreWidgetPreferencesRepository
import com.kebiao.viewer.core.data.widget.WidgetScheduleSnapshot
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.kernel.model.coursesOfDay
import com.kebiao.viewer.core.kernel.model.endLocalTime
import com.kebiao.viewer.core.kernel.model.findSlot
import com.kebiao.viewer.core.kernel.model.startLocalTime
import com.kebiao.viewer.core.kernel.time.BeijingTime
import com.kebiao.viewer.core.reminder.model.ReminderRule
import com.kebiao.viewer.core.reminder.model.ReminderScopeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

open class ScheduleGlanceWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context.applicationContext, appWidgetIds)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val repository = DataStoreWidgetPreferencesRepository(appContext)
            appWidgetIds.forEach { repository.clearWidgetDayOffset(it) }
            WidgetCatalog.notifyInstalledChanged(appContext)
        }
    }

    companion object {
        fun updateWidgets(context: Context, appWidgetIds: IntArray? = null) {
            val appContext = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                val manager = AppWidgetManager.getInstance(appContext)
                val ids = appWidgetIds ?: scheduleWidgetIds(appContext, manager)
                if (ids.isEmpty()) return@launch
                val repository = DataStoreWidgetPreferencesRepository(appContext)
                val snapshot = repository.scheduleSnapshotFlow.first()
                ids.forEach { appWidgetId ->
                    val offset = repository.widgetDayOffset(appWidgetId)
                    manager.updateAppWidget(
                        appWidgetId,
                        buildScheduleViews(appContext, appWidgetId, offset, snapshot),
                    )
                }
            }
        }

        private fun scheduleWidgetIds(context: Context, manager: AppWidgetManager): IntArray {
            val ids = WidgetCatalog.entries(context)
                .firstOrNull { it.id == "today" }
                ?.let { entry ->
                    (listOf(entry.provider) + entry.vendorProviders)
                        .flatMap { component ->
                            runCatching { manager.getAppWidgetIds(component).toList() }.getOrDefault(emptyList())
                        }
                }
                .orEmpty()
            return ids.toIntArray()
        }

        private fun buildScheduleViews(
            context: Context,
            appWidgetId: Int,
            offset: Int,
            snapshot: WidgetScheduleSnapshot?,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_schedule_today)
            val zone = BeijingTime.resolveZone(snapshot?.timeZoneId ?: DEFAULT_TIME_ZONE_ID)
            BeijingTime.setForcedNow(snapshot?.debugForcedDateTimeIso?.let(::parseLocalDateTime))
            val today = BeijingTime.todayIn(zone)
            val targetDate = today.plusDays(offset.toLong())
            val weekIndex = resolveWeekIndex(targetDate, snapshot?.termStartDateIso?.let(::parseLocalDate))
            val courses = snapshot.coursesOfDay(targetDate.dayOfWeek.value)
                .filter { it.activeOnWeek(weekIndex) }
                .sortedBy { it.time.startNode }

            views.setTextViewText(R.id.widget_title, "${dateFormatter.format(targetDate)} · ${offsetTagLabel(offset)}")
            views.setTextViewText(R.id.widget_subtitle, weekdayLabel(targetDate))
            views.setOnClickPendingIntent(
                R.id.widget_prev,
                actionPendingIntent(context, appWidgetId, ScheduleWidgetActionReceiver.ACTION_PREV),
            )
            views.setOnClickPendingIntent(
                R.id.widget_next,
                actionPendingIntent(context, appWidgetId, ScheduleWidgetActionReceiver.ACTION_NEXT),
            )
            views.setViewVisibility(R.id.widget_prev, if (offset > -1) View.VISIBLE else View.INVISIBLE)
            views.setViewVisibility(R.id.widget_next, if (offset < 1) View.VISIBLE else View.INVISIBLE)
            views.setViewVisibility(R.id.widget_reset, if (offset == 0) View.GONE else View.VISIBLE)
            views.setOnClickPendingIntent(
                R.id.widget_reset,
                actionPendingIntent(context, appWidgetId, ScheduleWidgetActionReceiver.ACTION_RESET),
            )

            views.removeAllViews(R.id.widget_course_list)
            if (courses.isEmpty()) {
                views.setViewVisibility(R.id.widget_course_list, View.GONE)
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                views.setTextViewText(R.id.widget_empty, offsetEmptyLabel(offset))
            } else {
                views.setViewVisibility(R.id.widget_course_list, View.VISIBLE)
                views.setViewVisibility(R.id.widget_empty, View.GONE)
                courses.take(MAX_ROWS).forEach { course ->
                    views.addView(
                        R.id.widget_course_list,
                        buildCourseRow(context, course, snapshot?.timingProfile, snapshot?.reminderRules.orEmpty()),
                    )
                }
            }
            return views
        }

        private fun buildCourseRow(
            context: Context,
            course: CourseItem,
            timingProfile: TermTimingProfile?,
            reminderRules: List<ReminderRule>,
        ): RemoteViews {
            val row = RemoteViews(context.packageName, R.layout.widget_schedule_course_row)
            val nodeRange = "${course.time.startNode}-${course.time.endNode}节"
            val timeRange = timingProfile?.courseClockRange(course) ?: nodeRange
            val subline = listOf(course.location, course.teacher)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { "待定" }
            row.setTextViewText(R.id.course_nodes, nodeRange)
            row.setTextViewText(R.id.course_time, timeRange)
            row.setTextViewText(R.id.course_title, course.title)
            row.setTextViewText(R.id.course_subtitle, subline)
            row.setTextViewText(R.id.course_badge, if (reminderRules.any { it.matches(course) }) "提醒" else "")
            return row
        }

        private fun actionPendingIntent(context: Context, appWidgetId: Int, action: String): PendingIntent {
            val intent = Intent(context, ScheduleWidgetActionReceiver::class.java).apply {
                setPackage(context.packageName)
                putExtra(ScheduleWidgetActionReceiver.EXTRA_ACTION, action)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            return PendingIntent.getBroadcast(
                context,
                appWidgetId * 31 + action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun WidgetScheduleSnapshot?.coursesOfDay(dayOfWeek: Int): List<CourseItem> {
            if (this == null) return emptyList()
            val importedCourses = schedule?.coursesOfDay(dayOfWeek).orEmpty()
            val manualDayCourses = manualCourses.filter { it.time.dayOfWeek == dayOfWeek }
            return importedCourses + manualDayCourses
        }

        private fun ReminderRule.matches(course: CourseItem): Boolean = when (scopeType) {
            ReminderScopeType.SingleCourse -> courseId == course.id
            ReminderScopeType.TimeSlot ->
                startNode == course.time.startNode && endNode == course.time.endNode
        }

        private fun TermTimingProfile.courseStartTime(course: CourseItem): LocalTime? =
            findSlot(course.time.startNode, course.time.endNode)?.startLocalTime()

        private fun TermTimingProfile.courseClockRange(course: CourseItem): String =
            findSlot(course.time.startNode, course.time.endNode)?.let { slot ->
                "${clockFormatter.format(slot.startLocalTime())}-${clockFormatter.format(slot.endLocalTime())}"
            } ?: "${course.time.startNode}-${course.time.endNode}节"

        private fun weekdayLabel(date: LocalDate): String = when (date.dayOfWeek.value) {
            1 -> "星期一"
            2 -> "星期二"
            3 -> "星期三"
            4 -> "星期四"
            5 -> "星期五"
            6 -> "星期六"
            7 -> "星期日"
            else -> ""
        }

        private fun offsetTagLabel(offset: Int): String = when (offset) {
            -1 -> "昨天"
            0 -> "今天"
            1 -> "明天"
            else -> if (offset > 0) "+$offset 天" else "$offset 天"
        }

        private fun offsetEmptyLabel(offset: Int): String = when (offset) {
            -1 -> "昨日没有课程"
            0 -> "今日没有课程，享受一天"
            1 -> "明日没有课程"
            else -> "当日没有课程"
        }

        private fun parseLocalDate(value: String): LocalDate? =
            runCatching { LocalDate.parse(value) }.getOrNull()

        private fun parseLocalDateTime(value: String): LocalDateTime? =
            runCatching { LocalDateTime.parse(value) }.getOrNull()

        private val dateFormatter = DateTimeFormatter.ofPattern("M月d日")
        private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private const val DEFAULT_TIME_ZONE_ID = "Asia/Shanghai"
        private const val MAX_ROWS = 4
    }
}

/**
 * Vendor-aware twin of [ScheduleGlanceWidgetReceiver]. Registered separately so MIUI / vivo /
 * HONOR launchers can pick it up via their custom widget actions and meta-data.
 */
class ScheduleGlanceWidgetReceiverMIUI : ScheduleGlanceWidgetReceiver()
