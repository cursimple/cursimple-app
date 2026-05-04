@file:OptIn(androidx.glance.ExperimentalGlanceApi::class)

package com.kebiao.viewer.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kebiao.viewer.core.data.DataStoreScheduleRepository
import com.kebiao.viewer.core.data.DataStoreUserPreferencesRepository
import com.kebiao.viewer.core.data.reminder.DataStoreReminderRepository
import com.kebiao.viewer.core.data.term.DataStoreTermProfileRepository
import com.kebiao.viewer.core.data.widget.DataStoreWidgetPreferencesRepository
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.kernel.model.coursesOfDay
import com.kebiao.viewer.core.kernel.model.endLocalTime
import com.kebiao.viewer.core.kernel.model.findSlot
import com.kebiao.viewer.core.kernel.model.startLocalTime
import com.kebiao.viewer.core.kernel.time.BeijingTime
import com.kebiao.viewer.core.reminder.model.ReminderRule
import com.kebiao.viewer.core.reminder.model.ReminderScopeType
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ScheduleGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val termProfileRepository = DataStoreTermProfileRepository(appContext)
        val scheduleRepository = DataStoreScheduleRepository(appContext, termProfileRepository)
        val reminderRepository = DataStoreReminderRepository(appContext)
        val widgetPreferencesRepository = DataStoreWidgetPreferencesRepository(appContext)
        val userPreferencesRepository = DataStoreUserPreferencesRepository(appContext)
        val schedule = scheduleRepository.scheduleFlow.first()
        val offset = widgetPreferencesRepository.widgetDayOffsetFlow.first()
        val reminderRules = reminderRepository.reminderRulesFlow.first()
        val timingProfile = widgetPreferencesRepository.timingProfileFlow.first()
        val userPrefs = userPreferencesRepository.preferencesFlow.first()
        val zone = BeijingTime.resolveZone(userPrefs.timeZoneId)
        BeijingTime.setForcedNow(userPrefs.debugForcedDateTime)

        val today = BeijingTime.todayIn(zone)
        val targetDate = today.plusDays(offset.toLong())
        val isToday = offset == 0
        val now = BeijingTime.nowTimeIn(zone)
        val dayOfWeek = targetDate.dayOfWeek.value
        val weekIndex = resolveWeekIndex(targetDate, userPrefs.termStartDate)
        val courses = schedule?.coursesOfDay(dayOfWeek).orEmpty()
            .filter { it.activeOnWeek(weekIndex) }
            .sortedBy { it.time.startNode }

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(WidgetStyle.cardCorner)
                        .padding(WidgetStyle.outerPadding),
                ) {
                    DayHeader(date = targetDate, offset = offset)
                    Spacer(GlanceModifier.height(8.dp))
                    if (courses.isEmpty()) {
                        EmptyDay(offset = offset)
                    } else {
                        LazyColumn(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .defaultWeight(),
                        ) {
                            items(items = courses, itemId = { it.id.hashCode().toLong() }) { course ->
                                val courseStart = timingProfile?.courseStartTime(course)
                                val courseEnd = timingProfile?.courseEndTime(course)
                                val isCurrent = isToday && courseStart != null && courseEnd != null &&
                                    !now.isBefore(courseStart) && now.isBefore(courseEnd)
                                Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                                    CourseEntry(
                                        course = course,
                                        profile = timingProfile,
                                        isCurrent = isCurrent,
                                        hasReminder = reminderRules.any { it.matches(course) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun refreshAll(context: Context) {
        updateAll(context)
    }
}

@Composable
private fun DayHeader(date: LocalDate, offset: Int) {
    val context = LocalContext.current
    val dateFormatter = DateTimeFormatter.ofPattern("M月d日")
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircleButton(
            label = "‹",
            onClick = if (offset > -1) {
                ScheduleWidgetActionReceiver.action(context, ScheduleWidgetActionReceiver.ACTION_PREV)
            } else {
                null
            },
        )
        Spacer(GlanceModifier.width(8.dp))
        Box(
            modifier = GlanceModifier
                .defaultWeight(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateFormatter.format(date),
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    PillBadge(
                        text = offsetTagLabel(offset),
                        container = if (offset == 0) GlanceTheme.colors.primary
                        else GlanceTheme.colors.surfaceVariant,
                        onContainer = if (offset == 0) GlanceTheme.colors.onPrimary
                        else GlanceTheme.colors.onSurfaceVariant,
                    )
                }
                Spacer(GlanceModifier.height(2.dp))
                if (offset == 0) {
                    Text(
                        text = weekdayLabel(date),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp,
                        ),
                        maxLines = 1,
                    )
                } else {
                    ResetTodayButton(date)
                }
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        IconCircleButton(
            label = "›",
            onClick = if (offset < 1) {
                ScheduleWidgetActionReceiver.action(context, ScheduleWidgetActionReceiver.ACTION_NEXT)
            } else {
                null
            },
        )
    }
}

@Composable
private fun ResetTodayButton(date: LocalDate) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(10.dp)
            .clickable(ScheduleWidgetActionReceiver.action(context, ScheduleWidgetActionReceiver.ACTION_RESET))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${weekdayLabel(date)} · 回今天",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun CourseEntry(
    course: CourseItem,
    profile: TermTimingProfile?,
    isCurrent: Boolean,
    hasReminder: Boolean,
) {
    val nodeRange = "${course.time.startNode}-${course.time.endNode}节"
    val timeRange = profile?.courseClockRange(course) ?: nodeRange
    val accent = if (isCurrent) GlanceTheme.colors.primary else GlanceTheme.colors.tertiary
    AccentRow(accent = accent) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = nodeRange,
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = timeRange,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                    maxLines = 1,
                )
                if (isCurrent) {
                    Spacer(GlanceModifier.width(6.dp))
                    PillBadge(
                        text = "进行中",
                        container = GlanceTheme.colors.primary,
                        onContainer = GlanceTheme.colors.onPrimary,
                    )
                }
                if (hasReminder) {
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = "🔔",
                        style = TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 11.sp,
                        ),
                    )
                }
            }
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = course.title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            val subline = listOf(course.location, course.teacher)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { "待定" }
            Text(
                text = subline,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EmptyDay(offset: Int) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(WidgetStyle.rowCorner)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🌿",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 18.sp,
                ),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = offsetEmptyLabel(offset),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

private fun ReminderRule.matches(course: CourseItem): Boolean = when (scopeType) {
    ReminderScopeType.SingleCourse -> courseId == course.id
    ReminderScopeType.TimeSlot ->
        startNode == course.time.startNode && endNode == course.time.endNode
}

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
