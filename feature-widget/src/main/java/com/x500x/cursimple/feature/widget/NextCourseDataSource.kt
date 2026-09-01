package com.x500x.cursimple.feature.widget

import android.content.Context
import com.x500x.cursimple.core.data.DataStoreManualCourseRepository
import com.x500x.cursimple.core.data.DataStoreScheduleRepository
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.coursesOfDay
import com.x500x.cursimple.core.kernel.model.weekdayLabel
import com.x500x.cursimple.core.kernel.time.BeijingTime
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate

internal data class NextCourseRow(
    val id: String,
    val label: String,
    val period: String,
    val title: String,
    val time: String,
    val sub: String,
    val isFocus: Boolean,
    val isPast: Boolean,
) {
    val stableId: Long = id.hashCode().toLong()
}

internal data class NextCourseWidgetData(
    val widgetTheme: WidgetThemePreferences,
    val headerLabel: String,
    val badgeText: String?,
    val emptyTitle: String,
    val rows: List<NextCourseRow>,
) {
    val themeAccent: ThemeAccent = widgetTheme.themeAccent
}

internal object NextCourseDataSource {
    private val cache = WidgetDataCache<NextCourseWidgetData>()

    /** [reuseRecent] 为 true 时优先复用刚读出的当次结果，让列表跟着头部走同一份数据。 */
    suspend fun load(context: Context, reuseRecent: Boolean = false): NextCourseWidgetData {
        if (reuseRecent) {
            cache.get(WIDGET_SHARED_CACHE_KEY, System.nanoTime())?.let { return it }
        }
        return loadFresh(context)
            .also { cache.put(WIDGET_SHARED_CACHE_KEY, System.nanoTime(), it) }
    }

    private suspend fun loadFresh(context: Context): NextCourseWidgetData {
        val appContext = context.applicationContext
        val termProfileRepository = DataStoreTermProfileRepository(appContext)
        val scheduleRepository = DataStoreScheduleRepository(appContext, termProfileRepository)
        val manualCourseRepository = DataStoreManualCourseRepository(appContext, termProfileRepository)
        val userPreferencesRepository = DataStoreUserPreferencesRepository(appContext)
        val widgetPreferencesRepository = DataStoreWidgetPreferencesRepository(appContext)
        val schedule = scheduleRepository.scheduleFlow.first()
        val manualCourses = manualCourseRepository.manualCoursesFlow.first()
        val timingProfile = widgetPreferencesRepository.timingProfileFlow.first()
        val widgetTheme = widgetPreferencesRepository.themePreferencesFlow.first()
        val userPrefs = userPreferencesRepository.preferencesFlow.first()
        val zone = BeijingTime.zone
        BeijingTime.setForcedNow(userPrefs.debugForcedDateTime)
        val today = BeijingTime.todayIn(zone)
        val now = BeijingTime.nowTimeIn(zone)
        val termStart = resolveWidgetTermStartDate(
            termProfileRepository = termProfileRepository,
            timingProfile = timingProfile,
            preferenceTermStartDate = userPrefs.termStartDate,
        )

        fun coursesForDate(targetDate: LocalDate): WidgetScheduleDay = resolveWidgetScheduleDay(
            targetDate = targetDate,
            termStart = termStart,
            temporaryScheduleOverrides = userPrefs.temporaryScheduleOverrides,
            holidayCalendar = userPrefs.holidayCalendar,
        ) { dayOfWeek ->
            schedule?.coursesOfDay(dayOfWeek).orEmpty() +
                manualCourses.filter { it.time.dayOfWeek == dayOfWeek }
        }

        val todayDay = coursesForDate(today)
        val displayDay = if (shouldShowNextDayAtNight(now, todayDay.courses, timingProfile)) {
            coursesForDate(today.plusDays(1))
        } else {
            todayDay
        }
        val targetDate = displayDay.targetDate
        val sourceDate = displayDay.sourceDate
        val displayCourses = displayDay.courses

        val visibleEntries = visibleNextCourseEntries(
            courses = displayCourses,
            today = today,
            targetDate = targetDate,
            now = now,
            timingProfile = timingProfile,
        )
        val live = visibleEntries.firstOrNull { it.status == CourseStatus.Live }?.course
        val firstUpcoming = visibleEntries.firstOrNull { it.status == CourseStatus.Upcoming }?.course

        val badgeText: String? = when {
            live != null -> "上课中"
            firstUpcoming != null -> {
                val startTime = timingProfile?.courseStartTime(firstUpcoming)
                val mins = startTime?.let { Duration.between(now, it).toMinutes() }
                if (mins != null && mins in 1..600) formatCountdownMinutes(mins) else null
            }
            else -> null
        }

        val todayHeader = nextCourseDayHeader(
            targetDate = targetDate,
            sourceDate = sourceDate,
            today = today,
            holidayLabel = displayDay.holidayLabel,
        )
        val headerLabel = when {
            live != null -> "$todayHeader · ${if (live.category == CourseCategory.Exam) "考试中" else "上课中"}"
            firstUpcoming != null -> if (todayHeader != "今日课程") todayHeader else "下一节课"
            displayCourses.isNotEmpty() -> "$todayHeader · 已结束"
            else -> if (todayHeader != "今日课程") todayHeader else "下一节课"
        }
        val emptyTitle = nextCourseEmptyTitle(
            weekIndex = displayDay.weekIndex,
            termStartDate = termStart,
            targetDate = targetDate,
            today = today,
            hasCourses = displayCourses.isNotEmpty(),
            holidayLabel = displayDay.holidayLabel,
        )

        val rows = visibleEntries.map { entry ->
            val course = entry.course
            val timeRange = timingProfile?.courseClockRange(course, separator = " – ")
                ?: "${course.time.startNode}-${course.time.endNode}节"
            val period = "${course.time.startNode}-${course.time.endNode}节"
            val label = when (entry.status) {
                CourseStatus.Live -> if (course.category == CourseCategory.Exam) "考试中" else "上课中"
                CourseStatus.Upcoming -> "即将开始"
                CourseStatus.Past -> "已结束"
            }
            val sub = listOf(course.location, course.teacher)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { "待定" }
            NextCourseRow(
                id = course.id,
                label = label,
                period = period,
                title = if (course.category == CourseCategory.Exam) "考试 · ${course.title}" else course.title,
                time = timeRange,
                sub = sub,
                isFocus = course === live || (live == null && course === firstUpcoming),
                isPast = entry.status == CourseStatus.Past,
            )
        }

        return NextCourseWidgetData(
            widgetTheme = widgetTheme,
            headerLabel = headerLabel,
            badgeText = badgeText,
            emptyTitle = emptyTitle,
            rows = rows,
        )
    }

    private fun formatCountdownMinutes(minutes: Long): String {
        if (minutes < 60) return "${minutes}分钟后"
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0L) "${h}小时后" else "${h}小时${m}分后"
    }
}

/** 表头前缀；放假当天补上假日名，来源日期与目标日期不同时补上“按X月X日周X”。 */
internal fun nextCourseDayHeader(
    targetDate: LocalDate,
    sourceDate: LocalDate,
    today: LocalDate,
    holidayLabel: String? = null,
): String {
    val dayLabel = if (targetDate == today) "今日课程" else "明日课程"
    return when {
        holidayLabel != null -> "$dayLabel · $holidayLabel"
        sourceDate != targetDate -> "$dayLabel · 按${sourceDateLabel(sourceDate)}"
        else -> dayLabel
    }
}

private fun sourceDateLabel(date: LocalDate): String =
    "${date.monthValue}月${date.dayOfMonth}日${weekdayLabel(date.dayOfWeek.value)}"
