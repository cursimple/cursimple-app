package com.kebiao.viewer.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kebiao.viewer.core.data.DataStoreScheduleRepository
import com.kebiao.viewer.core.data.DataStoreUserPreferencesRepository
import com.kebiao.viewer.core.data.reminder.DataStoreReminderRepository
import com.kebiao.viewer.core.data.widget.DataStoreWidgetPreferencesRepository
import com.kebiao.viewer.core.data.widget.WidgetDay
import com.kebiao.viewer.core.kernel.model.coursesOfDay
import com.kebiao.viewer.core.kernel.time.BeijingTime
import com.kebiao.viewer.core.reminder.model.ReminderScopeType
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

class ScheduleGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val scheduleRepository = DataStoreScheduleRepository(context.applicationContext)
        val reminderRepository = DataStoreReminderRepository(context.applicationContext)
        val widgetPreferencesRepository = DataStoreWidgetPreferencesRepository(context.applicationContext)
        val userPreferencesRepository = DataStoreUserPreferencesRepository(context.applicationContext)
        val schedule = scheduleRepository.scheduleFlow.first()
        val widgetDay = widgetPreferencesRepository.widgetDayFlow.first()
        val reminderRules = reminderRepository.reminderRulesFlow.first()
        val zone = BeijingTime.resolveZone(userPreferencesRepository.preferencesFlow.first().timeZoneId)
        val dayOfWeek = when (widgetDay) {
            WidgetDay.Today -> todayDayOfWeek(zone)
            WidgetDay.Tomorrow -> tomorrowDayOfWeek(zone)
        }
        val courses = schedule
            ?.coursesOfDay(dayOfWeek)
            .orEmpty()
            .sortedBy { it.time.startNode }

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.background)
                        .padding(12.dp),
                ) {
                    Text(
                        text = if (widgetDay == WidgetDay.Today) "今天课程" else "明天课程",
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Row {
                        Button(
                            text = if (widgetDay == WidgetDay.Today) "切换到明天" else "切换到今天",
                            onClick = actionRunCallback<ToggleWidgetDayAction>(),
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    if (courses.isEmpty()) {
                        Text(
                            text = if (widgetDay == WidgetDay.Today) "今天无课程" else "明天无课程",
                            style = TextStyle(color = GlanceTheme.colors.onBackground),
                        )
                    } else {
                        courses.take(4).forEach { course ->
                            Text(
                                text = buildCourseLine(
                                    title = course.title,
                                    startNode = course.time.startNode,
                                    endNode = course.time.endNode,
                                    location = course.location.ifBlank { "待定" },
                                    hasReminder = reminderRules.any { rule ->
                                        when (rule.scopeType) {
                                            ReminderScopeType.SingleCourse -> rule.courseId == course.id
                                            ReminderScopeType.TimeSlot -> {
                                                rule.startNode == course.time.startNode &&
                                                    rule.endNode == course.time.endNode
                                            }
                                        }
                                    },
                                ),
                                style = TextStyle(color = GlanceTheme.colors.onBackground),
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun refreshAll(context: Context) {
        updateAll(context)
    }

    private fun todayDayOfWeek(zone: ZoneId): Int = LocalDate.now(zone).dayOfWeek.value

    private fun tomorrowDayOfWeek(zone: ZoneId): Int = (todayDayOfWeek(zone) % 7) + 1

    private fun buildCourseLine(
        title: String,
        startNode: Int,
        endNode: Int,
        location: String,
        hasReminder: Boolean,
    ): String {
        val reminderMarker = if (hasReminder) " [提醒]" else ""
        return "$startNode-$endNode $title @ $location$reminderMarker"
    }
}

class ToggleWidgetDayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        DataStoreWidgetPreferencesRepository(context.applicationContext).toggleWidgetDay()
        ScheduleGlanceWidget().refreshAll(context.applicationContext)
    }
}
