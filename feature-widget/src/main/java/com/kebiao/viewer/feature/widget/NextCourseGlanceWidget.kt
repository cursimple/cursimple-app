@file:OptIn(androidx.glance.ExperimentalGlanceApi::class)

package com.kebiao.viewer.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import androidx.glance.LocalSize
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
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
import com.kebiao.viewer.core.data.DataStoreManualCourseRepository
import com.kebiao.viewer.core.data.DataStoreScheduleRepository
import com.kebiao.viewer.core.data.DataStoreUserPreferencesRepository
import com.kebiao.viewer.core.data.term.DataStoreTermProfileRepository
import com.kebiao.viewer.core.data.widget.DataStoreWidgetPreferencesRepository
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.kernel.model.coursesOfDay
import com.kebiao.viewer.core.kernel.model.endLocalTime
import com.kebiao.viewer.core.kernel.model.findSlot
import com.kebiao.viewer.core.kernel.model.startLocalTime
import com.kebiao.viewer.core.kernel.time.BeijingTime
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

class NextCourseGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(160.dp, 120.dp),
            DpSize(220.dp, 180.dp),
            DpSize(300.dp, 260.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val termProfileRepository = DataStoreTermProfileRepository(appContext)
        val scheduleRepository = DataStoreScheduleRepository(appContext, termProfileRepository)
        val manualCourseRepository = DataStoreManualCourseRepository(appContext, termProfileRepository)
        val userPreferencesRepository = DataStoreUserPreferencesRepository(appContext)
        val widgetPreferencesRepository = DataStoreWidgetPreferencesRepository(appContext)
        val schedule = scheduleRepository.scheduleFlow.first()
        val manualCourses = manualCourseRepository.manualCoursesFlow.first()
        val timingProfile = widgetPreferencesRepository.timingProfileFlow.first()
        val userPrefs = userPreferencesRepository.preferencesFlow.first()
        val zone = BeijingTime.resolveZone(userPrefs.timeZoneId)
        BeijingTime.setForcedNow(userPrefs.debugForcedDateTime)
        val today = BeijingTime.todayIn(zone)
        val now = BeijingTime.nowTimeIn(zone)
        val dayOfWeek = today.dayOfWeek.value
        val weekIndex = resolveWeekIndex(today, userPrefs.termStartDate)

        val displayCourses = (schedule?.coursesOfDay(dayOfWeek).orEmpty() +
            manualCourses.filter { it.time.dayOfWeek == dayOfWeek })
            .filter { it.activeOnWeek(weekIndex) }
            .sortedBy { it.time.startNode }

        data class Annotated(val course: CourseItem, val status: CourseStatus)

        val annotated = displayCourses.map { course ->
            val startTime = timingProfile?.courseStartTime(course)
            val endTime = timingProfile?.courseEndTime(course)
            val status = when {
                startTime == null || endTime == null -> CourseStatus.Upcoming
                !now.isBefore(endTime) -> CourseStatus.Past
                !now.isBefore(startTime) -> CourseStatus.Live
                else -> CourseStatus.Upcoming
            }
            Annotated(course, status)
        }
        val live = annotated.firstOrNull { it.status == CourseStatus.Live }?.course
        val firstUpcoming = annotated.firstOrNull { it.status == CourseStatus.Upcoming }?.course

        val headerBadge: (@Composable () -> Unit)? = when {
            live != null -> {
                @Composable {
                    PillBadge(
                        text = "上课中",
                        container = GlanceTheme.colors.primary,
                        onContainer = GlanceTheme.colors.onPrimary,
                    )
                }
            }
            firstUpcoming != null -> {
                val startTime = timingProfile?.courseStartTime(firstUpcoming)
                val mins = startTime?.let { Duration.between(now, it).toMinutes() }
                if (mins != null && mins in 1..600) {
                    @Composable {
                        PillBadge(
                            text = formatCountdown(mins),
                            container = GlanceTheme.colors.tertiaryContainer,
                            onContainer = GlanceTheme.colors.onTertiaryContainer,
                        )
                    }
                } else null
            }
            else -> null
        }

        val headerLabel = when {
            live != null -> "今日课程 · 上课中"
            firstUpcoming != null -> "下一节课"
            annotated.isNotEmpty() -> "今日课程已结束"
            else -> "下一节课"
        }

        provideContent {
            val sizeClass = WidgetSizeClass.fromDp(
                widthDp = LocalSize.current.width.value.roundToInt(),
                heightDp = LocalSize.current.height.value.roundToInt(),
            )
            val outerPadding = if (sizeClass == WidgetSizeClass.Compact) 8.dp else WidgetStyle.outerPadding
            val rowGap = if (sizeClass == WidgetSizeClass.Compact) 6.dp else 8.dp
            val displayAnnotated = if (sizeClass == WidgetSizeClass.Compact) {
                annotated.take(sizeClass.nextCourseRows())
            } else {
                annotated
            }

            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(WidgetStyle.cardCorner)
                        .padding(outerPadding),
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel(text = headerLabel)
                        Box(modifier = GlanceModifier.defaultWeight()) { Spacer(GlanceModifier.height(0.dp)) }
                        headerBadge?.invoke()
                    }
                    Spacer(GlanceModifier.height(rowGap))
                    if (displayAnnotated.isEmpty()) {
                        EmptyState(sizeClass)
                    } else {
                        LazyColumn(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .defaultWeight(),
                        ) {
                            items(displayAnnotated, itemId = { it.course.id.hashCode().toLong() }) { entry ->
                                val isFocus = entry.course === live ||
                                    (live == null && entry.course === firstUpcoming)
                                Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = rowGap)) {
                                    if (sizeClass != WidgetSizeClass.Compact && isFocus) {
                                        FocusCard(
                                            course = entry.course,
                                            profile = timingProfile,
                                            status = entry.status,
                                        )
                                    } else {
                                        CompactRow(
                                            course = entry.course,
                                            profile = timingProfile,
                                            status = entry.status,
                                        )
                                    }
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

internal enum class CourseStatus { Past, Live, Upcoming }

@Composable
private fun FocusCard(
    course: CourseItem,
    profile: TermTimingProfile?,
    status: CourseStatus,
) {
    val timeRange = profile?.courseClockRange(course, separator = " – ")
        ?: "${course.time.startNode}-${course.time.endNode}节"
    val accent = when (status) {
        CourseStatus.Live -> GlanceTheme.colors.primary
        CourseStatus.Upcoming -> GlanceTheme.colors.tertiary
        CourseStatus.Past -> GlanceTheme.colors.surfaceVariant
    }
    val isPast = status == CourseStatus.Past
    val titleColor = if (isPast) GlanceTheme.colors.onSurfaceVariant
    else GlanceTheme.colors.onSurface
    val label = when (status) {
        CourseStatus.Live -> "上课中"
        CourseStatus.Upcoming -> "即将开始"
        CourseStatus.Past -> "已结束"
    }
    AccentRow(accent = accent, dimmed = isPast) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = "${course.time.startNode}-${course.time.endNode}节",
                    style = TextStyle(
                        color = if (status == CourseStatus.Past) GlanceTheme.colors.onSurfaceVariant
                        else GlanceTheme.colors.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = course.title,
                style = TextStyle(
                    color = titleColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = timeRange,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
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
private fun CompactRow(course: CourseItem, profile: TermTimingProfile?, status: CourseStatus) {
    val timeRange = profile?.courseClockRange(course)
        ?: "${course.time.startNode}-${course.time.endNode}节"
    val accent = when (status) {
        CourseStatus.Live -> GlanceTheme.colors.primary
        CourseStatus.Upcoming -> GlanceTheme.colors.tertiary
        CourseStatus.Past -> GlanceTheme.colors.surfaceVariant
    }
    val isPast = status == CourseStatus.Past
    val titleColor = if (isPast) GlanceTheme.colors.onSurfaceVariant
    else GlanceTheme.colors.onSurface
    val label = when (status) {
        CourseStatus.Live -> "上课中"
        CourseStatus.Upcoming -> "稍后"
        CourseStatus.Past -> "已结束"
    }
    AccentRow(accent = accent, dimmed = isPast) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = timeRange,
                    style = TextStyle(
                        color = if (status == CourseStatus.Past) GlanceTheme.colors.onSurfaceVariant
                        else GlanceTheme.colors.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Text(
                text = course.title,
                style = TextStyle(
                    color = titleColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            val subline = listOf(course.location, course.teacher)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (subline.isNotBlank()) {
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
}

@Composable
private fun EmptyState(sizeClass: WidgetSizeClass) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(WidgetStyle.rowCorner)
            .padding(
                horizontal = if (sizeClass == WidgetSizeClass.Compact) 10.dp else 12.dp,
                vertical = if (sizeClass == WidgetSizeClass.Compact) 12.dp else 16.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "✨",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = if (sizeClass == WidgetSizeClass.Compact) 18.sp else 20.sp,
                ),
            )
            Spacer(GlanceModifier.height(if (sizeClass == WidgetSizeClass.Compact) 2.dp else 4.dp))
            Text(
                text = if (sizeClass == WidgetSizeClass.Compact) "今天没有课程" else "今天没有更多课程",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (sizeClass == WidgetSizeClass.Compact) 12.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            if (sizeClass != WidgetSizeClass.Compact) {
                Text(
                    text = "好好休息吧",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                )
            }
        }
    }
}

private fun formatCountdown(minutes: Long): String {
    if (minutes < 60) return "${minutes}分钟后"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0L) "${h}小时后" else "${h}小时${m}分后"
}

open class NextCourseGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextCourseGlanceWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetCatalog.notifyInstalledChanged(context)
    }
}

class NextCourseGlanceWidgetReceiverMIUI : NextCourseGlanceWidgetReceiver()
