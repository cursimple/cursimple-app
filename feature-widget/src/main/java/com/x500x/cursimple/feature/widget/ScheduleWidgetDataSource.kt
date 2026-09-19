package com.x500x.cursimple.feature.widget

import android.content.Context
import com.x500x.cursimple.core.data.DataStoreManualCourseRepository
import com.x500x.cursimple.core.data.DataStoreScheduleRepository
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.reminder.DataStoreReminderRepository
import com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import com.x500x.cursimple.core.data.widget.resolveAccent
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.coursesOfDay
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.reminder.model.ReminderRule
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

internal data class ScheduleWidgetCourseRow(
    val id: String,
    val nodeRange: String,
    val timeRange: String,
    val title: String,
    val subtitle: String,
    val hasReminder: Boolean,
    /** 放假当天的课程，行文字按不可用态显示。 */
    val onHoliday: Boolean = false,
    /** 相对当前时刻的状态；不是今天、放假或没有作息时间时为空。 */
    val status: CourseStatus? = null,
    /** 考试与普通课程的状态文案不同。 */
    val isExam: Boolean = false,
) {
    val stableId: Long = id.hashCode().toLong()
}

internal data class ScheduleWidgetDayData(
    val offset: Int,
    val manualOffset: Int,
    val targetDate: LocalDate,
    val sourceDate: LocalDate,
    val rows: List<ScheduleWidgetCourseRow>,
    val widgetTheme: WidgetThemePreferences = WidgetThemePreferences(),
    val beforeTermStart: Boolean = false,
    val termStartMissing: Boolean = false,
    val termStartDate: LocalDate? = null,
    val holidayLabel: WidgetHolidayLabel? = null,
) {
    val themeAccent: ThemeAccent = widgetTheme.themeAccent
}

internal object ScheduleWidgetDataSource {
    private val dayCache = WidgetDataCache<ScheduleWidgetDayData>()

    /** [reuseRecent] 为 true 时优先复用刚读出的当次结果，让列表跟着头部走同一份数据。 */
    suspend fun loadDay(
        context: Context,
        appWidgetId: Int,
        reuseRecent: Boolean = false,
    ): ScheduleWidgetDayData {
        if (reuseRecent) {
            dayCache.get(appWidgetId, System.nanoTime())?.let { return it }
        }
        return loadFreshDay(context, appWidgetId)
            .also { dayCache.put(appWidgetId, System.nanoTime(), it) }
    }

    /** 偏好或课表刚被改过时清掉短时缓存，避免列表还拿着上一天的那一份。 */
    fun invalidate() {
        dayCache.clear()
    }

