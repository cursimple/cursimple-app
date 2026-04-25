package com.kebiao.viewer.feature.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kebiao.viewer.core.data.DataStoreScheduleRepository
import com.kebiao.viewer.core.kernel.model.coursesOfDay
import kotlinx.coroutines.flow.first
import java.util.Calendar

class ScheduleGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = DataStoreScheduleRepository(context.applicationContext)
        val schedule = repository.scheduleFlow.first()
        val todayCourses = schedule
            ?.coursesOfDay(todayDayOfWeek())
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
                        text = "课表查看",
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    if (todayCourses.isEmpty()) {
                        Text(
                            text = "今日无课程",
                            style = TextStyle(color = GlanceTheme.colors.onBackground),
                        )
                    } else {
                        todayCourses.take(4).forEach { course ->
                            Text(
                                text = "${course.time.startNode}-${course.time.endNode} ${course.title} @ ${course.location.ifBlank { "待定" }}",
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

    private fun todayDayOfWeek(): Int {
        val calendarDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return when (calendarDay) {
            Calendar.SUNDAY -> 7
            else -> calendarDay - 1
        }
    }
}
