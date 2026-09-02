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

        // 放假当天课程照常列出，但不判上课中、不给倒计时
        val visibleEntries = if (displayDay.onHoliday) {
            emptyList()
        } else {
            visibleNextCourseEntries(
                courses = displayCourses,
                today = today,
                targetDate = targetDate,
                now = now,
                timingProfile = timingProfile,
            )
        }
        val live = visibleEntries.firstOrNull { it.status == CourseStatus.Live }?.course
        val firstUpcoming = visibleEntries.firstOrNull { it.status == CourseStatus.Upcoming }?.course

        val badgeText: String? = when {
            live != null -> appContext.getString(R.string.widget_status_live)
            firstUpcoming != null -> {
                val startTime = timingProfile?.courseStartTime(firstUpcoming)
                val mins = startTime?.let { Duration.between(now, it).toMinutes() }
                if (mins != null && mins in 1..600) appContext.countdownText(mins) else null
            }
            else -> null
        }

        val dayHeader = nextCourseDayHeader(
            targetDate = targetDate,
            sourceDate = sourceDate,
            today = today,
            holidayLabel = displayDay.holidayLabel,
        )
        val dayHeaderText = appContext.nextCourseDayHeaderText(dayHeader)
        val plainToday = dayHeader is NextCourseDayHeader.Plain && !dayHeader.tomorrow
        val nextCourseLabel = appContext.getString(R.string.widget_label_next)
        val headerLabel = when {
            live != null -> appContext.getString(
                R.string.widget_next_header_status,
                dayHeaderText,
                appContext.getString(
                    widgetCourseStatusRes(CourseStatus.Live, live.category == CourseCategory.Exam),
                ),
            )

            firstUpcoming != null -> if (plainToday) nextCourseLabel else dayHeaderText
            displayCourses.isNotEmpty() -> appContext.getString(
                R.string.widget_next_header_status,
                dayHeaderText,
                appContext.getString(R.string.widget_status_finished),
            )

            else -> if (plainToday) nextCourseLabel else dayHeaderText
        }
        val emptyTitle = appContext.nextCourseEmptyText(
            nextCourseEmptyLabel(
                weekIndex = displayDay.weekIndex,
                termStartDate = termStart,
                targetDate = targetDate,
                today = today,
                hasCourses = displayCourses.isNotEmpty(),
                holidayLabel = displayDay.holidayLabel,
            ),
        )

        val rows = visibleEntries.map { entry ->
            val course = entry.course
            val period = appContext.widgetNodeRangeText(course.time.startNode, course.time.endNode)
            val timeRange = timingProfile?.courseClockRange(course, separator = " – ") ?: period
            val exam = course.category == CourseCategory.Exam
            val label = appContext.getString(widgetCourseStatusRes(entry.status, exam))
            val sub = listOf(course.location, course.teacher)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { appContext.getString(R.string.widget_course_subtitle_placeholder) }
            NextCourseRow(
                id = course.id,
                label = label,
                period = period,
                title = appContext.widgetCourseTitleText(course.title, exam),
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

    private fun Context.countdownText(minutes: Long): String {
        if (minutes < 60) return getString(R.string.widget_countdown_minutes, minutes.toInt())
        val h = (minutes / 60).toInt()
        val m = (minutes % 60).toInt()
        return if (m == 0) {
            getString(R.string.widget_countdown_hours, h)
        } else {
            getString(R.string.widget_countdown_hours_minutes, h, m)
        }
    }
}