    private suspend fun loadFreshDay(context: Context, appWidgetId: Int): ScheduleWidgetDayData {
        val appContext = context.applicationContext
        val termProfileRepository = DataStoreTermProfileRepository(appContext)
        val scheduleRepository = DataStoreScheduleRepository(appContext, termProfileRepository)
        val manualCourseRepository = DataStoreManualCourseRepository(appContext, termProfileRepository)
        val reminderRepository = DataStoreReminderRepository(appContext)
        val userPreferencesRepository = DataStoreUserPreferencesRepository(appContext)
        val widgetPreferencesRepository = DataStoreWidgetPreferencesRepository(appContext)

        val userPrefs = userPreferencesRepository.preferencesFlow.first()
        val timingProfile = widgetPreferencesRepository.timingProfileFlow.first()
        // 没单独挑过小组件配色时跟着应用主题色走
        val widgetTheme = widgetPreferencesRepository.themePreferencesFlow.first()
            .resolveAccent(userPrefs.themeAccent)
        val zone = BeijingTime.zone
        BeijingTime.setForcedNow(userPrefs.debugForcedDateTime)
        val today = BeijingTime.todayIn(zone)
        val now = BeijingTime.nowTimeIn(zone)
        // 偏移锚在按下那天，跨过零点自动作废，不会机械地又往后顺延一天
        // INVALID_APPWIDGET_ID 就是 0，正好对上仓储里共用那一份偏移的伪实例 id
        val manualOffset = widgetPreferencesRepository.effectiveWidgetDayOffset(
            appWidgetId = appWidgetId,
            todayIso = today.toString(),
        )
        val termStart = resolveWidgetTermStartDate(
            termProfileRepository = termProfileRepository,
            timingProfile = timingProfile,
            preferenceTermStartDate = userPrefs.termStartDate,
        )
        val sources = DaySources(
            schedule = scheduleRepository.scheduleFlow.first(),
            manualCourses = manualCourseRepository.manualCoursesFlow.first(),
            reminderRules = reminderRepository.reminderRulesFlow.first(),
            temporaryScheduleOverrides = userPrefs.temporaryScheduleOverrides,
            holidayCalendar = userPrefs.holidayCalendar,
        )

        fun dayAt(offset: Int) = loadDate(
            context = appContext,
            targetDate = today.plusDays(offset.toLong()),
            today = today,
            now = now,
            offset = offset,
            manualOffset = manualOffset,
            termStart = termStart,
            timingProfile = timingProfile,
            sources = sources,
            widgetTheme = widgetTheme,
        )

        if (manualOffset != 0) return dayAt(manualOffset).data

        val currentDay = dayAt(0)
        if (!shouldShowNextDayAtNight(now, currentDay.courses, timingProfile)) return currentDay.data
        // 今天已经上完才往后翻，并且跳过放假与空课的日子，不是机械地加一天
        val nextDay = (1..AUTO_ADVANCE_MAX_DAYS)
            .firstNotNullOfOrNull { offset ->
                dayAt(offset).takeIf { it.rows.isNotEmpty() && !it.onHoliday }
            }
        return (nextDay ?: dayAt(1)).data
    }

    /** 一次读出、多天共用的课表来源，免得往后找有课的一天时把仓储重读好几遍。 */
    private data class DaySources(
        val schedule: com.x500x.cursimple.core.kernel.model.TermSchedule?,
        val manualCourses: List<CourseItem>,
        val reminderRules: List<ReminderRule>,
        val temporaryScheduleOverrides: List<TemporaryScheduleOverride>,
        val holidayCalendar: HolidayCalendarSettings,
    )

    /** 往后找有课的一天最多看这么多天，都没有就按明天显示。 */
    private const val AUTO_ADVANCE_MAX_DAYS = 7

    private fun loadDate(
        context: Context,
        targetDate: LocalDate,
        today: LocalDate,
        now: LocalTime,
        offset: Int,
        manualOffset: Int,
        termStart: LocalDate?,
        timingProfile: TermTimingProfile?,
        sources: DaySources,
        widgetTheme: WidgetThemePreferences,
    ): LoadedDay {
        val day = resolveWidgetScheduleDay(
            targetDate = targetDate,
            termStart = termStart,
            temporaryScheduleOverrides = sources.temporaryScheduleOverrides,
            holidayCalendar = sources.holidayCalendar,
        ) { dayOfWeek ->
            sources.schedule?.coursesOfDay(dayOfWeek).orEmpty() +
                sources.manualCourses.filter { it.time.dayOfWeek == dayOfWeek }
        }
        val rows = day.courses.map {
            it.toRow(
                context = context,
                timingProfile = timingProfile,
                reminderRules = sources.reminderRules,
                onHoliday = day.onHoliday,
                status = widgetRowStatus(
                    course = it,
                    today = today,
                    targetDate = targetDate,
                    now = now,
                    timingProfile = timingProfile,
                    onHoliday = day.onHoliday,
                ),
            )
        }

        return LoadedDay(
            data = ScheduleWidgetDayData(
                offset = offset,
                manualOffset = manualOffset,
                targetDate = targetDate,
                sourceDate = day.sourceDate,
                rows = rows,
                widgetTheme = widgetTheme,
                beforeTermStart = isBeforeTermStart(day.weekIndex),
                termStartMissing = day.weekIndex == null,
                termStartDate = termStart,
                holidayLabel = day.holidayLabel,
            ),
            courses = day.courses,
            onHoliday = day.onHoliday,
        )
    }

    private data class LoadedDay(
        val data: ScheduleWidgetDayData,
        val courses: List<CourseItem>,
        val onHoliday: Boolean,
    ) {
        val rows: List<ScheduleWidgetCourseRow> get() = data.rows
    }

    private fun CourseItem.toRow(
        context: Context,
        timingProfile: TermTimingProfile?,
        reminderRules: List<ReminderRule>,
        onHoliday: Boolean,
        status: CourseStatus?,
    ): ScheduleWidgetCourseRow {
        val nodeRange = context.widgetNodeRangeText(time.startNode, time.endNode)
        val timeRange = timingProfile?.courseClockRange(this) ?: nodeRange
        val subtitle = listOf(location, teacher)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifBlank { context.getString(R.string.widget_course_subtitle_placeholder) }
        return ScheduleWidgetCourseRow(
            id = id,
            nodeRange = nodeRange,
            timeRange = timeRange,
            title = context.widgetCourseTitleText(title, category == CourseCategory.Exam),
            subtitle = subtitle,
            hasReminder = reminderRules.any { it.matchesWidgetCourse(this, timingProfile) },
            onHoliday = onHoliday,
            status = status,
            isExam = category == CourseCategory.Exam,
        )
    }
}

/**
 * 小组件视角下的今天（ISO 文本）。
 * 调试用的强制时间也在这里生效，手动翻页的锚点与展示的日期才会是同一天。
 */
internal suspend fun widgetTodayIso(context: Context): String {
    val prefs = DataStoreUserPreferencesRepository(context.applicationContext).preferencesFlow.first()
    BeijingTime.setForcedNow(prefs.debugForcedDateTime)
    return BeijingTime.todayIn(BeijingTime.zone).toString()
}

/**
 * 行上要标的状态。
 *
 * 只有今天且不放假的课才有状态可言；放假当天课程照常列出但不判上课中，
 * 没有作息时间就算不出起止时刻，同样不标。
 */
internal fun widgetRowStatus(
    course: CourseItem,
    today: LocalDate,
    targetDate: LocalDate,
    now: LocalTime,
    timingProfile: TermTimingProfile?,
    onHoliday: Boolean,
): CourseStatus? {
    if (onHoliday || timingProfile == null || targetDate != today) return null
    return resolveCourseStatus(
        course = course,
        today = today,
        targetDate = targetDate,
        now = now,
        timingProfile = timingProfile,
    )
}

/** 所有小组件共用的开学日期来源，保证不同小组件算出同一个教学周。 */
internal suspend fun resolveWidgetTermStartDate(
    termProfileRepository: DataStoreTermProfileRepository,
    timingProfile: TermTimingProfile?,
    preferenceTermStartDate: LocalDate?,
): LocalDate? {
    val activeTermId = termProfileRepository.activeTermId()
    val activeTermStartIso = termProfileRepository.termsFlow.first()
        .firstOrNull { it.id == activeTermId }
        ?.termStartDate
    return selectTermStartDate(
        activeTermStartIso = activeTermStartIso,
        timingProfileTermStartIso = timingProfile?.termStartDate,
        preferenceTermStartDate = preferenceTermStartDate,
    )
}

/** 把计时档案的开学日期换成统一解析出的日期；日期为空或本就一致时返回原档案。 */
internal fun TermTimingProfile.withTermStartDate(termStartDate: LocalDate?): TermTimingProfile {
    val iso = termStartDate?.toString() ?: return this
    return if (iso == this.termStartDate) this else copy(termStartDate = iso)
}

/** 当前学期档案 → 小组件计时档案 → 用户偏好，取第一个能解析出日期的来源。 */
internal fun selectTermStartDate(
    activeTermStartIso: String?,
    timingProfileTermStartIso: String?,
    preferenceTermStartDate: LocalDate?,
): LocalDate? =
    activeTermStartIso?.let(::parseIsoDate)
        ?: timingProfileTermStartIso?.let(::parseIsoDate)
        ?: preferenceTermStartDate

private fun parseIsoDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()
